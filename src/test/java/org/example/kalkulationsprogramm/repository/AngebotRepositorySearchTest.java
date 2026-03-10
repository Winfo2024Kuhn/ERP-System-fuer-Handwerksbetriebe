package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AngebotRepositorySearchTest {

        @Autowired
        private AngebotRepository angebotRepository;
        @Autowired
        private KundeRepository kundeRepository;

        @Test
        void search_respects_umlauts_and_ascii_separately() {
                Angebot a1 = new Angebot();
                Kunde k1 = new Kunde();
                k1.setName("Haentschel");
                k1.setKundennummer("K1001");
                kundeRepository.saveAndFlush(k1);
                a1.setKunde(k1);
                a1.setBauvorhaben("Testbau");
                a1.setAnlegedatum(LocalDate.now());
                angebotRepository.saveAndFlush(a1);

                Angebot a2 = new Angebot();
                Kunde k2 = new Kunde();
                k2.setName("H\u00E4ntschel"); // Häntschel
                k2.setKundennummer("K1002");
                kundeRepository.saveAndFlush(k2);
                a2.setKunde(k2);
                a2.setBauvorhaben("Testbau 2");
                a2.setAnlegedatum(LocalDate.now());
                angebotRepository.saveAndFlush(a2);

                // Query: Häntschel should match only Häntschel
                List<Angebot> umlaut = angebotRepository.search("H\u00E4ntschel", null, null, null, null);
                assertThat(umlaut).extracting(a -> a.getKunde().getName())
                                .containsExactlyInAnyOrder("H\u00E4ntschel");

                // Query: Haentschel should match only Haentschel
                List<Angebot> ascii = angebotRepository.search("Haentschel", null, null, null, null);
                assertThat(ascii).extracting(a -> a.getKunde().getName())
                                .containsExactlyInAnyOrder("Haentschel");
        }

        @Test
        void search_bauvorhaben_does_not_cross_map_umlauts() {
                Angebot a1 = new Angebot();
                Kunde k1 = new Kunde();
                k1.setName("Kunde");
                k1.setKundennummer("K1003");
                kundeRepository.saveAndFlush(k1);
                a1.setKunde(k1);
                a1.setBauvorhaben("M\u00FCllerstra\u00DFe Dach"); // Müllerstraße Dach
                a1.setAnlegedatum(LocalDate.now());
                angebotRepository.saveAndFlush(a1);

                Angebot a2 = new Angebot();
                Kunde k2 = new Kunde();
                k2.setName("Kunde");
                k2.setKundennummer("K1004");
                kundeRepository.saveAndFlush(k2);
                a2.setKunde(k2);
                a2.setBauvorhaben("Muellerbau");
                a2.setAnlegedatum(LocalDate.now());
                angebotRepository.saveAndFlush(a2);

                // Query: Mueller should match only 'Muellerbau'
                List<Angebot> res1 = angebotRepository.search(null, "Mueller", null, null, null);
                assertThat(res1).extracting(Angebot::getBauvorhaben)
                                .containsExactlyInAnyOrder("Muellerbau");

                // Query: Müller should match only the umlaut variant
                List<Angebot> res2 = angebotRepository.search(null, "M\u00FCller", null, null, null);
                assertThat(res2).extracting(Angebot::getBauvorhaben)
                                .containsExactlyInAnyOrder("M\u00FCllerstra\u00DFe Dach");
        }
}
