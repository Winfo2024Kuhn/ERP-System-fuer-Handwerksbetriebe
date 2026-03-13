package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AnfrageRepositoryTest {

    @Autowired
    private AnfrageRepository anfrageRepository;

    @Autowired
    private ProjektRepository projektRepository;

    @Test
    void findByProjektIdInReturnsMatchingAnfragen() {
        Projekt projekt1 = createProjekt("AN1");
        Projekt projekt2 = createProjekt("AN2");
        Projekt projekt3 = createProjekt("AN3");
        projektRepository.saveAndFlush(projekt1);
        projektRepository.saveAndFlush(projekt2);
        projektRepository.saveAndFlush(projekt3);

        Anfrage anfrage1 = new Anfrage();
        anfrage1.setProjekt(projekt1);
        anfrageRepository.save(anfrage1);

        Anfrage anfrage2 = new Anfrage();
        anfrage2.setProjekt(projekt2);
        anfrageRepository.save(anfrage2);

        Anfrage anfrage3 = new Anfrage();
        anfrage3.setProjekt(projekt3);
        anfrageRepository.save(anfrage3);
        anfrageRepository.flush();

        List<Anfrage> result = anfrageRepository.findByProjektIdIn(List.of(projekt1.getId(), projekt2.getId()));

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
