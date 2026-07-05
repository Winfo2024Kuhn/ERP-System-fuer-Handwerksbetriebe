package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.BuchungsTyp
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.Feiertag
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.domain.Zeitkonto
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.example.kalkulationsprogramm.service.FeiertagService
import org.example.kalkulationsprogramm.service.MonatsSaldoService
import org.example.kalkulationsprogramm.service.MonatsSaldoWarmupService
import org.example.kalkulationsprogramm.service.ProjektAuswertungPdfService
import org.example.kalkulationsprogramm.service.ZeitbuchungAuditService
import org.example.kalkulationsprogramm.service.ZeitkontoService
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.ArrayDeque

@RestController
@RequestMapping("/api/zeitverwaltung")
class ZeitverwaltungController(
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val abwesenheitRepository: AbwesenheitRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val feiertagService: FeiertagService,
    private val zeitkontoService: ZeitkontoService,
    private val projektAuswertungPdfService: ProjektAuswertungPdfService,
    private val projektRepository: ProjektRepository,
    private val arbeitsgangStundensatzRepository: ArbeitsgangStundensatzRepository,
    private val arbeitsgangRepository: ArbeitsgangRepository,
    private val auditService: ZeitbuchungAuditService,
    private val frontendUserProfileRepository: FrontendUserProfileRepository,
    private val monatsSaldoService: MonatsSaldoService,
    private val monatsSaldoWarmupService: MonatsSaldoWarmupService,
) {
    @GetMapping("/buchungen")
    fun getBuchungen(
        @RequestParam mitarbeiterId: Long,
        @RequestParam jahr: Int,
        @RequestParam monat: Int,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val start = LocalDateTime.of(jahr, monat, 1, 0, 0)
        val end = YearMonth.of(jahr, monat).atEndOfMonth().atTime(23, 59, 59)
        val result = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(mitarbeiterId, start)
            .filter { it.startZeit?.let { s -> s.isBefore(end) && s.isAfter(start.minusSeconds(1)) } == true }
            .map(::buchungToMap)
        return ResponseEntity.ok(result)
    }

    @PutMapping("/buchungen/{id}")
    fun updateBuchung(@PathVariable id: Long, @RequestBody data: Map<String, Any?>): ResponseEntity<Any> {
        val aenderungsgrund = data["aenderungsgrund"] as? String
        if (aenderungsgrund.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Aenderungsgrund ist ein Pflichtfeld"))
        }
        val bearbeiter = resolveBearbeiter(data["bearbeiterId"]?.toString()?.toLongOrNull())
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Bearbeiter konnte nicht ermittelt werden"))
        val buchung = zeitbuchungRepository.findById(id).orElseThrow { IllegalArgumentException("Buchung nicht gefunden: $id") }
        applyBuchungChanges(buchung, data)
        recalcDauer(buchung)
        buchung.markiereAlsGeaendert(bearbeiter)
        auditService.protokolliereAenderung(buchung, bearbeiter, ErfassungsQuelle.DESKTOP, aenderungsgrund)
        val saved = zeitbuchungRepository.save(buchung)
        buchung.mitarbeiter?.id?.let { monatsSaldoService.invalidiereFuerDateTime(it, buchung.startZeit) }
        return ResponseEntity.ok(buchungToMap(saved))
    }

    @DeleteMapping("/buchungen/{id}")
    fun deleteBuchung(
        @PathVariable id: Long,
        @RequestParam(required = false) bearbeiterId: Long?,
        @RequestParam(required = false) grund: String?,
    ): ResponseEntity<Void> {
        val bearbeiter = resolveBearbeiter(bearbeiterId)
        val buchung = zeitbuchungRepository.findById(id).orElse(null)
        val mitarbeiterId = buchung?.mitarbeiter?.id
        val startZeit = buchung?.startZeit
        if (buchung != null && bearbeiter != null) {
            buchung.markiereAlsGeaendert(bearbeiter)
            zeitbuchungRepository.save(buchung)
            auditService.protokolliereStorno(buchung, bearbeiter, ErfassungsQuelle.DESKTOP, grund?.takeIf { it.isNotBlank() } ?: "Manuelle Loeschung im Buero")
        }
        zeitbuchungRepository.deleteById(id)
        if (mitarbeiterId != null && startZeit != null) monatsSaldoService.invalidiereFuerDateTime(mitarbeiterId, startZeit)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/buchungen/{id}/historie")
    fun getBuchungHistorie(@PathVariable id: Long): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(auditService.getHistorie(id))

    @GetMapping("/aenderungsgruende")
    fun getAenderungsgruende(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(auditService.getAenderungsgruende())

    @PostMapping("/buchungen")
    fun createBuchung(@RequestBody data: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val buchung = Zeitbuchung()
        val typ = runCatching { BuchungsTyp.valueOf(data["typ"]?.toString() ?: "ARBEIT") }.getOrDefault(BuchungsTyp.ARBEIT)
        buchung.typ = typ
        data["mitarbeiterId"]?.toString()?.toLongOrNull()?.let { mitarbeiterId ->
            buchung.mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId).orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $mitarbeiterId") }
        }
        val projektId = data["projektId"]?.toString()?.toLongOrNull()
        if (projektId != null && projektId > 0) {
            buchung.projekt = projektRepository.findById(projektId).orElseThrow { IllegalArgumentException("Projekt nicht gefunden: $projektId") }
        } else if (typ != BuchungsTyp.PAUSE) {
            throw IllegalArgumentException("projektId ist erforderlich fuer Arbeitsbuchungen")
        }
        applyArbeitsgang(buchung, data)
        applyProduktkategorie(buchung, data)
        data["startZeit"]?.toString()?.let { buchung.startZeit = LocalDateTime.parse(it) }
        data["endeZeit"]?.toString()?.let { buchung.endeZeit = LocalDateTime.parse(it) }
        if (data.containsKey("notiz")) buchung.notiz = data["notiz"] as? String
        recalcDauer(buchung)
        val saved = zeitbuchungRepository.save(buchung)
        saved.mitarbeiter?.id?.let { monatsSaldoService.invalidiereFuerDateTime(it, saved.startZeit) }
        return ResponseEntity.status(HttpStatus.CREATED).body(buchungToMap(saved))
    }

    @GetMapping("/kalender")
    fun getKalender(
        @RequestParam mitarbeiterId: Long,
        @RequestParam jahr: Int,
        @RequestParam monat: Int,
    ): ResponseEntity<Map<String, Any?>> {
        val yearMonth = YearMonth.of(jahr, monat)
        val ersterTag = yearMonth.atDay(1)
        val letzterTag = yearMonth.atEndOfMonth()
        val feiertage = feiertagService.getFeiertageZwischen(ersterTag, letzterTag)
        val feiertagDaten = feiertage.mapNotNull { it.datum }.toSet()
        val zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId)
        val buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(mitarbeiterId, ersterTag.atStartOfDay())
        val abwesenheiten = abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag)
        val buchungenProTag = linkedMapOf<LocalDate, MutableList<Map<String, Any?>>>()
        buchungen.filter { it.startZeit?.toLocalDate()?.let { d -> !d.isBefore(ersterTag) && !d.isAfter(letzterTag) } == true }
            .forEach { b -> b.startZeit?.toLocalDate()?.let { d -> buchungenProTag.computeIfAbsent(d) { mutableListOf() }.add(buchungToMap(b)) } }
        abwesenheiten.forEach { a ->
            val map = linkedMapOf<String, Any?>(
                "id" to -(a.id ?: 0L),
                "abwesenheitId" to a.id,
                "projektId" to null,
                "projektName" to a.typ?.name,
                "arbeitsgangId" to null,
                "arbeitsgangName" to "",
                "startZeit" to "08:00:00",
                "endeZeit" to null,
                "dauerMinuten" to a.stunden?.multiply(BigDecimal("60"))?.toLong(),
                "dauerFormatiert" to a.stunden?.let { "${it}h" },
                "notiz" to a.notiz,
                "typ" to a.typ?.name,
            )
            a.datum?.let { buchungenProTag.computeIfAbsent(it) { mutableListOf() }.add(map) }
        }
        val tage = mutableListOf<Map<String, Any?>>()
        var tag = ersterTag
        while (!tag.isAfter(letzterTag)) {
            val currentTag = tag
            var istStunden = BigDecimal.ZERO
            if (currentTag in feiertagDaten) istStunden = istStunden.add(zeitkonto.getSollstundenFuerTag(currentTag.dayOfWeek.value))
            buchungenProTag[currentTag].orEmpty().forEach { buchung ->
                if (buchung["typ"]?.toString() == "PAUSE") return@forEach
                val minuten = buchung["dauerMinuten"] as? Long
                if (minuten != null) istStunden = istStunden.add(BigDecimal(minuten).divide(BigDecimal("60"), 2, RoundingMode.HALF_UP))
            }
            tage += linkedMapOf(
                "datum" to currentTag.toString(),
                "wochentag" to currentTag.dayOfWeek.value,
                "istFeiertag" to (currentTag in feiertagDaten),
                "feiertagName" to feiertage.firstOrNull { it.datum == currentTag }?.bezeichnung,
                "sollStunden" to if (currentTag in feiertagDaten) BigDecimal.ZERO else zeitkonto.getSollstundenFuerTag(currentTag.dayOfWeek.value),
                "buchungen" to buchungenProTag.getOrDefault(currentTag, mutableListOf()),
                "istStunden" to istStunden,
            )
            tag = tag.plusDays(1)
        }
        val sollMonat = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, jahr, monat)
        val istMonat = tage.map { it["istStunden"] as BigDecimal }.fold(BigDecimal.ZERO, BigDecimal::add)
        return ResponseEntity.ok(
            linkedMapOf(
                "jahr" to jahr,
                "monat" to monat,
                "mitarbeiterId" to mitarbeiterId,
                "tage" to tage,
                "sollStundenMonat" to sollMonat,
                "istStundenMonat" to istMonat,
                "differenz" to istMonat.subtract(sollMonat),
                "feiertage" to feiertage.map { mapOf("datum" to it.datum.toString(), "bezeichnung" to it.bezeichnung) },
            ),
        )
    }

    @GetMapping("/feiertage")
    fun getFeiertage(@RequestParam jahr: Int): ResponseEntity<List<Feiertag>> =
        ResponseEntity.ok(feiertagService.getFeiertageForJahr(jahr))

    @GetMapping("/feiertage/zwischen")
    fun getFeiertageZwischen(@RequestParam von: String, @RequestParam bis: String): ResponseEntity<List<Map<String, String?>>> {
        val feiertage = feiertagService.getFeiertageZwischen(LocalDate.parse(von), LocalDate.parse(bis))
        return ResponseEntity.ok(feiertage.map { mapOf("datum" to it.datum?.toString(), "bezeichnung" to it.bezeichnung) })
    }

    @PostMapping("/feiertage/regenerieren")
    fun regeneriereFeiertage(@RequestParam vonJahr: Int, @RequestParam bisJahr: Int): ResponseEntity<List<Feiertag>> =
        ResponseEntity.ok(feiertagService.regeneriereFeiertage(vonJahr, bisJahr))

    @GetMapping("/zeitkonten")
    fun getAlleZeitkonten(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(mitarbeiterRepository.findAll().map { m ->
            val konto = zeitkontoService.getOrCreateZeitkonto(m.id)
            linkedMapOf(
                "mitarbeiterId" to m.id,
                "mitarbeiterName" to "${m.vorname} ${m.nachname}",
                "montagStunden" to konto.montagStunden,
                "dienstagStunden" to konto.dienstagStunden,
                "mittwochStunden" to konto.mittwochStunden,
                "donnerstagStunden" to konto.donnerstagStunden,
                "freitagStunden" to konto.freitagStunden,
                "samstagStunden" to konto.samstagStunden,
                "sonntagStunden" to konto.sonntagStunden,
                "wochenstunden" to konto.getWochenstunden(),
                "buchungStartZeit" to konto.buchungStartZeit?.toString(),
                "buchungEndeZeit" to konto.buchungEndeZeit?.toString(),
            )
        })

    @PutMapping("/zeitkonten/{mitarbeiterId}")
    fun updateZeitkonto(@PathVariable mitarbeiterId: Long, @RequestBody data: Map<String, Any?>): ResponseEntity<Zeitkonto> {
        val updated = zeitkontoService.aktualisiereZeitkonto(
            mitarbeiterId,
            BigDecimal(data["montagStunden"].toString()),
            BigDecimal(data["dienstagStunden"].toString()),
            BigDecimal(data["mittwochStunden"].toString()),
            BigDecimal(data["donnerstagStunden"].toString()),
            BigDecimal(data["freitagStunden"].toString()),
            BigDecimal(data["samstagStunden"].toString()),
            BigDecimal(data["sonntagStunden"].toString()),
        )
        updated.buchungStartZeit = data["buchungStartZeit"]?.toString()?.let(LocalTime::parse)
        updated.buchungEndeZeit = data["buchungEndeZeit"]?.toString()?.let(LocalTime::parse)
        zeitkontoService.speichereZeitkonto(updated)
        monatsSaldoService.invalidiereAlle(mitarbeiterId)
        return ResponseEntity.ok(updated)
    }

    @GetMapping("/auswertung/projekt/{projektId}/pdf")
    fun getProjektAuswertungPdf(
        @PathVariable projektId: Long,
        @RequestParam(required = false) von: LocalDate?,
        @RequestParam(required = false) bis: LocalDate?,
        @RequestParam(required = false, defaultValue = "datum") sortField: String,
        @RequestParam(required = false, defaultValue = "asc") sortDir: String,
        @RequestParam(required = false, defaultValue = "arbeitsgang") groupBy: String,
    ): ResponseEntity<Resource> {
        val pdfPath = projektAuswertungPdfService.generatePdf(projektId, von, bis, sortField, sortDir, groupBy)
        val resource = UrlResource(pdfPath.toUri())
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Regiebericht.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource)
    }

    @GetMapping("/auswertung/projekt/{projektId}")
    fun getProjektAuswertung(
        @PathVariable projektId: Long,
        @RequestParam(required = false) von: LocalDate?,
        @RequestParam(required = false) bis: LocalDate?,
    ): ResponseEntity<Map<String, Any?>> {
        val alleBuchungen = zeitbuchungRepository.findByProjektId(projektId)
            .filter { b ->
                val d = b.startZeit?.toLocalDate() ?: return@filter false
                (von == null || !d.isBefore(von)) && (bis == null || !d.isAfter(bis))
            }
        val nachArbeitsgang = alleBuchungen.groupBy { it.arbeitsgangStundensatz?.arbeitsgang?.beschreibung ?: "Nicht zugeordnet" }
        var gesamtStunden = BigDecimal.ZERO
        val taetigkeiten = nachArbeitsgang.map { (arbeitsgang, list) ->
            var gesamtMinuten = 0L
            val einzel = list.sortedBy { it.startZeit }.map { b ->
                if (b.startZeit != null && b.endeZeit != null) gesamtMinuten += Duration.between(b.startZeit, b.endeZeit).toMinutes()
                buchungToMap(b)
            }
            val stunden = BigDecimal(gesamtMinuten).divide(BigDecimal("60"), 2, RoundingMode.HALF_UP)
            gesamtStunden = gesamtStunden.add(stunden)
            linkedMapOf("arbeitsgang" to arbeitsgang, "anzahlBuchungen" to list.size, "gesamtMinuten" to gesamtMinuten, "gesamtStunden" to stunden, "buchungen" to einzel)
        }.sortedByDescending { it["gesamtStunden"] as BigDecimal }
        val result = linkedMapOf<String, Any?>(
            "projektId" to projektId,
            "von" to von,
            "bis" to bis,
            "taetigkeiten" to taetigkeiten,
            "gesamtStunden" to gesamtStunden,
            "anzahlBuchungen" to alleBuchungen.size,
        )
        alleBuchungen.firstOrNull()?.projekt?.let {
            result["projektName"] = it.bauvorhaben
            result["kunde"] = it.getKunde()
            result["auftragsnummer"] = it.auftragsnummer
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/saldo-cache/warmup")
    fun warmupSaldoCache(): ResponseEntity<Map<String, Any>> {
        val start = System.currentTimeMillis()
        monatsSaldoWarmupService.warmupCache()
        return ResponseEntity.ok(
            linkedMapOf(
                "status" to "ok",
                "message" to "MonatsSaldo-Cache wurde erfolgreich befuellt",
                "dauerMs" to (System.currentTimeMillis() - start),
            ),
        )
    }

    private fun resolveBearbeiter(bearbeiterId: Long?): Mitarbeiter? {
        if (bearbeiterId != null) {
            mitarbeiterRepository.findById(bearbeiterId).orElse(null)?.let { return it }
        }
        return frontendUserProfileRepository.findAll().firstOrNull { it.mitarbeiter != null }?.mitarbeiter
    }

    private fun applyBuchungChanges(buchung: Zeitbuchung, data: Map<String, Any?>) {
        data["startZeit"]?.toString()?.let { buchung.startZeit = LocalDateTime.parse(it) }
        if (data.containsKey("endeZeit")) buchung.endeZeit = data["endeZeit"]?.toString()?.let(LocalDateTime::parse)
        if (data.containsKey("notiz")) buchung.notiz = data["notiz"] as? String
        val projektId = data["projektId"]?.toString()?.toLongOrNull()
        if (projektId != null) {
            buchung.projekt = projektRepository.findById(projektId).orElseThrow { IllegalArgumentException("Projekt nicht gefunden: $projektId") }
            buchung.projektProduktkategorie = null
        }
        applyArbeitsgang(buchung, data)
        applyProduktkategorie(buchung, data)
    }

    private fun applyArbeitsgang(buchung: Zeitbuchung, data: Map<String, Any?>) {
        val arbeitsgangId = data["arbeitsgangId"]?.toString()?.toLongOrNull() ?: return
        val buchungsJahr = data["startZeit"]?.toString()?.let { LocalDateTime.parse(it).year } ?: buchung.startZeit?.year ?: LocalDate.now().year
        val stundensatz = arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, buchungsJahr)
            .or { arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(arbeitsgangId, buchungsJahr) }
            .or { arbeitsgangStundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgangId) }
        stundensatz.ifPresent { buchung.arbeitsgangStundensatz = it }
        arbeitsgangRepository.findById(arbeitsgangId).ifPresent { buchung.arbeitsgang = it }
    }

    private fun applyProduktkategorie(buchung: Zeitbuchung, data: Map<String, Any?>) {
        if (!data.containsKey("produktkategorieId")) return
        val produktkategorieId = data["produktkategorieId"]?.toString()?.toLongOrNull()
        if (produktkategorieId == null) {
            buchung.projektProduktkategorie = null
            return
        }
        val match = buchung.projekt?.projektProduktkategorien?.firstOrNull { it.produktkategorie?.id == produktkategorieId }
        buchung.projektProduktkategorie = match
    }

    private fun recalcDauer(buchung: Zeitbuchung) {
        val start = buchung.startZeit
        val ende = buchung.endeZeit
        if (start != null && ende != null) {
            buchung.anzahlInStunden = BigDecimal.valueOf(Duration.between(start, ende).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
        }
    }

    private fun buchungToMap(b: Zeitbuchung): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>("id" to b.id)
        val projekt = b.projekt
        if (projekt != null) {
            map["projektId"] = projekt.id
            map["projektName"] = projekt.bauvorhaben
        } else {
            map["projektId"] = null
            map["projektName"] = if (b.typ == BuchungsTyp.PAUSE) "Pause" else null
        }
        val ag = b.arbeitsgangStundensatz?.arbeitsgang ?: b.arbeitsgang
        map["arbeitsgangId"] = ag?.id
        map["arbeitsgangName"] = ag?.beschreibung
        val kat = b.projektProduktkategorie?.produktkategorie
        map["produktkategorieId"] = kat?.id
        map["produktkategorieName"] = kat?.bezeichnung
        map["produktkategoriePfad"] = kat?.let(::buildKategoriePfad)
        map["mitarbeiterName"] = b.mitarbeiter?.let { "${it.vorname} ${it.nachname}" } ?: "Unbekannt"
        map["qualifikationName"] = b.mitarbeiter?.qualifikation?.bezeichnung
        map["startZeit"] = b.startZeit?.toLocalTime()?.toString()?.take(5)
        map["endeZeit"] = b.endeZeit?.toLocalTime()?.toString()?.take(5)
        map["startDateTime"] = b.startZeit?.toString()
        map["endeDateTime"] = b.endeZeit?.toString()
        map["notiz"] = b.notiz
        if (b.startZeit != null && b.endeZeit != null) {
            val minuten = Duration.between(b.startZeit, b.endeZeit).toMinutes()
            map["dauerMinuten"] = minuten
            map["dauerFormatiert"] = "%d:%02d".format(minuten / 60, minuten % 60)
        } else {
            map["dauerMinuten"] = null
            map["dauerFormatiert"] = null
        }
        map["typ"] = b.typ?.name ?: "ARBEIT"
        return map
    }

    private fun buildKategoriePfad(kategorie: Produktkategorie): String {
        val parts = ArrayDeque<String>()
        var current: Produktkategorie? = kategorie
        while (current != null) {
            parts.addFirst(current.bezeichnung)
            current = current.uebergeordneteKategorie
        }
        return parts.joinToString("/")
    }
}
