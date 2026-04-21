package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pflegt den gewichteten Durchschnittspreis pro Artikel.
 *
 * <p>Der Agent ({@code ArtikelMatchingToolService.updateArtikelPreis}) normiert
 * Preise auf €/kg; entsprechend ist auch die Gewichtungsgroesse kg. Formel:
 * <pre>
 *   p_neu_ges = (p_alt * m_alt + p_neu * m_neu) / (m_alt + m_neu)
 *   m_neu_ges = m_alt + m_neu
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelDurchschnittspreisService {

    private static final int PREIS_SCALE = 4;
    private static final int MENGE_SCALE = 3;

    private final ArtikelRepository artikelRepository;

    /**
     * Verrechnet einen neuen Datenpunkt (Menge/Preis aus Rechnung) in den
     * laufenden gewichteten Durchschnitt.
     * Ueberspringt ungueltige Werte still (Log-Warning), damit ein einzelner
     * Rechnungsfehler den Agenten-Flow nicht kippt.
     */
    @Transactional
    public void aktualisiere(Artikel artikel, BigDecimal mengeKg, BigDecimal preisProKg) {
        if (artikel == null) {
            log.warn("Durchschnittspreis-Update uebersprungen: artikel == null");
            return;
        }
        if (mengeKg == null || preisProKg == null) {
            log.warn("Durchschnittspreis-Update uebersprungen fuer Artikel {}: mengeKg={} preisProKg={}",
                    artikel.getId(), mengeKg, preisProKg);
            return;
        }
        if (mengeKg.signum() <= 0 || preisProKg.signum() <= 0) {
            log.warn("Durchschnittspreis-Update uebersprungen fuer Artikel {}: mengeKg={} preisProKg={} (<= 0)",
                    artikel.getId(), mengeKg, preisProKg);
            return;
        }

        BigDecimal altPreis = artikel.getDurchschnittspreisNetto();
        BigDecimal altMenge = artikel.getDurchschnittspreisMenge();
        if (altMenge == null) altMenge = BigDecimal.ZERO;

        BigDecimal neuerPreis;
        BigDecimal neueMenge;
        if (altPreis == null || altMenge.signum() == 0) {
            neuerPreis = preisProKg.setScale(PREIS_SCALE, RoundingMode.HALF_UP);
            neueMenge = mengeKg.setScale(MENGE_SCALE, RoundingMode.HALF_UP);
        } else {
            BigDecimal gewichtetAlt = altPreis.multiply(altMenge);
            BigDecimal gewichtetNeu = preisProKg.multiply(mengeKg);
            neueMenge = altMenge.add(mengeKg).setScale(MENGE_SCALE, RoundingMode.HALF_UP);
            neuerPreis = gewichtetAlt.add(gewichtetNeu)
                    .divide(altMenge.add(mengeKg), PREIS_SCALE, RoundingMode.HALF_UP);
        }

        artikel.setDurchschnittspreisNetto(neuerPreis);
        artikel.setDurchschnittspreisMenge(neueMenge);
        artikel.setDurchschnittspreisAktualisiertAm(LocalDateTime.now());
        artikelRepository.save(artikel);

        log.info("Durchschnittspreis Artikel {}: {} €/kg bei {} kg (neu: +{} kg zu {} €/kg)",
                artikel.getId(), neuerPreis, neueMenge, mengeKg, preisProKg);
    }

    /**
     * Setzt fuer alle Artikel den Durchschnittspreis initial auf den ungewichteten
     * Mittelwert der aktuellen Lieferantenpreise (Historie mit Mengen liegt nicht
     * vor). {@code durchschnittspreis_menge} bleibt bei 0, damit der naechste
     * Matching-Agent-Lauf den Wert korrekt mit-gewichtet (frische Werte ueberwiegen).
     * Ergebnis: Anzahl verarbeitet / uebersprungen.
     */
    @Transactional
    public BackfillErgebnis backfillAlle() {
        long start = System.currentTimeMillis();
        int verarbeitet = 0;
        int uebersprungen = 0;

        List<Artikel> artikel = artikelRepository.findAll();
        for (Artikel a : artikel) {
            List<LieferantenArtikelPreise> preise = a.getArtikelpreis();
            if (preise == null || preise.isEmpty()) {
                uebersprungen++;
                continue;
            }
            List<BigDecimal> gueltig = preise.stream()
                    .map(LieferantenArtikelPreise::getPreis)
                    .filter(p -> p != null && p.signum() > 0)
                    .toList();
            if (gueltig.isEmpty()) {
                uebersprungen++;
                continue;
            }
            BigDecimal summe = gueltig.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal schnitt = summe.divide(
                    BigDecimal.valueOf(gueltig.size()), PREIS_SCALE, RoundingMode.HALF_UP);
            a.setDurchschnittspreisNetto(schnitt);
            a.setDurchschnittspreisMenge(BigDecimal.ZERO);
            a.setDurchschnittspreisAktualisiertAm(LocalDateTime.now());
            artikelRepository.save(a);
            verarbeitet++;
        }

        long dauerMs = System.currentTimeMillis() - start;
        log.info("Durchschnittspreis-Backfill fertig: {} verarbeitet, {} uebersprungen, {} ms",
                verarbeitet, uebersprungen, dauerMs);
        return new BackfillErgebnis(verarbeitet, uebersprungen, dauerMs);
    }

    public record BackfillErgebnis(int verarbeitet, int uebersprungen, long dauerMs) {}
}
