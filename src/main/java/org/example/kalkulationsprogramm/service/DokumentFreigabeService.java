package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAuditDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verwaltet die digitale Freigabe von Geschäftsdokumenten (Angebot, Auftragsbestätigung)
 * über einen UUID-Token, der dem Kunden per E-Mail zugesandt wird.
 *
 * Sicherheitsmodell:
 *  - Beim Versand wird ein SHA-256-Hash über die Geschäftsdaten + serverseitiges Salt
 *    gespeichert ("Fingerabdruck zum Versand-Zeitpunkt").
 *  - Beim Annehmen wird ein zweiter Hash über Original-Hash + Akzeptanzdaten erstellt
 *    und als unveränderbarer Beweis gespeichert.
 *  - Standardgültigkeit: 14 Tage ab Versand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DokumentFreigabeService
{
    /** Default-Gültigkeit in Tagen, wenn der Aufrufer keine eigene Wahl trifft. */
    public static final int DEFAULT_GUELTIGKEITS_TAGE = 14;
    /**
     * Marker-Message für unbekannte UUID — der Controller nutzt sie, um eindeutig
     * zwischen "Freigabe nicht gefunden" (→ 404) und Service-Validierungsfehlern
     * (→ 400) zu unterscheiden, ohne auf String-Heuristiken angewiesen zu sein.
     */
    public static final String UNBEKANNTE_UUID_MESSAGE = "Unbekannte Freigabe-UUID";
    /** Untere und obere Grenze für vom Anwender gewählte Gültigkeitstage. */
    private static final int MIN_GUELTIGKEITS_TAGE = 1;
    private static final int MAX_GUELTIGKEITS_TAGE = 365;

    private static final DateTimeFormatter ABLAUF_DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DokumentFreigabeRepository repository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    private final AusgangsGeschaeftsDokumentAuditService ausgangsGeschaeftsDokumentAuditService;
    private final WebPushService webPushService;
    private final DateiSpeicherService dateiSpeicherService;
    private final AutoAuftragsbestaetigungVersandService autoAuftragsbestaetigungVersandService;
    private final ProjektManagementService projektManagementService;
    private final AnfrageRepository anfrageRepository;

    @Value("${freigabe.hash.salt:CHANGE_ME_LOCAL_ONLY}")
    private String hashSalt;

    @Value("${freigabe.public-base-url:https://bauschlosserei-kuhn.de}")
    private String publicBaseUrl;

    /**
     * Erzeugt einen Freigabe-Token für ein Anfrage-Geschäftsdokument
     * (Angebot oder Auftragsbestätigung an einer offenen Anfrage).
     */
    @Transactional
    public DokumentFreigabe erstelleFuerAnfrage(AnfrageGeschaeftsdokument dokument, String kundeName, String kundeEmail)
    {
        return erstelleFuerAnfrage(dokument, kundeName, kundeEmail, DEFAULT_GUELTIGKEITS_TAGE);
    }

    /**
     * Wie {@link #erstelleFuerAnfrage(AnfrageGeschaeftsdokument, String, String)},
     * aber mit individuell gewählter Gültigkeitsdauer in Tagen.
     */
    @Transactional
    public DokumentFreigabe erstelleFuerAnfrage(AnfrageGeschaeftsdokument dokument, String kundeName, String kundeEmail, int gueltigkeitTage)
    {
        revokeAltePendingFreigaben(FreigabeQuellTyp.ANFRAGE, dokument.getId());
        DokumentFreigabe freigabe = baseFreigabe(gueltigkeitTage);
        freigabe.setQuellTyp(FreigabeQuellTyp.ANFRAGE);
        freigabe.setQuellDokumentId(dokument.getId());
        freigabe.setDokumentNummer(dokument.getDokumentid());
        freigabe.setDokumentArt(dokument.getGeschaeftsdokumentart());
        freigabe.setDokumentBetrag(dokument.getBruttoBetrag());
        freigabe.setDokumentDatei(dokument.getGespeicherterDateiname());
        freigabe.setBauvorhaben(dokument.getAnfrage() != null ? dokument.getAnfrage().getBauvorhaben() : null);
        freigabe.setKundeName(kundeName);
        freigabe.setKundeEmail(kundeEmail);
        freigabe.setHashOriginal(berechneHashOriginal(freigabe));
        return repository.save(freigabe);
    }

    /**
     * Erzeugt einen Freigabe-Token für ein Projekt-Geschäftsdokument.
     */
    @Transactional
    public DokumentFreigabe erstelleFuerProjekt(ProjektGeschaeftsdokument dokument, String kundeName, String kundeEmail)
    {
        return erstelleFuerProjekt(dokument, kundeName, kundeEmail, DEFAULT_GUELTIGKEITS_TAGE);
    }

    /**
     * Wie {@link #erstelleFuerProjekt(ProjektGeschaeftsdokument, String, String)},
     * aber mit individuell gewählter Gültigkeitsdauer in Tagen.
     */
    @Transactional
    public DokumentFreigabe erstelleFuerProjekt(ProjektGeschaeftsdokument dokument, String kundeName, String kundeEmail, int gueltigkeitTage)
    {
        revokeAltePendingFreigaben(FreigabeQuellTyp.PROJEKT, dokument.getId());
        DokumentFreigabe freigabe = baseFreigabe(gueltigkeitTage);
        freigabe.setQuellTyp(FreigabeQuellTyp.PROJEKT);
        freigabe.setQuellDokumentId(dokument.getId());
        freigabe.setDokumentNummer(dokument.getDokumentid());
        freigabe.setDokumentArt(dokument.getGeschaeftsdokumentart());
        freigabe.setDokumentBetrag(dokument.getBruttoBetrag());
        freigabe.setDokumentDatei(dokument.getGespeicherterDateiname());
        freigabe.setBauvorhaben(dokument.getProjekt() != null ? dokument.getProjekt().getBauvorhaben() : null);
        freigabe.setKundeName(kundeName);
        freigabe.setKundeEmail(kundeEmail);
        freigabe.setHashOriginal(berechneHashOriginal(freigabe));
        return repository.save(freigabe);
    }

    /**
     * Liefert die fertige öffentliche URL, die in die E-Mail eingefügt wird.
     */
    public String buildPublicUrl(DokumentFreigabe freigabe)
    {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        return base + "/freigabe/" + freigabe.getUuid();
    }

    /**
     * Erzeugt den HTML-Block für die E-Mail, der den Freigabe-Link enthält.
     * Wird sowohl beim E-Mail-Versand als auch bei der Template-Vorschau genutzt.
     */
    public static String buildFreigabeBlockHtml(String url, String dokumentArt, int gueltigkeitTage, LocalDateTime ablaufDatum)
    {
        String art = dokumentArt == null || dokumentArt.isBlank() ? "Dokument" : dokumentArt;
        String tageText = gueltigkeitTage == 1 ? "1 Tag" : gueltigkeitTage + " Tage";
        String ablaufText = ablaufDatum != null
                ? "Der Link ist " + tageText + " gültig (bis zum " + ablaufDatum.format(ABLAUF_DATUM_FORMAT) + ")."
                : "Der Link ist " + tageText + " gültig.";
        return "<div style=\"margin:24px 0;padding:16px 18px;border-left:3px solid #500010;background:#fafafa;font-family:Arial,Helvetica,sans-serif;\">"
                + "<p style=\"margin:0 0 6px 0;font-weight:600;color:#1e293b;\">" + art + " digital prüfen und annehmen</p>"
                + "<p style=\"margin:0 0 10px 0;color:#475569;line-height:1.45;\">"
                + "Sie können dieses " + art + " bequem online ansehen und mit einem Klick verbindlich annehmen:"
                + "</p>"
                + "<p style=\"margin:0;\"><a href=\"" + url + "\" style=\"color:#500010;font-weight:600;text-decoration:underline;\">"
                + url + "</a></p>"
                + "<p style=\"margin:8px 0 0 0;color:#94a3b8;font-size:13px;\">" + ablaufText + "</p>"
                + "</div>";
    }

    /**
     * Erzeugt einen Freigabe-Token für ein AusgangsGeschaeftsDokument (neues Dokumentsystem).
     * Die PDF-Datei wird beim E-Mail-Versand serverseitig gespeichert und hier eingetragen.
     * Nach Ablauf der Freigabe wird sie über {@link #loeschePdfFuerFreigabe(String)} bereinigt.
     */
    @Transactional
    public DokumentFreigabe erstelleFuerAusgangsGeschaeftsDokument(AusgangsGeschaeftsDokument dok, String kundeEmail, String pdfDateiname)
    {
        return erstelleFuerAusgangsGeschaeftsDokument(dok, kundeEmail, pdfDateiname, DEFAULT_GUELTIGKEITS_TAGE);
    }

    /**
     * Wie {@link #erstelleFuerAusgangsGeschaeftsDokument(AusgangsGeschaeftsDokument, String, String)},
     * aber mit individuell gewählter Gültigkeitsdauer in Tagen.
     */
    @Transactional
    public DokumentFreigabe erstelleFuerAusgangsGeschaeftsDokument(AusgangsGeschaeftsDokument dok, String kundeEmail, String pdfDateiname, int gueltigkeitTage)
    {
        revokeAltePendingFreigaben(FreigabeQuellTyp.AUSGANGS_DOKUMENT, dok.getId());
        DokumentFreigabe freigabe = baseFreigabe(gueltigkeitTage);
        freigabe.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        freigabe.setQuellDokumentId(dok.getId());
        freigabe.setDokumentNummer(dok.getDokumentNummer());
        freigabe.setDokumentArt(typZuBezeichnung(dok.getTyp()));
        freigabe.setDokumentBetrag(dok.getBetragBrutto());
        freigabe.setDokumentDatei(pdfDateiname);

        String bauvorhaben = null;
        String kundeName = null;
        if (dok.getProjekt() != null)
        {
            bauvorhaben = dok.getProjekt().getBauvorhaben();
            if (dok.getProjekt().getKundenId() != null)
            {
                kundeName = dok.getProjekt().getKundenId().getName();
            }
        }
        else if (dok.getAnfrage() != null)
        {
            bauvorhaben = dok.getAnfrage().getBauvorhaben();
            if (dok.getAnfrage().getKunde() != null)
            {
                kundeName = dok.getAnfrage().getKunde().getName();
            }
        }
        if (kundeName == null && dok.getKunde() != null)
        {
            kundeName = dok.getKunde().getName();
        }

        freigabe.setBauvorhaben(bauvorhaben);
        freigabe.setKundeName(kundeName);
        freigabe.setKundeEmail(kundeEmail);
        freigabe.setHashOriginal(berechneHashOriginal(freigabe));
        return repository.save(freigabe);
    }

    /**
     * Löscht die gespeicherte PDF einer abgelaufenen Freigabe vom Datenträger.
     * Wird von der Internetseite aufgerufen, wenn die Freigabe abgelaufen ist.
     */
    @Transactional
    public void loeschePdfFuerFreigabe(String uuid)
    {
        repository.findByUuid(uuid).ifPresent(freigabe ->
        {
            String dateiname = freigabe.getDokumentDatei();
            if (dateiname != null && !dateiname.isBlank())
            {
                dateiSpeicherService.loescheDokumentPdfByDateiname(dateiname);
                freigabe.setDokumentDatei(null);
                repository.save(freigabe);
            }
        });
    }

    /**
     * Erstellt einen Freigabe-Token für ein Dokument (per ID) und gibt den fertigen
     * HTML-Block zurück, der in die E-Mail-Vorlage eingebettet werden kann.
     * Sucht zuerst im neuen System (AusgangsGeschaeftsDokument), dann im alten.
     * Nur für Angebote und Auftragsbestätigungen – bei anderen Typen wird Optional.empty() zurückgegeben.
     */
    @Transactional
    public Optional<String> erstelleFreigabeBlockFuerDokument(Long dokumentId, boolean isAnfrage, String recipient, String pdfDateiname)
    {
        return erstelleFreigabeBlockFuerDokument(dokumentId, isAnfrage, recipient, pdfDateiname, DEFAULT_GUELTIGKEITS_TAGE);
    }

    /**
     * Wie {@link #erstelleFreigabeBlockFuerDokument(Long, boolean, String, String)},
     * aber mit individuell gewählter Gültigkeitsdauer in Tagen.
     * Werte außerhalb [1; 365] werden auf den Default zurückgesetzt.
     */
    @Transactional
    public Optional<String> erstelleFreigabeBlockFuerDokument(Long dokumentId, boolean isAnfrage, String recipient, String pdfDateiname, int gueltigkeitTage)
    {
        if (dokumentId == null) return Optional.empty();
        int tage = clampGueltigkeitTage(gueltigkeitTage);
        try
        {
            // Neues System: AusgangsGeschaeftsDokument (DocumentEditor)
            Optional<AusgangsGeschaeftsDokument> agdOpt = ausgangsGeschaeftsDokumentRepository.findById(dokumentId);
            if (agdOpt.isPresent())
            {
                AusgangsGeschaeftsDokument agd = agdOpt.get();
                if (!istAngebotOderABTyp(agd.getTyp())) return Optional.empty();
                DokumentFreigabe freigabe = erstelleFuerAusgangsGeschaeftsDokument(agd, recipient, pdfDateiname, tage);
                return Optional.of(buildFreigabeBlockHtml(buildPublicUrl(freigabe), typZuBezeichnung(agd.getTyp()), tage, freigabe.getAblaufDatum()));
            }

            // Fallback: altes System
            if (isAnfrage)
            {
                return anfrageDokumentRepository.findById(dokumentId)
                        .filter(d -> d instanceof AnfrageGeschaeftsdokument)
                        .filter(d -> istAngebotOderAB(((AnfrageGeschaeftsdokument) d).getGeschaeftsdokumentart()))
                        .map(d -> {
                            AnfrageGeschaeftsdokument gesDoc = (AnfrageGeschaeftsdokument) d;
                            String kundeName = gesDoc.getAnfrage() != null && gesDoc.getAnfrage().getKunde() != null
                                    ? gesDoc.getAnfrage().getKunde().getName() : null;
                            DokumentFreigabe freigabe = erstelleFuerAnfrage(gesDoc, kundeName, recipient, tage);
                            return buildFreigabeBlockHtml(buildPublicUrl(freigabe), gesDoc.getGeschaeftsdokumentart(), tage, freigabe.getAblaufDatum());
                        });
            }
            else
            {
                return projektDokumentRepository.findById(dokumentId)
                        .filter(d -> d instanceof ProjektGeschaeftsdokument)
                        .filter(d -> istAngebotOderAB(((ProjektGeschaeftsdokument) d).getGeschaeftsdokumentart()))
                        .map(d -> {
                            ProjektGeschaeftsdokument gesDoc = (ProjektGeschaeftsdokument) d;
                            String kundeName = gesDoc.getProjekt() != null && gesDoc.getProjekt().getKundenId() != null
                                    ? gesDoc.getProjekt().getKundenId().getName() : null;
                            DokumentFreigabe freigabe = erstelleFuerProjekt(gesDoc, kundeName, recipient, tage);
                            return buildFreigabeBlockHtml(buildPublicUrl(freigabe), gesDoc.getGeschaeftsdokumentart(), tage, freigabe.getAblaufDatum());
                        });
            }
        }
        catch (Exception e)
        {
            return Optional.empty();
        }
    }

    private static int clampGueltigkeitTage(int tage)
    {
        if (tage < MIN_GUELTIGKEITS_TAGE || tage > MAX_GUELTIGKEITS_TAGE) return DEFAULT_GUELTIGKEITS_TAGE;
        return tage;
    }

    private static boolean istAngebotOderABTyp(AusgangsGeschaeftsDokumentTyp typ)
    {
        // Nur Angebote bekommen einen digitalen Freigabe-Link. Auftragsbestätigungen
        // werden vom Büro versendet, der Kunde stimmt nur dem Angebot zu — die AB ist
        // dessen Folge und braucht keine zweite Bestätigung mehr.
        return typ == AusgangsGeschaeftsDokumentTyp.ANGEBOT;
    }

    private static String typZuBezeichnung(AusgangsGeschaeftsDokumentTyp typ)
    {
        if (typ == null) return "Dokument";
        return switch (typ)
        {
            case ANGEBOT -> "Angebot";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestätigung";
            default -> typ.name();
        };
    }

    private static boolean istAngebotOderAB(String art)
    {
        if (art == null) return false;
        String lower = art.toLowerCase(Locale.GERMAN);
        return lower.contains("angebot") || lower.contains("auftragsbest");
    }

    /**
     * Lädt eine Freigabe anhand der UUID und aktualisiert den Status auf EXPIRED,
     * falls das Ablaufdatum überschritten ist.
     */
    @Transactional
    public Optional<DokumentFreigabe> findByUuidUndAktualisiereStatus(String uuid)
    {
        return repository.findByUuid(uuid).map(freigabe ->
        {
            if (freigabe.getStatus() == FreigabeStatus.PENDING && freigabe.istAbgelaufen())
            {
                freigabe.setStatus(FreigabeStatus.EXPIRED);
                repository.save(freigabe);
            }
            return freigabe;
        });
    }

    /**
     * Markiert eine Freigabe als digital akzeptiert. Speichert IP, User-Agent,
     * Vor-/Nachname der unterzeichnenden Person und berechnet einen
     * unveränderbaren Acceptance-Hash als Beweis.
     *
     * <p>Idempotent: Wiederholtes Akzeptieren derselben UUID liefert die bereits
     * gespeicherte Freigabe ohne Namens-, IP- oder Hash-Felder zu überschreiben —
     * der erste Klick ist beweisrelevant.</p>
     *
     * @throws IllegalArgumentException wenn Vor- oder Nachname fehlen
     *         (Service-Check, der die DB-NULLABLE-Spalte zusätzlich absichert).
     */
    @Transactional
    public DokumentFreigabe akzeptiere(String uuid, String ip, String userAgent, String email,
                                       String vorname, String nachname, String unterzeichnerName)
    {
        DokumentFreigabe freigabe = repository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException(UNBEKANNTE_UUID_MESSAGE));

        if (freigabe.getStatus() == FreigabeStatus.ACCEPTED)
        {
            // Idempotenz: erster Klick bleibt der Beweis — Namen/IP/Hash unverändert.
            return freigabe;
        }
        if (freigabe.getStatus() != FreigabeStatus.PENDING)
        {
            throw new IllegalStateException("Freigabe ist nicht mehr gültig: " + freigabe.getStatus());
        }
        if (freigabe.istAbgelaufen())
        {
            freigabe.setStatus(FreigabeStatus.EXPIRED);
            repository.save(freigabe);
            throw new IllegalStateException("Freigabe ist abgelaufen");
        }

        String vornameNorm = normalisiereName(vorname);
        String nachnameNorm = normalisiereName(nachname);
        String anzeigeName = normalisiereName(unterzeichnerName);
        if (vornameNorm == null || vornameNorm.isBlank() || nachnameNorm == null || nachnameNorm.isBlank())
        {
            // Service-Check: neue Akzeptanzen müssen den Namen tragen, auch wenn die
            // DB-Spalte aus Altbestand-Gründen NULLABLE ist.
            throw new IllegalArgumentException("Vor- und Nachname sind für die digitale Annahme erforderlich.");
        }
        if (anzeigeName == null || anzeigeName.isBlank())
        {
            anzeigeName = (vornameNorm + " " + nachnameNorm).trim();
        }

        LocalDateTime jetzt = LocalDateTime.now();
        freigabe.setStatus(FreigabeStatus.ACCEPTED);
        freigabe.setAkzeptiertAm(jetzt);
        freigabe.setAkzeptiertIp(ip);
        freigabe.setAkzeptiertUserAgent(userAgent);
        freigabe.setAkzeptiertEmail(email);
        freigabe.setUnterzeichnerVorname(vornameNorm);
        freigabe.setUnterzeichnerNachname(nachnameNorm);
        freigabe.setUnterzeichnerName(anzeigeName);
        freigabe.setHashAcceptance(berechneHashAcceptance(freigabe, ip, email, jetzt));
        DokumentFreigabe saved = repository.save(freigabe);

        // Push-Notification an alle registrierten Geräte (Büro), damit jemand sofort
        // sieht, dass der Kunde digital angenommen hat. Format: "Freigabe eingegangen
        // von <Unterzeichner> für <Kunde>" — Unterzeichner ist die konkret klickende
        // Person, Kunde der Stammdatensatz (bei Firmenkunden i.d.R. unterschiedlich).
        try {
            String art = saved.getDokumentArt() == null ? "Dokument" : saved.getDokumentArt();
            String kunde = saved.getKundeName() == null || saved.getKundeName().isBlank()
                    ? (saved.getKundeEmail() == null ? "Ein Kunde" : saved.getKundeEmail())
                    : saved.getKundeName();
            String unterzeichner = saved.getUnterzeichnerName() == null || saved.getUnterzeichnerName().isBlank()
                    ? null : saved.getUnterzeichnerName();
            String body = unterzeichner != null
                    ? "Freigabe eingegangen von " + unterzeichner + " für " + kunde
                        + " — " + art + " " + saved.getDokumentNummer() + " digital angenommen."
                    : kunde + " hat " + art + " " + saved.getDokumentNummer() + " digital angenommen.";
            // Klick auf den Push oeffnet die Mobile-PWA-Projekteseite, weil mit der
            // Annahme die Anfrage zum Projekt wird (siehe erzeugeAutoAuftragsbestaetigungWennAngebot).
            webPushService.notifyFreigabeAnnahme(art + " angenommen", body, "/zeiterfassung/projekte");
        } catch (Exception ignored) {
            // Push-Probleme dürfen die Annahme nie blockieren
        }

        // Auto-Auftragsbestätigung: Hat der Kunde ein Angebot angenommen, erzeugen wir
        // direkt eine Auftragsbestätigung als Folgedokument (mit den geerbten Positionen
        // und den ABS-Standard-Textbausteinen). Fehler dürfen die Annahme nicht blockieren.
        try {
            erzeugeAutoAuftragsbestaetigungWennAngebot(saved);
        } catch (Exception ignored) {
            // bewusst geschluckt: AB-Erstellung ist Komfort, nicht kritisch
        }
        return saved;
    }

    /**
     * Wenn die akzeptierte Freigabe zu einem AusgangsGeschaeftsDokument vom Typ ANGEBOT gehört
     * und noch keine Auftragsbestätigung als Nachfolger existiert, wird automatisch eine
     * Auftragsbestätigung als Folgedokument erstellt. Inhalt, Positionen, Kunde und Projekt
     * werden vom Angebot geerbt; Standard-Textbausteine werden beim Typwechsel ausgetauscht.
     *
     * <p>Hängt das Angebot noch an einer Anfrage (kein Projekt), wird die Anfrage VOR der
     * AB-Erzeugung in ein neues Projekt überführt — analog zu „Von Anfrage übernehmen" im
     * ProjektEditor. Damit ist die {@code PROJEKTNUMMER} bei der AB-Erzeugung bereits
     * verfügbar und wird in PDF- und E-Mail-Platzhaltern korrekt aufgelöst.</p>
     */
    private void erzeugeAutoAuftragsbestaetigungWennAngebot(DokumentFreigabe freigabe)
    {
        if (freigabe.getQuellTyp() != FreigabeQuellTyp.AUSGANGS_DOKUMENT) return;
        Long angebotId = freigabe.getQuellDokumentId();
        if (angebotId == null) return;

        AusgangsGeschaeftsDokument angebot = ausgangsGeschaeftsDokumentRepository.findById(angebotId).orElse(null);
        if (angebot == null) return;
        if (angebot.getTyp() != AusgangsGeschaeftsDokumentTyp.ANGEBOT) return;

        // Angebot wird mit der Annahme verbindlich → sperren.
        if (!angebot.isDigitalAngenommen())
        {
            angebot.setDigitalAngenommen(true);
            angebot = ausgangsGeschaeftsDokumentRepository.save(angebot);
            // GoBD-Audit: rechtsverbindlicher Akt — Hash-Kette schützt das Annahmedatum.
            ausgangsGeschaeftsDokumentAuditService.protokolliereDigitaleAnnahme(angebot, freigabe.getAkzeptiertIp());
        }

        // Kein doppeltes Erzeugen: Hat das Angebot bereits eine Auftragsbestätigung als Nachfolger,
        // tun wir nichts.
        if (angebot.getNachfolger() != null && angebot.getNachfolger().stream()
                .anyMatch(n -> n.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG && !n.isStorniert()))
        {
            return;
        }

        // Auto-Projekt anlegen, BEVOR die AB erzeugt wird. Sonst wäre {{PROJEKTNUMMER}}
        // im PDF und in der E-Mail leer/„—". Das Angebot wird dabei vom Anfrage- aufs
        // Projekt-Konto migriert (siehe AusgangsGeschaeftsDokumentService.migrateFromAnfrageToProjekt),
        // wodurch die anschließend erzeugte AB das Projekt automatisch vom Vorgänger erbt.
        if (angebot.getAnfrage() != null && angebot.getProjekt() == null)
        {
            Long anfrageId = angebot.getAnfrage().getId();
            erzeugeAutoProjektAusAnfrage(anfrageId);
            // Reload: Angebot ist nun am Projekt, Anfrage gelöscht.
            angebot = ausgangsGeschaeftsDokumentRepository.findById(angebotId).orElse(angebot);
        }

        AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        dto.setVorgaengerId(angebotId);
        // Datum/Betreff/Inhalt werden vom Service aus dem Vorgänger geerbt.
        // Standard-Textbausteine werden beim Typwechsel auf AB getauscht.
        AusgangsGeschaeftsDokument ab = ausgangsGeschaeftsDokumentService.erstellen(dto);

        // Auftragsbestätigung ist das verbindliche Folgedokument der Annahme — direkt sperren.
        if (ab != null && !ab.isDigitalAngenommen())
        {
            ab.setDigitalAngenommen(true);
            ab = ausgangsGeschaeftsDokumentRepository.save(ab);
            // GoBD-Audit: AB ist rechtlich Folge der Angebotsannahme; spiegeln wir
            // im Audit, damit Prüfer den vollständigen Annahme-Vorgang nachvollziehen kann.
            ausgangsGeschaeftsDokumentAuditService.protokolliereDigitaleAnnahme(ab, freigabe.getAkzeptiertIp());
        }

        // Auto-Versand der AB als PDF-Mail an den Kunden (Issue #55).
        // Empfänger ist die Adresse aus der Freigabe — das ist die Adresse, an die
        // das Angebot ging. Versandfehler dürfen die Annahme nie blockieren.
        if (ab != null)
        {
            String empfaenger = freigabe.getKundeEmail();
            if (empfaenger != null && !empfaenger.isBlank())
            {
                try
                {
                    autoAuftragsbestaetigungVersandService.versende(ab, empfaenger, freigabe);
                }
                catch (Exception ignored) { /* Versand-Fehler werden im Service geloggt */ }
            }
        }
    }

    /**
     * Legt analog zum „Von Anfrage übernehmen"-Flow im ProjektEditor automatisch ein Projekt
     * aus einer Anfrage an, wenn der Kunde das Angebot digital angenommen hat.
     *
     * <p>Übernommen werden:
     * <ul>
     *   <li>Bauvorhaben, Kunde, Kundennummer (aus der Anfrage)</li>
     *   <li>Projekt-Adresse aus {@code Anfrage.projektStrasse/Plz/Ort}</li>
     *   <li>Produktkategorien-Mapping aus den Leistungen des Angebots/der AB
     *       (siehe {@link AusgangsGeschaeftsDokumentService#berechneKategorieVorschlagFuerAnfrage})</li>
     *   <li>Alle Anfrage-Dokumente, Notizen, E-Mails und das Angebot selbst
     *       (über {@link ProjektManagementService#erstelleProjekt})</li>
     * </ul>
     * Auftragsnummer wird automatisch vergeben, Projektart standardmäßig {@code PAUSCHAL}.
     */
    private void erzeugeAutoProjektAusAnfrage(Long anfrageId)
    {
        if (anfrageId == null) return;
        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        if (anfrage == null) return;

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(anfrageId));
        LocalDate heute = LocalDate.now();
        dto.setAnlegedatum(heute);
        // Auftragsnummer folgt der festen Logik YYYY/MM/NNNCC: NNN = Kunden-Slot im Jahr,
        // CC = laufender Auftrag dieses Kunden im Jahr. Folgeaufträge desselben Kunden
        // im selben Jahr teilen sich den Slot, neue Kunden bekommen den nächsten Slot.
        Long kundeIdFuerNummer = anfrage.getKunde() != null ? anfrage.getKunde().getId() : null;
        dto.setAuftragsnummer(projektManagementService.generiereKundenAuftragsnummer(heute, kundeIdFuerNummer));
        dto.setProjektArt("PAUSCHAL");

        // Produktkategorien-Mapping aus Angebot/AB ableiten — gleiche Logik wie
        // /api/anfragen/{id}/produktkategorien-vorschlag im ProjektErstellenModal.
        List<KategorieVorschlagDto> vorschlaege =
                ausgangsGeschaeftsDokumentService.berechneKategorieVorschlagFuerAnfrage(anfrageId);
        if (vorschlaege != null && !vorschlaege.isEmpty())
        {
            List<ProjektProduktkategorieErfassenDto> kategorien = new ArrayList<>();
            for (KategorieVorschlagDto v : vorschlaege)
            {
                ProjektProduktkategorieErfassenDto pp = new ProjektProduktkategorieErfassenDto();
                pp.setProduktkategorieID(v.getKategorieId());
                pp.setMenge(v.getMenge());
                kategorien.add(pp);
            }
            dto.setProduktkategorien(kategorien);
        }

        try
        {
            projektManagementService.erstelleProjekt(
                    dto,
                    anfrage.getProjektStrasse(),
                    anfrage.getProjektPlz(),
                    anfrage.getProjektOrt(),
                    null, null, null);
        }
        catch (Exception e)
        {
            // Stacktrace mitloggen, damit z.B. DataIntegrityViolationException bei einer
            // Auftragsnummer-Race-Kollision (UNIQUE-Constraint) sichtbar wird und nicht
            // stillschweigend untergeht.
            log.warn("Auto-Projektanlage aus Anfrage {} fehlgeschlagen", anfrageId, e);
        }
    }

    /**
     * Lädt den Audit-Trail einer akzeptierten Freigabe für ein einzelnes Quell-Dokument.
     * Wird vom Frontend on-demand beim Klick auf den „Angenommen"-Badge geladen, damit
     * die rechtlich relevanten Beweisdaten (E-Mail, IP, Zeitstempel, Hash) nicht in
     * der Listenansicht mitgeladen werden müssen.
     *
     * <p>Liefert {@link Optional#empty()} wenn keine Freigabe existiert.</p>
     */
    @Transactional(readOnly = true)
    public Optional<FreigabeAuditDto> findAuditByQuelle(FreigabeQuellTyp typ, Long quellDokumentId)
    {
        if (quellDokumentId == null) return Optional.empty();
        Map<Long, DokumentFreigabe> map = findJuengsteProQuelle(typ, List.of(quellDokumentId));
        DokumentFreigabe f = map.get(quellDokumentId);
        if (f == null) return Optional.empty();
        return Optional.of(FreigabeAuditDto.builder()
                .status(f.getStatus().name())
                .dokumentArt(f.getDokumentArt())
                .dokumentNummer(f.getDokumentNummer())
                .erstelltAm(f.getErstelltAm())
                .ablaufDatum(f.getAblaufDatum())
                .akzeptiertAm(f.getAkzeptiertAm())
                .akzeptiertEmail(f.getAkzeptiertEmail())
                .akzeptiertIp(f.getAkzeptiertIp())
                .akzeptiertUserAgent(f.getAkzeptiertUserAgent())
                .unterzeichnerVorname(f.getUnterzeichnerVorname())
                .unterzeichnerNachname(f.getUnterzeichnerNachname())
                .unterzeichnerName(f.getUnterzeichnerName())
                .hashOriginal(f.getHashOriginal())
                .hashAcceptance(f.getHashAcceptance())
                .build());
    }

    /**
     * Lädt für eine Liste von Quell-Dokument-IDs jeweils die jüngste Freigabe
     * (bevorzugt ACCEPTED, sonst aktuellste). Wird für Status-Badges in
     * Anfrage- bzw. Projekt-Übersichten genutzt.
     */
    @Transactional(readOnly = true)
    public Map<Long, DokumentFreigabe> findJuengsteProQuelle(FreigabeQuellTyp typ, List<Long> quellDokumentIds)
    {
        if (quellDokumentIds == null || quellDokumentIds.isEmpty())
        {
            return Map.of();
        }
        List<DokumentFreigabe> alle = repository.findByQuelle(typ, quellDokumentIds);
        return alle.stream().collect(Collectors.toMap(
                DokumentFreigabe::getQuellDokumentId,
                f -> f,
                DokumentFreigabeService::pickRelevant
        ));
    }

    /**
     * Findet pro Anfrage-ID die relevanteste Freigabe (akzeptiert &gt; pending &gt; abgelaufen,
     * sonst aktuellste). Joint die Anfrage-Geschäftsdokumente, weil DokumentFreigabe nur
     * die Quell-Dokument-ID kennt, nicht die Container-Anfrage.
     */
    @Transactional(readOnly = true)
    public Map<Long, DokumentFreigabe> findJuengsteProAnfrage(List<Long> anfrageIds)
    {
        if (anfrageIds == null || anfrageIds.isEmpty())
        {
            return Map.of();
        }
        // Altes System (AnfrageGeschaeftsdokument) — Quelltyp ANFRAGE
        List<Object[]> mappingAlt = anfrageDokumentRepository.findGeschaeftsdokumentIdMappingByAnfrageIds(anfrageIds);
        Map<Long, DokumentFreigabe> alteFreigaben = aggregiereProContainer(FreigabeQuellTyp.ANFRAGE, mappingAlt);

        // Neues System (AusgangsGeschaeftsDokument an Anfrage) — Quelltyp AUSGANGS_DOKUMENT
        List<Object[]> mappingNeu = ausgangsGeschaeftsDokumentRepository.findIdAnfrageIdMappingByAnfrageIds(anfrageIds);
        Map<Long, DokumentFreigabe> neueFreigaben = aggregiereProContainer(FreigabeQuellTyp.AUSGANGS_DOKUMENT, mappingNeu);

        return mergeFreigabenProContainer(alteFreigaben, neueFreigaben);
    }

    /**
     * Pendant zu {@link #findJuengsteProAnfrage} für Projekte.
     */
    @Transactional(readOnly = true)
    public Map<Long, DokumentFreigabe> findJuengsteProProjekt(List<Long> projektIds)
    {
        if (projektIds == null || projektIds.isEmpty())
        {
            return Map.of();
        }
        // Altes System (ProjektGeschaeftsdokument) — Quelltyp PROJEKT
        List<Object[]> mappingAlt = projektDokumentRepository.findGeschaeftsdokumentIdMappingByProjektIds(projektIds);
        Map<Long, DokumentFreigabe> alteFreigaben = aggregiereProContainer(FreigabeQuellTyp.PROJEKT, mappingAlt);

        // Neues System (AusgangsGeschaeftsDokument an Projekt) — Quelltyp AUSGANGS_DOKUMENT
        List<Object[]> mappingNeu = ausgangsGeschaeftsDokumentRepository.findIdProjektIdMappingByProjektIds(projektIds);
        Map<Long, DokumentFreigabe> neueFreigaben = aggregiereProContainer(FreigabeQuellTyp.AUSGANGS_DOKUMENT, mappingNeu);

        return mergeFreigabenProContainer(alteFreigaben, neueFreigaben);
    }

    private static Map<Long, DokumentFreigabe> mergeFreigabenProContainer(
            Map<Long, DokumentFreigabe> a, Map<Long, DokumentFreigabe> b)
    {
        Map<Long, DokumentFreigabe> result = new java.util.HashMap<>(a);
        b.forEach((containerId, freigabe) -> result.merge(containerId, freigabe, DokumentFreigabeService::pickRelevant));
        return result;
    }

    private Map<Long, DokumentFreigabe> aggregiereProContainer(FreigabeQuellTyp typ, List<Object[]> dokToContainerMapping)
    {
        if (dokToContainerMapping.isEmpty())
        {
            return Map.of();
        }
        Map<Long, Long> dokIdZuContainerId = new java.util.HashMap<>();
        for (Object[] row : dokToContainerMapping)
        {
            Long dokId = (Long) row[0];
            Long containerId = (Long) row[1];
            dokIdZuContainerId.put(dokId, containerId);
        }
        List<DokumentFreigabe> freigaben = repository.findByQuelle(typ, new java.util.ArrayList<>(dokIdZuContainerId.keySet()));
        Map<Long, DokumentFreigabe> result = new java.util.HashMap<>();
        for (DokumentFreigabe f : freigaben)
        {
            // EXPIRED-Update: Im Read-Path nicht persistieren – Wert ist trotzdem korrekt
            // (PENDING-Freigaben mit überschrittenem Ablauf werden vom GET-Endpoint
            // beim nächsten Abruf in der DB aktualisiert).
            FreigabeStatus effektiverStatus = f.getStatus() == FreigabeStatus.PENDING && f.istAbgelaufen()
                    ? FreigabeStatus.EXPIRED : f.getStatus();
            f.setStatus(effektiverStatus);
            Long containerId = dokIdZuContainerId.get(f.getQuellDokumentId());
            if (containerId == null) continue;
            result.merge(containerId, f, DokumentFreigabeService::pickRelevant);
        }
        return result;
    }

    private static DokumentFreigabe pickRelevant(DokumentFreigabe a, DokumentFreigabe b)
    {
        if (a.getStatus() == FreigabeStatus.ACCEPTED) return a;
        if (b.getStatus() == FreigabeStatus.ACCEPTED) return b;
        if (a.getStatus() == FreigabeStatus.PENDING && b.getStatus() != FreigabeStatus.PENDING) return a;
        if (b.getStatus() == FreigabeStatus.PENDING && a.getStatus() != FreigabeStatus.PENDING) return b;
        return a.getErstelltAm().isAfter(b.getErstelltAm()) ? a : b;
    }

    private DokumentFreigabe baseFreigabe(int gueltigkeitTage)
    {
        int tage = clampGueltigkeitTage(gueltigkeitTage);
        DokumentFreigabe freigabe = new DokumentFreigabe();
        freigabe.setUuid(UUID.randomUUID().toString());
        freigabe.setErstelltAm(LocalDateTime.now());
        freigabe.setAblaufDatum(LocalDateTime.now().plusDays(tage));
        freigabe.setStatus(FreigabeStatus.PENDING);
        return freigabe;
    }

    /**
     * Setzt alle noch ausstehenden (PENDING) Freigaben für dasselbe Quell-Dokument
     * auf REVOKED. Wird vor jeder Neu-Erstellung aufgerufen, damit nach einem erneuten
     * E-Mail-Versand (z.B. nach Rabatt-Verhandlung) der alte Link nicht mehr akzeptiert
     * werden kann und immer das aktuelle Angebot freigegeben wird.
     *
     * Bereits akzeptierte (ACCEPTED) oder abgelaufene (EXPIRED) Freigaben werden NICHT
     * verändert — die bleiben als historischer Beweis erhalten.
     */
    private void revokeAltePendingFreigaben(FreigabeQuellTyp quellTyp, Long quellDokumentId)
    {
        if (quellDokumentId == null) return;
        List<DokumentFreigabe> bestehende = repository.findByQuelle(quellTyp, List.of(quellDokumentId));
        LocalDateTime jetzt = LocalDateTime.now();
        for (DokumentFreigabe alt : bestehende)
        {
            if (alt.getStatus() == FreigabeStatus.PENDING)
            {
                alt.setStatus(FreigabeStatus.REVOKED);
                // Restdatei nach Möglichkeit aufräumen, damit keine alte PDF mehr abrufbar ist.
                String dateiname = alt.getDokumentDatei();
                if (dateiname != null && !dateiname.isBlank())
                {
                    try
                    {
                        dateiSpeicherService.loescheDokumentPdfByDateiname(dateiname);
                    }
                    catch (Exception ignored) { /* Datei evtl. schon weg */ }
                    alt.setDokumentDatei(null);
                }
                alt.setAblaufDatum(jetzt);
                repository.save(alt);
            }
        }
    }

    private String berechneHashOriginal(DokumentFreigabe f)
    {
        BigDecimal betrag = f.getDokumentBetrag();
        String input = String.join("|",
                f.getQuellTyp() == null ? "" : f.getQuellTyp().name(),
                String.valueOf(f.getQuellDokumentId()),
                f.getDokumentNummer() == null ? "" : f.getDokumentNummer(),
                f.getDokumentArt() == null ? "" : f.getDokumentArt(),
                betrag == null ? "" : betrag.toPlainString(),
                f.getKundeEmail() == null ? "" : f.getKundeEmail(),
                hashSalt
        );
        return sha256Hex(input);
    }

    private String berechneHashAcceptance(DokumentFreigabe freigabe, String ip, String email, LocalDateTime zeitpunkt)
    {
        String input = String.join("|",
                freigabe.getHashOriginal(),
                freigabe.getUuid(),
                ip == null ? "" : ip,
                email == null ? "" : email,
                zeitpunkt.toString(),
                // Unterzeichner-Name ist Teil der Beweissicherung — fließt damit in den
                // unveränderbaren Acceptance-Hash mit ein. Altdatensätze (vor V317) haben
                // keinen Namen — String.join behandelt null wie ein leeres Segment.
                freigabe.getUnterzeichnerName() == null ? "" : freigabe.getUnterzeichnerName(),
                hashSalt
        );
        return sha256Hex(input);
    }

    /**
     * Trimmt, kollabiert Whitespace und entfernt nicht-druckbare Steuerzeichen.
     * Liefert null, wenn der Eingang null oder nach Bereinigung leer ist.
     */
    private static String normalisiereName(String input)
    {
        if (input == null) return null;
        // Steuerzeichen (außer normalem Leerzeichen) raus, Tab/CR/LF zu Leerzeichen,
        // Mehrfach-Whitespace zu einem Leerzeichen, trimmen.
        String cleaned = input.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String sha256Hex(String input)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
