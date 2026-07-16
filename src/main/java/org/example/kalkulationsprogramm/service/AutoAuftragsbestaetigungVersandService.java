package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.LayoutDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Versendet eine Auftragsbestätigung automatisch per E-Mail an den Kunden,
 * sobald dieser ein Angebot digital angenommen hat. Die AB ist zu diesem
 * Zeitpunkt bereits als Folgedokument erzeugt (siehe
 * {@link DokumentFreigabeService#erzeugeAutoAuftragsbestaetigungWennAngebot}).
 *
 * <p>Layout-Quelle: Die im Formularwesen für "Auftragsbestätigung"
 * zugewiesene Vorlage wird geladen und parsed; daraus werden Briefkopf-
 * Hintergrundbild und {@link FormBlockDto}s (Adresse, Datum, Dokumentnummer,
 * Logo etc.) extrahiert und an {@link RechnungPdfService} übergeben.
 * Damit ist die PDF visuell identisch zum manuellen Export aus dem
 * DocumentEditor.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoAuftragsbestaetigungVersandService
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RechnungPdfService rechnungPdfService;
    private final SystemSettingsService systemSettingsService;
    private final EmailTextTemplateService emailTextTemplateService;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    private final FormularTemplateService formularTemplateService;
    private final FormularTextbausteinDefaultService formularTextbausteinDefaultService;
    private final EmailSignatureService emailSignatureService;
    private final ProjektEmailArchivService projektEmailArchivService;
    private final DokumentFreigabeRepository dokumentFreigabeRepository;

    /**
     * Einstieg fuer den asynchronen Versand NACH dem Commit der
     * Annahme-Transaktion (siehe {@link DokumentFreigabeService}). Laedt AB und
     * Freigabe frisch aus der DB, weil die Entities des Annahme-Requests nach
     * dem Commit detached sind und Lazy-Zugriffe (Projekt, Kunde) sonst
     * scheitern wuerden. Die eigene Transaktion haelt die Session fuer die
     * PDF-Generierung offen.
     */
    @Transactional
    public boolean versendeNachAnnahme(Long abId, String empfaenger, String freigabeUuid)
    {
        AusgangsGeschaeftsDokument ab = ausgangsGeschaeftsDokumentRepository.findById(abId).orElse(null);
        if (ab == null)
        {
            log.warn("Auto-AB-Versand uebersprungen: Dokument {} nicht mehr vorhanden", abId);
            return false;
        }
        DokumentFreigabe freigabe = freigabeUuid == null ? null
                : dokumentFreigabeRepository.findByUuid(freigabeUuid).orElse(null);
        return versende(ab, empfaenger, freigabe);
    }

    /**
     * Versendet die Auftragsbestätigung als PDF-Mail. Fehler werden geloggt
     * und nicht propagiert — die Annahme darf an einem SMTP-Ausfall nicht scheitern.
     *
     * @return {@code true}, wenn der Versand erfolgreich war.
     */
    public boolean versende(AusgangsGeschaeftsDokument ab, String empfaenger)
    {
        return versende(ab, empfaenger, null);
    }

    /**
     * Wie {@link #versende(AusgangsGeschaeftsDokument, String)}, aber zusätzlich mit
     * Annahme-Beleg im HTML-Body. Wird nach digitaler Angebotsannahme aufgerufen — die
     * Mail enthält dann denselben optisch hervorgehobenen Block wie die Angebots-Mail
     * (linker Akzent-Border, hellgrauer Hintergrund), aber mit dem Text "Angebot
     * angenommen — diese Auftragsbestätigung wurde automatisch erstellt" plus
     * Annahme-Datum und Audit-Hash als Beweis.
     */
    public boolean versende(AusgangsGeschaeftsDokument ab, String empfaenger, DokumentFreigabe freigabe)
    {
        if (ab == null || ab.getTyp() != AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
        {
            return false;
        }
        if (empfaenger == null || empfaenger.isBlank())
        {
            log.warn("Auto-AB-Versand übersprungen: kein Empfänger für AB {}", ab.getDokumentNummer());
            return false;
        }

        Path tempPdf = null;
        try
        {
            byte[] pdfBytes = generierePdfFuerAB(ab);
            String filename = "Auftragsbestaetigung_" + sanitizeForFilename(ab.getDokumentNummer()) + ".pdf";
            tempPdf = Files.createTempFile("auto-ab-", ".pdf");
            Files.write(tempPdf, pdfBytes);

            EmailService.EmailContent content = baueEmailInhalt(ab);
            if (freigabe != null)
            {
                content = mitAnnahmeBeleg(content, freigabe);
            }

            EmailService emailService = new EmailService(
                    systemSettingsService.getSmtpHost(),
                    systemSettingsService.getSmtpPort(),
                    systemSettingsService.getSmtpUsername(),
                    systemSettingsService.getSmtpPassword());

            String absender = ermittleAbsenderAdresse();
            String htmlMitSignatur = emailSignatureService
                    .appendSystemSignatureIfConfigured(content.htmlBody());
            String messageId = emailService.sendEmailAndReturnMessageId(
                    empfaenger,
                    null,
                    absender,
                    content.subject(),
                    htmlMitSignatur,
                    tempPdf.toString(),
                    filename);

            // Erst archivieren, dann Versanddatum setzen: die Archivierung kann
            // bei Lock-Kontention bis zu ~6s Retry-Pausen schlafen — waere das
            // Versanddatum schon geschrieben, hielte die umgebende
            // versendeNachAnnahme-Tx waehrenddessen einen Write-Lock auf der
            // AB-Zeile. So schlaeft der Retry, ohne Row-Locks zu halten (die
            // Tx-Connection bleibt belegt — bewusster, seltener Trade-off).
            archiviereAlsProjektEmail(ab, empfaenger, absender,
                    content.subject(), htmlMitSignatur, messageId, tempPdf, filename);
            markiereAlsVersendet(ab);
            // Bewusst ohne Empfaenger-Adresse geloggt (DSGVO) — die
            // Dokumentnummer reicht zum Wiederfinden im E-Mail-Center.
            log.info("Auto-AB {} per Mail versendet", ab.getDokumentNummer());
            return true;
        }
        catch (Exception e)
        {
            log.error("Auto-AB-Versand für {} fehlgeschlagen: {}", ab.getDokumentNummer(), e.getMessage(), e);
            return false;
        }
        finally
        {
            if (tempPdf != null)
            {
                try { Files.deleteIfExists(tempPdf); } catch (IOException ignored) { /* temp-Datei ggf. schon weg */ }
            }
        }
    }

    /**
     * Anzahl Archivierungsversuche + Basis-Pause (waechst linear pro Versuch).
     * Der IMAP-Import-Job kann Locks auf der email-Tabelle halten (in Produktion
     * beobachteter "Lock wait timeout" beim Funnel-Pendant) — ohne Retry ginge
     * der E-Mail-Center-Eintrag samt PDF dauerhaft verloren, obwohl die
     * SMTP-Mail beim Kunden ist. Pause paketsichtbar fuer Tests.
     */
    private static final int MAX_ARCHIV_VERSUCHE = 3;
    long archivRetryPauseMillis = 2_000L;

    /**
     * Archiviert die versendete AB-Mail samt PDF im E-Mail-Center des Projekts
     * — gleicher Weg wie die automatischen Mahnungen. Ohne diesen Eintrag
     * waere die Mail im ERP unsichtbar: der IMAP-Sent-Poll ist kein
     * Sicherheitsnetz, T-Online legt SMTP-versendete Mails nicht zuverlaessig
     * in INBOX.Sent ab (in Produktion nachgewiesen, siehe
     * {@link AnfrageBestaetigungVersandService}).
     *
     * <p>Bei Lock-/Commit-Konflikten wird bis zu {@value #MAX_ARCHIV_VERSUCHE}-mal
     * wiederholt (die Archivierung laeuft in REQUIRES_NEW, ein gescheiterter
     * Versuch hinterlaesst keine halbe Zeile). Nach dem letzten Versuch wird
     * geschluckt und geloggt: die SMTP-Mail ist bereits beim Kunden, ein
     * Archivierungsfehler darf den Annahme-Flow nicht kippen.</p>
     *
     * <p>Paketsichtbar, damit der Unit-Test die Retry-Logik direkt pruefen
     * kann, ohne den vollen Versand-Pfad (PDF + echter SMTP) aufzubauen.</p>
     */
    void archiviereAlsProjektEmail(AusgangsGeschaeftsDokument ab, String empfaenger, String absender,
            String subject, String htmlBody, String messageId, Path pdf, String dateiname)
    {
        if (ab.getProjekt() == null)
        {
            log.warn("Auto-AB {} versendet, aber ohne Projekt — Mail kann nicht im E-Mail-Center archiviert werden",
                    ab.getDokumentNummer());
            return;
        }
        for (int versuch = 1; versuch <= MAX_ARCHIV_VERSUCHE; versuch++)
        {
            try
            {
                projektEmailArchivService.archiviereVersandteEmail(
                        ab.getProjekt(), empfaenger, absender, subject, htmlBody, messageId, pdf, dateiname);
                return;
            }
            catch (Exception e)
            {
                if (versuch == MAX_ARCHIV_VERSUCHE)
                {
                    log.error("Auto-AB {} versendet, aber Archivierung im E-Mail-Center nach {} Versuchen fehlgeschlagen: {}",
                            ab.getDokumentNummer(), versuch, e.getMessage(), e);
                    return;
                }
                log.warn("Auto-AB {} Archivierung fehlgeschlagen (Versuch {}/{}): {} — neuer Versuch in {}ms",
                        ab.getDokumentNummer(), versuch, MAX_ARCHIV_VERSUCHE, e.getMessage(),
                        archivRetryPauseMillis * versuch);
                try
                {
                    Thread.sleep(archivRetryPauseMillis * versuch);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Transactional
    protected void markiereAlsVersendet(AusgangsGeschaeftsDokument ab)
    {
        AusgangsGeschaeftsDokument frisch = ausgangsGeschaeftsDokumentRepository.findById(ab.getId()).orElse(null);
        if (frisch == null) return;
        frisch.setVersandDatum(LocalDate.now());
        ausgangsGeschaeftsDokumentRepository.save(frisch);
    }

    // ======================= PDF =======================

    private byte[] generierePdfFuerAB(AusgangsGeschaeftsDokument ab)
    {
        KopfdatenDto kopfdaten = buildKopfdaten(ab);
        String templateName = ladeTemplateName(ab).orElse(null);
        List<ContentBlockDto> contentBlocks = baueContentBlocks(ab, templateName);
        VorlagenDaten vorlage = ladeVorlagenDaten(templateName);
        return buildPdfBytes(ab, kopfdaten, contentBlocks, vorlage);
    }

    /**
     * Setzt die finalen ContentBlocks zusammen: Vortexte aus den Formularwesen-
     * Defaults → Inhalte/Positionen aus {@code positionenJson} → Nachtexte aus
     * den Defaults. Damit landen Anrede, Liefer- und Zahlungsbedingungen, Schluss-
     * formel automatisch in der Auto-AB — analog zum DocumentEditor.
     */
    private List<ContentBlockDto> baueContentBlocks(AusgangsGeschaeftsDokument ab, String templateName)
    {
        List<ContentBlockDto> kern = parsePositionenJsonZuContentBlocks(ab.getPositionenJson());
        if (templateName == null) return kern;

        FormularTextbausteinDefaultService.DefaultsForDokumenttyp defaults;
        try
        {
            defaults = formularTextbausteinDefaultService.loadForDokumenttyp(templateName, "Auftragsbestätigung");
        }
        catch (Exception e)
        {
            log.warn("Standard-Textbausteine für AB konnten nicht geladen werden: {}", e.getMessage());
            return kern;
        }

        Map<String, String> ctx = bauePlatzhalterKontext(ab);
        List<ContentBlockDto> result = new ArrayList<>();
        for (Textbaustein tb : defaults.vortexte()) result.add(textbausteinAlsBlock(tb, ctx));
        // Auch Freitexte aus dem positionenJson können Platzhalter enthalten
        // (z.B. wenn der Sachbearbeiter im DocumentEditor TEXT-Blöcke mit
        // {{KUNDENNAME}} oder {{BEZUGSDOKUMENTNUMMER}} eingefügt hat).
        for (ContentBlockDto kb : kern) result.add(loeseBlockAuf(kb, ctx));
        for (Textbaustein tb : defaults.nachtexte()) result.add(textbausteinAlsBlock(tb, ctx));
        return result;
    }

    /**
     * Wendet die Platzhalter-Auflösung auf alle text-tragenden Felder eines
     * {@link ContentBlockDto} an: {@code text} (TEXT-Block), {@code beschreibung}
     * (SERVICE-Titel) und {@code beschreibungHtml} (SERVICE-Beschreibung).
     */
    private static ContentBlockDto loeseBlockAuf(ContentBlockDto b, Map<String, String> ctx)
    {
        return new ContentBlockDto(
                b.type(),
                aufloesePlatzhalter(b.text(), ctx),
                b.fett(),
                b.fontSize(),
                b.pos(),
                aufloesePlatzhalter(b.beschreibung(), ctx),
                aufloesePlatzhalter(b.beschreibungHtml(), ctx),
                b.menge(),
                b.einheit(),
                b.einzelpreis(),
                b.gesamt(),
                b.optional(),
                aufloesePlatzhalter(b.sectionLabel(), ctx),
                b.rabattProzent());
    }

    private static ContentBlockDto textbausteinAlsBlock(Textbaustein tb, Map<String, String> ctx)
    {
        String html = aufloesePlatzhalter(tb.getHtml() != null ? tb.getHtml() : "", ctx);
        return new ContentBlockDto("TEXT", html, false, 10,
                null, null, null, null, null, null, null, false, null, null);
    }

    /**
     * Setzt {@code {{TOKEN}}}-Platzhalter im HTML auf. Wert-Lookup ist
     * case-insensitive (Token wird auf UPPERCASE normalisiert), unbekannte
     * Tokens bleiben unverändert stehen — symmetrisch zum Frontend
     * {@code DocumentEditor.replacePlaceholders}.
     */
    static String aufloesePlatzhalter(String text, Map<String, String> ctx)
    {
        if (text == null || text.isEmpty()) return text;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_äöüÄÖÜß]+)\\s*\\}\\}");
        java.util.regex.Matcher m = p.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find())
        {
            String key = m.group(1).toUpperCase(Locale.GERMAN);
            String value = ctx.get(key);
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Baut den Platzhalter-Kontext für die Inhalts-Auflösung. Werte werden in
     * der gleichen Schreibweise hinterlegt wie der Frontend-DocumentEditor sie
     * erwartet (siehe {@code react-pc-frontend/src/components/document-editor/index.tsx}).
     */
    private Map<String, String> bauePlatzhalterKontext(AusgangsGeschaeftsDokument ab)
    {
        Map<String, String> ctx = new HashMap<>();
        Kunde kunde = effektiverKunde(ab);
        AusgangsGeschaeftsDokument vorgaenger = ab.getVorgaenger();

        ctx.put("DOKUMENTNUMMER", nullSafe(ab.getDokumentNummer()));
        ctx.put("RECHNUNGSNUMMER", nullSafe(ab.getDokumentNummer()));
        ctx.put("DOKUMENTTYP", "Auftragsbestätigung");
        ctx.put("DATUM", ab.getDatum() != null ? ab.getDatum().format(DATUM_FORMAT) : "");
        ctx.put("BETREFF", nullSafe(ab.getBetreff()));
        ctx.put("BAUVORHABEN", nullSafe(ermittleBauvorhaben(ab)));
        ctx.put("PROJEKTNUMMER", nullSafe(ermittleProjektnummer(ab)));
        ctx.put("ZAHLUNGSZIEL_TAGE", ab.getZahlungszielTage() != null ? ab.getZahlungszielTage().toString() : "8");
        ctx.put("ZAHLUNGSZIEL", berechneZahlungszielDatum(ab));

        if (kunde != null)
        {
            ctx.put("KUNDENNAME", nullSafe(kunde.getName()));
            ctx.put("KUNDENNUMMER", nullSafe(kunde.getKundennummer()));
            ctx.put("KUNDENADRESSE", nullSafe(baueAdresseAusKunde(kunde)));
            ctx.put("ANSPRECHPARTNER", nullSafe(kunde.getAnsprechspartner()));
            ctx.put("ANREDE", kunde.getAnrede() != null ? kunde.getAnrede().toAnredeText() : "Sehr geehrte Damen und Herren");
        }
        else
        {
            ctx.put("ANREDE", "Sehr geehrte Damen und Herren");
        }

        if (vorgaenger != null)
        {
            ctx.put("BEZUGSDOKUMENT", nullSafe(vorgaenger.getDokumentNummer()));
            ctx.put("BEZUGSDOKUMENTNUMMER", nullSafe(vorgaenger.getDokumentNummer()));
            ctx.put("BEZUGSDOKUMENTTYP", vorgaenger.getTyp() != null ? typLabel(vorgaenger.getTyp()) : "");
            ctx.put("BEZUGSDOKUMENTDATUM", vorgaenger.getDatum() != null ? vorgaenger.getDatum().format(DATUM_FORMAT) : "");
        }
        return ctx;
    }

    private static String typLabel(AusgangsGeschaeftsDokumentTyp typ)
    {
        return switch (typ) {
            case ANGEBOT -> "Angebot";
            case NACHTRAGSANGEBOT -> "Nachtragsangebot";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestätigung";
            case RECHNUNG -> "Rechnung";
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case STORNO -> "Stornorechnung";
            default -> typ.name();
        };
    }

    private static String berechneZahlungszielDatum(AusgangsGeschaeftsDokument ab)
    {
        int tage = ab.getZahlungszielTage() != null ? ab.getZahlungszielTage() : 8;
        LocalDate basis = ab.getDatum() != null ? ab.getDatum() : LocalDate.now();
        return basis.plusDays(tage).format(DATUM_FORMAT);
    }

    private static Kunde effektiverKunde(AusgangsGeschaeftsDokument ab)
    {
        if (ab.getKunde() != null) return ab.getKunde();
        if (ab.getProjekt() != null && ab.getProjekt().getKundenId() != null) return ab.getProjekt().getKundenId();
        if (ab.getAnfrage() != null && ab.getAnfrage().getKunde() != null) return ab.getAnfrage().getKunde();
        return null;
    }

    private static String ermittleBauvorhaben(AusgangsGeschaeftsDokument ab)
    {
        if (ab.getProjekt() != null && ab.getProjekt().getBauvorhaben() != null) return ab.getProjekt().getBauvorhaben();
        if (ab.getAnfrage() != null && ab.getAnfrage().getBauvorhaben() != null) return ab.getAnfrage().getBauvorhaben();
        return ab.getBetreff();
    }

    private static String ermittleProjektnummer(AusgangsGeschaeftsDokument ab)
    {
        if (ab.getProjekt() != null && ab.getProjekt().getAuftragsnummer() != null) return ab.getProjekt().getAuftragsnummer();
        // Fallback bei Anfrage-AB: Vorgänger-Dokumentnummer als Bezug.
        if (ab.getAnfrage() != null) return "—";
        return "";
    }

    private byte[] buildPdfBytes(AusgangsGeschaeftsDokument ab, KopfdatenDto kopfdaten,
                                  List<ContentBlockDto> blocks, VorlagenDaten vorlage)
    {
        // Wenn die Vorlage einen "table"-FormBlock liefert, leiten wir Content-
        // Bereich + Header/Footer-Rechtecke daraus ab — analog zum Frontend
        // (DocumentEditor → DokumentGeneratorController). Sonst Fallback auf
        // das Standard-Layout (kein Briefkopf konfiguriert).
        LayoutDto layout = vorlage.formBlocks().isEmpty()
                ? RechnungPdfService.getDefaultLayout()
                : RechnungPdfService.createLayoutFromFormBlocks(vorlage.formBlocks(), 595f, 842f);
        RechnungDto dto = new RechnungDto(
                layout,
                kopfdaten,
                blocks,
                vorlage.formBlocks,           // Adresse / Datum / Dokumentnummer / Logo … aus der Vorlage
                null,                          // Schlusstext: kommt über Textbausteine im positionenJson
                vorlage.backgroundImagePage1,  // Briefkopf-Bild Seite 1
                vorlage.backgroundImagePage2,  // Briefkopf-Bild Folgeseiten
                null,                          // globaler Rabatt
                null,                          // Abrechnungsverlauf
                ab.getBetragNetto(),           // expliziter Netto-Betrag → AB übernimmt den Wert vom Angebot
                null);
        return rechnungPdfService.generatePdfBytes(dto);
    }

    /**
     * Liefert den Namen der im Formularwesen für "Auftragsbestätigung"
     * zugewiesenen Vorlage. Dient sowohl zum Laden des Layouts als auch der
     * Standard-Textbausteine — beides muss aus derselben Vorlage stammen.
     *
     * <p>Fallback-Kette (von explizit zu implizit), damit Auto-AB nach
     * digitaler Annahme auch dann mit Vor-/Nachtexten rendert, wenn der
     * Inhaber im Formularwesen nur eine globale Vorlage zugewiesen hat:
     * <ol>
     *   <li>Vorlage explizit für "Auftragsbestätigung" zugewiesen</li>
     *   <li>Vorlage des Vorgängers (typischerweise "Angebot") — daraus werden
     *       die AB-Defaults gelesen, falls in dieser Vorlage gepflegt</li>
     * </ol>
     * Die eigentlichen Default-Textbausteine werden anschliessend immer für
     * "Auftragsbestätigung" geladen — die Vorlage wirkt nur als Adresse,
     * unter der die Defaults persistiert sind.</p>
     */
    Optional<String> ladeTemplateName(AusgangsGeschaeftsDokument ab)
    {
        Optional<String> direkt = ladeTemplateNameFuer("Auftragsbestätigung");
        if (direkt.isPresent()) return direkt;
        if (ab != null && ab.getVorgaenger() != null && ab.getVorgaenger().getTyp() != null)
        {
            return ladeTemplateNameFuer(typLabel(ab.getVorgaenger().getTyp()));
        }
        return Optional.empty();
    }

    private Optional<String> ladeTemplateNameFuer(String dokumenttypLabel)
    {
        try { return formularTemplateService.getPreferredTemplateForDokumenttyp(dokumenttypLabel, null); }
        catch (Exception e)
        {
            log.warn("Vorlagenzuordnung für '{}' konnte nicht ermittelt werden: {}",
                    dokumenttypLabel, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lädt die Vorlage zum gegebenen Namen und extrahiert daraus alle für die
     * PDF-Erzeugung nötigen Layout-Daten: Hintergrundbilder (Seite 1, Folgeseiten)
     * und {@link FormBlockDto}s mit den exakten Pixel-Positionen aller Felder
     * (Adresse, Datum, Logo etc.).
     *
     * <p>Wenn {@code templateName} {@code null} ist oder das Laden fehlschlägt,
     * wird eine leere {@link VorlagenDaten}-Instanz zurückgegeben — die PDF
     * wird dann mit dem Standard-Briefkopf gerendert (Versand-Erfolg vor Optik).</p>
     */
    private VorlagenDaten ladeVorlagenDaten(String templateName)
    {
        if (templateName == null || templateName.isBlank()) return VorlagenDaten.leer();
        try
        {
            FormularTemplateService.NamedTemplateData data = formularTemplateService.loadNamedTemplate(templateName);
            return parseVorlagenHtml(data.html());
        }
        catch (Exception e)
        {
            log.warn("Vorlage '{}' konnte nicht geladen werden, fallback auf Standard-Briefkopf: {}",
                    templateName, e.getMessage());
            return VorlagenDaten.leer();
        }
    }

    /**
     * Parst das gespeicherte Vorlagen-HTML (siehe
     * {@code FormularwesenMain.serialize}) in ein Hintergrundbild-Paar plus
     * eine {@link FormBlockDto}-Liste. Das Format ist symmetrisch zum
     * Frontend: jeder Block ist ein {@code <div>} mit {@code data-*}-Attributen,
     * Hintergründe stecken in {@code <meta name="background-image[-page2]">}.
     */
    static VorlagenDaten parseVorlagenHtml(String html)
    {
        if (html == null || html.isBlank()) return VorlagenDaten.leer();
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        String bg1 = parseMetaContent(doc, "background-image");
        String bg2 = parseMetaContent(doc, "background-image-page2");
        List<FormBlockDto> blocks = new ArrayList<>();
        for (Element el : doc.select("[data-block-type]"))
        {
            FormBlockDto block = parseFormBlockElement(el);
            if (block != null) blocks.add(block);
        }
        return new VorlagenDaten(bg1, bg2, blocks);
    }

    private static String parseMetaContent(org.jsoup.nodes.Document doc, String name)
    {
        Element meta = doc.selectFirst("meta[name=" + name + "]");
        if (meta == null) return null;
        String content = meta.attr("content");
        if (content == null || content.isBlank()) return null;
        return urlDecode(content);
    }

    private static FormBlockDto parseFormBlockElement(Element el)
    {
        String type = el.attr("data-block-type");
        if (type == null || type.isBlank()) return null;
        String id = el.id();
        if (id == null || id.isBlank()) id = type + "_" + Math.abs(el.hashCode());
        int page = parseInt(el.attr("data-page"), 1);
        float x = parseFloat(el.attr("data-x"), 0f);
        float y = parseFloat(el.attr("data-y"), 0f);
        float width = parseFloat(el.attr("data-width"), 0f);
        float height = parseFloat(el.attr("data-height"), 0f);
        String content = el.hasAttr("data-content") ? urlDecode(el.attr("data-content")) : null;
        Map<String, Object> styles = parseStyleAttribute(el.attr("data-style"));
        return new FormBlockDto(id, type, page, x, y, width, height, content, styles);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseStyleAttribute(String raw)
    {
        if (raw == null || raw.isBlank()) return Map.of();
        try
        {
            String json = urlDecode(raw);
            return OBJECT_MAPPER.readValue(json, Map.class);
        }
        catch (Exception e)
        {
            return Map.of();
        }
    }

    private static String urlDecode(String s)
    {
        if (s == null) return null;
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return s; }
    }

    private static int parseInt(String s, int fallback)
    {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static float parseFloat(String s, float fallback)
    {
        try { return Float.parseFloat(s); } catch (Exception e) { return fallback; }
    }

    /**
     * Container für die aus der Formularwesen-Vorlage geladenen Layout-Daten.
     */
    record VorlagenDaten(String backgroundImagePage1, String backgroundImagePage2, List<FormBlockDto> formBlocks)
    {
        static VorlagenDaten leer() { return new VorlagenDaten(null, null, List.of()); }
    }

    private KopfdatenDto buildKopfdaten(AusgangsGeschaeftsDokument ab)
    {
        Kunde kunde = effektiverKunde(ab);
        String kundenAdresse = ab.getRechnungsadresseOverride();
        if ((kundenAdresse == null || kundenAdresse.isBlank()) && kunde != null)
        {
            kundenAdresse = baueAdresseAusKunde(kunde);
        }

        return new KopfdatenDto(
                ab.getDokumentNummer(),
                ab.getDatum() != null ? ab.getDatum() : LocalDate.now(),
                ab.getDatum() != null ? ab.getDatum() : LocalDate.now(),
                kunde != null ? kunde.getName() : null,
                kundenAdresse,
                ab.getBetreff(),
                kunde != null ? kunde.getKundennummer() : null,
                "Auftragsbestätigung",
                ab.getVorgaenger() != null ? ab.getVorgaenger().getDokumentNummer() : null,
                ermittleProjektnummer(ab),
                ermittleBauvorhaben(ab),
                ab.getVorgaenger() != null && ab.getVorgaenger().getTyp() != null
                        ? typLabel(ab.getVorgaenger().getTyp()) : null,
                ab.getVorgaenger() != null && ab.getVorgaenger().getDatum() != null
                        ? ab.getVorgaenger().getDatum().format(DATUM_FORMAT) : null,
                ab.getZahlungszielTage());
    }

    private static String baueAdresseAusKunde(Kunde kunde)
    {
        if (kunde == null) return null;
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
        return sb.length() == 0 ? null : sb.toString();
    }

    // ======================= positionenJson Parser =======================

    /**
     * Konvertiert das im DocumentEditor erzeugte {@code positionenJson}
     * (rekursive Struktur aus TEXT/SERVICE/SECTION_HEADER/SUBTOTAL/SEPARATOR/CLOSURE)
     * in die flache {@link ContentBlockDto}-Liste, die der {@link RechnungPdfService}
     * erwartet. Position-Nummerierung wird hierarchisch vergeben (1, 1.1, 2 …),
     * analog zur Frontend-Logik.
     */
    static List<ContentBlockDto> parsePositionenJsonZuContentBlocks(String positionenJson)
    {
        if (positionenJson == null || positionenJson.isBlank()) return List.of();
        try
        {
            JsonNode root = OBJECT_MAPPER.readTree(positionenJson);
            JsonNode blocks;
            if (root.isArray()) blocks = root;
            else if (root.has("blocks") && root.get("blocks").isArray()) blocks = root.get("blocks");
            else return List.of();

            List<ContentBlockDto> result = new ArrayList<>();
            int[] counters = new int[] { 0 };
            for (JsonNode block : blocks)
            {
                appendBlock(block, "", counters, result);
            }
            return result;
        }
        catch (Exception e)
        {
            log.warn("positionenJson konnte nicht geparst werden: {}", e.getMessage());
            return List.of();
        }
    }

    private static void appendBlock(JsonNode block, String parentPos, int[] counters, List<ContentBlockDto> out)
    {
        String type = optString(block, "type");
        if (type == null) return;

        switch (type)
        {
            case "TEXT" ->
            {
                String text = optString(block, "content");
                int fontSize = optInt(block, "fontSize", 10);
                boolean fett = optBoolean(block, "fett", false);
                out.add(new ContentBlockDto("TEXT", text, fett, fontSize,
                        null, null, null, null, null, null, null, false, null, null));
            }
            case "SERVICE" ->
            {
                counters[0]++;
                String pos = parentPos.isEmpty() ? String.valueOf(counters[0]) : parentPos + "." + counters[0];
                BigDecimal menge = optBigDecimal(block, "quantity", BigDecimal.ONE);
                BigDecimal einzelpreis = optBigDecimal(block, "price", BigDecimal.ZERO);
                BigDecimal gesamt = menge.multiply(einzelpreis);
                BigDecimal rabattProzent = optBigDecimal(block, "discount", null);
                if (rabattProzent != null && rabattProzent.signum() > 0)
                {
                    BigDecimal faktor = BigDecimal.ONE.subtract(
                            rabattProzent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                    gesamt = gesamt.multiply(faktor).setScale(2, RoundingMode.HALF_UP);
                }
                else
                {
                    gesamt = gesamt.setScale(2, RoundingMode.HALF_UP);
                }
                out.add(new ContentBlockDto(
                        "SERVICE",
                        null,
                        optBoolean(block, "fett", false),
                        optInt(block, "fontSize", 10),
                        pos,
                        optString(block, "title"),
                        optString(block, "description"),
                        menge,
                        optString(block, "unit", "Stk"),
                        einzelpreis,
                        gesamt,
                        optBoolean(block, "optional", false),
                        null,
                        rabattProzent));
            }
            case "SECTION_HEADER" ->
            {
                counters[0]++;
                String pos = parentPos.isEmpty() ? String.valueOf(counters[0]) : parentPos + "." + counters[0];
                out.add(new ContentBlockDto("SECTION_HEADER", null, false, 0,
                        pos, null, null, null, null, null, null, false,
                        optString(block, "sectionLabel", ""), null));
                JsonNode children = block.get("children");
                if (children != null && children.isArray())
                {
                    int[] childCounters = new int[] { 0 };
                    for (JsonNode child : children)
                    {
                        appendBlock(child, pos, childCounters, out);
                    }
                }
            }
            case "SUBTOTAL" -> out.add(new ContentBlockDto("SUBTOTAL", null, false, 0,
                    null, null, null, null, null, null, null, false, null, null));
            case "SEPARATOR" -> out.add(new ContentBlockDto("SEPARATOR", null, false, 0,
                    null, null, null, null, null, null, null, false, null, null));
            case "CLOSURE" -> out.add(new ContentBlockDto("CLOSURE", null, false, 0,
                    null, null, null, null, null, null, null, false, null, null));
            default -> { /* unbekannter Blocktyp wird ignoriert */ }
        }
    }

    private static String optString(JsonNode node, String key) { return optString(node, key, null); }
    private static String optString(JsonNode node, String key, String fallback)
    {
        if (node == null || !node.has(key) || node.get(key).isNull()) return fallback;
        return node.get(key).asText();
    }
    private static int optInt(JsonNode node, String key, int fallback)
    {
        if (node == null || !node.has(key) || node.get(key).isNull()) return fallback;
        return node.get(key).asInt(fallback);
    }
    private static boolean optBoolean(JsonNode node, String key, boolean fallback)
    {
        if (node == null || !node.has(key) || node.get(key).isNull()) return fallback;
        return node.get(key).asBoolean(fallback);
    }
    private static BigDecimal optBigDecimal(JsonNode node, String key, BigDecimal fallback)
    {
        if (node == null || !node.has(key) || node.get(key).isNull()) return fallback;
        try { return new BigDecimal(node.get(key).asText()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ======================= E-Mail =======================

    private EmailService.EmailContent baueEmailInhalt(AusgangsGeschaeftsDokument ab)
    {
        Map<String, String> ctx = baueTemplateKontext(ab);
        EmailService.EmailContent rendered = emailTextTemplateService.render("AUFTRAGSBESTAETIGUNG", ctx);
        if (rendered != null && rendered.subject() != null && !rendered.subject().isBlank()) return rendered;

        // Fallback auf hartkodiertes Template
        return EmailService.buildOrderConfirmationEmail(
                null,
                "Sehr geehrte Damen und Herren",
                ctx.getOrDefault("KUNDENNAME", ""),
                ctx.getOrDefault("BAUVORHABEN", ""),
                ctx.getOrDefault("PROJEKTNUMMER", ""),
                ctx.getOrDefault("DOKUMENTNUMMER", ab.getDokumentNummer()),
                ctx.getOrDefault("BETRAG", ""),
                "Bauschlosserei Kuhn");
    }

    /**
     * Kontext für die E-Mail-Vorlagen-Engine ({@code EmailTextTemplateService}).
     * Liegt auf dem PDF-Platzhalter-Kontext auf und ergänzt nur den Betrag,
     * sodass Mail- und PDF-Werte garantiert konsistent sind.
     */
    private Map<String, String> baueTemplateKontext(AusgangsGeschaeftsDokument ab)
    {
        Map<String, String> ctx = new HashMap<>(bauePlatzhalterKontext(ab));
        ctx.put("BETRAG", formatBetrag(ermittleBruttoBetrag(ab)));
        return ctx;
    }

    /**
     * Liefert den Brutto-Betrag der AB. Bei Folgedokumenten setzt
     * {@code AusgangsGeschaeftsDokumentService.erstellen} weder netto noch
     * brutto, weil das Frontend bisher die Beträge erst beim Speichern
     * nachrechnet. Daher: erst die persistierten Werte verwenden, dann
     * Netto+MwSt, dann Fallback aus dem geerbten {@code positionenJson}.
     */
    static BigDecimal ermittleBruttoBetrag(AusgangsGeschaeftsDokument ab)
    {
        BigDecimal mwst = ab.getMwstSatz() != null ? ab.getMwstSatz() : new BigDecimal("0.19");
        if (ab.getBetragBrutto() != null) return ab.getBetragBrutto();
        BigDecimal netto = ab.getBetragNetto() != null
                ? ab.getBetragNetto()
                : summiereNettoAusJson(ab.getPositionenJson());
        if (netto == null) return null;
        return netto.multiply(BigDecimal.ONE.add(mwst)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Aggregiert den Nettobetrag aus dem {@code positionenJson} eines
     * AusgangsGeschaeftsDokuments — nur SERVICE-Blöcke (auch verschachtelt
     * unter SECTION_HEADER), {@code optional==true} ausgeschlossen, Positions-
     * Rabatt {@code discount} berücksichtigt. Symmetrisch zur Frontend-Logik
     * im DocumentEditor.
     */
    static BigDecimal summiereNettoAusJson(String positionenJson)
    {
        if (positionenJson == null || positionenJson.isBlank()) return null;
        try
        {
            JsonNode root = OBJECT_MAPPER.readTree(positionenJson);
            JsonNode blocks = root.isArray() ? root
                    : (root.has("blocks") && root.get("blocks").isArray() ? root.get("blocks") : null);
            if (blocks == null) return null;
            BigDecimal summe = BigDecimal.ZERO;
            for (JsonNode b : blocks) summe = summe.add(summiereBlock(b));
            return summe.setScale(2, RoundingMode.HALF_UP);
        }
        catch (Exception e)
        {
            log.warn("Netto-Summe konnte aus positionenJson nicht ermittelt werden: {}", e.getMessage());
            return null;
        }
    }

    private static BigDecimal summiereBlock(JsonNode block)
    {
        String type = optString(block, "type", "");
        if ("SERVICE".equals(type))
        {
            if (optBoolean(block, "optional", false)) return BigDecimal.ZERO;
            BigDecimal menge = optBigDecimal(block, "quantity", BigDecimal.ONE);
            BigDecimal preis = optBigDecimal(block, "price", BigDecimal.ZERO);
            BigDecimal pos = menge.multiply(preis);
            BigDecimal rabatt = optBigDecimal(block, "discount", null);
            if (rabatt != null && rabatt.signum() > 0)
            {
                BigDecimal faktor = BigDecimal.ONE.subtract(
                        rabatt.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                pos = pos.multiply(faktor);
            }
            return pos;
        }
        if ("SECTION_HEADER".equals(type))
        {
            JsonNode children = block.get("children");
            if (children != null && children.isArray())
            {
                BigDecimal s = BigDecimal.ZERO;
                for (JsonNode c : children) s = s.add(summiereBlock(c));
                return s;
            }
        }
        return BigDecimal.ZERO;
    }

    private static String formatBetrag(BigDecimal betrag)
    {
        if (betrag == null) return "";
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        return nf.format(betrag);
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private String ermittleAbsenderAdresse()
    {
        // Konfigurierbar im Firma-Editor → System-Setup; Fallback auf SMTP-User.
        // Hintergrund: Wer eine separate "Sub-Email" als sichtbaren Absender
        // hinterlegt, vermeidet dass Gmail die SMTP-Login-Adresse anhand
        // ihres reinen Auto-Mail-Verkehrs als Bulk/Spam einstuft.
        return systemSettingsService.getMailFromAddress();
    }

    private static String sanitizeForFilename(String input)
    {
        if (input == null) return "Dokument";
        return input.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    // ======================= Annahme-Beleg im HTML-Body =======================

    private static final DateTimeFormatter ANNAHME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");

    /**
     * Hängt einen Annahme-Beleg-Block an den HTML-Body an. Optisch identisch zum
     * Freigabe-Block in der Angebots-Mail (siehe {@code DokumentFreigabeService.buildFreigabeBlockHtml}):
     * linker Akzent-Border in #500010, hellgrauer Hintergrund, Arial. So bleibt das
     * Markenbild über beide Mail-Typen konsistent.
     */
    private EmailService.EmailContent mitAnnahmeBeleg(EmailService.EmailContent content, DokumentFreigabe freigabe)
    {
        String belegHtml = buildAnnahmeBelegHtml(freigabe);
        String html = content.htmlBody() == null ? belegHtml : content.htmlBody() + belegHtml;
        return new EmailService.EmailContent(content.subject(), html);
    }

    private static String buildAnnahmeBelegHtml(DokumentFreigabe f)
    {
        String wann = f.getAkzeptiertAm() != null ? f.getAkzeptiertAm().format(ANNAHME_FORMAT) : "—";
        String hash = kuerzeHash(f.getHashAcceptance());
        String angebotsNummer = nullSafe(f.getDokumentNummer());
        // Unterzeichner-Name gehört zur Beweissicherung (siehe Issue-Spec): bei
        // Firmenkunden klickt nicht zwingend die Person, deren Name in den Stammdaten
        // steht. Wir nennen daher die konkret freigebende Person mit Vor- und Nachname.
        String unterzeichner = f.getUnterzeichnerName() == null || f.getUnterzeichnerName().isBlank()
                ? null : f.getUnterzeichnerName();

        String beweisZeile = unterzeichner != null
                ? "Annahme durch <strong>" + escapeHtml(unterzeichner) + "</strong> am <strong>" + wann + "</strong>"
                : "Annahme am <strong>" + wann + "</strong>";

        return "<div style=\"margin:24px 0;padding:16px 18px;border-left:3px solid #500010;background:#fafafa;font-family:Arial,Helvetica,sans-serif;\">"
                + "<p style=\"margin:0 0 6px 0;font-weight:600;color:#1e293b;\">Angebot angenommen — Auftragsbestätigung</p>"
                + "<p style=\"margin:0 0 10px 0;color:#475569;line-height:1.45;\">"
                + "Diese Mail wurde automatisch vom System erstellt, nachdem das Angebot "
                + "<strong>" + escapeHtml(angebotsNummer) + "</strong> online digital angenommen wurde. "
                + "Im Anhang finden Sie Ihre verbindliche Auftragsbestätigung als PDF."
                + "</p>"
                + "<p style=\"margin:8px 0 0 0;color:#94a3b8;font-size:13px;line-height:1.5;\">"
                + "Beweissicherung: " + beweisZeile + "<br>"
                + "Audit-Hash zur Rechtssicherheit: <code style=\"font-family:monospace;color:#64748b;\">" + hash + "</code><br>"
                + "Sollten Sie diese Annahme <em>nicht</em> selbst durchgeführt haben, melden Sie sich bitte umgehend bei uns."
                + "</p>"
                + "</div>";
    }

    /**
     * Minimaler HTML-Escape für vom Nutzer eingegebene Namen, damit der
     * Annahme-Beleg auch bei Sonderzeichen wie {@code &} oder {@code <}
     * sauber rendert.
     */
    private static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String kuerzeHash(String hash)
    {
        if (hash == null || hash.isBlank()) return "—";
        return hash.length() <= 16 ? hash : hash.substring(0, 16) + "…";
    }
}
