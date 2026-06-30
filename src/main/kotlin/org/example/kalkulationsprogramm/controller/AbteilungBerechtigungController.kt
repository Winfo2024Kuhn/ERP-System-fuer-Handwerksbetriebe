package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.AbteilungDokumentBerechtigung
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.dto.AbteilungBerechtigungDto
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/abteilungen")
open class AbteilungBerechtigungController(
    private val abteilungRepository: AbteilungRepository,
    private val berechtigungRepository: AbteilungDokumentBerechtigungRepository,
) {

    @GetMapping("/berechtigungen")
    open fun getAllBerechtigungen(): ResponseEntity<List<AbteilungBerechtigungDto.Response>> {
        val result = abteilungRepository.findAll().map { toResponse(it) }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}/berechtigungen")
    open fun getBerechtigungen(@PathVariable id: Long): ResponseEntity<AbteilungBerechtigungDto.Response> {
        val abteilung = abteilungRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(toResponse(abteilung))
    }

    @PutMapping("/{id}/berechtigungen")
    @Transactional
    open fun updateBerechtigungen(
        @PathVariable id: Long,
        @RequestBody request: AbteilungBerechtigungDto.UpdateRequest,
    ): ResponseEntity<AbteilungBerechtigungDto.Response> {
        val abteilung = abteilungRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val existing = berechtigungRepository.findByAbteilungId(id)
        val grouped = existing
            .filter { it.dokumentTyp != null }
            .groupBy { it.dokumentTyp!! }

        val map = mutableMapOf<LieferantDokumentTyp, AbteilungDokumentBerechtigung>()
        val toDelete = mutableListOf<AbteilungDokumentBerechtigung>()

        grouped.forEach { (typ, list) ->
            map[typ] = list.first()
            if (list.size > 1) {
                toDelete.addAll(list.drop(1))
            }
        }

        if (toDelete.isNotEmpty()) {
            berechtigungRepository.deleteAll(toDelete)
            berechtigungRepository.flush()
        }

        request.darfRechnungenGenehmigen?.let { abteilung.darfRechnungenGenehmigen = it }
        request.darfRechnungenSehen?.let { abteilung.darfRechnungenSehen = it }
        request.darfFreigabeAnnahmePushen?.let { abteilung.darfFreigabeAnnahmePushen = it }
        request.darfWebseitenAnfragenPushen?.let { abteilung.darfWebseitenAnfragenPushen = it }
        abteilungRepository.save(abteilung)

        request.berechtigungen.orEmpty().forEach { tb ->
            val typ = tb.typ ?: return@forEach
            val berechtigung = map[typ] ?: AbteilungDokumentBerechtigung().also {
                it.abteilung = abteilung
                it.dokumentTyp = typ
            }
            berechtigung.darfSehen = tb.darfSehen == true
            berechtigung.darfScannen = tb.darfScannen == true
            berechtigungRepository.save(berechtigung)
        }

        return getBerechtigungen(id)
    }

    @GetMapping("/dokumenttypen")
    open fun getDokumentTypen(): ResponseEntity<List<String>> =
        ResponseEntity.ok(LieferantDokumentTyp.entries.map { it.name })

    private fun toResponse(abteilung: Abteilung): AbteilungBerechtigungDto.Response {
        val berechtigungen = berechtigungRepository.findByAbteilungId(abteilung.id)
        val map = berechtigungen
            .filter { it.dokumentTyp != null }
            .associateBy({ it.dokumentTyp!! }, { it })

        val typBerechtigungen = LieferantDokumentTyp.entries.map { typ ->
            val berechtigung = map[typ]
            AbteilungBerechtigungDto.TypBerechtigung.builder()
                .typ(typ)
                .darfSehen(berechtigung?.darfSehen == true)
                .darfScannen(berechtigung?.darfScannen == true)
                .build()
        }

        return AbteilungBerechtigungDto.Response.builder()
            .abteilungId(abteilung.id)
            .abteilungName(abteilung.name)
            .berechtigungen(typBerechtigungen)
            .darfRechnungenGenehmigen(abteilung.darfRechnungenGenehmigen == true)
            .darfRechnungenSehen(abteilung.darfRechnungenSehen == true)
            .darfFreigabeAnnahmePushen(abteilung.darfFreigabeAnnahmePushen == true)
            .darfWebseitenAnfragenPushen(abteilung.darfWebseitenAnfragenPushen == true)
            .build()
    }
}
