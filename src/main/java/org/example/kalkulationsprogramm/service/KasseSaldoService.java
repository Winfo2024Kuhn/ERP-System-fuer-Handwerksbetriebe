package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Zentrale Berechnung des Bar-Saldos.
 *
 * Saldo = Summe aller VALIDIERTEN Bar-Bewegungen:
 *   + KASSE_EINNAHME
 *   + PRIVATEINLAGE
 *   - KASSE_AUSGABE
 *   - PRIVATENTNAHME
 *
 * Der Service ist die einzige Stelle, an der die Bar-Saldo-Mathematik
 * passiert — andere Services (BelegService, KasseShortcutService, Scheduler)
 * leiten ihre Pruefungen hier durch, damit es keine zwei Wahrheiten gibt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KasseSaldoService {

    private static final List<BelegKategorie> BAR_KATEGORIEN = List.of(
            BelegKategorie.KASSE_EINNAHME,
            BelegKategorie.KASSE_AUSGABE,
            BelegKategorie.PRIVATENTNAHME,
            BelegKategorie.PRIVATEINLAGE);

    private final BelegRepository belegRepository;
    private final KasseEinstellungRepository kasseEinstellungRepository;

    /**
     * Aktueller Bar-Saldo aller bereits validierten Bewegungen.
     */
    @Transactional(readOnly = true)
    public BigDecimal berechneAktuellenSaldo() {
        List<Beleg> belege = belegRepository.findValidierteByKategorien(
                BelegStatus.VALIDIERT, BAR_KATEGORIEN);
        BigDecimal saldo = BigDecimal.ZERO;
        for (Beleg b : belege) {
            saldo = saldo.add(signedBetrag(b.getBelegKategorie(), b.getBetragBrutto()));
        }
        return saldo.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Bar-Saldo zu einem Stichtag: alle validierten Bar-Bewegungen mit
     * Belegdatum bis einschliesslich {@code stichtag}.
     *
     * <p>Wird vom Kassensturz gebraucht ("was muesste laut Kassenbuch heute
     * Abend in der Lade liegen") und vom Monatsabschluss fuer Anfangs- und
     * Endbestand. Belege ohne Datum bleiben draussen -- sie lassen sich
     * keinem Stichtag zuordnen und werden vor dem Abschluss ohnehin
     * eingefordert.</p>
     *
     * @param stichtag Tag einschliesslich; {@code null} bedeutet "alles"
     */
    @Transactional(readOnly = true)
    public BigDecimal berechneSaldoBis(java.time.LocalDate stichtag) {
        List<Beleg> belege = belegRepository.findValidierteByKategorien(
                BelegStatus.VALIDIERT, BAR_KATEGORIEN);
        BigDecimal saldo = BigDecimal.ZERO;
        for (Beleg b : belege) {
            if (stichtag != null) {
                if (b.getBelegDatum() == null || b.getBelegDatum().isAfter(stichtag)) continue;
            }
            saldo = saldo.add(signedBetrag(b.getBelegKategorie(), b.getBetragBrutto()));
        }
        return saldo.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Saldo nach einer geplanten Aenderung. Der "alte" Anteil wird abgezogen
     * (bei Update; bei Neuanlage null/null uebergeben), der "neue" wird addiert.
     *
     * @param kategorieAlt  Kategorie der Vorgaenger-Version (oder null bei Neuanlage)
     * @param bruttoAlt     Brutto-Betrag der Vorgaenger-Version (oder null)
     * @param kategorieNeu  Kategorie nach der Aenderung
     * @param bruttoNeu     Brutto-Betrag nach der Aenderung
     */
    @Transactional(readOnly = true)
    public BigDecimal projiziereSaldo(BelegKategorie kategorieAlt, BigDecimal bruttoAlt,
                                       BelegKategorie kategorieNeu, BigDecimal bruttoNeu) {
        BigDecimal saldo = berechneAktuellenSaldo();
        if (kategorieAlt != null && bruttoAlt != null && kategorieAlt.istKassenBewegung()) {
            saldo = saldo.subtract(signedBetrag(kategorieAlt, bruttoAlt));
        }
        if (kategorieNeu != null && bruttoNeu != null && kategorieNeu.istKassenBewegung()) {
            saldo = saldo.add(signedBetrag(kategorieNeu, bruttoNeu));
        }
        return saldo.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Liefert den konfigurierten Kassen-Mindestbestand (default 0).
     */
    @Transactional(readOnly = true)
    public BigDecimal getMindestbestand() {
        return kasseEinstellungRepository.findSingleton()
                .map(KasseEinstellung::getMindestbestand)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Wirft KasseUnterdeckungException, wenn der projizierte Saldo unter dem
     * konfigurierten Mindestbestand liegen wuerde. Die Validierung greift NUR
     * fuer validierte Bewegungen — Belege im Status NEU duerfen vorlaeufig
     * "negativ" sein, weil sie noch nicht ins Kassenbuch zaehlen.
     */
    public void assertSaldoMindestensMindestbestand(BigDecimal projizierterSaldo) {
        BigDecimal min = getMindestbestand();
        if (projizierterSaldo.compareTo(min) < 0) {
            throw new KasseUnterdeckungException(projizierterSaldo, min);
        }
    }

    private static BigDecimal signedBetrag(BelegKategorie kategorie, BigDecimal brutto) {
        if (brutto == null) {
            return BigDecimal.ZERO;
        }
        return kategorie.istAusgang() ? brutto.negate() : brutto;
    }
}
