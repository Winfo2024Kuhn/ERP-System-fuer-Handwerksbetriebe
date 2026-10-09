package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Abfragen gegen H2 für das Nachlesen von Werkstoffzeugnissen und den
 * Datumsfilter der Dokumentübersicht (Eingangsdatum, wenn kein Dokumentdatum da ist).
 */
@DataJpaTest
@DirtiesContext
class LieferantDokumentNachleseRepositoryTest {

    @Autowired
    private LieferantDokumentRepository dokumentRepository;

    @Autowired
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @PersistenceContext
    private EntityManager em;

    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterstahl GmbH");
        em.persist(lieferant);
    }

    @Test
    void findetZeugnisseOhneGeschaeftsdatenNummerOderPositionen() {
        LieferantDokument ohneDaten = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, LocalDateTime.of(2026, 9, 1, 8, 0));
        LieferantDokument ohneNummer = mitDaten(dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null), "  ", null);
        position(ohneNummer);
        LieferantDokument ohnePositionen = mitDaten(dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null), "Z-3", null);
        LieferantDokument vollstaendig = mitDaten(dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null), "Z-4", null);
        position(vollstaendig);
        // Andere Typen bleiben unberührt, auch wenn ihnen Daten fehlen
        dokument(LieferantDokumentTyp.SONSTIG, null);
        em.flush();
        em.clear();

        assertThat(dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                .containsExactly(ohneDaten.getId(), ohneNummer.getId(), ohnePositionen.getId());
    }

    @Test
    void erkenntVerknuepfungInBeideRichtungen() {
        LieferantDokument lieferschein = dokument(LieferantDokumentTyp.LIEFERSCHEIN, null);
        LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null);
        LieferantDokument allein = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null);
        zeugnis.getVerknuepfteDokumente().add(lieferschein);
        em.flush();
        em.clear();

        assertThat(dokumentRepository.zaehleMitVerknuepfung(zeugnis.getId())).isEqualTo(1);
        assertThat(dokumentRepository.zaehleMitVerknuepfung(lieferschein.getId())).isEqualTo(1);
        assertThat(dokumentRepository.zaehleMitVerknuepfung(allein.getId())).isZero();
        assertThat(dokumentRepository.zaehleMitVerknuepfung(Long.MAX_VALUE)).isZero();
    }

    @Test
    void datumsfilterNimmtOhneDokumentdatumDasEingangsdatum() {
        LieferantDokument mitDatum = mitDaten(dokument(LieferantDokumentTyp.RECHNUNG, LocalDateTime.of(2025, 12, 30, 9, 0)),
                "RE-1", LocalDate.of(2026, 9, 2));
        LieferantDokument ohneDatumImSeptember = mitDaten(
                dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, LocalDateTime.of(2026, 9, 30, 23, 59)), "Z-1", null);
        mitDaten(dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, LocalDateTime.of(2026, 10, 1, 0, 0)), "Z-2", null);
        // Dokumentdatum hat Vorrang: Upload im September, Beleg aber vom August
        mitDaten(dokument(LieferantDokumentTyp.LIEFERSCHEIN, LocalDateTime.of(2026, 9, 5, 9, 0)), "LS-1",
                LocalDate.of(2026, 8, 31));
        em.flush();
        em.clear();

        assertThat(geschaeftsdokumentRepository
                .findAllByDatumOderEingangBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .extracting(LieferantGeschaeftsdokument::getId)
                .containsExactlyInAnyOrder(mitDatum.getId(), ohneDatumImSeptember.getId());
    }

    private LieferantDokument dokument(LieferantDokumentTyp typ, LocalDateTime upload) {
        LieferantDokument doc = new LieferantDokument();
        doc.setLieferant(lieferant);
        doc.setTyp(typ);
        doc.setOriginalDateiname("zeugnis-dummy.pdf");
        doc.setUploadDatum(upload != null ? upload : LocalDateTime.of(2026, 9, 15, 12, 0));
        em.persist(doc);
        return doc;
    }

    private LieferantDokument mitDaten(LieferantDokument doc, String nummer, LocalDate datum) {
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setDokument(doc);
        g.setDokumentNummer(nummer);
        g.setDokumentDatum(datum);
        doc.setGeschaeftsdaten(g);
        em.persist(g);
        return doc;
    }

    private void position(LieferantDokument doc) {
        LieferantDokumentPosition p = new LieferantDokumentPosition();
        p.setGeschaeftsdokument(doc.getGeschaeftsdaten());
        p.setPositionNr(1);
        p.setBezeichnung("Flachstahl");
        p.setMenge(BigDecimal.ONE);
        em.persist(p);
    }
}
