package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.KalenderEintrag
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.service.KalenderService
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
import java.time.LocalDate
import java.time.LocalTime

@RestController
@RequestMapping("/api/kalender")
class KalenderController(
    private val kalenderService: KalenderService,
    private val mitarbeiterRepository: MitarbeiterRepository,
) {
    data class TeilnehmerDto(
        val id: Long?,
        val name: String,
    ) {
        companion object {
            fun fromEntity(m: Mitarbeiter): TeilnehmerDto =
                TeilnehmerDto(m.id, "${m.vorname} ${m.nachname}")
        }
    }

    data class KalenderEintragDto(
        val id: Long?,
        val titel: String?,
        val beschreibung: String?,
        val datum: LocalDate?,
        val startZeit: LocalTime?,
        val endeZeit: LocalTime?,
        val ganztaegig: Boolean,
        val farbe: String?,
        val projektId: Long?,
        val projektName: String?,
        val kundeId: Long?,
        val kundeName: String?,
        val lieferantId: Long?,
        val lieferantName: String?,
        val anfrageId: Long?,
        val anfrageBetreff: String?,
        val erstellerId: Long?,
        val erstellerName: String?,
        val teilnehmer: List<TeilnehmerDto>,
    ) {
        companion object {
            fun fromEntity(e: KalenderEintrag): KalenderEintragDto {
                val teilnehmerDtos = e.teilnehmer.map(TeilnehmerDto::fromEntity)
                val projekt = e.projekt
                val kunde = e.kunde
                val lieferant = e.lieferant
                val anfrage = e.anfrage
                val ersteller = e.ersteller

                return KalenderEintragDto(
                    id = e.id,
                    titel = e.titel,
                    beschreibung = e.beschreibung,
                    datum = e.datum,
                    startZeit = e.startZeit,
                    endeZeit = e.endeZeit,
                    ganztaegig = e.isGanztaegig(),
                    farbe = e.farbe,
                    projektId = projekt?.id,
                    projektName = projekt?.bauvorhaben,
                    kundeId = kunde?.id,
                    kundeName = kunde?.name,
                    lieferantId = lieferant?.id,
                    lieferantName = lieferant?.lieferantenname,
                    anfrageId = anfrage?.id,
                    anfrageBetreff = anfrage?.bauvorhaben,
                    erstellerId = ersteller?.id,
                    erstellerName = ersteller?.let { "${it.vorname} ${it.nachname}" },
                    teilnehmer = teilnehmerDtos,
                )
            }
        }
    }

    @GetMapping
    fun getEintraege(
        @RequestParam jahr: Int,
        @RequestParam monat: Int,
        @RequestParam(required = false) mitarbeiterId: Long?,
    ): ResponseEntity<List<KalenderEintragDto>> {
        val eintraege =
            if (mitarbeiterId != null) {
                kalenderService.getEintraegeForMitarbeiter(mitarbeiterId, jahr, monat)
            } else {
                kalenderService.getEintraegeForMonat(jahr, monat)
            }
        return ResponseEntity.ok(eintraege.map(KalenderEintragDto::fromEntity))
    }

    @GetMapping("/mobile")
    fun getEintraegeMobile(
        @RequestParam token: String,
        @RequestParam jahr: Int,
        @RequestParam monat: Int,
    ): ResponseEntity<List<KalenderEintragDto>> {
        val mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null)
        if (mitarbeiter == null || mitarbeiter.aktiv != true) {
            return ResponseEntity.status(401).build()
        }

        val eintraege = kalenderService.getEintraegeForMitarbeiter(mitarbeiter.id, jahr, monat)
        return ResponseEntity.ok(eintraege.map(KalenderEintragDto::fromEntity))
    }

    @GetMapping("/mobile/tag")
    fun getEintraegeForTag(
        @RequestParam token: String,
        @RequestParam datum: String,
    ): ResponseEntity<List<KalenderEintragDto>> {
        val mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null)
        if (mitarbeiter == null || mitarbeiter.aktiv != true) {
            return ResponseEntity.status(401).build()
        }

        val localDate = LocalDate.parse(datum)
        val eintraege = kalenderService.getEintraegeForMitarbeiterTag(mitarbeiter.id, localDate)
        return ResponseEntity.ok(eintraege.map(KalenderEintragDto::fromEntity))
    }

    @GetMapping("/{id}")
    fun getEintrag(@PathVariable id: Long): ResponseEntity<KalenderEintragDto> {
        val eintrag = kalenderService.getEintragWithTeilnehmer(id)
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(eintrag))
    }

    data class KalenderEintragRequest(
        val titel: String?,
        val beschreibung: String?,
        val datum: LocalDate?,
        val startZeit: LocalTime?,
        val endeZeit: LocalTime?,
        val ganztaegig: Boolean,
        val farbe: String?,
        val projektId: Long?,
        val kundeId: Long?,
        val lieferantId: Long?,
        val anfrageId: Long?,
        val teilnehmerIds: List<Long>?,
        val erstellerId: Long?,
    )

    @PostMapping
    fun createEintrag(@RequestBody request: KalenderEintragRequest): ResponseEntity<KalenderEintragDto> {
        val eintrag = KalenderEintrag().apply {
            titel = request.titel
            beschreibung = request.beschreibung
            datum = request.datum
            startZeit = request.startZeit
            endeZeit = request.endeZeit
            ganztaegig = request.ganztaegig
            farbe = request.farbe
        }

        val saved = kalenderService.saveEintrag(
            eintrag,
            request.projektId,
            request.kundeId,
            request.lieferantId,
            request.anfrageId,
            request.erstellerId,
            request.teilnehmerIds,
        )
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved))
    }

    @PostMapping("/mobile")
    fun createEintragMobile(
        @RequestParam token: String,
        @RequestBody request: KalenderEintragRequest,
    ): ResponseEntity<KalenderEintragDto> {
        val mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null)
        if (mitarbeiter == null || mitarbeiter.aktiv != true) {
            return ResponseEntity.status(401).build()
        }

        val eintrag = KalenderEintrag().apply {
            titel = request.titel
            beschreibung = request.beschreibung
            datum = request.datum
            startZeit = request.startZeit
            endeZeit = request.endeZeit
            ganztaegig = request.ganztaegig
            farbe = request.farbe
        }

        val saved = kalenderService.saveEintrag(
            eintrag,
            request.projektId,
            request.kundeId,
            request.lieferantId,
            request.anfrageId,
            mitarbeiter.id,
            request.teilnehmerIds,
        )
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved))
    }

    @PutMapping("/{id}")
    fun updateEintrag(
        @PathVariable id: Long,
        @RequestBody request: KalenderEintragRequest,
    ): ResponseEntity<KalenderEintragDto> {
        val existing = kalenderService.getEintrag(id) ?: return ResponseEntity.notFound().build()

        existing.apply {
            titel = request.titel
            beschreibung = request.beschreibung
            datum = request.datum
            startZeit = request.startZeit
            endeZeit = request.endeZeit
            ganztaegig = request.ganztaegig
            farbe = request.farbe
        }

        val saved = kalenderService.saveEintrag(
            existing,
            request.projektId,
            request.kundeId,
            request.lieferantId,
            request.anfrageId,
            null,
            request.teilnehmerIds,
        )
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved))
    }

    @DeleteMapping("/{id}")
    fun deleteEintrag(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        kalenderService.deleteEintrag(id)
        return ResponseEntity.ok(mapOf("deleted" to true))
    }
}
