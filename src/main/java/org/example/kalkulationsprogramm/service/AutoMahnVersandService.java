package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.service.AutoAuftragsbestaetigungVersandService.VorlagenDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.LayoutDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erzeugt und versendet Mahnstufen (Zahlungserinnerung, 1./2. Mahnung)
 * automatisch per E-Mail, sobald eine Rechnung gemäss den globalen
 * Tage-Schwellen aus {@link Firmeninformation} ueberfaellig ist.
 *
 * <p>Die Stufen eskalieren mit <strong>garantierten Abstaenden</strong>: nur
 * die Zahlungserinnerung haengt an der Faelligkeit der Original-Rechnung
 * ({@code tageBisZahlungserinnerung}); die 1. Mahnung folgt fruehestens
 * {@code tageBisErsteMahnung} Tage nach dem <em>Versand</em> der
 * Zahlungserinnerung, die 2. Mahnung fruehestens {@code tageBisZweiteMahnung}
 * Tage nach dem Versand der 1. Mahnung. So folgen die Stufen auch bei einer
 * bereits lange ueberfaelligen Rechnung nicht im Tagesabstand aufeinander.</p>
 *
 * <p>Im Gegensatz zur ereignisgetriebenen Auto-Auftragsbestaetigung laeuft die
 * Mahnung zeit-getrieben: ein taeglicher Cron pruefst alle offenen Rechnungen
 * und legt fuer jede faellige Rechnung die naechste fehlende Mahnstufe an.
 * Mahnungen werden als regulaere {@link ProjektGeschaeftsdokument}-Eintraege
 * mit {@code geschaeftsdokumentart="Mahnung"} + {@code mahnstufe} gespeichert,
 * damit sie im OffenePostenEditor analog zu manuellen Mahnungen erscheinen.</p>
 *
 * <p>Eine Mahnung enthaelt bewusst <strong>keine Leistungspositionen</strong>
 * — sie verweist nur auf die offene Original-Rechnung. Die Position-Liste
 * hat der Kunde mit der urspruenglichen Rechnung schon bekommen (DATEV-/
 * Lexware-Konvention).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMahnVersandService
{
    private static final DateTimeFormatter DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FirmeninformationRepository firmaRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final RechnungPdfService rechnungPdfService;
    private final EmailTextTemplateService emailTextTemplateService;
    private final SystemSettingsService systemSettingsService;
    private final FormularTemplateService formularTemplateService;
    private final FormularTextbausteinDefaultService formularTextbausteinDefaultService;
    private final EmailSignatureService emailSignatureService;

    /**
     * Taeglich um 09:00 — durchlaeuft offene Rechnungen, erzeugt fehlende
     * Mahnstufen gemaess Firmen-Konfiguration. Jede Rechnung wird in einer
     * eigenen Tx behandelt, damit ein Fehler bei einer Rechnung den Lauf nicht
     * abbricht.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void verarbeiteFaelligeMahnungen()
    {
        Firmeninformation firma = firmaRepository.findById(1L).orElse(null);
        if (firma == null || !firma.isMahnverfahrenAktiv())
        {
            return;
        }

        // Fetch-Variante der Offene-Posten-Query: der Scheduler-Thread hat
        // keine offene Hibernate-Session, die Entities sind detached — alle
        // lazy Beziehungen (mahnungen, projekt, kunde) muessen daher schon
        // beim Laden aufgeloest sein.
        List<ProjektGeschaeftsdokument> offene =
                projektDokumentRepository.findOffeneGeschaeftsdokumenteFuerMahnlauf();
        LocalDate heute = LocalDate.now();
        int versendet = 0;
        for (ProjektGeschaeftsdokument dok : offene)
        {
            try
            {
                if (verarbeiteRechnung(dok, firma, heute)) versendet++;
            }
            catch (Exception e)
            {
                log.error("Auto-Mahn-Lauf fuer Dokument {} fehlgeschlagen: {}",
                        dok.getId(), e.getMessage(), e);
            }
        }
        if (versendet > 0)
        {
            log.info("Auto-Mahn-Lauf abgeschlossen: {} Mahnung(en) versendet", versendet);
        }
    }

    /**
     * Pruefung + Versand fuer eine einzelne Rechnung. Liefert {@code true},
     * wenn eine Mahnung erzeugt und versendet wurde.
     */
    boolean verarbeiteRechnung(ProjektGeschaeftsdokument dok, Firmeninformation firma, LocalDate heute)
    {
        if (!istOriginalRechnungOhneMahnstufe(dok)) return false;
        if (!dok.isSystemGeneriert()) return false;
        if (dok.isBezahlt()) return false;
        if (dok.getFaelligkeitsdatum() == null) return false;
        if (dok.getProjekt() == null) return false;

        long tageUeberfaellig = ChronoUnit.DAYS.between(dok.getFaelligkeitsdatum(), heute);
        if (tageUeberfaellig <= 0) return false;

        Mahnstufe naechsteStufe = ermittleNaechsteStufe(dok, firma, tageUeberfaellig, heute);
        if (naechsteStufe == null) return false;

        String empfaenger = ermittleEmpfaenger(dok);
        if (empfaenger == null || empfaenger.isBlank())
        {
            log.warn("Auto-Mahnung uebersprungen: keine E-Mail fuer Rechnung {} (Projekt {})",
                    dok.getDokumentid(), dok.getProjekt().getId());
            return false;
        }

        return erzeugeUndVersende(dok, naechsteStufe, firma, empfaenger, heute, tageUeberfaellig);
    }

    private static boolean istOriginalRechnungOhneMahnstufe(ProjektGeschaeftsdokument dok)
    {
        if (dok.getMahnstufe() != null) return false;
        String art = dok.getGeschaeftsdokumentart();
        return art != null && art.toLowerCase(Locale.GERMAN).contains("rechnung")
                && !art.toLowerCase(Locale.GERMAN).contains("mahn");
    }

    /**
     * Ermittelt die naechste faellige Mahnstufe. Stufen werden in fester
     * Reihenfolge eskaliert (Zahlungserinnerung → 1. Mahnung → 2. Mahnung)
     * mit garantierten Abstaenden:
     *
     * <ul>
     *   <li><strong>Zahlungserinnerung</strong>: faellig, sobald die Rechnung
     *       {@code tageBisZahlungserinnerung} Tage ueberfaellig ist.</li>
     *   <li><strong>1. Mahnung</strong>: fruehestens
     *       {@code tageBisErsteMahnung} Tage nach dem Versand der
     *       Zahlungserinnerung.</li>
     *   <li><strong>2. Mahnung</strong>: fruehestens
     *       {@code tageBisZweiteMahnung} Tage nach dem Versand der
     *       1. Mahnung.</li>
     * </ul>
     *
     * <p>Damit folgen die Stufen auch bei einer bei Aktivierung schon lange
     * ueberfaelligen Rechnung nicht im Tagesabstand aufeinander.</p>
     */
    static Mahnstufe ermittleNaechsteStufe(ProjektGeschaeftsdokument rechnung,
                                            Firmeninformation firma,
                                            long tageUeberfaellig,
                                            LocalDate heute)
    {
        Set<Mahnstufe> bereits = bereitsVersendeteStufen(rechnung);

        if (!bereits.contains(Mahnstufe.ZAHLUNGSERINNERUNG))
        {
            return tageUeberfaellig >= firma.getTageBisZahlungserinnerung()
                    ? Mahnstufe.ZAHLUNGSERINNERUNG
                    : null;
        }
        if (!bereits.contains(Mahnstufe.ERSTE_MAHNUNG))
        {
            return istAbstandSeitVorstufeErreicht(rechnung, Mahnstufe.ZAHLUNGSERINNERUNG,
                    firma.getTageBisErsteMahnung(), heute)
                    ? Mahnstufe.ERSTE_MAHNUNG
                    : null;
        }
        if (!bereits.contains(Mahnstufe.ZWEITE_MAHNUNG))
        {
            return istAbstandSeitVorstufeErreicht(rechnung, Mahnstufe.ERSTE_MAHNUNG,
                    firma.getTageBisZweiteMahnung(), heute)
                    ? Mahnstufe.ZWEITE_MAHNUNG
                    : null;
        }
        return null;
    }

    /**
     * {@code true}, wenn seit dem Datum der bereits versendeten Vorstufe
     * mindestens {@code abstandTage} Tage vergangen sind. Der Abstand wird
     * auf mindestens 1 geklemmt, damit nie am selben Tag eskaliert wird.
     */
    private static boolean istAbstandSeitVorstufeErreicht(ProjektGeschaeftsdokument rechnung,
                                                          Mahnstufe vorstufe,
                                                          int abstandTage,
                                                          LocalDate heute)
    {
        LocalDate vorstufenDatum = datumDerStufe(rechnung, vorstufe, heute);
        return !heute.isBefore(vorstufenDatum.plusDays(Math.max(1, abstandTage)));
    }

    /**
     * Datum, an dem die uebergebene Mahnstufe versendet wurde. Existieren
     * mehrere Mahn-Dokumente derselben Stufe (z.B. manuell erstellte neben
     * automatisch versendeten — die Listen-Reihenfolge aus der DB ist dabei
     * zufaellig), zaehlt konservativ das SPAETESTE Datum. Fallback-Kette pro
     * Mahn-Dokument: {@code emailVersandDatum} → {@code rechnungsdatum} →
     * {@code uploadDatum}. Ist alles {@code null} (oder das Mahn-Dokument
     * unauffindbar), gilt die Stufe als heute versendet — Ergebnis: keine
     * Eskalation am selben Tag.
     */
    private static LocalDate datumDerStufe(ProjektGeschaeftsdokument rechnung,
                                           Mahnstufe stufe,
                                           LocalDate heute)
    {
        if (rechnung.getMahnungen() == null) return heute;
        LocalDate spaetestes = null;
        for (ProjektGeschaeftsdokument mahnung : rechnung.getMahnungen())
        {
            if (mahnung.getMahnstufe() != stufe) continue;
            LocalDate datum = datumDesMahnDokuments(mahnung, heute);
            if (spaetestes == null || datum.isAfter(spaetestes)) spaetestes = datum;
        }
        return spaetestes != null ? spaetestes : heute;
    }

    /** Fallback-Kette fuer das Versanddatum eines einzelnen Mahn-Dokuments. */
    private static LocalDate datumDesMahnDokuments(ProjektGeschaeftsdokument mahnung, LocalDate heute)
    {
        if (mahnung.getEmailVersandDatum() != null) return mahnung.getEmailVersandDatum();
        if (mahnung.getRechnungsdatum() != null) return mahnung.getRechnungsdatum();
        if (mahnung.getUploadDatum() != null) return mahnung.getUploadDatum();
        return heute;
    }

    private static Set<Mahnstufe> bereitsVersendeteStufen(ProjektGeschaeftsdokument rechnung)
    {
        if (rechnung.getMahnungen() == null || rechnung.getMahnungen().isEmpty())
        {
            return EnumSet.noneOf(Mahnstufe.class);
        }
        Set<Mahnstufe> result = EnumSet.noneOf(Mahnstufe.class);
        for (ProjektGeschaeftsdokument m : rechnung.getMahnungen())
        {
            if (m.getMahnstufe() != null) result.add(m.getMahnstufe());
        }
        return result;
    }

    private static String ermittleEmpfaenger(ProjektGeschaeftsdokument rechnung)
    {
        Projekt projekt = rechnung.getProjekt();
        if (projekt == null) return null;
        Kunde kunde = projekt.getKundenId();
        if (kunde == null) return null;
        List<String> mails = kunde.getKundenEmails();
        if (mails == null || mails.isEmpty()) return null;
        for (String m : mails)
        {
            if (m != null && !m.isBlank()) return m.trim();
        }
        return null;
    }

    /**
     * Erzeugt PDF, persistiert das Mahn-Dokument via {@link DateiSpeicherService}
     * und versendet die E-Mail. Tempfile wird immer aufgeraeumt.
     */
    private boolean erzeugeUndVersende(ProjektGeschaeftsdokument rechnung,
                                       Mahnstufe stufe,
                                       Firmeninformation firma,
                                       String empfaenger,
                                       LocalDate heute,
                                       long tageUeberfaellig)
    {
        String typLabel = labelFuer(stufe);
        String mahnNummer = generiereMahnNummer(rechnung, stufe);
        LocalDate neuesFaelligkeitsdatum = heute.plusDays(
                Math.max(1, firma.getMahnverfahrenNeuesZahlungszielTage()));

        Map<String, String> ctx = bauePlatzhalterKontext(rechnung, stufe, typLabel,
                mahnNummer, heute, neuesFaelligkeitsdatum, tageUeberfaellig);

        EmailService.EmailContent mailInhalt = emailTextTemplateService.render(stufe.name(), ctx);
        if (mailInhalt == null)
        {
            log.warn("Auto-Mahnung uebersprungen: keine aktive E-Mail-Vorlage fuer {} hinterlegt",
                    stufe.name());
            return false;
        }

        byte[] pdfBytes = generierePdf(rechnung, typLabel, mahnNummer, heute,
                neuesFaelligkeitsdatum, tageUeberfaellig, ctx);

        Path tempPdf = null;
        try
        {
            tempPdf = Files.createTempFile("auto-mahnung-", ".pdf");
            Files.write(tempPdf, pdfBytes);

            String dateiname = "Mahnung_" + sanitize(stufe.name()) + "_"
                    + sanitize(mahnNummer) + ".pdf";
            ProjektGeschaeftsdokument gespeichert = persistiereMahnung(
                    rechnung, stufe, mahnNummer, heute, neuesFaelligkeitsdatum, tempPdf, dateiname);

            EmailService emailService = new EmailService(
                    systemSettingsService.getSmtpHost(),
                    systemSettingsService.getSmtpPort(),
                    systemSettingsService.getSmtpUsername(),
                    systemSettingsService.getSmtpPassword());

            String absender = ermittleAbsenderAdresse();
            String htmlMitSignatur = emailSignatureService
                    .appendSystemSignatureIfConfigured(mailInhalt.htmlBody());
            emailService.sendEmail(empfaenger, null, absender,
                    mailInhalt.subject(), htmlMitSignatur,
                    tempPdf.toString(), dateiname);

            markiereVersendet(gespeichert.getId(), heute);

            log.info("Auto-Mahnung [{}] {} fuer Rechnung {} an {} versendet",
                    typLabel, mahnNummer, rechnung.getDokumentid(), empfaenger);
            return true;
        }
        catch (Exception e)
        {
            log.error("Auto-Mahnung Versand fuer Rechnung {} fehlgeschlagen: {}",
                    rechnung.getDokumentid(), e.getMessage(), e);
            return false;
        }
        finally
        {
            if (tempPdf != null)
            {
                try { Files.deleteIfExists(tempPdf); } catch (IOException ignored) {}
            }
        }
    }

    @Transactional
    protected ProjektGeschaeftsdokument persistiereMahnung(ProjektGeschaeftsdokument rechnung,
                                                            Mahnstufe stufe,
                                                            String mahnNummer,
                                                            LocalDate heute,
                                                            LocalDate neuesFaelligkeitsdatum,
                                                            Path tempPdf,
                                                            String dateiname)
    {
        ZugferdDaten daten = new ZugferdDaten();
        daten.setRechnungsnummer(mahnNummer);
        daten.setRechnungsdatum(heute);
        daten.setFaelligkeitsdatum(neuesFaelligkeitsdatum);
        daten.setBetrag(rechnung.getBruttoBetrag());
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setMahnstufe(stufe.name());
        daten.setReferenzDokumentId(rechnung.getId());

        return dateiSpeicherService.speichereZugferdDatei(
                tempPdf, dateiname, rechnung.getProjekt().getId(), daten);
    }

    @Transactional
    protected void markiereVersendet(Long dokumentId, LocalDate datum)
    {
        projektDokumentRepository.findById(dokumentId).ifPresent(d -> {
            d.setEmailVersandDatum(datum);
            projektDokumentRepository.save(d);
        });
    }

    // ======================= Vorschau =======================

    /**
     * Erzeugt eine PDF-Vorschau, ohne irgendetwas zu persistieren oder zu
     * versenden. Wird vom OffenePostenEditor benutzt, damit der Sachbearbeiter
     * vor Aktivierung des Mahnverfahrens prüfen kann, wie eine Mahnung pro
     * Stufe konkret aussehen würde.
     *
     * <p>Falls die Rechnung noch nicht überfällig ist, wird trotzdem ein PDF
     * erzeugt — die Tage-Angabe wird auf 1 hochgesetzt, damit der
     * Einleitungstext sinnvoll bleibt.</p>
     *
     * @throws IllegalArgumentException wenn das Dokument nicht existiert oder
     *         keine Original-Rechnung ist.
     */
    @Transactional(readOnly = true)
    public byte[] generiereVorschauPdf(Long rechnungId, Mahnstufe stufe)
    {
        ProjektGeschaeftsdokument rechnung = projektDokumentRepository.findById(rechnungId)
                .filter(ProjektGeschaeftsdokument.class::isInstance)
                .map(ProjektGeschaeftsdokument.class::cast)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rechnung nicht gefunden: " + rechnungId));

        LocalDate heute = LocalDate.now();
        Firmeninformation firma = firmaRepository.findById(1L).orElse(null);
        int neuesZielTage = firma != null ? firma.getMahnverfahrenNeuesZahlungszielTage() : 7;
        LocalDate neuesFaelligkeitsdatum = heute.plusDays(Math.max(1, neuesZielTage));

        long tageUeberfaellig = rechnung.getFaelligkeitsdatum() != null
                ? Math.max(1, ChronoUnit.DAYS.between(rechnung.getFaelligkeitsdatum(), heute))
                : 1;

        String typLabel = labelFuer(stufe);
        String mahnNummer = generiereMahnNummer(rechnung, stufe);
        Map<String, String> ctx = bauePlatzhalterKontext(rechnung, stufe, typLabel,
                mahnNummer, heute, neuesFaelligkeitsdatum, tageUeberfaellig);

        return generierePdf(rechnung, typLabel, mahnNummer, heute,
                neuesFaelligkeitsdatum, tageUeberfaellig, ctx);
    }

    /**
     * Vorschau für eine Rechnung aus dem DokumentUebersichtEditor, wo mit
     * {@link AusgangsGeschaeftsDokument} gearbeitet wird statt mit
     * {@link ProjektGeschaeftsdokument}. Mappt das Ausgangs-Dokument
     * in-Memory zu einer leichtgewichtigen {@code ProjektGeschaeftsdokument}-
     * Hülle (kein DB-Insert) und delegiert an die normale PDF-Generierung.
     *
     * <p>Es wird nichts persistiert und nichts versendet — reine Optik-Pruefung.</p>
     */
    @Transactional(readOnly = true)
    public byte[] generiereVorschauPdfFuerAusgangsRechnung(Long ausgangsDokumentId, Mahnstufe stufe)
    {
        AusgangsGeschaeftsDokument ausgang = ausgangsGeschaeftsDokumentRepository.findById(ausgangsDokumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ausgangs-Dokument nicht gefunden: " + ausgangsDokumentId));

        ProjektGeschaeftsdokument simulation = new ProjektGeschaeftsdokument();
        simulation.setDokumentid(ausgang.getDokumentNummer());
        simulation.setGeschaeftsdokumentart("Rechnung");
        simulation.setBezahlt(false);
        simulation.setBruttoBetrag(ausgang.getBetragBrutto());
        simulation.setRechnungsdatum(ausgang.getDatum());
        int zahlungsziel = ausgang.getZahlungszielTage() != null ? ausgang.getZahlungszielTage() : 8;
        simulation.setFaelligkeitsdatum(ausgang.getDatum() != null
                ? ausgang.getDatum().plusDays(zahlungsziel)
                : LocalDate.now().minusDays(1));
        simulation.setProjekt(ausgang.getProjekt() != null
                ? ausgang.getProjekt()
                : ausgangProjektFallback(ausgang));

        return generiereVorschauPdfIntern(simulation, stufe);
    }

    /**
     * Gemeinsame interne Vorschau — wird sowohl vom Projekt- als auch vom
     * Ausgangs-Pfad aufgerufen. Die separaten public-Wrapper laden das
     * jeweilige Quell-Dokument und mappen es auf eine
     * {@link ProjektGeschaeftsdokument}-Sicht.
     */
    private byte[] generiereVorschauPdfIntern(ProjektGeschaeftsdokument rechnung, Mahnstufe stufe)
    {
        LocalDate heute = LocalDate.now();
        Firmeninformation firma = firmaRepository.findById(1L).orElse(null);
        int neuesZielTage = firma != null ? firma.getMahnverfahrenNeuesZahlungszielTage() : 7;
        LocalDate neuesFaelligkeitsdatum = heute.plusDays(Math.max(1, neuesZielTage));

        long tageUeberfaellig = rechnung.getFaelligkeitsdatum() != null
                ? Math.max(1, ChronoUnit.DAYS.between(rechnung.getFaelligkeitsdatum(), heute))
                : 1;

        String typLabel = labelFuer(stufe);
        String mahnNummer = generiereMahnNummer(rechnung, stufe);
        Map<String, String> ctx = bauePlatzhalterKontext(rechnung, stufe, typLabel,
                mahnNummer, heute, neuesFaelligkeitsdatum, tageUeberfaellig);

        return generierePdf(rechnung, typLabel, mahnNummer, heute,
                neuesFaelligkeitsdatum, tageUeberfaellig, ctx);
    }

    /**
     * Wenn ein {@link AusgangsGeschaeftsDokument} kein verknuepftes Projekt
     * hat (z.B. Direktrechnung an Kunde ohne Projekt), bauen wir eine minimale
     * In-Memory-Projekt-Huelle, damit der Kontext-Builder den Kunden auflesen
     * kann.
     */
    private static Projekt ausgangProjektFallback(AusgangsGeschaeftsDokument ausgang)
    {
        Projekt p = new Projekt();
        p.setBauvorhaben(ausgang.getBetreff());
        p.setKundenId(ausgang.getKunde());
        return p;
    }

    // ======================= PDF =======================

    /**
     * Rendert das Mahn-PDF. Lädt — analog zur Auto-Auftragsbestätigung —
     * die im Formularwesen für die jeweilige Mahnstufe zugewiesene Vorlage
     * (Briefkopf-Hintergrundbild + FormBlocks für Adresse / Datum / Logo /…)
     * und die zugewiesenen Standard-Textbausteine (Vor- und Nachtexte mit
     * Platzhalter-Auflösung).
     *
     * <p>Im Leistungsbereich werden bewusst <strong>keine
     * Positionen</strong> übernommen — stattdessen kommt die Forderungs-
     * Box (Rechnungsnr / Datum / Tage überfällig / offener Betrag) zwischen
     * Vor- und Nachtexte. So bleibt die Mahnung schlank und verweist nur
     * auf die offene Original-Rechnung.</p>
     *
     * <p>Wenn keine Vorlage zugeordnet ist, fallback auf das Standard-Layout
     * mit hartcodierten Mahn-Texten — damit der Versand auch ohne
     * Konfiguration funktioniert.</p>
     */
    private byte[] generierePdf(ProjektGeschaeftsdokument rechnung,
                                String typLabel,
                                String mahnNummer,
                                LocalDate heute,
                                LocalDate neuesFaelligkeitsdatum,
                                long tageUeberfaellig,
                                Map<String, String> ctx)
    {
        KopfdatenDto kopfdaten = new KopfdatenDto(
                mahnNummer,
                heute,
                heute,
                ctx.getOrDefault("KUNDENNAME", ""),
                ctx.getOrDefault("KUNDENADRESSE", ""),
                typLabel + " — Rechnung " + nullSafe(rechnung.getDokumentid()),
                ctx.getOrDefault("KUNDENNUMMER", ""),
                typLabel,
                rechnung.getDokumentid(),
                ctx.getOrDefault("PROJEKTNUMMER", ""),
                ctx.getOrDefault("BAUVORHABEN", ""),
                "Rechnung",
                rechnung.getRechnungsdatum() != null
                        ? rechnung.getRechnungsdatum().format(DATUM_FORMAT) : null,
                null);

        String templateName = ladeTemplateName(typLabel).orElse(null);
        VorlagenDaten vorlage = ladeVorlagenDaten(templateName);
        List<ContentBlockDto> blocks = baueContentBlocks(rechnung, typLabel, templateName,
                tageUeberfaellig, neuesFaelligkeitsdatum, ctx);

        LayoutDto layout = vorlage.formBlocks().isEmpty()
                ? RechnungPdfService.getDefaultLayout()
                : RechnungPdfService.createLayoutFromFormBlocks(vorlage.formBlocks(), 595f, 842f);

        RechnungDto dto = new RechnungDto(
                layout,
                kopfdaten,
                blocks,
                vorlage.formBlocks(),               // Adresse / Datum / Dokumentnummer / Logo …
                null,                                // Schlusstext: kommt aus Textbaustein-Defaults
                vorlage.backgroundImagePage1(),      // Briefkopf-Bild Seite 1
                vorlage.backgroundImagePage2(),      // Briefkopf-Bild Folgeseiten
                null,                                // kein globaler Rabatt
                null,                                // kein Abrechnungsverlauf
                null,                                // kein Betrag-Override — der Summen-Block wird
                                                     // bei Mahnungen ohnehin unterdrückt (RechnungPdfService)
                null);
        return rechnungPdfService.generatePdfBytes(dto);
    }

    /**
     * Setzt Vor- und Nachtexte aus den im Formularwesen zugewiesenen
     * Standard-Textbausteinen für die jeweilige Mahnstufe zusammen — und
     * fügt zwischen ihnen die Forderungs-Box ein. Im Leistungsbereich
     * werden bewusst keine Positionen übernommen.
     *
     * <p>Wenn keine Vorlage zugeordnet ist oder das Laden der Defaults
     * fehlschlägt, fallback auf hartcodierte Anrede + Einleitung + Schluss
     * — damit der Versand auch ohne Konfiguration funktioniert.</p>
     */
    private List<ContentBlockDto> baueContentBlocks(ProjektGeschaeftsdokument rechnung,
                                                     String typLabel,
                                                     String templateName,
                                                     long tageUeberfaellig,
                                                     LocalDate neuesFaelligkeitsdatum,
                                                     Map<String, String> ctx)
    {
        List<ContentBlockDto> result = new ArrayList<>();
        FormularTextbausteinDefaultService.DefaultsForDokumenttyp defaults = null;
        if (templateName != null)
        {
            try
            {
                defaults = formularTextbausteinDefaultService.loadForDokumenttyp(templateName, typLabel);
            }
            catch (Exception e)
            {
                log.warn("Standard-Textbausteine fuer Mahnstufe '{}' konnten nicht geladen werden: {}",
                        typLabel, e.getMessage());
            }
        }

        if (defaults != null && !defaults.vortexte().isEmpty())
        {
            for (Textbaustein tb : defaults.vortexte()) result.add(textbausteinAlsBlock(tb, ctx));
        }
        else
        {
            // Fallback wenn keine Textbausteine: hartkodierter Anrede + Einleitung
            result.add(textBlock(buildAnredeBlock(ctx)));
            result.add(textBlock(buildEinleitungsBlock(typLabel, rechnung, tageUeberfaellig)));
        }

        // Forderungs-Box ist immer dabei — sie ist der Mahn-spezifische Kern
        result.add(textBlock(buildForderungsTabelle(rechnung, tageUeberfaellig, neuesFaelligkeitsdatum)));

        if (defaults != null && !defaults.nachtexte().isEmpty())
        {
            for (Textbaustein tb : defaults.nachtexte()) result.add(textbausteinAlsBlock(tb, ctx));
        }
        else
        {
            result.add(textBlock(buildSchlussBlock(typLabel, neuesFaelligkeitsdatum)));
        }
        return result;
    }

    /**
     * Liefert den Namen der im Formularwesen für die Mahnstufe zugewiesenen
     * Vorlage. Symmetrisch zum {@code AutoAuftragsbestaetigungVersandService}.
     *
     * <p>Fallback auf die Vorlage von "Rechnung", weil Inhaber im Formularwesen
     * typischerweise nur eine globale Briefpapier-Vorlage explizit zuordnen
     * (oft an "Rechnung") und darunter die Default-Textbausteine für alle
     * Dokumenttypen pflegen. Ohne diesen Fallback bleibt das Mahn-PDF ohne
     * Vor-/Nachtexte, weil keine Mahnstufen-spezifische Zuordnung existiert.</p>
     */
    Optional<String> ladeTemplateName(String typLabel)
    {
        Optional<String> direkt = ladeTemplateNameFuer(typLabel);
        if (direkt.isPresent()) return direkt;
        return ladeTemplateNameFuer("Rechnung");
    }

    private Optional<String> ladeTemplateNameFuer(String dokumenttypLabel)
    {
        try
        {
            return formularTemplateService.getPreferredTemplateForDokumenttyp(dokumenttypLabel, null);
        }
        catch (Exception e)
        {
            log.warn("Vorlagenzuordnung fuer '{}' konnte nicht ermittelt werden: {}",
                    dokumenttypLabel, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lädt die Vorlage und parst Briefkopf-Bilder + FormBlocks. Bei Fehlern
     * fallback auf eine leere Vorlage — die PDF wird dann ohne Briefkopf
     * gerendert (Versand-Erfolg vor Optik).
     */
    private VorlagenDaten ladeVorlagenDaten(String templateName)
    {
        if (templateName == null || templateName.isBlank()) return VorlagenDaten.leer();
        try
        {
            FormularTemplateService.NamedTemplateData data = formularTemplateService.loadNamedTemplate(templateName);
            return AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(data.html());
        }
        catch (Exception e)
        {
            log.warn("Vorlage '{}' konnte nicht geladen werden, Mahn-PDF ohne Briefkopf: {}",
                    templateName, e.getMessage());
            return VorlagenDaten.leer();
        }
    }

    private static ContentBlockDto textbausteinAlsBlock(Textbaustein tb, Map<String, String> ctx)
    {
        String html = AutoAuftragsbestaetigungVersandService.aufloesePlatzhalter(
                tb.getHtml() != null ? tb.getHtml() : "", ctx);
        return new ContentBlockDto("TEXT", html, false, 10,
                null, null, null, null, null, null, null, false, null, null);
    }

    private static ContentBlockDto textBlock(String html)
    {
        return new ContentBlockDto("TEXT", html, false, 10,
                null, null, null, null, null, null, null, false, null, null);
    }

    private static String buildAnredeBlock(Map<String, String> ctx)
    {
        String anrede = ctx.getOrDefault("ANREDE", "Sehr geehrte Damen und Herren");
        String name = ctx.getOrDefault("KUNDENNAME", "");
        return "<p>" + anrede + (name.isBlank() ? "" : " " + name) + ",</p>";
    }

    private static String buildEinleitungsBlock(String typLabel,
                                                 ProjektGeschaeftsdokument rechnung,
                                                 long tageUeberfaellig)
    {
        String rechnungsNr = nullSafe(rechnung.getDokumentid());
        String bv = nullSafe(rechnung.getProjekt() != null ? rechnung.getProjekt().getBauvorhaben() : "");
        return switch (typLabel)
        {
            case "Zahlungserinnerung" -> "<p>vermutlich ist es Ihrer Aufmerksamkeit entgangen, "
                    + "dass die Rechnung mit der Nummer <strong>" + rechnungsNr + "</strong>"
                    + (bv.isBlank() ? "" : " für das Bauvorhaben <strong>" + bv + "</strong>")
                    + " seit <strong>" + tageUeberfaellig + " Tagen</strong> "
                    + "überfällig ist und noch nicht beglichen wurde.</p>";
            case "1. Mahnung" -> "<p>trotz unserer Zahlungserinnerung haben wir bisher keinen "
                    + "Zahlungseingang für die Rechnung <strong>" + rechnungsNr + "</strong>"
                    + (bv.isBlank() ? "" : " (Bauvorhaben <strong>" + bv + "</strong>)")
                    + " feststellen können. Die Rechnung ist seit <strong>"
                    + tageUeberfaellig + " Tagen</strong> überfällig.</p>";
            case "2. Mahnung" -> "<p>leider mussten wir feststellen, dass die Rechnung "
                    + "<strong>" + rechnungsNr + "</strong>"
                    + (bv.isBlank() ? "" : " für das Bauvorhaben <strong>" + bv + "</strong>")
                    + " auch nach unserer 1. Mahnung noch immer nicht beglichen wurde "
                    + "(<strong>" + tageUeberfaellig + " Tage</strong> überfällig).</p>";
            default -> "<p>Die Rechnung " + rechnungsNr + " ist seit " + tageUeberfaellig
                    + " Tagen überfällig.</p>";
        };
    }

    private static String buildForderungsTabelle(ProjektGeschaeftsdokument rechnung,
                                                  long tageUeberfaellig,
                                                  LocalDate neuesFaelligkeitsdatum)
    {
        String betrag = formatBetrag(rechnung.getBruttoBetrag());
        String rdat = rechnung.getRechnungsdatum() != null
                ? rechnung.getRechnungsdatum().format(DATUM_FORMAT) : "—";
        String fdat = rechnung.getFaelligkeitsdatum() != null
                ? rechnung.getFaelligkeitsdatum().format(DATUM_FORMAT) : "—";
        return "<p><strong>Offene Forderung:</strong><br>"
                + "Rechnungsnummer: <strong>" + nullSafe(rechnung.getDokumentid()) + "</strong><br>"
                + "Rechnungsdatum: " + rdat + "<br>"
                + "Ursprüngliches Fälligkeitsdatum: " + fdat + "<br>"
                + "Tage überfällig: <strong>" + tageUeberfaellig + "</strong><br>"
                + "Offener Betrag: <strong>" + betrag + "</strong><br>"
                + "Neues Zahlungsziel: <strong>" + neuesFaelligkeitsdatum.format(DATUM_FORMAT)
                + "</strong></p>";
    }

    private static String buildSchlussBlock(String typLabel, LocalDate neuesFaelligkeitsdatum)
    {
        String datum = neuesFaelligkeitsdatum.format(DATUM_FORMAT);
        return switch (typLabel)
        {
            case "Zahlungserinnerung" -> "<p>Bitte überweisen Sie den ausstehenden Betrag bis zum "
                    + "<strong>" + datum + "</strong>. Sollte sich Ihre Zahlung mit dieser Erinnerung "
                    + "überschnitten haben, betrachten Sie diese E-Mail bitte als gegenstandslos.</p>";
            case "1. Mahnung" -> "<p>Wir bitten Sie, den ausstehenden Betrag umgehend, spätestens bis zum "
                    + "<strong>" + datum + "</strong> zu überweisen, um zusätzliche Mahngebühren "
                    + "zu vermeiden.</p>";
            case "2. Mahnung" -> "<p>Wir fordern Sie hiermit letztmalig auf, den ausstehenden Betrag "
                    + "bis zum <strong>" + datum + "</strong> zu überweisen. Andernfalls sehen wir uns "
                    + "gezwungen, die Forderung an ein Inkassobüro zu übergeben oder gerichtliche "
                    + "Schritte einzuleiten. Die dadurch entstehenden Kosten gehen zu Ihren Lasten.</p>";
            default -> "<p>Bitte überweisen Sie bis zum <strong>" + datum + "</strong>.</p>";
        };
    }

    // ======================= Kontext / Helpers =======================

    private static Map<String, String> bauePlatzhalterKontext(ProjektGeschaeftsdokument rechnung,
                                                                Mahnstufe stufe,
                                                                String typLabel,
                                                                String mahnNummer,
                                                                LocalDate heute,
                                                                LocalDate neuesFaelligkeitsdatum,
                                                                long tageUeberfaellig)
    {
        Map<String, String> ctx = new HashMap<>();
        Projekt projekt = rechnung.getProjekt();
        Kunde kunde = projekt != null ? projekt.getKundenId() : null;

        // Mahn-spezifische Tokens
        ctx.put("MAHNNUMMER", mahnNummer);
        ctx.put("MAHNSTUFE", typLabel);
        ctx.put("NEUES_FAELLIGKEITSDATUM", neuesFaelligkeitsdatum.format(DATUM_FORMAT));
        ctx.put("TAGE_UEBERFAELLIG", String.valueOf(tageUeberfaellig));

        // --- Superset des Auto-AB-Kontexts ---
        // Wenn der Inhaber die Mahn-Vorlage einfach von der AB übernimmt (oder
        // dieselben Standard-Textbausteine recycled), müssen alle dort üblichen
        // Platzhalter aufgelöst werden — sonst tauchen {{ZAHLUNGSZIEL}} oder
        // {{ANSPRECHPARTNER}} im fertigen PDF roh auf.
        //
        // Wichtig: DOKUMENTNUMMER zeigt bei Mahnungen auf die ANGEMAHNTE
        // Original-Rechnungsnummer, weil die V250-Mail-Templates dort die
        // Rechnungsnummer erwarten ("die Rechnung mit der Nummer
        // {{DOKUMENTNUMMER}}"). Die interne Mahnnummer steckt in {{MAHNNUMMER}}.
        ctx.put("DOKUMENTNUMMER", nullSafe(rechnung.getDokumentid()));
        ctx.put("RECHNUNGSNUMMER", nullSafe(rechnung.getDokumentid()));
        ctx.put("DOKUMENTTYP", typLabel);
        ctx.put("DATUM", heute.format(DATUM_FORMAT));
        ctx.put("BETRAG", formatBetrag(rechnung.getBruttoBetrag()));
        ctx.put("BETREFF", typLabel + " — Rechnung " + nullSafe(rechnung.getDokumentid()));
        // Zahlungsziel = neue Mahn-Frist (so verstanden der Empfänger das Wort)
        long zielTage = ChronoUnit.DAYS.between(heute, neuesFaelligkeitsdatum);
        ctx.put("ZAHLUNGSZIEL_TAGE", String.valueOf(Math.max(0, zielTage)));
        ctx.put("ZAHLUNGSZIEL", neuesFaelligkeitsdatum.format(DATUM_FORMAT));
        ctx.put("RECHNUNGSDATUM", rechnung.getRechnungsdatum() != null
                ? rechnung.getRechnungsdatum().format(DATUM_FORMAT) : "");
        // Original-Fälligkeit der angemahnten Rechnung
        ctx.put("FAELLIGKEITSDATUM", rechnung.getFaelligkeitsdatum() != null
                ? rechnung.getFaelligkeitsdatum().format(DATUM_FORMAT) : "");

        // Bezug = die offene Original-Rechnung
        ctx.put("BEZUGSDOKUMENT", nullSafe(rechnung.getDokumentid()));
        ctx.put("BEZUGSDOKUMENTNUMMER", nullSafe(rechnung.getDokumentid()));
        ctx.put("BEZUGSDOKUMENTTYP", "Rechnung");
        ctx.put("BEZUGSDOKUMENTDATUM", rechnung.getRechnungsdatum() != null
                ? rechnung.getRechnungsdatum().format(DATUM_FORMAT) : "");

        if (projekt != null)
        {
            ctx.put("PROJEKTNUMMER", nullSafe(projekt.getAuftragsnummer()));
            ctx.put("BAUVORHABEN", nullSafe(projekt.getBauvorhaben()));
        }
        else
        {
            ctx.put("PROJEKTNUMMER", "");
            ctx.put("BAUVORHABEN", "");
        }
        if (kunde != null)
        {
            ctx.put("KUNDENNAME", nullSafe(kunde.getName()));
            ctx.put("KUNDENNUMMER", nullSafe(kunde.getKundennummer()));
            ctx.put("KUNDENADRESSE", nullSafe(baueAdresseAusKunde(kunde)));
            ctx.put("ANSPRECHPARTNER", nullSafe(kunde.getAnsprechspartner()));
            ctx.put("ANREDE", kunde.getAnrede() != null
                    ? kunde.getAnrede().toAnredeText() : "Sehr geehrte Damen und Herren");
        }
        else
        {
            ctx.put("KUNDENNAME", "");
            ctx.put("KUNDENNUMMER", "");
            ctx.put("KUNDENADRESSE", "");
            ctx.put("ANSPRECHPARTNER", "");
            ctx.put("ANREDE", "Sehr geehrte Damen und Herren");
        }
        Objects.requireNonNull(stufe); // wird im Logging genutzt
        return ctx;
    }

    private static String labelFuer(Mahnstufe stufe)
    {
        return switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "Zahlungserinnerung";
            case ERSTE_MAHNUNG -> "1. Mahnung";
            case ZWEITE_MAHNUNG -> "2. Mahnung";
        };
    }

    /**
     * Bildet eine eindeutige Mahnnummer aus der Original-Rechnungsnummer und
     * einem Stufen-Suffix. Beispiel: "RE-2026/05/0042-Z" / "-M1" / "-M2".
     */
    private static String generiereMahnNummer(ProjektGeschaeftsdokument rechnung, Mahnstufe stufe)
    {
        String basis = nullSafe(rechnung.getDokumentid());
        String suffix = switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "-Z";
            case ERSTE_MAHNUNG -> "-M1";
            case ZWEITE_MAHNUNG -> "-M2";
        };
        return basis + suffix;
    }

    private static String baueAdresseAusKunde(Kunde kunde)
    {
        StringBuilder sb = new StringBuilder();
        if (kunde.getName() != null && !kunde.getName().isBlank()) sb.append(kunde.getName());
        if (kunde.getStrasse() != null && !kunde.getStrasse().isBlank())
        {
            if (sb.length() > 0) sb.append("\n");
            sb.append(kunde.getStrasse());
        }
        boolean plzOrt = (kunde.getPlz() != null && !kunde.getPlz().isBlank())
                || (kunde.getOrt() != null && !kunde.getOrt().isBlank());
        if (plzOrt)
        {
            if (sb.length() > 0) sb.append("\n");
            if (kunde.getPlz() != null) sb.append(kunde.getPlz()).append(" ");
            if (kunde.getOrt() != null) sb.append(kunde.getOrt());
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    private String ermittleAbsenderAdresse()
    {
        // Konfigurierbar im Firma-Editor → System-Setup; Fallback auf SMTP-User.
        return systemSettingsService.getMailFromAddress();
    }

    private static String formatBetrag(BigDecimal betrag)
    {
        if (betrag == null) return "";
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        return nf.format(betrag);
    }

    private static String sanitize(String input)
    {
        if (input == null) return "Mahnung";
        return input.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
