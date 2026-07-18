package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto
import org.springframework.stereotype.Component

@Component
class ProduktkategorieMapper {
    fun toProduktkategorieResponseDto(produktkategorie: Produktkategorie?): ProduktkategorieResponseDto? {
        if (produktkategorie == null) {
            return null
        }
        val isLeaf = produktkategorie.unterkategorien.isEmpty()
        return toProduktkategorieResponseDtoWithLeaf(produktkategorie, isLeaf)
    }

    fun toProduktkategorieResponseDtoWithLeaf(
        produktkategorie: Produktkategorie?,
        isLeaf: Boolean
    ): ProduktkategorieResponseDto? {
        if (produktkategorie == null) {
            return null
        }
        val dto = ProduktkategorieResponseDto()
        dto.id = produktkategorie.id
        dto.bezeichnung = produktkategorie.bezeichnung
        dto.verrechnungseinheit = produktkategorie.verrechnungseinheit
        dto.beschreibung = produktkategorie.beschreibung

        var bildUrl = produktkategorie.bildUrl
        if (!bildUrl.isNullOrBlank() && !bildUrl.startsWith("/")) {
            bildUrl = "/api/images/$bildUrl"
        }
        dto.bildUrl = bildUrl

        dto.isLeaf = isLeaf
        dto.pfad = bauePfad(produktkategorie)
        dto.parentId = produktkategorie.uebergeordneteKategorie?.id
        return dto
    }

    private fun bauePfad(kategorie: Produktkategorie): String {
        val namen = mutableListOf<String>()
        var current: Produktkategorie? = kategorie
        while (current != null) {
            current.bezeichnung?.let(namen::add)
            current = current.uebergeordneteKategorie
        }
        namen.reverse()
        return namen.joinToString(" > ")
    }
}
