package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.BelegPosition;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegPositionRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aufteilungs-Logik fuer Belege, die nur teilweise zur Firma gehoeren
 * (Mischbon mit privatem Einkauf + Bueromaterial etc.).
 *
 * Ablauf:
 *  1) {@link BelegKiAnalyseService} ruft {@link #speicherePositionen} auf,
 *     sobald die KI die einzelnen Positionen extrahiert hat.
 *  2) Mobile-Client lhost die Positionen, der Nutzer hakt im UI die geschaeftlichen
 *     an und sendet die Liste an {@link #aktualisiereAuswahl(Long, Set)}.
 *  3) Der Service summiert die angehakten Positionen, leitet bei Bedarf netto
 *     aus brutto + Satz ab (via {@link MwstRechnerService}) und persistiert die
 *     Ergebnisse in {@code beleg.betragFirmaNetto/Brutto/Mwst}.
 *
 * Die Original-Beleg-Summen ({@code betragBrutto}/{@code betragNetto}) bleiben
 * unveraendert — GoBD-Pflicht, der Originalbeleg muss exakt so dokumentiert
 * sein wie auf dem Bon abgedruckt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegSplitService {

    private final BelegRepository belegRepository;
    private final BelegPositionRepository belegPositionRepository;
    private final MwstRechnerService mwstRechnerService;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private final KassenbuchSchreibschutz schreibschutz;

    @Transactional(readOnly = true)
    public List<BelegPosition> ladePositionen(Long belegId) {
        return belegPositionRepository.findByBelegIdOrderBySortierungAsc(belegId);
    }

    /**
     * Persistiert die von der KI extrahierten Positionen am Beleg.
     * Bei einem Re-Analyse-Lauf werden vorhandene Positionen ersetzt, damit
     * der Nutzer auf dem neuesten Stand arbeitet — bewusst akzeptiert, dass
     * dabei die alte Checkbox-Auswahl verloren geht (selten und besser als
     * inkonsistente Daten).
     */
    @Transactional
    public void speicherePositionen(Beleg beleg, List<BelegPosition> neue) {
        if (beleg == null || beleg.getId() == null) {
            return;
        }
        belegPositionRepository.deleteByBelegId(beleg.getId());
        if (neue == null || neue.isEmpty()) {
            recomputeFirmaSummen(beleg, List.of());
            return;
        }
        int idx = 0;
        for (BelegPosition p : neue) {
            p.setBeleg(beleg);
            if (p.getSortierung() <= 0) {
                p.setSortierung(idx);
            }
            idx++;
        }
        List<BelegPosition> persistiert = belegPositionRepository.saveAll(neue);
        recomputeFirmaSummen(beleg, persistiert);
    }

    /**
     * Setzt {@code istFuerFirma} fuer alle Positionen des Belegs entsprechend
     * der vom Nutzer gesendeten ID-Liste. Positionen, deren ID NICHT in der
     * Liste steht, werden auf false gesetzt — die Liste ist die vollstaendige
     * Ist-Auswahl, kein Delta.
     */
    @Transactional
    public Beleg aktualisiereAuswahl(Long belegId, Set<Long> firmaPositionIds) {
        Beleg beleg = belegRepository.findById(belegId).orElseThrow(
                () -> new IllegalArgumentException("Beleg nicht gefunden: " + belegId));
        // Die Auswahl verschiebt betragFirmaNetto/Brutto/Mwst und damit den
        // Betrag, der buchhalterisch auf die Firma laeuft. An einem
        // festgeschriebenen Beleg ist das eine stille Aenderung an einer
        // abgeschlossenen Buchung -- also gesperrt.
        schreibschutz.assertNichtFestgeschrieben(beleg, "die Aufteilung Firma/Privat");
        if (beleg.getAufteilungsModus() != BelegAufteilungsModus.TEILWEISE) {
            throw new IllegalArgumentException(
                    "Beleg ist nicht auf TEILWEISE gestellt — Positions-Auswahl nicht erlaubt");
        }
        Set<Long> auswahl = firmaPositionIds == null ? Set.of() : firmaPositionIds;
        List<BelegPosition> positionen = belegPositionRepository.findByBelegIdOrderBySortierungAsc(belegId);
        for (BelegPosition p : positionen) {
            p.setIstFuerFirma(auswahl.contains(p.getId()));
        }
        // Dirty-Checking innerhalb derselben Transaktion uebernimmt die Persistenz
        // der ist_fuer_firma-Flags — ein expliziter Save-Loop ist nicht noetig.
        recomputeFirmaSummen(beleg, positionen);
        return belegRepository.save(beleg);
    }

    /**
     * Public-API-Variante (Re-Analyse durch Buchhalter via PC-Validate). Laedt
     * die aktuelle Positions-Liste nach. Wird typischerweise nur aufgerufen,
     * wenn der Buchhalter den aufteilungsModus am PC umschaltet — Service kennt
     * keine Positionen-Referenz mehr.
     */
    public void recomputeFirmaSummen(Beleg beleg) {
        if (beleg == null) return;
        List<BelegPosition> positionen = beleg.getId() != null
                ? belegPositionRepository.findByBelegIdOrderBySortierungAsc(beleg.getId())
                : List.of();
        recomputeFirmaSummen(beleg, positionen);
    }

    /**
     * Rechnet die {@code betragFirma*}-Summen am Beleg neu durch. Erwartet die
     * Positionen bereits geladen — Aufrufer haben sie meist schon in der Hand
     * und sparen sich so eine zusaetzliche DB-Query.
     *
     * Bei VOLLSTAENDIG werden die Firma-Felder geleert; die Buchhaltung liest
     * dann wieder die Standard-Belegsummen.
     */
    void recomputeFirmaSummen(Beleg beleg, List<BelegPosition> positionen) {
        if (beleg == null) return;
        if (beleg.getAufteilungsModus() != BelegAufteilungsModus.TEILWEISE) {
            beleg.setBetragFirmaNetto(null);
            beleg.setBetragFirmaBrutto(null);
            beleg.setBetragFirmaMwst(null);
            aktualisiereProzentAnteile(beleg);
            return;
        }

        List<BelegPosition> firmaPositionen = positionen.stream()
                .filter(BelegPosition::isIstFuerFirma).toList();

        BigDecimal netto = BigDecimal.ZERO;
        BigDecimal brutto = BigDecimal.ZERO;
        // MwSt-Summen je Satz, damit gemischte Saetze korrekt aufgeschluesselt bleiben.
        Map<BigDecimal, BigDecimal> mwstJeSatz = new HashMap<>();

        for (BelegPosition p : firmaPositionen) {
            BigDecimal pBrutto = p.getBetragBrutto();
            BigDecimal pNetto = p.getBetragNetto();
            BigDecimal satz = p.getMwstSatz();

            // Ableitung netto <-> brutto wenn nur eines vorhanden + Satz da ist.
            if (pBrutto == null && pNetto != null && satz != null) {
                pBrutto = mwstRechnerService.bruttoAusNetto(pNetto, satz);
            }
            if (pNetto == null && pBrutto != null && satz != null) {
                pNetto = mwstRechnerService.nettoAusBrutto(pBrutto, satz);
            }
            // Fallback: kein Satz bekannt -> netto = brutto (steuerfrei oder unbekannt)
            if (pNetto == null && pBrutto != null) {
                pNetto = pBrutto;
            }
            if (pBrutto == null && pNetto != null) {
                pBrutto = pNetto;
            }
            if (pNetto == null || pBrutto == null) {
                continue;
            }

            netto = netto.add(pNetto);
            brutto = brutto.add(pBrutto);

            BigDecimal mwstPos = pBrutto.subtract(pNetto);
            BigDecimal key = satz != null ? satz : BigDecimal.ZERO;
            mwstJeSatz.merge(key, mwstPos, BigDecimal::add);
        }

        BigDecimal mwstSumme = BigDecimal.ZERO;
        for (BigDecimal v : mwstJeSatz.values()) {
            mwstSumme = mwstSumme.add(v);
        }

        beleg.setBetragFirmaNetto(netto.setScale(2, RoundingMode.HALF_UP));
        beleg.setBetragFirmaBrutto(brutto.setScale(2, RoundingMode.HALF_UP));
        beleg.setBetragFirmaMwst(mwstSumme.setScale(2, RoundingMode.HALF_UP));
        aktualisiereProzentAnteile(beleg);
    }

    /**
     * Rechnet die prozentualen Kostenstellen-Anteile des Belegs auf die soeben
     * geaenderte Bemessungsgrundlage um.
     *
     * Noetig, seit die Anteile auf dem Firmenanteil beruhen statt auf dem festen
     * Originalbetrag: haekelt der Mitarbeiter am Handy eine Position an oder ab,
     * aendert sich der Firmenanteil — die gespeicherten Anteilsbetraege waeren
     * sonst veraltet, und Rest plus Anteile ergaeben nicht mehr den
     * Firmenanteil (im Stundensatz-Rechner sichtbar als zu hohe Gemeinkosten).
     *
     * Absolute Betraege bleiben unangetastet: die hat der Nutzer bewusst
     * eingetippt und sie sollen sich nicht hinter seinem Ruecken aendern.
     */
    private void aktualisiereProzentAnteile(Beleg beleg) {
        if (beleg.getId() == null) {
            return;
        }
        List<BelegKostenstellenAnteil> anteile =
                belegKostenstellenAnteilRepository.findByBelegId(beleg.getId());
        if (anteile.isEmpty()) {
            return;
        }
        List<BelegKostenstellenAnteil> geaendert = new ArrayList<>();
        for (BelegKostenstellenAnteil a : anteile) {
            if (a.getProzent() == null) {
                continue;
            }
            a.berechneAnteil(beleg.getBuchungsbetragNetto(), beleg.getBuchungsbetragBrutto());
            geaendert.add(a);
        }
        if (!geaendert.isEmpty()) {
            belegKostenstellenAnteilRepository.saveAll(geaendert);
        }
    }
}
