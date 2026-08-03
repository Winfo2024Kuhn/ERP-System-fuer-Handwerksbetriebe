package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service für die automatische Verarbeitung von Steuerberater-E-Mails.
 * Erkennt und verarbeitet Lohnabrechnungen und BWA-Dokumente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteuerberaterEmailProcessingService {

    private final SteuerberaterKontaktRepository steuerberaterRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final LohnabrechnungRepository lohnabrechnungRepository;
    private final BwaUploadRepository bwaUploadRepository;
    private final EmailRepository emailRepository;
    private final GeminiDokumentAnalyseService geminiService;
    private final ObjectMapper objectMapper;

    /** "1.234" / "12.500" / "1.234.567" -- Punkte als Tausendertrenner. */
    private static final Pattern TAUSENDER_PUNKT = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})+$");

    /** "12,500" / "1,234,567" -- Kommas als Tausendertrenner (spiegelbildlich zu TAUSENDER_PUNKT). */
    private static final Pattern TAUSENDER_KOMMA = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");

    @Value("${file.lohnabrechnung-dir:uploads/lohnabrechnungen}")
    private String lohnabrechnungDir;

    @Value("${file.bwa-dir:uploads/bwa}")
    private String bwaDir;

    @Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    /**
     * Verarbeitet eine E-Mail und prüft ob sie vom Steuerberater stammt.
     * @return true wenn die E-Mail als Steuerberater-E-Mail verarbeitet wurde
     */
    @Transactional
    public boolean processSteuerberaterEmail(Email email) {
        if (email == null || email.getFromAddress() == null) {
            return false;
        }

        // Prüfe ob Absender ein Steuerberater ist
        SteuerberaterKontakt steuerberater = findSteuerberaterByEmail(email.getFromAddress());
        if (steuerberater == null) {
            return false;
        }

        log.info("[Steuerberater] E-Mail von Steuerberater erkannt: {} ({})", 
                steuerberater.getName(), email.getFromAddress());

        // E-Mail dem Steuerberater zuordnen
        email.assignToSteuerberater(steuerberater);
        emailRepository.save(email);

        // Anhänge verarbeiten
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            for (EmailAttachment attachment : email.getAttachments()) {
                processAttachment(attachment, email, steuerberater);
            }
        }

        return true;
    }

    /**
     * Findet Steuerberater anhand der E-Mail-Adresse oder Domain.
     */
    private SteuerberaterKontakt findSteuerberaterByEmail(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return null;
        }

        String emailLower = fromAddress.toLowerCase().trim();

        // 1. Exakter E-Mail-Match
        Optional<SteuerberaterKontakt> exact = steuerberaterRepository.findByEmailIgnoreCase(emailLower);
        if (exact.isPresent() && Boolean.TRUE.equals(exact.get().getAktiv())) {
            return exact.get();
        }

        // 2. Domain-Match (alle aktiven Steuerberater mit autoProcessEmails prüfen)
        String domain = emailLower.contains("@") 
                ? emailLower.substring(emailLower.lastIndexOf("@") + 1) 
                : null;
        
        if (domain != null) {
            List<SteuerberaterKontakt> aktive = steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue();
            for (SteuerberaterKontakt sb : aktive) {
                if (sb.getEmail() != null && sb.getEmail().toLowerCase().endsWith("@" + domain)) {
                    return sb;
                }
            }
        }

        return null;
    }

    /**
     * Verarbeitet einen einzelnen Anhang.
     */
    private void processAttachment(EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) {
        String filename = attachment.getOriginalFilename();
        if (filename == null) {
            return;
        }

        String filenameLower = filename.toLowerCase();

        // Nur PDFs verarbeiten
        if (!filenameLower.endsWith(".pdf")) {
            log.debug("[Steuerberater] Überspringe Nicht-PDF: {}", filename);
            return;
        }

        // BWA per Dateinamen-Keyword (eindeutig genug, spart einen KI-Call)
        if (isBwaFilename(filenameLower)) {
            processBwaPdf(attachment, email, steuerberater);
            return;
        }

        // Alle übrigen PDFs vom Steuerberater per KI klassifizieren –
        // Dateinamen sind nicht zuverlässig (z.B. "2026-05.pdf" ohne "lohn").
        processSteuerberaterPdf(attachment, email, steuerberater);
    }

    /**
     * Prüft ob Dateiname auf Lohnabrechnung hindeutet.
     */
    private boolean isLohnabrechnungFilename(String filename) {
        return filename.contains("lohn") || 
               filename.contains("gehalt") || 
               filename.contains("abrechnung") ||
               filename.contains("entgelt") ||
               filename.contains("verdienst");
    }

    /**
     * Prüft ob Dateiname auf BWA hindeutet.
     */
    private boolean isBwaFilename(String filename) {
        return filename.contains("bwa") || 
               filename.contains("summen") || 
               filename.contains("salden") ||
               filename.contains("betriebswirtschaftlich");
    }

    /**
     * Klassifiziert und verarbeitet eine Steuerberater-PDF per KI.
     * Lohnabrechnungen kommen als Sammel-PDF (alle Mitarbeiter in einer Datei):
     * Die KI liefert pro Abrechnung den Seitenbereich, die PDF wird gesplittet
     * und jede Teil-PDF dem passenden Mitarbeiter zugewiesen.
     */
    private void processSteuerberaterPdf(EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) {
        log.info("[Steuerberater] Analysiere PDF: {}", attachment.getOriginalFilename());

        try {
            Path pdfPath = Paths.get(mailAttachmentDir, attachment.getStoredFilename());
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath);
                return;
            }
            // Idempotenz: dieselbe E-Mail + Datei nie doppelt verarbeiten (Backfill)
            if (lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(email.getId(), attachment.getOriginalFilename())) {
                log.info("[Steuerberater] Anhang {} bereits importiert (Skipped)", attachment.getOriginalFilename());
                return;
            }

            byte[] fileBytes = Files.readAllBytes(pdfPath);

            String aiPrompt = """
                Du bekommst ein PDF, das ein Steuerberater per E-Mail an einen Handwerksbetrieb geschickt hat.

                1. Klassifiziere das Dokument:
                   - "LOHNABRECHNUNG": Lohn-/Gehalts-/Entgeltabrechnung(en)
                   - "BWA": Betriebswirtschaftliche Auswertung
                   - "SONSTIGES": alles andere

                2. Bei LOHNABRECHNUNG: Das PDF ist meist eine Sammel-PDF mit den Abrechnungen
                   MEHRERER Mitarbeiter hintereinander (je 1-2 Seiten pro Mitarbeiter).
                   Finde JEDE einzelne Abrechnung und gib ihren Seitenbereich an (1-basiert).
                   Achte auf den Mitarbeiternamen im Kopf jeder Abrechnung – ein neuer Name
                   bedeutet eine neue Abrechnung.

                Antworte NUR mit JSON (kein Markdown):
                {
                    "dokumentTyp": "LOHNABRECHNUNG",
                    "abrechnungen": [
                        {
                            "mitarbeiterName": "Vor- und Nachname",
                            "seiten": "1-2",
                            "monat": 1-12,
                            "jahr": YYYY,
                            "bruttolohn": Betrag als Zahl (z.B. 2500.00),
                            "nettolohn": Betrag als Zahl (z.B. 1800.50)
                        }
                    ]
                }
                Bei BWA oder SONSTIGES: "abrechnungen" als leeres Array.
                """;

            String aiResponse = geminiService.rufGeminiApiMitPrompt(fileBytes, "application/pdf", aiPrompt, true);

            JsonNode root = parseAiJson(aiResponse);
            String dokumentTyp = root != null && root.has("dokumentTyp")
                    ? root.get("dokumentTyp").asText("")
                    : "";

            if ("BWA".equalsIgnoreCase(dokumentTyp)) {
                processBwaPdf(attachment, email, steuerberater);
                return;
            }

            if ("LOHNABRECHNUNG".equalsIgnoreCase(dokumentTyp)
                    && root.has("abrechnungen") && root.get("abrechnungen").isArray()
                    && root.get("abrechnungen").size() > 0) {
                verarbeiteLohnabrechnungen(root.get("abrechnungen"), aiResponse, fileBytes, attachment, email, steuerberater);
                return;
            }

            // KI hat nicht geliefert: Dateinamen-Heuristik als letzte Rettung,
            // damit eine erkennbare Lohnabrechnung nicht verloren geht.
            if (isLohnabrechnungFilename(attachment.getOriginalFilename().toLowerCase())) {
                log.warn("[Steuerberater] KI-Klassifikation fehlgeschlagen, Dateiname deutet auf Lohnabrechnung: {}",
                        attachment.getOriginalFilename());
                verarbeiteLohnabrechnungFallback(aiResponse, fileBytes, attachment, email, steuerberater);
                return;
            }

            log.info("[Steuerberater] PDF als '{}' klassifiziert, keine Verarbeitung: {}",
                    dokumentTyp.isBlank() ? "unbekannt" : dokumentTyp, attachment.getOriginalFilename());

        } catch (Exception e) {
            log.error("[Steuerberater] Fehler bei PDF-Verarbeitung: {}", e.getMessage(), e);
        }
    }

    /**
     * Splittet die Sammel-PDF anhand der KI-Seitenbereiche und legt pro
     * Mitarbeiter eine Lohnabrechnung an. Korrektur-Läufe (Steuerberater schickt
     * geänderte Abrechnung für denselben Monat) ersetzen den alten Eintrag.
     */
    private void verarbeiteLohnabrechnungen(JsonNode abrechnungen, String aiResponse, byte[] fileBytes,
            EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) throws IOException {

        int erstellt = 0;
        int nichtZugeordnet = 0;

        try (PDDocument originalDoc = Loader.loadPDF(fileBytes)) {
            int totalPages = originalDoc.getNumberOfPages();

            for (JsonNode node : abrechnungen) {
                String mitarbeiterName = node.has("mitarbeiterName") ? node.get("mitarbeiterName").asText(null) : null;

                Mitarbeiter mitarbeiter = mitarbeiterName != null ? findMitarbeiterByName(mitarbeiterName) : null;
                if (mitarbeiter == null) {
                    nichtZugeordnet++;
                    log.warn("[Steuerberater] Kein Mitarbeiter gefunden für Abrechnung '{}' in {} – Segment übersprungen",
                            mitarbeiterName, attachment.getOriginalFilename());
                    continue;
                }

                Integer jahr = node.has("jahr") && node.get("jahr").canConvertToInt() ? node.get("jahr").asInt() : null;
                Integer monat = node.has("monat") && node.get("monat").canConvertToInt() ? node.get("monat").asInt() : null;
                if (jahr == null || monat == null || monat < 1 || monat > 12) {
                    Integer[] periode = extractPeriodFromFilename(attachment.getOriginalFilename());
                    if (jahr == null) jahr = periode[0];
                    if (monat == null || monat < 1 || monat > 12) monat = periode[1];
                }

                // Seitenbereich extrahieren und als eigene PDF speichern
                String seiten = node.has("seiten") ? node.get("seiten").asText("") : "";
                byte[] teilPdf = extrahiereSeiten(originalDoc, seiten, totalPages);
                String gespeicherterName = speichereLohnabrechnungPdf(teilPdf);

                speichereLohnabrechnung(mitarbeiter, jahr, monat,
                        node.has("bruttolohn") ? node.get("bruttolohn").decimalValue() : null,
                        node.has("nettolohn") ? node.get("nettolohn").decimalValue() : null,
                        gespeicherterName, aiResponse, attachment, email, steuerberater);
                erstellt++;
            }
        }

        log.info("[Steuerberater] Sammel-PDF {} verarbeitet: {} Lohnabrechnung(en) erstellt, {} nicht zuordenbar",
                attachment.getOriginalFilename(), erstellt, nichtZugeordnet);
    }

    /**
     * Fallback ohne brauchbare KI-Antwort: ganze PDF als eine Abrechnung,
     * Mitarbeiter und Periode aus dem Dateinamen.
     */
    private void verarbeiteLohnabrechnungFallback(String aiResponse, byte[] fileBytes,
            EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) throws IOException {

        Mitarbeiter mitarbeiter = findMitarbeiterFromFilename(attachment.getOriginalFilename());
        if (mitarbeiter == null) {
            log.warn("[Steuerberater] Auch im Dateinamen kein Mitarbeiter erkennbar: {} – manuell prüfen!",
                    attachment.getOriginalFilename());
            return;
        }

        Integer[] periode = extractPeriodFromFilename(attachment.getOriginalFilename());
        String gespeicherterName = speichereLohnabrechnungPdf(fileBytes);
        speichereLohnabrechnung(mitarbeiter, periode[0], periode[1], null, null,
                gespeicherterName, aiResponse, attachment, email, steuerberater);
    }

    /**
     * Legt eine Lohnabrechnung an oder ersetzt die bestehende desselben
     * Mitarbeiters für denselben Monat (Korrektur-Mail vom Steuerberater).
     */
    private void speichereLohnabrechnung(Mitarbeiter mitarbeiter, Integer jahr, Integer monat,
            java.math.BigDecimal brutto, java.math.BigDecimal netto, String gespeicherterDateiname,
            String aiResponse, EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) {

        Lohnabrechnung la = lohnabrechnungRepository
                .findByMitarbeiterIdAndJahrAndMonat(mitarbeiter.getId(), jahr, monat)
                .orElseGet(Lohnabrechnung::new);

        boolean ersetzt = la.getId() != null;
        if (ersetzt) {
            loescheAlteSplitDatei(la.getGespeicherterDateiname());
        }

        la.setMitarbeiter(mitarbeiter);
        la.setSteuerberater(steuerberater);
        la.setJahr(jahr);
        la.setMonat(monat);
        la.setOriginalDateiname(attachment.getOriginalFilename());
        la.setGespeicherterDateiname(gespeicherterDateiname);
        la.setSourceEmail(email);
        la.setAiRawJson(aiResponse);
        la.setStatus(LohnabrechnungStatus.ANALYSIERT);
        la.setBruttolohn(brutto);
        la.setNettolohn(netto);
        la.setImportDatum(LocalDateTime.now());

        lohnabrechnungRepository.save(la);
        log.info("[Steuerberater] Lohnabrechnung für {} ({}/{}) {}",
                mitarbeiter.getNachname(), monat, jahr, ersetzt ? "aktualisiert (Korrektur)" : "erstellt");
    }

    /**
     * Löscht die alte gesplittete Teil-PDF einer ersetzten Lohnabrechnung.
     * Original-Mail-Anhänge (mail-attachment-dir) werden nie angefasst.
     */
    private void loescheAlteSplitDatei(String gespeicherterDateiname) {
        if (gespeicherterDateiname == null || gespeicherterDateiname.isBlank()) {
            return;
        }
        try {
            Path basis = Paths.get(lohnabrechnungDir).toAbsolutePath().normalize();
            Path datei = basis.resolve(gespeicherterDateiname).normalize();
            if (datei.startsWith(basis)) {
                Files.deleteIfExists(datei);
            }
        } catch (IOException e) {
            log.warn("[Steuerberater] Alte Lohnabrechnungs-PDF konnte nicht gelöscht werden: {}", e.getMessage());
        }
    }

    /**
     * Speichert eine (Teil-)PDF im Lohnabrechnungs-Verzeichnis unter UUID-Namen.
     */
    private String speichereLohnabrechnungPdf(byte[] pdfBytes) throws IOException {
        Path dir = Paths.get(lohnabrechnungDir);
        Files.createDirectories(dir);
        String dateiname = UUID.randomUUID() + ".pdf";
        Files.write(dir.resolve(dateiname), pdfBytes);
        return dateiname;
    }

    /**
     * Extrahiert einen 1-basierten Seitenbereich ("3" oder "1-2") als neue PDF.
     * Ungültige Angaben liefern das gesamte Dokument.
     */
    private byte[] extrahiereSeiten(PDDocument original, String seitenRange, int totalPages) throws IOException {
        int von = 1;
        int bis = totalPages;

        if (seitenRange != null && !seitenRange.isBlank()) {
            Matcher m = Pattern.compile("(\\d+)\\s*(?:-\\s*(\\d+))?").matcher(seitenRange.trim());
            if (m.matches()) {
                von = Integer.parseInt(m.group(1));
                bis = m.group(2) != null ? Integer.parseInt(m.group(2)) : von;
            }
        }
        von = Math.max(1, Math.min(von, totalPages));
        bis = Math.max(von, Math.min(bis, totalPages));

        try (PDDocument teil = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = von - 1; i < bis; i++) {
                teil.addPage(original.getPage(i));
            }
            teil.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Parst die KI-Antwort tolerant (Markdown-Blöcke entfernen).
     */
    private JsonNode parseAiJson(String aiResponse) {
        if (aiResponse == null) {
            return null;
        }
        try {
            String json = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            if (json.startsWith("{")) {
                return objectMapper.readTree(json);
            }
        } catch (Exception e) {
            log.warn("[Steuerberater] KI-Antwort kein gültiges JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Findet Mitarbeiter anhand des von der KI extrahierten Namens.
     * Erst Vor- und Nachname, dann eindeutiger Nachname-Match.
     */
    private Mitarbeiter findMitarbeiterByName(String name) {
        String cleanName = name.toLowerCase();
        List<Mitarbeiter> aktive = mitarbeiterRepository.findByAktivTrue();

        for (Mitarbeiter m : aktive) {
            if (cleanName.contains(m.getNachname().toLowerCase())
                    && cleanName.contains(m.getVorname().toLowerCase())) {
                return m;
            }
        }

        // Nachname allein reicht, wenn er eindeutig ist (KI kürzt Vornamen manchmal ab)
        Mitarbeiter einziger = null;
        for (Mitarbeiter m : aktive) {
            if (cleanName.contains(m.getNachname().toLowerCase())) {
                if (einziger != null) {
                    return null; // mehrdeutig
                }
                einziger = m;
            }
        }
        return einziger;
    }

    /**
     * Extrahiert Jahr/Monat aus Dateiname.
     * Patterns: "2025-01", "01-2025", "Januar 2025", etc.
     */
    private Integer[] extractPeriodFromFilename(String filename) {
        int currentYear = java.time.LocalDate.now().getYear();
        int currentMonth = java.time.LocalDate.now().getMonthValue();
        
        if (filename == null) {
            return new Integer[]{currentYear, currentMonth};
        }

        // Pattern: 2025-01, 2025_01, 202501
        Pattern yearMonthPattern = Pattern.compile("(20\\d{2})[_-]?(0[1-9]|1[0-2])");
        Matcher m1 = yearMonthPattern.matcher(filename);
        if (m1.find()) {
            return new Integer[]{Integer.parseInt(m1.group(1)), Integer.parseInt(m1.group(2))};
        }

        // Pattern: 01-2025, 01_2025
        Pattern monthYearPattern = Pattern.compile("(0[1-9]|1[0-2])[_-](20\\d{2})");
        Matcher m2 = monthYearPattern.matcher(filename);
        if (m2.find()) {
            return new Integer[]{Integer.parseInt(m2.group(2)), Integer.parseInt(m2.group(1))};
        }

        // Default: Vormonat (Lohnabrechnung kommt meist für Vormonat)
        int prevMonth = currentMonth == 1 ? 12 : currentMonth - 1;
        int prevYear = currentMonth == 1 ? currentYear - 1 : currentYear;
        return new Integer[]{prevYear, prevMonth};
    }

    /**
     * Findet Mitarbeiter anhand von Name im Dateinamen.
     */
    private Mitarbeiter findMitarbeiterFromFilename(String filename) {
        if (filename == null) {
            return null;
        }

        String cleanFilename = filename.toLowerCase()
                .replaceAll("[_\\-.]", " ")
                .replaceAll("\\s+", " ");

        List<Mitarbeiter> alleMitarbeiter = mitarbeiterRepository.findByAktivTrue();
        
        for (Mitarbeiter ma : alleMitarbeiter) {
            String nachname = ma.getNachname().toLowerCase();
            String vorname = ma.getVorname().toLowerCase();
            
            // Prüfe ob Nachname im Dateinamen vorkommt
            if (cleanFilename.contains(nachname)) {
                return ma;
            }
            
            // Prüfe Vorname + Nachname
            if (cleanFilename.contains(vorname) && cleanFilename.contains(nachname)) {
                return ma;
            }
        }

        return null;
    }

    /**
     * Verarbeitet eine BWA-PDF.
     */
    private void processBwaPdf(EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) {
        log.info("[Steuerberater] Verarbeite BWA: {}", attachment.getOriginalFilename());

        if (bwaUploadRepository.existsBySourceEmailIdAndOriginalDateiname(email.getId(), attachment.getOriginalFilename())) {
            log.info("[Steuerberater] BWA {} bereits importiert (Skipped)", attachment.getOriginalFilename());
            return;
        }

        try {
            Path pdfPath = Paths.get(mailAttachmentDir, attachment.getStoredFilename());
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath);
                return;
            }
            byte[] fileBytes = Files.readAllBytes(pdfPath);

            String aiPrompt = """
                Analysiere dieses PDF. Es handelt sich um eine BWA (Betriebswirtschaftliche Auswertung).
                Extrahiere folgende Daten als JSON. Alle Betraege als reine Zahl in Euro,
                Punkt als Dezimaltrenner, ohne Tausenderpunkte und ohne Waehrungszeichen.
                Wenn ein Wert in der BWA nicht vorkommt, gib null zurueck (nicht 0).
                {
                    "monat": 1-12,
                    "jahr": YYYY,
                    "gesamtkosten": Summe aller Kosten des Monats,
                    "gemeinkosten": Summe Gemeinkosten, nur falls die BWA sie explizit ausweist, sonst null,
                    "personalkosten": Summe Personalkosten (Loehne, Gehaelter, soziale Abgaben),
                    "wareneinsatz": Wareneinsatz / Materialaufwand / Roh-, Hilfs- und Betriebsstoffe,
                    "fremdleistungen": bezogene Leistungen von Subunternehmern
                }
                """;

            String aiResponse = geminiService.rufGeminiApiMitPrompt(fileBytes, "application/pdf", aiPrompt, true);

            // JSON Parsen
            Integer jahr = null;
            Integer monat = null;
            java.math.BigDecimal gemeinkosten = null;

            if (aiResponse != null) {
                String json = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
                if (json.startsWith("{")) {
                     JsonNode root = objectMapper.readTree(json);
                     if (root.hasNonNull("jahr")) jahr = root.get("jahr").asInt();
                     if (root.hasNonNull("monat")) monat = root.get("monat").asInt();
                     gemeinkosten = ermittleGemeinkosten(root);
                }
            }

            // Fallback auf Dateiname
            if (jahr == null || monat == null) {
                Integer[] periode = extractPeriodFromFilename(attachment.getOriginalFilename());
                if (jahr == null) jahr = periode[0];
                if (monat == null) monat = periode[1];
            }

            BwaUpload bwa = new BwaUpload();
            bwa.setTyp(BwaTyp.MONATLICH);
            bwa.setJahr(jahr);
            bwa.setMonat(monat);
            bwa.setOriginalDateiname(attachment.getOriginalFilename());
            bwa.setGespeicherterDateiname(attachment.getStoredFilename());
            bwa.setUploadDatum(LocalDateTime.now());
            bwa.setSteuerberater(steuerberater);
            bwa.setSourceEmail(email);
            // Gemeinkosten übertragen (für Dashboard und Stundensatz-Rechner).
            // gesamtGemeinkosten ist das Feld, das die Oberfläche anzeigt --
            // kostenAusBwa bleibt dem Rechnungs-Abgleich vorbehalten.
            if (gemeinkosten != null) {
                bwa.setGesamtGemeinkosten(gemeinkosten);
                bwa.setAnalysiert(true);
                bwa.setAnalyseDatum(LocalDateTime.now());
            } else {
                // Kein verwertbarer Betrag erkannt: NICHT als analysiert markieren,
                // sonst zeigt die Karte gruen "Analysiert" neben 0,00 EUR an.
                log.warn("[Steuerberater] BWA {} enthaelt keine erkennbaren Gemeinkosten - bleibt 'Ausstehend'",
                        attachment.getOriginalFilename());
            }
            bwa.setAiRawJson(aiResponse);

            bwaUploadRepository.save(bwa);
            log.info("[Steuerberater] BWA für {}/{} erstellt (Gemeinkosten: {})", monat, jahr, gemeinkosten);

        } catch (Exception e) {
             log.error("[Steuerberater] Fehler bei BWA-Verarbeitung: {}", e.getMessage(), e);
             // Fallback ohne KI
             Integer[] periode = extractPeriodFromFilename(attachment.getOriginalFilename());
             BwaUpload bwa = new BwaUpload();
             bwa.setTyp(BwaTyp.MONATLICH);
             bwa.setJahr(periode[0]);
             bwa.setMonat(periode[1]);
             bwa.setOriginalDateiname(attachment.getOriginalFilename());
             bwa.setGespeicherterDateiname(attachment.getStoredFilename());
             bwa.setUploadDatum(LocalDateTime.now());
             bwa.setSteuerberater(steuerberater);
             bwa.setSourceEmail(email);
             bwaUploadRepository.save(bwa);
        }
    }

    /**
     * Ermittelt die Gemeinkosten aus der KI-Antwort zu einer BWA.
     *
     * Reihenfolge:
     * 1. Feld "gemeinkosten", wenn die BWA es explizit ausweist.
     * 2. Sonst "gesamtkosten" minus Personalkosten, Wareneinsatz und
     *    Fremdleistungen -- in einer Standard-BWA (SKR 03/04) gibt es keine
     *    Zeile "Gemeinkosten", wohl aber diese Summen. Abgezogen wird alles,
     *    was einem Auftrag direkt zurechenbar ist: Loehne stecken im
     *    Stundensatz-Rechner schon im Lohn-Block, Material und
     *    Subunternehmer-Leistungen werden dem Projekt direkt berechnet.
     *    Bleiben sie drin, faellt der Stundensatz systematisch zu hoch aus.
     * 3. Sonst null -- die BWA gilt dann bewusst als nicht analysiert.
     *
     * @return Betrag &gt; 0 auf zwei Nachkommastellen oder null, nie 0
     */
    static java.math.BigDecimal ermittleGemeinkosten(JsonNode root) {
        java.math.BigDecimal direkt = leseBetrag(root, "gemeinkosten");
        if (direkt != null && direkt.signum() > 0) {
            return skaliere(direkt);
        }
        java.math.BigDecimal gesamt = leseBetrag(root, "gesamtkosten");
        if (gesamt == null || gesamt.signum() <= 0) {
            return null;
        }
        java.math.BigDecimal rest = gesamt
                .subtract(nz(leseBetrag(root, "personalkosten")))
                .subtract(nz(leseBetrag(root, "wareneinsatz")))
                .subtract(nz(leseBetrag(root, "fremdleistungen")));
        return rest.signum() > 0 ? skaliere(rest) : null;
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    /** Auf die Genauigkeit der Spalte bringen, statt die DB still runden zu lassen. */
    private static java.math.BigDecimal skaliere(java.math.BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Liest einen Geldbetrag aus einem JSON-Feld. Toleriert Zahlen und Strings
     * ("1.234,56", "1234.56 EUR"), weil die KI beides liefern kann.
     * {@code hasNonNull} ist Pflicht: bei explizitem JSON-null wuerde
     * {@code asDouble()} stillschweigend 0 liefern.
     *
     * @return der Betrag als Absolutwert oder null, wenn nichts Verwertbares drinsteht
     */
    private static java.math.BigDecimal leseBetrag(JsonNode root, String feld) {
        if (root == null || !root.hasNonNull(feld)) {
            return null;
        }
        JsonNode node = root.get(feld);
        if (node.isNumber()) {
            // valueOf statt new BigDecimal(double): sonst 1234.5599999999999
            return java.math.BigDecimal.valueOf(node.asDouble()).abs();
        }
        if (!node.isTextual()) {
            return null;
        }
        String roh = node.asText().replaceAll("[^0-9,.-]", "").trim();
        if (roh.isEmpty()) {
            return null;
        }
        int letztesKomma = roh.lastIndexOf(',');
        int letzterPunkt = roh.lastIndexOf('.');
        String normalisiert;
        if (letztesKomma >= 0 && letzterPunkt >= 0) {
            // Beide Zeichen vorhanden: das zuletzt stehende ist das Dezimalzeichen.
            // "1.234,56" (deutsch) und "1,234.56" (englisch) werden so beide richtig
            // gelesen -- vorher ergab "1,234.56" 1,23 EUR, Faktor 1000 daneben.
            normalisiert = letztesKomma > letzterPunkt
                    ? roh.replace(".", "").replace(',', '.')
                    : roh.replace(",", "");
        } else if (letzterPunkt >= 0) {
            // Nur Punkte: "12.500" und "1.234.567" sind Tausendertrenner,
            // "12500.50" und "1.5" sind Dezimalzahlen. Ohne diese Unterscheidung
            // wuerde aus 12.500 EUR still ein Betrag von 12,50 EUR.
            normalisiert = TAUSENDER_PUNKT.matcher(roh).matches()
                    ? roh.replace(".", "")
                    : roh;
        } else if (letztesKomma >= 0) {
            // Nur Kommas -- spiegelbildlich zur Punkt-Seite: exakt dreistellige
            // Gruppen ("12,500", "1,234,567") sind Tausendertrenner, alles
            // andere ist das deutsche Dezimalkomma ("1234,56", "0,50").
            // Der Prompt verlangt den Punkt als Dezimaltrenner; liefert die KI
            // trotzdem ein Komma mit genau drei Folgeziffern, ist das die
            // englische Tausenderschreibweise -- ein Geldbetrag mit drei
            // Nachkommastellen kommt in einer BWA nicht vor.
            normalisiert = TAUSENDER_KOMMA.matcher(roh).matches()
                    ? roh.replace(",", "")
                    : roh.replace(',', '.');
        } else {
            normalisiert = roh;
        }
        try {
            return new java.math.BigDecimal(normalisiert).abs();
        } catch (NumberFormatException e) {
            log.warn("[Steuerberater] Betrag '{}' im Feld '{}' nicht lesbar", node.asText(), feld);
            return null;
        }
    }

}
