package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Findet die Freitextsuche, was der Nutzer meint? Laeuft gegen die echte
 * Testdatenbank, weil die Zahlensuche einen regulaeren Ausdruck in SQL nutzt.
 */
@DataJpaTest
class ArtikelFreitextSucheTest {

    @Autowired private ArtikelRepository artikelRepository;
    @Autowired private LieferantenRepository lieferantenRepository;
    @Autowired private WerkstoffRepository werkstoffRepository;

    @BeforeEach
    void anlegen() {
        Werkstoff v2a = new Werkstoff();
        v2a.setName("1.4301");
        werkstoffRepository.saveAndFlush(v2a);

        artikel("A-003270", "Flachstahl 20 x 10", null);
        artikel("A-003271", "Flachstahl 120 x 10", null);
        artikel("AL-FL-x100-20", "Flachstahl 100 x 20", null);
        artikel("A-003272", "Flachstahl 20 x 5", null);
        artikel("A-003273", "Rundrohr 42,4 x 2", null);
        artikel("A-003274", "Rundrohr 42 x 2", null);
        artikel("A-003275", "Träger HEA 200", null);
        artikel("A-003276", "Sechskantschraube M8 x 20", null);
        artikel("A-003277", "Sechskantschraube M80 x 20", null);
        Artikel blech = artikel("A-003278", "Blech 2 mm", null);
        blech.setWerkstoff(v2a);
        blech.setSuchtext("blech 2 mm 1.4301");

        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Muster Stahlhandel");
        lieferantenRepository.saveAndFlush(lieferant);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(blech);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("100234");
        preis.setPreis(BigDecimal.TEN);
        blech.getArtikelpreis().add(preis);
        artikelRepository.saveAndFlush(blech);
    }

    private Artikel artikel(String nummer, String name, String suchtext) {
        Artikel a = new Artikel();
        a.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        a.setArtikelnummer(nummer);
        a.setProduktname(name);
        a.setSuchtext(suchtext != null ? suchtext : ArtikelSuchtext.aufbauen(a));
        return artikelRepository.saveAndFlush(a);
    }

    private List<String> suche(String eingabe) {
        return artikelRepository.findAll(ArtikelFreitextSuche.fuer(eingabe)).stream().map(Artikel::getProduktname).sorted().toList();
    }

    @Test
    void zahlenZaehlenNurAlsGanzeZahl() {
        // Der Fall aus der Praxis: frueher kamen 100 x 20 und 120 x 10 mit.
        assertThat(suche("flachstahl 20x10")).containsExactly("Flachstahl 20 x 10");
        assertThat(suche("flachstahl 20")).containsExactly("Flachstahl 100 x 20", "Flachstahl 20 x 10", "Flachstahl 20 x 5");
    }

    @Test
    void dezimalkommaUndPunktSindGleich_undGanzeZahlTrifftKeineKommazahl() {
        assertThat(suche("rohr 42,4")).containsExactly("Rundrohr 42,4 x 2");
        assertThat(suche("rohr 42.4x2")).containsExactly("Rundrohr 42,4 x 2");
        assertThat(suche("rohr 42")).containsExactly("Rundrohr 42 x 2");
    }

    @Test
    void wortMitZahlAmEnde_trifftKeineLaengereZahl() {
        assertThat(suche("m8")).containsExactly("Sechskantschraube M8 x 20");
    }

    @Test
    void umlauteUndUmschreibungSindGleich() {
        assertThat(suche("traeger 200")).containsExactly("Träger HEA 200");
        assertThat(suche("träger")).containsExactly("Träger HEA 200");
    }

    @Test
    void nummernAbVierZeichenAuchAlsTeilstueck() {
        assertThat(suche("3270")).containsExactly("Flachstahl 20 x 10");
        assertThat(suche("4301")).containsExactly("Blech 2 mm");
        assertThat(suche("0234")).containsExactly("Blech 2 mm");
        assertThat(suche("muster blech")).containsExactly("Blech 2 mm");
    }

    @Test
    void zerlegtDieEingabeInWoerter() {
        // Die Abmessung darf zusammen oder getrennt geschrieben sein.
        assertThat(ArtikelFreitextSuche.zerlege("rundrohr 42.4 x2")).containsExactly("rundrohr", "42.4", "2");
        assertThat(ArtikelFreitextSuche.zerlege("Rundrohr 42.4x2")).containsExactly("rundrohr", "42.4", "2");
        assertThat(ArtikelFreitextSuche.zerlege("60x40x3")).containsExactly("60", "40", "3");
        // Ein Komma zwischen Ziffern ist ein Dezimalkomma, sonst ein Trenner.
        assertThat(ArtikelFreitextSuche.zerlege("rohr 42,4x2")).containsExactly("rohr", "42,4", "2");
        assertThat(ArtikelFreitextSuche.zerlege("rohr,42")).containsExactly("rohr", "42");
        // Mehrfachnennungen und Leerzeichen duerfen nicht stoeren.
        assertThat(ArtikelFreitextSuche.zerlege("  rohr   rohr  42.4 ")).containsExactly("rohr", "42.4");
        assertThat(ArtikelFreitextSuche.zerlege("   ")).isEmpty();
        assertThat(ArtikelFreitextSuche.zerlege(null)).isEmpty();
    }

    @Test
    void sonderzeichenSindKeinMuster() {
        assertThat(suche("20(x")).isEmpty();
        assertThat(suche("'; DROP TABLE artikel; --")).isEmpty();
        // Hochkomma im Zahlenpfad (Ziffer am Rand -> regulaerer Ausdruck)
        assertThat(suche("1'or'1")).isEmpty();
        assertThat(suche("20'")).isEmpty();
        assertThat(suche("20' or '1'='1")).isEmpty();
        assertThat(artikelRepository.count()).isEqualTo(10);
    }
}
