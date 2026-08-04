package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.example.kalkulationsprogramm.domain.BelegAuditAktion;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.repository.BelegAuditRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.BDDMockito.given;

/**
 * Tests fuer die Pruefung der Protokollkette.
 *
 * <p>Jeder Test beschreibt einen Angriff auf das Kassenbuch: einen Eintrag
 * loeschen, einen Eintrag nachtraeglich veraendern, die Verkettung
 * aufbrechen. Der Verifier muss jeden davon finden -- genau das ist der
 * Nachweis, den ein Steuerpruefer sehen will.</p>
 *
 * <p>DSGVO: nur Dummy-Daten.</p>
 */
@ExtendWith(MockitoExtension.class)
class BelegAuditChainVerifierTest {

    @Mock private BelegAuditRepository auditRepository;

    @InjectMocks private BelegAuditChainVerifier verifier;

    @Test
    @DisplayName("Leeres Protokoll gilt als unversehrt")
    void leeresProtokollIstIntakt() {
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(List.of());

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isTrue();
        assertThat(b.getGesamtAnzahl()).isZero();
    }

    @Test
    @DisplayName("Saubere Kette wird als unversehrt erkannt")
    void sauberKetteIstIntakt() {
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(kette(5));

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isTrue();
        assertThat(b.getGesamtAnzahl()).isEqualTo(5);
        assertThat(b.getLetzterChainIndex()).isEqualTo(4L);
        assertThat(b.getFehler()).isEmpty();
    }

    @Test
    @DisplayName("Gelöschter Eintrag hinterlässt eine Lücke und wird gefunden")
    void geloeschterEintragWirdGefunden() {
        List<BelegAudit> kette = kette(5);
        kette.remove(2); // Position 2 verschwindet -- so, wie es ein Manipulator täte
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(kette);

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler()).hasSize(1);
        assertThat(b.getFehler().get(0).typ()).isEqualTo("LUECKE_IN_DER_KETTE");
    }

    @Test
    @DisplayName("Nachträglich geänderter Betrag wird gefunden")
    void manipulierterEintragWirdGefunden() {
        List<BelegAudit> kette = kette(4);
        // Der klassische Fall: jemand macht aus einer Ausgabe von 100 € eine
        // von 10 €, um Bargeld verschwinden zu lassen.
        kette.get(1).setBetragBrutto(new BigDecimal("10.00"));
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(kette);

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler().get(0).typ()).isEqualTo("EINTRAG_VERAENDERT");
        assertThat(b.getFehler().get(0).chainIndex()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Aufgebrochene Verkettung wird gefunden")
    void gebrocheneVerkettungWirdGefunden() {
        List<BelegAudit> kette = kette(4);
        // Vorgänger-Verweis umbiegen und den eigenen Hash passend nachziehen:
        // der Eintrag selbst ist dann in sich stimmig, hängt aber nicht mehr
        // an seinem Vorgänger.
        BelegAudit dritter = kette.get(2);
        dritter.setPreviousHash("0".repeat(64));
        dritter.setEntryHash(dritter.computeEntryHash());
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(kette);

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler().get(0).typ()).isEqualTo("KETTE_GEBROCHEN");
    }

    @Test
    @DisplayName("Fehlende Kettenposition wird als Lücke gemeldet")
    void fehlendeKettenpositionWirdGemeldet() {
        List<BelegAudit> kette = kette(2);
        kette.get(1).setChainIndex(null);
        given(auditRepository.findAllByOrderByChainIndexAsc()).willReturn(kette);

        BelegAuditChainVerifier.Bericht b = verifier.verify();

        assertThat(b.isIntakt()).isFalse();
        assertThat(b.getFehler().get(0).typ()).isEqualTo("LUECKE_IN_DER_KETTE");
    }

    // ===================== Hilfen =====================

    /** Baut eine korrekt verkettete Folge von Protokolleintraegen. */
    private List<BelegAudit> kette(int laenge) {
        List<BelegAudit> kette = new ArrayList<>(laenge);
        String vorgaenger = null;
        for (int i = 0; i < laenge; i++) {
            BelegAudit a = new BelegAudit();
            a.setId((long) i + 1);
            a.setChainIndex((long) i);
            a.setBelegId((long) i + 100);
            a.setAktion(BelegAuditAktion.ERFASST);
            a.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
            a.setBelegDatum(LocalDate.of(2026, 3, i + 1));
            a.setBeschreibung("Buchung " + i);
            a.setBetragBrutto(new BigDecimal("100.00"));
            a.setGeaendertAm(LocalDateTime.of(2026, 3, i + 1, 10, 0));
            a.setPreviousHash(vorgaenger);
            a.setEntryHash(a.computeEntryHash());
            vorgaenger = a.getEntryHash();
            kette.add(a);
        }
        return kette;
    }
}
