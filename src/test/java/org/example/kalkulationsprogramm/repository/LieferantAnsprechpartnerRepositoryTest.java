package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abfragen der Lieferanten-Ansprechpartner auf H2: Sortierung, Obergrenze,
 * gesammeltes Laden für eine Seite, Freitextsuche der Lieferantenliste und
 * Löschen samt Lieferant.
 */
@DataJpaTest
class LieferantAnsprechpartnerRepositoryTest {

    @Autowired LieferantAnsprechpartnerRepository repository;
    @Autowired LieferantenRepository lieferanten;
    @Autowired EntityManager em;

    private Lieferanten stahl;
    private Lieferanten holz;

    @BeforeEach
    void setUp() {
        stahl = lieferant("Muster Stahl KG", true);
        holz = lieferant("Holz Beispiel GmbH", false);
        person(stahl, "Max", "mustermann", "Außendienst", null, "0171 1234567");
        person(stahl, null, "Mustermann", "Zentrale", "0931 4444-0", null);
        person(stahl, "Erika", "Musterfrau", "Innendienst", "0931 4444-12", null);
        person(holz, "Moritz", "Beispiel", null, null, null);
        em.flush();
        em.clear();
    }

    private Lieferanten lieferant(String name, boolean aktiv) {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname(name);
        l.setIstAktiv(aktiv);
        return lieferanten.save(l);
    }

    private LieferantAnsprechpartner person(Lieferanten l, String vorname, String nachname, String aufgabe,
                                            String telefon, String mobil) {
        LieferantAnsprechpartner a = new LieferantAnsprechpartner();
        a.setLieferant(l);
        a.setVorname(vorname);
        a.setNachname(nachname);
        a.setAufgabe(aufgabe);
        a.setTelefon(telefon);
        a.setMobiltelefon(mobil);
        a.setEmail(vorname == null ? null : vorname.toLowerCase() + "@example.com");
        return repository.save(a);
    }

    @Test
    @DisplayName("Liste: Nachname, dann Vorname (leer zuerst), ohne Groß-/Kleinschreibung; Obergrenze greift")
    void sortierungUndObergrenze() {
        assertThat(repository.findeFuerLieferant(stahl.getId(), PageRequest.of(0, 200)))
                .extracting(a -> a.getVorname() + " " + a.getNachname())
                .containsExactly("Erika Musterfrau", "null Mustermann", "Max mustermann");
        assertThat(repository.findeFuerLieferant(stahl.getId(), PageRequest.of(0, 2))).hasSize(2);
    }

    @Test
    @DisplayName("Gesammelt für mehrere Lieferanten, je Lieferant gleich sortiert")
    void gesammelt() {
        List<LieferantAnsprechpartner> alle = repository.findeFuerLieferanten(List.of(stahl.getId(), holz.getId()));
        assertThat(alle).extracting(a -> a.getLieferant().getId())
                .containsExactly(stahl.getId(), stahl.getId(), stahl.getId(), holz.getId());
        assertThat(alle.getFirst().getNachname()).isEqualTo("Musterfrau");
    }

    @Test
    @DisplayName("Zählen, Finden nur beim eigenen Lieferanten")
    void zaehlenUndFinden() {
        assertThat(repository.countByLieferantId(stahl.getId())).isEqualTo(3);
        assertThat(repository.countByLieferantId(holz.getId())).isEqualTo(1);
        Long moritz = repository.findeFuerLieferant(holz.getId(), PageRequest.of(0, 1)).getFirst().getId();
        assertThat(repository.findByIdAndLieferantId(moritz, holz.getId())).isPresent();
        assertThat(repository.findByIdAndLieferantId(moritz, stahl.getId())).isEmpty();
    }

    @Test
    @DisplayName("Rufnummern-Verzeichnis: alle Lieferanten, nur Personen mit Nummer")
    void telefonverzeichnis() {
        assertThat(repository.findeTelefonverzeichnis())
                .extracting(z -> (String) z[5] + "/" + z[6] + "/" + z[3] + "/" + z[4])
                .containsExactlyInAnyOrder("Max/mustermann/null/0171 1234567", "null/Mustermann/0931 4444-0/null",
                        "Erika/Musterfrau/0931 4444-12/null");
    }

