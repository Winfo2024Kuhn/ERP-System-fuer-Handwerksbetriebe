package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sichert den Kern des Umbaus ab: Die Preisuebernahme haengt am Commit der
 * Dokumentanalyse.
 *
 * <p>Die uebrigen Tests rufen {@code uebernehmePreise} unmittelbar auf und sagen
 * damit nichts ueber die Verdrahtung. Hier laeuft der echte Weg - ein
 * veroeffentlichtes {@link PreisUebernahmeEvent} in einer echten Transaktion -
 * und deckt die drei Faelle ab, die den Umbau ausmachen:
 *
 * <ul>
 * <li>Commit: der Preisstand entsteht, aber erst <b>nach</b> dem Commit.</li>
 * <li>Rollback: kein Preisstand. Das war der urspruengliche Fehler - im
 *     Preisverlauf stand ein Preis fuer einen Beleg, den es in der Datenbank
 *     nie gab.</li>
 * <li>Ohne Transaktion: Spring verwirft das Event stillschweigend
 *     ({@code fallbackExecution} bleibt aus). Auch das ist gewollt und soll
 *     nicht unbemerkt kippen, wenn jemand die Annotation aendert.</li>
 * </ul>
 *
 * <p>Wie {@code PreisUebernahmeServiceTest} laeuft die Klasse <b>ohne</b> die
 * Rollback-Klammer von {@code @DataJpaTest}: Der Testrumpf muss selbst
 * entscheiden, wann committet wird, sonst gibt es kein {@code AFTER_COMMIT}.
 */
@DataJpaTest
@Import({PreisUebernahmeService.class, PreisUebernahmeEventListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreisUebernahmeNachCommitTest {

    /** Aelter als jeder Beleg in diesen Tests. */
    private static final Date BESTANDSSTAND = new Date(0);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transaktionsverwaltung;

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Autowired
    private LieferantenArtikelPreiseRepository artikelPreiseRepository;

    @Test
    void preisstandEntstehtErstNachDemCommitDerAnalyse() {
        Lieferanten lieferant = lieferant("Musterlieferant Commit");
        Artikel artikel = artikelMitPreis(lieferant, "TX-1", "10.00");

        neueTransaktion().executeWithoutResult(status -> {
            eventPublisher.publishEvent(event(lieferant, "TX-1", "12.00"));

            // Noch mitten in der Analyse-Transaktion: Der Listener darf hier noch
            // nichts geschrieben haben, sonst haengt die Uebernahme wieder am
            // Zwischenstand statt am Ergebnis.
            assertEquals(1, artikelPreiseRepository.findeVerlauf(artikel.getId()).size(),
                    "Vor dem Commit darf noch kein neuer Preisstand existieren");
        });

        List<LieferantenArtikelPreise> verlauf = artikelPreiseRepository.findeVerlauf(artikel.getId());
        assertEquals(2, verlauf.size(), "Nach dem Commit muss der neue Preisstand da sein");
        assertEquals(0, new BigDecimal("12.00").compareTo(aktuellerPreis(artikel)));
    }

    @Test
    void rollbackDerAnalyseHinterlaesstKeinenPreisstand() {
        Lieferanten lieferant = lieferant("Musterlieferant Rollback");
        Artikel artikel = artikelMitPreis(lieferant, "TX-2", "10.00");

        neueTransaktion().executeWithoutResult(status -> {
            eventPublisher.publishEvent(event(lieferant, "TX-2", "12.00"));
            // Die Analyse scheitert nach dem Auslesen - z.B. weil das Dokument
            // gegen eine Fremdschluesselbedingung laeuft.
            status.setRollbackOnly();
        });

        assertEquals(1, artikelPreiseRepository.findeVerlauf(artikel.getId()).size(),
                "Ein zurueckgerollter Beleg darf keinen Preisstand hinterlassen");
        assertEquals(0, new BigDecimal("10.00").compareTo(aktuellerPreis(artikel)));
    }

    @Test
    void ohneLaufendeTransaktionWirdDasEventVerworfen() {
        Lieferanten lieferant = lieferant("Musterlieferant Transaktionslos");
        Artikel artikel = artikelMitPreis(lieferant, "TX-3", "10.00");

        eventPublisher.publishEvent(event(lieferant, "TX-3", "12.00"));

        assertEquals(1, artikelPreiseRepository.findeVerlauf(artikel.getId()).size(),
                "Ohne Transaktion gibt es keinen Commit, an den die Uebernahme haengen koennte");
        assertEquals(0, new BigDecimal("10.00").compareTo(aktuellerPreis(artikel)));
    }

    // ------------------------------------------------------------------
    // Hilfsmittel
    // ------------------------------------------------------------------

    private TransactionTemplate neueTransaktion() {
        return new TransactionTemplate(transaktionsverwaltung);
    }

    private PreisUebernahmeEvent event(Lieferanten lieferant, String nummer, String preis) {
        return new PreisUebernahmeEvent(lieferant, PreisQuelle.RECHNUNG, new Date(), "RE-2026-0815",
                List.of(new PreisUebernahmeService.Position(nummer, new BigDecimal(preis), "1 C62", "C62")));
    }

    private Lieferanten lieferant(String name) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname(name);
        return lieferantenRepository.save(lieferant);
    }

    private Artikel artikelMitPreis(Lieferanten lieferant, String externeNummer, String preis) {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        LieferantenArtikelPreise stand = new LieferantenArtikelPreise();
        stand.setArtikel(artikel);
        stand.setLieferant(lieferant);
        stand.setExterneArtikelnummer(externeNummer);
        stand.setPreis(new BigDecimal(preis));
        stand.setPreisAenderungsdatum(BESTANDSSTAND);
        stand.setQuelle(PreisQuelle.MANUELL);
        artikel.getArtikelpreis().add(stand);

        return artikelRepository.save(artikel);
    }

    private BigDecimal aktuellerPreis(Artikel artikel) {
        return artikelPreiseRepository.findeVerlauf(artikel.getId()).stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .findFirst()
                .orElseThrow()
                .getPreis();
    }
}
