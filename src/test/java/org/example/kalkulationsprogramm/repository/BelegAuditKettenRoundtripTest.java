package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.example.kalkulationsprogramm.domain.BelegAuditAktion;
import org.example.kalkulationsprogramm.domain.BelegAuditChainState;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.service.BelegAuditChainVerifier;
import org.example.kalkulationsprogramm.service.BelegAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueft die Hash-Kette gegen eine echte Datenbank statt gegen Mocks.
 *
 * <p>Das ist die Stelle, an der die Manipulationssicherung praktisch scheitern
 * kann: Der Hash wird ueber die Form gebildet, die nach dem Insert in der
 * Datenbank steht. Normalisiert die Datenbank dabei irgendetwas — DECIMAL auf
 * zwei Nachkommastellen, DATETIME auf Mikrosekunden, gekuerzte Strings — und
 * der Code rechnet trotzdem mit dem Zustand aus dem Arbeitsspeicher, dann
 * meldet der Verifier spaeter eine Manipulation, die es nie gab. Das faellt in
 * reinen Mockito-Tests grundsaetzlich nicht auf, weil dort nie eine Datenbank
 * beteiligt ist.</p>
 *
 * <p>Deshalb wird hier bewusst der Persistence-Context geleert, bevor geprueft
 * wird: Nur so kommen die Eintraege wirklich frisch aus der Datenbank und
 * nicht aus dem Cache der laufenden Transaktion.</p>
 *
 * <p>DSGVO: nur Dummy-Daten.</p>
 */
@DataJpaTest
@Import({ BelegAuditService.class, BelegAuditChainVerifier.class })
class BelegAuditKettenRoundtripTest {

    @Autowired private BelegAuditService auditService;
    @Autowired private BelegAuditChainVerifier verifier;
    @Autowired private BelegRepository belegRepository;
    @Autowired private BelegAuditRepository auditRepository;
    @Autowired private BelegAuditChainStateRepository chainStateRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void kettenkopfAnlegen() {
        // Im Test laeuft keine Flyway-Migration (ddl-auto), also legen wir die
        // Singleton-Zeile hier an -- in echt macht das V351.
        if (chainStateRepository.findById(1).isEmpty()) {
            BelegAuditChainState state = new BelegAuditChainState();
            state.setId(1);
            state.setLastChainIndex(-1L);
            state.setLastLaufendeNummer(0L);
            state.setUpdatedAt(LocalDateTime.now());
            chainStateRepository.saveAndFlush(state);
        }
    }

    @Test
    @DisplayName("Die Kette bleibt nach dem Datenbank-Roundtrip nachrechenbar")
    void ketteUeberlebtRoundtrip() {
        Beleg beleg = beleg(new BigDecimal("19.99"));

        auditService.protokolliereErfassung(beleg, null, null);
        auditService.protokolliereValidierung(beleg, null, null);
        auditService.protokolliereFestschreibung(beleg, null, "Monatsabschluss März 2026", null);

        // Cache leeren: die Eintraege muessen frisch aus der Datenbank kommen,
        // sonst prueft der Verifier nur den Arbeitsspeicher gegen sich selbst.
        entityManager.flush();
        entityManager.clear();

        BelegAuditChainVerifier.Bericht bericht = verifier.verify();

        assertThat(bericht.isIntakt())
                .as("Fehler: %s", bericht.getFehler())
                .isTrue();
        assertThat(bericht.getGesamtAnzahl()).isEqualTo(3);
        assertThat(bericht.getLetzterChainIndex()).isEqualTo(2L);
        assertThat(bericht.getLetzterEntryHash()).hasSize(64);
    }

