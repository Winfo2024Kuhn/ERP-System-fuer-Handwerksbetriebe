package org.example.kalkulationsprogramm.mapper

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Anrede
import org.example.kalkulationsprogramm.domain.Kunde
import org.junit.jupiter.api.Test

class AnfrageMapperTest {

    private val mapper = AnfrageMapper()

    @Test
    fun mapptVollstaendigesAnfrageZuDto() {
        val kunde = Kunde().apply {
            id = 1L
            name = "Bauhaus GmbH"
            kundennummer = "K-001"
            kundenEmails = mutableListOf("info@bauhaus.de")
            strasse = "Baustraße 1"
            plz = "10115"
            ort = "Berlin"
            telefon = "030 12345"
            mobiltelefon = "0170 12345"
            ansprechspartner = "Herr Meier"
            anrede = Anrede.HERR
        }

        val anfrage = Anfrage().apply {
            id = 42L
            this.kunde = kunde
        }

        val dto = requireNotNull(mapper.toAnfrageResponseDto(anfrage))

        assertThat(dto.id).isEqualTo(42L)
        assertThat(dto.kundenId).isEqualTo(1L)
        assertThat(dto.kundenName).isEqualTo("Bauhaus GmbH")
        assertThat(dto.kundennummer).isEqualTo("K-001")
        assertThat(dto.kundenStrasse).isEqualTo("Baustraße 1")
        assertThat(dto.kundenPlz).isEqualTo("10115")
        assertThat(dto.kundenOrt).isEqualTo("Berlin")
        assertThat(dto.kundenTelefon).isEqualTo("030 12345")
        assertThat(dto.kundenMobiltelefon).isEqualTo("0170 12345")
        assertThat(dto.kundenAnsprechpartner).isEqualTo("Herr Meier")
        assertThat(dto.kundenAnrede).isEqualTo("HERR")
    }

    @Test
    fun gibtNullZurueckBeiNullAnfrage() {
        val dto = mapper.toAnfrageResponseDto(null)

        assertThat(dto).isNull()
    }

    @Test
    fun mapptAnfrageOhneKunde() {
        val anfrage = Anfrage().apply {
            id = 10L
            kunde = null
        }

        val dto = requireNotNull(mapper.toAnfrageResponseDto(anfrage))

        assertThat(dto.id).isEqualTo(10L)
        assertThat(dto.kundenId).isNull()
        assertThat(dto.kundenName).isNull()
    }

    @Test
    fun vereinigtKundenEmailsUndAnfrageEmailsOhneDuplikate() {
        val kunde = Kunde().apply {
            id = 1L
            name = "Test"
            kundenEmails = mutableListOf("info@test.de", "doppelt@test.de")
        }

        val anfrage = Anfrage().apply {
            id = 1L
            this.kunde = kunde
            kundenEmails = mutableListOf("doppelt@test.de", "extra@test.de")
        }

        val dto = requireNotNull(mapper.toAnfrageResponseDto(anfrage))

        assertThat(dto.kundenEmails)
            .containsExactlyInAnyOrder("info@test.de", "doppelt@test.de", "extra@test.de")
    }

    @Test
    fun mapptKundeOhneAnrede() {
        val kunde = Kunde().apply {
            id = 1L
            name = "Test"
            anrede = null
        }

        val anfrage = Anfrage().apply {
            id = 1L
            this.kunde = kunde
        }

        val dto = requireNotNull(mapper.toAnfrageResponseDto(anfrage))

        assertThat(dto.kundenAnrede).isNull()
    }
}
