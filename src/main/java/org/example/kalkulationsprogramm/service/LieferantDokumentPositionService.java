package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest die Positionen eines Lieferanten-Dokuments aus (KI-Antwort oder
 * ZUGFeRD/XRechnung) und speichert sie.
 *
 * <p>Positionen bekommen nur echte Geschäftsdokumente – siehe
 * {@link #hatPositionen}. Formulare, Kataloge oder Infoschreiben, die ein
 * Lieferant als PDF schickt, bleiben ohne.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantDokumentPositionService {

    /** Schlüssel der Positionsliste in der KI-Antwort (aiRawJson). */
    public static final String KI_FELD_POSITIONEN = "artikelPositionen";

    private static final Set<LieferantDokumentTyp> TYPEN_MIT_POSITIONEN = EnumSet.of(
            LieferantDokumentTyp.ANGEBOT,
            LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG,
            LieferantDokumentTyp.LIEFERSCHEIN,
            LieferantDokumentTyp.RECHNUNG,
            LieferantDokumentTyp.GUTSCHRIFT);

    static final int MAX_BEZEICHNUNG = 500;
    static final int MAX_ARTIKELNUMMER = 64;
    static final int MAX_EINHEIT = 20;
    /** Schutz gegen ausufernde KI-Antworten. */
    static final int MAX_POSITIONEN = 2000;

    private final LieferantDokumentPositionRepository positionRepository;

    /** Bekommt dieser Dokumenttyp Positionen? */
    public static boolean hatPositionen(LieferantDokumentTyp typ) {
        return typ != null && TYPEN_MIT_POSITIONEN.contains(typ);
    }

    /**
     * Ersetzt die gespeicherten Positionen eines Geschäftsdokuments.
     *
     * <ul>
     * <li>{@code positionen == null}: nicht ausgelesen – bestehende bleiben.</li>
     * <li>Typ ohne Positionen (z. B. SONSTIG): bestehende werden gelöscht.</li>
     * <li>sonst: alte löschen, neue in Belegreihenfolge anlegen. Eine bisherige
     * Positionsaufteilung geht dabei verloren; die Projektbeträge selbst bleiben.</li>
     * </ul>
     *
     * @return Anzahl gespeicherter Positionen
     */
    @Transactional
    public int ersetzePositionen(LieferantGeschaeftsdokument gd, LieferantDokumentTyp typ,
            List<AusgelesenePosition> positionen) {
        if (gd == null || gd.getId() == null || positionen == null) {
            return 0;
        }
        positionRepository.deleteByGeschaeftsdokumentId(gd.getId());
        if (!hatPositionen(typ)) {
            return 0;
        }
        List<LieferantDokumentPosition> neu = new ArrayList<>();
        int nr = 1;
        for (AusgelesenePosition a : positionen) {
            if (a == null || neu.size() >= MAX_POSITIONEN) {
                continue;
            }
            LieferantDokumentPosition p = new LieferantDokumentPosition();
            p.setGeschaeftsdokument(gd);
            p.setPositionNr(nr++);
            p.setPositionsArt(a.positionsArt() != null ? a.positionsArt() : PositionsArt.WARE);
            p.setExterneArtikelnummer(kuerze(a.externeArtikelnummer(), MAX_ARTIKELNUMMER));
            p.setBezeichnung(bezeichnungOderErsatz(a, p.getPositionNr()));
            // Spaltengrenzen einhalten: ein Ausreißer der KI darf die Analyse nicht
            // beim Commit kippen (die Positionen laufen in derselben Transaktion).
            p.setMenge(passend(a.menge(), 15, 3));
            p.setMengeneinheit(kuerze(a.mengeneinheit(), MAX_EINHEIT));
            p.setEinzelpreis(passend(a.einzelpreis(), 15, 4));
            p.setPreiseinheit(kuerze(a.preiseinheit(), MAX_EINHEIT));
            p.setGesamtpreisNetto(passend(a.gesamtpreisNetto(), 15, 2));
            neu.add(p);
        }
        positionRepository.saveAll(neu);
        log.debug("[Positionen] Dokument {}: {} Positionen gespeichert", gd.getId(), neu.size());
        return neu.size();
    }

    @Transactional(readOnly = true)
    public List<LieferantDokumentPosition> findePositionen(Long geschaeftsdokumentId) {
        return positionRepository.findByGeschaeftsdokumentId(geschaeftsdokumentId);
    }

    // ------------------------------------------------------------ Einlesen

    /**
     * Positionen aus der KI-Antwort.
     *
     * @return {@code null}, wenn die Antwort keine Positionsliste enthält
     */
    public static List<AusgelesenePosition> ausKiAntwort(JsonNode antwort) {
        JsonNode liste = antwort == null ? null : antwort.get(KI_FELD_POSITIONEN);
        if (liste == null || !liste.isArray()) {
            return null;
        }
        List<AusgelesenePosition> ergebnis = new ArrayList<>();
        for (JsonNode pos : liste) {
            if (pos == null || !pos.isObject()) {
                continue;
            }
            String artikelnummer = text(pos, "externeArtikelnummer");
            String bezeichnung = text(pos, "bezeichnung");
            BigDecimal gesamt = betrag(pos.get("gesamtpreisNetto"));
            if (artikelnummer == null && bezeichnung == null && gesamt == null) {
                continue;
            }
            PositionsArt art = PositionsArt.vonText(text(pos, "positionsArt"));
            if (art == PositionsArt.WARE && text(pos, "positionsArt") == null
                    && gesamt != null && gesamt.signum() < 0) {
                art = PositionsArt.RABATT;
            }
            ergebnis.add(new AusgelesenePosition(art, artikelnummer, bezeichnung,
                    betrag(pos.get("menge")), text(pos, "mengeneinheit"),
                    betrag(pos.get("einzelpreis")), text(pos, "preiseinheit"), gesamt));
        }
        return ergebnis;
    }

    /** Positionen aus ZUGFeRD/XRechnung; {@code null} bleibt {@code null}. */
    public static List<AusgelesenePosition> ausZugferd(List<ZugferdArtikelPosition> positionen) {
        if (positionen == null) {
            return null;
        }
        return positionen.stream()
                .map(p -> new ZugferdArtikelPosition(p.getExterneArtikelnummer(), p.getBezeichnung(),
                        vernuenftig(p.getMenge()), p.getMengeneinheit(), vernuenftig(p.getEinzelpreis()),
                        p.getPreiseinheit(), vernuenftig(p.getGesamtpreisNetto())))
                .map(p -> new AusgelesenePosition(
                        p.getGesamtpreisNetto() != null && p.getGesamtpreisNetto().signum() < 0
                                ? PositionsArt.RABATT
                                : PositionsArt.WARE,
                        leerAlsNull(p.getExterneArtikelnummer()), leerAlsNull(p.getBezeichnung()),
                        p.getMenge(), leerAlsNull(p.getMengeneinheit()),
                        p.getEinzelpreis(), leerAlsNull(p.getPreiseinheit()), p.getGesamtpreisNetto()))
                .toList();
    }

    /**
     * Schreibt Positionen in eine gespeicherte KI-Antwort zurück. Der
     * Dokumentenabgleich liest Positionen aus der KI-Antwort – so zählen auch
     * nachträglich ausgelesene Positionen dort mit.
     *
     * @return neue KI-Antwort, oder {@code null}, wenn {@code aiRawJson} leer/unlesbar ist
     */
    public static String mitPositionen(String aiRawJson, List<AusgelesenePosition> positionen,
            ObjectMapper objectMapper) {
        if (aiRawJson == null || aiRawJson.isBlank() || positionen == null) {
            return null;
        }
        try {
            JsonNode wurzel = objectMapper.readTree(aiRawJson);
            if (!(wurzel instanceof ObjectNode objekt)) {
                return null;
            }
            ArrayNode liste = objektListe(positionen, objectMapper);
            objekt.set(KI_FELD_POSITIONEN, liste);
            return objectMapper.writeValueAsString(objekt);
        } catch (Exception e) {
            // Nur der Fehlertyp: Jackson-Meldungen zitieren Inhalt (DSGVO).
            log.debug("[Positionen] KI-Antwort nicht lesbar: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static ArrayNode objektListe(List<AusgelesenePosition> positionen, ObjectMapper objectMapper) {
        ArrayNode liste = objectMapper.createArrayNode();
        int nr = 1;
        for (AusgelesenePosition p : positionen) {
            ObjectNode o = liste.addObject();
            o.put("positionNr", nr++);
            o.put("positionsArt", p.positionsArt() != null ? p.positionsArt().name() : PositionsArt.WARE.name());
            o.put("externeArtikelnummer", p.externeArtikelnummer());
            o.put("bezeichnung", p.bezeichnung());
            o.put("menge", p.menge());
            o.put("mengeneinheit", p.mengeneinheit());
            o.put("einzelpreis", p.einzelpreis());
            o.put("preiseinheit", p.preiseinheit());
            o.put("gesamtpreisNetto", p.gesamtpreisNetto());
        }
        return liste;
    }

    // ------------------------------------------------------------ Hilfen

    /**
     * Rundet auf die Nachkommastellen der Spalte; Werte, die nicht in
     * DECIMAL(precision, scale) passen, werden verworfen.
     */
    static BigDecimal passend(BigDecimal wert, int precision, int scale) {
        // Größe VOR dem Runden prüfen: setScale auf "1e999999999" baut eine Zahl mit
        // einer Milliarde Stellen.
        if (wert == null || wert.precision() - wert.scale() > precision - scale) {
            return null;
        }
        BigDecimal gerundet = wert.setScale(scale, java.math.RoundingMode.HALF_UP);
        return gerundet.precision() - gerundet.scale() > precision - scale ? null : gerundet;
    }

    private static String bezeichnungOderErsatz(AusgelesenePosition a, int nr) {
        String bezeichnung = leerAlsNull(a.bezeichnung());
        if (bezeichnung == null) {
            bezeichnung = a.externeArtikelnummer() != null
                    ? "Artikel " + a.externeArtikelnummer().trim()
                    : "Position " + nr;
        }
        return kuerze(bezeichnung, MAX_BEZEICHNUNG);
    }

    private static String text(JsonNode node, String feld) {
        JsonNode wert = node.get(feld);
        if (wert == null || wert.isNull()) {
            return null;
        }
        return leerAlsNull(wert.asText());
    }

    private static String leerAlsNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String kuerze(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * Zahl aus der KI-Antwort: als Zahl oder als Text, auch im deutschen
     * Format ("1.234,56").
     */
    static BigDecimal betrag(JsonNode wert) {
        if (wert == null || wert.isNull()) {
            return null;
        }
        if (wert.isNumber()) {
            if ((wert.isDouble() || wert.isFloat()) && !Double.isFinite(wert.doubleValue())) {
                return null;
            }
            try {
                return vernuenftig(wert.decimalValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String roh = wert.asText();
        if (roh == null) {
            return null;
        }
        String t = roh.replace("€", "").replace(" ", "").replace(" ", "").trim();
        if (t.isEmpty() || t.length() > 32) {
            return null;
        }
        if (t.contains(",")) {
            // Deutsches Format: Punkt = Tausender, Komma = Dezimal
            t = t.replace(".", "").replace(',', '.');
        }
        try {
            return vernuenftig(new BigDecimal(t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Höchstens 18 Vorkommastellen; Nachkommastellen werden auf 10 gerundet
     * ("0.8333333333333334" €/kg ist ein echter Wert). Erst ab 50 Nachkommastellen
     * oder großem Exponenten wird verworfen – dort wäre schon das Runden teuer.
     */
    static BigDecimal vernuenftig(BigDecimal wert) {
        if (wert == null || wert.scale() > 50 || wert.scale() < -18 || wert.precision() - wert.scale() > 18) {
            return null;
        }
        return wert.scale() > 10 ? wert.setScale(10, java.math.RoundingMode.HALF_UP) : wert;
    }
}
