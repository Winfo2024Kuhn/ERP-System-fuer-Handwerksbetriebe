package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Issue #242: Nebenkosten merken sich, zu welchen Warenpositionen sie gehören. Der Bezug kommt
 * mit den Positionen zurück und verschwindet beim erneuten Auslesen (alle Positionen löschen).
 */
@DataJpaTest
@DirtiesContext
class LieferantDokumentPositionBezugTest {

    @Autowired
    private LieferantDokumentPositionRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void bezugKommtMitDenPositionenZurueck() {
        LieferantGeschaeftsdokument rechnung = rechnung();
        LieferantDokumentPosition flachstahl = position(rechnung, 1, PositionsArt.WARE, "Flachstahl 50x5");
        LieferantDokumentPosition rohr = position(rechnung, 2, PositionsArt.WARE, "Rundrohr 60,3x2,9");
        LieferantDokumentPosition zuschlag = position(rechnung, 3, PositionsArt.NEBENKOSTEN, "Legierungszuschlag");
        zuschlag.getBezugPositionIds().add(flachstahl.getId());
        em.flush();
        em.clear();

        List<LieferantDokumentPosition> geladen = repository.findByGeschaeftsdokumentId(rechnung.getId());

        assertThat(geladen).extracting(LieferantDokumentPosition::getPositionNr).containsExactly(1, 2, 3);
        assertThat(geladen.get(2).getBezugPositionIds()).containsExactly(flachstahl.getId());
        assertThat(geladen.get(0).getBezugPositionIds()).isEmpty();
        assertThat(rohr.getId()).isNotNull();
    }

    @Test
    void positionenLoeschenNimmtDenBezugMit() {
        LieferantGeschaeftsdokument rechnung = rechnung();
        LieferantDokumentPosition flachstahl = position(rechnung, 1, PositionsArt.WARE, "Flachstahl 50x5");
        LieferantDokumentPosition zuschlag = position(rechnung, 2, PositionsArt.NEBENKOSTEN, "Legierungszuschlag");
        zuschlag.getBezugPositionIds().add(flachstahl.getId());
        em.flush();
        em.clear();

        assertThat(repository.deleteByGeschaeftsdokumentId(rechnung.getId())).isEqualTo(2);
        em.flush();
        em.clear();

        assertThat(repository.countByGeschaeftsdokumentId(rechnung.getId())).isZero();
        Number bezuege = (Number) em.createNativeQuery("SELECT COUNT(*) FROM lieferant_dokument_position_bezug")
                .getSingleResult();
        assertThat(bezuege.longValue()).isZero();
    }

    private LieferantGeschaeftsdokument rechnung() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterstahl GmbH");
        em.persist(lieferant);
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("rechnung.pdf");
        dokument.setUploadDatum(LocalDateTime.now());
        em.persist(dokument);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokument(dokument);
        gd.setDokumentNummer("RE-4711");
        dokument.setGeschaeftsdaten(gd);
        em.persist(gd);
        return gd;
    }

    private LieferantDokumentPosition position(LieferantGeschaeftsdokument gd, int nr, PositionsArt art,
            String bezeichnung) {
        LieferantDokumentPosition p = new LieferantDokumentPosition();
        p.setGeschaeftsdokument(gd);
        p.setPositionNr(nr);
        p.setPositionsArt(art);
        p.setBezeichnung(bezeichnung);
        p.setGesamtpreisNetto(BigDecimal.TEN);
        em.persist(p);
        return p;
    }
}
