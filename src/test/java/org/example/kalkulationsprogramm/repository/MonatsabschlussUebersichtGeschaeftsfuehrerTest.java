package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Geschäftsführung erfasst Projektzeiten ohne Arbeitszeitkonto und hat
 * deshalb keinen Monatsabschluss. Sie darf in der Abschluss-Übersicht auch dann
 * nicht auftauchen, wenn im Zeitraum Zeitbuchungen vorliegen – sonst landet sie
 * über "Alle auswählen" im Sammelabschluss und schlägt dort dauerhaft fehl.
 */
@DataJpaTest(showSql = false)
class MonatsabschlussUebersichtGeschaeftsfuehrerTest {
    @Autowired MonatsabschlussUebersichtRepository repository;
    @Autowired TestEntityManager em;

    private static final LocalDate VON = LocalDate.of(2026, 8, 1);
    private static final LocalDate BIS = LocalDate.of(2026, 8, 31);

    @Test
    void geschaeftsfuehrungBleibtTrotzZeitbuchungenAusDerUebersicht() {
        Mitarbeiter angestellt = person("Max", "Mustermann", false);
        Mitarbeiter chefin = person("Erika", "Musterfrau", true);
        buchung(angestellt);
        buchung(chefin);
        em.flush();

        assertThat(ids()).containsExactly(angestellt.getId());
    }

    @Test
    void geschaeftsfuehrungVerschwindetAuchBeiDirekterIdAbfrage() {
        Mitarbeiter chefin = person("Erika", "Musterfrau", true);
        buchung(chefin);
        em.flush();

        assertThat(repository.personen(chefin.getId(), null, VON, BIS, VON.atStartOfDay(),
                BIS.atTime(23, 59, 59), ym(), ym(), PageRequest.of(0, 50))).isEmpty();
    }

    private List<Long> ids() {
        return repository.personen(null, null, VON, BIS, VON.atStartOfDay(), BIS.atTime(23, 59, 59),
                ym(), ym(), PageRequest.of(0, 50)).stream().map(MonatsabschlussUebersichtRepository.Person::getId).toList();
    }

    private int ym() {
        return VON.getYear() * 12 + VON.getMonthValue();
    }

    private Mitarbeiter person(String vorname, String nachname, boolean geschaeftsfuehrung) {
        Mitarbeiter m = new Mitarbeiter();
        m.setArt(MitarbeiterArt.MENSCH);
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setAktiv(true);
        m.setFuehrtZeitkonto(true);
        m.setIstGeschaeftsfuehrer(geschaeftsfuehrung);
        m.setEintrittsdatum(LocalDate.of(2020, 1, 1));
        return em.persist(m);
    }

    private void buchung(Mitarbeiter m) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2026, 8, 3, 8, 0));
        b.setEndeZeit(LocalDateTime.of(2026, 8, 3, 16, 0));
        em.persist(b);
    }
}
