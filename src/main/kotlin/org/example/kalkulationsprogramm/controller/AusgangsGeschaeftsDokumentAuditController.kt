package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository
import org.example.kalkulationsprogramm.service.AuditChainVerifier
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService
import org.example.kalkulationsprogramm.service.SteuerpruefungZ3ExportService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/ausgangs-dokumente/audit")
class AusgangsGeschaeftsDokumentAuditController(
    private val auditRepository: AusgangsGeschaeftsDokumentAuditRepository,
    @Suppress("unused")
    private val auditService: AusgangsGeschaeftsDokumentAuditService,
    private val auditChainVerifier: AuditChainVerifier,
    private val z3ExportService: SteuerpruefungZ3ExportService,
) {
    @GetMapping(value = ["/export"], produces = ["text/csv; charset=UTF-8"])
    fun exportCsv(
        @RequestParam("von") von: String,
        @RequestParam("bis") bis: String,
    ): ResponseEntity<String> {
        val vonDt = LocalDate.parse(von).atStartOfDay()
        val bisDt = LocalDate.parse(bis).atTime(23, 59, 59)
        val eintraege = auditRepository.findByGeaendertAmBetweenOrderByChainIndexAsc(vonDt, bisDt)
        val csv = StringBuilder()
        csv.append("ChainIndex;Zeitpunkt;Aktion;DokumentNummer;Typ;BetragNetto;BetragBrutto;")
            .append("Gebucht;Storniert;DigitalAngenommen;BearbeiterId;Begruendung;IpAdresse;")
            .append("InhaltHash;PreviousHash;EntryHash\n")

        for (audit in eintraege) {
            csv.append(readField(audit, "chainIndex")?.toString() ?: "").append(';')
            csv.append(readLocalDateTime(audit, "geaendertAm")?.format(TS) ?: "").append(';')
            csv.append((readField(audit, "aktion") as? Enum<*>)?.name ?: "").append(';')
            csv.append(esc(readString(audit, "dokumentNummer"))).append(';')
            csv.append((readField(audit, "typ") as? Enum<*>)?.name ?: "").append(';')
            csv.append(readField(audit, "betragNetto")?.toString() ?: "").append(';')
            csv.append(readField(audit, "betragBrutto")?.toString() ?: "").append(';')
            csv.append(readBoolean(audit, "gebucht")).append(';')
            csv.append(readBoolean(audit, "storniert")).append(';')
            csv.append(readBoolean(audit, "digitalAngenommen")).append(';')
            csv.append(readLong(readField(audit, "geaendertVon"), "id") ?: "").append(';')
            csv.append(esc(readString(audit, "aenderungsgrund"))).append(';')
            csv.append(esc(readString(audit, "ipAdresse"))).append(';')
            csv.append(readString(audit, "inhaltHash") ?: "").append(';')
            csv.append(readString(audit, "previousHash") ?: "").append(';')
            csv.append(readString(audit, "entryHash") ?: "").append('\n')
        }

        val filename = "audit_ausgangsdokumente_${von}_bis_$bis.csv"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.toString())
    }

    @GetMapping("/anzahl")
    fun anzahl(
        @RequestParam("von") von: String,
        @RequestParam("bis") bis: String,
    ): ResponseEntity<Long> {
        val vonDt = LocalDate.parse(von).atStartOfDay()
        val bisDt = LocalDate.parse(bis).atTime(23, 59, 59)
        return ResponseEntity.ok(auditRepository.countByGeaendertAmBetween(vonDt, bisDt))
    }

    @GetMapping("/verify")
    fun verify(): ResponseEntity<AuditChainVerifier.Bericht> =
        ResponseEntity.ok(auditChainVerifier.verify())

    @GetMapping(value = ["/z3-paket"])
    @Throws(IOException::class)
    fun z3Paket(
        @RequestParam("von") von: String,
        @RequestParam("bis") bis: String,
    ): ResponseEntity<ByteArray> {
        val vonD = LocalDate.parse(von)
        val bisD = LocalDate.parse(bis)
        val zip = z3ExportService.erzeugeZip(vonD, bisD)
        val filename = "steuerpruefung_${von}_bis_$bis.zip"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip)
    }

    private fun esc(value: String?): String {
        if (value == null) return ""
        val escaped = value.replace("\"", "\"\"")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(";", ",")
        return "\"$escaped\""
    }

    private fun readString(target: Any?, fieldName: String): String? =
        readField(target, fieldName) as? String

    private fun readLong(target: Any?, fieldName: String): Long? =
        (readField(target, fieldName) as? Number)?.toLong()

    private fun readBoolean(target: Any?, fieldName: String): Boolean =
        readField(target, fieldName) as? Boolean ?: false

    private fun readLocalDateTime(target: Any?, fieldName: String): LocalDateTime? =
        readField(target, fieldName) as? LocalDateTime

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

    companion object {
        private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
