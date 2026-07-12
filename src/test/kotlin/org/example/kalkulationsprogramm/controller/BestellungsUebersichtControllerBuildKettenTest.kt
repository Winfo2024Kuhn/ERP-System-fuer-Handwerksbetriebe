package org.example.kalkulationsprogramm.controller

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class BestellungsUebersichtControllerBuildKettenTest {

    private val dokumentRepository = mock(LieferantDokumentRepository::class.java)
    private val controller = BestellungsUebersichtController(
        dokumentRepository,
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
    )

    @Test
    fun crashtNichtWennVerknuepftesDokumentNichtInEingabeliste() {
        val lieferant = Lieferanten().apply {
            id = 1L
            lieferantenname = "Max Mustermann GmbH"
        }

        val anfrage = neuesDokument(10L, lieferant, LieferantDokumentTyp.ANGEBOT)
        val rechnung = neuesDokument(11L, lieferant, LieferantDokumentTyp.RECHNUNG)
        rechnung.geschaeftsdaten = mock()
        `when`(dokumentRepository.findAll()).thenReturn(listOf(rechnung))
        val ketten = controller.getUebersicht().body!!.offeneRechnungen

        assertThat(ketten).hasSize(1)
        val kette = ketten[0]
        assertThat(kette.bestellung!!.id).isEqualTo(11L)
    }

    @Test
    fun liefertLeereListeWennEingabeleer() {
        `when`(dokumentRepository.findAll()).thenReturn(emptyList())
        val body = controller.getUebersicht().body!!
        assertThat(body.offeneBestellungen).isEmpty()
        assertThat(body.offeneWareneingaenge).isEmpty()
        assertThat(body.offeneRechnungen).isEmpty()
    }

    @Test
    fun verarbeitetMehrereVerknuepfteDokumenteInDerListe() {
        val lieferant = Lieferanten().apply {
            id = 2L
            lieferantenname = "Test Lieferant"
        }

        val anfrage = neuesDokument(20L, lieferant, LieferantDokumentTyp.ANGEBOT)
        val rechnung = neuesDokument(21L, lieferant, LieferantDokumentTyp.RECHNUNG)
        anfrage.typ = LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG
        anfrage.geschaeftsdaten = mock()
        rechnung.geschaeftsdaten = mock()
        `when`(dokumentRepository.findAll()).thenReturn(listOf(anfrage, rechnung))
        val body = controller.getUebersicht().body!!

        assertThat(body.offeneBestellungen).hasSize(1)
        assertThat(body.offeneRechnungen).hasSize(1)
    }

    private fun neuesDokument(id: Long, lieferant: Lieferanten, typ: LieferantDokumentTyp): LieferantDokument =
        LieferantDokument().apply {
            this.id = id
            this.lieferant = lieferant
            this.typ = typ
            uploadDatum = LocalDateTime.now()
        }
}
