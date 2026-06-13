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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert die Ketten-Reparatur gegen die ECHTEN lokalen Audit-Daten (Kopie der
 * Produktiv-DB). Vor dem Fix ist die Kette gebrochen; nach {@code rebuildChain()} muss
 * der Verifier sie als intakt melden.
 *
 * <p>{@code @DataJpaTest} rollt die Transaktion am Ende zurück — die lokale DB wird
 * also NICHT verändert. Der Test belegt nur, dass der Rebuild auf realen Daten
 * funktioniert, bevor er auf Produktiv angewendet wird.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import({AuditChainRepairService.class, AuditChainVerifier.class,
        AusgangsGeschaeftsDokumentAuditService.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver",
})
class AuditChainRepairIntegrationTest {

    @Autowired
    private AuditChainRepairService repairService;

    @Autowired
    private AuditChainVerifier verifier;

    @Autowired
    private AusgangsGeschaeftsDokumentAuditService auditService;

    @Autowired
    private AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @Autowired
    private EntityManager em;

    @Test
    void rebuildMachtEchteLokaleKetteIntakt() {
        long anzahlMitIndex = auditRepository.findAllByOrderByChainIndexAsc().stream()
                .filter(a -> a.getChainIndex() != null)
                .count();

        // Vorbedingung: ohne Daten ist der Test bedeutungslos.
        org.junit.jupiter.api.Assumptions.assumeTrue(anzahlMitIndex > 0,
                "Keine verketteten Audit-Einträge in der lokalen DB — Test übersprungen.");

        int neuVerkettet = repairService.rebuildChain();
        assertThat(neuVerkettet).isEqualTo((int) anzahlMitIndex);

        AuditChainVerifier.Bericht bericht = verifier.verify();
        assertThat(bericht.isIntakt())
                .as("Kette muss nach Rebuild intakt sein. Erste Bruchstelle: "
                        + (bericht.getFehler().isEmpty() ? "—" : bericht.getFehler().get(0)))
                .isTrue();
    }

    /**
     * Deterministische Regression für den Roundtrip-Fix: Ein Eintrag mit fixem
     * Sub-Mikrosekunden-Zeitstempel wird über den echten {@code appendToChain}-Pfad
     * geschrieben. MySQL {@code DATETIME(6)} verliert die Nanosekunden — weil der Hash
     * aber über die per {@code refresh} zurückgelesene DB-Form berechnet wird, muss
     * das Nachrechnen exakt passen. Bewusst KEIN {@code now()}, damit der Test nicht
     * von der Laufzeit-Präzision abhängt.
     */
    @Test
    void appendToChainHashIstNachRoundtripReproduzierbar() {
        AusgangsGeschaeftsDokumentAudit audit = new AusgangsGeschaeftsDokumentAudit();
        audit.setDokumentId(0L);
        audit.setAktion(AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT);
        audit.setDokumentNummer("TEST-DET-00001");
        audit.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        audit.setDatum(LocalDate.of(2026, 1, 1));
        audit.setBetreff("Determinismus Max Mustermann");
        audit.setBetragNetto(new BigDecimal("100.00"));
        audit.setBetragBrutto(new BigDecimal("119.00"));
        audit.setMwstSatz(new BigDecimal("0.1900"));
        audit.setInhaltHash("a".repeat(64));
        audit.setAenderungsgrund("Determinismus-Test");
        // Fixer Wert mit Nanosekunden jenseits der Mikrosekunden-Grenze.
        audit.setGeaendertAm(LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123_456_789));

        AusgangsGeschaeftsDokumentAudit saved = auditService.appendToChain(audit);
        Long id = saved.getId();
        em.flush();
        em.clear();

        AusgangsGeschaeftsDokumentAudit neu = auditRepository.findById(id).orElseThrow();

        assertThat(neu.getGeaendertAm().getNano() % 1000)
                .as("DATETIME(6) hält nur Mikrosekunden — Sub-µs muss weg sein")
                .isZero();
        assertThat(neu.computeEntryHash())
                .as("Hash über die gespeicherte Form muss reproduzierbar sein")
                .isEqualTo(neu.getEntryHash());
    }
}
