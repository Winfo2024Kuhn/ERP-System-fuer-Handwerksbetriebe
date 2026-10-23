package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import jakarta.persistence.criteria.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.EntityManager;

/**
 * Sortierung mit festgelegter Lage leerer Werte (H2). Dass PostgreSQL wie gewohnt
 * sortiert, prueft {@link DatenbankKompatibilitaetSucheTest}.
 */
@DataJpaTest
class PortableSortierungTest {

    @Autowired
    ArtikelRepository artikel;
    @Autowired
    EntityManager em;

    @Test
    @DisplayName("Leere Werte wie bisher: aufsteigend zuerst, absteigend zuletzt; Angabe am Sort.Order geht vor")
    void leerwerte() {
        assertThat(PortableSortierung.leerwerte(Sort.Order.asc("a"))).isEqualTo(Nulls.FIRST);
        assertThat(PortableSortierung.leerwerte(Sort.Order.desc("a"))).isEqualTo(Nulls.LAST);
        assertThat(PortableSortierung.leerwerte(Sort.Order.asc("a").nullsLast())).isEqualTo(Nulls.LAST);
        assertThat(PortableSortierung.leerwerte(Sort.Order.desc("a").nullsFirst())).isEqualTo(Nulls.FIRST);
    }

    @Test
    @DisplayName("Seite ohne Sortierung - die Sortierung haengt an der Specification")
    void ohneSortierung() {
        Pageable seite = PortableSortierung.ohneSortierung(PageRequest.of(2, 25, Sort.by("produktname")));
        assertThat(seite.getPageNumber()).isEqualTo(2);
        assertThat(seite.getPageSize()).isEqualTo(25);
        assertThat(seite.getSort().isUnsorted()).isTrue();
        assertThat(PortableSortierung.ohneSortierung(Pageable.unpaged()).isPaged()).isFalse();
        Specification<Artikel> filter = (root, query, cb) -> null;
        assertThat(PortableSortierung.mit(filter, Sort.unsorted())).isSameAs(filter);
    }

    @Test
    @DisplayName("Sortiert ueber Verweise (LEFT JOIN), ohne Gross-/Kleinschreibung, Zaehlung bleibt richtig")
    void verweisUndGrossKlein() {
        Werkstoff stahl = new Werkstoff();
        stahl.setName("s235jr");
        em.persist(stahl);
        Artikel a = new Artikel();
        a.setProduktname("Rohr A");
        a.setWerkstoff(stahl);
        em.persist(a);
        Werkstoff edelstahl = new Werkstoff();
        edelstahl.setName("V2A");
        em.persist(edelstahl);
        Artikel b = new Artikel();
        b.setProduktname("Rohr B");
        b.setWerkstoff(edelstahl);
        em.persist(b);
        Artikel ohne = new Artikel();
        ohne.setProduktname("Rohr C");
        em.persist(ohne);
        em.flush();

        Sort sort = Sort.by(Sort.Order.desc("werkstoff.name").ignoreCase());
        var seite = artikel.findAll(PortableSortierung.mit(Specification.unrestricted(), sort),
                PortableSortierung.ohneSortierung(PageRequest.of(0, 2, sort)));

        // "V2A" vor "s235jr" nur ohne Gross-/Kleinschreibung; ohne Werkstoff zuletzt (Seite 2)
        assertThat(seite.getContent()).extracting(Artikel::getProduktname).containsExactly("Rohr B", "Rohr A");
        assertThat(seite.getTotalElements()).isEqualTo(3);
        assertThat(artikel.findAll(PortableSortierung.mit(Specification.unrestricted(), sort))
                .stream().map(Artikel::getProduktname).toList()).isEqualTo(List.of("Rohr B", "Rohr A", "Rohr C"));
    }
}
