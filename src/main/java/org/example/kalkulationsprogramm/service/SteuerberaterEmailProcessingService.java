package org.example.kalkulationsprogramm.service;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einstieg für Steuerberater-E-Mails: erkennt den Absender (über
 * {@link SteuerberaterAbsenderErkennung}), ordnet die Mail dem Steuerberater zu und
 * verteilt die PDF-Anhänge. BWAs legt {@link SteuerberaterBwaAblage} ab, Lohnabrechnungen
 * {@link SteuerberaterLohnabrechnungAblage}; welche PDF was ist, entscheidet der Dateiname
 * oder die KI.
 */
@Slf4j
@Service
public class SteuerberaterEmailProcessingService {

    private static final String KLASSIFIZIERUNGS_PROMPT = """
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

    private final SteuerberaterAbsenderErkennung absenderErkennung;
    private final SteuerberaterLohnabrechnungAblage lohnabrechnungAblage;
    private final SteuerberaterBwaAblage bwaAblage;
    private final EmailRepository emailRepository;
    private final LohnabrechnungRepository lohnabrechnungRepository;
    private final DokumentAnalyseService dokumentAnalyseService;
    private final ObjectMapper objectMapper;
    private final String mailAttachmentDir;

    public SteuerberaterEmailProcessingService(SteuerberaterAbsenderErkennung absenderErkennung,
            SteuerberaterLohnabrechnungAblage lohnabrechnungAblage,
            SteuerberaterBwaAblage bwaAblage,
            EmailRepository emailRepository,
            LohnabrechnungRepository lohnabrechnungRepository,
            DokumentAnalyseService dokumentAnalyseService,
            ObjectMapper objectMapper,
            @Value("${file.mail-attachment-dir}") String mailAttachmentDir) {
        this.absenderErkennung = absenderErkennung;
        this.lohnabrechnungAblage = lohnabrechnungAblage;
        this.bwaAblage = bwaAblage;
        this.emailRepository = emailRepository;
        this.lohnabrechnungRepository = lohnabrechnungRepository;
        this.dokumentAnalyseService = dokumentAnalyseService;
        this.objectMapper = objectMapper;
        this.mailAttachmentDir = mailAttachmentDir;
    }

    /**
     * Verarbeitet eine E-Mail und prüft ob sie vom Steuerberater stammt.
     * @return true wenn die E-Mail als Steuerberater-E-Mail verarbeitet wurde
     */
    @Transactional
    public boolean processSteuerberaterEmail(Email email) {
        if (email == null || email.getFromAddress() == null) {
            return false;
        }

        SteuerberaterKontakt steuerberater = absenderErkennung.erkenne(email.getFromAddress()).orElse(null);
        if (steuerberater == null) {
            return false;
        }

        log.info("[Steuerberater] E-Mail von Steuerberater erkannt: {} ({})",
                steuerberater.getName(), email.getFromAddress());

        email.assignToSteuerberater(steuerberater);
        emailRepository.save(email);

        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            for (EmailAttachment attachment : email.getAttachments()) {
                processAttachment(new SteuerberaterAnhang(attachment, email, steuerberater));
            }
        }

        return true;
    }

    /**
     * Verarbeitet einen einzelnen Anhang.
     */
    private void processAttachment(SteuerberaterAnhang quelle) {
        String filename = quelle.originalDateiname();
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
            bwaAblage.legeAb(quelle);
            return;
        }

        // Alle übrigen PDFs vom Steuerberater per KI klassifizieren –
        // Dateinamen sind nicht zuverlässig (z.B. "2026-05.pdf" ohne "lohn").
        processSteuerberaterPdf(quelle);
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
     * Klassifiziert eine Steuerberater-PDF per KI und reicht sie weiter.
     * Lohnabrechnungen kommen als Sammel-PDF (alle Mitarbeiter in einer Datei):
     * Die KI liefert pro Abrechnung den Seitenbereich, die Lohnabrechnungs-Ablage
     * splittet die PDF und weist jede Teil-PDF dem passenden Mitarbeiter zu.
     */
    private void processSteuerberaterPdf(SteuerberaterAnhang quelle) {
        String dateiname = quelle.originalDateiname();
        log.info("[Steuerberater] Analysiere PDF: {}", dateiname);

        try {
            Path pdfPath = Path.of(mailAttachmentDir, quelle.anhang().getStoredFilename());
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath);
                return;
            }
            // Idempotenz: dieselbe E-Mail + Datei nie doppelt verarbeiten (Backfill)
            if (lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(quelle.email().getId(), dateiname)) {
                log.info("[Steuerberater] Anhang {} bereits importiert (Skipped)", dateiname);
                return;
            }

            byte[] fileBytes = Files.readAllBytes(pdfPath);
            String aiResponse = dokumentAnalyseService.rufKiMitPrompt(
                    fileBytes, "application/pdf", KLASSIFIZIERUNGS_PROMPT, true);

            JsonNode root = parseAiJson(aiResponse);
            String dokumentTyp = root != null && root.has("dokumentTyp")
                    ? root.get("dokumentTyp").asString("")
                    : "";

            if ("BWA".equalsIgnoreCase(dokumentTyp)) {
                bwaAblage.legeAb(quelle);
                return;
            }

            if ("LOHNABRECHNUNG".equalsIgnoreCase(dokumentTyp)
                    && root.has("abrechnungen") && root.get("abrechnungen").isArray()
                    && root.get("abrechnungen").size() > 0) {
                lohnabrechnungAblage.legeSammelPdfAb(root.get("abrechnungen"), aiResponse, fileBytes, quelle);
                return;
            }

            // KI hat nicht geliefert: Dateinamen-Heuristik als letzte Rettung,
            // damit eine erkennbare Lohnabrechnung nicht verloren geht.
            if (isLohnabrechnungFilename(dateiname.toLowerCase())) {
                log.warn("[Steuerberater] KI-Klassifikation fehlgeschlagen, Dateiname deutet auf Lohnabrechnung: {}",
                        dateiname);
                lohnabrechnungAblage.legeNachDateinamenAb(aiResponse, fileBytes, quelle);
                return;
            }

            log.info("[Steuerberater] PDF als '{}' klassifiziert, keine Verarbeitung: {}",
                    dokumentTyp.isBlank() ? "unbekannt" : dokumentTyp, dateiname);

        } catch (Exception e) {
            log.error("[Steuerberater] Fehler bei PDF-Verarbeitung: {}", e.getMessage(), e);
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
}
