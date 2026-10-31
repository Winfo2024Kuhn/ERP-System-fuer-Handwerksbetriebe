package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Schlägt für einen gescannten Beleg einen Dateinamen vor
 * ({@link KiFunktion#SCAN_BENENNUNG}). Ohne KI oder bei einem Fehler gibt es
 * einen neutralen Namen mit Zeitstempel – der Scan geht nie verloren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanDateinameService {

    private static final Duration ZEITLIMIT = Duration.ofSeconds(60);

    private final KiDienst kiDienst;

    private static final String SYSTEM_PROMPT_FILENAME = """
            You are a smart file naming assistant.
            Analyze the document image and generate a concise, professional filename.

            Format: "{Type}_{Sender/Context}_{Date}"
            - Type: Rechnung, Vertrag, Brief, Notiz, etc. (German)
            - Sender: Company name or person if visible.
            - Date: YYYY-MM-DD (from the document) or TODAY if not found.

            Examples:
            - Rechnung_Amazon_2023-12-24
            - Brief_Finanzamt_2024-01-05
            - Notiz_Meeting_2024-03-10

            Rules:
            1. Return ONLY the filename string.
            2. No file extension.
            3. No spaces (use underscores).
            4. If text is illegible, return "Scan_{Timestamp}".
            """;

    public String generateFilename(byte[] imageBytes) {
        if (!kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)) {
            return ersatzName();
        }
        try {
            KiAnfrage anfrage = KiAnfrage.mitAnweisung(SYSTEM_PROMPT_FILENAME)
                    .nutzer(KiTeil.datei(imageBytes, "image/jpeg"), KiTeil.text("Generate a filename."))
                    .temperatur(0.0)
                    .zeitlimit(ZEITLIMIT)
                    .bauen();
            String text = kiDienst.sende(KiFunktion.SCAN_BENENNUNG, anfrage).text().trim();
            text = text.replace(" ", "_").replaceAll("[^a-zA-Z0-9_\\-]", "");
            return text.isEmpty() ? ersatzName() : text;
        } catch (RuntimeException e) {
            log.error("Failed to generate filename: {}", e.getClass().getSimpleName());
            return ersatzName();
        }
    }

    private static String ersatzName() {
        return "Scan_" + System.currentTimeMillis();
    }
}
