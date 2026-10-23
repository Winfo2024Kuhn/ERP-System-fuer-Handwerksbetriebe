package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Sichtbarkeit im Termin-Kalender auf Datenbank-Ebene (Issue #267). Dummy-Daten: Max Mustermann. */
@DataJpaTest
class KalenderEintragRepositoryTest {

    @Autowired private KalenderEintragRepository repository;
    @Autowired private MitarbeiterRepository mitarbeiterRepository;

    private static final LocalDate TAG = LocalDate.of(2026, 10, 20);

    @Test
    void firmenterminUndMitarbeiterSicht() {
        Mitarbeiter max = mitarbeiter("Max", "Mustermann");
        Mitarbeiter erika = mitarbeiter("Erika", "Musterfrau");

        KalenderEintrag firma = repository.save(termin("Betriebsausflug", null, Set.of(), TAG));
        KalenderEintrag eingeladen = repository.save(termin("Aufmaß", erika, Set.of(max), TAG));
        KalenderEintrag privatErika = repository.save(termin("Privat", erika, Set.of(), TAG));
        KalenderEintrag ohneErstellerMitTeilnehmer = repository.save(termin("Baustelle", null, Set.of(erika), TAG));
        repository.save(termin("Außerhalb", null, Set.of(), TAG.plusMonths(2)));
        repository.flush();

        assertThat(repository.findFirmenEintraegeBetween(TAG.minusDays(1), TAG.plusDays(1)))
                .extracting(KalenderEintrag::getId)
                .containsExactly(firma.getId());

        assertThat(repository.findByMitarbeiterAndDatumBetween(max.getId(), TAG.minusDays(1), TAG.plusDays(1)))
                .extracting(KalenderEintrag::getId)
                .containsExactlyInAnyOrder(firma.getId(), eingeladen.getId())
                .doesNotContain(privatErika.getId(), ohneErstellerMitTeilnehmer.getId());
    }

    private Mitarbeiter mitarbeiter(String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setAktiv(true);
        return mitarbeiterRepository.save(m);
    }

    private static KalenderEintrag termin(String titel, Mitarbeiter ersteller, Set<Mitarbeiter> teilnehmer, LocalDate datum) {
        KalenderEintrag e = new KalenderEintrag();
        e.setTitel(titel);
        e.setDatum(datum);
        e.setErsteller(ersteller);
        e.setTeilnehmer(new HashSet<>(teilnehmer));
        return e;
    }
}
