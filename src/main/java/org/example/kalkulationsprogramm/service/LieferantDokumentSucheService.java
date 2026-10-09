package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.PositionsSuchtext;
import org.example.kalkulationsprogramm.dto.PositionsSuchtreffer;
import org.example.kalkulationsprogramm.dto.PositionsTrefferDto;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Sucht Lieferanten-Dokumente über ihre Positionen: „Flachstahl“ findet
 * Werkstoffzeugnis, Lieferschein und Rechnung, in denen Flachstahl steht –
 * ebenso Werkstoff, Charge, Abmessung oder Artikelnummer.
 *
 * <p>Mehrere Suchwörter müssen in derselben Position stehen. Je Dokument kommt
 * die erste passende Position als Trefferzeile zurück.
 *
 * <p>Außerdem die Freitextsuche der Dokumentübersicht (Eingang): Nummern,
 * Betrag, Typ, Lieferant, Referenz-/Bestellnummer und Kommission.
 */
@Service
@RequiredArgsConstructor
public class LieferantDokumentSucheService {

    /** Mehr Positionen lesen wir je Suche nicht – die Liste zeigt Dokumente, nicht Positionen. */
    static final int MAX_POSITIONEN = 500;

    private static final String TRENNER = " · ";

    private final LieferantDokumentPositionRepository positionRepository;
    private final LieferantDokumentService dokumentService;
    private final ObjectMapper objectMapper;

    /**
     * Positionstreffer für die Dokumentliste eines Lieferanten. Mit Mitarbeiter
     * nur in den Dokumenttypen, die er sehen darf – wie
     * {@link LieferantDokumentService#getDokumenteFiltered}.
     *
     * @param mitarbeiterId {@code null} = ohne Rechteprüfung (wie die Dokumentliste ohne Token)
     */
    @Transactional(readOnly = true)
    public List<PositionsTrefferDto> sucheBeiLieferant(Long lieferantId, String eingabe, Long mitarbeiterId) {
        Collection<LieferantDokumentTyp> typen = mitarbeiterId == null
                ? EnumSet.allOf(LieferantDokumentTyp.class)
                : dokumentService.getBerechtigungen(mitarbeiterId).getSichtbareTypen();
        return new ArrayList<>(suchePositionen(eingabe, lieferantId, typen, null, null).values());
    }

    /**
     * Sucht in den Positionen.
     *
     * @param lieferantId {@code null} = alle Lieferanten
     * @param typen       nur Dokumente dieser Typen; leer = keine Treffer
     * @param von         Belegdatum ab; {@code null} = ohne Grenze
     * @param bis         Belegdatum bis; {@code null} = ohne Grenze
     * @return Treffer je Dokument-ID, neueste Dokumente zuerst
     */
    @Transactional(readOnly = true)
    public Map<Long, PositionsTrefferDto> suchePositionen(String eingabe, Long lieferantId,
            Collection<LieferantDokumentTyp> typen, LocalDate von, LocalDate bis) {
        List<String> woerter = PositionsSuchtext.suchwoerter(eingabe);
        if (woerter.isEmpty() || typen == null || typen.isEmpty()) {
            return Map.of();
        }
        List<PositionsSuchtreffer> treffer = positionRepository.suche(typen, lieferantId, von, bis,
                muster(woerter, 0), muster(woerter, 1), muster(woerter, 2), muster(woerter, 3), muster(woerter, 4),
                PageRequest.of(0, MAX_POSITIONEN));

        Map<Long, PositionsSuchtreffer> erste = new LinkedHashMap<>();
        Map<Long, Integer> anzahl = new LinkedHashMap<>();
        for (PositionsSuchtreffer t : treffer) {
            erste.putIfAbsent(t.dokumentId(), t);
            anzahl.merge(t.dokumentId(), 1, Integer::sum);
        }
        Map<Long, PositionsTrefferDto> ergebnis = new LinkedHashMap<>();
        erste.forEach((id, t) -> ergebnis.put(id,
                new PositionsTrefferDto(id, trefferText(t), anzahl.get(id) - 1)));
        return ergebnis;
    }

    /**
     * Passt ein Eingangsdokument zur Freitextsuche der Dokumentübersicht?
     * Positionen sucht {@link #suchePositionen} – hier nur die Kopfdaten.
     *
     * @param q Suchbegriff, bereits kleingeschrieben
     */
    public boolean passtZurEingangssuche(LieferantGeschaeftsdokument gd, String q) {
        if (enthaelt(gd.getDokumentNummer(), q) || enthaelt(gd.getReferenzNummer(), q)
                || enthaelt(gd.getBestellnummer(), q)) {
            return true;
        }
        if (gd.getBetragBrutto() != null && gd.getBetragBrutto().toPlainString().contains(q)) {
            return true;
        }
        if (gd.getDokument() != null) {
            if (gd.getDokument().getTyp() != null && enthaelt(gd.getDokument().getTyp().name(), q)) {
                return true;
            }
            if (gd.getDokument().getLieferant() != null
                    && enthaelt(gd.getDokument().getLieferant().getLieferantenname(), q)) {
                return true;
            }
        }
        return kommissionEnthaelt(gd.getAiRawJson(), q);
    }

    /** Kommission/Bauvorhaben steht nur in der KI-Antwort – erst grob prüfen, dann lesen. */
    private boolean kommissionEnthaelt(String json, String q) {
        if (json == null || !json.toLowerCase(Locale.ROOT).contains(q)) {
            return false;
        }
        try {
            JsonNode kommission = objectMapper.readTree(json).get("kommission");
            return kommission != null && kommission.isTextual() && enthaelt(kommission.asText(), q);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static boolean enthaelt(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String muster(List<String> woerter, int index) {
        return index < woerter.size() ? PositionsSuchtext.enthaeltMuster(woerter.get(index)) : null;
    }

    /** „Flachstahl 50x5 · S235JR · Charge 123456 · 12 Stück“ – Doppeltes aus der Bezeichnung entfällt. */
    static String trefferText(PositionsSuchtreffer t) {
        List<String> teile = new ArrayList<>();
        String bezeichnung = leerAlsNull(t.bezeichnung());
        if (bezeichnung != null) {
            teile.add(bezeichnung);
        }
        // Normalisiert vergleichen: "50 × 5" steht schon in "Flachstahl 50x5".
        String vergleich = bezeichnung == null ? "" : PositionsSuchtext.normalisiere(bezeichnung);
        for (String zusatz : new String[] { t.abmessung(), t.werkstoff() }) {
            String z = leerAlsNull(zusatz);
            if (z != null && !vergleich.contains(PositionsSuchtext.normalisiere(z))) {
                teile.add(z);
            }
        }
        String charge = leerAlsNull(t.charge());
        if (charge != null) {
            teile.add("Charge " + charge);
        }
        String menge = menge(t.menge(), t.mengeneinheit());
        if (menge != null) {
            teile.add(menge);
        }
        return String.join(TRENNER, teile);
    }

    private static String menge(BigDecimal menge, String einheit) {
        if (menge == null) {
            return null;
        }
        String zahl = menge.stripTrailingZeros().toPlainString().replace('.', ',');
        String e = leerAlsNull(einheit);
        return e == null ? zahl : zahl + " " + e;
    }

    private static String leerAlsNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
