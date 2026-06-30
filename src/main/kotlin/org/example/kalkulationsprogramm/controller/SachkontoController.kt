package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Sachkonto
import org.example.kalkulationsprogramm.domain.SachkontoTyp
import org.example.kalkulationsprogramm.dto.SachkontoDto
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.SachkontoRepository
import org.example.kalkulationsprogramm.service.BelegService
import org.example.kalkulationsprogramm.service.BelegeKasseExportPdfService
import org.slf4j.LoggerFactory
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@RestController
@RequestMapping("/api/buchhaltung")
class SachkontoController(
    private val sachkontoRepository: SachkontoRepository,
    private val belegRepository: BelegRepository,
    private val belegService: BelegService,
    private val belegeKasseExportPdfService: BelegeKasseExportPdfService,
) {
    private fun resolveCaller(token: String?, auth: Authentication?): Mitarbeiter? =
        belegService.findCaller(token, auth)

    @GetMapping("/sachkonten")
    fun list(
        @RequestParam(value = "nurAktive", defaultValue = "true") nurAktive: Boolean,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<List<SachkontoDto.Response>> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val konten = if (nurAktive) {
            sachkontoRepository.findByAktivTrueOrderBySortierungAscBezeichnungAsc()
        } else {
            sachkontoRepository.findAllByOrderBySortierungAscBezeichnungAsc()
        }
        return ResponseEntity.ok(konten.map(::toDto))
    }

    @PostMapping("/sachkonten")
    @Transactional
    fun create(@RequestBody req: SachkontoDto.UpsertRequest, auth: Authentication?): ResponseEntity<*> {
        val caller = resolveCaller(null, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        if (req.bezeichnung.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bezeichnung fehlt"))
        }
        val sachkonto = Sachkonto()
        applyUpsert(sachkonto, req)
        sachkontoRepository.save(sachkonto)
        return ResponseEntity.ok(toDto(sachkonto))
    }

    @PutMapping("/sachkonten/{id}")
    @Transactional
    fun update(
        @PathVariable id: Long,
        @RequestBody req: SachkontoDto.UpsertRequest,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(null, auth)
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        val sachkonto = sachkontoRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        applyUpsert(sachkonto, req)
        sachkontoRepository.save(sachkonto)
        return ResponseEntity.ok(toDto(sachkonto))
    }

    @GetMapping("/auswertung")
    fun auswertung(
        @RequestParam(value = "von", required = false) von: String?,
        @RequestParam(value = "bis", required = false) bis: String?,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<SachkontoDto.AuswertungResponse> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val vonDate = parseDate(von)
        val bisDate = parseDate(bis)
        val alle = belegRepository.findByStatusOrderByUploadDatumDesc(BelegStatus.VALIDIERT)

        val summen = HashMap<Long, BigDecimal>()
        val counts = HashMap<Long, Int>()
        var ohneKonto = BigDecimal.ZERO
        var ohneKontoAnzahl = 0

        for (beleg in alle) {
            val datum = beleg.belegDatum
            if (vonDate != null && (datum == null || datum.isBefore(vonDate))) continue
            if (bisDate != null && (datum == null || datum.isAfter(bisDate))) continue
            val brutto = beleg.betragBrutto ?: BigDecimal.ZERO
            val sachkonto = beleg.sachkonto
            if (sachkonto == null) {
                ohneKonto = ohneKonto.add(brutto)
                ohneKontoAnzahl++
            } else {
                val key = sachkonto.id
                if (key != null) {
                    summen.merge(key, brutto, BigDecimal::add)
                    counts.merge(key, 1, Int::plus)
                }
            }
        }

        val zeilen = ArrayList<SachkontoDto.AuswertungZeile>()
        var sumAufwand = BigDecimal.ZERO
        var sumErtrag = BigDecimal.ZERO
        var sumPrivat = BigDecimal.ZERO

        for (sachkonto in sachkontoRepository.findAllByOrderBySortierungAscBezeichnungAsc()) {
            val summe = summen[sachkonto.id] ?: continue
            zeilen.add(
                SachkontoDto.AuswertungZeile.builder()
                    .sachkontoId(sachkonto.id)
                    .nummer(sachkonto.nummer)
                    .bezeichnung(sachkonto.bezeichnung)
                    .kontoTyp(sachkonto.kontoTyp?.name)
                    .summe(summe.setScale(2, RoundingMode.HALF_UP))
                    .anzahlBelege(counts.getOrDefault(sachkonto.id, 0))
                    .build(),
            )
            when (sachkonto.kontoTyp) {
                SachkontoTyp.AUFWAND -> sumAufwand = sumAufwand.add(summe)
                SachkontoTyp.ERTRAG -> sumErtrag = sumErtrag.add(summe)
                SachkontoTyp.PRIVAT -> sumPrivat = sumPrivat.add(summe)
                SachkontoTyp.NEUTRAL, null -> Unit
            }
        }

        if (ohneKonto.signum() != 0) {
            zeilen.add(
                SachkontoDto.AuswertungZeile.builder()
                    .sachkontoId(null)
                    .bezeichnung("(Noch keinem Konto zugeordnet)")
                    .kontoTyp(null)
                    .summe(ohneKonto.setScale(2, RoundingMode.HALF_UP))
                    .anzahlBelege(ohneKontoAnzahl)
                    .build(),
            )
        }

        return ResponseEntity.ok(
            SachkontoDto.AuswertungResponse.builder()
                .von(vonDate?.toString())
                .bis(bisDate?.toString())
                .summeAufwand(sumAufwand.setScale(2, RoundingMode.HALF_UP))
                .summeErtrag(sumErtrag.setScale(2, RoundingMode.HALF_UP))
                .summePrivat(sumPrivat.setScale(2, RoundingMode.HALF_UP))
                .summeOhneKonto(ohneKonto.setScale(2, RoundingMode.HALF_UP))
                .zeilen(zeilen)
                .build(),
        )
    }

    @GetMapping("/auswertung/monat/pdf")
    fun auswertungMonatPdf(
        @RequestParam("jahr") jahr: Int,
        @RequestParam("monat") monat: Int,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = resolveCaller(token, auth)
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        if (monat < 1 || monat > 12 || jahr < 2000 || jahr > 2999) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Ungültiger Monat oder Jahr"))
        }
        return try {
            val pdfPath = belegeKasseExportPdfService.generatePdf(jahr, monat)
            val resource = UrlResource(pdfPath.toUri())
            val filename = "Belege-Kasse-%04d-%02d.pdf".format(jahr, monat)
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$filename\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource)
        } catch (ex: Exception) {
            log.error("Belege-Monatsexport fehlgeschlagen", ex)
            ResponseEntity.internalServerError().body(mapOf("message" to "PDF-Erzeugung fehlgeschlagen"))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SachkontoController::class.java)

        private fun applyUpsert(sachkonto: Sachkonto, req: SachkontoDto.UpsertRequest) {
            req.nummer?.let { sachkonto.nummer = if (it.isBlank()) null else it.trim() }
            req.bezeichnung?.let { sachkonto.bezeichnung = it.trim() }
            req.kontoTyp?.let {
                try {
                    sachkonto.kontoTyp = SachkontoTyp.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    // Wert unveraendert lassen.
                }
            }
            req.beschreibung?.let { sachkonto.beschreibung = it }
            req.aktiv?.let { sachkonto.aktiv = it }
            req.sortierung?.let { sachkonto.sortierung = it }
        }

        private fun toDto(sachkonto: Sachkonto): SachkontoDto.Response =
            SachkontoDto.Response.builder()
                .id(sachkonto.id)
                .nummer(sachkonto.nummer)
                .bezeichnung(sachkonto.bezeichnung)
                .kontoTyp(sachkonto.kontoTyp?.name)
                .beschreibung(sachkonto.beschreibung)
                .aktiv(sachkonto.isAktiv())
                .sortierung(sachkonto.sortierung)
                .build()

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
