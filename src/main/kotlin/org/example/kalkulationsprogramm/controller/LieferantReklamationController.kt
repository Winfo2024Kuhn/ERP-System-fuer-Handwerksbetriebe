package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.controller.LieferantenController.LieferantBildDto
import org.example.kalkulationsprogramm.domain.LieferantBild
import org.example.kalkulationsprogramm.domain.LieferantReklamation
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.ReklamationStatus
import org.example.kalkulationsprogramm.dto.CreateReklamationRequest
import org.example.kalkulationsprogramm.dto.LieferantReklamationDto
import org.example.kalkulationsprogramm.repository.LieferantBildRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID

@RestController
@RequestMapping("/api/reklamationen")
class LieferantReklamationController(
    private val reklamationRepository: LieferantReklamationRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val dokumentRepository: LieferantDokumentRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val bildRepository: LieferantBildRepository,
) {
    @GetMapping("/lieferant/{lieferantId}")
    fun getByLieferant(@PathVariable lieferantId: Long): ResponseEntity<List<LieferantReklamationDto>> {
        if (!lieferantenRepository.existsById(lieferantId)) {
            return ResponseEntity.notFound().build()
        }
        val list = reklamationRepository.findByLieferantIdOrderByStatusAscErstelltAmDesc(lieferantId)
        return ResponseEntity.ok(list.map(::mapToDto))
    }

    @PatchMapping("/{id}/status")
    @Transactional
    fun updateStatus(@PathVariable id: Long, @RequestParam status: ReklamationStatus): ResponseEntity<Void> {
        val reklamation = reklamationRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        reklamation.status = status
        reklamationRepository.save(reklamation)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        if (!reklamationRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        reklamationRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/lieferant/{lieferantId}")
    @Transactional
    fun create(
        @PathVariable lieferantId: Long,
        @RequestBody request: CreateReklamationRequest,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<LieferantReklamationDto> {
        val lieferant = lieferantenRepository.findById(lieferantId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val mitarbeiter = mitarbeiterByToken(token)

        var reklamation = LieferantReklamation().apply {
            this.lieferant = lieferant
            erstelltVon = mitarbeiter
            beschreibung = request.beschreibung
            status = request.status ?: ReklamationStatus.OFFEN
        }

        request.lieferscheinId?.let { lieferscheinId ->
            val lieferschein = dokumentRepository.findById(lieferscheinId).orElse(null)
            if (lieferschein != null && lieferschein.lieferant?.id == lieferantId) {
                reklamation.lieferschein = lieferschein
            }
        }

        reklamation = reklamationRepository.save(reklamation)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(reklamation))
    }

    @PostMapping(value = ["/{id}/bilder"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Transactional
    fun uploadBild(
        @PathVariable id: Long,
        @RequestPart("datei") datei: MultipartFile,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<LieferantBildDto> {
        val reklamation = reklamationRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val mitarbeiter = mitarbeiterByToken(token)

        return try {
            val originalFilename = StringUtils.cleanPath(Objects.requireNonNull(datei.originalFilename))
            val storedFilename = "${UUID.randomUUID()}_$originalFilename"
            val lieferantDir = Paths.get("uploads", "lieferanten", reklamation.lieferant?.id.toString(), "bilder")
            Files.createDirectories(lieferantDir)
            val targetPath = lieferantDir.resolve(storedFilename)
            datei.transferTo(targetPath)

            var bild = LieferantBild().apply {
                lieferant = reklamation.lieferant
                this.reklamation = reklamation
                originalDateiname = originalFilename
                gespeicherterDateiname = storedFilename
                erstelltAm = LocalDateTime.now()
                hochgeladenVon = mitarbeiter
            }
            bild = bildRepository.save(bild)

            ResponseEntity.status(HttpStatus.CREATED).body(toBildDto(bild, mitarbeiter))
        } catch (_: IOException) {
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/lieferscheine/search")
    fun searchLieferscheine(
        @RequestParam lieferantId: Long,
        @RequestParam query: String,
    ): ResponseEntity<List<LieferscheinSearchDto>> {
        val results = dokumentRepository.searchLieferscheine(lieferantId, query)
        val dtos = results.map { doc ->
            LieferscheinSearchDto().apply {
                id = doc.id
                originalDateiname = doc.getEffektiverDateiname()
                if (doc.geschaeftsdaten != null) {
                    dokumentNummer = doc.geschaeftsdaten?.dokumentNummer
                    datum = doc.geschaeftsdaten?.dokumentDatum
                } else {
                    datum = doc.uploadDatum?.toLocalDate()
                }
            }
        }
        return ResponseEntity.ok(dtos)
    }

    private fun mapToDto(entity: LieferantReklamation): LieferantReklamationDto =
        LieferantReklamationDto().apply {
            id = entity.id
            lieferantId = entity.lieferant?.id
            lieferantName = entity.lieferant?.lieferantenname

            entity.lieferschein?.let { lieferschein ->
                lieferscheinId = lieferschein.id
                lieferscheinDateiname = lieferschein.getEffektiverDateiname()
                lieferscheinNummer = lieferschein.geschaeftsdaten?.dokumentNummer
            }

            erstellerName = entity.erstelltVon?.let { "${it.vorname} ${it.nachname}" }
            erstelltAm = entity.erstelltAm
            beschreibung = entity.beschreibung
            status = entity.status
            bilder = entity.bilder.map { toBildDto(it, it.hochgeladenVon) }
        }

    private fun toBildDto(bild: LieferantBild, mitarbeiter: Mitarbeiter?): LieferantBildDto =
        LieferantBildDto().apply {
            id = bild.id
            originalDateiname = bild.originalDateiname
            url = "/api/lieferanten/bilder/file/${bild.gespeicherterDateiname}"
            beschreibung = bild.beschreibung
            erstelltAm = bild.erstelltAm
            if (mitarbeiter != null) {
                mitarbeiterVorname = mitarbeiter.vorname
                mitarbeiterNachname = mitarbeiter.nachname
            }
        }

    private fun mitarbeiterByToken(token: String?): Mitarbeiter? {
        if (!StringUtils.hasText(token)) {
            return null
        }
        return mitarbeiterRepository.findByLoginToken(token!!).orElse(null)
    }

    class LieferscheinSearchDto {
        var id: Long? = null
        var dokumentNummer: String? = null
        var originalDateiname: String? = null
        var datum: LocalDate? = null
    }
}
