package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ZugferdErstellServiceTest {

    private lateinit var service: ZugferdErstellService

    @BeforeEach
    fun setUp() {
        service = ZugferdErstellService()
    }

    @Nested
    inner class NormalizeDokumentenart {

        @Test
        fun wirftRuntimeExceptionBeiNichtExistierendemPdf() {
            val daten = ZugferdDaten().apply {
                rechnungsnummer = "RE-2025-001"
                kundenName = "Test GmbH"
                betrag = BigDecimal("1190.00")
            }

            assertThatThrownBy { service.erzeuge("nicht/existierend.pdf", daten) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining("ZUGFeRD Erstellung fehlgeschlagen")
        }
    }

    @Nested
    inner class ErstellungMitNullWerten {

        @Test
        fun wirftExceptionBeiUngueltigemPfad() {
            val daten = ZugferdDaten()

            assertThatThrownBy { service.erzeuge("/tmp/nonexistent.pdf", daten) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }
}
