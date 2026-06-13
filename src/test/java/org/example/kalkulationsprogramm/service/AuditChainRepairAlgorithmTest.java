package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministischer, CI-unabhängiger Test der Rebuild-Algorithmik gegen eine frische
 * In-Memory-H2 (MySQL-Modus). Im Gegensatz zu {@code AuditChainRepairIntegrationTest}
 * (läuft gegen die lokale MySQL-Kopie und kann bei leerer DB zum No-Op werden) legt
 * dieser Test seine eigenen Dummy-Einträge an — er prüft die Logik immer, ohne
 * Abhängigkeit von vorhandenen Realdaten oder lokaler Infrastruktur.
 *
 * <p>Die DB-spezifische Mikrosekunden-Trunkierung (MySQL {@code DATETIME(6)}) ist NICHT
 * Gegenstand dieses Tests — die wird im MySQL-Integrationstest geprüft. Hier geht es um
 * die reine Verkettungs-/Guard-Logik des Rebuilds.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditChainRepairService.class, AuditChainVerifier.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-rebuild;MODE=MySQL;DB_CLOSE_DELAY=-1;"
                + "DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class AuditChainRepairAlgorithmTest {

    @Autowired
    private AuditChainRepairService repairService;

    @Autowired
    private AuditChainVerifier verifier;

    @Autowired
    private AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @Autowired
    private AuditChainStateRepository chainStateRepository;

    @Test
    void rebuildHeiltKaputteHashesBeiLueckenloserKette() {
        gibChainState();
        // Drei lückenlose Einträge (0,1,2) mit ABSICHTLICH falschen Hashes — wie nach dem
        // Truncation-Bug: Inhalt korrekt, gespeicherter entry_hash nicht reproduzierbar.
        auditRepository.saveAndFlush(dummy(0, "RE-2026/01/00001", "FALSCH-0", "FALSCH-PREV"));
        auditRepository.saveAndFlush(dummy(1, "RE-2026/01/00002", "FALSCH-1", "FALSCH-0"));
        auditRepository.saveAndFlush(dummy(2, "RE-2026/01/00003", "FALSCH-2", "FALSCH-1"));

        assertThat(verifier.verify().isIntakt())
                .as("Vorbedingung: Kette mit falschen Hashes ist gebrochen")
                .isFalse();

        int anzahl = repairService.rebuildChain();

        assertThat(anzahl).isEqualTo(3);
        assertThat(verifier.verify().isIntakt())
                .as("Nach Rebuild muss die Kette intakt sein")
                .isTrue();
        // Erster Eintrag hat previous_hash == null, die folgenden zeigen aufeinander.
        var alle = auditRepository.findAllByOrderByChainIndexAsc();
        assertThat(alle.get(0).getPreviousHash()).isNull();
        assertThat(alle.get(1).getPreviousHash()).isEqualTo(alle.get(0).getEntryHash());
        assertThat(alle.get(2).getPreviousHash()).isEqualTo(alle.get(1).getEntryHash());
    }

    @Test
    void rebuildBrichtBeiEchterIndexLueckeAb() {
        gibChainState();
        // Lücke: 0, 1, 3 — Eintrag 2 fehlt (z.B. gelöschte Zeile = Manipulationsverdacht).
        auditRepository.saveAndFlush(dummy(0, "RE-2026/01/00001", "h0", null));
        auditRepository.saveAndFlush(dummy(1, "RE-2026/01/00002", "h1", "h0"));
        auditRepository.saveAndFlush(dummy(3, "RE-2026/01/00004", "h3", "h1"));

        assertThatThrownBy(() -> repairService.rebuildChain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht lückenlos");

        // Nichts wurde stillschweigend „geheilt".
        assertThat(verifier.verify().isIntakt()).isFalse();
    }

    private void gibChainState() {
        AuditChainState state = new AuditChainState();
        state.setId(1);
        state.setLastChainIndex(-1L);
        state.setLastEntryHash(null);
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);
    }

    private AusgangsGeschaeftsDokumentAudit dummy(long index, String nummer,
                                                  String entryHash, String previousHash) {
        AusgangsGeschaeftsDokumentAudit a = new AusgangsGeschaeftsDokumentAudit();
        a.setChainIndex(index);
        a.setDokumentId(100L + index);
        a.setAktion(AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT);
        a.setDokumentNummer(nummer);
        a.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        a.setDatum(LocalDate.of(2026, 1, 1));
        a.setBetreff("Auftrag Max Mustermann");
        a.setBetragNetto(new BigDecimal("100.00"));
        a.setBetragBrutto(new BigDecimal("119.00"));
        a.setMwstSatz(new BigDecimal("0.1900"));
        a.setInhaltHash("a".repeat(64));
        a.setGeaendertAm(LocalDateTime.of(2026, 1, 1, 10, 0, 0).plusMinutes(index));
        a.setAenderungsgrund("Initiale Erstellung");
        a.setPreviousHash(previousHash);
        a.setEntryHash(entryHash);
        return a;
    }
}
