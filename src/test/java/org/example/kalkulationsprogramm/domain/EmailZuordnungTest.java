package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests fuer die Zuordnungs-Invarianten der {@link Email}.
 *
 * <p>Fachlich: Eine Mail gehoert zu genau EINEM Vorgang (Projekt oder Anfrage).
 * Der Lieferant ist zusaetzlich moeglich, weil sich der Schriftpartner durch das
 * Umhaengen auf einen anderen Vorgang nicht aendert – die Mail soll auf der
 * Lieferanten-Karte sichtbar bleiben.
 *
 * <p>Alle Daten sind Dummy-Daten (DSGVO).
 */
@DisplayName("Email – Zuordnung")
class EmailZuordnungTest {

    private static Lieferanten lieferant(long id) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(id);
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        return lieferant;
    }

    private static Projekt projekt(long id) {
        Projekt projekt = new Projekt();
        projekt.setId(id);
        return projekt;
    }

    private static Anfrage anfrage(long id) {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(id);
        return anfrage;
    }

    @Test
    @DisplayName("Zusatz-Verknuepfung laesst die Projekt-Zuordnung unangetastet")
    void zusatzVerknuepfung_behaeltProjekt() {
        Email email = new Email();
        Projekt projekt = projekt(1L);
        email.assignToProjekt(projekt);

        email.verknuepfeLieferantZusaetzlich(lieferant(7L));

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(email.getProjekt()).isSameAs(projekt);
        assertThat(email.getLieferant()).isNotNull();
    }

    @Test
    @DisplayName("Ohne Vorgang wird der Lieferant zur regulaeren Zuordnung")
    void zusatzVerknuepfung_ohneVorgang_wirdZuordnung() {
        Email email = new Email();

        email.verknuepfeLieferantZusaetzlich(lieferant(7L));

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
        assertThat(email.getLieferant()).isNotNull();
    }

    @Test
    @DisplayName("Umhaengen auf ein anderes Projekt behaelt den Lieferanten-Verweis")
    void umhaengenAufProjekt_behaeltLieferant() {
        Email email = new Email();
        Lieferanten lieferant = lieferant(7L);
        email.assignToProjekt(projekt(1L));
        email.verknuepfeLieferantZusaetzlich(lieferant);

        Projekt anderes = projekt(2L);
        email.assignToProjekt(anderes);

        assertThat(email.getProjekt()).isSameAs(anderes);
        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Umhaengen auf eine Anfrage behaelt den Lieferanten-Verweis und loest das Projekt")
    void umhaengenAufAnfrage_behaeltLieferant() {
        Email email = new Email();
        Lieferanten lieferant = lieferant(7L);
        email.assignToProjekt(projekt(1L));
        email.verknuepfeLieferantZusaetzlich(lieferant);

        Anfrage anfrage = anfrage(3L);
        email.assignToAnfrage(anfrage);

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(email.getAnfrage()).isSameAs(anfrage);
        assertThat(email.getProjekt()).isNull();
        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Zuordnung entfernen loest auch den Lieferanten-Verweis")
    void clearAssignment_loestAlles() {
        Email email = new Email();
        email.assignToProjekt(projekt(1L));
        email.verknuepfeLieferantZusaetzlich(lieferant(7L));

        email.clearAssignment();

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.KEINE);
        assertThat(email.getProjekt()).isNull();
        assertThat(email.getLieferant()).isNull();
    }

    @Test
    @DisplayName("Direkte Lieferanten-Zuordnung bleibt exklusiv")
    void assignToLieferant_bleibtExklusiv() {
        Email email = new Email();
        email.assignToProjekt(projekt(1L));

        email.assignToLieferant(lieferant(7L));

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
        assertThat(email.getProjekt()).isNull();
        assertThat(email.getAnfrage()).isNull();
    }
}
