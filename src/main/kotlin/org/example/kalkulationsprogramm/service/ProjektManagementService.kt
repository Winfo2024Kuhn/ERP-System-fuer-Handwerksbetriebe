package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.domain.ProjektArt
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto
import org.example.kalkulationsprogramm.mapper.ProjektMapper
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
open class ProjektManagementService(
    private val projektRepository: ProjektRepository,
    private val kundeRepository: KundeRepository,
    private val produktkategorieRepository: ProduktkategorieRepository,
    private val projektMapper: ProjektMapper,
) {
    fun fuehreAnfrageZusammen(projektId: Long, anfrageId: Long): ProjektResponseDto = ProjektResponseDto()

    @Transactional
    open fun erstelleProjekt(dto: ProjektErstellenDto, strasse: String?, plz: String?, ort: String?, land: String?): ProjektResponseDto {
        val projekt = Projekt()
        applyProjektDto(projekt, dto, strasse, plz, ort)
        val saved = projektRepository.save(projekt)
        return projektMapper.toProjektResponseDto(saved) ?: ProjektResponseDto()
    }

    @Transactional
    open fun aktualisiereProjekt(id: Long, dto: ProjektErstellenDto, strasse: String?, plz: String?, ort: String?, land: String?): ProjektResponseDto {
        val projekt = projektRepository.findById(id).orElseThrow { NoSuchElementException("Projekt $id nicht gefunden") }
        applyProjektDto(projekt, dto, strasse, plz, ort)
        val saved = projektRepository.save(projekt)
        return projektMapper.toProjektResponseDto(saved) ?: ProjektResponseDto()
    }

    fun aktualisiereMaterialkosten(projektId: Long, materialDtos: List<MaterialkostenErfassenDto>?): ProjektResponseDto = ProjektResponseDto()
    fun fuegeArtikelMaterialkosten(projektId: Long, artikelAuswahl: List<ArtikelMengeDto>?): ProjektResponseDto = ProjektResponseDto()
    fun entferneArtikelMaterialkosten(projektId: Long, artikelInProjektId: Long): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereArtikelInProjekt(projektId: Long, artikelInProjektId: Long, menge: Double?): ProjektResponseDto = ProjektResponseDto()
    fun entferneMaterialkosten(projektId: Long, materialId: Long): ProjektResponseDto = ProjektResponseDto()
    fun fuegeProduktkategorienHinzu(projektId: Long, kategorien: List<ProjektProduktkategorieErfassenDto>?): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereProjektProduktkategorie(projektId: Long, ppkId: Long, dto: ProjektProduktkategorieErfassenDto): ProjektResponseDto = ProjektResponseDto()
    fun loescheProjektProduktkategorie(projektId: Long, ppkId: Long): ProjektResponseDto = ProjektResponseDto()

    open fun findeAlle(): List<Projekt> = projektRepository.findAll()

    @Transactional
    open fun loescheProjekt(projektID: Long) {
        if (projektRepository.existsById(projektID)) {
            projektRepository.deleteById(projektID)
        }
    }

    open fun findeProjekteMitFilter(q: String?, abgeschlossen: Boolean?, pageable: Pageable): Page<ProjektResponseDto> {
        val spec = projektFilter(q, abgeschlossen)
        return projektRepository.findAll(spec, pageable)
            .map { projektMapper.toProjektListeDto(it) ?: ProjektResponseDto() }
    }

    open fun findeProjektById(id: Long): ProjektResponseDto =
        projektRepository.findById(id)
            .map { projektMapper.toProjektResponseDto(it) ?: ProjektResponseDto() }
            .orElseThrow { NoSuchElementException("Projekt $id nicht gefunden") }

    open fun findeProjektEntity(id: Long): Projekt =
        projektRepository.findById(id).orElseThrow { NoSuchElementException("Projekt $id nicht gefunden") }

    @Transactional
    open fun updateProjektKurzbeschreibung(projektId: Long, kurzbeschreibung: String?): ProjektResponseDto {
        val projekt = findeProjektEntity(projektId)
        projekt.kurzbeschreibung = kurzbeschreibung
        return projektMapper.toProjektResponseDto(projektRepository.save(projekt)) ?: ProjektResponseDto()
    }

    fun fuegeZeitenHinzu(projektId: Long, arbeitszeit: Double?, fahrzeit: Double?, bearbeiterId: Long?): ProjektResponseDto = ProjektResponseDto()

    open fun generiereNaechsteAuftragsnummer(anlegedatum: LocalDate?): String {
        val datum = anlegedatum ?: LocalDate.now()
        val prefix = auftragsnummerPrefix(datum)
        val zaehler = getNaechsterAuftragsnummerZaehler(datum)
        return "$prefix${zaehler.toString().padStart(5, '0')}"
    }

    fun generiereKundenAuftragsnummer(anlegedatum: LocalDate?, kundeId: Long?): String = generiereNaechsteAuftragsnummer(anlegedatum)

    open fun getNaechsterAuftragsnummerZaehler(anlegedatum: LocalDate?): Long {
        val prefix = auftragsnummerPrefix(anlegedatum ?: LocalDate.now())
        val maxZaehler = projektRepository.findAuftragsnummernByPrefix(prefix)
            .mapNotNull { nummer -> nummer.removePrefix(prefix).toLongOrNull() }
            .maxOrNull() ?: 0L
        return maxZaehler + 1
    }

    open fun istAuftragsnummerVergeben(auftragsnummer: String?): Boolean =
        !auftragsnummer.isNullOrBlank() && projektRepository.existsByAuftragsnummer(auftragsnummer)

    open fun istAuftragsnummerVergebenFuerAnderesProjekt(auftragsnummer: String?, projektId: Long?): Boolean =
        !auftragsnummer.isNullOrBlank() && projektRepository.existsByAuftragsnummerAndIdNot(auftragsnummer, projektId)

    private fun applyProjektDto(projekt: Projekt, dto: ProjektErstellenDto, strasse: String?, plz: String?, ort: String?) {
        projekt.bauvorhaben = dto.bauvorhaben?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("Bauvorhaben ist erforderlich")
        projekt.auftragsnummer = dto.auftragsnummer?.trim().takeUnless { it.isNullOrBlank() }
            ?: generiereNaechsteAuftragsnummer(dto.anlegedatum)
        projekt.anlegedatum = dto.anlegedatum ?: projekt.anlegedatum ?: LocalDate.now()
        projekt.abschlussdatum = dto.abschlussdatum
        projekt.bruttoPreis = dto.bruttoPreis ?: BigDecimal.ZERO
        projekt.bezahlt = dto.isBezahlt
        projekt.abgeschlossen = dto.isAbgeschlossen
        projekt.kurzbeschreibung = dto.kurzbeschreibung
        projekt.strasse = strasse ?: dto.strasse
        projekt.plz = plz ?: dto.plz
        projekt.ort = ort ?: dto.ort
        projekt.projektArt = dto.projektArt?.let { runCatching { ProjektArt.valueOf(it) }.getOrNull() } ?: ProjektArt.PAUSCHAL
        projekt.kundenId = dto.kundenId?.let { kundeRepository.findById(it).orElse(null) }

        projekt.kundenEmails.clear()
        projekt.kundenEmails.addAll(dto.kundenEmails.orEmpty().mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct())

        if (dto.produktkategorien != null) {
            projekt.projektProduktkategorien.clear()
            dto.produktkategorien.orEmpty()
                .filter { it.produktkategorieID != null }
                .forEach { katDto ->
                    val produktkategorie = produktkategorieRepository.findById(katDto.produktkategorieID!!).orElse(null)
                    if (produktkategorie != null) {
                        projekt.projektProduktkategorien.add(
                            ProjektProduktkategorie().apply {
                                this.projekt = projekt
                                this.produktkategorie = produktkategorie
                                this.menge = katDto.menge ?: BigDecimal.ZERO
                            },
                        )
                    }
                }
        }
    }

    private fun projektFilter(q: String?, abgeschlossen: Boolean?): Specification<Projekt> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            if (!q.isNullOrBlank()) {
                val like = "%${q.lowercase()}%"
                val kunde = root.join<Any, Any>("kundenId", jakarta.persistence.criteria.JoinType.LEFT)
                predicates += cb.or(
                    cb.like(cb.lower(root.get("bauvorhaben")), like),
                    cb.like(cb.lower(root.get("auftragsnummer")), like),
                    cb.like(cb.lower(kunde.get("name")), like),
                    cb.like(cb.lower(kunde.get("kundennummer")), like),
                )
            }
            if (abgeschlossen != null) {
                predicates += cb.equal(root.get<Boolean>("abgeschlossen"), abgeschlossen)
            }
            cb.and(*predicates.toTypedArray())
        }

    private fun auftragsnummerPrefix(datum: LocalDate): String =
        "${datum.year}/${datum.monthValue.toString().padStart(2, '0')}/"

    companion object {
        @JvmStatic
        fun pruefeAuftragsnummer(auftragsnummer: String?): Boolean = !auftragsnummer.isNullOrBlank()
    }
}
