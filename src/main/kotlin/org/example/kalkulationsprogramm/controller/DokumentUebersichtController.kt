package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.util.Comparator

@RestController
@RequestMapping("/api/dokumentuebersicht")
class DokumentUebersichtController(
    private val ausgangsRepo: AusgangsGeschaeftsDokumentRepository,
    private val lieferantGdRepo: LieferantGeschaeftsdokumentRepository,
) {
    @GetMapping("/ausgang")
    fun getAusgang(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) dokumentNummer: String?,
        @RequestParam(required = false) typ: AusgangsGeschaeftsDokumentTyp?,
        @RequestParam(required = false) kundeId: Long?,
        @RequestParam(required = false) betragMin: Double?,
        @RequestParam(required = false) betragMax: Double?,
    ): ResponseEntity<List<AusgangsDokumentUebersichtDto>> {
        var dokumente = when {
            year != null && month != null -> {
                val ym = YearMonth.of(year, month)
                ausgangsRepo.findByDatumBetweenOrderByDatumDesc(ym.atDay(1), ym.atEndOfMonth())
            }
            year != null -> ausgangsRepo.findByDatumBetweenOrderByDatumDesc(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
            )
            else -> ausgangsRepo.findAllByOrderByDatumDesc()
        }

        if (!search.isNullOrBlank()) {
            val q = search.lowercase()
            dokumente = dokumente.filter { matchesAusgangSearch(it, q) }
        }
        if (!dokumentNummer.isNullOrBlank()) {
            val q = dokumentNummer.lowercase()
            dokumente = dokumente.filter { it.dokumentNummer?.lowercase()?.contains(q) == true }
        }
        if (typ != null) {
            dokumente = dokumente.filter { it.typ == typ }
        }
        if (kundeId != null) {
            dokumente = dokumente.filter { it.kunde?.id == kundeId }
        }
        if (betragMin != null) {
            dokumente = dokumente.filter { (it.betragBrutto?.toDouble() ?: Double.NEGATIVE_INFINITY) >= betragMin }
        }
        if (betragMax != null) {
            dokumente = dokumente.filter { (it.betragBrutto?.toDouble() ?: Double.POSITIVE_INFINITY) <= betragMax }
        }

        return ResponseEntity.ok(dokumente.map(::toAusgangsDto))
    }

    @GetMapping("/eingang")
    fun getEingang(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) dokumentNummer: String?,
        @RequestParam(required = false) typ: LieferantDokumentTyp?,
        @RequestParam(required = false) lieferantId: Long?,
        @RequestParam(required = false) betragMin: Double?,
        @RequestParam(required = false) betragMax: Double?,
    ): ResponseEntity<List<EingangsDokumentUebersichtDto>> {
        var dokumente = when {
            year != null && month != null -> {
                val ym = YearMonth.of(year, month)
                lieferantGdRepo.findAllByDatumBetween(ym.atDay(1), ym.atEndOfMonth())
            }
            year != null -> lieferantGdRepo.findAllByDatumBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
            )
            else -> lieferantGdRepo.findAllSortedByDatum()
        }

        if (!search.isNullOrBlank()) {
            val q = search.lowercase()
            dokumente = dokumente.filter { matchesEingangSearch(it, q) }
        }
        if (!dokumentNummer.isNullOrBlank()) {
            val q = dokumentNummer.lowercase()
            dokumente = dokumente.filter { it.dokumentNummer?.lowercase()?.contains(q) == true }
        }
        if (typ != null) {
            dokumente = dokumente.filter { it.dokument?.typ == typ }
        }
        if (lieferantId != null) {
            dokumente = dokumente.filter { it.dokument?.lieferant?.id == lieferantId }
        }
        if (betragMin != null) {
            dokumente = dokumente.filter { (it.betragBrutto?.toDouble() ?: Double.NEGATIVE_INFINITY) >= betragMin }
        }
        if (betragMax != null) {
            dokumente = dokumente.filter { (it.betragBrutto?.toDouble() ?: Double.POSITIVE_INFINITY) <= betragMax }
        }

        dokumente = dokumente.sortedWith(
            Comparator.comparing<LieferantGeschaeftsdokument, LocalDate?>(
                { it.dokumentDatum },
                Comparator.nullsLast(Comparator.reverseOrder()),
            ),
        )

        return ResponseEntity.ok(dokumente.map(::toEingangsDto))
    }

    private fun matchesAusgangSearch(d: AusgangsGeschaeftsDokument, q: String): Boolean =
        d.dokumentNummer?.lowercase()?.contains(q) == true ||
            d.betreff?.lowercase()?.contains(q) == true ||
            d.typ?.name?.lowercase()?.contains(q) == true ||
            d.betragBrutto?.toPlainString()?.contains(q) == true ||
            d.kunde?.name?.lowercase()?.contains(q) == true ||
            d.projekt?.auftragsnummer?.lowercase()?.contains(q) == true

    private fun matchesEingangSearch(gd: LieferantGeschaeftsdokument, q: String): Boolean =
        gd.dokumentNummer?.lowercase()?.contains(q) == true ||
            gd.betragBrutto?.toPlainString()?.contains(q) == true ||
            gd.dokument?.typ?.name?.lowercase()?.contains(q) == true ||
            gd.dokument?.lieferant?.lieferantenname?.lowercase()?.contains(q) == true

    private fun toAusgangsDto(d: AusgangsGeschaeftsDokument): AusgangsDokumentUebersichtDto =
        AusgangsDokumentUebersichtDto(
            id = d.id,
            dokumentNummer = d.dokumentNummer,
            typ = d.typ,
            datum = d.datum,
            betreff = d.betreff,
            betragBrutto = d.betragBrutto?.toDouble(),
            betragNetto = d.betragNetto?.toDouble(),
            gebucht = d.isGebucht(),
            storniert = d.isStorniert(),
            digitalAngenommen = d.isDigitalAngenommen(),
            kundeId = d.kunde?.id,
            kundenName = d.kunde?.name,
            projektId = d.projekt?.id,
            projektAuftragsnummer = d.projekt?.auftragsnummer,
        )

    private fun toEingangsDto(gd: LieferantGeschaeftsdokument): EingangsDokumentUebersichtDto {
        val dokument = gd.dokument
        val dto = EingangsDokumentUebersichtDto(
            id = gd.id,
            dokumentNummer = gd.dokumentNummer,
            dokumentDatum = gd.dokumentDatum,
            betragNetto = gd.betragNetto?.toDouble(),
            betragBrutto = gd.betragBrutto?.toDouble(),
            bezahlt = gd.bezahlt == true,
        )

        if (dokument != null) {
            dto.dokumentId = dokument.id
            dto.originalDateiname = dokument.getEffektiverDateiname()
            dto.typ = dokument.typ?.name
            val lieferant = dokument.lieferant
            if (lieferant != null) {
                dto.lieferantId = lieferant.id
                dto.lieferantName = lieferant.lieferantenname
            }
            val attachment = dokument.attachment
            val attachmentEmail = attachment?.email
            if (attachmentEmail != null) {
                dto.pdfUrl = "/api/emails/${attachmentEmail.id}/attachments/${attachment.id}"
            } else if (dokument.gespeicherterDateiname != null && dto.lieferantId != null) {
                dto.pdfUrl = "/api/lieferanten/${dto.lieferantId}/dokumente/${dokument.id}/download"
            }
        }
        return dto
    }

    data class AusgangsDokumentUebersichtDto(
        var id: Long? = null,
        var dokumentNummer: String? = null,
        var typ: AusgangsGeschaeftsDokumentTyp? = null,
        var datum: LocalDate? = null,
        var betreff: String? = null,
        var betragNetto: Double? = null,
        var betragBrutto: Double? = null,
        var gebucht: Boolean = false,
        var storniert: Boolean = false,
        var digitalAngenommen: Boolean = false,
        var kundeId: Long? = null,
        var kundenName: String? = null,
        var projektId: Long? = null,
        var projektAuftragsnummer: String? = null,
    )

    data class EingangsDokumentUebersichtDto(
        var id: Long? = null,
        var dokumentId: Long? = null,
        var lieferantId: Long? = null,
        var lieferantName: String? = null,
        var dokumentNummer: String? = null,
        var typ: String? = null,
        var dokumentDatum: LocalDate? = null,
        var betragNetto: Double? = null,
        var betragBrutto: Double? = null,
        var bezahlt: Boolean = false,
        var originalDateiname: String? = null,
        var pdfUrl: String? = null,
    )
}
