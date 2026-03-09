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

        // Prüfe ob Lohnabrechnung (Keywords im Dateinamen)
        if (isLohnabrechnungFilename(filenameLower)) {
            processLohnabrechnungPdf(attachment, email, steuerberater);
            return;
        }

        // Prüfe ob BWA (Keywords im Dateinamen)
        if (isBwaFilename(filenameLower)) {
            processBwaPdf(attachment, email, steuerberater);
            return;
        }

        // Fallback: Unbekanntes Dokument - vorerst ignorieren
        // KI-Analyse ist noch nicht implementiert
        log.info("[Steuerberater] Unbekanntes PDF, überspringe: {}", filename);
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
     * Verarbeitet eine Lohnabrechnung-PDF.
     */
    private void processLohnabrechnungPdf(EmailAttachment attachment, Email email, SteuerberaterKontakt steuerberater) {
        log.info("[Steuerberater] Verarbeite Lohnabrechnung: {}", attachment.getOriginalFilename());

        try {
            Path pdfPath = Paths.get(mailAttachmentDir, attachment.getStoredFilename());
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath);
                return;
            }
            if (lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(email.getId(), attachment.getOriginalFilename())) {
                log.info("[Steuerberater] Lohnabrechnung {} bereits importiert (Skipped)", attachment.getOriginalFilename());
                return;
            }

            byte[] fileBytes = Files.readAllBytes(pdfPath);

            String aiPrompt = """
                Analysiere dieses PDF. Es handelt sich um eine Lohnabrechnung.
                Extrahiere folgende Daten als JSON (nur das JSON, kein Markdown):
                {
                    "mitarbeiterName": "Vollständiger Name des Mitarbeiters",
                    "monat": 1-12,
                    "jahr": YYYY,
                    "bruttolohn": Betrag als Zahl (z.B. 2500.00),
                    "nettolohn": Betrag als Zahl (z.B. 1800.50),
                    "istSammelPdf": true/false
                }
                """;

            String aiResponse = geminiService.rufGeminiApiMitPrompt(fileBytes, "application/pdf", aiPrompt, true);
            createLohnabrechnungFromAiResponse(aiResponse, attachment, email, steuerberater);

        } catch (Exception e) {
            log.error("[Steuerberater] Fehler bei Lohnabrechnung-Verarbeitung: {}", e.getMessage(), e);
            createFallbackLohnabrechnung(attachment, email, steuerberater);
        }
    }

    /**
     * Erstellt Lohnabrechnung(en) aus KI-Antwort.
     */
    private void createLohnabrechnungFromAiResponse(String aiResponse, EmailAttachment attachment, 
            Email email, SteuerberaterKontakt steuerberater) {
        
        try {
            JsonNode root = null;
            if (aiResponse != null) {
                // Bereinige Markdown Blöcke
                String json = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
                if (json.startsWith("{")) {
                    root = objectMapper.readTree(json);
                }
            }

            Integer jahr = null;
            Integer monat = null;
            String mitarbeiterName = null;
            java.math.BigDecimal brutto = null;
            java.math.BigDecimal netto = null;

            if (root != null) {
                if (root.has("jahr")) jahr = root.get("jahr").asInt();
                if (root.has("monat")) monat = root.get("monat").asInt();
                if (root.has("mitarbeiterName")) mitarbeiterName = root.get("mitarbeiterName").asText();
                if (root.has("bruttolohn")) brutto = new java.math.BigDecimal(root.get("bruttolohn").asDouble());
                if (root.has("nettolohn")) netto = new java.math.BigDecimal(root.get("nettolohn").asDouble());
            }

            // Fallback auf Dateinamen wenn AI fehlschlägt oder Werte fehlen
            if (jahr == null || monat == null) {
                Integer[] periode = extractPeriodFromFilename(attachment.getOriginalFilename());
                if (jahr == null) jahr = periode[0];
                if (monat == null) monat = periode[1];
            }

            // Mitarbeiter finden
            Mitarbeiter mitarbeiter = null;
            if (mitarbeiterName != null && !mitarbeiterName.isBlank()) {
                mitarbeiter = findMitarbeiterByName(mitarbeiterName);
            }
            if (mitarbeiter == null) {
                mitarbeiter = findMitarbeiterFromFilename(attachment.getOriginalFilename());
            }

            if (mitarbeiter != null) {
                Lohnabrechnung la = new Lohnabrechnung();
                la.setMitarbeiter(mitarbeiter);
                la.setSteuerberater(steuerberater);
                la.setJahr(jahr);
                la.setMonat(monat);
                la.setOriginalDateiname(attachment.getOriginalFilename());
                la.setGespeicherterDateiname(attachment.getStoredFilename());
                la.setSourceEmail(email);
                la.setAiRawJson(aiResponse);
                la.setStatus(LohnabrechnungStatus.ANALYSIERT);
                la.setBruttolohn(brutto);
                la.setNettolohn(netto);
                la.setImportDatum(LocalDateTime.now());
                
                lohnabrechnungRepository.save(la);
                log.info("[Steuerberater] Lohnabrechnung für {} ({}/{}) erstellt", 
                        mitarbeiter.getNachname(), monat, jahr);
            } else {
                log.warn("[Steuerberater] Kein Mitarbeiter gefunden für: {}", attachment.getOriginalFilename());
                createFallbackLohnabrechnung(attachment, email, steuerberater);
            }

        } catch (Exception e) {
             log.error("Fehler beim Parsen der AI Antwort: {}", e.getMessage());
             createFallbackLohnabrechnung(attachment, email, steuerberater);
        }
    }

    private Mitarbeiter findMitarbeiterByName(String name) {
        String cleanName = name.toLowerCase();
        for (Mitarbeiter m : mitarbeiterRepository.findByAktivTrue()) {
             String n = m.getNachname().toLowerCase();
             String v = m.getVorname().toLowerCase();
             // Simple contains check
             if (cleanName.contains(n) && cleanName.contains(v)) {
                 return m;
             }
        }
        return null;
    }

    /**
     * Erstellt Lohnabrechnung ohne Mitarbeiter-Zuweisung (zur manuellen Zuordnung).
     */
    private void createFallbackLohnabrechnung(EmailAttachment attachment, Email email, 
            SteuerberaterKontakt steuerberater) {
        
        // TODO: Ohne Mitarbeiter speichern und zur manuellen Zuordnung markieren
        log.warn("[Steuerberater] Erstelle Lohnabrechnung ohne Mitarbeiter-Zuweisung: {}", 
                attachment.getOriginalFilename());
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
                Extrahiere folgende Daten als JSON:
                {
                    "monat": 1-12,
                    "jahr": YYYY,
                    "gesamtkosten": Betrag als Zahl (Summe Gesamtkosten),
                    "gemeinkosten": Betrag als Zahl (Summe Gemeinkosten, falls ausgewiesen),
                    "personalkosten": Betrag als Zahl
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
                     if (root.has("jahr")) jahr = root.get("jahr").asInt();
                     if (root.has("monat")) monat = root.get("monat").asInt();
                     if (root.has("gemeinkosten")) gemeinkosten = new java.math.BigDecimal(root.get("gemeinkosten").asDouble());
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
            // Kosten aus BWA übertragen (für Dashboard)
            if (gemeinkosten != null) {
                bwa.setKostenAusBwa(gemeinkosten);
                bwa.setAnalysiert(true);
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

    // TODO: KI-basierte PDF-Analyse implementieren
    // Vorerst werden PDFs nur anhand Keywords im Dateinamen klassifiziert
}
