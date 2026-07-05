package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
open class ProjektManagementService {
    fun fuehreAnfrageZusammen(projektId: Long, anfrageId: Long): ProjektResponseDto = ProjektResponseDto()
    fun erstelleProjekt(dto: ProjektErstellenDto, strasse: String?, plz: String?, ort: String?, land: String?): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereProjekt(id: Long, dto: ProjektErstellenDto, strasse: String?, plz: String?, ort: String?, land: String?): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereMaterialkosten(projektId: Long, materialDtos: List<MaterialkostenErfassenDto>?): ProjektResponseDto = ProjektResponseDto()
    fun fuegeArtikelMaterialkosten(projektId: Long, artikelAuswahl: List<ArtikelMengeDto>?): ProjektResponseDto = ProjektResponseDto()
    fun entferneArtikelMaterialkosten(projektId: Long, artikelInProjektId: Long): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereArtikelInProjekt(projektId: Long, artikelInProjektId: Long, menge: Double?): ProjektResponseDto = ProjektResponseDto()
    fun entferneMaterialkosten(projektId: Long, materialId: Long): ProjektResponseDto = ProjektResponseDto()
    fun fuegeProduktkategorienHinzu(projektId: Long, kategorien: List<ProjektProduktkategorieErfassenDto>?): ProjektResponseDto = ProjektResponseDto()
    fun aktualisiereProjektProduktkategorie(projektId: Long, ppkId: Long, dto: ProjektProduktkategorieErfassenDto): ProjektResponseDto = ProjektResponseDto()
    fun loescheProjektProduktkategorie(projektId: Long, ppkId: Long): ProjektResponseDto = ProjektResponseDto()
    fun findeAlle(): List<Projekt> = emptyList()
    fun loescheProjekt(projektID: Long) {}
    fun findeProjekteMitFilter(q: String?, abgeschlossen: Boolean?, pageable: Pageable): Page<ProjektResponseDto> = PageImpl(emptyList(), pageable, 0)
    fun findeProjektById(id: Long): ProjektResponseDto = ProjektResponseDto()
    fun findeProjektEntity(id: Long): Projekt = Projekt()
    fun updateProjektKurzbeschreibung(projektId: Long, kurzbeschreibung: String?): ProjektResponseDto = ProjektResponseDto()
    fun fuegeZeitenHinzu(projektId: Long, arbeitszeit: Double?, fahrzeit: Double?, bearbeiterId: Long?): ProjektResponseDto = ProjektResponseDto()
    fun generiereNaechsteAuftragsnummer(anlegedatum: LocalDate?): String = "0000"
    fun generiereKundenAuftragsnummer(anlegedatum: LocalDate?, kundeId: Long?): String = generiereNaechsteAuftragsnummer(anlegedatum)
    fun getNaechsterAuftragsnummerZaehler(anlegedatum: LocalDate?): Long = 1
    fun istAuftragsnummerVergeben(auftragsnummer: String?): Boolean = false
    fun istAuftragsnummerVergebenFuerAnderesProjekt(auftragsnummer: String?, projektId: Long?): Boolean = false

    companion object {
        @JvmStatic
        fun pruefeAuftragsnummer(auftragsnummer: String?): Boolean = !auftragsnummer.isNullOrBlank()
    }
}
