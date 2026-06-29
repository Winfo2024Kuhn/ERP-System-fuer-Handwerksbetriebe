package org.example.kalkulationsprogramm.controller

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BestellungsUebersichtControllerBuildKettenTest {

    private val controller = BestellungsUebersichtController(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
    )

    @Test
    fun crashtNichtWennVerknuepftesDokumentNichtInEingabeliste() {
        val lieferant = Lieferanten().apply {
            id = 1L
            lieferantenname = "Max Mustermann GmbH"
        }

        val anfrage = neuesDokument(10L, lieferant, LieferantDokumentTyp.ANGEBOT)
        val rechnung = neuesDokument(11L, lieferant, LieferantDokumentTyp.RECHNUNG)
        rechnung.verknuepfteDokumente = hashSetOf(anfrage)

        val ketten = buildKetten(listOf(rechnung))

        assertThat(ketten).hasSize(1)
        val kette = ketten[0]
        assertThat(invoke(kette, "lieferantId")).isEqualTo(1L)
        assertThat(invoke(kette, "lieferantName")).isEqualTo("Max Mustermann GmbH")
        val dokumente = invoke(kette, "dokumente") as List<*>
        assertThat(dokumente).hasSize(1)
        assertThat(readField(dokumente[0]!!, "id")).isEqualTo(11L)
    }

    @Test
    fun liefertLeereListeWennEingabeleer() {
        assertThat(buildKetten(emptyList())).isEmpty()
    }

    @Test
    fun verarbeitetMehrereVerknuepfteDokumenteInDerListe() {
        val lieferant = Lieferanten().apply {
            id = 2L
            lieferantenname = "Test Lieferant"
        }

        val anfrage = neuesDokument(20L, lieferant, LieferantDokumentTyp.ANGEBOT)
        val rechnung = neuesDokument(21L, lieferant, LieferantDokumentTyp.RECHNUNG)
        rechnung.verknuepfteDokumente = hashSetOf(anfrage)
        anfrage.verknuepfteDokumente = hashSetOf(rechnung)

        val ketten = buildKetten(listOf(anfrage, rechnung))

        assertThat(ketten).hasSize(1)
        val dokumente = invoke(ketten[0], "dokumente") as List<*>
        assertThat(dokumente).hasSize(2)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildKetten(dokumente: List<LieferantDokument>): List<Any> {
        val method = BestellungsUebersichtController::class.java.getDeclaredMethod("buildKetten", List::class.java)
        method.isAccessible = true
        return method.invoke(controller, dokumente) as List<Any>
    }

    private fun invoke(target: Any, methodName: String): Any? =
        target.javaClass.getDeclaredMethod(methodName).invoke(target)

    private fun readField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun neuesDokument(id: Long, lieferant: Lieferanten, typ: LieferantDokumentTyp): LieferantDokument =
        LieferantDokument().apply {
            this.id = id
            this.lieferant = lieferant
            this.typ = typ
            uploadDatum = LocalDateTime.now()
        }
}
