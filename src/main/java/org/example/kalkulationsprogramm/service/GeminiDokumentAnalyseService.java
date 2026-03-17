package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service zur KI-gestützten Analyse von Lieferanten-Dokumenten.
 * 
 * Reihenfolge der Datenextraktion:
 * 1. ZUGFeRD-PDF prüfen (strukturierte Daten eingebettet)
 * 2. XML-Datei prüfen (maschinenlesbar)
 * 3. Fallback: Gemini AI für OCR/Analyse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiDokumentAnalyseService {

    private final ObjectMapper objectMapper;
    private final LieferantenRepository lieferantenRepository;
    private final LieferantDokumentRepository dokumentRepository;
    private final ZugferdExtractorService zugferdExtractorService;
    private final LieferantenArtikelPreiseRepository artikelPreiseRepository;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final SystemSettingsService systemSettingsService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Synchronized lock to ensure sequential API calls (prevents rate limiting &
    // race conditions)
    private static final ReentrantLock API_LOCK = new ReentrantLock(true); // fair lock

    // Minimum delay between API calls (ms) to prevent rate limiting
    private static final long API_CALL_DELAY_MS = 500;
    private static volatile long lastApiCallTime = 0;

    // Retry configuration for transient failures
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000; // 2 seconds, doubles each retry

    @Value("${ai.gemini.model.dokument-analyse:gemini-3-flash-preview}")
    private String geminiModel;

    @Value("${ai.gemini.model.pro:gemini-3.1-pro-preview}")
    private String geminiProModel;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    private static final String SYSTEM_PROMPT_DOKUMENT_ANALYSE = """
            Du bist ein Experte für die Analyse von deutschen Geschäftsdokumenten.

            !!! KRITISCHE PRÜFUNG: DOKUMENT-GÜLTIGKEIT !!!
            Prüfe das Dokument EXTREM SORGFÄLTIG auf Wasserzeichen oder großflächige Texte wie:
            - "Abschrift" (sehr häufig bei Kopien!)
            - "Kopie"
            - "Entwurf"
            - "Duplikat"
            - "Nicht zur Verbuchung"
            Falls einer dieser Begriffe irgendwo im Dokument (oft diagonal im Hintergrund als Wasserzeichen) sichtbar ist, ist das Dokument KEIN offizielles Original.
            DIES IST ESSENZIELL FÜR DIE BUCHHALTUNG.

            FALLS EINE KOPIE/ABSCHRIFT ERKANNT WIRD:
            Hänge zwingend " (Kopie)" an den dokumentTyp an (Beispiel: "RECHNUNG (Kopie)").

            DOKUMENTTYP-KLASSIFIKATION (WICHTIG - lies sorgfältig!):

            1. ANGEBOT:
               - Titel enthält: "Angebot", "Offerte", "Kostenvoranschlag"
               - Typische Nummern: AN-..., A-..., Angebots-Nr...
               - Hat KEINE Zahlungsaufforderung, keinen Fälligkeitstermin
               - Ist unverbindlich, Kunde muss erst bestellen

            2. AUFTRAGSBESTAETIGUNG (AB):
               - Titel enthält: "Auftragsbestätigung", "AB", "Bestellbestätigung", "Order Confirmation"
               - Typische Nummern: AB-..., AUF-..., Auftrags-Nr..., Bestätigung-Nr...
               - Bestätigt eine eingegangene Bestellung
               - Hat oft Liefertermin, aber KEINE Zahlungsaufforderung
               - Referenziert oft eine Angebotsnummer oder Bestellnummer

            3. LIEFERSCHEIN:
               - Titel enthält: "Lieferschein", "Warenbegleitschein", "Packing Slip"
               - Typische Nummern: LS-..., L-..., Lieferschein-Nr...
               - Dokumentiert gelieferte Waren
               - Hat KEINEN Gesamtbetrag mit MwSt, oft nur Stückzahlen

            4. RECHNUNG:
               - Titel enthält: "Rechnung", "Invoice", "Faktura"
               - Typische Nummern: RE-..., R-..., Rechnung-Nr..., Invoice-Nr...
               - Hat IMMER einen Bruttobetrag mit MwSt-Ausweis
               - Hat IMMER Zahlungsziel/Fälligkeitsdatum oder Bankverbindung
               - Fordert zur Zahlung auf

            5. GUTSCHRIFT (Credit Note):
               - Korrektur einer vorherigen Rechnung (Retoure, Nachlass, Rabatt)
               - Enthält oft Bezug auf Originalrechnung
               - Hat negativen Betrag oder "Gutschrift" im Titel
               - Keine Zahlungsaufforderung, sondern Guthaben

            6. SONSTIG (KEIN GESCHÄFTSDOKUMENT):
               - Kataloge, Produktinformationen, Werbematerial
               - Newsletter, Rundschreiben, Infoschreiben
               - Allgemeine Korrespondenz ohne Geschäftsvorgang
               - Technische Datenblätter, Zertifikate
               - ALLES was NICHT Angebot/AB/Lieferschein/Rechnung/Gutschrift ist
               - WICHTIG: Wenn du dir nicht sicher bist ob es ein Geschäftsdokument ist,
                 dann wähle SONSTIG und setze confidence auf 0.0!

            EXTRAHIERE die folgenden Informationen als JSON:

            {
                "dokumentTyp": "ANGEBOT|AUFTRAGSBESTAETIGUNG|LIEFERSCHEIN|RECHNUNG|GUTSCHRIFT|SONSTIG (ggf. mit Vermerk ' (Kopie)')",
                "istGeschaeftsdokument": true/false,
                "dokumentNummer": "Die Dokumentnummer (exakt wie im Dokument) oder null",
                "dokumentDatum": "YYYY-MM-DD oder null",
                "betragNetto": 1234.56 oder null,
                "betragBrutto": 1469.33 oder null,
                "mwstSatz": 0.19 oder null,
                "liefertermin": "YYYY-MM-DD oder null",
                "zahlungsziel": "YYYY-MM-DD (berechnetes Fälligkeitsdatum) oder null",
                "bestellnummer": "Unsere Bestellnummer falls erwähnt oder null",
                "referenzNummer": "Nummer die auf ein VORHERIGES Dokument verweist. PRIORITÄT je nach Dokumenttyp: Bei AB→Anfrages-Nr./Anfrage-Nr. suchen. Bei RECHNUNG/LIEFERSCHEIN→Auftrags-Nr./AB-Nr. suchen. Bei GUTSCHRIFT→Rechnungs-Nr. suchen. Suche nach: 'Ihr Angebot', 'Angebots-Nr.', 'Auftrags-Nr.', 'AB-Nr.', 'Ihre Bestellung', 'Rechnungs-Nr.'",
                "bereitsGezahlt": true/false,
                "skontoTage": 8 oder null,
                "skontoProzent": 2.0 oder null,
                "nettoTage": 30 oder null,
                "confidence": 0.0-1.0,
                "artikelPositionen": [...],
                "lieferantName": "Name des Lieferanten/Absenders",
                "lieferantStrasse": "Straße und Hausnummer",
                "lieferantPlz": "Postleitzahl",
                "lieferantOrt": "Ort"
            }

            WICHTIG FÜR NICHT-GESCHÄFTSDOKUMENTE:
            - Wenn dokumentTyp = "SONSTIG", dann setze istGeschaeftsdokument = false
            - Wenn dokumentTyp = "SONSTIG", dann sind alle anderen Felder (dokumentNummer, betrag etc.) = null
            - confidence sollte bei SONSTIG = 0.0 sein, da keine relevanten Daten extrahiert werden

            ARTIKELPOSITIONEN EXTRAKTION (WICHTIG für Rechnungen):
            - Extrahiere ALLE Positionen mit Artikelnummer/Materialnummer
            - Die externe Artikelnummer ist oft eine Materialnummer wie "12345" oder "MAT-001"
            - Die Preiseinheit ist entscheidend: "€/t" = pro Tonne, "€/100kg" = pro 100kg, "€/kg" = pro kg
            - Falls keine Preiseinheit erkennbar, nimm "kg" an

            ZAHLUNGSBEDINGUNGEN ERKENNUNG (SEHR WICHTIG!):
            Typische Muster auf deutschen Rechnungen:
            - "8 TAGE 2% 30 TAGE NETTO" → skontoTage=8, skontoProzent=2, nettoTage=30
            - "14 Tage 3% Skonto, 30 Tage netto" → skontoTage=14, skontoProzent=3, nettoTage=30
            - "bei Zahlung binnen 10 Tagen 2% Skonto" → skontoTage=10, skontoProzent=2
            - "zahlbar innerhalb 30 Tagen" → nettoTage=30
            - "SOFORT NETTO KASSE" → nettoTage=0 (sofort fällig)
            - "rein netto Kasse" → nettoTage=0
            - "Valuta 30 Tage" → nettoTage=30

            LIEFERTERMIN ERKENNUNG (WICHTIG - NICHT MIT ZAHLUNGSBEDINGUNGEN VERWECHSELN!):
            Der Liefertermin gibt an WANN DIE WARE GELIEFERT WIRD, nicht wann bezahlt werden muss!

            Formate:
            - Explizites Datum: "15.01.2026" → liefertermin="2026-01-15"
            - Kalenderwoche: "KW 02/2026" → berechne Montag der KW → liefertermin="2026-01-05"
            - KW-Bereich: "KW 02-03/2026" → verwende Montag der ersten KW → liefertermin="2026-01-05"
            - Mit CA/U.V.: "CA. KW 02/2026 U.V." → ignoriere "CA." und "U.V.", parse KW
            - "TERMIN: KW 45/2025" → liefertermin="2025-11-03"
            - TAGE-FORMAT: "TERMIN: CA. 8 TAGE U. Ü. V." → liefertermin = dokumentDatum + 8 Tage
            - TAGE-FORMAT: "Lieferzeit ca. 5 Tage" → liefertermin = dokumentDatum + 5 Tage
            - TAGE-FORMAT: "Lieferung innerhalb 10 Werktagen" → liefertermin = dokumentDatum + 10 Tage

            WICHTIG: "8 TAGE" bei TERMIN/LIEFERZEIT ist NICHT Skonto! Das ist der Liefertermin!
            Skonto steht immer im Kontext von "Zahlung", "Skonto", "netto", "%".

            ZAHLUNGSZIEL BERECHNUNG:
            - Wenn ein explizites Fälligkeitsdatum im Dokument steht → verwende dieses
            - Wenn nur nettoTage gefunden → berechne: dokumentDatum + nettoTage = zahlungsziel
            - Beispiel: dokumentDatum="2024-12-01", nettoTage=30 → zahlungsziel="2024-12-31"


            REGELN:
            1. Antworte NUR mit dem JSON-Objekt, ohne Erklärungen.
            2. Verwende null für fehlende Werte.
            3. Beträge als Dezimalzahlen ohne Währungssymbol.
            4. Datum immer im Format YYYY-MM-DD.
            5. Bei AUFTRAGSBESTAETIGUNG besonders auf "AB", "Bestätigung", "Auftragsbestätigung" im Titel achten!
            6. Wenn das Dokument "Rechnung" im Titel hat UND Zahlungsinfos enthält -> RECHNUNG
            7. Wenn das Dokument "Auftragsbestätigung" oder "AB" im Titel hat -> AUFTRAGSBESTAETIGUNG
            8. confidence zwischen 0.0 und 1.0 basierend auf Lesbarkeit und Eindeutigkeit.
            9. bereitsGezahlt = true wenn:
                - "bereits bezahlt", "Betrag dankend erhalten" oder ähnliches steht
                - "Vorauskasse", "per vorkasse" oder "Vorkasse" vermerkt ist
                - Ein "Bezahlt"-Kasten/Stempel sichtbar ist (z.B. oben rechts "Bezahlt", häufig bei Amazon)
                - Eine "Zahlungsreferenznummer" angegeben ist
                - Eine direkte Zahlungsart angegeben ist: "Kreditkarte", "MasterCard", "VISA", "PayPal", "Amazon Pay", "Lastschrift"
                - "Zahlung erfolgt am..." oder ähnliches Datum in der Vergangenheit
                - SEPA-Lastschrift / SEPA-Mandat / Einzugsermächtigung / Bankeinzug erwähnt wird
                - "Abbuchung erfolgt", "wird abgebucht", "Einzug", "Lastschrifteinzug"
                - "per Lastschrift", "per Bankeinzug", "Mandat-Referenz"
                - "IBAN-Lastschrift", "SEPA Direct Debit", "Basisverfahren"
                - "Der Betrag wird eingezogen", "erfolgt automatisch"
                - "buchen wir ab", "buchen ab", "werden abgebucht", "Betrag buchen wir"
                - "Mandatsreferenz" oder "Gläubiger-ID" vorhanden
                - Bei automatischen Zahlungsarten ist bereitsGezahlt IMMER true!
            10. IMMER nach Skonto-Bedingungen suchen! Diese stehen oft klein gedruckt am Dokumentende.
            11. Wenn zahlungsziel nicht als Datum lesbar, aber nettoTage erkannt: berechne zahlungsziel selbst!
            12. Wenn das Dokument kein Anfrage/AB/Lieferschein/Rechnung ist --> dokumentTyp="SONSTIG", istGeschaeftsdokument=false
            13. REFERENZNUMMER EXTRAKTION (SEHR WICHTIG für Dokumenten-Verknüpfung!):
                - Bei AUFTRAGSBESTAETIGUNG: Suche nach "Ihr Anfrage", "Anfrages-Nr.", "Bezug: Anfrage" → das ist die Anfragesnummer
                - Bei LIEFERSCHEIN: Suche nach "Auftrags-Nr.", "AB-Nr.", "Ihre Bestellung" → das ist die AB-Nummer
                - Bei RECHNUNG: Suche nach "Auftrags-Nr.", "AB-Nr." (PRIORITÄT!) oder "Lieferschein-Nr." → verweist auf AB oder Lieferschein
                - Bei GUTSCHRIFT: Suche nach "Rechnungs-Nr.", "zu Rechnung" → verweist auf die Original-Rechnung
            14. DOKUMENT-GÜLTIGKEIT: Wenn "Abschrift", "Kopie", "Entwurf" oder "Duplikat" irgendwo im Dokument steht, MUSS " (Kopie)" an dokumentTyp angehängt werden.
            """;

    /**
     * Analysiert ein Dokument und erstellt automatisch LieferantGeschaeftsdokument.
     * 
     * Reihenfolge:
     * 1. ZUGFeRD-PDF prüfen
     * 2. XML-Datei prüfen
     * 3. Fallback: Gemini AI
     */
    @Transactional
    public LieferantGeschaeftsdokument analysiereDokument(LieferantDokument dokument) {
        return analysiereDokument(dokument, null);
    }

    /**
     * Re-analysiert ein Dokument anhand der ID in einer neuen Transaktion.
     * Wird für Batch-Reanalyse verwendet um Session-Konflikte zu vermeiden.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LieferantGeschaeftsdokument reanalysiereDokumentById(Long dokumentId) {
        LieferantDokument dokument = dokumentRepository.findById(dokumentId).orElse(null);
        if (dokument == null) {
            log.warn("Dokument {} nicht gefunden für Re-Analyse", dokumentId);
            return null;
        }
        return analysiereDokument(dokument, null);
    }

    /**
     * Startet die Analyse asynchron im Hintergrund.
     * Ideal für User-Uploads, damit der User nicht warten muss.
     * 
     * TEMPORARILY DISABLED FOR TESTING
     */
    @org.springframework.scheduling.annotation.Async
    public void analyzeAsync(Long dokumentId) {
        // TEMPORARILY DISABLED FOR TESTING - uncomment below to re-enable
        // log.info("Starte asynchrone Analyse für Dokument {}", dokumentId);
        // reanalysiereDokumentById(dokumentId);
        log.debug("analyzeAsync DISABLED for testing - skipping document {}", dokumentId);
    }

    /**
     * Analysiert eine Datei OHNE sie als Dokument zu speichern.
     * Wird für den manuellen Dokument-Importer verwendet (Vorschau der extrahierten
     * Daten).
     * 
     * @param dateiPfad        Pfad zur temporären Datei
     * @param originalFilename Original-Dateiname für MIME-Type Erkennung
     * @return AnalyzeResponse DTO mit extrahierten Daten
     */
    public org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analyzeFile(
            Path dateiPfad, String originalFilename) {
        return analyzeFile(dateiPfad, originalFilename, false);
    }

    public org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analyzeFile(
            Path dateiPfad, String originalFilename, boolean useProModel) {
        try {
            String lower = originalFilename.toLowerCase();

            // 1. Versuche ZUGFeRD-Extraktion bei PDF
            if (lower.endsWith(".pdf")) {
                var zugferdResult = versucheZugferdExtraktionFuerPreview(dateiPfad, originalFilename);
                if (zugferdResult != null) {
                    return zugferdResult;
                }
            }

            // 2. Fallback: KI-Analyse (für PDFs und Bilder)
            return analysierePerKiFuerPreview(dateiPfad, originalFilename, useProModel);

        } catch (Exception e) {
            log.error("Fehler bei Dateianalyse: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Analysiert eine PDF auf MEHRERE Rechnungen (z.B. Amazon mit 2+ Rechnungen).
     * Erkennt "Seite x/y" Muster um Grenzen zu identifizieren und splittet die PDF.
     * 
     * @param dateiPfad        Pfad zur PDF-Datei
     * @param originalFilename Original-Dateiname
     * @return Liste von MultiInvoiceAnalyzeResponse (bei einzelner Rechnung: Liste
     *         mit 1 Element)
     */
    public java.util.List<org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse> analyzeFileForMultipleInvoices(
            Path dateiPfad, String originalFilename) {

        java.util.List<org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse> results = new java.util.ArrayList<>();

        try {
            // Nur PDFs können mehrere Rechnungen enthalten
            if (!originalFilename.toLowerCase().endsWith(".pdf")) {
                // Für nicht-PDFs: Normale Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .build());
                }
                return results;
            }

            // KI fragen: Wie viele Dokumente sind in dieser PDF?
            byte[] pdfBytes = Files.readAllBytes(dateiPfad);
            String multiDocPrompt = buildMultiDocumentPrompt();
            String jsonResponse = rufGeminiApiMitPrompt(pdfBytes, "application/pdf", multiDocPrompt);

            if (jsonResponse == null) {
                // Fallback: Einzelne Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
                return results;
            }

            // JSON parsen
            var jsonArray = objectMapper.readTree(jsonResponse);

            if (!jsonArray.isArray() || jsonArray.size() == 0) {
                // Fallback: Einzelne Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
                return results;
            }

            // Mehrere Rechnungen erkannt - PDF splitten
            try (org.apache.pdfbox.pdmodel.PDDocument originalDoc = org.apache.pdfbox.Loader
                    .loadPDF(dateiPfad.toFile())) {

                int totalPages = originalDoc.getNumberOfPages();

                for (var invoiceNode : jsonArray) {
                    String pageRange = invoiceNode.has("seiten") ? invoiceNode.get("seiten").asText() : "alle";

                    // Parse AnalyzeResponse von diesem Invoice-Node
                    var analyzeResponse = parseJsonToAnalyzeResponse(invoiceNode.toString());

                    // PDF-Seiten extrahieren
                    int[] pages = parsePageRange(pageRange, totalPages);
                    byte[] splitPdfBytes = extractPages(originalDoc, pages);

                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange(pageRange)
                            .analyzeResponse(analyzeResponse)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(splitPdfBytes))
                            .build());
                }
            }

            return results;

        } catch (Exception e) {
            log.error("Fehler bei Multi-Invoice-Analyse: {}", e.getMessage(), e);
            // Fallback: Einzelne Analyse
            try {
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    byte[] pdfBytes = Files.readAllBytes(dateiPfad);
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
            } catch (Exception ex) {
                log.error("Auch Fallback fehlgeschlagen", ex);
            }
            return results;
        }
    }

    private String buildMultiDocumentPrompt() {
        return """
                Analysiere dieses PDF-Dokument SEHR SORGFÄLTIG.

                WICHTIG: Prüfe ob MEHRERE SEPARATE Rechnungen/Geschäftsdokumente in diesem PDF enthalten sind.
                Achte auf "Seite 1/X" Muster - wenn die Seitenzählung neu bei 1 beginnt, ist das ein neues Dokument!

                Beispiel: "Seite 1/2", "Seite 2/2" = erste Rechnung (2 Seiten)
                          "Seite 1/1" = zweite Rechnung (1 Seite)

                Antworte NUR mit einem JSON-Array. Für JEDES erkannte Dokument ein Objekt:
                [
                  {
                    "seiten": "1-2",
                    "dokumentTyp": "RECHNUNG",
                    "dokumentNummer": "...",
                    "dokumentDatum": "YYYY-MM-DD",
                    "betragBrutto": 123.45,
                    "betragNetto": 103.74,
                    "bestellnummer": "...",
                    "referenzNummer": "...",
                    "bereitsGezahlt": true,
                    "confidence": 0.95
                  }
                ]

                Wenn nur EIN Dokument vorhanden ist, gib trotzdem ein Array mit einem Element zurück.

                WICHTIG FÜR "bereitsGezahlt":
                Setze auf true wenn:
                - "Bezahlt"-Vermerk sichtbar (z.B. Box "Bezahlt" oben rechts wie bei Amazon)
                - Zahlungsreferenznummer vorhanden
                - Zahlungsart Kreditkarte/PayPal/Amazon Pay/Lastschrift
                - "Betrag dankend erhalten"
                - SEPA-Lastschrift / SEPA-Mandat / Einzugsermächtigung / Bankeinzug
                - "Abbuchung erfolgt", "wird abgebucht", "per Lastschrift"
                - "buchen wir ab", "buchen ab", "Mandatsreferenz", "Gläubiger-ID"
                - Bei automatischen Zahlungsarten ist bereitsGezahlt IMMER true!
                """;
    }

    public String rufGeminiApiMitPrompt(byte[] bytes, String mimeType, String customPrompt) {
        return rufGeminiApiMitPrompt(bytes, mimeType, customPrompt, false);
    }

    public String rufGeminiApiMitPrompt(byte[] bytes, String mimeType, String customPrompt, boolean useProModel) {
        API_LOCK.lock();
        try {
            // Ensure minimum delay between API calls to prevent rate limiting
            long now = System.currentTimeMillis();
            long elapsed = now - lastApiCallTime;
            if (elapsed < API_CALL_DELAY_MS && lastApiCallTime > 0) {
                try {
                    Thread.sleep(API_CALL_DELAY_MS - elapsed);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            log.debug("[Gemini API] Starte Multi-Doc API-Aufruf (Lock erworben)");

            String base64Data = Base64.getEncoder().encodeToString(bytes);
            String modelToUse = useProModel ? geminiProModel : geminiModel;
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelToUse
                    + ":generateContent?key=" + systemSettingsService.getGeminiApiKey();

            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");

            // Bild/PDF Teil
            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Data);

            // Custom Prompt Teil
            parts.addObject().put("text", customPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastApiCallTime = System.currentTimeMillis();

            if (response.statusCode() != 200) {
                log.error("Gemini API Fehler: {} - {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode candidates = responseJson.path("candidates");
            if (candidates.isEmpty()) {
                return null;
            }

            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

            // JSON aus Markdown extrahieren falls vorhanden
            if (text.contains("```json")) {
                int start = text.indexOf("```json") + 7;
                int end = text.indexOf("```", start);
                if (end > start) {
                    text = text.substring(start, end).trim();
                }
            } else if (text.contains("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.indexOf("```", start);
                if (end > start) {
                    text = text.substring(start, end).trim();
                }
            }

            log.debug("[Gemini API] Multi-Doc API-Aufruf erfolgreich");
            return text.trim();

        } catch (Exception e) {
            log.error("Gemini Multi-Doc API-Aufruf fehlgeschlagen: {}", e.getMessage(), e);
            return null;
        } finally {
            API_LOCK.unlock();
        }
    }

    private int[] parsePageRange(String pageRange, int totalPages) {
        if (pageRange == null || pageRange.equalsIgnoreCase("alle")) {
            int[] all = new int[totalPages];
            for (int i = 0; i < totalPages; i++) {
                all[i] = i;
            }
            return all;
        }

        try {
            java.util.List<Integer> pages = new java.util.ArrayList<>();
            String[] parts = pageRange.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    int start = Integer.parseInt(range[0].trim()) - 1; // 0-indexed
                    int end = Integer.parseInt(range[1].trim()) - 1;
                    for (int i = start; i <= end && i < totalPages; i++) {
                        if (i >= 0)
                            pages.add(i);
                    }
                } else {
                    int page = Integer.parseInt(part) - 1;
                    if (page >= 0 && page < totalPages)
                        pages.add(page);
                }
            }
            return pages.stream().mapToInt(Integer::intValue).toArray();
        } catch (Exception e) {
            log.warn("Konnte Page-Range nicht parsen: {}", pageRange);
            int[] all = new int[totalPages];
            for (int i = 0; i < totalPages; i++) {
                all[i] = i;
            }
            return all;
        }
    }

    private byte[] extractPages(org.apache.pdfbox.pdmodel.PDDocument sourceDoc, int[] pageIndices) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument newDoc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (int pageIndex : pageIndices) {
                if (pageIndex >= 0 && pageIndex < sourceDoc.getNumberOfPages()) {
                    newDoc.addPage(sourceDoc.getPage(pageIndex));
                }
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            newDoc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * ZUGFeRD-Extraktion die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse versucheZugferdExtraktionFuerPreview(
            Path dateiPfad, String dateiname) {
        try {
            var zugferd = zugferdExtractorService.extract(dateiPfad.toString(), dateiname);

            if (zugferd.getRechnungsnummer() == null && zugferd.getBetrag() == null) {
                return null;
            }

            log.info("ZUGFeRD-Daten gefunden für Preview: Nr={}, Betrag={}",
                    zugferd.getRechnungsnummer(), zugferd.getBetrag());

            // Dokumenttyp bestimmen
            LieferantDokumentTyp typ = LieferantDokumentTyp.RECHNUNG;
            if (zugferd.getGeschaeftsdokumentart() != null) {
                typ = switch (zugferd.getGeschaeftsdokumentart()) {
                    case "Angebot" -> LieferantDokumentTyp.ANGEBOT;
                    case "Auftragsbestätigung" -> LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
                    case "Gutschrift" -> LieferantDokumentTyp.GUTSCHRIFT;
                    default -> LieferantDokumentTyp.RECHNUNG;
                };
            }

            return org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder()
                    .dokumentTyp(typ)
                    .dokumentNummer(zugferd.getRechnungsnummer())
                    .dokumentDatum(zugferd.getRechnungsdatum())
                    .betragNetto(zugferd.getBetragNetto())
                    .betragBrutto(zugferd.getBetrag())
                    .mwstSatz(zugferd.getMwstSatz())
                    .zahlungsziel(zugferd.getFaelligkeitsdatum())
                    .bestellnummer(zugferd.getBestellnummer())
                    .referenzNummer(zugferd.getReferenzNummer())
                    .skontoTage(zugferd.getSkontoTage())
                    .skontoProzent(zugferd.getSkontoProzent())
                    .nettoTage(zugferd.getNettoTage())
                    .aiConfidence(1.0)
                    .analyseQuelle("ZUGFeRD")
                    .build();

        } catch (Exception e) {
            log.debug("ZUGFeRD-Preview fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * XML-Extraktion die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse versucheXmlExtraktionFuerPreview(
            Path dateiPfad) {
        try {
            String xmlContent = Files.readString(dateiPfad, java.nio.charset.StandardCharsets.UTF_8);

            if (!xmlContent.contains("Invoice") && !xmlContent.contains("CrossIndustryInvoice")) {
                return null;
            }

            String dokumentNummer = extractXmlValue(xmlContent, "ID", "InvoiceNumber", "ram:ID");
            java.math.BigDecimal betragBrutto = null;
            String betragStr = extractXmlValue(xmlContent, "GrandTotalAmount", "PayableAmount", "ram:GrandTotalAmount");
            if (betragStr != null) {
                try {
                    betragBrutto = new java.math.BigDecimal(betragStr.replace(',', '.'));
                } catch (NumberFormatException ignored) {
                }
            }

            LocalDate dokumentDatum = null;
            String datumStr = extractXmlValue(xmlContent, "IssueDate", "IssueDateTime", "ram:DateTimeString");
            if (datumStr != null) {
                try {
                    datumStr = datumStr.replaceAll("[^0-9]", "");
                    if (datumStr.length() >= 8) {
                        dokumentDatum = LocalDate.parse(datumStr.substring(0, 8),
                                java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                    }
                } catch (Exception ignored) {
                }
            }

            if (dokumentNummer == null && betragBrutto == null) {
                return null;
            }

            return org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder()
                    .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                    .dokumentNummer(dokumentNummer)
                    .dokumentDatum(dokumentDatum)
                    .betragBrutto(betragBrutto)
                    .aiConfidence(1.0)
                    .analyseQuelle("XML")
                    .build();

        } catch (Exception e) {
            log.debug("XML-Preview fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * KI-Analyse die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analysierePerKiFuerPreview(
            Path dateiPfad, String originalFilename, boolean useProModel) {
        try {
            byte[] bytes = Files.readAllBytes(dateiPfad);
            String mimeType = getMimeTypeFromFilename(originalFilename);

            String jsonResponse = rufGeminiApi(bytes, mimeType, useProModel);
            if (jsonResponse == null) {
                return null;
            }

            // JSON parsen und DTO erstellen
            return parseJsonToAnalyzeResponse(jsonResponse);

        } catch (Exception e) {
            log.error("KI-Preview fehlgeschlagen: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getMimeTypeFromFilename(String filename) {
        if (filename == null)
            return "application/pdf";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        return "application/pdf";
    }

    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse parseJsonToAnalyzeResponse(
            String jsonResponse) {
        try {
            var json = objectMapper.readTree(jsonResponse);

            var builder = org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder();

            // Dokumenttyp
            String typStr = json.has("dokumentTyp") ? json.get("dokumentTyp").asText(null) : null;
            if (typStr != null) {
                typStr = typStr.replace(" (Kopie)", "").trim();
                try {
                    builder.dokumentTyp(LieferantDokumentTyp.valueOf(typStr));
                } catch (Exception e) {
                    builder.dokumentTyp(LieferantDokumentTyp.SONSTIG);
                }
            } else {
                builder.dokumentTyp(LieferantDokumentTyp.RECHNUNG);
            }

            // Dokumentnummer
            if (json.has("dokumentNummer") && !json.get("dokumentNummer").isNull()) {
                builder.dokumentNummer(json.get("dokumentNummer").asText());
            }

            // Datum
            LocalDate parsedDokumentDatum = null;
            if (json.has("dokumentDatum") && !json.get("dokumentDatum").isNull()) {
                parsedDokumentDatum = parseDateFlexibel(json.get("dokumentDatum").asText());
                if (parsedDokumentDatum != null) {
                    builder.dokumentDatum(parsedDokumentDatum);
                } else {
                    log.warn("Konnte dokumentDatum nicht parsen: '{}'", json.get("dokumentDatum").asText());
                }
            }

            // Beträge
            if (json.has("betragNetto") && !json.get("betragNetto").isNull()) {
                builder.betragNetto(new java.math.BigDecimal(json.get("betragNetto").asText()));
            }
            if (json.has("betragBrutto") && !json.get("betragBrutto").isNull()) {
                builder.betragBrutto(new java.math.BigDecimal(json.get("betragBrutto").asText()));
            }
            if (json.has("mwstSatz") && !json.get("mwstSatz").isNull()) {
                builder.mwstSatz(new java.math.BigDecimal(json.get("mwstSatz").asText()));
            }

            // Zahlungsziel
            LocalDate parsedZahlungsziel = null;
            if (json.has("zahlungsziel") && !json.get("zahlungsziel").isNull()) {
                String zahlungszielStr = json.get("zahlungsziel").asText();
                parsedZahlungsziel = parseDateFlexibel(zahlungszielStr);
                if (parsedZahlungsziel != null) {
                    builder.zahlungsziel(parsedZahlungsziel);
                } else {
                    log.warn("Konnte zahlungsziel nicht parsen: '{}' – versuche Fallback über nettoTage", zahlungszielStr);
                }
            }

            // Referenzen
            if (json.has("bestellnummer") && !json.get("bestellnummer").isNull()) {
                builder.bestellnummer(json.get("bestellnummer").asText());
            }
            if (json.has("referenzNummer") && !json.get("referenzNummer").isNull()) {
                builder.referenzNummer(json.get("referenzNummer").asText());
            }

            // Skonto
            Integer parsedNettoTage = null;
            if (json.has("skontoTage") && !json.get("skontoTage").isNull()) {
                builder.skontoTage(json.get("skontoTage").asInt());
            }
            if (json.has("skontoProzent") && !json.get("skontoProzent").isNull()) {
                builder.skontoProzent(new java.math.BigDecimal(json.get("skontoProzent").asText()));
            }
            if (json.has("nettoTage") && !json.get("nettoTage").isNull()) {
                parsedNettoTage = json.get("nettoTage").asInt();
                builder.nettoTage(parsedNettoTage);
            }

            // Fallback: zahlungsziel aus dokumentDatum + nettoTage berechnen
            if (parsedZahlungsziel == null && parsedDokumentDatum != null && parsedNettoTage != null && parsedNettoTage > 0) {
                parsedZahlungsziel = parsedDokumentDatum.plusDays(parsedNettoTage);
                builder.zahlungsziel(parsedZahlungsziel);
                log.info("Zahlungsziel per Fallback berechnet: {} + {} Tage = {}", parsedDokumentDatum, parsedNettoTage, parsedZahlungsziel);
            }

            // Bereits gezahlt
            if (json.has("bereitsGezahlt") && !json.get("bereitsGezahlt").isNull()) {
                builder.bereitsGezahlt(json.get("bereitsGezahlt").asBoolean());
            }

            // Lieferantendaten
            if (json.has("lieferantName") && !json.get("lieferantName").isNull()) {
                builder.lieferantName(json.get("lieferantName").asText());
            }
            if (json.has("lieferantStrasse") && !json.get("lieferantStrasse").isNull()) {
                builder.lieferantStrasse(json.get("lieferantStrasse").asText());
            }
            if (json.has("lieferantPlz") && !json.get("lieferantPlz").isNull()) {
                builder.lieferantPlz(json.get("lieferantPlz").asText());
            }
            if (json.has("lieferantOrt") && !json.get("lieferantOrt").isNull()) {
                builder.lieferantOrt(json.get("lieferantOrt").asText());
            }

            // Confidence
            if (json.has("confidence") && !json.get("confidence").isNull()) {
                builder.aiConfidence(json.get("confidence").asDouble());
            } else {
                builder.aiConfidence(0.8);
            }

            builder.analyseQuelle("KI");

            return builder.build();

        } catch (Exception e) {
            log.error("Fehler beim Parsen der KI-Antwort: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Versucht ein Datum flexibel zu parsen (ISO, deutsches Format, Slash-Format).
     * Gibt null zurück wenn kein Format passt.
     */
    private LocalDate parseDateFlexibel(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        dateStr = dateStr.trim();

        // 1. ISO-Format: YYYY-MM-DD (Standard von der KI erwartet)
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {
        }

        // 2. Deutsches Format: dd.MM.yyyy (z.B. "05.03.2026")
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignored) {
        }

        // 3. Slash-Format: dd/MM/yyyy
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
        }

        // 4. Kompaktes Format ohne Trennzeichen: yyyyMMdd
        try {
            if (dateStr.length() == 8 && dateStr.matches("\\d{8}")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
            }
        } catch (Exception ignored) {
        }

        log.warn("Datum konnte mit keinem Format geparst werden: '{}'", dateStr);
        return null;
    }


    // REQUIRES_NEW: Jede Dokumentanalyse in eigener Transaktion → sofortiger Commit
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LieferantGeschaeftsdokument analysiereDokument(LieferantDokument dokument, Path explicitPath) {
        try {
            // Bei REQUIRES_NEW müssen wir das Dokument frisch laden, da es sonst "detached"
            // ist
            LieferantDokument freshDokument = dokumentRepository.findById(dokument.getId()).orElse(null);
            if (freshDokument == null) {
                log.error("Dokument {} nicht gefunden!", dokument.getId());
                return null;
            }

            Path dateiPfad = explicitPath != null ? explicitPath : getDateiPfad(freshDokument);
            if (dateiPfad == null || !Files.exists(dateiPfad)) {
                log.warn("Konnte Datei nicht finden für Dokument {}", freshDokument.getId());
                return null;
            }

            String dateiname = freshDokument.getEffektiverDateiname();
            LieferantGeschaeftsdokument geschaeftsdaten = null;

            // 1. Prüfe auf ZUGFeRD-PDF (nur PDFs - XML ist eingebettet)
            if (dateiname != null) {
                String lower = dateiname.toLowerCase();

                if (lower.endsWith(".pdf")) {
                    // Versuche ZUGFeRD-Extraktion
                    geschaeftsdaten = versucheZugferdExtraktion(dateiPfad, dateiname, freshDokument);
                }
            }

            // 2. Falls keine strukturierten Daten, Fallback auf KI
            if (geschaeftsdaten == null) {
                log.info("Keine ZUGFeRD/XML-Daten gefunden, verwende KI-Analyse für Dokument {}",
                        freshDokument.getId());
                geschaeftsdaten = analysierePerKi(freshDokument, dateiPfad);
            }

            if (geschaeftsdaten == null) {
                log.warn("Konnte keine Metadaten extrahieren für Dokument {}", freshDokument.getId());
                return null;
            }

            // Duplikat-Check: Wenn Dokumentnummer existiert, WIEDERVERWENDEN statt löschen
            // Bei PDF+XML-Paar sollen beide Dokumente die gleichen Geschäftsdaten
            // referenzieren
            if (geschaeftsdaten.getDokumentNummer() != null && !geschaeftsdaten.getDokumentNummer().isBlank()) {
                Long lieferantId = freshDokument.getLieferant().getId();
                var existingList = lieferantGeschaeftsdokumentRepository.findByLieferantIdAndDokumentNummer(
                        lieferantId, geschaeftsdaten.getDokumentNummer());
                var existingOpt = existingList.stream()
                        .filter(gd -> !gd.getId().equals(freshDokument.getId()))
                        .findFirst();
                if (existingOpt.isPresent()) {
                    LieferantGeschaeftsdokument bestehendes = existingOpt.get();
                    log.info(
                            "Dokumentnummer {} existiert bereits (ID={}). Aktualisiere statt löschen.",
                            geschaeftsdaten.getDokumentNummer(), bestehendes.getId());

                    // Aktualisiere das bestehende Geschäftsdokument mit neuen Daten (falls besser)
                    // ZUGFeRD/XML hat höhere Priorität als KI, daher Daten übernehmen
                    if (geschaeftsdaten.getAiRawJson() == null) { // Neue Daten kommen von ZUGFeRD/XML
                        // Nur Felder aktualisieren die im neuen besser sind
                        if (geschaeftsdaten.getDokumentDatum() != null) {
                            bestehendes.setDokumentDatum(geschaeftsdaten.getDokumentDatum());
                        }
                        if (geschaeftsdaten.getBetragNetto() != null) {
                            bestehendes.setBetragNetto(geschaeftsdaten.getBetragNetto());
                        }
                        if (geschaeftsdaten.getBetragBrutto() != null) {
                            bestehendes.setBetragBrutto(geschaeftsdaten.getBetragBrutto());
                        }
                        if (geschaeftsdaten.getZahlungsziel() != null) {
                            bestehendes.setZahlungsziel(geschaeftsdaten.getZahlungsziel());
                        }
                        if (geschaeftsdaten.getBestellnummer() != null) {
                            bestehendes.setBestellnummer(geschaeftsdaten.getBestellnummer());
                        }
                        if (geschaeftsdaten.getReferenzNummer() != null) {
                            bestehendes.setReferenzNummer(geschaeftsdaten.getReferenzNummer());
                        }
                        if (geschaeftsdaten.getAiConfidence() != null &&
                                (bestehendes.getAiConfidence() == null ||
                                        geschaeftsdaten.getAiConfidence()
                                                .compareTo(bestehendes.getAiConfidence()) > 0)) {
                            bestehendes.setAiConfidence(geschaeftsdaten.getAiConfidence());
                        }
                        log.info("Geschäftsdaten aktualisiert mit ZUGFeRD/XML-Daten");
                    }

                    // Das aktuelle Dokument referenziert die bestehenden Geschäftsdaten
                    geschaeftsdaten = bestehendes;
                }
            }

            // Dokumenttyp aktualisieren falls erkannt (auch SONSTIG überschreiben)
            if (geschaeftsdaten.getDokumentNummer() != null &&
                    (freshDokument.getTyp() == null || freshDokument.getTyp() == LieferantDokumentTyp.SONSTIG)) {
                LieferantDokumentTyp erkannterTyp = erkenneTypAusNummer(geschaeftsdaten.getDokumentNummer());
                if (erkannterTyp != null) {
                    freshDokument.setTyp(erkannterTyp);
                }
            }

            // Automatische Verknüpfung
            automatischeVerknuepfung(freshDokument, geschaeftsdaten);

            // WICHTIG: Bei @MapsId funktioniert Cascade NICHT von Parent zu Child!
            // Das Child (geschaeftsdaten) muss ZUERST explizit gespeichert werden.
            // geschaeftsdaten.dokument wurde bereits gesetzt (in
            // parseJsonToGeschaeftsdaten)
            LieferantGeschaeftsdokument savedGeschaeftsdaten = lieferantGeschaeftsdokumentRepository
                    .saveAndFlush(geschaeftsdaten);
            log.debug("LieferantGeschaeftsdokument gespeichert mit ID: {}", savedGeschaeftsdaten.getId());

            // Dann Dokument aktualisieren (referenziert jetzt das gespeicherte
            // geschaeftsdaten)
            freshDokument.setGeschaeftsdaten(savedGeschaeftsdaten);
            dokumentRepository.saveAndFlush(freshDokument);

            log.info("Dokument {} erfolgreich analysiert: {} (Quelle: {}, Confidence: {})",
                    freshDokument.getId(),
                    geschaeftsdaten.getDokumentNummer(),
                    geschaeftsdaten.getAiRawJson() != null ? "KI" : "ZUGFeRD/XML",
                    geschaeftsdaten.getAiConfidence());

            // Nachträgliche Verknüpfung: Suche ob DIESES Dokument als Referenz in anderen
            // genutzt wird
            nachtraeglicheVerknuepfung(freshDokument, savedGeschaeftsdaten);

            return savedGeschaeftsdaten;

        } catch (Exception e) {
            log.error("Fehler bei Dokumentanalyse für Dokument {}", dokument.getId(), e);
            return null;
        }
    }

    /**
     * Versucht ZUGFeRD-Daten aus PDF zu extrahieren.
     */
    private LieferantGeschaeftsdokument versucheZugferdExtraktion(Path dateiPfad, String dateiname,
            LieferantDokument dokument) {
        try {
            ZugferdDaten zugferd = zugferdExtractorService.extract(dateiPfad.toString(), dateiname);

            // Prüfe ob relevante Daten extrahiert wurden
            if (zugferd.getRechnungsnummer() == null && zugferd.getBetrag() == null) {
                log.debug("Keine ZUGFeRD-Daten in PDF gefunden: {}", dateiname);
                return null;
            }

            log.info("ZUGFeRD-Daten gefunden in {}: Nr={}, Betrag={}",
                    dateiname, zugferd.getRechnungsnummer(), zugferd.getBetrag());

            // Existierendes Geschaeftsdokument wiederverwenden oder neues erstellen
            LieferantGeschaeftsdokument gd = null;
            if (dokument != null) {
                gd = dokument.getGeschaeftsdaten();
            }

            if (gd == null) {
                gd = new LieferantGeschaeftsdokument();
                if (dokument != null) {
                    gd.setDokument(dokument);
                }
            }
            gd.setDokumentNummer(zugferd.getRechnungsnummer());
            gd.setDokumentDatum(zugferd.getRechnungsdatum());
            gd.setBetragBrutto(zugferd.getBetrag());
            gd.setAiConfidence(1.0); // Strukturierte Daten = 100% Confidence
            gd.setAnalysiertAm(LocalDateTime.now());

            // Erweiterte ZUGFeRD-Felder (Skonto, Netto, Zahlungsziel)
            if (zugferd.getFaelligkeitsdatum() != null) {
                gd.setZahlungsziel(zugferd.getFaelligkeitsdatum());
            }
            if (zugferd.getBetragNetto() != null) {
                gd.setBetragNetto(zugferd.getBetragNetto());
            }
            if (zugferd.getMwstSatz() != null) {
                gd.setMwstSatz(zugferd.getMwstSatz());
            }
            if (zugferd.getSkontoTage() != null) {
                gd.setSkontoTage(zugferd.getSkontoTage());
            }
            if (zugferd.getSkontoProzent() != null) {
                gd.setSkontoProzent(zugferd.getSkontoProzent());
            }
            if (zugferd.getNettoTage() != null) {
                gd.setNettoTage(zugferd.getNettoTage());
            }
            if (zugferd.getBestellnummer() != null) {
                gd.setBestellnummer(zugferd.getBestellnummer());
            }
            if (zugferd.getReferenzNummer() != null) {
                gd.setReferenzNummer(zugferd.getReferenzNummer());
            }

            // Dokumenttyp aus ZUGFeRD-Daten
            if (zugferd.getGeschaeftsdokumentart() != null) {
                LieferantDokumentTyp typ = switch (zugferd.getGeschaeftsdokumentart()) {
                    case "Rechnung" -> LieferantDokumentTyp.RECHNUNG;
                    case "Angebot" -> LieferantDokumentTyp.ANGEBOT;
                    case "Auftragsbestätigung" -> LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
                    case "Gutschrift" -> LieferantDokumentTyp.GUTSCHRIFT;
                    default -> null;
                };
                // Überschreibe auch SONSTIG (Default-Typ) mit erkanntem Typ
                if (typ != null && (dokument == null || dokument.getTyp() == null
                        || dokument.getTyp() == LieferantDokumentTyp.SONSTIG)) {
                    if (dokument != null)
                        dokument.setTyp(typ);
                }
            }

            // Artikelpositionen verarbeiten und Preise aktualisieren
            if (dokument != null && dokument.getLieferant() != null && zugferd.getArtikelPositionen() != null) {
                verarbeiteZugferdArtikelPositionen(zugferd.getArtikelPositionen(), dokument.getLieferant());
            }

            return gd;

        } catch (Exception e) {
            log.debug("ZUGFeRD-Extraktion fehlgeschlagen für {}: {}", dateiname, e.getMessage());
            return null;
        }
    }

    /**
     * Versucht XML-Rechnung zu parsen (z.B. XRechnung, ZUGFeRD-XML).
     */
    private LieferantGeschaeftsdokument versucheXmlExtraktion(Path dateiPfad, LieferantDokument dokument) {
        try {
            String xmlContent = Files.readString(dateiPfad, StandardCharsets.UTF_8);

            // Einfache Prüfung ob es eine Rechnungs-XML ist
            if (!xmlContent.contains("Invoice") && !xmlContent.contains("CrossIndustryInvoice")) {
                log.debug("XML ist keine Rechnung: {}", dateiPfad.getFileName());
                return null;
            }

            log.info("XML-Rechnung gefunden: {}", dateiPfad.getFileName());

            // Existierendes Geschaeftsdokument wiederverwenden oder neues erstellen
            LieferantGeschaeftsdokument gd = null;
            if (dokument != null) {
                gd = dokument.getGeschaeftsdaten();
            }
            if (gd == null) {
                gd = new LieferantGeschaeftsdokument();
                if (dokument != null) {
                    gd.setDokument(dokument);
                }
            }
            gd.setAiConfidence(1.0); // Strukturierte Daten
            gd.setDatenquelle("XML");
            gd.setAnalysiertAm(LocalDateTime.now());

            // Dokumentnummer extrahieren
            gd.setDokumentNummer(extractXmlValue(xmlContent, "ID", "InvoiceNumber", "ram:ID"));

            // Betrag extrahieren
            String betrag = extractXmlValue(xmlContent, "GrandTotalAmount", "PayableAmount", "ram:GrandTotalAmount");
            if (betrag != null) {
                try {
                    gd.setBetragBrutto(new BigDecimal(betrag.replace(',', '.')));
                } catch (NumberFormatException ignored) {
                }
            }

            // Datum extrahieren
            String datum = extractXmlValue(xmlContent, "IssueDate", "IssueDateTime", "ram:DateTimeString");
            if (datum != null) {
                try {
                    datum = datum.replaceAll("[^0-9]", "");
                    if (datum.length() >= 8) {
                        gd.setDokumentDatum(LocalDate.parse(datum.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE));
                    }
                } catch (Exception ignored) {
                }
            }

            // Typ auf Rechnung setzen (XML ist meist Rechnung)
            if (dokument != null && dokument.getTyp() == null) {
                dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            }

            return gd.getDokumentNummer() != null || gd.getBetragBrutto() != null ? gd : null;

        } catch (Exception e) {
            log.debug("XML-Extraktion fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Einfache XML-Wert-Extraktion für mehrere mögliche Tag-Namen.
     */
    private String extractXmlValue(String xml, String... tagNames) {
        for (String tag : tagNames) {
            // Versuche verschiedene Formate
            String[] patterns = {
                    "<" + tag + ">([^<]+)</" + tag + ">",
                    "<[^:]+:" + tag + ">([^<]+)</[^:]+:" + tag + ">"
            };
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(xml);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        }
        return null;
    }

    /**
     * Fallback: Analyse per Gemini AI mit Validierung und Retry bei unvollständigen
     * Daten. Bei fehlgeschlagener Validierung wird das Dokument trotzdem
     * gespeichert,
     * aber mit manuellePruefungErforderlich=true markiert.
     */
    /**
     * Führt die Analyse durch und gibt das Ergebnis zurück, OHNE Änderungen in der
     * DB zu speichern.
     * Nutzt ZUGFeRD oder KI.
     */
    public LieferantGeschaeftsdokument analyzeAndReturnData(Path dateiPfad, String originalDateiname) {
        try {
            String lower = originalDateiname != null ? originalDateiname.toLowerCase() : "";

            // 1. ZUGFeRD (nur PDFs - XML ist in PDF eingebettet)
            if (lower.endsWith(".pdf")) {
                LieferantGeschaeftsdokument zugferd = versucheZugferdExtraktion(dateiPfad, originalDateiname, null);
                if (zugferd != null)
                    return zugferd;
            }

            // 2. Standalone XML (XRechnung, ZUGFeRD-XML)
            if (lower.endsWith(".xml")) {
                LieferantGeschaeftsdokument xmlResult = versucheXmlExtraktion(dateiPfad, null);
                if (xmlResult != null)
                    return xmlResult;
            }

            // 3. Fallback: KI-Analyse (für PDFs und Bilder)
            String mimeType = "application/pdf";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                mimeType = "image/jpeg";
            else if (lower.endsWith(".png"))
                mimeType = "image/png";
            else if (lower.endsWith(".xml"))
                mimeType = "application/xml";

            return analysierePerKi(dateiPfad, mimeType, null);

        } catch (Exception e) {
            log.error("Fehler bei analyzeAndReturnData: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fallback: Analyse per Gemini AI mit Validierung und Retry bei unvollständigen
     * Daten. Bei fehlgeschlagener Validierung wird das Dokument trotzdem
     * gespeichert,
     * aber mit manuellePruefungErforderlich=true markiert.
     */
    private LieferantGeschaeftsdokument analysierePerKi(LieferantDokument dokument, Path dateiPfad) {
        return analysierePerKi(dateiPfad, getMimeType(dokument), dokument != null ? dokument.getId() : null);
    }

    private LieferantGeschaeftsdokument analysierePerKi(Path dateiPfad, String mimeType, Long docIdForLog) {
        try {
            byte[] bytes = Files.readAllBytes(dateiPfad);
            // mimeType ist bereits übergeben

            // Erster Versuch mit Standard-Model (Retry-Logik ist in rufGeminiApi eingebaut)
            String jsonResponse = rufGeminiApi(bytes, mimeType, false);
            if (jsonResponse == null) {
                log.warn(
                        "[KI-Analyse] Keine API-Antwort für Dokument {} - erstelle leeres Geschäftsdokument zur manuellen Prüfung",
                        docIdForLog);
                // Erstelle leeres Geschäftsdokument mit Flag für manuelle Prüfung
                LieferantGeschaeftsdokument emptyResult = new LieferantGeschaeftsdokument();
                // emptyResult.setDokument(dokument); // NICHT SETZEN, da wir noch kein Dokument
                // haben!
                emptyResult.setManuellePruefungErforderlich(true);
                emptyResult.setDatenquelle("AI_FAILED");
                emptyResult.setAnalysiertAm(java.time.LocalDateTime.now());
                return emptyResult;
            }

            // Prüfe ob JSON abgeschnitten wurde (häufig bei vielen Artikelpositionen)
            boolean jsonTruncated = isJsonTruncated(jsonResponse);
            if (jsonTruncated) {
                log.warn(
                        "[KI-Analyse] JSON-Antwort wurde abgeschnitten für Dokument {}. Retry ohne Artikelpositionen...",
                        docIdForLog);
                // Retry mit Flag um Artikelpositionen zu überspringen
                jsonResponse = rufGeminiApi(bytes, mimeType, false, true); // skipArtikelPositionen=true
                if (jsonResponse == null || isJsonTruncated(jsonResponse)) {
                    log.warn("[KI-Analyse] Auch vereinfachte Anfrage abgeschnitten - versuche Pro-Model...");
                    jsonResponse = rufGeminiApi(bytes, mimeType, true, true); // Pro-Model + skipArtikelPositionen
                }
            }

            LieferantGeschaeftsdokument result = mapJsonToData(jsonResponse);

            // Wenn Parsing fehlgeschlagen, prüfe ob JSON abgeschnitten und retry
            if (result == null) {
                if (isJsonTruncated(jsonResponse)) {
                    log.warn(
                            "[KI-Analyse] JSON nach wie vor abgeschnitten - versuche nochmal mit Pro-Model ohne Artikel...");
                    jsonResponse = rufGeminiApi(bytes, mimeType, true, true);
                    result = jsonResponse != null ? mapJsonToData(jsonResponse) : null;
                }

                if (result == null) {
                    log.warn(
                            "[KI-Analyse] JSON-Parsing fehlgeschlagen für Dokument {} - erstelle zur manuellen Prüfung",
                            docIdForLog);
                    result = new LieferantGeschaeftsdokument();
                    result.setManuellePruefungErforderlich(true);
                    result.setDatenquelle("AI_PARSE_FAILED");
                    result.setAiRawJson(jsonResponse);
                    result.setAnalysiertAm(java.time.LocalDateTime.now());
                    return result;
                }
            }

            // Validiere die extrahierten Daten
            // Typ haben wir hier potentiell noch nicht sicher, wir validieren allgemein auf
            // Nummer
            boolean validierungBestanden = validiereGeschaeftsdaten(result, null);

            if (!validierungBestanden) {
                log.warn(
                        "[KI-Analyse] Datenvalidierung fehlgeschlagen für Dokument {} (Typ unbekannt). Versuche mit Pro-Model...",
                        docIdForLog);

                // Retry mit Pro-Model
                jsonResponse = rufGeminiApi(bytes, mimeType, true);
                if (jsonResponse != null) {
                    LieferantGeschaeftsdokument retryResult = mapJsonToData(jsonResponse);
                    if (retryResult != null && validiereGeschaeftsdaten(retryResult, null)) {
                        log.info("[KI-Analyse] Pro-Model erfolgreich - Datenvalidierung bestanden");
                        retryResult.setManuellePruefungErforderlich(false);
                        return retryResult;
                    }
                    // Pro-Model hat auch nicht alle Daten - benutze das bessere Ergebnis
                    if (retryResult != null) {
                        result = retryResult;
                    }
                }

                // Beide Models konnten nicht alle Pflichtfelder extrahieren - Flag setzen
                log.warn(
                        "[KI-Analyse] Auch Pro-Model konnte keine vollständigen Daten extrahieren - markiere zur manuellen Prüfung");
                result.setManuellePruefungErforderlich(true);
            } else {
                // Validierung bestanden
                result.setManuellePruefungErforderlich(false);
            }

            return result;

        } catch (Exception e) {
            log.error("KI-Analyse fehlgeschlagen für Dokument {}", docIdForLog, e);
            // Auch bei Exception: Dokument mit Flag erstellen
            LieferantGeschaeftsdokument errorResult = new LieferantGeschaeftsdokument();
            errorResult.setManuellePruefungErforderlich(true);
            errorResult.setDatenquelle("AI_ERROR");
            errorResult.setAnalysiertAm(java.time.LocalDateTime.now());
            return errorResult;
        }
    }

    /**
     * Validiert ob die extrahierten Geschäftsdaten vollständig sind basierend auf
     * dem Dokumenttyp.
     * 
     * Pflichtfelder je Typ:
     * - RECHNUNG: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - LIEFERSCHEIN: Dokumentnummer
     * - ANGEBOT: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - AUFTRAGSBESTAETIGUNG: Dokumentnummer
     * - GUTSCHRIFT: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - SONSTIG: keine Pflichtfelder (darf null sein)
     */
    private boolean validiereGeschaeftsdaten(LieferantGeschaeftsdokument gd, LieferantDokumentTyp typ) {
        if (gd == null) {
            return false;
        }

        // Dokumentnummer ist für fast alle Typen Pflicht
        boolean hatDokumentNummer = gd.getDokumentNummer() != null && !gd.getDokumentNummer().isBlank();
        boolean hatBetrag = gd.getBetragBrutto() != null || gd.getBetragNetto() != null;

        if (typ == null) {
            // Wenn Typ unbekannt, mindestens Dokumentnummer prüfen
            if (!hatDokumentNummer) {
                log.debug("[Validierung] Fehlend: Dokumentnummer (Typ unbekannt)");
                return false;
            }
            return true;
        }

        switch (typ) {
            case RECHNUNG:
                // Rechnung: Dokumentnummer + Betrag erforderlich
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] RECHNUNG: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] RECHNUNG: Kein Betrag (Brutto oder Netto) erkannt");
                    return false;
                }
                return true;

            case LIEFERSCHEIN:
                // Lieferschein: Nur Dokumentnummer erforderlich (keine Beträge)
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] LIEFERSCHEIN: Dokumentnummer fehlt");
                    return false;
                }
                return true;

            case ANGEBOT:
                // Angebot: Dokumentnummer + Betrag (damit man vergleichen kann)
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] ANGEBOT: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] ANGEBOT: Kein Betrag erkannt");
                    return false;
                }
                return true;

            case AUFTRAGSBESTAETIGUNG:
                // AB: Dokumentnummer erforderlich
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] AUFTRAGSBESTAETIGUNG: Dokumentnummer fehlt");
                    return false;
                }
                return true;

            case GUTSCHRIFT:
                // Gutschrift: Dokumentnummer + Betrag
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] GUTSCHRIFT: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] GUTSCHRIFT: Kein Betrag erkannt");
                    return false;
                }
                return true;

            case SONSTIG:
                // Sonstige Dokumente haben keine Pflichtfelder
                return true;

            default:
                // Unbekannter Typ - mindestens Dokumentnummer
                return hatDokumentNummer;
        }
    }

    private Path getDateiPfad(LieferantDokument dokument) {
        String dateiname = dokument.getEffektiverGespeicherterDateiname();
        if (dateiname == null)
            return null;

        Long lieferantId = dokument.getLieferant().getId();

        // 1. E-Mail-Attachments: uploads/attachments/ (flat structure - most common)
        Path pfad = Path.of(uploadPath, "attachments", dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        // 2. E-Mail-Attachments: uploads/attachments/lieferanten/{lieferantId}/
        pfad = Path.of(uploadPath, "attachments", "lieferanten", lieferantId.toString(), dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        // 3. Manuell hochgeladene Dokumente: uploads/lieferanten/{lieferantId}/
        pfad = Path.of(uploadPath, "lieferanten", lieferantId.toString(), dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        // 4. Vendor-Invoices: uploads/attachments/vendor-invoices/
        pfad = Path.of(uploadPath, "attachments", "vendor-invoices", dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        // 5. Fallback: uploads/lieferant-emails/
        pfad = Path.of(uploadPath, "lieferant-emails", dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        // 6. Fallback: uploads/email/
        pfad = Path.of(uploadPath, "email", dateiname);
        if (Files.exists(pfad)) {
            log.debug("Datei gefunden unter: {}", pfad);
            return pfad;
        }

        log.warn("Datei nicht gefunden: {} (geprüft: attachments/, attachments/lieferanten/{}, lieferanten/{})",
                dateiname, lieferantId, lieferantId);
        return null;
    }

    /**
     * Prüft ob eine E-Mail-Domain zu einem bekannten Lieferanten gehört.
     * Extrahiert die Domain aus der E-Mail-Adresse und sucht passende Lieferanten.
     */
    public Optional<Lieferanten> findeLieferantByEmailDomain(String emailAddress) {
        if (emailAddress == null || !emailAddress.contains("@")) {
            return Optional.empty();
        }

        String domain = emailAddress.substring(emailAddress.lastIndexOf("@") + 1).toLowerCase();
        List<Lieferanten> lieferanten = lieferantenRepository.findByEmailDomain(domain);

        if (lieferanten.isEmpty()) {
            log.debug("Kein Lieferant gefunden für Domain: {}", domain);
            return Optional.empty();
        }

        if (lieferanten.size() > 1) {
            log.warn("Mehrere Lieferanten gefunden für Domain {}: {}", domain, lieferanten.size());
        }

        return Optional.of(lieferanten.getFirst());
    }

    /**
     * Führt automatische Verknüpfung basierend auf Referenznummer und Dokumenttyp
     * durch.
     */
    private void automatischeVerknuepfung(LieferantDokument dokument, LieferantGeschaeftsdokument geschaeftsdaten) {
        automatischeVerknuepfung(dokument, geschaeftsdaten, null);
    }

    private void automatischeVerknuepfung(LieferantDokument dokument, LieferantGeschaeftsdokument geschaeftsdaten,
            List<LieferantDokument> vorhandeneKandidaten) {
        String referenzNummer = geschaeftsdaten.getReferenzNummer() != null ? geschaeftsdaten.getReferenzNummer().trim()
                : null;

        if (referenzNummer == null || referenzNummer.isBlank()) {
            log.debug("[Verknüpfung] Dokument {} hat keine Referenznummer", dokument.getId());

            // FIXME: Wenn wir Fallback auch OHNE Referenznummer wollen, müssen wir hier
            // weiterlaufen.
            // Vorerst lasse ich es wie es war, um nicht zu viel auf einmal zu ändern.
            if (geschaeftsdaten.getBetragBrutto() == null) // Nur returnen wenn auch kein Brutto da ist für Fallback
                return;
        }

        if (dokument.getLieferant() == null) {
            log.warn("[Verknüpfung] Dokument {} hat keinen Lieferanten!", dokument.getId());
            return;
        }

        // Bestimme welche Dokumenttypen als Vorgänger gesucht werden sollen
        List<LieferantDokumentTyp> vorgaengerTypen = switch (dokument.getTyp()) {
            case RECHNUNG -> List.of(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LieferantDokumentTyp.LIEFERSCHEIN);
            case GUTSCHRIFT -> List.of(LieferantDokumentTyp.RECHNUNG); // Gutschrift verweist auf Rechnung
            case LIEFERSCHEIN -> List.of(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            case AUFTRAGSBESTAETIGUNG -> List.of(LieferantDokumentTyp.ANGEBOT);
            default -> List.of();
        };

        if (vorgaengerTypen.isEmpty()) {
            log.debug("[Verknüpfung] Dokumenttyp {} hat keine Vorgängertypen definiert", dokument.getTyp());
            return;
        }

        log.info("[Verknüpfung] Suche für Dokument {} (Typ: {}, Referenz: '{}') bei Lieferant {}",
                dokument.getId(), dokument.getTyp(), geschaeftsdaten.getReferenzNummer(),
                dokument.getLieferant().getId());

        // Suche passende Dokumente beim gleichen Lieferanten
        List<LieferantDokument> kandidaten;
        if (vorhandeneKandidaten != null) {
            kandidaten = vorhandeneKandidaten.stream()
                    .filter(d -> vorgaengerTypen.contains(d.getTyp()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            kandidaten = dokumentRepository.findByLieferantIdAndTypIn(
                    dokument.getLieferant().getId(), vorgaengerTypen);
        }

        log.info("[Verknüpfung] Gefunden: {} Kandidaten vom Typ {}", kandidaten.size(), vorgaengerTypen);

        for (LieferantDokument kandidat : kandidaten) {
            if (kandidat.getGeschaeftsdaten() == null) {
                log.debug("[Verknüpfung] Kandidat {} hat keine Geschäftsdaten", kandidat.getId());
                continue;
            }

            String kandidatNummer = kandidat.getGeschaeftsdaten().getDokumentNummer();
            log.debug("[Verknüpfung] Vergleiche Kandidat {} DokumentNr '{}' mit Referenz '{}'",
                    kandidat.getId(), kandidatNummer, referenzNummer);

            if (kandidatNummer != null && referenzNummer != null) {
                // Erst exakter Vergleich (Trim + IgnoreCase)
                if (kandidatNummer.trim().equalsIgnoreCase(referenzNummer.trim())) {
                    dokument.getVerknuepfteDokumente().add(kandidat);
                    log.info("[Verknüpfung] EXAKTER MATCH! Dokument {} -> {} (Referenz: {})",
                            dokument.getId(), kandidat.getId(), referenzNummer);
                    break;
                }
                // Dann normalisierter Vergleich (ohne Sonderzeichen/Leerzeichen)
                String kandidatNummerNorm = normalizeNummer(kandidatNummer);
                String referenzNummerNorm = normalizeNummer(referenzNummer);
                if (kandidatNummerNorm != null && referenzNummerNorm != null &&
                        kandidatNummerNorm.equals(referenzNummerNorm)) {
                    dokument.getVerknuepfteDokumente().add(kandidat);
                    log.info("[Verknüpfung] NORMALISIERTER MATCH! Dokument {} -> {} (Referenz: {} -> {}, DokNr: {} -> {})",
                            dokument.getId(), kandidat.getId(), referenzNummer, referenzNummerNorm,
                            kandidatNummer, kandidatNummerNorm);
                    break;
                }
            }
        }

        // FALLBACK: Wenn keine Referenznummer-Verknüpfung, versuche Brutto +
        // Datum-Match
        if (dokument.getVerknuepfteDokumente().isEmpty() && geschaeftsdaten.getBetragBrutto() != null) {
            java.math.BigDecimal meinBrutto = geschaeftsdaten.getBetragBrutto();
            java.time.LocalDate meinDatum = geschaeftsdaten.getDokumentDatum();

            for (LieferantDokument kandidat : kandidaten) {
                if (kandidat.getGeschaeftsdaten() == null)
                    continue;

                java.math.BigDecimal kandBrutto = kandidat.getGeschaeftsdaten().getBetragBrutto();
                java.time.LocalDate kandDatum = kandidat.getGeschaeftsdaten().getDokumentDatum();

                // Brutto muss gleich sein
                if (kandBrutto == null || meinBrutto.compareTo(kandBrutto) != 0)
                    continue;

                // Datum muss innerhalb ±1 Monat liegen
                if (meinDatum != null && kandDatum != null) {
                    java.time.LocalDate minDate = meinDatum.minusMonths(1);
                    java.time.LocalDate maxDate = meinDatum.plusMonths(1);
                    if (kandDatum.isBefore(minDate) || kandDatum.isAfter(maxDate))
                        continue;
                }

                dokument.getVerknuepfteDokumente().add(kandidat);
                log.info("[Verknüpfung] FALLBACK-MATCH! Dokument {} -> {} (Brutto: {}, Datum: {} vs {})",
                        dokument.getId(), kandidat.getId(), meinBrutto, meinDatum, kandDatum);
                break;
            }
        }

        if (dokument.getVerknuepfteDokumente().isEmpty()) {
            log.info("[Verknüpfung] Kein Match gefunden für Referenz '{}'", geschaeftsdaten.getReferenzNummer());
        }
    }

    /**
     * Nachträgliche Verknüpfung: Sucht ANDERE Dokumente die DIESES Dokument als
     * Referenz nutzen,
     * ODER Dokumente auf die DIESES Dokument verweist (bidirektionale Prüfung).
     * 
     * @param dokument Das Dokument für das Verknüpfungen gesucht werden sollen
     */
    /**
     * Nachträgliche Verknüpfung: Sucht ANDERE Dokumente die DIESES Dokument als
     * Referenz nutzen,
     * ODER Dokumente auf die DIESES Dokument verweist (bidirektionale Prüfung).
     * 
     * @param dokument Das Dokument für das Verknüpfungen gesucht werden sollen
     */
    public void performRelink(LieferantDokument dokument) {
        if (dokument.getLieferant() == null)
            return;
        // Lade alle Dokumente des Lieferanten
        List<LieferantDokument> alleDokumente = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(
                dokument.getLieferant().getId());
        performRelink(dokument, alleDokumente);
    }

    /**
     * Nachträgliche Verknüpfung mit bereits geladener Dokumentenliste (Optimierung
     * für Batch).
     */
    public void performRelink(LieferantDokument dokument, List<LieferantDokument> alleDokumente) {
        if (dokument.getLieferant() == null || dokument.getGeschaeftsdaten() == null) {
            return;
        }

        LieferantGeschaeftsdokument geschaeftsdaten = dokument.getGeschaeftsdaten();
        String meineDokumentNummer = geschaeftsdaten.getDokumentNummer() != null
                ? geschaeftsdaten.getDokumentNummer().trim()
                : null;

        // 1. Suche: Verweist dieses Dokument auf ein anderes? (z.B. Rechnung -> AB)
        automatischeVerknuepfung(dokument, geschaeftsdaten, alleDokumente);

        // 2. Suche: Verweisen andere Dokumente auf dieses? (z.B. Lieferschein ->
        // Rechnung - eher selten, meist andersrum)
        // Aber wichtig für Konsistenz
        if (meineDokumentNummer != null) {
            for (LieferantDokument anderes : alleDokumente) {
                if (anderes.getId().equals(dokument.getId()))
                    continue;
                if (anderes.getGeschaeftsdaten() == null)
                    continue;

                String fremdReferenz = anderes.getGeschaeftsdaten().getReferenzNummer();
                if (fremdReferenz != null && fremdReferenz.trim().equalsIgnoreCase(meineDokumentNummer)) {
                    if (!anderes.getVerknuepfteDokumente().contains(dokument)) {
                        anderes.getVerknuepfteDokumente().add(dokument);
                        dokumentRepository.save(anderes);
                        log.info("[Relink] Dokument {} verweist auf {} (Ref: {})",
                                anderes.getId(), dokument.getId(), fremdReferenz);
                    }
                }
            }
        }
    }

    // Alte Methode umbenannt/ersetzt durch allgemeinere public Methode
    private void nachtraeglicheVerknuepfung(LieferantDokument neuesDokument,
            LieferantGeschaeftsdokument neueGeschaeftsdaten) {
        performRelink(neuesDokument);
    }

    private String getMimeType(LieferantDokument dokument) {
        String dateiname = dokument.getEffektiverDateiname();
        if (dateiname == null)
            return "application/pdf";

        String lower = dateiname.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        return "application/pdf";
    }

    /**
     * Prüft ob ein JSON-String abgeschnitten wurde (unvollständig ist).
     * Das passiert wenn die API-Antwort das maxOutputTokens-Limit erreicht.
     */
    private boolean isJsonTruncated(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        String trimmed = json.trim();
        // Gültiges JSON muss mit } oder ] enden
        if (!trimmed.endsWith("}") && !trimmed.endsWith("]")) {
            return true;
        }
        // Zusätzlich: Prüfe ob Klammern balanciert sind
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        char prevChar = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{')
                    openBraces++;
                else if (c == '}')
                    openBraces--;
                else if (c == '[')
                    openBrackets++;
                else if (c == ']')
                    openBrackets--;
            }
            prevChar = c;
        }
        return openBraces != 0 || openBrackets != 0;
    }

    private String rufGeminiApi(byte[] dokumentBytes, String mimeType, boolean useProModel) {
        return rufGeminiApi(dokumentBytes, mimeType, useProModel, false);
    }

    private String rufGeminiApi(byte[] dokumentBytes, String mimeType, boolean useProModel,
            boolean skipArtikelPositionen) {
        API_LOCK.lock();
        try {
            // Ensure minimum delay between API calls to prevent rate limiting
            long now = System.currentTimeMillis();
            long elapsed = now - lastApiCallTime;
            if (elapsed < API_CALL_DELAY_MS && lastApiCallTime > 0) {
                try {
                    Thread.sleep(API_CALL_DELAY_MS - elapsed);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            log.debug("[Gemini API] Starte Dokumentanalyse API-Aufruf (Lock erworben, Model: {})",
                    useProModel ? "Pro" : "Standard");

            if (systemSettingsService.getGeminiApiKey().isBlank()) {
                log.warn("Gemini API Key nicht konfiguriert");
                return null;
            }

            String base64Data = Base64.getEncoder().encodeToString(dokumentBytes);

            // Build Request
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Prompt
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", SYSTEM_PROMPT_DOKUMENT_ANALYSE));
            systemInstruction.set("parts", sysParts);
            requestBody.set("systemInstruction", systemInstruction);

            // User Content
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();

            ObjectNode dataPart = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Data);
            dataPart.set("inlineData", inlineData);
            parts.add(dataPart);

            // Bei skipArtikelPositionen: Kürzere Anfrage um Tokenlimit zu vermeiden
            String userText = skipArtikelPositionen
                    ? "Analysiere dieses Dokument. WICHTIG: Gib artikelPositionen als leeres Array [] zurück um die Antwort kurz zu halten."
                    : "Analysiere dieses Dokument.";
            parts.add(objectMapper.createObjectNode().put("text", userText));

            userMsg.set("parts", parts);
            contents.add(userMsg);
            requestBody.set("contents", contents);

            // Config - maxOutputTokens wichtig für PDFs mit vielen Artikeln
            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", 0.0);
            config.put("responseMimeType", "application/json");
            config.put("maxOutputTokens", 16384); // Erhöht für PDFs mit vielen Artikelpositionen
            requestBody.set("generationConfig", config);

            // Send with RETRY logic
            String body = objectMapper.writeValueAsString(requestBody);

            // RETRY LOOP: Bei Fehlern bis zu MAX_RETRIES versuchen
            // Beim 3. Versuch (attempt == 2) wird das Pro-Model als Fallback verwendet
            HttpResponse<String> response = null;
            Exception lastException = null;

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    // Beim letzten Versuch (3.) auf Pro-Model wechseln als Fallback
                    String modelToUse;
                    if (attempt == MAX_RETRIES - 1 && !useProModel) {
                        modelToUse = geminiProModel;
                        log.info("[Gemini API] Letzter Versuch - wechsle zu Pro-Model: {}", geminiProModel);
                    } else {
                        modelToUse = useProModel ? geminiProModel : geminiModel;
                    }

                    String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                            modelToUse, systemSettingsService.getGeminiApiKey());

                    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(180))
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();

                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    lastApiCallTime = System.currentTimeMillis();

                    // Erfolg (2xx) - Parse Response
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode root = objectMapper.readTree(response.body());

                        // Prüfe ob candidates vorhanden
                        JsonNode candidates = root.path("candidates");
                        if (candidates.isEmpty() || candidates.get(0) == null) {
                            log.warn("[Gemini API] Keine candidates in Antwort - mglw. Safety-Block");
                            // Log safety ratings if present
                            if (root.has("promptFeedback")) {
                                log.warn("[Gemini API] promptFeedback: {}", root.get("promptFeedback"));
                            }
                            return null;
                        }

                        String result = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

                        // Log die KI-Antwort (gekürzt) für Debugging
                        String logPreview = result.length() > 500 ? result.substring(0, 500) + "..." : result;
                        log.info("[Gemini API] Dokumentanalyse erfolgreich (Attempt {}, Model: {}). Antwort: {}",
                                attempt + 1, modelToUse, logPreview);
                        return result;
                    }

                    // Rate Limiting (429) oder Server-Fehler (5xx) - Retry mit Backoff
                    if (response.statusCode() == 429 || response.statusCode() >= 500) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt); // Exponential backoff
                        log.warn("[Gemini API] Fehler {} bei Attempt {}/{} - Retry in {}ms. Body: {}",
                                response.statusCode(), attempt + 1, MAX_RETRIES, delay,
                                response.body().substring(0, Math.min(200, response.body().length())));
                        Thread.sleep(delay);
                        continue;
                    }

                    // Client-Fehler (4xx außer 429) - Nicht retryable
                    log.error("[Gemini API] Client-Fehler {} - kein Retry. Body: {}",
                            response.statusCode(), response.body());
                    return null;

                } catch (java.net.http.HttpTimeoutException timeoutEx) {
                    // Timeout - Retry mit Backoff
                    lastException = timeoutEx;
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt);
                    log.warn("[Gemini API] Timeout bei Attempt {}/{} - Retry in {}ms: {}",
                            attempt + 1, MAX_RETRIES, delay, timeoutEx.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    lastException = e;
                    log.error("[Gemini API] Exception bei Attempt {}/{}: {}",
                            attempt + 1, MAX_RETRIES, e.getMessage());
                    // Nur bei bestimmten Exceptions retry
                    if (e instanceof java.io.IOException) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        break; // Andere Exceptions nicht retryable
                    }
                }
            }

            // Alle Retries erschöpft
            log.error("[Gemini API] Alle {} Retries fehlgeschlagen. Letzte Exception: {}",
                    MAX_RETRIES, lastException != null ? lastException.getMessage() : "keine");
            return null;

        } catch (Exception e) {
            log.error("Fehler beim Aufruf der Gemini API", e);
            return null;
        } finally {
            API_LOCK.unlock();
        }
    }

    private LieferantGeschaeftsdokument mapJsonToData(String jsonString) {
        try {
            JsonNode json = objectMapper.readTree(jsonString);

            // Prüfe zuerst ob es ein Geschäftsdokument ist
            if (json.has("istGeschaeftsdokument") && !json.get("istGeschaeftsdokument").asBoolean()) {
                log.info("KI klassifiziert Dokument als KEIN Geschäftsdokument");
                return null;
            }

            // Prüfe Dokumenttyp - wenn SONSTIG, dann kein Geschäftsdokument
            if (json.has("dokumentTyp") && !json.get("dokumentTyp").isNull()) {
                String typString = json.get("dokumentTyp").asText().toUpperCase();
                if ("SONSTIG".equals(typString)) {
                    log.info("KI klassifiziert Dokument als SONSTIG (kein Geschäftsdokument)");
                    return null;
                }
            }

            // Neues Geschaeftsdokument erstellen
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setAiRawJson(jsonString);
            gd.setAnalysiertAm(LocalDateTime.now());

            // Dokumenttyp aus KI-Response
            if (json.has("dokumentTyp") && !json.get("dokumentTyp").isNull()) {
                String typString = json.get("dokumentTyp").asText().toUpperCase();
                try {
                    LieferantDokumentTyp typ = LieferantDokumentTyp.valueOf(typString);
                    gd.setDetectedTyp(typ);
                    log.info("KI erkannte Dokumenttyp: {}", typ);
                } catch (IllegalArgumentException e) {
                    log.debug("Unbekannter Dokumenttyp von KI: {}", typString);
                }
            }

            // Dokumentnummer
            if (json.has("dokumentNummer") && !json.get("dokumentNummer").isNull()) {
                gd.setDokumentNummer(json.get("dokumentNummer").asText().trim());
            }

            // Datum
            if (json.has("dokumentDatum") && !json.get("dokumentDatum").isNull()) {
                try {
                    gd.setDokumentDatum(
                            LocalDate.parse(json.get("dokumentDatum").asText(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception e) {
                    log.debug("Konnte Datum nicht parsen: {}", json.get("dokumentDatum").asText());
                }
            }

            // Beträge
            if (json.has("betragNetto") && !json.get("betragNetto").isNull()) {
                gd.setBetragNetto(new BigDecimal(json.get("betragNetto").asText()));
            }
            if (json.has("betragBrutto") && !json.get("betragBrutto").isNull()) {
                gd.setBetragBrutto(new BigDecimal(json.get("betragBrutto").asText()));
            }
            if (json.has("mwstSatz") && !json.get("mwstSatz").isNull()) {
                gd.setMwstSatz(new BigDecimal(json.get("mwstSatz").asText()));
            }

            // Liefertermin
            if (json.has("liefertermin") && !json.get("liefertermin").isNull()) {
                try {
                    gd.setLiefertermin(
                            LocalDate.parse(json.get("liefertermin").asText(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception e) {
                    log.debug("Konnte Liefertermin nicht parsen: {}", json.get("liefertermin").asText());
                }
            }

            // Bestellnummer und Referenznummer
            if (json.has("bestellnummer") && !json.get("bestellnummer").isNull()) {
                gd.setBestellnummer(json.get("bestellnummer").asText().trim());
            }
            if (json.has("referenzNummer") && !json.get("referenzNummer").isNull()) {
                gd.setReferenzNummer(json.get("referenzNummer").asText().trim());
            }

            // Zahlungsziel
            LocalDate zahlungszielDatum = null;
            if (json.has("zahlungsziel") && !json.get("zahlungsziel").isNull()) {
                try {
                    zahlungszielDatum = LocalDate.parse(json.get("zahlungsziel").asText(),
                            DateTimeFormatter.ISO_LOCAL_DATE);
                    gd.setZahlungsziel(zahlungszielDatum);
                } catch (Exception e) {
                    log.debug("Konnte Zahlungsziel nicht parsen: {}", json.get("zahlungsziel").asText());
                }
            }

            // Skonto-Konditionen parsen
            Integer skontoTage = null;
            BigDecimal skontoProzent = null;
            Integer nettoTage = null;

            if (json.has("skontoTage") && !json.get("skontoTage").isNull()) {
                skontoTage = json.get("skontoTage").asInt();
                gd.setSkontoTage(skontoTage);
                log.info("Skonto erkannt: {} Tage", skontoTage);
            }
            if (json.has("skontoProzent") && !json.get("skontoProzent").isNull()) {
                skontoProzent = new BigDecimal(json.get("skontoProzent").asText());
                gd.setSkontoProzent(skontoProzent);
                log.info("Skonto Prozent: {}%", skontoProzent);
            }
            if (json.has("nettoTage") && !json.get("nettoTage").isNull()) {
                nettoTage = json.get("nettoTage").asInt();
                gd.setNettoTage(nettoTage);
                log.info("Netto Tage: {}", nettoTage);
            } else {
                log.info("KEIN nettoTage in KI-Response gefunden. JSON hat: {}", json.fieldNames());
            }

            // Fallback: Berechne zahlungsziel aus nettoTage wenn nicht vorhanden
            if (zahlungszielDatum == null && nettoTage != null && gd.getDokumentDatum() != null) {
                zahlungszielDatum = gd.getDokumentDatum().plusDays(nettoTage);
                gd.setZahlungsziel(zahlungszielDatum);
                log.debug("Zahlungsziel aus nettoTage berechnet: {} + {} Tage = {}",
                        gd.getDokumentDatum(), nettoTage, zahlungszielDatum);
            }

            // Bereits gezahlt (Vorauskasse etc.)
            if (json.has("bereitsGezahlt") && !json.get("bereitsGezahlt").isNull()) {
                boolean bereitsGezahlt = json.get("bereitsGezahlt").asBoolean();
                gd.setBereitsGezahlt(bereitsGezahlt);
                if (bereitsGezahlt) {
                    gd.setBezahlt(true); // Wenn bereits gezahlt, setze auch bezahlt-Flag
                }
            }

            // Confidence
            if (json.has("confidence") && !json.get("confidence").isNull()) {
                gd.setAiConfidence(json.get("confidence").asDouble());
            }

            // Artikelpositionen verarbeiten (Preise aktualisieren) - hier noch nicht
            // möglich ohne Lieferant/DB
            // Verlagen wir auf nach dem Speichern wenn nötig.
            // if (dokument.getLieferant() != null) {
            // verarbeiteArtikelPositionen(json, dokument.getLieferant());
            // }

            return gd;

        } catch (Exception e) {
            log.error("Fehler beim Parsen der KI-Response: {}", jsonString, e);
            return null;
        }
    }

    private LieferantDokumentTyp erkenneTypAusNummer(String nummer) {
        if (nummer == null)
            return null;
        String upper = nummer.toUpperCase();

        if (upper.startsWith("RE") || upper.contains("RECHNUNG"))
            return LieferantDokumentTyp.RECHNUNG;
        if (upper.startsWith("AB") || upper.contains("AUFTRAGS"))
            return LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
        if (upper.startsWith("LS") || upper.contains("LIEFER"))
            return LieferantDokumentTyp.LIEFERSCHEIN;
        if (upper.startsWith("AN") || upper.contains("ANGEBOT"))
            return LieferantDokumentTyp.ANGEBOT;

        return null;
    }

    /**
     * Verarbeitet extrahierte Artikelpositionen und aktualisiert Preise in der
     * Datenbank.
     * Nur Artikel mit bekannter externeArtikelnummer werden aktualisiert.
     */
    private void verarbeiteArtikelPositionen(JsonNode json, Lieferanten lieferant) {
        if (lieferant == null) {
            log.debug("Kein Lieferant - überspringe Artikelpreis-Update");
            return;
        }

        if (!json.has("artikelPositionen") || json.get("artikelPositionen").isNull()) {
            return;
        }

        JsonNode positionen = json.get("artikelPositionen");
        if (!positionen.isArray()) {
            return;
        }

        int updated = 0;
        int skipped = 0;

        for (JsonNode pos : positionen) {
            String externeNr = pos.has("externeArtikelnummer") ? pos.get("externeArtikelnummer").asText(null) : null;
            if (externeNr == null || externeNr.isBlank()) {
                skipped++;
                continue;
            }

            // Suche in LieferantenArtikelPreise nach externeArtikelnummer + lieferantId
            var lapOpt = artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id(
                    externeNr.trim(), lieferant.getId());

            if (lapOpt.isEmpty()) {
                log.debug("Artikel nicht gefunden: {} für Lieferant {}", externeNr, lieferant.getLieferantenname());
                skipped++;
                continue;
            }

            // Extrahiere Preis und Einheit
            BigDecimal einzelpreis = pos.has("einzelpreis") && !pos.get("einzelpreis").isNull()
                    ? new BigDecimal(pos.get("einzelpreis").asText().replace(',', '.'))
                    : null;
            String preiseinheit = pos.has("preiseinheit") ? pos.get("preiseinheit").asText("kg") : "kg";

            if (einzelpreis == null) {
                skipped++;
                continue;
            }

            // Normalisiere Preis auf €/kg
            BigDecimal preisProKg = normalizePreisZuKg(einzelpreis, preiseinheit);
            if (preisProKg == null) {
                log.debug("Preis außerhalb Bereich für {}: {} {}", externeNr, einzelpreis, preiseinheit);
                skipped++;
                continue;
            }

            // Update LieferantenArtikelPreise
            LieferantenArtikelPreise lap = lapOpt.get();
            lap.setPreis(preisProKg);
            lap.setPreisAenderungsdatum(new Date());
            artikelPreiseRepository.save(lap);
            updated++;

            log.info("Artikelpreis aktualisiert: {} = {} €/kg (war: {} {})",
                    externeNr, preisProKg, einzelpreis, preiseinheit);
        }

        if (updated > 0 || skipped > 0) {
            log.info("Artikelpreise verarbeitet: {} aktualisiert, {} übersprungen", updated, skipped);
        }
    }

    /**
     * Normalisiert einen Preis auf €/kg basierend auf der Preiseinheit.
     * Übernimmt Logik aus ArtikelImportService.
     */
    private BigDecimal normalizePreisZuKg(BigDecimal preis, String einheit) {
        if (preis == null)
            return null;

        // Umrechnung basierend auf Einheit
        if (einheit != null) {
            String e = einheit.toLowerCase().trim();
            if (e.contains("tonne") || e.equals("t") || e.equals("to") || e.contains("1000 kg")
                    || e.contains("1000kg")) {
                preis = preis.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            } else if (e.contains("100 kg") || e.contains("100kg")) {
                preis = preis.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            }
            // "kg" = keine Umrechnung
        }

        // Plausibilitäts-Check: typische Stahlpreise liegen bei 0.50 - 10.00 €/kg
        BigDecimal min = new BigDecimal("0.50");
        BigDecimal max = new BigDecimal("10.00");

        if (preis.compareTo(max) > 0) {
            // Vielleicht wurde Preis pro Tonne ohne Einheit angegeben
            BigDecimal durchTausend = preis.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            if (durchTausend.compareTo(min) >= 0 && durchTausend.compareTo(max) <= 0) {
                return durchTausend;
            }
            BigDecimal durchHundert = preis.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (durchHundert.compareTo(min) >= 0 && durchHundert.compareTo(max) <= 0) {
                return durchHundert;
            }
            return null; // Außerhalb Bereich
        }

        if (preis.compareTo(min) < 0) {
            // Vielleicht wurde Preis pro Gramm angegeben
            BigDecimal malTausend = preis.multiply(BigDecimal.valueOf(1000));
            if (malTausend.compareTo(min) >= 0 && malTausend.compareTo(max) <= 0) {
                return malTausend;
            }
            return null;
        }

        return preis;
    }

    /**
     * Verarbeitet Artikelpositionen aus strukturierten ZUGFeRD-Daten und
     * aktualisiert Preise.
     * Analog zu verarbeiteArtikelPositionen, aber mit ZugferdArtikelPosition DTO.
     */
    private void verarbeiteZugferdArtikelPositionen(
            java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen,
            Lieferanten lieferant) {
        if (lieferant == null || positionen == null || positionen.isEmpty()) {
            return;
        }

        int updated = 0;
        int skipped = 0;

        for (var pos : positionen) {
            String externeNr = pos.getExterneArtikelnummer();
            if (externeNr == null || externeNr.isBlank()) {
                skipped++;
                continue;
            }

            // Suche in LieferantenArtikelPreise nach externeArtikelnummer + lieferantId
            var lapOpt = artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id(
                    externeNr.trim(), lieferant.getId());

            if (lapOpt.isEmpty()) {
                log.debug("ZUGFeRD-Artikel nicht in DB: {} für Lieferant {}", externeNr,
                        lieferant.getLieferantenname());
                skipped++;
                continue;
            }

            if (pos.getEinzelpreis() == null) {
                skipped++;
                continue;
            }

            // Normalisiere Preis auf €/kg
            BigDecimal preisProKg = normalizePreisZuKg(pos.getEinzelpreis(), pos.getPreiseinheit());
            if (preisProKg == null) {
                log.debug("ZUGFeRD-Preis außerhalb Bereich für {}: {} {}",
                        externeNr, pos.getEinzelpreis(), pos.getPreiseinheit());
                skipped++;
                continue;
            }

            // Update LieferantenArtikelPreise
            LieferantenArtikelPreise lap = lapOpt.get();
            lap.setPreis(preisProKg);
            lap.setPreisAenderungsdatum(new Date());
            artikelPreiseRepository.save(lap);
            updated++;

            log.info("ZUGFeRD-Artikelpreis aktualisiert: {} = {} €/kg (war: {} {})",
                    externeNr, preisProKg, pos.getEinzelpreis(), pos.getPreiseinheit());
        }

        if (updated > 0 || skipped > 0) {
            log.info("ZUGFeRD-Artikelpreise: {} aktualisiert, {} übersprungen", updated, skipped);
        }
    }

    /**
     * Normalisiert eine Dokumentnummer oder Referenznummer für zuverlässige Vergleiche.
     * Entfernt alle Nicht-Alphanumerischen Zeichen (Bindestriche, Leerzeichen, Punkte etc.)
     * und konvertiert zu Großbuchstaben.
     * 
     * Beispiele:
     * - "1-434-5" -> "14345"
     * - "AB-2024-001" -> "AB2024001"
     * - "RE 2024 / 001" -> "RE2024001"
     * 
     * @param nummer Die Original-Nummer
     * @return Die normalisierte Nummer (nur Buchstaben + Ziffern, uppercase) oder null
     */
    private String normalizeNummer(String nummer) {
        if (nummer == null || nummer.isBlank()) {
            return null;
        }
        String normalized = nummer.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Führt eine einmalige Neuverknüpfung aller bestehenden Dokumente durch.
     * Sollte nach Einführung der normalisierten Nummern-Logik einmal ausgeführt werden,
     * um bestehende Dokumente mit der neuen Logik zu verknüpfen.
     * 
     * @return Anzahl der neu verknüpften Dokumente
     */
    @Transactional
    public int relinkAlleDokumente() {
        log.info("[Relink] Starte einmalige Neuverknüpfung aller Dokumente...");
        
        List<LieferantDokument> alleDokumente = dokumentRepository.findAll();
        int verknuepft = 0;
        int gesamt = alleDokumente.size();
        
        for (LieferantDokument dokument : alleDokumente) {
            if (dokument.getGeschaeftsdaten() == null) {
                continue;
            }
            
            int vorher = dokument.getVerknuepfteDokumente().size();
            
            // Bestehende Verknüpfungen löschen und neu aufbauen
            dokument.getVerknuepfteDokumente().clear();
            automatischeVerknuepfung(dokument, dokument.getGeschaeftsdaten());
            
            int nachher = dokument.getVerknuepfteDokumente().size();
            if (nachher > vorher) {
                verknuepft++;
                log.info("[Relink] Dokument {}: {} -> {} Verknüpfungen", 
                        dokument.getId(), vorher, nachher);
            }
        }
        
        log.info("[Relink] Fertig! {} von {} Dokumenten neu verknüpft.", verknuepft, gesamt);
        return verknuepft;
    }

    /**
     * Führt eine Neuverknüpfung aller Dokumente eines bestimmten Lieferanten durch.
     * 
     * @param lieferantId ID des Lieferanten
     * @return Anzahl der neu verknüpften Dokumente
     */
    @Transactional
    public int relinkDokumenteByLieferant(Long lieferantId) {
        log.info("[Relink] Starte Neuverknüpfung für Lieferant {}...", lieferantId);
        
        List<LieferantDokument> dokumente = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId);
        int verknuepft = 0;
        int gesamt = dokumente.size();
        
        for (LieferantDokument dokument : dokumente) {
            if (dokument.getGeschaeftsdaten() == null) {
                continue;
            }
            
            int vorher = dokument.getVerknuepfteDokumente().size();
            
            // Bestehende Verknüpfungen löschen und neu aufbauen
            dokument.getVerknuepfteDokumente().clear();
            automatischeVerknuepfung(dokument, dokument.getGeschaeftsdaten());
            
            int nachher = dokument.getVerknuepfteDokumente().size();
            if (nachher > vorher) {
                verknuepft++;
                log.info("[Relink] Dokument {}: {} -> {} Verknüpfungen", 
                        dokument.getId(), vorher, nachher);
            }
        }
        
        log.info("[Relink] Fertig für Lieferant {}! {} von {} Dokumenten neu verknüpft.", 
                lieferantId, verknuepft, gesamt);
        return verknuepft;
    }
}
