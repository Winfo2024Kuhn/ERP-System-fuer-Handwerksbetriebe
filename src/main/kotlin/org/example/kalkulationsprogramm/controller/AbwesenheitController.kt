package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Abwesenheit
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp
import org.example.kalkulationsprogramm.service.AbwesenheitService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/abwesenheit")
class AbwesenheitController(
    private val abwesenheitService: AbwesenheitService,
) {
    @PostMapping
    fun bucheAbwesenheit(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> =
        try {
            val mitarbeiterId = (body["mitarbeiterId"] as Number).toLong()
            val datum = LocalDate.parse(body["datum"] as String)
            val typ = AbwesenheitsTyp.valueOf(body["typ"] as String)
            val halberTag = body["halberTag"] as? Boolean ?: false
            val abwesenheit = abwesenheitService.bucheAbwesenheit(mitarbeiterId, datum, typ, halberTag)

            ResponseEntity.status(HttpStatus.CREATED).body(toResponse(abwesenheit))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Fehler beim Buchen: ${ex.message}"))
        }

    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    fun getAbwesenheitenByMitarbeiter(
        @PathVariable mitarbeiterId: Long,
        @RequestParam(required = false) von: String?,
        @RequestParam(required = false) bis: String?,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val abwesenheiten =
            if (von != null && bis != null) {
                abwesenheitService.getAbwesenheitenByMitarbeiterAndZeitraum(
                    mitarbeiterId,
                    LocalDate.parse(von),
                    LocalDate.parse(bis),
                )
            } else {
                abwesenheitService.getAbwesenheitenByMitarbeiter(mitarbeiterId)
            }

        return ResponseEntity.ok(
            abwesenheiten.map {
                toResponse(it) + mapOf(
                    "urlaubsantragId" to readLong(readField(it, "urlaubsantrag"), "id"),
                )
            },
        )
    }

    @GetMapping("/team")
    fun getTeamAbwesenheiten(
        @RequestParam von: String,
        @RequestParam bis: String,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val vonDate = LocalDate.parse(von)
        val bisDate = LocalDate.parse(bis)
        val abwesenheiten = abwesenheitService.getAllAbwesenheitenForZeitraum(vonDate, bisDate)

        return ResponseEntity.ok(
            abwesenheiten.map {
                val mitarbeiter = readField(it, "mitarbeiter")
                val name =
                    if (mitarbeiter != null) {
                        "${readString(mitarbeiter, "vorname") ?: ""} ${readString(mitarbeiter, "nachname") ?: ""}"
                    } else {
                        "Unbekannt"
                    }
                mapOf(
                    "id" to readLong(it, "id"),
                    "datum" to readLocalDate(it, "datum")?.toString(),
                    "typ" to (readField(it, "typ") as? AbwesenheitsTyp)?.name,
                    "stunden" to readField(it, "stunden"),
                    "mitarbeiterId" to (readLong(mitarbeiter, "id") ?: 0L),
                    "mitarbeiterName" to name,
                )
            },
        )
    }

    @DeleteMapping("/{id}")
    fun loescheAbwesenheit(@PathVariable id: Long): ResponseEntity<Any> =
        try {
            abwesenheitService.loescheAbwesenheit(id)
            ResponseEntity.noContent().build()
        } catch (_: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    private fun toResponse(abwesenheit: Abwesenheit): Map<String, Any?> =
        mapOf(
            "id" to readLong(abwesenheit, "id"),
            "datum" to readLocalDate(abwesenheit, "datum")?.toString(),
            "typ" to (readField(abwesenheit, "typ") as? AbwesenheitsTyp)?.name,
            "stunden" to readField(abwesenheit, "stunden"),
            "notiz" to (readString(abwesenheit, "notiz") ?: ""),
        )

    private fun readString(target: Any?, fieldName: String): String? =
        readField(target, fieldName) as? String

    private fun readLong(target: Any?, fieldName: String): Long? =
        (readField(target, fieldName) as? Number)?.toLong()

    private fun readLocalDate(target: Any?, fieldName: String): LocalDate? =
        readField(target, fieldName) as? LocalDate

    private fun readField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }
}
