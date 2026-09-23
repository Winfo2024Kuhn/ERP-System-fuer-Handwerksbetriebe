package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMengenbuchungRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@Transactional
class ProjektArtikelMaterialkostenIntegrationTest {
    @Autowired private ProjektManagementService service;
    @Autowired private ProjektRepository projektRepository;
    @Autowired private ArtikelRepository artikelRepository;
    @Autowired private ArtikelInProjektRepository artikelInProjektRepository;
    @Autowired private EinkaufBedarfRepository bedarfRepository;
    @Autowired private EinkaufMengenbuchungRepository mengenbuchungRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void katalogkostenBleibenMaterialkostenOhneAipBedarfOderBestellung() {
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Dummy Werkstattbau");
        projekt.setAuftragsnummer("DUMMY-001");
        projekt.setBruttoPreis(BigDecimal.ZERO);
        projekt.setAnlegedatum(LocalDate.now());
        projekt.setBezahlt(false);
        projekt = projektRepository.saveAndFlush(projekt);
        Long projektId = projekt.getId();

        Artikel artikel = new Artikel();
        artikel.setProduktname("Dummy Stahlprofil");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setExterneArtikelnummer("DUMMY-STAHL-01");
        preis.setPreis(new BigDecimal("2.0000"));
        preis.setPreisAenderungsdatum(new Date());
        artikel.getArtikelpreis().add(preis);
        artikel = artikelRepository.saveAndFlush(artikel);

        ArtikelMengeDto auswahl = new ArtikelMengeDto();
        auswahl.setArtikelId(artikel.getId());
        auswahl.setMenge(new BigDecimal("3"));
        auswahl.setEinheit("STUECK");
        auswahl.setPreis(new BigDecimal("2.5000"));

        service.erfasseArtikelKosten(projektId, List.of(auswahl));
        entityManager.flush();
        entityManager.clear();

        Projekt gespeichert = projektRepository.findById(projektId).orElseThrow();
        assertThat(gespeichert.getMaterialkosten()).hasSize(1);
        assertThat(gespeichert.getMaterialkosten().getFirst().getArtikelIdSnapshot()).isEqualTo(artikel.getId());
        assertThat(gespeichert.getMaterialkosten().getFirst().getMengeSnapshot())
                .isEqualByComparingTo("3.000000");
        assertThat(gespeichert.getMaterialkosten().getFirst().getBetrag()).isEqualByComparingTo("7.50");
        assertThat(gespeichert.getMaterialkosten().getFirst().getPreisJeEinheitSnapshot()).isEqualByComparingTo("2.500000");
        assertThat(artikelRepository.findById(artikel.getId()).orElseThrow().getArtikelpreis().getFirst().getPreis())
                .isEqualByComparingTo("2.0000");
        assertThat(artikelInProjektRepository.findAll()).isEmpty();
        assertThat(bedarfRepository.findAll().stream().filter(b -> projektId.equals(b.getProjektId())))
                .isEmpty();
        assertThat(mengenbuchungRepository.findAll()).isEmpty();

        MaterialkostenErfassenDto manuell = new MaterialkostenErfassenDto();
        manuell.setBeschreibung("Dummy Verpackungsmaterial");
        manuell.setBetrag(new BigDecimal("1.25"));
        service.aktualisiereMaterialkosten(projektId, List.of(manuell));
        entityManager.flush();
        entityManager.clear();
        Projekt nachPatch = projektRepository.findById(projektId).orElseThrow();
        assertThat(nachPatch.getMaterialkosten()).hasSize(2);
        assertThat(nachPatch.getMaterialkosten().stream().filter(m -> m.getArtikelIdSnapshot() != null)
                .findFirst().orElseThrow().getPreisJeEinheitSnapshot()).isEqualByComparingTo("2.500000");

        var listenErgebnis = service.findeProjekteMitFilter(null, null, null, null, null,
                null, null, null, null, 0, 50);
        assertThat(listenErgebnis.getContent()).extracting("id").contains(projektId);
    }
}
