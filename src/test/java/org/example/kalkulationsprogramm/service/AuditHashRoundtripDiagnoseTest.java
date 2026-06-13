package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstest für den Hash-Ketten-Roundtrip-Bug.
 *
 * <p>Ursache des Bugs: entry_hash wurde über den In-Memory-Zustand VOR dem INSERT
 * berechnet. MySQL {@code DATETIME(6)} trunkiert aber die Nanosekunden von
 * {@code LocalDateTime.now()} — beim späteren Verifizieren rechnet der Verifier über
 * den trunkierten Wert und meldet fälschlich EINTRAG_MANIPULIERT.</p>
 *
 * <p>Läuft gegen die lokale MySQL (Profil {@code local}) und rollt am Ende zurück.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver",
})
class AuditHashRoundtripDiagnoseTest {

    @Autowired
    private AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @Autowired
    private EntityManager em;

    /**
     * Reproduziert den ursprünglichen Bug: Roh-Nanosekunden überleben den DB-Roundtrip
     * NICHT — die kanonische Form (und damit der Hash) ändert sich. Dieser Test
     * dokumentiert, warum vor dem INSERT gerechneter Hash die Kette bricht.
     */
    @Test
    void rohNanosekundenUeberlebenDbRoundtripNicht() {
        AusgangsGeschaeftsDokumentAudit a = baseAudit();
        a.setGeaendertAm(LocalDateTime.now()); // ungetrimmt -> bis zu 9 Nachkommastellen

        // Nur aussagekräftig, wenn der Zeitstempel überhaupt Sub-Mikrosekunden trägt.
        if (a.getGeaendertAm().getNano() % 1000 == 0) {
            return;
        }
        String canonicalVorher = a.canonicalForm();

        auditRepository.saveAndFlush(a);
        em.clear();
        AusgangsGeschaeftsDokumentAudit neu = auditRepository.findById(a.getId()).orElseThrow();

        assertThat(neu.canonicalForm())
                .as("Roh-Nanosekunden werden von DATETIME(6) trunkiert -> Form ändert sich")
                .isNotEqualTo(canonicalVorher);
    }

    /**
     * Sichert den Fix ab: Wird der Zeitstempel — wie in {@code fromDokument} — auf
     * Mikrosekunden getrimmt, überlebt die kanonische Form den DB-Roundtrip und der
     * Verifier rechnet denselben Hash nach.
     */
    @Test
    void getrimmterZeitstempelUeberlebtDbRoundtrip() {
        AusgangsGeschaeftsDokumentAudit a = baseAudit();
        a.setGeaendertAm(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        a.setPreviousHash(null);
        a.setEntryHash(a.computeEntryHash());

        String canonicalVorher = a.canonicalForm();
        auditRepository.saveAndFlush(a);
        em.clear();

        AusgangsGeschaeftsDokumentAudit neu = auditRepository.findById(a.getId()).orElseThrow();

        assertThat(neu.canonicalForm())
                .as("getrimmter Zeitstempel muss den DB-Roundtrip unverändert überleben")
                .isEqualTo(canonicalVorher);
        assertThat(neu.computeEntryHash())
                .as("nachgerechneter Hash muss zum gespeicherten passen")
                .isEqualTo(neu.getEntryHash());
    }

    private AusgangsGeschaeftsDokumentAudit baseAudit() {
        AusgangsGeschaeftsDokumentAudit a = new AusgangsGeschaeftsDokumentAudit();
        a.setChainIndex(999_999L);
        a.setDokumentId(0L);
        a.setAktion(AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT);
        a.setDokumentNummer("TEST-DIAG-00001");
        a.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        a.setDatum(LocalDate.of(2026, 1, 1));
        a.setBetreff("Diagnose Max Mustermann");
        a.setBetragNetto(new BigDecimal("100.00"));
        a.setBetragBrutto(new BigDecimal("119.00"));
        a.setMwstSatz(new BigDecimal("0.1900"));
        a.setInhaltHash("a".repeat(64));
        a.setAenderungsgrund("Roundtrip-Diagnose");
        return a;
    }
}
