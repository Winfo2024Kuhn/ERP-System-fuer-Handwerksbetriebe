package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.KostenstellenTyp
import org.example.kalkulationsprogramm.dto.EmailAbsenderDto
import org.example.kalkulationsprogramm.dto.FirmeninformationDto
import org.example.kalkulationsprogramm.dto.KostenstelleDto
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto
import org.example.kalkulationsprogramm.service.EmailAbsenderService
import org.example.kalkulationsprogramm.service.FirmeninformationService
import org.example.kalkulationsprogramm.service.KostenstelleService
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

@RestController
@RequestMapping("/api/firma")
class FirmaController(
    private val firmeninformationService: FirmeninformationService,
    private val kostenstelleService: KostenstelleService,
    private val steuerberaterKontaktService: SteuerberaterKontaktService,
    private val emailAbsenderService: EmailAbsenderService,
) {
    @GetMapping
    fun getFirmeninformation(): ResponseEntity<FirmeninformationDto> {
        return ResponseEntity.ok(firmeninformationService.getFirmeninformation())
    }

    @PutMapping
    fun speichernFirmeninformation(@RequestBody dto: FirmeninformationDto): ResponseEntity<FirmeninformationDto> {
        return ResponseEntity.ok(firmeninformationService.speichern(dto))
    }

    @PostMapping(value = ["/logo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadLogo(@RequestParam("datei") datei: MultipartFile): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(firmeninformationService.speichereLogoDatei(datei))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        } catch (e: IOException) {
            ResponseEntity.internalServerError().body(mapOf("message" to "Speichern fehlgeschlagen"))
        }
    }

    @GetMapping("/logo")
    fun downloadLogo(): ResponseEntity<ByteArray> {
        val bytes = firmeninformationService.loadLogoBytes() ?: return ResponseEntity.notFound().build()
        val contentType = firmeninformationService.ermittleLogoContentType()
        val mediaType = if (contentType != null) MediaType.parseMediaType(contentType) else MediaType.APPLICATION_OCTET_STREAM
        return ResponseEntity.ok().contentType(mediaType).body(bytes)
    }

    @DeleteMapping("/logo")
    fun deleteLogo(): ResponseEntity<FirmeninformationDto> {
        return ResponseEntity.ok(firmeninformationService.loescheLogoDatei())
    }

    @GetMapping("/kostenstellen")
    fun getKostenstellen(): ResponseEntity<List<KostenstelleDto>> {
        return ResponseEntity.ok(kostenstelleService.findAll())
    }

    @GetMapping("/kostenstellen/typ/{typ}")
    fun getKostenstellenByTyp(@PathVariable typ: KostenstellenTyp): ResponseEntity<List<KostenstelleDto>> {
        return ResponseEntity.ok(kostenstelleService.findByTyp(typ))
    }

    @GetMapping("/kostenstellen/{id}")
    fun getKostenstelle(@PathVariable id: Long): ResponseEntity<KostenstelleDto> {
        val dto = kostenstelleService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @PostMapping("/kostenstellen")
    fun erstelleKostenstelle(@RequestBody dto: KostenstelleDto): ResponseEntity<KostenstelleDto> {
        dto.id = null
        return ResponseEntity.ok(kostenstelleService.speichern(dto))
    }

    @PutMapping("/kostenstellen/{id}")
    fun aktualisiereKostenstelle(@PathVariable id: Long, @RequestBody dto: KostenstelleDto): ResponseEntity<KostenstelleDto> {
        dto.id = id
        return ResponseEntity.ok(kostenstelleService.speichern(dto))
    }

    @DeleteMapping("/kostenstellen/{id}")
    fun loescheKostenstelle(@PathVariable id: Long): ResponseEntity<Void> {
        kostenstelleService.loeschen(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/kostenstellen/init")
    fun initKostenstellen(): ResponseEntity<List<KostenstelleDto>> {
        kostenstelleService.erstelleStandardKostenstellen()
        return ResponseEntity.ok(kostenstelleService.findAll())
    }

    @GetMapping("/steuerberater")
    fun getSteuerberater(): ResponseEntity<List<SteuerberaterKontaktDto>> {
        return ResponseEntity.ok(steuerberaterKontaktService.findAll())
    }

    @GetMapping("/steuerberater/{id}")
    fun getSteuerberaterById(@PathVariable id: Long): ResponseEntity<SteuerberaterKontaktDto> {
        val dto = steuerberaterKontaktService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @PostMapping("/steuerberater")
    fun erstelleSteuerberater(@RequestBody dto: SteuerberaterKontaktDto): ResponseEntity<SteuerberaterKontaktDto> {
        dto.id = null
        return ResponseEntity.ok(steuerberaterKontaktService.speichern(dto))
    }

    @PutMapping("/steuerberater/{id}")
    fun aktualisiereSteuerberater(
        @PathVariable id: Long,
        @RequestBody dto: SteuerberaterKontaktDto,
    ): ResponseEntity<SteuerberaterKontaktDto> {
        dto.id = id
        return ResponseEntity.ok(steuerberaterKontaktService.speichern(dto))
    }

    @DeleteMapping("/steuerberater/{id}")
    fun loescheSteuerberater(@PathVariable id: Long): ResponseEntity<Void> {
        steuerberaterKontaktService.loeschen(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/email-absender")
    fun getEmailAbsender(): ResponseEntity<List<EmailAbsenderDto>> {
        return ResponseEntity.ok(emailAbsenderService.findAll())
    }

    @PostMapping("/email-absender")
    fun erstelleEmailAbsender(@RequestBody dto: EmailAbsenderDto): ResponseEntity<Any> {
        return try {
            dto.id = null
            ResponseEntity.ok(emailAbsenderService.save(dto))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PutMapping("/email-absender/{id}")
    fun aktualisiereEmailAbsender(@PathVariable id: Long, @RequestBody dto: EmailAbsenderDto): ResponseEntity<Any> {
        return try {
            dto.id = id
            ResponseEntity.ok(emailAbsenderService.save(dto))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @DeleteMapping("/email-absender/{id}")
    fun loescheEmailAbsender(@PathVariable id: Long): ResponseEntity<Void> {
        emailAbsenderService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
