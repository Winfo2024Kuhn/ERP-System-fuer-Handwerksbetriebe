package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.KostenstelleRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RestController
@RequestMapping("/api/bestellungen-uebersicht")
class BestellungsUebersichtController(
    private val dokumentRepository: LieferantDokumentRepository,
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val kostenstelleRepository: KostenstelleRepository,
    private val projektAnteilRepository: LieferantDokumentProjektAnteilRepository,
    private val belegRepository: BelegRepository,
    private val belegKostenstellenAnteilRepository: BelegKostenstellenAnteilRepository,
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun getUebersicht(): ResponseEntity<BestellungsUebersichtDto> {
        val dokumente = dokumentRepository.findAll()
            .filter { !it.ausgeblendet && it.geschaeftsdaten != null }
            .sortedByDescending { it.uploadDatum }

        fun chains(vararg typen: LieferantDokumentTyp): List<DokumentenKette> =
            dokumente
                .filter { it.typ in typen }
                .map { DokumentenKette(bestellung = toRef(it)) }

        return ResponseEntity.ok(
            BestellungsUebersichtDto(
                offeneBestellungen = chains(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG),
                offeneWareneingaenge = chains(LieferantDokumentTyp.LIEFERSCHEIN),
                offeneRechnungen = chains(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.GUTSCHRIFT),
            ),
        )
    }

    @GetMapping("/kostenstellen")
    @Transactional(readOnly = true)
    fun kostenstellen(): List<KostenstelleDto> =
        kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()
            .map { KostenstelleDto(it.id, it.bezeichnung, it.typ?.name, it.beschreibung) }

    @GetMapping("/belege-offen")
    @Transactional(readOnly = true)
    fun offeneBelege(): List<BelegZuordnungDto> =
        belegRepository.findNichtEmailImportierteOhneKostenstellenZuordnung().map(::toBelegDto)

    @GetMapping("/geschaeftsdaten/{id}")
    @Transactional(readOnly = true)
    fun geschaeftsdaten(@PathVariable id: Long): ResponseEntity<GeschaeftsdatenDto> =
        geschaeftsdokumentRepository.findById(id)
            .map { ResponseEntity.ok(toGeschaeftsdatenDto(it)) }
            .orElseGet { ResponseEntity.notFound().build() }

    @GetMapping("/belegdaten/{id}")
    @Transactional(readOnly = true)
    fun belegdaten(@PathVariable id: Long): ResponseEntity<BelegZuordnungDto> =
        belegRepository.findById(id)
            .map { ResponseEntity.ok(toBelegDto(it)) }
            .orElseGet { ResponseEntity.notFound().build() }

    @GetMapping("/zuordnungen/{id}")
    @Transactional(readOnly = true)
    fun zuordnungen(@PathVariable id: Long): List<ZuordnungDto> =
        projektAnteilRepository.findByDokumentIdEager(id).map(::toZuordnungDto)

    @GetMapping("/beleg-zuordnungen/{id}")
    @Transactional(readOnly = true)
    fun belegZuordnungen(@PathVariable id: Long): List<ZuordnungDto> =
        belegKostenstellenAnteilRepository.findByBelegId(id).map { anteil ->
            ZuordnungDto().also { dto ->
                dto.id = anteil.id
                dto.quelle = "BELEG"
                dto.kostenstelleId = anteil.kostenstelle?.id
                dto.kostenstelleName = anteil.kostenstelle?.bezeichnung
                dto.betrag = anteil.berechneterBetrag ?: anteil.absoluterBetrag
                dto.prozentanteil = anteil.prozent?.let { BigDecimal.valueOf(it.toLong()) }
                dto.beschreibung = anteil.beschreibung
                dto.zugeordnetAm = anteil.zugeordnetAm
                dto.streckungJahre = anteil.streckungJahre
                dto.streckungStartJahr = anteil.streckungStartJahr
                dto.jahresanteil = anteil.jahresanteil
                dto.belegId = anteil.beleg?.id
            }
        }

    @PostMapping("/ausblenden")
    @Transactional
    fun ausblenden(@RequestBody request: AusblendenRequest): ResponseEntity<Void> {
        updateAusgeblendet(request.dokumentIds.orEmpty(), true)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/einblenden")
    @Transactional
    fun einblenden(@RequestBody request: AusblendenRequest): ResponseEntity<Void> {
        updateAusgeblendet(request.dokumentIds.orEmpty(), false)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/zuordnen")
    fun zuordnen(@RequestBody request: ZuordnungRequest): ResponseEntity<Void> {
        if (request.geschaeftsdokumentId == null) return ResponseEntity.badRequest().build()
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/beleg-zuordnen")
    fun belegZuordnen(@RequestBody request: BelegZuordnungRequest): ResponseEntity<Void> {
        if (request.belegId == null) return ResponseEntity.badRequest().build()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/zuordnungen/kostenstelle/{id}")
    @Transactional(readOnly = true)
    fun zuordnungenKostenstelle(@PathVariable id: Long): List<ZuordnungDto> =
        projektAnteilRepository.findByKostenstelleId(id).map(::toZuordnungDto)

    @GetMapping("/kostenstellen/auswertung")
    @Transactional(readOnly = true)
    fun kostenstellenAuswertung(
        @RequestParam jahr: Int?,
        @RequestParam monat: Int?,
    ): List<KostenstelleAuswertungDto> {
        val zeitraum = jahr?.let { j -> monat?.let { YearMonth.of(j, it) } }
        return kostenstelleRepository.findByAktivTrueOrderBySortierungAsc().map { ks ->
            val zuordnungen = projektAnteilRepository.findByKostenstelleId(ks.id)
                .map(::toZuordnungDto)
                .filter { z ->
                    val datum = z.dokumentDatum
                    zeitraum == null || (datum != null && YearMonth.from(datum) == zeitraum)
                }
            KostenstelleAuswertungDto(
                kostenstelleId = ks.id,
                kostenstelleName = ks.bezeichnung,
                summe = zuordnungen.fold(BigDecimal.ZERO) { acc, z -> acc + (z.jahresanteil ?: z.betrag ?: BigDecimal.ZERO) },
                zuordnungen = zuordnungen,
            )
        }
    }

    private fun updateAusgeblendet(ids: List<Long>, ausgeblendet: Boolean) {
        ids.filter { it > 0 }.forEach { id ->
            dokumentRepository.findById(id).ifPresent {
                it.ausgeblendet = ausgeblendet
                dokumentRepository.save(it)
            }
        }
    }

    data class BestellungsUebersichtDto(
        val offeneBestellungen: List<DokumentenKette>,
        val offeneWareneingaenge: List<DokumentenKette>,
        val offeneRechnungen: List<DokumentenKette>,
    )

    data class AusblendenRequest(
        val id: Long? = null,
        val typ: LieferantDokumentTyp? = null,
        val dokumentIds: List<Long>? = null,
    )

    data class DokumentenKette(
        val bestellung: DokumentRef? = null,
        val wareneingaenge: List<DokumentRef> = emptyList(),
        val rechnungen: List<DokumentRef> = emptyList(),
    )

    class DokumentRef {
        var id: Long? = null
        var typ: LieferantDokumentTyp? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragBrutto: Double? = null
        var betragNetto: Double? = null
        var liefertermin: LocalDate? = null
        var dateiname: String? = null
        var pdfUrl: String? = null
    }

    class GeschaeftsdatenDto {
        var id: Long? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var liefertermin: LocalDate? = null
        var bestellnummer: String? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var istLagerbestellung: Boolean? = null
    }

    class ZuordnungRequest {
        var geschaeftsdokumentId: Long? = null
        var frontendUserProfileId: Long? = null
        var projektAnteile: List<ProjektAnteil>? = null
    }

    class BelegZuordnungRequest {
        var belegId: Long? = null
        var frontendUserProfileId: Long? = null
        var projektAnteile: List<ProjektAnteil>? = null
    }

    class ProjektAnteil {
        var projektId: Long? = null
        var kostenstelleId: Long? = null
        var betrag: BigDecimal? = null
        var prozentanteil: BigDecimal? = null
        var beschreibung: String? = null
        var streckungJahre: Int? = null
    }

    class ZuordnungDto {
        var id: Long? = null
        var quelle: String? = null
        var projektId: Long? = null
        var projektName: String? = null
        var kostenstelleId: Long? = null
        var kostenstelleName: String? = null
        var betrag: BigDecimal? = null
        var prozentanteil: BigDecimal? = null
        var beschreibung: String? = null
        var zugeordnetAm: LocalDateTime? = null
        var zugeordnetVonName: String? = null
        var streckungJahre: Int? = null
        var streckungStartJahr: Int? = null
        var jahresanteil: BigDecimal? = null
        var lieferantName: String? = null
        var bestellnummer: String? = null
        var dokumentDatum: LocalDate? = null
        var geschaeftsdokumentId: Long? = null
        var dokumentId: Long? = null
        var belegId: Long? = null
    }

    class BelegZuordnungDto {
        var id: Long? = null
        var belegNummer: String? = null
        var belegDatum: LocalDate? = null
        var beschreibung: String? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var lieferantName: String? = null
        var originalDateiname: String? = null
        var mimeType: String? = null
        var pdfUrl: String? = null
    }

    data class KostenstelleDto(val id: Long?, val bezeichnung: String?, val typ: String?, val beschreibung: String?)

    data class KostenstelleAuswertungDto(
        val kostenstelleId: Long?,
        val kostenstelleName: String?,
        val summe: BigDecimal?,
        val zuordnungen: List<ZuordnungDto>?,
    )

    private fun toRef(dokument: org.example.kalkulationsprogramm.domain.LieferantDokument): DokumentRef {
        val gd = dokument.geschaeftsdaten
        return DokumentRef().also {
            it.id = dokument.id
            it.typ = dokument.typ
            it.dokumentNummer = gd?.dokumentNummer
            it.dokumentDatum = gd?.dokumentDatum
            it.betragBrutto = gd?.betragBrutto?.toDouble()
            it.betragNetto = gd?.betragNetto?.toDouble()
            it.liefertermin = gd?.liefertermin
            it.dateiname = dokument.getEffektiverDateiname()
            it.pdfUrl = "/api/lieferanten/dokumente/${dokument.id}/datei"
        }
    }

    private fun toGeschaeftsdatenDto(gd: LieferantGeschaeftsdokument): GeschaeftsdatenDto =
        GeschaeftsdatenDto().also {
            it.id = gd.id
            it.dokumentNummer = gd.dokumentNummer
            it.dokumentDatum = gd.dokumentDatum
            it.betragNetto = gd.betragNetto
            it.betragBrutto = gd.betragBrutto
            it.mwstSatz = gd.mwstSatz
            it.liefertermin = gd.liefertermin
            it.bestellnummer = gd.bestellnummer
            it.lieferantId = gd.dokument?.lieferant?.id
            it.lieferantName = gd.dokument?.lieferant?.lieferantenname
            it.istLagerbestellung = gd.lagerbestellung
        }

    private fun toBelegDto(beleg: Beleg): BelegZuordnungDto =
        BelegZuordnungDto().also {
            it.id = beleg.id
            it.belegNummer = beleg.belegNummer
            it.belegDatum = beleg.belegDatum
            it.beschreibung = beleg.beschreibung
            it.betragNetto = beleg.betragNetto
            it.betragBrutto = beleg.betragBrutto
            it.lieferantName = beleg.lieferant?.lieferantenname ?: beleg.kiVorgeschlagenerLieferant
            it.originalDateiname = beleg.originalDateiname
            it.mimeType = beleg.mimeType
            it.pdfUrl = beleg.id?.let { id -> "/api/buchhaltung/belege/$id/datei" }
        }

    private fun toZuordnungDto(anteil: org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil): ZuordnungDto {
        val gd = anteil.dokument?.geschaeftsdaten
        return ZuordnungDto().also {
            it.id = anteil.id
            it.quelle = "LIEFERANT_DOKUMENT"
            it.projektId = anteil.projekt?.id
            it.projektName = anteil.projekt?.bauvorhaben
            it.kostenstelleId = anteil.kostenstelle?.id
            it.kostenstelleName = anteil.kostenstelle?.bezeichnung
            it.betrag = anteil.berechneterBetrag ?: anteil.absoluterBetrag
            it.prozentanteil = anteil.prozent?.let { p -> BigDecimal.valueOf(p.toLong()) }
            it.beschreibung = anteil.beschreibung
            it.zugeordnetAm = anteil.zugeordnetAm
            it.zugeordnetVonName = anteil.zugeordnetVon?.displayName
            it.streckungJahre = anteil.streckungJahre
            it.streckungStartJahr = anteil.streckungStartJahr
            it.jahresanteil = anteil.jahresanteil
            it.lieferantName = anteil.dokument?.lieferant?.lieferantenname
            it.bestellnummer = gd?.bestellnummer
            it.dokumentDatum = gd?.dokumentDatum
            it.geschaeftsdokumentId = gd?.id
            it.dokumentId = anteil.dokument?.id
        }
    }
}