    @Test
    @DisplayName("Beträge mit mehr Nachkommastellen brechen den Hash nicht")
    void nachkommastellenBrechenDenHashNicht() {
        // Ein Betrag, den die Spalte DECIMAL(15,2) beim Speichern runden muss.
        // Wuerde der Hash vor dem Insert gerechnet, passte er hinterher nicht
        // mehr zum gespeicherten Wert.
        Beleg beleg = beleg(new BigDecimal("19.999"));

        auditService.protokolliereErfassung(beleg, null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(verifier.verify().isIntakt()).isTrue();
    }

    @Test
    @DisplayName("Ein nachträglich geänderter Eintrag wird nach dem Roundtrip erkannt")
    void manipulationWirdNachRoundtripErkannt() {
        Beleg beleg = beleg(new BigDecimal("100.00"));
        auditService.protokolliereErfassung(beleg, null, null);
        auditService.protokolliereValidierung(beleg, null, null);
        entityManager.flush();
        entityManager.clear();

        // Direkt an der Tabelle drehen -- so, wie es jemand mit
        // Datenbankzugriff tun wuerde, um Bargeld verschwinden zu lassen.
        entityManager.createQuery(
                        "UPDATE BelegAudit a SET a.betragBrutto = :neu WHERE a.chainIndex = 0")
                .setParameter("neu", new BigDecimal("10.00"))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        BelegAuditChainVerifier.Bericht bericht = verifier.verify();

        assertThat(bericht.isIntakt()).isFalse();
        assertThat(bericht.getFehler().get(0).typ()).isEqualTo("EINTRAG_VERAENDERT");
    }

    @Test
    @DisplayName("Ein gelöschter Eintrag hinterlässt eine erkennbare Lücke")
    void geloeschterEintragWirdNachRoundtripErkannt() {
        Beleg beleg = beleg(new BigDecimal("50.00"));
        auditService.protokolliereErfassung(beleg, null, null);
        auditService.protokolliereValidierung(beleg, null, null);
        auditService.protokolliereFestschreibung(beleg, null, "Abschluss", null);
        entityManager.flush();
        entityManager.clear();

        entityManager.createQuery("DELETE FROM BelegAudit a WHERE a.chainIndex = 1").executeUpdate();
        entityManager.flush();
        entityManager.clear();

        BelegAuditChainVerifier.Bericht bericht = verifier.verify();

        assertThat(bericht.isIntakt()).isFalse();
        assertThat(bericht.getFehler().get(0).typ()).isEqualTo("LUECKE_IN_DER_KETTE");
    }

    @Test
    @DisplayName("Laufende Nummern werden lückenlos und aufsteigend vergeben")
    void laufendeNummernSindLueckenlos() {
        assertThat(auditService.zieheNaechsteLaufendeNummer()).isEqualTo(1L);
        assertThat(auditService.zieheNaechsteLaufendeNummer()).isEqualTo(2L);
        assertThat(auditService.zieheNaechsteLaufendeNummer()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Jeder Eintrag verweist auf den Hash seines Vorgängers")
    void verkettungStimmt() {
        Beleg beleg = beleg(new BigDecimal("12.34"));
        BelegAudit erster = auditService.protokolliereErfassung(beleg, null, null);
        BelegAudit zweiter = auditService.protokolliereValidierung(beleg, null, null);
        entityManager.flush();
        entityManager.clear();

        var alle = auditRepository.findAllByOrderByChainIndexAsc();

        assertThat(alle).hasSize(2);
        assertThat(alle.get(0).getPreviousHash()).isNull();
        assertThat(alle.get(0).getEntryHash()).isEqualTo(erster.getEntryHash());
        assertThat(alle.get(1).getPreviousHash()).isEqualTo(alle.get(0).getEntryHash());
        assertThat(alle.get(1).getAktion()).isEqualTo(BelegAuditAktion.VALIDIERT);
        assertThat(alle.get(1).getEntryHash()).isEqualTo(zweiter.getEntryHash());
    }

    // ===================== Hilfen =====================

    private Beleg beleg(BigDecimal brutto) {
        Beleg b = new Beleg();
        b.setStatus(BelegStatus.VALIDIERT);
        b.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setBelegDatum(LocalDate.of(2026, 3, 14));
        b.setBeschreibung("Schrauben und Dübel");
        b.setBetragBrutto(brutto);
        b.setIstUmbuchung(true);
        b.setUploadDatum(LocalDateTime.now());
        return belegRepository.saveAndFlush(b);
    }
}
