package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert das Persistenz-Verhalten von {@link LangzeitkrankmeldungRepository}
 * gegen H2 -- insbesondere die Ueberlappungs- und Zeitraum-Abfragen, die
 * spaeter {@code TagesSollService} (Task 3+) im Speicher auswerten.
 *
 * <p>Flyway ist im Testprofil deaktiviert (siehe
 * src/test/resources/application.properties), das Schema entsteht hier ueber
 * ddl-auto=create-drop direkt aus den Entity-Annotationen -- die
 * V367-Migration selbst prueft {@link org.example.kalkulationsprogramm.db.V367SchemaTest}.
 */
@DataJpaTest
class LangzeitkrankmeldungRepositoryTest {

    @Autowired
    private LangzeitkrankmeldungRepository repository;

    @Autowired
    private LangzeitkrankmeldungPhaseRepository phaseRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Test
    @DisplayName("Meldung mit zwei Phasen wird gespeichert und mit Phasen wieder geladen")
    void meldungMitZweiPhasen_wirdGespeichertUndGeladen() {
        Mitarbeiter mitarbeiter = neuerMitarbeiter();

        Langzeitkrankmeldung meldung = neueMeldung(mitarbeiter, LocalDate.of(2026, 1, 5), null);
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 15), null));
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.KRANKENGELD,
                LocalDate.of(2026, 2, 16), null, null));
        Langzeitkrankmeldung gespeichert = repository.saveAndFlush(meldung);

        Optional<Langzeitkrankmeldung> geladen = repository.findMitPhasenById(gespeichert.getId());

        assertThat(geladen).isPresent();
        assertThat(geladen.get().getMitarbeiter().getNachname()).isEqualTo("Mustermann");
        assertThat(geladen.get().getPhasen()).hasSize(2);
        assertThat(geladen.get().getPhasen())
                .extracting(LangzeitkrankmeldungPhase::getTyp)
                .containsExactlyInAnyOrder(LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG,
                        LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
    }

    @Test
    @DisplayName("findUeberlappende findet eine offene Meldung ohne Ende")
    void findUeberlappende_findetOffeneMeldung() {
        Mitarbeiter mitarbeiter = neuerMitarbeiter();
        repository.saveAndFlush(neueMeldung(mitarbeiter, LocalDate.of(2026, 3, 1), null));

        List<Langzeitkrankmeldung> gefunden = repository.findUeberlappende(
                mitarbeiter.getId(), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20));

        assertThat(gefunden).hasSize(1);
    }

    @Test
    @DisplayName("findUeberlappende ignoriert ABGEBROCHEN-Meldungen")
    void findUeberlappende_ignoriertAbgebrocheneMeldung() {
        Mitarbeiter mitarbeiter = neuerMitarbeiter();
        Langzeitkrankmeldung abgebrochen = neueMeldung(mitarbeiter, LocalDate.of(2026, 3, 1), null);
        abgebrochen.setStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN);
        repository.saveAndFlush(abgebrochen);

        List<Langzeitkrankmeldung> gefunden = repository.findUeberlappende(
                mitarbeiter.getId(), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20));

        assertThat(gefunden).isEmpty();
    }

    @Test
    @DisplayName("findImZeitraum liefert nur die Phasen, die den Zeitraum beruehren")
    void findImZeitraum_liefertNurBeruehrtePhasen() {
        Mitarbeiter mitarbeiter = neuerMitarbeiter();
        Langzeitkrankmeldung meldung = neueMeldung(mitarbeiter, LocalDate.of(2026, 1, 5), null);
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 15), null));
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), new BigDecimal("4.00")));
        repository.saveAndFlush(meldung);

        List<LangzeitkrankmeldungPhase> gefunden = phaseRepository.findImZeitraum(
                mitarbeiter.getId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 20));

        assertThat(gefunden).hasSize(1);
        assertThat(gefunden.get(0).getTyp()).isEqualTo(LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG);
    }

    private Mitarbeiter neuerMitarbeiter() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        return mitarbeiterRepository.saveAndFlush(mitarbeiter);
    }

    private Langzeitkrankmeldung neueMeldung(Mitarbeiter mitarbeiter, LocalDate beginn, LocalDate ende) {
        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(beginn);
        meldung.setEnde(ende);
        meldung.setLohnfortzahlungBis(beginn.plusWeeks(6));
        return meldung;
    }

    private LangzeitkrankmeldungPhase neuePhase(Langzeitkrankmeldung meldung, LangzeitkrankmeldungPhaseTyp typ,
            LocalDate von, LocalDate bis, BigDecimal stundenProTag) {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setLangzeitkrankmeldung(meldung);
        phase.setTyp(typ);
        phase.setVonDatum(von);
        phase.setBisDatum(bis);
        phase.setStundenProTag(stundenProTag);
        return phase;
    }
}
