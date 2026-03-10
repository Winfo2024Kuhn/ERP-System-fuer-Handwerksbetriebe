package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AngebotRepositoryTest {

    @Autowired
    private AngebotRepository angebotRepository;

    @Autowired
    private ProjektRepository projektRepository;

    @Test
    void findByProjektIdInReturnsMatchingAngebote() {
        Projekt projekt1 = createProjekt("AN1");
        Projekt projekt2 = createProjekt("AN2");
        Projekt projekt3 = createProjekt("AN3");
        projektRepository.saveAndFlush(projekt1);
        projektRepository.saveAndFlush(projekt2);
        projektRepository.saveAndFlush(projekt3);

        Angebot angebot1 = new Angebot();
        angebot1.setProjekt(projekt1);
        angebotRepository.save(angebot1);

        Angebot angebot2 = new Angebot();
        angebot2.setProjekt(projekt2);
        angebotRepository.save(angebot2);

        Angebot angebot3 = new Angebot();
        angebot3.setProjekt(projekt3);
        angebotRepository.save(angebot3);
        angebotRepository.flush();

        List<Angebot> result = angebotRepository.findByProjektIdIn(List.of(projekt1.getId(), projekt2.getId()));

        assertThat(result)
                .extracting(a -> a.getProjekt().getId())
                .containsExactlyInAnyOrder(projekt1.getId(), projekt2.getId());
    }

    private Projekt createProjekt(String auftragsnummer) {
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau");
        projekt.setAuftragsnummer(auftragsnummer);
        projekt.setAnlegedatum(LocalDate.now());
        projekt.setBruttoPreis(BigDecimal.ZERO);
        projekt.setBezahlt(false);
        return projekt;
    }
}
