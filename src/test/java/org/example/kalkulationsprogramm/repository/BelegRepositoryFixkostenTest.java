package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert
 * {@link BelegRepository#findValidierteFixkostenBelegeImZeitraum(LocalDate, LocalDate)}
 * gegen H2 -- die Quelle des Gemeinkosten-Topfs im Stundensatz-Rechner.
 *
 * Entscheidend ist der Ausschluss von Belegen, zu denen ein
 * {@link LieferantDokument} existiert: die laufen bereits ueber
 * LieferantDokumentProjektAnteil in die Gemeinkosten und wuerden sonst
 * doppelt zaehlen.
 */
@DataJpaTest
class BelegRepositoryFixkostenTest {

    private static final LocalDate VON = LocalDate.of(2024, 1, 1);
    private static final LocalDate BIS = LocalDate.of(2024, 12, 31);

    @Autowired private BelegRepository belegRepository;
    @Autowired private KostenstelleRepository kostenstelleRepository;
    @Autowired private LieferantDokumentRepository lieferantDokumentRepository;
    @Autowired private LieferantenRepository lieferantenRepository;

    @Test
    @DisplayName("Validierter Fixkosten-Beleg im Zeitraum wird geliefert")
    void fixkostenBelegWirdGeliefert() {
        saveBeleg(saveKostenstelle("Telefon", true), BelegStatus.VALIDIERT, LocalDate.of(2024, 6, 15));

        assertThat(belegRepository.findValidierteFixkostenBelegeImZeitraum(VON, BIS)).hasSize(1);
    }

    @Test
    @DisplayName("Beleg mit Lieferanten-Dokument wird ausgeschlossen - sonst zaehlt er doppelt")
    void belegMitLieferantDokumentWirdAusgeschlossen() {
        Beleg beleg = saveBeleg(saveKostenstelle("Strom", true), BelegStatus.VALIDIERT,
                LocalDate.of(2024, 6, 15));
        saveLieferantDokumentZu(beleg);

        assertThat(belegRepository.findValidierteFixkostenBelegeImZeitraum(VON, BIS)).isEmpty();
    }

    @Test
    @DisplayName("Ein Lieferanten-Dokument OHNE Beleg schliesst andere Belege nicht aus")
    void freistehendesLieferantDokumentStoertNicht() {
        saveBeleg(saveKostenstelle("Miete", true), BelegStatus.VALIDIERT, LocalDate.of(2024, 3, 1));
        saveLieferantDokumentZu(null);

        assertThat(belegRepository.findValidierteFixkostenBelegeImZeitraum(VON, BIS)).hasSize(1);
    }

    @Test
    @DisplayName("Nicht validierte Belege und Nicht-Fixkosten-Stellen bleiben draussen")
    void nurValidierteFixkosten() {
        saveBeleg(saveKostenstelle("Buero", true), BelegStatus.NEU, LocalDate.of(2024, 6, 15));
        saveBeleg(saveKostenstelle("Projekt", false), BelegStatus.VALIDIERT, LocalDate.of(2024, 6, 15));

        assertThat(belegRepository.findValidierteFixkostenBelegeImZeitraum(VON, BIS)).isEmpty();
    }

    @Test
    @DisplayName("Belege ausserhalb des Jahres bleiben draussen")
    void ausserhalbDesZeitraums() {
        Kostenstelle ks = saveKostenstelle("Versicherung", true);
        saveBeleg(ks, BelegStatus.VALIDIERT, LocalDate.of(2023, 12, 31));
        saveBeleg(ks, BelegStatus.VALIDIERT, LocalDate.of(2025, 1, 1));

        assertThat(belegRepository.findValidierteFixkostenBelegeImZeitraum(VON, BIS)).isEmpty();
    }

    private Kostenstelle saveKostenstelle(String name, boolean istFixkosten) {
        Kostenstelle ks = new Kostenstelle();
        ks.setBezeichnung(name);
        ks.setTyp(KostenstellenTyp.GEMEINKOSTEN);
        ks.setIstFixkosten(istFixkosten);
        return kostenstelleRepository.saveAndFlush(ks);
    }

    private Beleg saveBeleg(Kostenstelle ks, BelegStatus status, LocalDate datum) {
        Beleg b = new Beleg();
        b.setStatus(status);
        b.setKostenstelle(ks);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
        b.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);
        b.setBelegDatum(datum);
        b.setUploadDatum(LocalDateTime.now());
        b.setBetragNetto(new BigDecimal("100.00"));
        b.setBetragBrutto(new BigDecimal("119.00"));
        return belegRepository.saveAndFlush(b);
    }

    private void saveLieferantDokumentZu(Beleg beleg) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterlieferant GmbH " + System.nanoTime());
        lieferant = lieferantenRepository.saveAndFlush(lieferant);

        LieferantDokument d = new LieferantDokument();
        d.setLieferant(lieferant);
        d.setTyp(LieferantDokumentTyp.RECHNUNG);
        d.setUploadDatum(LocalDateTime.now());
        d.setBeleg(beleg);
        lieferantDokumentRepository.saveAndFlush(d);
    }
}
