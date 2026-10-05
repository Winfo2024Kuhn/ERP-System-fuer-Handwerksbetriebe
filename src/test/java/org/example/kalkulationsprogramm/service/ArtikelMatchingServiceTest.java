package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ArtikelMatchingService.class)
class ArtikelMatchingServiceTest {

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private ArtikelMatchingService artikelMatchingService;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Test
    void findetBestenTrefferNachAehnlichkeit() {
        Artikel a1 = new Artikel();
        a1.setProduktname("Quadratrohr 50X3");
        a1.setProduktlinie("EN 10305-5");
        artikelRepository.save(a1);

        Artikel a2 = new Artikel();
        a2.setProduktname("Rundrohr 50X3");
        a2.setProduktlinie("EN 10219");
        artikelRepository.save(a2);

        List<Artikel> treffer = artikelMatchingService.findeBesteTreffer("Quadratrohr 50X3", "EN 10305-5");

        assertThat(treffer).isNotEmpty();
        assertThat(treffer.getFirst().getProduktname()).isEqualTo("Quadratrohr 50X3");
    }

    @Test
    void findetLieferantNachDomainUndSpeichertNeueAdresse() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Beispiel Stahl");
        l.getKundenEmails().add("info@beispiel-stahl.example");
        lieferantenRepository.save(l);

        Lieferanten found = artikelMatchingService.findeLieferantFuerEmail("max.mustermann@beispiel-stahl.example")
                .orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(l.getId());

        artikelMatchingService.merkeLieferantenEmail(l, "max.mustermann@beispiel-stahl.example");
        Lieferanten reloaded = lieferantenRepository.findById(l.getId()).orElseThrow();
        assertThat(reloaded.getKundenEmails()).contains("info@beispiel-stahl.example", "max.mustermann@beispiel-stahl.example");
    }

    @Test
    void findetLieferantAuchBeiWhitespaceInAdresse() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Beispiel Stahl");
        l.getKundenEmails().add(" info@beispiel-stahl.example ");
        lieferantenRepository.save(l);

        assertThat(artikelMatchingService.findeLieferantFuerEmail("user@beispiel-stahl.example")).isPresent();
    }

    @Test
    void merkeLieferantenEmailTrimmtAdresse() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Beispiel Stahl");
        lieferantenRepository.save(l);

        artikelMatchingService.merkeLieferantenEmail(l, " new@beispiel-stahl.example ");
        Lieferanten reloaded = lieferantenRepository.findById(l.getId()).orElseThrow();
        assertThat(reloaded.getKundenEmails()).containsExactly("new@beispiel-stahl.example");
    }

    @Test
    void findeLieferantFuerUnbekannteDomainGibtLeer() {
        assertThat(artikelMatchingService.findeLieferantFuerEmail("user@unknown.com")).isEmpty();
    }
}

