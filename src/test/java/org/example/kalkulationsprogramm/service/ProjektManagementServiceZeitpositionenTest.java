package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@Transactional
class ProjektManagementServiceIT {

    @Autowired
    private ProjektManagementService projektManagementService;

    @Autowired
    private ProjektRepository projektRepository;

    @Autowired
    private ProduktkategorieRepository produktkategorieRepository;

    @Autowired
    private ArbeitsgangRepository arbeitsgangRepository;

    @Autowired
    private ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;

    @Autowired
    private ZeitbuchungRepository ZeitbuchungRepository;

    @Autowired
    private KundeRepository kundeRepository;

    @Autowired
    private AbteilungRepository abteilungRepository;

    @Test
    void aktualisiereProjektFuegtGenauEineZeitpositionHinzu() {
        Produktkategorie produktkategorie = new Produktkategorie();
        produktkategorie.setBezeichnung("Kategorie");
        produktkategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        produktkategorie = produktkategorieRepository.save(produktkategorie);

        Kunde kunde = erzeugeKunde("K-1");
        kunde.getKundenEmails().add("kunde@example.org");
        kunde = kundeRepository.save(kunde);

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau");
        projekt.setAuftragsnummer("A-1");
        projekt.setAnlegedatum(LocalDate.now());
        projekt.setBruttoPreis(BigDecimal.ONE);
        projekt.setBezahlt(false);
        projekt.setKundenId(kunde);

        ProjektProduktkategorie projektProduktkategorie = new ProjektProduktkategorie();
        projektProduktkategorie.setProjekt(projekt);
        projektProduktkategorie.setProduktkategorie(produktkategorie);
        projektProduktkategorie.setMenge(BigDecimal.ONE);
        projekt.getProjektProduktkategorien().add(projektProduktkategorie);

        projekt = projektRepository.saveAndFlush(projekt);

        Abteilung abteilung = new Abteilung();
        abteilung.setName("Test-Abteilung");
        abteilung = abteilungRepository.save(abteilung);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setBeschreibung("Montage");
        arbeitsgang.setAbteilung(abteilung);
        arbeitsgang = arbeitsgangRepository.saveAndFlush(arbeitsgang);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setArbeitsgang(arbeitsgang);
        stundensatz.setJahr(projekt.getAnlegedatum().getYear());
        stundensatz.setSatz(new BigDecimal("10.00"));
        arbeitsgangStundensatzRepository.saveAndFlush(stundensatz);

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setProduktkategorien(List.of(erzeugeProduktkategorieDto(produktkategorie.getId(), BigDecimal.ONE)));
        dto.setZeitPositionen(
                List.of(erzeugeZeitDto(arbeitsgang.getId(), produktkategorie.getId(), new BigDecimal("2.5"))));
        dto.setKundenEmails(List.of("kunde@example.org"));
        dto.setAuftragsnummer(projekt.getAuftragsnummer());
        dto.setKundenId(kunde.getId());

        projektManagementService.aktualisiereProjekt(projekt.getId(), dto, null, null, null, null, null);
        Zeitbuchung gespeicherteZeit = ZeitbuchungRepository.findAll().getFirst();
        assertThat(gespeicherteZeit.getProjekt().getId()).isEqualTo(projekt.getId());
        assertThat(gespeicherteZeit.getAnzahlInStunden()).isEqualByComparingTo(new BigDecimal("2.5"));
    }

    private Kunde erzeugeKunde(String kundennummer) {
        Kunde kunde = new Kunde();
        kunde.setKundennummer(kundennummer);
        kunde.setName("Kunde " + kundennummer);
        return kundeRepository.save(kunde);
    }

    private ProjektProduktkategorieErfassenDto erzeugeProduktkategorieDto(Long id, BigDecimal menge) {
        ProjektProduktkategorieErfassenDto dto = new ProjektProduktkategorieErfassenDto();
        dto.setProduktkategorieID(id);
        dto.setMenge(menge);
        return dto;
    }

    private ZeitErfassenDto erzeugeZeitDto(Long arbeitsgangId, Long produktkategorieId, BigDecimal stunden) {
        ZeitErfassenDto dto = new ZeitErfassenDto();
        dto.setArbeitsgangID(arbeitsgangId);
        dto.setProduktkategorieID(produktkategorieId);
        dto.setAnzahlInStunden(stunden);
        return dto;
    }

    @Test
    void aktualisiereProjektEntferntAlleZeitpositionenOhneFehler() {
        Produktkategorie produktkategorie = new Produktkategorie();
        produktkategorie.setBezeichnung("Kategorie");
        produktkategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        produktkategorie = produktkategorieRepository.save(produktkategorie);

        Kunde kunde = erzeugeKunde("K-2");
        kunde.getKundenEmails().add("kunde@example.org");
        kunde = kundeRepository.save(kunde);

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bauvorhaben");
        projekt.setAuftragsnummer("A-1");
        projekt.setAnlegedatum(LocalDate.now());
        projekt.setBruttoPreis(BigDecimal.ONE);
        projekt.setBezahlt(false);
        projekt.setKundenId(kunde);

        ProjektProduktkategorie projektProduktkategorie = new ProjektProduktkategorie();
        projektProduktkategorie.setProjekt(projekt);
        projektProduktkategorie.setProduktkategorie(produktkategorie);
        projektProduktkategorie.setMenge(BigDecimal.ONE);
        projekt.getProjektProduktkategorien().add(projektProduktkategorie);

        projekt = projektRepository.saveAndFlush(projekt);

        Abteilung abteilung = new Abteilung();
        abteilung.setName("Test-Abteilung-2"); // Use a different name to avoid unique constraint violations
        abteilung = abteilungRepository.save(abteilung);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setBeschreibung("Montage");
        arbeitsgang.setAbteilung(abteilung);
        arbeitsgang = arbeitsgangRepository.saveAndFlush(arbeitsgang);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setArbeitsgang(arbeitsgang);
        stundensatz.setJahr(projekt.getAnlegedatum().getYear());
        stundensatz.setSatz(new BigDecimal("10.00"));
        arbeitsgangStundensatzRepository.saveAndFlush(stundensatz);

        Zeitbuchung zeit = new Zeitbuchung();
        zeit.setProjekt(projekt);
        zeit.setProjektProduktkategorie(projekt.getProjektProduktkategorien().getFirst());
        zeit.setArbeitsgang(arbeitsgang);
        zeit.setArbeitsgangStundensatz(stundensatz);
        zeit.setAnzahlInStunden(new BigDecimal("3.0"));
        projekt.getZeitbuchungen().add(zeit);
        projekt = projektRepository.saveAndFlush(projekt);

        assertThat(ZeitbuchungRepository.count()).isEqualTo(1);

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setProduktkategorien(List.of(erzeugeProduktkategorieDto(produktkategorie.getId(), BigDecimal.ONE)));
        dto.setZeitPositionen(Collections.emptyList());
        dto.setKundenEmails(List.of("kunde@example.org"));
        dto.setAuftragsnummer(projekt.getAuftragsnummer());
        dto.setBauvorhaben(projekt.getBauvorhaben());
        dto.setKunde(projekt.getKunde());
        dto.setKundennummer(projekt.getKundennummer());
        dto.setBruttoPreis(projekt.getBruttoPreis());
        dto.setAnlegedatum(projekt.getAnlegedatum());
        dto.setBezahlt(projekt.isBezahlt());
        dto.setKundenId(kunde.getId());

        projektManagementService.aktualisiereProjekt(projekt.getId(), dto, null, null, null, null, null);

        assertThat(ZeitbuchungRepository.count()).isZero();
    }
}
