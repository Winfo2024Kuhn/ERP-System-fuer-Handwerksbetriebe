package org.example.kalkulationsprogramm.service

import com.lowagie.text.Image
import org.example.kalkulationsprogramm.domain.Firmeninformation
import org.example.kalkulationsprogramm.domain.Gewerk
import org.example.kalkulationsprogramm.dto.FirmeninformationDto
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository
import org.example.kalkulationsprogramm.repository.GewerkRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

@Service
class FirmeninformationService(
    private val repository: FirmeninformationRepository,
    private val gewerkRepository: GewerkRepository,
) {
    @Value("\${firma.logo.upload-dir:uploads/firma/logo}")
    private lateinit var logoUploadDir: String

    @Transactional(readOnly = true)
    fun getFirmeninformation(): FirmeninformationDto =
        toDto(repository.getOrCreate())

    @Transactional
    fun speichern(dto: FirmeninformationDto): FirmeninformationDto {
        var fi = repository.getOrCreate()

        fi.firmenname = dto.firmenname
        fi.strasse = dto.strasse
        fi.plz = dto.plz
        fi.ort = dto.ort
        fi.telefon = dto.telefon
        fi.fax = dto.fax
        fi.email = dto.email
        fi.website = dto.website
        fi.steuernummer = dto.steuernummer
        fi.ustIdNr = dto.ustIdNr
        fi.handelsregister = dto.handelsregister
        fi.handelsregisterNummer = dto.handelsregisterNummer
        fi.bankName = dto.bankName
        fi.iban = dto.iban
        fi.bic = dto.bic
        fi.logoDateiname = dto.logoDateiname
        fi.geschaeftsfuehrer = dto.geschaeftsfuehrer
        fi.fusszeileText = dto.fusszeileText
        fi.googleBewertungsLink = normalizeUrl(dto.googleBewertungsLink)

        fi.mahnverfahrenAktiv = dto.isMahnverfahrenAktiv
        fi.tageBisZahlungserinnerung = positivOrDefault(dto.tageBisZahlungserinnerung, 7)
        fi.tageBisErsteMahnung = positivOrDefault(dto.tageBisErsteMahnung, 14)
        fi.tageBisZweiteMahnung = positivOrDefault(dto.tageBisZweiteMahnung, 21)
        fi.mahnverfahrenNeuesZahlungszielTage = positivOrDefault(dto.mahnverfahrenNeuesZahlungszielTage, 7)

        fi.gewerk = dto.gewerkId?.let { gewerkId ->
            gewerkRepository.findById(gewerkId)
                .orElseThrow { IllegalArgumentException("Gewerk nicht gefunden: $gewerkId") }
        }
        fi.bgSatzOverride = dto.bgSatzOverride

        fi = repository.save(fi)
        return toDto(fi)
    }

    @Transactional
    @Throws(IOException::class)
    fun speichereLogoDatei(datei: MultipartFile?): FirmeninformationDto {
        if (datei == null || datei.isEmpty) {
            throw IllegalArgumentException("Datei ist leer")
        }
        if (datei.size > MAX_LOGO_BYTES) {
            throw IllegalArgumentException("Datei zu groß (maximal ${MAX_LOGO_BYTES / 1024 / 1024} MB)")
        }
        val contentType = datei.contentType
        val endung = MIME_ZU_ENDUNG[contentType?.lowercase(Locale.ROOT) ?: ""]
            ?: throw IllegalArgumentException("Ungültiger Dateityp - erlaubt sind PNG, JPEG und WebP")

        val basis = Path.of(logoUploadDir).toAbsolutePath().normalize()
        Files.createDirectories(basis)

        val neuerDateiname = "logo.$endung"
        val ziel = basis.resolve(neuerDateiname).normalize()
        if (!ziel.startsWith(basis)) {
            throw IllegalStateException("Ungueltiger Zielpfad")
        }

        var fi = repository.getOrCreate()
        val alterDateiname = fi.logoDateiname
        if (alterDateiname != null && alterDateiname != neuerDateiname) {
            loescheLogoDateiFallsVorhanden(basis, alterDateiname)
        }

        datei.inputStream.use { input ->
            Files.copy(input, ziel, StandardCopyOption.REPLACE_EXISTING)
        }

        fi.logoDateiname = neuerDateiname
        fi = repository.save(fi)
        log.info("Firmenlogo gespeichert: dateiname={} groesse={}B", neuerDateiname, datei.size)
        return toDto(fi)
    }

    @Transactional
    fun loescheLogoDatei(): FirmeninformationDto {
        var fi = repository.getOrCreate()
        val dateiname = fi.logoDateiname
        if (!dateiname.isNullOrBlank()) {
            val basis = Path.of(logoUploadDir).toAbsolutePath().normalize()
            loescheLogoDateiFallsVorhanden(basis, dateiname)
        }
        fi.logoDateiname = null
        fi = repository.save(fi)
        return toDto(fi)
    }

    @Transactional(readOnly = true)
    fun loadLogoBytes(): ByteArray? {
        val fi = repository.findFirmeninformation().orElse(null)
        if (fi == null || fi.logoDateiname.isNullOrBlank()) {
            return null
        }
        val basis = Path.of(logoUploadDir).toAbsolutePath().normalize()
        val datei = basis.resolve(fi.logoDateiname).normalize()
        if (!datei.startsWith(basis) || !Files.isRegularFile(datei)) {
            return null
        }
        return try {
            Files.readAllBytes(datei)
        } catch (e: IOException) {
            log.warn("Firmenlogo konnte nicht gelesen werden: {}", datei, e)
            null
        }
    }

    @Transactional(readOnly = true)
    fun loadLogoImage(): Image? {
        val bytes = loadLogoBytes() ?: return null
        return try {
            Image.getInstance(bytes)
        } catch (e: Exception) {
            log.warn("Firmenlogo konnte nicht als PDF-Bild geladen werden", e)
            null
        }
    }

    @Transactional(readOnly = true)
    fun ermittleLogoContentType(): String? {
        val fi = repository.findFirmeninformation().orElse(null)
        val name = fi?.logoDateiname?.lowercase(Locale.ROOT) ?: return null
        return when {
            name.endsWith(".png") -> MediaType.IMAGE_PNG_VALUE
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> MediaType.IMAGE_JPEG_VALUE
            name.endsWith(".webp") -> "image/webp"
            else -> MediaType.APPLICATION_OCTET_STREAM_VALUE
        }
    }

    private fun loescheLogoDateiFallsVorhanden(basis: Path, dateiname: String?) {
        if (dateiname.isNullOrBlank()) {
            return
        }
        val baseName = Path.of(dateiname).fileName.toString()
        val datei = basis.resolve(baseName).normalize()
        if (!datei.startsWith(basis)) {
            return
        }
        try {
            Files.deleteIfExists(datei)
        } catch (e: IOException) {
            log.warn("Altes Firmenlogo konnte nicht geloescht werden: {}", datei, e)
        }
    }

    private fun toDto(fi: Firmeninformation): FirmeninformationDto {
        val dto = FirmeninformationDto()
        dto.id = fi.id
        dto.firmenname = fi.firmenname
        dto.strasse = fi.strasse
        dto.plz = fi.plz
        dto.ort = fi.ort
        dto.telefon = fi.telefon
        dto.fax = fi.fax
        dto.email = fi.email
        dto.website = fi.website
        dto.steuernummer = fi.steuernummer
        dto.ustIdNr = fi.ustIdNr
        dto.handelsregister = fi.handelsregister
        dto.handelsregisterNummer = fi.handelsregisterNummer
        dto.bankName = fi.bankName
        dto.iban = fi.iban
        dto.bic = fi.bic
        dto.logoDateiname = fi.logoDateiname
        dto.geschaeftsfuehrer = fi.geschaeftsfuehrer
        dto.fusszeileText = fi.fusszeileText
        dto.googleBewertungsLink = fi.googleBewertungsLink
        dto.isMahnverfahrenAktiv = fi.isMahnverfahrenAktiv()
        dto.tageBisZahlungserinnerung = fi.tageBisZahlungserinnerung
        dto.tageBisErsteMahnung = fi.tageBisErsteMahnung
        dto.tageBisZweiteMahnung = fi.tageBisZweiteMahnung
        dto.mahnverfahrenNeuesZahlungszielTage = fi.mahnverfahrenNeuesZahlungszielTage
        val gewerk: Gewerk? = fi.gewerk
        if (gewerk != null) {
            dto.gewerkId = gewerk.id
            dto.gewerkName = gewerk.name
            dto.bgName = gewerk.bgName
            dto.bgSatzVorschlag = gewerk.bgSatzProzent
        }
        dto.bgSatzOverride = fi.bgSatzOverride
        dto.bgSatzEffektiv = fi.bgSatzOverride ?: gewerk?.bgSatzProzent
        return dto
    }

    companion object {
        private val log = LoggerFactory.getLogger(FirmeninformationService::class.java)
        const val MAX_LOGO_BYTES: Long = 2L * 1024 * 1024

        val ERLAUBTE_MIME_TYPES: List<String> = listOf(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp",
        )

        private val MIME_ZU_ENDUNG: Map<String, String> = mapOf(
            MediaType.IMAGE_PNG_VALUE to "png",
            MediaType.IMAGE_JPEG_VALUE to "jpg",
            "image/webp" to "webp",
        )

        private fun positivOrDefault(wert: Int, fallback: Int): Int =
            if (wert > 0) wert else fallback

        private fun normalizeUrl(url: String?): String? {
            val trimmed = url?.trim()
            return if (trimmed.isNullOrEmpty()) null else trimmed
        }
    }
}
