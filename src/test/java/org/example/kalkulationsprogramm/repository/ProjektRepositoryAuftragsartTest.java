package org.example.kalkulationsprogramm.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Sichert ab, dass die Sammel-Abfrage für die Projektliste dasselbe Ergebnis liefert
 * wie die alte Einzelabfrage pro Projekt: Ein Projekt gilt als Folgeauftrag, wenn der
 * Kunde ein früheres Projekt hat (früheres Datum oder gleiches Datum mit kleinerer ID).
 */
@DataJpaTest
class ProjektRepositoryAuftragsartTest {

    @Autowired
    private ProjektRepository projektRepository;

    @Autowired
    private KundeRepository kundeRepository;

    private Kunde kunde(String name, String kundennummer) {
        Kunde kunde = new Kunde();
        kunde.setName(name);
        kunde.setKundennummer(kundennummer);
        return kundeRepository.save(kunde);
    }

    private Projekt projekt(Kunde kunde, String auftragsnummer, LocalDate anlegedatum) {
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau " + auftragsnummer);
        projekt.setKundenId(kunde);
        projekt.setAuftragsnummer(auftragsnummer);
        projekt.setAnlegedatum(anlegedatum);
        projekt.setBruttoPreis(BigDecimal.ZERO);
        projekt.setBezahlt(false);
        return projektRepository.saveAndFlush(projekt);
    }

    @Test
    void findetNurProjekteMitFrueheremProjektDesselbenKunden() {
        Kunde stammkunde = kunde("Max Mustermann", "KN-1");
        Kunde neukunde = kunde("Erika Musterfrau", "KN-2");

        Projekt erstauftrag = projekt(stammkunde, "A-1", LocalDate.of(2026, 1, 10));
        Projekt folgeauftrag = projekt(stammkunde, "A-2", LocalDate.of(2026, 5, 20));
        Projekt einzelauftrag = projekt(neukunde, "A-3", LocalDate.of(2026, 6, 1));

        List<Long> alle = List.of(erstauftrag.getId(), folgeauftrag.getId(), einzelauftrag.getId());

        assertThat(projektRepository.findIdsMitVorherigemProjektFuerKunde(alle))
                .containsExactly(folgeauftrag.getId());
    }

    @Test
    void wertetBeiGleichemDatumDieKleinereIdAlsFruehersAus() {
        Kunde stammkunde = kunde("Max Mustermann", "KN-3");
        LocalDate gleicherTag = LocalDate.of(2026, 3, 15);

        Projekt zuerstAngelegt = projekt(stammkunde, "B-1", gleicherTag);
        Projekt danachAngelegt = projekt(stammkunde, "B-2", gleicherTag);

        assertThat(projektRepository.findIdsMitVorherigemProjektFuerKunde(
                List.of(zuerstAngelegt.getId(), danachAngelegt.getId())))
                .containsExactly(danachAngelegt.getId());
    }

    @Test
    void liefertKeinenTrefferFuerProjekteOhneKunden() {
        Projekt ohneKunde = new Projekt();
        ohneKunde.setBauvorhaben("Altprojekt ohne Kunde");
        ohneKunde.setAuftragsnummer("C-1");
        ohneKunde.setAnlegedatum(LocalDate.of(2026, 2, 2));
        ohneKunde.setBruttoPreis(BigDecimal.ZERO);
        ohneKunde.setBezahlt(false);
        ohneKunde = projektRepository.saveAndFlush(ohneKunde);

        assertThat(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(ohneKunde.getId())))
                .isEmpty();
    }

    @Test
    void liefertDasSelbeErgebnisWieDieEinzelabfrage() {
        Kunde stammkunde = kunde("Max Mustermann", "KN-4");
        Projekt erstauftrag = projekt(stammkunde, "D-1", LocalDate.of(2026, 1, 5));
        Projekt folgeauftrag = projekt(stammkunde, "D-2", LocalDate.of(2026, 4, 5));

        List<Long> sammelErgebnis = projektRepository.findIdsMitVorherigemProjektFuerKunde(
                List.of(erstauftrag.getId(), folgeauftrag.getId()));

        assertThat(sammelErgebnis.contains(erstauftrag.getId()))
                .isEqualTo(projektRepository.existsVorherigesProjektFuerKunde(
                        stammkunde.getId(), erstauftrag.getAnlegedatum(), erstauftrag.getId()));
        assertThat(sammelErgebnis.contains(folgeauftrag.getId()))
                .isEqualTo(projektRepository.existsVorherigesProjektFuerKunde(
                        stammkunde.getId(), folgeauftrag.getAnlegedatum(), folgeauftrag.getId()));
    }

    @Test
    void liefertNurNichtBeendeteProjekteFuerDieListe() {
        Kunde stammkunde = kunde("Max Mustermann", "KN-5");
        Projekt offen = projekt(stammkunde, "E-1", LocalDate.of(2026, 7, 1));
        Projekt beendet = projekt(stammkunde, "E-2", LocalDate.of(2026, 7, 2));
        beendet.setAbgeschlossen(true);
        projektRepository.saveAndFlush(beendet);

        assertThat(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc())
                .extracting(Projekt::getId)
                .contains(offen.getId())
                .doesNotContain(beendet.getId());
    }
}
