package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WerkstoffRepositoryTest {

    @Autowired
    private WerkstoffRepository werkstoffRepository;

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;


    @Test
    void savesWerkstoffAndAssociatesArtikel() {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setName("Stahl");
        werkstoff = werkstoffRepository.save(werkstoff);

        Artikel artikel = new Artikel();
        artikel.setProduktlinie("Linie");
        artikel.setProduktname("Produkt");
        artikel.setProdukttext("Beschreibung");
        artikel.setVerpackungseinheit(1L);
        artikel.setPreiseinheit("Stk");
        artikel.setWerkstoff(werkstoff);

        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("L1");
        lieferantenRepository.save(lieferant);

        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setLieferant(lieferant);
        lap.setExterneArtikelnummer("A-1");
        artikel.getArtikelpreis().add(lap);

        artikelRepository.save(artikel);

        Artikel reloaded = artikelRepository.findById(artikel.getId()).orElseThrow();
        assertThat(reloaded.getWerkstoff()).isNotNull();
        assertThat(reloaded.getWerkstoff().getId()).isEqualTo(werkstoff.getId());
        assertThat(reloaded.getExterneArtikelnummer()).isEqualTo("A-1");
    }

    /**
     * Der Werkstattname ist der Begriff, den der Schlosser in die Suche tippt
     * ("V2A" statt "1.4301"). Er muss die Runde durch die Datenbank ueberstehen,
     * sonst laeuft die Suchbedingung in {@code ArtikelController} ins Leere.
     */
    @Test
    void speichertWerkstattnameNebenTechnischemNamen() {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setName("1.4301");
        werkstoff.setAnzeigename("Edelstahl 1.4301");
        werkstoff.setWerkstattname("V2A");
        werkstoffRepository.save(werkstoff);

        Werkstoff geladen = werkstoffRepository.findByNameIgnoreCase("1.4301").orElseThrow();
        assertThat(geladen.getWerkstattname()).isEqualTo("V2A");
        assertThat(geladen.getName()).isEqualTo("1.4301");
    }

    /**
     * V4A ist kein einzelner Werkstoff, sondern die Werkstattbezeichnung fuer
     * die molybdaenlegierte Gruppe. Zwei Werkstoffe duerfen sich denselben
     * Werkstattnamen teilen - eine Suche nach "V4A" liefert dann beide.
     */
    @Test
    void mehrereWerkstoffeDuerfenDenselbenWerkstattnamenTragen() {
        Werkstoff v4a1 = new Werkstoff();
        v4a1.setName("1.4404");
        v4a1.setWerkstattname("V4A");
        werkstoffRepository.save(v4a1);

        Werkstoff v4a2 = new Werkstoff();
        v4a2.setName("1.4571");
        v4a2.setWerkstattname("V4A");
        werkstoffRepository.save(v4a2);

        assertThat(werkstoffRepository.findByNameIgnoreCase("1.4404").orElseThrow().getWerkstattname())
                .isEqualTo("V4A");
        assertThat(werkstoffRepository.findByNameIgnoreCase("1.4571").orElseThrow().getWerkstattname())
                .isEqualTo("V4A");
    }
}
