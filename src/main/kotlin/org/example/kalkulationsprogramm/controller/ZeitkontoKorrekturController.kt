package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.KorrekturTyp
import org.example.kalkulationsprogramm.domain.ZeitkontoKorrektur
import org.example.kalkulationsprogramm.service.ZeitkontoKorrekturService
import org.springframework.http.HttpStatus
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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.LinkedHashMap

@RestController
@RequestMapping("/api/zeitkonto/korrekturen")
class ZeitkontoKorrekturController(
    private val korrekturService: ZeitkontoKorrekturService,
) {
    @PostMapping
    fun erstelleKorrektur(@RequestBody body: Map<String, Any?>): ResponseEntity<*> =
        try {
            val mitarbeiterId = getLong(body, "mitarbeiterId")
            val stunden = getBigDecimal(body, "stunden")
            val datum = LocalDate.parse(body["datum"] as String)
            val grund = body["grund"] as String?
            val erstelltVonId = getLong(body, "erstelltVonId")
            val typ = (body["typ"] as String?)?.let(KorrekturTyp::valueOf) ?: KorrekturTyp.STUNDEN

            val korrektur = korrekturService.erstelleKorrektur(
                mitarbeiterId,
                stunden,
                datum,
                grund,
                erstelltVonId,
                ErfassungsQuelle.DESKTOP,
                typ,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(korrekturToMap(korrektur))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Fehler beim Erstellen: ${ex.message}"))
        }

    @PutMapping("/{id}")
    fun aendereKorrektur(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<*> =
        try {
            val stunden = if (body["stunden"] != null) getBigDecimal(body, "stunden") else null
            val grund = body["grund"] as String?
            val bearbeiterId = getLong(body, "bearbeiterId")
            val aenderungsgrund = body["aenderungsgrund"] as String?

            val korrektur = korrekturService.aendereKorrektur(
                id,
                stunden,
                grund,
                bearbeiterId,
                aenderungsgrund,
                ErfassungsQuelle.DESKTOP,
            )
            ResponseEntity.ok(korrekturToMap(korrektur))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        }

    @DeleteMapping("/{id}")
    fun storniereKorrektur(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<*> =
        try {
            val bearbeiterId = getLong(body, "bearbeiterId")
            val stornierungsgrund = body["stornierungsgrund"] as String?
            korrekturService.storniereKorrektur(id, bearbeiterId, stornierungsgrund, ErfassungsQuelle.DESKTOP)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Korrektur wurde storniert"))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        }

    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    fun getKorrekturenByMitarbeiter(
        @PathVariable mitarbeiterId: Long,
        @RequestParam(defaultValue = "false") alleAnzeigen: Boolean,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val korrekturen = if (alleAnzeigen) {
            korrekturService.getAlleKorrekturenByMitarbeiter(mitarbeiterId)
        } else {
            korrekturService.getAktiveKorrekturenByMitarbeiter(mitarbeiterId)
        }
        return ResponseEntity.ok(korrekturen.map(::korrekturToMap))
    }

    @GetMapping("/{id}/historie")
    fun getAuditHistorie(@PathVariable id: Long): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(korrekturService.getAuditHistorie(id))

    @GetMapping("/mitarbeiter/{mitarbeiterId}/summe")
    fun getSumme(
        @PathVariable mitarbeiterId: Long,
        @RequestParam(defaultValue = "0") jahr: Int,
    ): ResponseEntity<Map<String, Any>> {
        val effectiveJahr = if (jahr == 0) LocalDate.now().year else jahr
        val summe = korrekturService.summiereAktiveKorrekturen(mitarbeiterId, effectiveJahr)
        return ResponseEntity.ok(
            mapOf(
                "mitarbeiterId" to mitarbeiterId,
                "jahr" to effectiveJahr,
                "summeStunden" to summe,
            ),
        )
    }

    private fun korrekturToMap(korrektur: ZeitkontoKorrektur): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val mitarbeiter = korrektur.mitarbeiter
        map["id"] = korrektur.id
        map["mitarbeiterId"] = mitarbeiter?.id
        map["mitarbeiterName"] = "${mitarbeiter?.vorname} ${mitarbeiter?.nachname}"
        map["datum"] = korrektur.datum?.toString()
        map["stunden"] = korrektur.stunden
        map["grund"] = korrektur.grund
        map["version"] = korrektur.version
        map["typ"] = korrektur.typ?.name
        map["erstelltAm"] = korrektur.erstelltAm?.toString()
        map["erstelltVon"] = korrektur.erstelltVon?.let { "${it.vorname} ${it.nachname}" }
        map["storniert"] = korrektur.storniert
        if (korrektur.storniert == true) {
            map["storniertAm"] = korrektur.storniertAm?.toString()
            map["storniertVon"] = korrektur.storniertVon?.let { "${it.vorname} ${it.nachname}" }
            map["stornierungsgrund"] = korrektur.stornierungsgrund
        }
        return map
    }

    private fun getLong(body: Map<String, Any?>, key: String): Long {
        val value = body[key] ?: throw IllegalArgumentException("$key ist ein Pflichtfeld")
        return (value as Number).toLong()
    }

    private fun getBigDecimal(body: Map<String, Any?>, key: String): BigDecimal {
        val value = body[key] ?: throw IllegalArgumentException("$key ist ein Pflichtfeld")
        return if (value is BigDecimal) value else BigDecimal(value.toString())
    }
}
