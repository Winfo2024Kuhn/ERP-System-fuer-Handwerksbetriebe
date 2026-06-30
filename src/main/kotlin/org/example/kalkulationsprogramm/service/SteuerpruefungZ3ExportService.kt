package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class SteuerpruefungZ3ExportService(
    private val dokumentRepository: AusgangsGeschaeftsDokumentRepository,
    private val auditRepository: AusgangsGeschaeftsDokumentAuditRepository,
    private val verifier: AuditChainVerifier,
) {
    @Transactional(readOnly = true)
    fun erzeugeZip(von: LocalDate, bis: LocalDate): ByteArray {
        val vonDt = von.atStartOfDay()
        val bisDt = bis.atTime(23, 59, 59)
        val dokumente = dokumentRepository.findByDatumBetweenOrderByDatumDesc(von, bis)
        val audits = auditRepository.findByGeaendertAmBetweenOrderByChainIndexAsc(vonDt, bisDt)
        val verifyBericht = verifier.verify()

        val dokumenteCsv = buildDokumenteCsv(dokumente)
        val auditCsv = buildAuditCsv(audits)
        val info = buildInfoText(von, bis, dokumente.size, audits.size, verifyBericht)
        val manifest = buildManifest(dokumenteCsv, auditCsv, info)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out, StandardCharsets.UTF_8).use { zip ->
            writeEntry(zip, "INFO.txt", info)
            writeEntry(zip, "dokumente.csv", dokumenteCsv)
            writeEntry(zip, "audit.csv", auditCsv)
            writeEntry(zip, "manifest.sha256", manifest)
        }
        return out.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildDokumenteCsv(dokumente: List<AusgangsGeschaeftsDokument>): String = buildString {
        append("DokumentNummer;Typ;Datum;Betreff;BetragNetto;BetragBrutto;MwStSatz;")
        append("Gebucht;GebuchtAm;Storniert;StorniertAm;DigitalAngenommen;")
        append("VersandDatum;KundeId;ProjektId;VorgaengerNummer;ErstelltAm\n")

        for (d in dokumente) {
            append(esc(field<String>(d, "dokumentNummer"))).append(';')
            append(enumName(field(d, "typ"))).append(';')
            append(field<LocalDate>(d, "datum")?.toString().orEmpty()).append(';')
            append(esc(field<String>(d, "betreff"))).append(';')
            append(field<BigDecimal>(d, "betragNetto")?.toPlainString().orEmpty()).append(';')
            append(field<BigDecimal>(d, "betragBrutto")?.toPlainString().orEmpty()).append(';')
            append(field<BigDecimal>(d, "mwstSatz")?.toPlainString().orEmpty()).append(';')
            append(field<Boolean>(d, "gebucht") ?: false).append(';')
            append(field<LocalDateTime>(d, "gebuchtAm")?.toString().orEmpty()).append(';')
            append(field<Boolean>(d, "storniert") ?: false).append(';')
            append(field<LocalDateTime>(d, "storniertAm")?.toString().orEmpty()).append(';')
            append(field<Boolean>(d, "digitalAngenommen") ?: false).append(';')
            append(field<LocalDateTime>(d, "versandDatum")?.toString().orEmpty()).append(';')
            append(idOf(field(d, "kunde"))).append(';')
            append(idOf(field(d, "projekt"))).append(';')
            append(esc(field<String>(field(d, "vorgaenger") ?: Any(), "dokumentNummer"))).append(';')
            append(field<LocalDateTime>(d, "erstelltAm")?.format(TS).orEmpty()).append('\n')
        }
    }

    private fun buildAuditCsv(audits: List<AusgangsGeschaeftsDokumentAudit>): String = buildString {
        append("ChainIndex;Zeitpunkt;Aktion;DokumentNummer;Typ;BetragNetto;BetragBrutto;")
        append("Gebucht;Storniert;DigitalAngenommen;BearbeiterId;Begruendung;IpAdresse;")
        append("InhaltHash;PreviousHash;EntryHash\n")

        for (a in audits) {
            append(field<Long>(a, "chainIndex")?.toString().orEmpty()).append(';')
            append(field<LocalDateTime>(a, "geaendertAm")?.format(TS).orEmpty()).append(';')
            append(enumName(field(a, "aktion"))).append(';')
            append(esc(field<String>(a, "dokumentNummer"))).append(';')
            append(enumName(field(a, "typ"))).append(';')
            append(field<BigDecimal>(a, "betragNetto")?.toPlainString().orEmpty()).append(';')
            append(field<BigDecimal>(a, "betragBrutto")?.toPlainString().orEmpty()).append(';')
            append(field<Boolean>(a, "gebucht") ?: false).append(';')
            append(field<Boolean>(a, "storniert") ?: false).append(';')
            append(field<Boolean>(a, "digitalAngenommen") ?: false).append(';')
            append(idOf(field(a, "geaendertVon"))).append(';')
            append(esc(field<String>(a, "aenderungsgrund"))).append(';')
            append(esc(field<String>(a, "ipAdresse"))).append(';')
            append(field<String>(a, "inhaltHash").orEmpty()).append(';')
            append(field<String>(a, "previousHash").orEmpty()).append(';')
            append(field<String>(a, "entryHash").orEmpty()).append('\n')
        }
    }

    private fun buildInfoText(
        von: LocalDate,
        bis: LocalDate,
        anzahlDokumente: Int,
        anzahlAudits: Int,
        bericht: AuditChainVerifier.Bericht,
    ): String = buildString {
        append("Steuerpruefungs-Paket nach GoBD (Z3-Datentraegerueberlassung)\n")
        append("===========================================================\n\n")
        append("Zeitraum: ").append(von).append(" bis ").append(bis).append('\n')
        append("Erstellt am: ").append(LocalDateTime.now().format(TS)).append("\n\n")
        append("Inhalt\n------\n")
        append("dokumente.csv     : ").append(anzahlDokumente).append(" Ausgangsdokumente (Rechnungen, Angebote, Storno usw.)\n")
        append("audit.csv         : ").append(anzahlAudits).append(" Audit-Eintraege mit Hash-Kette\n")
        append("manifest.sha256   : SHA-256 jeder Datei dieses Pakets\n\n")
        append("Hash-Kette (Manipulationsschutz)\n--------------------------------\n")
        append("Jeder Audit-Eintrag enthaelt einen entry_hash. Dieser ist der SHA-256\n")
        append("ueber die kanonische Form aller Felder + den entry_hash des Vorgaengers\n")
        append("(Spalte previous_hash). Wer einen einzigen Eintrag manipuliert,\n")
        append("bricht ALLE nachfolgenden Hashes - eine nachtraegliche Aenderung der\n")
        append("Buchhaltung ist daher mathematisch nachweisbar.\n\n")
        append("Verifikation der Kette (Stand zum Export)\n-----------------------------------------\n")
        append("Status: ").append(if (bericht.isIntakt()) "INTAKT" else "GEBROCHEN").append('\n')
        append("Gepruefte Eintraege: ").append(field<Int>(bericht, "gesamtAnzahl") ?: 0).append('\n')
        val letzterChainIndex = field<Long>(bericht, "letzterChainIndex")
        if (letzterChainIndex != null) {
            append("Letzter chain_index: ").append(letzterChainIndex).append('\n')
            append("Letzter entry_hash : ").append(field<String>(bericht, "letzterEntryHash")).append('\n')
        }
        if (!bericht.isIntakt()) {
            append("\nFEHLER:\n")
            val fehler = field<List<AuditChainVerifier.Fehler>>(bericht, "fehler").orEmpty()
            for (f in fehler) {
                append("  - chain_index ").append(f.chainIndex())
                    .append(" / Dokument ").append(f.dokumentNummer())
                    .append(" : ").append(f.beschreibung()).append('\n')
            }
        }
        append("\nAnleitung fuer den Pruefer\n------------------------\n")
        append("1. dokumente.csv und audit.csv sind UTF-8, Trennzeichen Semikolon.\n")
        append("2. Lueckenlose Nummerierung pruefen: in dokumente.csv die DokumentNummer sortieren.\n")
        append("3. Hash-Kette pruefen: audit.csv nach ChainIndex aufsteigend sortieren.\n")
        append("4. manifest.sha256 prueft, dass die Dateien selbst nicht veraendert wurden.\n\n")
        append("Datenschutzhinweis\n------------------\n")
        append("Diese Datei enthaelt personenbezogene Daten und ist nur autorisierten Pruefern auszuhaendigen.\n")
    }

    private fun buildManifest(dokumenteCsv: String, auditCsv: String, info: String): String = buildString {
        append("# SHA-256 Manifest des Steuerpruefungs-Pakets\n")
        append("# Format: <hex>  <dateiname>\n")
        append(sha256(info)).append("  INFO.txt\n")
        append(sha256(dokumenteCsv)).append("  dokumente.csv\n")
        append(sha256(auditCsv)).append("  audit.csv\n")
    }

    private fun esc(value: String?): String {
        if (value == null) return ""
        val escaped = value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ").replace(";", ",")
        return "\"$escaped\""
    }

    private fun enumName(value: Any?): String = (value as? Enum<*>)?.name.orEmpty()

    private fun idOf(value: Any?): String = if (value == null) "" else field<Long>(value, "id")?.toString().orEmpty()

    private inline fun <reified T> field(target: Any, name: String): T? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target) as? T
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    companion object {
        private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
