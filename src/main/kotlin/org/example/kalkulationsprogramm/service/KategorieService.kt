package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.LieferantRolle
import org.example.kalkulationsprogramm.dto.Artikel.KategorieCreateDto
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto
import org.example.kalkulationsprogramm.repository.KategorieRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Service
class KategorieService(
    private val kategorieRepository: KategorieRepository,
) {
    fun findeHauptkategorien(): List<KategorieResponseDto> =
        kategorieRepository.findByParentKategorieIsNull()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.beschreibung ?: "" })
            .map { toDto(it) }

    fun alleKategorien(): List<KategorieResponseDto> =
        kategorieRepository.findAll()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.beschreibung ?: "" })
            .map { toDto(it) }

    fun findeUnterkategorien(parentId: Int?): List<KategorieResponseDto> =
        kategorieRepository.findByParentKategorie_Id(parentId)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.beschreibung ?: "" })
            .map { toDto(it) }

    @Transactional
    fun erstelleKategorie(dto: KategorieCreateDto): KategorieResponseDto {
        val bezeichnung = dto.bezeichnung?.trim().orEmpty()
        if (bezeichnung.isEmpty()) {
            throw IllegalArgumentException("Kategoriebezeichnung darf nicht leer sein.")
        }

        val neueKategorie = Kategorie().apply {
            beschreibung = bezeichnung
            typischeRollen = dto.typischeRollen?.toMutableSet() ?: mutableSetOf()
        }

        if (dto.parentId != null) {
            val parent = kategorieRepository.findById(dto.parentId!!)
                .orElseThrow { IllegalArgumentException("Eltern-Kategorie nicht gefunden.") }
            neueKategorie.parentKategorie = parent
        }

        return toDto(kategorieRepository.save(neueKategorie))
    }

    @Transactional
    fun aktualisiereTypischeRollen(kategorieId: Int, rollen: Set<LieferantRolle>?): KategorieResponseDto {
        val kategorie = kategorieRepository.findById(kategorieId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Kategorie nicht gefunden.") }
        kategorie.typischeRollen = rollen?.toMutableSet() ?: mutableSetOf()
        return toDto(kategorieRepository.save(kategorie))
    }

    @Transactional(readOnly = true)
    fun findeEffektiveRollen(kategorieId: Int): Set<LieferantRolle> {
        var aktuelle = kategorieRepository.findById(kategorieId).orElse(null)
        val besucht = HashSet<Int>()
        while (aktuelle != null && aktuelle.id != null && besucht.add(aktuelle.id!!)) {
            if (aktuelle.typischeRollen.isNotEmpty()) {
                return aktuelle.typischeRollen
            }
            aktuelle = aktuelle.parentKategorie
        }
        return emptySet()
    }

    fun findeKategorieUndUnterkategorieIds(kategorieId: Int?): List<Int> {
        if (kategorieId == null) {
            return emptyList()
        }
        val ids = linkedSetOf<Int>()
        collectKategorieIds(kategorieId, ids)
        return ArrayList(ids)
    }

    private fun collectKategorieIds(id: Int?, collector: MutableSet<Int>) {
        if (id == null || collector.contains(id)) {
            return
        }
        collector.add(id)
        kategorieRepository.findByParentKategorie_Id(id)
            .forEach { child -> collectKategorieIds(child.id, collector) }
    }

    private fun toDto(kategorie: Kategorie): KategorieResponseDto =
        KategorieResponseDto().apply {
            id = kategorie.id
            bezeichnung = kategorie.beschreibung
            leaf = !kategorieRepository.existsByParentKategorie_Id(kategorie.id)
            typischeRollen = kategorie.typischeRollen
        }
}
