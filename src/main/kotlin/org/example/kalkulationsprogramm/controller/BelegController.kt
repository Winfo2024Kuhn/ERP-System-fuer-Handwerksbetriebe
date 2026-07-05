package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.dto.BelegDto
import org.example.kalkulationsprogramm.service.BelegService
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException
import org.example.kalkulationsprogramm.service.MwstRechnerService
import org.slf4j.LoggerFactory
import org.springframework.core.io.UrlResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import java.util.Locale

@RestController
@RequestMapping("/api/buchhaltung")
class BelegController(
    private val belegService: BelegService,
    private val mwstRechnerService: MwstRechnerService,
) {
    private fun resolveCaller(token: String?, auth: Authentication?): Mitarbeiter? =
        belegService.findCaller(token, auth)

    private fun forbidden(msg: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("message" to msg))

    @GetMapping("/me/permissions")
    fun myPermissions(
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<BelegDto.PermissionResponse> {
        val caller = resolveCaller(token, auth)
        if (caller == null) {
            val response = BelegDto.PermissionResponse()
            response.isDarfSehen = false
            response.isDarfScannen = false
            return ResponseEntity.ok(
                response,
            )
        }
        return ResponseEntity.ok(belegService.getPermissions(caller))
    }

    @GetMapping("/mobile/me/permissions")
    fun myPermissionsMobile(@RequestParam(value = "token", required = false) token: String?): ResponseEntity<BelegDto.PermissionResponse> =
        myPermissions(token, null)

    @PostMapping(value = ["/mobile/belege"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadBelegMobile(
        @RequestPart("datei") datei: MultipartFile,
        @RequestParam(value = "lieferantId", required = false) lieferantId: Long?,
        @RequestParam(value = "aufteilungsModus", required = false) aufteilungsModus: String?,
        @RequestParam(value = "token", required = false) token: String?,
    ): ResponseEntity<*> =
        uploadBeleg(datei, lieferantId, aufteilungsModus, token, null)

    @PostMapping(value = ["/belege"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadBeleg(
        @RequestPart("datei") datei: MultipartFile,
        @RequestParam(value = "lieferantId", required = false) lieferantId: Long?,
        @RequestParam(value = "aufteilungsModus", required = false) aufteilungsModus: String?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "Nicht angemeldet"))
        }
        if (!belegService.darfScannen(caller)) {
            return forbidden("Keine Berechtigung zum Scannen von Belegen")
        }
        val modus = parseEnum(BelegAufteilungsModus::class.java, aufteilungsModus) ?: BelegAufteilungsModus.VOLLSTAENDIG
        return try {
            val beleg = belegService.uploadBeleg(datei, lieferantId, modus, caller)
            ResponseEntity.ok(belegService.toDto(beleg))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        } catch (ex: Exception) {
            log.error("Beleg-Upload fehlgeschlagen", ex)
            ResponseEntity.internalServerError().body(mapOf("message" to "Upload fehlgeschlagen"))
        }
    }

    @PutMapping("/belege/{id}/positionen")
    fun setzePositionsAuswahl(
        @PathVariable id: Long,
        @RequestBody req: BelegDto.PositionAuswahlRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        return try {
            ResponseEntity.ok(belegService.setzePositionsAuswahl(id, req.firmaPositionIds))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PutMapping("/mobile/belege/{id}/positionen")
    fun setzePositionsAuswahlMobile(
        @PathVariable id: Long,
        @RequestBody req: BelegDto.PositionAuswahlRequest,
        @RequestParam(value = "token", required = false) token: String?,
    ): ResponseEntity<*> =
        setzePositionsAuswahl(id, req, token, null)

    @GetMapping("/mobile/belege/{id}")
    fun getBelegMobile(
        @PathVariable id: Long,
        @RequestParam(value = "token", required = false) token: String?,
    ): ResponseEntity<BelegDto.Response> =
        getBeleg(id, token, null)

    @GetMapping("/mobile/belege")
    fun listBelegeMobile(@RequestParam(value = "token", required = false) token: String?): ResponseEntity<List<BelegDto.Response>> {
        val caller = resolveCaller(token, null)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(belegService.listBelegeFuerMobile(caller))
    }

    @PostMapping("/mwst-rechner")
    fun mwstRechner(
        @RequestBody(required = false) req: BelegDto.MwstRechnerRequest?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        if (req == null) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Request-Body fehlt"))
        }
        return try {
            val result = mwstRechnerService.berechne(req.netto, req.brutto, req.satzProzent)
            val response = BelegDto.MwstRechnerResponse()
            response.netto = result.netto
            response.brutto = result.brutto
            response.satzProzent = result.satzProzent
            response.mwstBetrag = result.mwstBetrag
            ResponseEntity.ok(response)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PostMapping("/mobile/mwst-rechner")
    fun mwstRechnerMobile(
        @RequestBody(required = false) req: BelegDto.MwstRechnerRequest?,
        @RequestParam(value = "token", required = false) token: String?,
    ): ResponseEntity<*> =
        mwstRechner(req, token, null)

    @PostMapping("/umbuchungen")
    fun createUmbuchung(
        @RequestBody req: BelegDto.UmbuchungCreateRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        return try {
            val beleg = belegService.createUmbuchung(req, caller)
            ResponseEntity.ok(belegService.toDto(beleg))
        } catch (ex: KasseUnterdeckungException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "message" to ex.message,
                    "projizierterSaldo" to ex.projizierterSaldo,
                    "mindestbestand" to ex.mindestbestand,
                ),
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        } catch (ex: Exception) {
            log.error("Umbuchung anlegen fehlgeschlagen", ex)
            ResponseEntity.internalServerError().body(mapOf("message" to "Anlegen fehlgeschlagen"))
        }
    }

    @GetMapping("/belege")
    fun listBelege(
        @RequestParam(value = "status", required = false) status: String?,
        @RequestParam(value = "kategorie", required = false) kategorie: String?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<List<BelegDto.Response>> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val st = parseEnum(BelegStatus::class.java, status)
        val kat = parseEnum(BelegKategorie::class.java, kategorie)
        return ResponseEntity.ok(belegService.listBelege(st, kat))
    }

    @GetMapping("/belege/{id}")
    fun getBeleg(
        @PathVariable id: Long,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<BelegDto.Response> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val response = belegService.getBeleg(id)
        return if (response == null) ResponseEntity.notFound().build() else ResponseEntity.ok(response)
    }

    @GetMapping("/belege/{id}/datei")
    fun downloadDatei(
        @PathVariable id: Long,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        val beleg = belegService.getRawBeleg(id) ?: return ResponseEntity.notFound().build<Any>()
        return try {
            val path = belegService.getBelegDatei(beleg)
            if (!Files.exists(path)) return ResponseEntity.notFound().build<Any>()
            val res = UrlResource(path.toUri())

            val storedMime = beleg.mimeType?.lowercase(Locale.ROOT)
            val inlineSicher = storedMime != null &&
                (
                    storedMime == "image/jpeg" || storedMime == "image/jpg" ||
                        storedMime == "image/png" || storedMime == "image/webp" ||
                        storedMime == "image/heic" || storedMime == "image/heif" ||
                        storedMime == "application/pdf"
                    )
            val contentType = if (inlineSicher) storedMime else "application/octet-stream"
            val filename = beleg.originalDateiname ?: "beleg-$id"
            val contentDisposition = (if (inlineSicher) ContentDisposition.inline() else ContentDisposition.attachment())
                .filename(filename, StandardCharsets.UTF_8)
                .build()

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(res)
        } catch (ex: Exception) {
            log.error("Beleg-Download fehlgeschlagen", ex)
            ResponseEntity.internalServerError().build<Any>()
        }
    }

    @PutMapping("/belege/{id}")
    fun updateBeleg(
        @PathVariable id: Long,
        @RequestBody req: BelegDto.UpdateRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        return try {
            val response = belegService.updateBeleg(id, req, caller)
            if (response == null) ResponseEntity.notFound().build<Any>() else ResponseEntity.ok(response)
        } catch (ex: KasseUnterdeckungException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "message" to ex.message,
                    "projizierterSaldo" to ex.projizierterSaldo,
                    "mindestbestand" to ex.mindestbestand,
                ),
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @DeleteMapping("/belege/{id}")
    fun deleteBeleg(
        @PathVariable id: Long,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        val ok = belegService.deleteBeleg(id)
        return if (ok) ResponseEntity.noContent().build<Any>() else ResponseEntity.notFound().build<Any>()
    }

    @GetMapping("/steuerberater-export")
    fun steuerberaterExport(
        @RequestParam(value = "von", required = false) von: String?,
        @RequestParam(value = "bis", required = false) bis: String?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        if (!von.isNullOrBlank() && parseDate(von) == null) {
            return ResponseEntity.badRequest().body(mapOf("message" to "'von' ist kein gueltiges Datum (YYYY-MM-DD)"))
        }
        if (!bis.isNullOrBlank() && parseDate(bis) == null) {
            return ResponseEntity.badRequest().body(mapOf("message" to "'bis' ist kein gueltiges Datum (YYYY-MM-DD)"))
        }
        val vonDate = parseDate(von)
        val bisDate = parseDate(bis)
        if (vonDate != null && bisDate != null && bisDate.isBefore(vonDate)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "'bis' liegt vor 'von'"))
        }
        return ResponseEntity.ok(belegService.listeFuerSteuerberaterExport(vonDate, bisDate))
    }

    @GetMapping("/kassenbuch")
    fun kassenbuch(
        @RequestParam(value = "von", required = false) von: String?,
        @RequestParam(value = "bis", required = false) bis: String?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        return ResponseEntity.ok(belegService.getKassenbuch(parseDate(von), parseDate(bis)))
    }

    companion object {
        private val log = LoggerFactory.getLogger(BelegController::class.java)

        private fun <E : Enum<E>> parseEnum(type: Class<E>, value: String?): E? {
            if (value.isNullOrBlank()) return null
            return try {
                java.lang.Enum.valueOf(type, value.trim().uppercase(Locale.ROOT))
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun parseDate(value: String?): LocalDate? {
            if (value.isNullOrBlank()) return null
            return try {
                LocalDate.parse(value)
            } catch (_: Exception) {
                null
            }
        }
    }
}
