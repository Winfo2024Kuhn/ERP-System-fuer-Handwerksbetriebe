package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZugferdErstellServiceTest {

    private ZugferdErstellService service;

    @BeforeEach
    void setUp() {
        service = new ZugferdErstellService();
    }

    @Nested
    class NormalizeDokumentenart {

        @Test
        void wirftRuntimeExceptionBeiNichtExistierendemPdf() {
            var daten = new org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");
            daten.setKundenName("Test GmbH");
            daten.setBetrag(new java.math.BigDecimal("1190.00"));

            assertThatThrownBy(() -> service.erzeuge("nicht/existierend.pdf", daten))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ZUGFeRD Erstellung fehlgeschlagen");
        }
    }

    @Nested
    class ErstellungMitNullWerten {

        @Test
        void wirftExceptionBeiUngueltigemPfad() {
            var daten = new org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten();
            // Alle Felder null belassen

            assertThatThrownBy(() -> service.erzeuge("/tmp/nonexistent.pdf", daten))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