    @Test
    @DisplayName("FRITZ!Box-Telefonbuch: nur aktive Lieferanten, nur Personen mit Nummer")
    void telefonbuch() {
        Lieferanten ohneAngabe = lieferant("Ohne Angabe AG", true);
        ohneAngabe.setIstAktiv(null);
        person(ohneAngabe, "Paul", "Beispiel", null, "0931 55", null);
        person(holz, "Inaktiv", "Beispiel", null, "0931 66", null);
        em.flush();

        assertThat(repository.findeFuerTelefonbuch())
                .extracting(z -> (String) z[2] + "@" + z[5])
                .containsExactlyInAnyOrder("mustermann@Muster Stahl KG", "Mustermann@Muster Stahl KG",
                        "Musterfrau@Muster Stahl KG", "Beispiel@Ohne Angabe AG");
    }

    @Test
    @DisplayName("Freitextsuche der Lieferantenliste findet über Ansprechpartner (Name, Aufgabe, Nummer, E-Mail)")
    void freitextsuche() {
        for (String wort : List.of("musterfrau", "innendienst", "4444-12", "erika@", "0171")) {
            assertThat(lieferanten.findAll(LieferantenSuche.spezifikation(wort, null, null, null, null),
                    PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG)).getContent())
                    .as(wort).extracting(Lieferanten::getId).containsExactly(stahl.getId());
        }
        assertThat(lieferanten.findAll(LieferantenSuche.spezifikation("Mustermann", null, null, null, null),
                PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG)).getContent()).hasSize(1);
        assertThat(lieferanten.findAll(LieferantenSuche.spezifikation("'; DROP TABLE lieferanten; --", null, null,
                null, null), PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("Lieferant löschen nimmt seine Ansprechpartner mit")
    void kaskade() {
        lieferanten.deleteById(holz.getId());
        em.flush();
        em.clear();
        assertThat(repository.countByLieferantId(holz.getId())).isZero();
        assertThat(repository.countByLieferantId(stahl.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("Handy-Suche: Name, E-Mail und Ansprechpartner (auch inaktive Lieferanten); ohne Suchwort nur aktive")
    void handySuche() {
        Lieferanten mitMail = lieferant("Mail Beispiel AG", true);
        mitMail.getKundenEmails().add("einkauf@mail-beispiel.example");
        em.flush();
        var sortiert = PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG);
        for (String wort : List.of("musterfrau", "Innendienst", "4444-12", "erika@", "0171 1234")) {
            assertThat(lieferanten.findAll(LieferantenSuche.handySuche(wort), sortiert).getContent())
                    .as(wort).extracting(Lieferanten::getId).containsExactly(stahl.getId());
        }
        assertThat(lieferanten.findAll(LieferantenSuche.handySuche("moritz"), sortiert).getContent())
                .extracting(Lieferanten::getId).containsExactly(holz.getId());
        assertThat(lieferanten.findAll(LieferantenSuche.handySuche("EINKAUF@"), sortiert).getContent())
                .extracting(Lieferanten::getId).containsExactly(mitMail.getId());
        assertThat(lieferanten.findAll(LieferantenSuche.handySuche("beispiel"), sortiert).getContent())
                .extracting(Lieferanten::getLieferantenname).containsExactly("Holz Beispiel GmbH", "Mail Beispiel AG");
        assertThat(lieferanten.findAll(LieferantenSuche.handySuche(null), sortiert).getContent())
                .extracting(Lieferanten::getLieferantenname).containsExactly("Mail Beispiel AG", "Muster Stahl KG");
        assertThat(lieferanten.findAll(LieferantenSuche.handySuche("'; DROP TABLE lieferanten; --"), sortiert)
                .getContent()).isEmpty();
    }

    @Test
    @DisplayName("E-Mails mehrerer Lieferanten in einer Abfrage")
    void emailsGesammelt() {
        stahl = lieferanten.findById(stahl.getId()).orElseThrow();
        stahl.getKundenEmails().add("info@stahl.example");
        em.flush();
        assertThat(lieferanten.findeEmailsFuerLieferanten(List.of(stahl.getId(), holz.getId())))
                .extracting(z -> z[0] + "/" + z[1]).containsExactly(stahl.getId() + "/info@stahl.example");
    }
}
