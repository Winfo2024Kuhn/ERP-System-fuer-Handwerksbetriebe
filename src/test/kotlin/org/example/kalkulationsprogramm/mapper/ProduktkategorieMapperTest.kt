package org.example.kalkulationsprogramm.mapper

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.junit.jupiter.api.Test

class ProduktkategorieMapperTest {

    private val mapper = ProduktkategorieMapper()

    @Test
    fun mapptAlleFelder() {
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Stahl"
            verrechnungseinheit = Verrechnungseinheit.KILOGRAMM
            beschreibung = "Stahlprodukte"
            bildUrl = "stahl.png"
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.id).isEqualTo(1L)
        assertThat(dto.bezeichnung).isEqualTo("Stahl")
        assertThat(dto.verrechnungseinheit).isEqualTo(Verrechnungseinheit.KILOGRAMM)
        assertThat(dto.beschreibung).isEqualTo("Stahlprodukte")
        assertThat(dto.bildUrl).isEqualTo("/api/images/stahl.png")
        assertThat(dto.isLeaf).isTrue()
    }

    @Test
    fun gibtNullZurueckBeiNullKategorie() {
        assertThat(mapper.toProduktkategorieResponseDto(null)).isNull()
    }

    @Test
    fun setzLeafAufFalseBeiVorhandenenUnterkategorien() {
        val kind = Produktkategorie().apply {
            id = 2L
            bezeichnung = "Unterkategorie"
        }
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Eltern"
            verrechnungseinheit = Verrechnungseinheit.STUECK
            unterkategorien = mutableListOf(kind)
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.isLeaf).isFalse()
    }

    @Test
    fun bauePfadMitHierarchie() {
        val opa = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Werkstoffe"
            verrechnungseinheit = Verrechnungseinheit.KILOGRAMM
        }
        val vater = Produktkategorie().apply {
            id = 2L
            bezeichnung = "Metalle"
            verrechnungseinheit = Verrechnungseinheit.KILOGRAMM
            uebergeordneteKategorie = opa
        }
        val kind = Produktkategorie().apply {
            id = 3L
            bezeichnung = "Stahl"
            verrechnungseinheit = Verrechnungseinheit.KILOGRAMM
            uebergeordneteKategorie = vater
        }

        val dto = mapper.toProduktkategorieResponseDto(kind)!!

        assertThat(dto.pfad).isEqualTo("Werkstoffe > Metalle > Stahl")
    }

    @Test
    fun bauePfadOhneElternkategorie() {
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Einzelne Kategorie"
            verrechnungseinheit = Verrechnungseinheit.STUECK
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.pfad).isEqualTo("Einzelne Kategorie")
    }

    @Test
    fun laeBildUrlMitSchraegstrichUnveraendert() {
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Test"
            verrechnungseinheit = Verrechnungseinheit.STUECK
            bildUrl = "/api/images/vorhandenes-bild.png"
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.bildUrl).isEqualTo("/api/images/vorhandenes-bild.png")
    }

    @Test
    fun behandeltNullBildUrl() {
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Test"
            verrechnungseinheit = Verrechnungseinheit.STUECK
            bildUrl = null
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.bildUrl).isNull()
    }

    @Test
    fun behandeltLeereBildUrl() {
        val kategorie = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Test"
            verrechnungseinheit = Verrechnungseinheit.STUECK
            bildUrl = "   "
        }

        val dto = mapper.toProduktkategorieResponseDto(kategorie)!!

        assertThat(dto.bildUrl).isEqualTo("   ")
    }
}
