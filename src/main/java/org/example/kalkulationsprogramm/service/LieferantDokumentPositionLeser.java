package org.example.kalkulationsprogramm.service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest nur die Positionen eines Dokuments per KI – vollständig, auch bei
 * sehr langen Belegen.
 *
 * <p>Wird gebraucht, wenn die normale Analyse die Positionen nicht liefern
 * konnte (Antwort zu lang) oder ein älteres Dokument noch keine hat. Passt
 * die Antwort nicht in ein Ausgabelimit, liest der Leser die PDF in
 * Seitenblöcken und fügt die Ergebnisse zusammen. Eine halbe Liste gibt es
 * nicht: Scheitert ein Block, ist das Ergebnis {@code null}.
 *
 * <p>Der eigentliche KI-Aufruf kommt als {@link KiAufruf} herein – so bleibt
 * der Leser ohne HTTP testbar und ohne Abhängigkeit zur Analyse-Klasse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LieferantDokumentPositionLeser {

    /** Seiten je Block beim seitenweisen Lesen. */
    static final int SEITEN_JE_BLOCK = 2;
    /** Mehr Seiten lesen wir nicht einzeln – Schutz vor endlosen Aufrufen. */
    static final int MAX_SEITEN = 60;

    static final String PROMPT_NUR_POSITIONEN = """
            Lies ALLE Positionen (Tabellenzeilen) dieses Lieferanten-Dokuments aus.
            Antworte NUR mit JSON in genau dieser Form:
            {"artikelPositionen": [ { ... }, ... ]}

            Jede Position:
            {"positionNr": 1,
             "positionsArt": "WARE|NEBENKOSTEN|RABATT",
             "externeArtikelnummer": "Artikel-/Materialnummer des Lieferanten oder null",
             "bezeichnung": "Artikeltext, kurz und vollständig (z. B. 'Flachstahl 50x5 S235JR')",
             "menge": 12.5 oder null,
             "mengeneinheit": "Stück|kg|m|t|... oder null",
             "einzelpreis": 12.34 oder null,
             "preiseinheit": "€/Stück|€/kg|€/100kg|€/t|€/m ... oder null",
             "gesamtpreisNetto": 154.25 oder null,
             "werkstoff": "Werkstoff/Güte, z. B. 'S235JR+AR', '1.4301' – nur wenn aufgedruckt, sonst null",
             "charge": "Charge/Schmelze/Heat No. exakt wie gedruckt – nur wenn aufgedruckt, sonst null",
             "abmessung": "Maße, z. B. '50x5', '60,3x2,9', 'IPE 200' – nur wenn aufgedruckt, sonst null"}

            Regeln:
            - JEDE Zeile mit Betrag oder Artikel ist eine Position, auch ohne Artikelnummer.
            - positionsArt NEBENKOSTEN: Fracht, Versand, Porto, Verpackung, Palette, Maut,
              Energie-, Legierungs-, Material-, Mindermengen- oder Kleinmengenzuschlag.
            - positionsArt RABATT: Rabatt-, Abzugs- oder Bonuszeilen; gesamtpreisNetto NEGATIV.
            - Alles andere ist WARE (Material, Artikel, Arbeitsleistung, Montage).
            - einzelpreis ist der Preis EINER Preiseinheit, gesamtpreisNetto die Zeilensumme netto.
            - KEINE Summenzeilen (Zwischensumme, Nettobetrag, MwSt, Gesamtbetrag) aufnehmen.
            - KEINE reinen Text-/Hinweiszeilen ohne Menge und Betrag aufnehmen.
            - Wird eine Position in Bestandteile mit Einzelpreisen zerlegt (Ausstattung,
              Zubehör, Optionen) und endet mit einer "Positionssumme", nur die Hauptposition
              mit der Positionssumme aufnehmen, nicht die Bestandteile.
            - Ist auf diesen Seiten nur eine solche Aufschlüsselung zu sehen (ohne eigene
              Positionsnummer in der Positionsübersicht), dann {"artikelPositionen": []}.
            - Zahlen als Dezimalzahl mit Punkt, ohne Währungszeichen. Fehlende Werte = null.
            - Werkstoffzeugnis (Abnahmeprüfzeugnis 3.1/3.2, Werkszeugnis): jede Erzeugnis-Zeile
              je Charge ist eine Position (bezeichnung z. B. "Flachstahl 50x5", Preise null,
              positionsArt WARE). Prüfwerte und chemische Analyse sind KEINE Positionen.
            - Wenn das Dokument keine Positionen hat: {"artikelPositionen": []}
            """;

    /** Antwort eines KI-Aufrufs. */
    public record KiAntwort(String text, boolean abgeschnitten) {
    }

    /** Ein KI-Aufruf mit Dokument und Prompt; {@code null} = Aufruf gescheitert. */
    @FunctionalInterface
    public interface KiAufruf {
        KiAntwort rufe(byte[] dokument, String mimeType, String prompt);
    }

    private final ObjectMapper objectMapper;

    /**
     * Liest alle Positionen; erst das ganze Dokument, bei zu langer Antwort
     * seitenweise.
     *
     * @return alle Positionen in Belegreihenfolge, oder {@code null}, wenn sie
     *         nicht vollständig gelesen werden konnten
     */
    public List<AusgelesenePosition> lese(byte[] dokument, String mimeType, KiAufruf aufruf) {
        if (dokument == null || dokument.length == 0 || aufruf == null) {
            return null;
        }
        KiAntwort ganz = aufruf.rufe(dokument, mimeType, PROMPT_NUR_POSITIONEN);
        List<AusgelesenePosition> ergebnis = auswerten(ganz);
        if (ergebnis != null) {
            return ergebnis;
        }
        if (ganz == null || !ganz.abgeschnitten() || !"application/pdf".equals(mimeType)) {
            return null;
        }
        log.info("[Positionen] Antwort zu lang – lese das Dokument seitenweise");
        return leseSeitenweise(dokument, mimeType, aufruf);
    }

    List<AusgelesenePosition> leseSeitenweise(byte[] pdf, String mimeType, KiAufruf aufruf) {
        try (PDDocument quelle = Loader.loadPDF(pdf)) {
            int seiten = quelle.getNumberOfPages();
            if (seiten <= 1 || seiten > MAX_SEITEN) {
                log.warn("[Positionen] Seitenweises Lesen nicht möglich ({} Seiten)", seiten);
                return null;
            }
            List<AusgelesenePosition> alle = new ArrayList<>();
            for (int start = 0; start < seiten; start += SEITEN_JE_BLOCK) {
                int ende = Math.min(start + SEITEN_JE_BLOCK, seiten);
                List<AusgelesenePosition> block = leseBlock(quelle, start, ende, mimeType, aufruf);
                if (block == null && ende - start > 1) {
                    // Block zu dicht beschrieben: Seite für Seite versuchen
                    block = new ArrayList<>();
                    for (int s = start; s < ende && block != null; s++) {
                        List<AusgelesenePosition> seite = leseBlock(quelle, s, s + 1, mimeType, aufruf);
                        if (seite == null) {
                            block = null;
                        } else {
                            block.addAll(seite);
                        }
                    }
                }
                if (block == null) {
                    log.warn("[Positionen] Seiten {}-{} nicht lesbar – keine Positionen gespeichert",
                            start + 1, ende);
                    return null;
                }
                alle.addAll(block);
            }
            return alle;
        } catch (Exception e) {
            log.warn("[Positionen] PDF nicht aufteilbar: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private List<AusgelesenePosition> leseBlock(PDDocument quelle, int start, int ende, String mimeType,
            KiAufruf aufruf) throws java.io.IOException {
        byte[] teil = seiten(quelle, start, ende);
        return auswerten(aufruf.rufe(teil, mimeType, PROMPT_NUR_POSITIONEN));
    }

    private static byte[] seiten(PDDocument quelle, int start, int ende) throws java.io.IOException {
        try (PDDocument teil = new PDDocument()) {
            for (int i = start; i < ende; i++) {
                teil.importPage(quelle.getPage(i));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            teil.save(out);
            return out.toByteArray();
        }
    }

    private List<AusgelesenePosition> auswerten(KiAntwort antwort) {
        if (antwort == null || antwort.abgeschnitten() || antwort.text() == null) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(antwort.text());
            return LieferantDokumentPositionService.ausKiAntwort(json);
        } catch (Exception e) {
            log.debug("[Positionen] KI-Antwort nicht lesbar: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
