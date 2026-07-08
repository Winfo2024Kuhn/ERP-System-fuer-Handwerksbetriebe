package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.ProjektDokument
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektDokumentResponseDto
import org.example.kalkulationsprogramm.mapper.ProjektMapper
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.example.kalkulationsprogramm.service.ProjektManagementService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/projekte")
class ProjektController(
    private val projektRepository: ProjektRepository,
    private val projektMapper: ProjektMapper,
    private val projektManagementService: ProjektManagementService,
    private val dateiSpeicherService: DateiSpeicherService,
    private val projektNotizRepository: ProjektNotizRepository,
) {
    @GetMapping
    fun getProjekte(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) abgeschlossen: Boolean?,
        pageable: Pageable,
    ): Page<ProjektResponseDto> =
        projektManagementService.findeProjekteMitFilter(q, abgeschlossen, pageable)

    @PostMapping
    fun createProjekt(@RequestBody dto: ProjektErstellenDto): ResponseEntity<ProjektResponseDto> =
        ResponseEntity.ok(projektManagementService.erstelleProjekt(dto, dto.strasse, dto.plz, dto.ort, null))

    @PutMapping("/{id}")
    fun updateProjekt(@PathVariable id: Long, @RequestBody dto: ProjektErstellenDto): ResponseEntity<ProjektResponseDto> =
        runCatching { projektManagementService.aktualisiereProjekt(id, dto, dto.strasse, dto.plz, dto.ort, null) }
            .fold(
                onSuccess = { ResponseEntity.ok(it) },
                onFailure = { ResponseEntity.notFound().build() },
            )

    @DeleteMapping("/{id}")
    fun deleteProjekt(@PathVariable id: Long): ResponseEntity<Void> {
        projektManagementService.loescheProjekt(id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{id}/kurzbeschreibung", consumes = ["text/plain"])
    fun updateKurzbeschreibung(@PathVariable id: Long, @RequestBody kurzbeschreibung: String): ResponseEntity<ProjektResponseDto> =
        runCatching { projektManagementService.updateProjektKurzbeschreibung(id, kurzbeschreibung) }
            .fold(
                onSuccess = { ResponseEntity.ok(it) },
                onFailure = { ResponseEntity.notFound().build() },
            )

    @GetMapping("/naechste-auftragsnummer")
    fun getNaechsteAuftragsnummer(
        @RequestParam(required = false, name = "datum")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        datum: LocalDate?,
    ): NaechsteAuftragsnummerResponse {
        val effectiveDate = datum ?: LocalDate.now()
        val prefix = "${effectiveDate.year}/${effectiveDate.monthValue.toString().padStart(2, '0')}/"
        val zaehler = projektManagementService.getNaechsterAuftragsnummerZaehler(effectiveDate)
        return NaechsteAuftragsnummerResponse(
            auftragsnummer = "$prefix${zaehler.toString().padStart(5, '0')}",
            prefix = prefix,
            zaehler = zaehler,
        )
    }

    @GetMapping("/auftragsnummer-verfuegbar")
    fun pruefeAuftragsnummer(
        @RequestParam auftragsnummer: String,
        @RequestParam(required = false) projektId: Long?,
    ): AuftragsnummerValidierungResponse {
        val vergeben = if (projektId == null) {
            projektManagementService.istAuftragsnummerVergeben(auftragsnummer)
        } else {
            projektManagementService.istAuftragsnummerVergebenFuerAnderesProjekt(auftragsnummer, projektId)
        }
        return AuftragsnummerValidierungResponse(
            verfuegbar = !vergeben,
            message = if (vergeben) "Diese Auftragsnummer ist bereits vergeben." else null,
        )
    }

    @GetMapping("/freigabe-status")
    fun getFreigabeStatus(@RequestParam ids: String?): Map<Long, Any> = emptyMap()

    @GetMapping("/{id}/dokumente")
    fun getProjektDokumente(
        @PathVariable id: Long,
        @RequestParam(required = false) gruppe: String?,
    ): List<ProjektDokumentResponseDto> =
        dateiSpeicherService.holeDokumenteZuProjekt(id)
            .asSequence()
            .filter { dokument -> gruppe.isNullOrBlank() || dokument.dokumentGruppe?.name == gruppe }
            .map(::toProjektDokumentDto)
            .toList()

    @GetMapping("/{id}/eingangsrechnungen")
    fun getEingangsrechnungen(@PathVariable id: Long): List<EingangsrechnungDto> = emptyList()

    @GetMapping("/{id}/notizen")
    fun getNotizen(@PathVariable id: Long): List<ProjektNotizDto> =
        projektNotizRepository.findByProjektIdOrderByErstelltAmDesc(id).map { notiz ->
            ProjektNotizDto().apply {
                this.id = notiz.id
                this.notiz = notiz.notiz
                this.erstelltAm = notiz.erstelltAm
                this.mitarbeiterId = notiz.mitarbeiter?.id
                this.mitarbeiterVorname = notiz.mitarbeiter?.vorname
                this.mitarbeiterNachname = notiz.mitarbeiter?.nachname
                this.mobileSichtbar = notiz.mobileSichtbar
                this.nurFuerErsteller = notiz.nurFuerErsteller
                this.canEdit = true
                this.bilder = notiz.bilder.map { bild ->
                    ProjektNotizBildDto().apply {
                        this.id = bild.id
                        this.originalDateiname = bild.originalDateiname
                        this.url = bild.gespeicherterDateiname?.let { "/api/projekte/$id/notizen/${notiz.id}/bilder/${bild.id}" }
                        this.erstelltAm = bild.erstelltAm
                    }
                }
            }
        }

    @GetMapping("/{id}")
    fun getProjektById(@PathVariable id: Long): ResponseEntity<ProjektResponseDto> =
        projektRepository.findById(id)
            .map { projektMapper.toProjektResponseDto(it) ?: ProjektResponseDto() }
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.notFound().build() }

    @GetMapping("/simple")
    fun getSimpleProjekte(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "500") size: Int,
        @RequestParam(required = false) nurOffene: Boolean?,
    ): List<ProjektSucheDto> {
        val pageRequest = PageRequest.of(0, size.coerceIn(1, 1000))
        return projektRepository.findSimpleByQuery(q, pageRequest)
            .asSequence()
            .filter { simple -> nurOffene != true || !simple.isAbgeschlossen() }
            .map { simple ->
                ProjektSucheDto(
                    id = simple.getId(),
                    bauvorhaben = simple.getBauvorhaben(),
                    auftragsnummer = simple.getAuftragsnummer(),
                    kunde = simple.getKunde(),
                    abgeschlossen = simple.isAbgeschlossen(),
                )
            }
            .toList()
    }

    @GetMapping("/suche")
    fun sucheProjekte(@RequestParam q: String): List<ProjektSucheDto> =
        projektRepository.searchByBauvorhabenOrKundeOrEmail(q).map { projekt ->
            ProjektSucheDto(
                id = projekt.id,
                bauvorhaben = projekt.bauvorhaben,
                auftragsnummer = projekt.auftragsnummer,
                kunde = projekt.getKunde(),
                abgeschlossen = projekt.isAbgeschlossen(),
            )
        }

    data class ProjektSucheDto(
        val id: Long?,
        val bauvorhaben: String?,
        val auftragsnummer: String?,
        val kunde: String?,
        val abgeschlossen: Boolean,
    )

    private fun toProjektDokumentDto(dokument: ProjektDokument): ProjektDokumentResponseDto =
        ProjektDokumentResponseDto().apply {
            id = dokument.id
            originalDateiname = dokument.originalDateiname
            dateityp = dokument.dateityp
            url = dokument.id?.let { "/api/projekte/${dokument.projekt?.id}/dokumente/$it" }
            netzwerkPfad = dokument.gespeicherterDateiname
            dokumentGruppe = dokument.dokumentGruppe?.name
            uploadDatum = dokument.uploadDatum
            emailVersandDatum = dokument.emailVersandDatum
            projektId = dokument.projekt?.id
            projektAuftragsnummer = dokument.projekt?.auftragsnummer
            projektKunde = dokument.projekt?.getKunde()
            lieferantId = dokument.lieferant?.id
            lieferantenname = dokument.lieferant?.lieferantenname
            uploadedByVorname = dokument.uploadedBy?.vorname
            uploadedByNachname = dokument.uploadedBy?.nachname
            if (dokument is ProjektGeschaeftsdokument) {
                rechnungsnummer = dokument.dokumentid
                rechnungsdatum = dokument.rechnungsdatum
                faelligkeitsdatum = dokument.faelligkeitsdatum
                geschaeftsdokumentart = dokument.geschaeftsdokumentart
                mahnstufe = dokument.mahnstufe?.name
                referenzDokumentId = dokument.referenzDokument?.id
                referenzDokumentNummer = dokument.referenzDokument?.dokumentid
                isMahnung = dokument.mahnstufe != null
                rechnungsbetrag = dokument.bruttoBetrag
                isBezahlt = dokument.bezahlt
            }
        }

    data class NaechsteAuftragsnummerResponse(val auftragsnummer: String?, val prefix: String?, val zaehler: Long)

    data class AuftragsnummerValidierungResponse(val verfuegbar: Boolean, val message: String?)

    class EingangsrechnungDto {
        var id: Long? = null
        var dokumentId: Long? = null
        var geschaeftsdokumentId: Long? = null
        var dokumentNummer: String? = null
        var dateiname: String? = null
        var dokumentDatum: LocalDate? = null
        var gesamtbetrag: BigDecimal? = null
        var prozent: Int? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var pdfUrl: String? = null
        var zugeordnetVonName: String? = null
        var zugeordnetAm: LocalDateTime? = null
        var alleZuordnungen: List<AnteilDto>? = null
        var dokumentenKette: List<DokumentKetteRefDto>? = null
    }

    class AnteilDto {
        var projektId: Long? = null
        var projektName: String? = null
        var projektNummer: String? = null
        var kostenstelleId: Long? = null
        var kostenstelleName: String? = null
        var prozent: Int? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var zugeordnetVonName: String? = null
        var zugeordnetAm: LocalDateTime? = null
    }

    class DokumentKetteRefDto {
        var id: Long? = null
        var typ: String? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var pdfUrl: String? = null
    }

    class ProjektNotizDto {
        var id: Long? = null
        var notiz: String? = null
        var erstelltAm: LocalDateTime? = null
        var mitarbeiterId: Long? = null
        var mitarbeiterVorname: String? = null
        var mitarbeiterNachname: String? = null
        var mobileSichtbar: Boolean = true
        var nurFuerErsteller: Boolean = false
        var canEdit: Boolean = false
        var bilder: List<ProjektNotizBildDto>? = null
    }

    class ProjektNotizBildDto {
        var id: Long? = null
        var originalDateiname: String? = null
        var url: String? = null
        var erstelltAm: LocalDateTime? = null
    }

    class ProjektNotizCreateDto {
        var notiz: String? = null
        var mobileSichtbar: Boolean = true
        var nurFuerErsteller: Boolean = false
    }
}
