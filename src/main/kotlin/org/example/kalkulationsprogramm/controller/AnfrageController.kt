package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.AnfrageDokument
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.AnfrageNotiz
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild
import org.example.kalkulationsprogramm.domain.DokumentGruppe
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageDokumentResponseDto
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageSeiteResponseDto
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeStatusKurzDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.service.AnfrageFunnelService
import org.example.kalkulationsprogramm.service.AnfrageService
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.example.kalkulationsprogramm.service.DokumentFreigabeService
import org.example.kalkulationsprogramm.service.FrontendUserProfileService
import org.example.kalkulationsprogramm.service.PdfAiExtractorService
import org.example.kalkulationsprogramm.service.ZugferdErstellService
import org.example.kalkulationsprogramm.service.ZugferdExtractorService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Collator
import java.time.format.DateTimeFormatter
import java.util.Locale

@RestController
@RequestMapping("/api/anfragen")
class AnfrageController(
    private val anfrageService: AnfrageService,
    private val ausgangsGeschaeftsDokumentService: AusgangsGeschaeftsDokumentService,
    private val dateiSpeicherService: DateiSpeicherService,
    private val zugferdErstellService: ZugferdErstellService,
    private val zugferdExtractorService: ZugferdExtractorService,
    private val pdfAiExtractorService: PdfAiExtractorService,
    private val kundeRepository: KundeRepository,
    private val anfrageNotizRepository: AnfrageNotizRepository,
    private val anfrageNotizBildRepository: AnfrageNotizBildRepository,
    private val anfrageRepository: AnfrageRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val frontendUserProfileService: FrontendUserProfileService,
    private val dokumentFreigabeService: DokumentFreigabeService,
) {
    private val log = LoggerFactory.getLogger(AnfrageController::class.java)

    @GetMapping("/funnel-ids")
    fun funnelAnfrageIds(): ResponseEntity<List<Long>> =
        ResponseEntity.ok(
            anfrageRepository.findOffeneFunnelAnfragen(AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN)
                .mapNotNull { it.id },
        )

    @GetMapping("/freigabe-status")
    fun freigabeStatus(@RequestParam("ids") ids: List<Long>): ResponseEntity<Map<Long, FreigabeStatusKurzDto>> {
        val result = linkedMapOf<Long, FreigabeStatusKurzDto>()
        dokumentFreigabeService.findJuengsteProAnfrage(ids).forEach { (anfrageId, freigabe) ->
            result[anfrageId] = FreigabeStatusKurzDto.builder()
                .status(freigabe.status?.name)
                .dokumentArt(freigabe.dokumentArt)
                .dokumentNummer(freigabe.dokumentNummer)
                .akzeptiertAm(freigabe.akzeptiertAm)
                .ablaufDatum(freigabe.ablaufDatum)
                .erstelltAm(freigabe.erstelltAm)
                .build()
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping(value = ["/zugferd/extract"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun extractZugferd(@RequestParam("datei") datei: MultipartFile): ResponseEntity<ZugferdDaten> =
        try {
            val temp = Files.createTempFile("zugferd-", ".pdf.html")
            Files.copy(datei.inputStream, temp, StandardCopyOption.REPLACE_EXISTING)
            val daten = zugferdExtractorService.extract(temp.toString(), datei.originalFilename)
            Files.deleteIfExists(temp)
            ResponseEntity.ok(daten)
        } catch (_: java.io.IOException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

    @PostMapping(value = ["/zugferd/extract-ai"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun extractZugferdWithAi(
        @RequestParam("datei") datei: MultipartFile,
        @RequestParam(value = "dokumentTyp", required = false) dokumentTyp: String?,
    ): ResponseEntity<ZugferdDaten> =
        try {
            val temp = Files.createTempFile("zugferd-anfrage-ai-", ".pdf")
            Files.copy(datei.inputStream, temp, StandardCopyOption.REPLACE_EXISTING)
            val typeToUse = dokumentTyp?.takeIf { it.isNotBlank() } ?: "Angebot"
            val aiResult = pdfAiExtractorService.analyze(temp.toString(), typeToUse)
            val daten = if (aiResult.isPresent) {
                aiResult.get().also {
                    if (it.geschaeftsdokumentart.isNullOrBlank()) {
                        it.geschaeftsdokumentart = detectDocTypeFromFilename(datei.originalFilename)
                    }
                }
            } else {
                zugferdExtractorService.extract(temp.toString(), datei.originalFilename)
            }
            Files.deleteIfExists(temp)
            ResponseEntity.ok(daten)
        } catch (_: java.io.IOException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

    private fun detectDocTypeFromFilename(filename: String?): String {
        val lower = filename?.lowercase(Locale.ROOT) ?: return "Angebot"
        return if (
            lower.contains("auftragsbestätigung") ||
            lower.contains("auftragsbestaetigung") ||
            lower.contains("ab_") ||
            lower.contains("ab-")
        ) {
            "Auftragsbestätigung"
        } else {
            "Angebot"
        }
    }

    @PostMapping
    fun erstelle(@RequestBody dto: AnfrageErstellenDto): ResponseEntity<AnfrageResponseDto> =
        ResponseEntity.ok().header("X-Message", "Anfrage gespeichert").body(anfrageService.erstelleAnfrage(dto))

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun erstelleMitBild(
        @RequestPart("anfrageDto") dto: AnfrageErstellenDto,
        @RequestPart(value = "imageFile", required = false) imageFile: MultipartFile?,
    ): ResponseEntity<AnfrageResponseDto> =
        ResponseEntity.status(HttpStatus.CREATED)
            .header("X-Message", "Anfrage gespeichert")
            .body(anfrageService.erstelleAnfrage(dto, imageFile))

    @PostMapping(value = ["/{anfrageID}/dokumente"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDokument(
        @PathVariable anfrageID: Long,
        @RequestParam("datei") dateien: List<MultipartFile>,
        @RequestParam(value = "gruppe", required = false) gruppe: DokumentGruppe?,
    ): ResponseEntity<List<AnfrageDokumentResponseDto>> =
        try {
            val verwendeteGruppe = gruppe ?: DokumentGruppe.DIVERSE_DOKUMENTE
            ResponseEntity.ok(dateien.map { mappeDokumentZuDto(dateiSpeicherService.speichereAnfragesDatei(it, anfrageID, verwendeteGruppe)) })
        } catch (e: Exception) {
            log.error("Fehler beim Hochladen von Anfrage-Dokumenten fuer Anfrage {}", anfrageID, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }

    @PostMapping(value = ["/{anfrageID}/zugferd"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun erzeugeZugferd(
        @PathVariable anfrageID: Long,
        @RequestPart("datei") pdf: MultipartFile,
        @RequestPart("zugferdDaten") daten: ZugferdDaten,
    ): ResponseEntity<Any> =
        try {
            val original = Files.createTempFile("zugferd-original-", ".pdf.html")
            Files.copy(pdf.inputStream, original, StandardCopyOption.REPLACE_EXISTING)
            val zugferdPfad = zugferdErstellService.erzeuge(original.toString(), daten)
            Files.deleteIfExists(original)
            val dokument = dateiSpeicherService.speichereAnfragesZugferdDatei(zugferdPfad, pdf.originalFilename, anfrageID, daten)
            val dto = mappeDokumentZuDto(dokument)
            Files.deleteIfExists(zugferdPfad)
            ResponseEntity.status(HttpStatus.CREATED).body(dto)
        } catch (e: Exception) {
            log.warn("ZUGFeRD-Erzeugung fuer Anfrage {} fehlgeschlagen", anfrageID, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (e.message ?: "Unbekannter Fehler")))
        }

    @GetMapping("/{anfrageID}/dokumente")
    fun listeDokumente(
        @PathVariable anfrageID: Long,
        @RequestParam(value = "gruppe", required = false) gruppe: DokumentGruppe?,
    ): ResponseEntity<List<AnfrageDokumentResponseDto>> {
        val dokumente = dateiSpeicherService.holeDokumenteZuAnfrage(anfrageID)
            .filter { gruppe == null || it.dokumentGruppe == gruppe }
        return ResponseEntity.ok(dokumente.map(::mappeDokumentZuDto))
    }

    @GetMapping("/{anfrageID}/email-dokumente")
    fun emailDokumente(@PathVariable anfrageID: Long): ResponseEntity<List<AnfrageDokumentResponseDto>> {
        val collator = Collator.getInstance(Locale.GERMANY).apply { strength = Collator.PRIMARY }
        val dtos = dateiSpeicherService.holeDokumenteZuAnfrage(anfrageID)
            .filter { d ->
                val name = d.originalDateiname?.lowercase(Locale.ROOT).orEmpty()
                val isPdf = d.dateityp?.lowercase(Locale.ROOT)?.contains("pdf") == true || name.endsWith(".pdf") || name.endsWith(".pdf.html")
                val isDrawing = name.contains("zeichnung") || name.contains("entwurf")
                d is AnfrageGeschaeftsdokument || (isPdf && isDrawing)
            }
            .map(::mappeDokumentZuDto)
            .sortedWith(
                compareByDescending<AnfrageDokumentResponseDto> { it.uploadDatum }
                    .thenComparator { a, b -> collator.compare(a.originalDateiname, b.originalDateiname) },
            )
        return ResponseEntity.ok(dtos)
    }

    @DeleteMapping("/{anfrageID}/dokumente/{dokumentID}")
    fun loescheDokument(@PathVariable anfrageID: Long, @PathVariable dokumentID: Long): ResponseEntity<Void> =
        try {
            dateiSpeicherService.loescheAnfrageDatei(dokumentID)
            ResponseEntity.noContent().build()
        } catch (_: Exception) {
            ResponseEntity.notFound().build()
        }

    @GetMapping
    fun liste(
        @RequestParam(required = false) jahr: Int?,
        @RequestParam(required = false) kundenname: String?,
        @RequestParam(required = false) kunde: String?,
        @RequestParam(required = false) bauvorhaben: String?,
        @RequestParam(required = false) anfragesnummer: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "false") nurOhneProjekt: Boolean,
    ): List<AnfrageResponseDto> =
        anfrageService.suche(jahr, kundenname ?: kunde, bauvorhaben, anfragesnummer, q, nurOhneProjekt)

    @GetMapping(params = ["page"])
    fun listeSeite(
        @RequestParam(required = false) jahr: Int?,
        @RequestParam(required = false) kundenname: String?,
        @RequestParam(required = false) kunde: String?,
        @RequestParam(required = false) bauvorhaben: String?,
        @RequestParam(required = false) anfragesnummer: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "false") nurOhneProjekt: Boolean,
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "12") size: Int,
    ): AnfrageSeiteResponseDto =
        anfrageService.sucheSeite(jahr, kundenname ?: kunde, bauvorhaben, anfragesnummer, q, nurOhneProjekt, page, size)

    @GetMapping("/jahre")
    fun verfuegbareJahre(): List<Int> = anfrageService.verfuegbareAnlegeJahre()

    @DeleteMapping("/{id}")
    fun loesche(
        @PathVariable id: Long,
        @RequestParam(value = "cascadeKunde", defaultValue = "false") cascadeKunde: Boolean,
    ): ResponseEntity<Map<String, Any>> {
        val result = anfrageService.loescheMitPruefung(id, cascadeKunde)
        val body = linkedMapOf<String, Any>(
            "grund" to result.grund().name,
            "hinweis" to result.hinweis(),
            "kundeMitgeloescht" to result.kundeMitgeloescht(),
        )
        return when (result.grund()) {
            AnfrageService.LoeschGrund.OK -> ResponseEntity.ok(body)
            AnfrageService.LoeschGrund.NICHT_GEFUNDEN -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
            else -> ResponseEntity.status(HttpStatus.CONFLICT).body(body)
        }
    }

    @PutMapping("/{id}")
    fun aktualisiere(@PathVariable id: Long, @RequestBody dto: AnfrageErstellenDto): ResponseEntity<AnfrageResponseDto> {
        val aktualisiert = anfrageService.aktualisiereAnfrage(id, dto) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().header("X-Message", "Anfrage gespeichert").body(aktualisiert)
    }

    @PatchMapping(value = ["/{id}/kurzbeschreibung"], consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun updateKurzbeschreibung(@PathVariable id: Long, @RequestBody kurzbeschreibung: String): ResponseEntity<AnfrageResponseDto> =
        anfrageService.updateAnfrageKurzbeschreibung(id, kurzbeschreibung)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping(value = ["/{id}"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun aktualisiereMitBild(
        @PathVariable id: Long,
        @RequestPart("anfrageDto") dto: AnfrageErstellenDto,
        @RequestPart(value = "imageFile", required = false) imageFile: MultipartFile?,
    ): ResponseEntity<AnfrageResponseDto> {
        val aktualisiert = anfrageService.aktualisiereAnfrage(id, dto, imageFile) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().header("X-Message", "Anfrage gespeichert").body(aktualisiert)
    }

    @GetMapping("/{id}")
    fun hole(@PathVariable id: Long): ResponseEntity<AnfrageResponseDto> =
        anfrageService.findeDto(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/{id}/projekt-vorlage")
    fun projektVorlage(@PathVariable id: Long): ResponseEntity<ProjektErstellenDto> {
        val anfrage = anfrageService.finde(id) ?: return ResponseEntity.notFound().build()
        val dto = ProjektErstellenDto()
        dto.bauvorhaben = anfrage.bauvorhaben
        dto.kunde = anfrage.kunde?.name
        dto.bruttoPreis = anfrage.betrag
        dto.kundennummer = anfrage.kunde?.kundennummer
        dto.anlegedatum = anfrage.anlegedatum
        dto.kundenEmails = anfrage.kundenEmails.filterNotNull().distinct()
        anfrage.kunde?.kundennummer?.let { kundennummer ->
            dto.kundenId = kundeRepository.findByKundennummerIgnoreCase(kundennummer).map { it.id }.orElse(null)
        }
        dto.anfrageIds = listOfNotNull(anfrage.id)
        return ResponseEntity.ok(dto)
    }

    @GetMapping("/{id}/produktkategorien-vorschlag")
    fun produktkategorienVorschlag(@PathVariable id: Long): ResponseEntity<List<KategorieVorschlagDto>> =
        ResponseEntity.ok(ausgangsGeschaeftsDokumentService.berechneKategorieVorschlagFuerAnfrage(id))

    private fun mappeDokumentZuDto(dokument: AnfrageDokument): AnfrageDokumentResponseDto {
        val dto = AnfrageDokumentResponseDto()
        dto.id = dokument.id
        dto.originalDateiname = dokument.originalDateiname
        dto.gespeicherterDateiname = dokument.gespeicherterDateiname
        dto.dateityp = dokument.dateityp
        dto.url = "/api/dokumente/${dokument.gespeicherterDateiname}"
        dto.thumbnailUrl = "/api/dokumente/${dokument.gespeicherterDateiname}/thumbnail"
        val nameForType = dokument.originalDateiname?.lowercase(Locale.ROOT) ?: dokument.gespeicherterDateiname?.lowercase(Locale.ROOT).orEmpty()
        if (nameForType.endsWith(".sza") || nameForType.endsWith(".tcd")) {
            try {
                dto.netzwerkPfad = dateiSpeicherService.holeNetzwerkPfad(dokument.gespeicherterDateiname)
            } catch (_: Exception) {
            }
        }
        dto.dokumentGruppe = dokument.dokumentGruppe.name
        dto.uploadDatum = dokument.uploadDatum
        dto.emailVersandDatum = dokument.emailVersandDatum
        if (dokument is AnfrageGeschaeftsdokument) {
            dto.rechnungsnummer = dokument.dokumentid
            dto.geschaeftsdokumentart = dokument.geschaeftsdokumentart
            dto.rechnungsbetrag = dokument.bruttoBetrag
        }
        return dto
    }

    data class AnfrageNotizDto(
        var id: Long? = null,
        var notiz: String? = null,
        var erstelltAm: String? = null,
        var mitarbeiterId: Long? = null,
        var mitarbeiterVorname: String? = null,
        var mitarbeiterNachname: String? = null,
        var mobileSichtbar: Boolean = false,
        var nurFuerErsteller: Boolean = false,
        var canEdit: Boolean = false,
        var bilder: List<AnfrageNotizBildDto> = emptyList(),
    )

    data class AnfrageNotizBildDto(
        var id: Long? = null,
        var url: String? = null,
        var originalDateiname: String? = null,
        var erstelltAm: String? = null,
    )

    private fun resolveMitarbeiter(userProfileId: Long?, mitarbeiterId: Long?, token: String?): Mitarbeiter? {
        if (mitarbeiterId != null) return mitarbeiterRepository.findById(mitarbeiterId).orElse(null)
        if (!token.isNullOrBlank()) return mitarbeiterRepository.findByLoginToken(token).orElse(null)
        if (userProfileId != null) return frontendUserProfileService.findById(userProfileId).map { it.mitarbeiter }.orElse(null)
        return null
    }

    private fun hasEditPermission(notiz: AnfrageNotiz, requester: Mitarbeiter?, isMobile: Boolean): Boolean {
        if (requester == null) return false
        if (!isMobile) return true
        return notiz.mitarbeiter?.id == requester.id
    }

    private fun mapNotizToDto(notiz: AnfrageNotiz, currentMitarbeiter: Mitarbeiter?, isMobile: Boolean): AnfrageNotizDto =
        AnfrageNotizDto(
            id = notiz.id,
            notiz = notiz.notiz,
            erstelltAm = notiz.erstelltAm?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            mitarbeiterId = notiz.mitarbeiter?.id,
            mitarbeiterVorname = notiz.mitarbeiter?.vorname,
            mitarbeiterNachname = notiz.mitarbeiter?.nachname,
            mobileSichtbar = notiz.isMobileSichtbar(),
            nurFuerErsteller = notiz.isNurFuerErsteller(),
            canEdit = hasEditPermission(notiz, currentMitarbeiter, isMobile),
            bilder = notiz.bilder.map(::mapBildToDto),
        )

    private fun mapBildToDto(bild: AnfrageNotizBild): AnfrageNotizBildDto =
        AnfrageNotizBildDto(
            id = bild.id,
            originalDateiname = bild.originalDateiname,
            erstelltAm = bild.erstelltAm?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            url = "/api/images/${bild.gespeicherterDateiname}",
        )

    @GetMapping("/{anfrageId}/notizen")
    fun getNotizen(
        @PathVariable anfrageId: Long,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<List<AnfrageNotizDto>> {
        val notizen = anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(anfrageId)
        val requestingUser = resolveMitarbeiter(userProfileId, mitarbeiterId, token)
        val isMobile = !token.isNullOrBlank()
        val finalUser = requestingUser ?: Mitarbeiter()
        val dtos = notizen
            .filter { n -> !n.isNurFuerErsteller() || (finalUser.id != null && n.mitarbeiter?.id == finalUser.id) }
            .map { mapNotizToDto(it, finalUser, isMobile) }
        return ResponseEntity.ok(dtos)
    }

    @PostMapping("/{anfrageId}/emails")
    fun addAnfrageEmail(@PathVariable anfrageId: Long, @RequestBody body: Map<String, String>): ResponseEntity<Map<String, Any>> {
        val email = body["email"]?.trim()?.lowercase(Locale.ROOT)
        if (email.isNullOrEmpty()) return ResponseEntity.badRequest().body(mapOf("error" to "E-Mail-Adresse fehlt"))
        val anfrage = anfrageService.finde(anfrageId) ?: return ResponseEntity.notFound().build()
        if (anfrage.kundenEmails.contains(email)) {
            return ResponseEntity.ok(mapOf("message" to "E-Mail-Adresse bereits vorhanden", "added" to false))
        }
        anfrage.kundenEmails.add(email)
        anfrageService.speichere(anfrage)
        return ResponseEntity.ok(mapOf("message" to "E-Mail-Adresse gespeichert", "added" to true))
    }

    @PostMapping("/{anfrageId}/notizen")
    fun addNotiz(
        @PathVariable anfrageId: Long,
        @RequestBody dto: AnfrageNotizDto,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<AnfrageNotizDto> {
        val anfrage = anfrageService.finde(anfrageId) ?: return ResponseEntity.notFound().build()
        val mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val notiz = AnfrageNotiz().apply {
            this.anfrage = anfrage
            this.mitarbeiter = mitarbeiter
            this.notiz = dto.notiz
            this.mobileSichtbar = dto.mobileSichtbar
            this.nurFuerErsteller = dto.nurFuerErsteller
        }
        return ResponseEntity.ok(mapNotizToDto(anfrageNotizRepository.save(notiz), mitarbeiter, !token.isNullOrBlank()))
    }

    @PatchMapping("/{anfrageId}/notizen/{notizId}")
    fun updateNotiz(
        @PathVariable anfrageId: Long,
        @PathVariable notizId: Long,
        @RequestBody dto: AnfrageNotizDto,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<AnfrageNotizDto> {
        val notiz = anfrageNotizRepository.findById(notizId).orElseThrow { RuntimeException("Notiz nicht gefunden") }
        val mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token)
        val isMobile = !token.isNullOrBlank()
        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        dto.notiz?.let { notiz.notiz = it }
        notiz.mobileSichtbar = dto.mobileSichtbar
        notiz.nurFuerErsteller = dto.nurFuerErsteller
        return ResponseEntity.ok(mapNotizToDto(anfrageNotizRepository.save(notiz), mitarbeiter, isMobile))
    }

    @DeleteMapping("/{anfrageId}/notizen/{notizId}")
    fun deleteNotiz(
        @PathVariable anfrageId: Long,
        @PathVariable notizId: Long,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<Void> {
        val notiz = anfrageNotizRepository.findById(notizId).orElseThrow { RuntimeException("Notiz nicht gefunden") }
        val mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token)
        val isMobile = !token.isNullOrBlank()
        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        notiz.bilder.forEach { b ->
            try {
                dateiSpeicherService.loescheBild("/api/images/${b.gespeicherterDateiname}")
            } catch (_: Exception) {
            }
        }
        anfrageNotizRepository.delete(notiz)
        return ResponseEntity.ok().build()
    }

    @PostMapping(value = ["/{anfrageId}/notizen/{notizId}/bilder"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadNotizBild(
        @PathVariable anfrageId: Long,
        @PathVariable notizId: Long,
        @RequestParam("datei") file: MultipartFile,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<AnfrageNotizBildDto> {
        val notiz = anfrageNotizRepository.findById(notizId).orElseThrow { RuntimeException("Notiz nicht gefunden") }
        val mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token)
        if (!hasEditPermission(notiz, mitarbeiter, !token.isNullOrBlank())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        try {
            val url = dateiSpeicherService.speichereBild(file)
            val gespeicherterName = url.substring(url.lastIndexOf("/") + 1)
            val bild = AnfrageNotizBild().apply {
                this.notiz = notiz
                this.gespeicherterDateiname = gespeicherterName
                this.originalDateiname = file.originalFilename
                this.dateityp = file.contentType
            }
            return ResponseEntity.ok(mapBildToDto(anfrageNotizBildRepository.save(bild)))
        } catch (e: Exception) {
            throw RuntimeException("Fehler beim Hochladen des Bildes", e)
        }
    }

    @DeleteMapping("/{anfrageId}/notizen/{notizId}/bilder/{bildId}")
    fun deleteNotizBild(
        @PathVariable anfrageId: Long,
        @PathVariable notizId: Long,
        @PathVariable bildId: Long,
        @RequestHeader(value = "X-User-Profile-Id", required = false) userProfileId: Long?,
        @RequestHeader(value = "X-Mitarbeiter-Id", required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<Void> {
        val bild = anfrageNotizBildRepository.findById(bildId).orElseThrow { RuntimeException("Bild nicht gefunden") }
        val notiz = bild.notiz ?: return ResponseEntity.badRequest().build()
        if (notiz.id != notizId) return ResponseEntity.badRequest().build()
        val mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token)
        if (!hasEditPermission(notiz, mitarbeiter, !token.isNullOrBlank())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        try {
            dateiSpeicherService.loescheBild("/api/images/${bild.gespeicherterDateiname}")
        } catch (e: Exception) {
            log.warn("Notizbild {} konnte nicht vom Dateispeicher geloescht werden", bildId, e)
        }
        anfrageNotizBildRepository.delete(bild)
        return ResponseEntity.ok().build()
    }
}
