package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PositionsSuchtext;
import org.example.kalkulationsprogramm.dto.PositionsSuchtreffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Positionssuche gegen H2: Suchtext wird beim Speichern gebildet, Wörter sind
 * UND-verknüpft, Typen und Lieferant filtern, LIKE-Sonderzeichen sind maskiert.
 */
@DataJpaTest
@DirtiesContext
class LieferantDokumentPositionRepositorySucheTest {

    private static final Set<LieferantDokumentTyp> ALLE = EnumSet.allOf(LieferantDokumentTyp.class);

    @Autowired
    private LieferantDokumentPositionRepository repository;

    @PersistenceContext
    private EntityManager em;

    private Lieferanten lieferant;
    private Lieferanten andererLieferant;
    private LieferantDokument zeugnis;
    private LieferantDokument rechnung;

    @BeforeEach
    void setUp() {
        lieferant = lieferant("Musterstahl GmbH");
        andererLieferant = lieferant("Andere GmbH");

        zeugnis = dokument(lieferant, LieferantDokumentTyp.WERKSTOFFZEUGNIS, LocalDate.of(2026, 9, 1));
        position(zeugnis, 1, "Flachstahl", "S235JR+AR", "123456", "50 × 5");
        position(zeugnis, 2, "Rundrohr", "S355J2H", "777888", "60,3x2,9");

        rechnung = dokument(lieferant, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 10, 15));
        position(rechnung, 1, "FL 50x5 S235JR", null, null, null);
        position(rechnung, 2, "Rabatt 5%_Aktion", null, null, null);

        LieferantDokument fremd = dokument(andererLieferant, LieferantDokumentTyp.LIEFERSCHEIN, null);
        position(fremd, 1, "Flachstahl 50x5", null, null, null);

        em.flush();
        em.clear();
    }

    @Test
    void findetMaterialUeberAlleTypen() {
        List<PositionsSuchtreffer> treffer = suche(lieferant.getId(), ALLE, "50 x 5");

        assertThat(treffer).extracting(PositionsSuchtreffer::dokumentId)
                .containsExactlyInAnyOrder(zeugnis.getId(), rechnung.getId());
    }

    @Test
    void alleWoerterInDerselbenPosition() {
        assertThat(suche(lieferant.getId(), ALLE, "flachstahl 123456"))
                .extracting(PositionsSuchtreffer::positionNr).containsExactly(1);
        // "Rundrohr" und "123456" stehen in verschiedenen Positionen
        assertThat(suche(lieferant.getId(), ALLE, "rundrohr 123456")).isEmpty();
    }

    @Test
    void findetChargeUndWerkstoff() {
        assertThat(suche(lieferant.getId(), ALLE, "777888"))
                .extracting(PositionsSuchtreffer::bezeichnung).containsExactly("Rundrohr");
        assertThat(suche(lieferant.getId(), ALLE, "s355"))
                .extracting(PositionsSuchtreffer::charge).containsExactly("777888");
    }

    @Test
    void filtertTypenUndLieferant() {
        assertThat(suche(lieferant.getId(), Set.of(LieferantDokumentTyp.RECHNUNG), "50x5"))
                .extracting(PositionsSuchtreffer::dokumentId).containsExactly(rechnung.getId());
        assertThat(suche(null, ALLE, "flachstahl")).hasSize(2);
    }

    @Test
    void likeSonderzeichenSindMaskiert() {
        assertThat(suche(lieferant.getId(), ALLE, "5%_a")).extracting(PositionsSuchtreffer::positionNr)
                .containsExactly(2);
        assertThat(suche(lieferant.getId(), ALLE, "%")).isEmpty();
        assertThat(suche(lieferant.getId(), ALLE, "'; DROP TABLE lieferant_dokument_position; --")).isEmpty();
        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void filtertNachBelegdatum() {
        // Zeugnis vom 01.09., Rechnung vom 15.10.2026 (siehe setUp)
        assertThat(suche(lieferant.getId(), ALLE, "50x5", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .extracting(PositionsSuchtreffer::dokumentId).containsExactly(rechnung.getId());
        assertThat(suche(lieferant.getId(), ALLE, "50x5", null, LocalDate.of(2026, 9, 30)))
                .extracting(PositionsSuchtreffer::dokumentId).containsExactly(zeugnis.getId());
    }

    @Test
    void ohneBelegdatumZaehltDasEingangsdatum() {
        LieferantDokument ohneDatum = dokument(lieferant, LieferantDokumentTyp.WERKSTOFFZEUGNIS, null);
        ohneDatum.setUploadDatum(LocalDateTime.of(2026, 8, 14, 9, 0));
        position(ohneDatum, 1, "Vierkantrohr", "S235JRH", "445566", "40x40x3");
        em.flush();
        em.clear();

        assertThat(suche(lieferant.getId(), ALLE, "445566", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .extracting(PositionsSuchtreffer::dokumentId).containsExactly(ohneDatum.getId());
        assertThat(suche(lieferant.getId(), ALLE, "445566", LocalDate.of(2026, 9, 1), null)).isEmpty();
    }

    @Test
    void suchtextWirdBeimSpeichernGebildet() {
        LieferantDokumentPosition p = repository.findByGeschaeftsdokumentId(zeugnis.getId()).get(0);
        assertThat(p.getSuchtext()).isEqualTo("flachstahl s235jr+ar 123456 50x5");
    }

    private List<PositionsSuchtreffer> suche(Long lieferantId, Set<LieferantDokumentTyp> typen, String eingabe) {
        return suche(lieferantId, typen, eingabe, null, null);
    }

    private List<PositionsSuchtreffer> suche(Long lieferantId, Set<LieferantDokumentTyp> typen, String eingabe,
            LocalDate von, LocalDate bis) {
        List<String> w = PositionsSuchtext.suchwoerter(eingabe);
        String[] m = new String[5];
        for (int i = 0; i < w.size(); i++) {
            m[i] = PositionsSuchtext.enthaeltMuster(w.get(i));
        }
        return repository.suche(typen, lieferantId, von, bis, m[0], m[1], m[2], m[3], m[4], PageRequest.of(0, 50));
    }

    private Lieferanten lieferant(String name) {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname(name);
        em.persist(l);
        return l;
    }

    private LieferantDokument dokument(Lieferanten owner, LieferantDokumentTyp typ, LocalDate datum) {
        LieferantDokument doc = new LieferantDokument();
        doc.setLieferant(owner);
        doc.setTyp(typ);
        doc.setOriginalDateiname("beleg.pdf");
        doc.setUploadDatum(LocalDateTime.now());
        em.persist(doc);
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setDokument(doc);
        g.setDokumentNummer("NR-" + typ.name());
        g.setDokumentDatum(datum);
        doc.setGeschaeftsdaten(g);
        em.persist(g);
        return doc;
    }

    private void position(LieferantDokument doc, int nr, String bezeichnung, String werkstoff, String charge,
            String abmessung) {
        LieferantDokumentPosition p = new LieferantDokumentPosition();
        p.setGeschaeftsdokument(doc.getGeschaeftsdaten());
        p.setPositionNr(nr);
        p.setBezeichnung(bezeichnung);
        p.setWerkstoff(werkstoff);
        p.setCharge(charge);
        p.setAbmessung(abmessung);
        p.setMenge(BigDecimal.ONE);
        em.persist(p);
    }
}
