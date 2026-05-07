package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests für die Hash-Kette des Audit-Logs.
 *
 * <p>Geprüft werden:
 * <ul>
 *   <li>Eine korrekt verkettete Sequenz wird als intakt erkannt.</li>
 *   <li>Manipulation eines Feld-Wertes bricht die Kette an genau dieser Stelle.</li>
 *   <li>Eine Lücke im chain_index wird erkannt.</li>
 *   <li>Ein falscher previous_hash wird erkannt.</li>
 *   <li>Leeres Audit-Log gilt als intakt (Trivialfall).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditChainVerifierTest {

    @Mock
    private AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @InjectMocks
    private AuditChainVerifier verifier;

    private List<AusgangsGeschaeftsDokumentAudit> kette;

    @BeforeEach
    void setUp() {
        kette = new ArrayList<>();
        String previous = null;
        for (int i = 0; i < 3; i++) {
            AusgangsGeschaeftsDokumentAudit a = baseAudit(i);
            a.setPreviousHash(previous);
            a.setEntryHash(a.computeEntryHash());
            previous = a.getEntryHash();
            kette.add(a);
        }
    }

    @Test
    void leereKetteIstIntakt() {
        when(auditRepository.findAllByOrderByChainIndexAsc()).thenReturn(List.of());
        AuditChainVerifier.Bericht b = verifier.verify();
        assertThat(b.isIntakt()).isTrue();
        assertThat(b.getGesamtAnzahl()).isZero();
    }

    @Test
    void korrekteKetteIstIntakt() {
        when(auditRepository.findAllByOrderByChainIndexAsc()).thenReturn(kette);
        AuditChainVerifier.Bericht b = verifier.verify();
        assertThat(b.isIntakt()).isTrue();
        assertThat(b.getGesamtAnzahl()).isEqualTo(3);
        assertThat(b.getLetzterChainIndex()).isEqualTo(2);
        assertThat(b.getLetzterEntryHash()).isEqualTo(kette.get(2).getEntryHash());
    }

    @Test
    void manipulierterBetragWirdErkannt() {
        // Angreifer ändert den Betrag der mittleren Rechnung — entryHash bleibt aber alt.
        kette.get(1).setBetragNetto(new BigDecimal("99999.99"));
        when(auditRepository.findAllByOrderByChainIndexAsc()).thenReturn(kette);

        AuditChainVerifier.Bericht b = verifier.verify();
        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler()).hasSize(1);
        assertThat(b.getFehler().get(0).chainIndex()).isEqualTo(1L);
        assertThat(b.getFehler().get(0).beschreibung()).contains("Hash stimmt nicht");
    }

    @Test
    void luckeImChainIndexWirdErkannt() {
        // Eintrag 1 wird gelöscht — chain_index springt 0 -> 2.
        kette.remove(1);
        when(auditRepository.findAllByOrderByChainIndexAsc()).thenReturn(kette);

        AuditChainVerifier.Bericht b = verifier.verify();
        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler().get(0).beschreibung()).contains("Erwartet chain_index=1");
    }

    @Test
    void falscherPreviousHashWirdErkannt() {
        kette.get(2).setPreviousHash("0".repeat(64));
        // entryHash neu berechnen, damit nur previousHash unstimmig ist (nicht der eigene Hash):
        kette.get(2).setEntryHash(kette.get(2).computeEntryHash());
        when(auditRepository.findAllByOrderByChainIndexAsc()).thenReturn(kette);

        AuditChainVerifier.Bericht b = verifier.verify();
        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler().get(0).typ()).isEqualTo("KETTE_GEBROCHEN");
    }

    private AusgangsGeschaeftsDokumentAudit baseAudit(int i) {
        AusgangsGeschaeftsDokumentAudit a = new AusgangsGeschaeftsDokumentAudit();
        a.setChainIndex((long) i);
        a.setDokumentId(100L + i);
        a.setAktion(AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT);
        a.setDokumentNummer("2026/01/0000" + (i + 1));
        a.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        a.setDatum(LocalDate.of(2026, 1, 1).plusDays(i));
        a.setBetragNetto(new BigDecimal("1000.00"));
        a.setBetragBrutto(new BigDecimal("1190.00"));
        a.setMwstSatz(new BigDecimal("0.1900"));
        a.setGeaendertAm(LocalDateTime.of(2026, 1, 1, 10, 0).plusMinutes(i));
        a.setInhaltHash("a".repeat(64));
        return a;
    }
}
