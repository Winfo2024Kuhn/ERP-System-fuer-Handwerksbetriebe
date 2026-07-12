package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Leistung
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository
import org.springframework.stereotype.Component

@Component
class LeistungMapper(
    private val produktkategorieRepository: ProduktkategorieRepository,
    private val produktkategorieMapper: ProduktkategorieMapper
) {

    fun toDto(leistung: Leistung?): LeistungDto? {
        if (leistung == null) {
            return null
        }
        val dto = LeistungDto()
        dto.id = leistung.id
        dto.name = leistung.bezeichnung
        dto.description = leistung.beschreibung
        dto.price = leistung.preis
        dto.unit = leistung.einheit
        val kategorie = leistung.kategorie
        if (kategorie != null) {
            dto.folderId = kategorie.id
            dto.kategoriePfad = produktkategorieMapper.toProduktkategorieResponseDto(kategorie)?.pfad
        }
        return dto
    }

    fun toEntity(dto: LeistungCreateDto?): Leistung? {
        if (dto == null) {
            return null
        }
        val leistung = Leistung()
        updateEntity(leistung, dto)
        return leistung
    }

    fun updateEntity(leistung: Leistung, dto: LeistungCreateDto) {
        leistung.bezeichnung = dto.name
        leistung.beschreibung = dto.description
        leistung.preis = dto.price

        val folderId = dto.folderId
        if (folderId != null) {
            val kat = produktkategorieRepository.findById(folderId).orElse(null)
            leistung.kategorie = kat
            leistung.einheit = dto.unit ?: kat?.verrechnungseinheit
        } else {
            leistung.kategorie = null
            leistung.einheit = dto.unit
        }
    }
}
