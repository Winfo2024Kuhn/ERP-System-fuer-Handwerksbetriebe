package org.example.kalkulationsprogramm.service;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.BwaTyp;
import org.example.kalkulationsprogramm.domain.BwaUpload;
import org.example.kalkulationsprogramm.repository.BwaUploadRepository;
import org.example.kalkulationsprogramm.util.KiGeldbetrag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Legt eine BWA aus einer Steuerberater-Mail ab: Monat und Gemeinkosten per KI lesen
 * und als {@link BwaUpload} speichern. Die PDF bleibt der Mail-Anhang selbst.
 */
@Slf4j
@Service
public class SteuerberaterBwaAblage {

    private static final String BWA_PROMPT = """
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

    private final BwaUploadRepository bwaUploadRepository;
    private final DokumentAnalyseService dokumentAnalyseService;
    private final ObjectMapper objectMapper;
    private final String mailAttachmentDir;

    public SteuerberaterBwaAblage(BwaUploadRepository bwaUploadRepository,
            DokumentAnalyseService dokumentAnalyseService,
            ObjectMapper objectMapper,
            @Value("${file.mail-attachment-dir}") String mailAttachmentDir) {
        this.bwaUploadRepository = bwaUploadRepository;
        this.dokumentAnalyseService = dokumentAnalyseService;
        this.objectMapper = objectMapper;
        this.mailAttachmentDir = mailAttachmentDir;
    }

    /** Was die KI aus der BWA gelesen hat; jedes Feld kann fehlen. */
    private record BwaWerte(Integer jahr, Integer monat, BigDecimal gemeinkosten) {
        static final BwaWerte LEER = new BwaWerte(null, null, null);
    }

    /**
     * Liest die BWA per KI und legt sie an. Dieselbe Mail mit derselben Datei wird nur einmal
     * angelegt (Backfill). Scheitert die KI, entsteht die BWA trotzdem – mit dem Monat aus dem
     * Dateinamen und ohne Gemeinkosten.
     */
    public void legeAb(SteuerberaterAnhang quelle) {
        log.info("[Steuerberater] Verarbeite BWA: {}", quelle.originalDateiname());

        if (bwaUploadRepository.existsBySourceEmailIdAndOriginalDateiname(quelle.email().getId(),
                quelle.originalDateiname())) {
            log.info("[Steuerberater] BWA {} bereits importiert (Skipped)", quelle.originalDateiname());
            return;
        }

        try {
            Path pdfPath = Path.of(mailAttachmentDir, quelle.anhang().getStoredFilename());
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath);
                return;
            }
            byte[] fileBytes = Files.readAllBytes(pdfPath);
            String aiResponse = dokumentAnalyseService.rufKiMitPrompt(fileBytes, "application/pdf", BWA_PROMPT, true);
            speichereAnalysiert(quelle, aiResponse, leseWerte(aiResponse));
        } catch (Exception e) {
            log.error("[Steuerberater] Fehler bei BWA-Verarbeitung: {}", e.getMessage(), e);
            // Rückfall ohne KI
            SteuerberaterPeriode periode = SteuerberaterPeriode.ausDateiname(quelle.originalDateiname());
            bwaUploadRepository.save(neueBwa(quelle, periode.jahr(), periode.monat()));
        }
    }

    /**
     * Liest Monat, Jahr und Gemeinkosten aus der KI-Antwort (Markdown-Block erlaubt).
     * Ungültiges JSON wirft – dann greift der Rückfall in {@link #legeAb}.
     */
    private BwaWerte leseWerte(String aiResponse) {
        if (aiResponse == null) {
            return BwaWerte.LEER;
        }
        String json = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
        if (!json.startsWith("{")) {
            return BwaWerte.LEER;
        }
        JsonNode root = objectMapper.readTree(json);
        return new BwaWerte(
                root.hasNonNull("jahr") ? root.get("jahr").asInt(0) : null,
                root.hasNonNull("monat") ? root.get("monat").asInt(0) : null,
                ermittleGemeinkosten(root));
    }

    private void speichereAnalysiert(SteuerberaterAnhang quelle, String aiResponse, BwaWerte werte) {
        Integer jahr = werte.jahr();
        Integer monat = werte.monat();
        if (jahr == null || monat == null) {
            SteuerberaterPeriode periode = SteuerberaterPeriode.ausDateiname(quelle.originalDateiname());
            if (jahr == null) jahr = periode.jahr();
            if (monat == null) monat = periode.monat();
        }

        BwaUpload bwa = neueBwa(quelle, jahr, monat);
        // Gemeinkosten übertragen (für Dashboard und Stundensatz-Rechner).
        // gesamtGemeinkosten ist das Feld, das die Oberfläche anzeigt --
        // kostenAusBwa bleibt dem Rechnungs-Abgleich vorbehalten.
        BigDecimal gemeinkosten = werte.gemeinkosten();
        if (gemeinkosten != null) {
            bwa.setGesamtGemeinkosten(gemeinkosten);
            bwa.setAnalysiert(true);
            bwa.setAnalyseDatum(LocalDateTime.now());
        } else {
            // Kein verwertbarer Betrag erkannt: NICHT als analysiert markieren,
            // sonst zeigt die Karte gruen "Analysiert" neben 0,00 EUR an.
            log.warn("[Steuerberater] BWA {} enthaelt keine erkennbaren Gemeinkosten - bleibt 'Ausstehend'",
                    quelle.originalDateiname());
        }
        bwa.setAiRawJson(aiResponse);

        bwaUploadRepository.save(bwa);
        log.info("[Steuerberater] BWA für {}/{} erstellt (Gemeinkosten: {})", monat, jahr, gemeinkosten);
    }

    private static BwaUpload neueBwa(SteuerberaterAnhang quelle, Integer jahr, Integer monat) {
        BwaUpload bwa = new BwaUpload();
        bwa.setTyp(BwaTyp.MONATLICH);
        bwa.setJahr(jahr);
        bwa.setMonat(monat);
        bwa.setOriginalDateiname(quelle.originalDateiname());
        bwa.setGespeicherterDateiname(quelle.anhang().getStoredFilename());
        bwa.setUploadDatum(LocalDateTime.now());
        bwa.setSteuerberater(quelle.steuerberater());
        bwa.setSourceEmail(quelle.email());
        return bwa;
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
    static BigDecimal ermittleGemeinkosten(JsonNode root) {
        BigDecimal direkt = KiGeldbetrag.lese(root, "gemeinkosten");
        if (direkt != null && direkt.signum() > 0) {
            return skaliere(direkt);
        }
        BigDecimal gesamt = KiGeldbetrag.lese(root, "gesamtkosten");
        if (gesamt == null || gesamt.signum() <= 0) {
            return null;
        }
        BigDecimal rest = gesamt
                .subtract(nz(KiGeldbetrag.lese(root, "personalkosten")))
                .subtract(nz(KiGeldbetrag.lese(root, "wareneinsatz")))
                .subtract(nz(KiGeldbetrag.lese(root, "fremdleistungen")));
        return rest.signum() > 0 ? skaliere(rest) : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Auf die Genauigkeit der Spalte bringen, statt die DB still runden zu lassen. */
    private static BigDecimal skaliere(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
