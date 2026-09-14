package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MonatsabschlussSnapshotRepositoryTest {

    private static final LocalDate VON = LocalDate.of(2026, 8, 1);
    private static final LocalDate BIS = LocalDate.of(2026, 8, 31);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AbwesenheitRepository repository;

    @Test
    void aggregiertTypUndPhaseMitNullphasenUndInklusivenMonatsgrenzen() {
        Mitarbeiter mitarbeiter = mitarbeiter();
        Mitarbeiter anderer = mitarbeiter();
        abwesenheit(mitarbeiter, VON, AbwesenheitsTyp.URLAUB, "7.75", null);
        abwesenheit(mitarbeiter, BIS, AbwesenheitsTyp.URLAUB, "4.50", null);
        abwesenheit(mitarbeiter, VON.plusDays(1), AbwesenheitsTyp.KRANKHEIT, "6.25", null);
        abwesenheit(mitarbeiter, VON.plusDays(2), AbwesenheitsTyp.KRANKHEIT, "1.50", null);

        LangzeitkrankmeldungPhase lohn = phase(mitarbeiter, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG);
        LangzeitkrankmeldungPhase krankengeld = phase(mitarbeiter, LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        LangzeitkrankmeldungPhase wiedereingliederung = phase(mitarbeiter,
                LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG);
        abwesenheit(mitarbeiter, VON.plusDays(3), AbwesenheitsTyp.KRANKHEIT, "7.75", lohn);
        abwesenheit(mitarbeiter, VON.plusDays(4), AbwesenheitsTyp.KRANKHEIT, "2.25", lohn);
        abwesenheit(mitarbeiter, VON.plusDays(5), AbwesenheitsTyp.KRANKHEIT, "8.00", krankengeld);
        abwesenheit(mitarbeiter, VON.plusDays(6), AbwesenheitsTyp.KRANKHEIT, "3.50", wiedereingliederung);

        // Both filters must apply to rows with and without a phase relation.
        for (AbwesenheitsTyp typ : List.of(AbwesenheitsTyp.URLAUB, AbwesenheitsTyp.KRANKHEIT)) {
            abwesenheit(mitarbeiter, VON.minusDays(1), typ, "99.00", null);
            abwesenheit(mitarbeiter, BIS.plusDays(1), typ, "99.00", null);
            abwesenheit(anderer, VON, typ, "99.00", null);
        }
        abwesenheit(mitarbeiter, VON.minusDays(2), AbwesenheitsTyp.KRANKHEIT, "99.00", lohn);
        abwesenheit(mitarbeiter, BIS.plusDays(2), AbwesenheitsTyp.KRANKHEIT, "99.00", krankengeld);
        abwesenheit(anderer, VON.plusDays(3), AbwesenheitsTyp.KRANKHEIT, "99.00",
                phase(anderer, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG));
        entityManager.flush();
        entityManager.clear();

        List<AbwesenheitRepository.StundenNachTypUndPhase> ergebnis =
                repository.sumStundenNachTypUndPhase(mitarbeiter.getId(), VON, BIS);

        assertThat(ergebnis).hasSize(5);
        pruefeSumme(ergebnis, AbwesenheitsTyp.URLAUB, null, "12.25");
        pruefeSumme(ergebnis, AbwesenheitsTyp.KRANKHEIT, null, "7.75");
        pruefeSumme(ergebnis, AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG, "10.00");
        pruefeSumme(ergebnis, AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.KRANKENGELD, "8.00");
        pruefeSumme(ergebnis, AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG, "3.50");
    }

    @Test
    void liefertKeineGruppenWennImMonatKeineAbwesenheitenVorliegen() {
        Mitarbeiter mitarbeiter = mitarbeiter();
        abwesenheit(mitarbeiter, VON.minusDays(1), AbwesenheitsTyp.URLAUB, "8.00", null);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.sumStundenNachTypUndPhase(mitarbeiter.getId(), VON, BIS)).isEmpty();
    }

    private void pruefeSumme(List<AbwesenheitRepository.StundenNachTypUndPhase> ergebnis,
            AbwesenheitsTyp typ, LangzeitkrankmeldungPhaseTyp phaseTyp, String stunden) {
        assertThat(ergebnis).filteredOn(zeile -> zeile.getTyp() == typ && zeile.getPhaseTyp() == phaseTyp)
                .singleElement().satisfies(zeile -> assertThat(zeile.getStunden()).isEqualByComparingTo(stunden));
    }

    private Mitarbeiter mitarbeiter() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        return entityManager.persist(mitarbeiter);
    }

    private LangzeitkrankmeldungPhase phase(Mitarbeiter mitarbeiter, LangzeitkrankmeldungPhaseTyp typ) {
        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(VON.minusMonths(2));
        meldung.setLohnfortzahlungBis(VON.plusDays(4));
        entityManager.persist(meldung);
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setLangzeitkrankmeldung(meldung);
        phase.setTyp(typ);
        phase.setVonDatum(VON.minusDays(2));
        return entityManager.persist(phase);
    }

    private void abwesenheit(Mitarbeiter mitarbeiter, LocalDate datum, AbwesenheitsTyp typ,
            String stunden, LangzeitkrankmeldungPhase phase) {
        Abwesenheit abwesenheit = new Abwesenheit();
        abwesenheit.setMitarbeiter(mitarbeiter);
        abwesenheit.setDatum(datum);
        abwesenheit.setTyp(typ);
        abwesenheit.setStunden(new BigDecimal(stunden));
        abwesenheit.setLangzeitkrankmeldungPhase(phase);
        entityManager.persist(abwesenheit);
    }
}
