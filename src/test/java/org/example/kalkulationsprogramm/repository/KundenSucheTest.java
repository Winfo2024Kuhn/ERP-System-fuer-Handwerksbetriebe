package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import jakarta.persistence.EntityManager;

/**
 * Kundenliste mit Filtern (H2). Gegen echtes PostgreSQL prueft das
 * {@link DatenbankKompatibilitaetSucheTest}; hier fallen Rueckfaelle schon im
 * normalen Testlauf auf - etwa ein wieder eingebautes DISTINCT oder doppelte Zeilen.
 */
@DataJpaTest
class KundenSucheTest {

    @Autowired
    KundeRepository kunden;
    @Autowired
    EntityManager em;

    @BeforeEach
    void anlegen() {
        Kunde max = kunde("Max Mustermann", "K-1", "Würzburg", "max@example.com", "rechnung@example.com");
        Kunde erika = kunde("Erika Musterfrau", "K-2", "Würzburg", "erika@example.com");
        em.persist(max);
        em.persist(erika);
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Carport Mustermann");
        projekt.setAuftragsnummer("A-1");
        projekt.setAnlegedatum(LocalDate.of(2026, 3, 15));
        projekt.setBruttoPreis(BigDecimal.TEN);
        projekt.setBezahlt(false);
        projekt.setAbgeschlossen(false);
        projekt.setProjektArt(ProjektArt.values()[0]);
        projekt.setKundenId(max);
        em.persist(projekt);
        em.flush();
        em.clear();
    }

    private List<String> suche(String freitext, String name, String nummer, String ort, String email, String typ) {
        return kunden.findAll(KundenSuche.spezifikation(freitext, name, nummer, ort, email, typ),
                PageRequest.of(0, 50, KundenSuche.SORTIERUNG)).map(Kunde::getName).getContent();
    }

    @Test
    @DisplayName("E-Mail-Filter: zwei passende Adressen beim selben Kunden ergeben eine Zeile")
    void emailOhneDoppelteZeilen() {
        assertThat(suche(null, null, null, null, "@EXAMPLE.com", null))
                .containsExactly("Erika Musterfrau", "Max Mustermann");
    }

    @Test
    @DisplayName("Typ-Filter: Kunde hat ein Projekt, Anfrager noch keins")
    void typ() {
        assertThat(suche(null, null, null, null, null, "KUNDE")).containsExactly("Max Mustermann");
        assertThat(suche(null, null, null, null, null, "anfrager")).containsExactly("Erika Musterfrau");
        assertThat(suche(null, null, null, null, null, "unbekannt")).hasSize(2);
    }

    @Test
    @DisplayName("Freitext und Felder ohne Gross-/Kleinschreibung, alle Filter zusammen")
    void alleFilter() {
        assertThat(suche("MUSTERMANN", null, null, null, null, null)).containsExactly("Max Mustermann");
        assertThat(suche("muster", "max", "k-1", "WÜRZBURG", "rechnung@", "KUNDE")).containsExactly("Max Mustermann");
    }

    @Test
    @DisplayName("Freitext findet auch E-Mail-Adressen, ohne doppelte Zeilen")
    void freitextFindetEmail() {
        assertThat(suche("RECHNUNG@example", null, null, null, null, null)).containsExactly("Max Mustermann");
        assertThat(suche("@example.com", null, null, null, null, null))
                .containsExactly("Erika Musterfrau", "Max Mustermann");
    }

    private static Kunde kunde(String name, String nummer, String ort, String... emails) {
        Kunde kunde = new Kunde();
        kunde.setName(name);
        kunde.setKundennummer(nummer);
        kunde.setOrt(ort);
        kunde.setKundenEmails(new ArrayList<>(List.of(emails)));
        return kunde;
    }
}
