package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Urlaubsantrag
import org.example.kalkulationsprogramm.service.UrlaubsantragService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/urlaub")
class UrlaubsantragController(
    private val service: UrlaubsantragService,
) {
    @PostMapping("/antraege")
    fun createAntrag(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        return try {
            val mitarbeiterId = (body["mitarbeiterId"] as Number).toLong()
            val von = LocalDate.parse(body["von"] as String)
            val bis = LocalDate.parse(body["bis"] as String)
            val bemerkung = body["bemerkung"] as String?
            val typStr = body["typ"] as String?
            val typ = if (typStr != null) Urlaubsantrag.Typ.valueOf(typStr) else Urlaubsantrag.Typ.URLAUB

            ResponseEntity.ok(service.createAntrag(mitarbeiterId, von, bis, bemerkung, typ))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/antraege")
    fun getAntraege(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) mitarbeiterId: Long?,
        @RequestParam(required = false) jahr: Int?,
    ): ResponseEntity<List<Urlaubsantrag>> {
        if (mitarbeiterId != null && jahr != null) {
            return ResponseEntity.ok(service.getAntraegeByMitarbeiterAndYear(mitarbeiterId, jahr))
        }

        if (mitarbeiterId != null && status != null) {
            try {
                val antragStatus = Urlaubsantrag.Status.valueOf(status)
                return ResponseEntity.ok(service.getAntraegeByMitarbeiterAndStatus(mitarbeiterId, antragStatus))
            } catch (_: IllegalArgumentException) {
            }
        }

        if (mitarbeiterId != null) {
            return ResponseEntity.ok(service.getAntraegeByMitarbeiter(mitarbeiterId))
        }

        if (!status.isNullOrBlank()) {
            try {
                val antragStatus = Urlaubsantrag.Status.valueOf(status)
                return ResponseEntity.ok(service.getAntraegeByStatus(antragStatus))
            } catch (_: IllegalArgumentException) {
            }
        }

        return ResponseEntity.ok(service.getOffeneAntraege())
    }

    @PutMapping("/antraege/{id}/approve")
    fun approveAntrag(@PathVariable id: Long): ResponseEntity<Urlaubsantrag> {
        return ResponseEntity.ok(service.approveAntrag(id))
    }

    @PutMapping("/antraege/{id}/reject")
    fun rejectAntrag(@PathVariable id: Long): ResponseEntity<Urlaubsantrag> {
        return ResponseEntity.ok(service.rejectAntrag(id))
    }

    @PutMapping("/antraege/{id}/storno")
    fun stornoAntrag(@PathVariable id: Long): ResponseEntity<Urlaubsantrag> {
        return ResponseEntity.ok(service.stornoAntrag(id))
    }

    @GetMapping("/resturlaub")
    fun getResturlaub(
        @RequestParam mitarbeiterId: Long,
        @RequestParam(required = false) jahr: Int?,
    ): ResponseEntity<Any> {
        val targetJahr = jahr ?: LocalDate.now().year
        return try {
            val verbleibend = service.getResturlaub(mitarbeiterId, targetJahr)
            ResponseEntity.ok(mapOf("verbleibend" to verbleibend, "jahr" to targetJahr))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/typen")
    fun getAbwesenheitsTypen(): ResponseEntity<List<Map<String, String>>> {
        val typen = listOf(
            mapOf("value" to "URLAUB", "label" to "Urlaub"),
            mapOf("value" to "KRANKHEIT", "label" to "Krankheit"),
            mapOf("value" to "FORTBILDUNG", "label" to "Fortbildung"),
            mapOf("value" to "ZEITAUSGLEICH", "label" to "Zeitausgleich"),
        )
        return ResponseEntity.ok(typen)
    }
}
