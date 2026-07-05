package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Werkstoff
import org.example.kalkulationsprogramm.dto.ImportAnalysisResult
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.KategorieRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.WerkstoffRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Date

@Service
class ArtikelImportService(
    private val artikelRepository: ArtikelRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val kategorieRepository: KategorieRepository,
    private val werkstoffRepository: WerkstoffRepository,
) {
    @Transactional(readOnly = true)
    fun readHeaders(file: MultipartFile): List<String> {
        try {
            val bytes = file.bytes
            val content = String(bytes, detectCharset(bytes))
            BufferedReader(StringReader(content)).use { reader ->
                val line = reader.readLine() ?: return emptyList()
                val delimiter = if (line.contains(";")) ";" else ","
                return line.split(delimiter)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        } catch (e: java.io.IOException) {
            throw RuntimeException("Konnte Header nicht lesen", e)
        }
    }

    @Transactional(readOnly = true)
    fun analyzeImport(
        file: MultipartFile,
        lieferantenName: String,
        spaltenZuordnung: Map<String, String>,
    ): ImportAnalysisResult {
        val result = ImportAnalysisResult()
        val newArticleExamples = ArrayList<String>()
        result.newArticleExamples = newArticleExamples

        try {
            val bytes = file.bytes
            val content = String(bytes, detectCharset(bytes))
            BufferedReader(StringReader(content)).use { reader ->
                val headerLine = reader.readLine() ?: return result
                val headerIndex = headerLine.split(";")
                    .mapIndexed { index, header -> header.trim().lowercase() to index }
                    .toMap()

                var line = reader.readLine()
                while (line != null) {
                    val values = line.split(";").toTypedArray()
                    val externeNr = getValue("externeArtikelnummer", values, headerIndex, spaltenZuordnung)
                    val preisStr = getValue("preis", values, headerIndex, spaltenZuordnung)
                    if (externeNr != null && preisStr != null) {
                        if (artikelRepository.findByExterneArtikelnummer(externeNr).isPresent) {
                            result.existingCount = result.existingCount + 1
                        } else {
                            result.newCount = result.newCount + 1
                            if (newArticleExamples.size < 5) {
                                val name = getValue("produktname", values, headerIndex, spaltenZuordnung)
                                newArticleExamples.add(name ?: externeNr)
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: java.io.IOException) {
            throw RuntimeException("CSV konnte nicht analysiert werden", e)
        }
        return result
    }

    @Transactional
    fun importiereCsv(
        file: MultipartFile,
        lieferantenName: String,
        spaltenZuordnung: Map<String, String>,
        defaultKategorieId: Long?,
    ) {
        try {
            val bytes = file.bytes
            val content = String(bytes, detectCharset(bytes))
            BufferedReader(StringReader(content)).use { reader ->
                val headerLine = reader.readLine() ?: return
                val headerIndex = headerLine.split(";")
                    .mapIndexed { index, header -> header.trim().lowercase() to index }
                    .toMap()

                val lieferant = lieferantenRepository.findByLieferantenname(lieferantenName)
                    .orElseGet {
                        Lieferanten().apply {
                            this.lieferantenname = lieferantenName
                            this.istAktiv = true
                            this.startZusammenarbeit = Date()
                        }.let(lieferantenRepository::save)
                    }

                val defaultKategorie: Kategorie? = defaultKategorieId
                    ?.let { kategorieRepository.findById(Math.toIntExact(it)).orElse(null) }

                var line = reader.readLine()
                while (line != null) {
                    val values = line.split(";").toTypedArray()
                    val externeNr = getValue("externeArtikelnummer", values, headerIndex, spaltenZuordnung)
                    val preisStr = getValue("preis", values, headerIndex, spaltenZuordnung)
                    val einheitStr = getValue("preiseinheit", values, headerIndex, spaltenZuordnung)
                    if (externeNr == null || preisStr == null) {
                        line = reader.readLine()
                        continue
                    }

                    var isNew = false
                    val artikel = artikelRepository.findByExterneArtikelnummer(externeNr).orElseGet {
                        isNew = true
                        Artikel()
                    }

                    setIfPresent({ artikel.produktlinie = it }, "produktlinie", values, headerIndex, spaltenZuordnung)
                    setIfPresent({ artikel.produktname = it }, "produktname", values, headerIndex, spaltenZuordnung)
                    getValue("werkstoff", values, headerIndex, spaltenZuordnung)?.let {
                        artikel.werkstoff = resolveWerkstoff(it)
                    }
                    setIfPresent({ artikel.produkttext = it }, "produkttext", values, headerIndex, spaltenZuordnung)
                    setIfPresentLong({ artikel.verpackungseinheit = it }, "verpackungseinheit", values, headerIndex, spaltenZuordnung)
                    setIfPresent({ artikel.preiseinheit = it }, "preiseinheit", values, headerIndex, spaltenZuordnung)

                    if (isNew && defaultKategorie != null) {
                        artikel.kategorie = defaultKategorie
                    }

                    var preis = parseBigDecimal(preisStr)
                    if (preis == null) {
                        line = reader.readLine()
                        continue
                    }

                    val einheit = parseBigDecimal(einheitStr)
                    if (einheit != null && einheit > BigDecimal.ZERO) {
                        preis = preis.divide(einheit, 4, RoundingMode.HALF_UP)
                    }

                    preis = normalizePreis(preis)
                    if (preis == null) {
                        log.warn("Preis fuer Artikel {} liegt außerhalb des erwarteten Bereichs", externeNr)
                        line = reader.readLine()
                        continue
                    }

                    val lap = artikel.artikelpreis
                        .firstOrNull { it.lieferant?.id == lieferant.id }
                        ?: LieferantenArtikelPreise().also {
                            it.artikel = artikel
                            it.lieferant = lieferant
                            artikel.artikelpreis.add(it)
                        }

                    lap.externeArtikelnummer = externeNr
                    lap.preis = preis
                    lap.preisAenderungsdatum = Date()

                    artikelRepository.save(artikel)
                    line = reader.readLine()
                }
            }
        } catch (e: java.io.IOException) {
            throw RuntimeException("CSV konnte nicht importiert werden", e)
        }
    }

    private fun getValue(
        feld: String,
        values: Array<String>,
        headerIndex: Map<String, Int>,
        mapping: Map<String, String>,
    ): String? {
        val header = mapping[feld]
        val idx = if (header != null) {
            headerIndex[header.lowercase()]
        } else {
            defaultHeaders[feld]?.let { headerIndex[it] }
        }
        if (idx == null || idx >= values.size) {
            return null
        }
        return values[idx].trim().takeIf { it.isNotEmpty() }
    }

    private fun setIfPresent(
        setter: (String) -> Unit,
        feld: String,
        values: Array<String>,
        headerIndex: Map<String, Int>,
        mapping: Map<String, String>,
    ) {
        getValue(feld, values, headerIndex, mapping)?.let(setter)
    }

    private fun setIfPresentLong(
        setter: (Long) -> Unit,
        feld: String,
        values: Array<String>,
        headerIndex: Map<String, Int>,
        mapping: Map<String, String>,
    ) {
        val digits = getValue(feld, values, headerIndex, mapping)?.replace(Regex("[^0-9]"), "")
        if (!digits.isNullOrEmpty()) {
            try {
                setter(digits.toLong())
            } catch (_: NumberFormatException) {
            }
        }
    }

    private fun resolveWerkstoff(werkstoffName: String?): Werkstoff? {
        val normalized = werkstoffName?.trim()
        if (normalized.isNullOrBlank()) {
            return null
        }
        val existing = werkstoffRepository.findByNameIgnoreCase(normalized)
        if (existing.isPresent) {
            return existing.get()
        }
        return try {
            werkstoffRepository.save(Werkstoff().apply { name = normalized })
        } catch (e: DataIntegrityViolationException) {
            werkstoffRepository.findByNameIgnoreCase(normalized).orElseThrow { e }
        }
    }

    private fun normalizePreis(preis: BigDecimal): BigDecimal? {
        val min = BigDecimal("0.50")
        val max = BigDecimal("10.00")

        if (preis > max) {
            val durchHundert = preis.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            if (durchHundert >= min && durchHundert <= max) {
                return durchHundert
            }
            val durchTausend = preis.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
            if (durchTausend >= min && durchTausend <= max) {
                return durchTausend
            }
            return null
        }

        if (preis < min) {
            val malHundert = preis.multiply(BigDecimal.valueOf(100))
            if (malHundert >= min && malHundert <= max) {
                return malHundert
            }
            val malTausend = preis.multiply(BigDecimal.valueOf(1000))
            if (malTausend >= min && malTausend <= max) {
                return malTausend
            }
            return null
        }

        return preis
    }

    private fun parseBigDecimal(value: String?): BigDecimal? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            BigDecimal(value.replace(",", "."))
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        val charsetsToTry = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("Windows-1252"),
            StandardCharsets.ISO_8859_1,
            Charset.forName("Windows-1250"),
        )

        if (hasBOM(bytes)) {
            log.debug("UTF-8 BOM erkannt")
            return StandardCharsets.UTF_8
        }

        for (charset in charsetsToTry) {
            if (isValidEncoding(bytes, charset)) {
                log.debug("Codierung erkannt: {}", charset.name())
                return charset
            }
        }

        log.warn("Keine eindeutige Codierung erkannt, verwende UTF-8 als Fallback")
        return StandardCharsets.UTF_8
    }

    private fun hasBOM(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()

    private fun isValidEncoding(bytes: ByteArray, charset: Charset): Boolean {
        return try {
            val content = String(bytes, charset)
            if (content.contains("\uFFFD")) {
                return false
            }
            val controlChars = content.chars()
                .filter { Character.isISOControl(it) && it != '\n'.code && it != '\r'.code && it != '\t'.code }
                .count()

            if (controlChars > content.length * 0.01) {
                return false
            }

            content.contains(";") || content.contains(",")
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArtikelImportService::class.java)

        private val defaultHeaders = mapOf(
            "externeArtikelnummer" to "materialnummer",
            "preis" to "nettopreis",
            "produktlinie" to "produktlinie",
            "produktname" to "produktname",
            "werkstoff" to "werkstoff",
            "produkttext" to "produkttext",
            "verpackungseinheit" to "packgroesse",
            "preiseinheit" to "preiseinheit",
            "waehrung" to "waehrung",
        )
    }
}
