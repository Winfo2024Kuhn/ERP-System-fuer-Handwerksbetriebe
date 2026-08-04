package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert die Query-Semantik von
 * {@link BelegKostenstellenAnteilRepository#findAktiveFixkostenAnteileImJahr(int)}
 * gegen H2. Wichtig: die Query muss die exakt selbe Logik wie
 * {@link BelegKostenstellenAnteil#isStreckungAktivFuerJahr(int)} abbilden — der
 * Verrechnungslohn-Rechner haengt davon ab, dass ein nicht-gestreckter
 * 2024er Bar-Beleg in 2025+ NICHT erneut im Gemeinkostentopf landet.
 */
@DataJpaTest
class BelegKostenstellenAnteilRepositoryTest {

    @Autowired private BelegKostenstellenAnteilRepository repo;
    @Autowired private BelegRepository belegRepository;
    @Autowired private KostenstelleRepository kostenstelleRepository;

    @Test
    @DisplayName("Nicht gestreckter Anteil aus 2024 ist NUR in 2024 aktiv, nicht in 2025")
    void nichtGestreckt_nurStartJahr() {
        Kostenstelle ks = saveKostenstelle("Werkstatt", true);
        Beleg b = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(b, ks, 100, 1, 2024);

        assertThat(repo.findAktiveFixkostenAnteileImJahr(2024)).hasSize(1);
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2025)).isEmpty();
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2023)).isEmpty();
    }

    @Test
    @DisplayName("Gestreckter Anteil (4 Jahre ab 2024) ist 2024-2027 aktiv, 2028 nicht mehr")
    void gestreckt_RangeMatching() {
        Kostenstelle ks = saveKostenstelle("Zertifizierung", true);
        Beleg b = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(b, ks, 100, 4, 2024);

        assertThat(repo.findAktiveFixkostenAnteileImJahr(2024)).hasSize(1);
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2025)).hasSize(1);
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2026)).hasSize(1);
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2027)).hasSize(1);
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2028)).isEmpty();
        assertThat(repo.findAktiveFixkostenAnteileImJahr(2023)).isEmpty();
    }

    @Test
    @DisplayName("NEU-Beleg wird ausgeschlossen — nur VALIDIERT fliesst in Gemeinkosten")
    void neuBeleg_nichtEnthalten() {
        Kostenstelle ks = saveKostenstelle("Buero", true);
        Beleg b = saveBeleg(BelegStatus.NEU);
        saveAnteil(b, ks, 100, 1, 2024);

        assertThat(repo.findAktiveFixkostenAnteileImJahr(2024)).isEmpty();
    }

    @Test
    @DisplayName("Nicht-Fixkosten-Kostenstelle wird ausgeschlossen")
    void nichtFixkosten_nichtEnthalten() {
        Kostenstelle ks = saveKostenstelle("Projekt-Kostenstelle", false);
        Beleg b = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(b, ks, 100, 1, 2024);

        assertThat(repo.findAktiveFixkostenAnteileImJahr(2024)).isEmpty();
    }

    @Test
    @DisplayName("findByBelegId + deleteByBelegId Sanity")
    void findUndDelete() {
        Kostenstelle ks = saveKostenstelle("Lager", true);
        Beleg b = saveBeleg(BelegStatus.NEU);
        saveAnteil(b, ks, 50, 1, 2024);
        saveAnteil(b, ks, 50, 1, 2024);

        assertThat(repo.findByBelegId(b.getId())).hasSize(2);

        repo.deleteByBelegId(b.getId());
        repo.flush();
        assertThat(repo.findByBelegId(b.getId())).isEmpty();
    }

    @Test
    @DisplayName("summiereAufgeteilteBetraegeProBeleg summiert je Beleg, nicht je Anteil")
    void summiereAufgeteilteBetraege() {
        Kostenstelle ks = saveKostenstelle("Werkstatt", true);
        Beleg mitZweiAnteilen = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(mitZweiAnteilen, ks, 30, 1, 2024);
        saveAnteil(mitZweiAnteilen, ks, 30, 1, 2024);
        Beleg mitEinemAnteil = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(mitEinemAnteil, ks, 100, 1, 2024);

        Map<Long, BigDecimal> proBeleg = new HashMap<>();
        for (Object[] zeile : repo.summiereAufgeteilteBetraegeProBeleg()) {
            proBeleg.put((Long) zeile[0], (BigDecimal) zeile[1]);
        }

        // 30% + 30% von 100 EUR
        assertThat(proBeleg.get(mitZweiAnteilen.getId())).isEqualByComparingTo("60.00");
        assertThat(proBeleg.get(mitEinemAnteil.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Die volle Summe wird geliefert, auch wenn der Anteil gestreckt ist")
    void summeIgnoriertStreckung() {
        // Wichtig fuer den Verrechnungslohn: abgezogen wird der voll zugeordnete
        // Betrag, nicht der Jahresanteil -- sonst bliebe im Belegjahr ein zu
        // grosser Rest auf der direkten Kostenstelle stehen.
        Kostenstelle ks = saveKostenstelle("Zertifizierung", true);
        Beleg b = saveBeleg(BelegStatus.VALIDIERT);
        saveAnteil(b, ks, 60, 3, 2024);

        Object[] zeile = repo.summiereAufgeteilteBetraegeProBeleg().get(0);
        assertThat((BigDecimal) zeile[1]).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Beleg ohne Anteile taucht in der Summen-Abfrage gar nicht auf")
    void belegOhneAnteileFehltInDerSumme() {
        saveBeleg(BelegStatus.VALIDIERT);

        assertThat(repo.summiereAufgeteilteBetraegeProBeleg()).isEmpty();
    }

    private Kostenstelle saveKostenstelle(String name, boolean istFixkosten) {
        Kostenstelle ks = new Kostenstelle();
        ks.setBezeichnung(name);
        ks.setTyp(KostenstellenTyp.GEMEINKOSTEN);
        ks.setIstFixkosten(istFixkosten);
        return kostenstelleRepository.saveAndFlush(ks);
    }

    private Beleg saveBeleg(BelegStatus status) {
        Beleg b = new Beleg();
        b.setStatus(status);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
        b.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);
        b.setBelegDatum(LocalDate.of(2024, 6, 15));
        b.setUploadDatum(LocalDateTime.now());
        b.setBetragBrutto(new BigDecimal("100.00"));
        return belegRepository.saveAndFlush(b);
    }

    private void saveAnteil(Beleg beleg, Kostenstelle ks, int prozent, int streckungJahre, int startJahr) {
        BelegKostenstellenAnteil a = new BelegKostenstellenAnteil();
        a.setBeleg(beleg);
        a.setKostenstelle(ks);
        a.setProzent(prozent);
        a.setStreckungJahre(streckungJahre);
        a.setStreckungStartJahr(startJahr);
        a.berechneAnteil(beleg.getBetragBrutto(), beleg.getBetragBrutto());
        repo.saveAndFlush(a);
    }
}
