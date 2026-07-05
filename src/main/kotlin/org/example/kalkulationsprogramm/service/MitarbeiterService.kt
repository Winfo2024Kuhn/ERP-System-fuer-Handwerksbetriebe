package org.example.kalkulationsprogramm.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart
import org.example.kalkulationsprogramm.domain.DokumentGruppe
import org.example.kalkulationsprogramm.domain.Krankenkasse
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.MitarbeiterDokument
import org.example.kalkulationsprogramm.domain.MitarbeiterNotiz
import org.example.kalkulationsprogramm.domain.MitarbeiterStundenlohn
import org.example.kalkulationsprogramm.domain.Qualifikation
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterStundenlohnDto
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.example.kalkulationsprogramm.repository.KrankenkasseRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterDokumentRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterNotizRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Service
class MitarbeiterService(
    private val repository: MitarbeiterRepository,
    private val dokumentRepository: MitarbeiterDokumentRepository,
    private val notizRepository: MitarbeiterNotizRepository,
    private val abteilungRepository: AbteilungRepository,
    private val krankenkasseRepository: KrankenkasseRepository,
    private val stundenlohnRepository: MitarbeiterStundenlohnRepository,
) {
    @Value("\${file.upload-dir:uploads}")
    private lateinit var uploadDir: String

    @Value("\${zeiterfassung.base-url:http://localhost:8080}")
    private lateinit var zeiterfassungBaseUrl: String

    @Transactional(readOnly = true)
    fun list(): List<MitarbeiterDto> = repository.findAll().map { mapToDto(it) }

    @Transactional(readOnly = true)
    fun findById(id: Long): Optional<MitarbeiterDto> = repository.findById(id).map { mapToDto(it) }

    @Transactional(readOnly = true)
    fun findByToken(token: String): Optional<MitarbeiterDto> =
        repository.findByLoginTokenAndAktivTrue(token).map { mapToDto(it) }

    @Transactional
    fun save(id: Long?, dto: MitarbeiterErstellenDto): MitarbeiterDto {
        val entity = if (id != null) {
            repository.findById(id).orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }
        } else {
            Mitarbeiter()
        }

        entity.vorname = dto.vorname
        entity.nachname = dto.nachname
        entity.strasse = dto.strasse
        entity.plz = dto.plz
        entity.ort = dto.ort
        entity.email = dto.email
        entity.telefon = dto.telefon
        entity.festnetz = dto.festnetz
        entity.qualifikation = Qualifikation.fromString(dto.qualifikation)
        entity.stundenlohn = dto.stundenlohn
        entity.geburtstag = dto.geburtstag
        entity.eintrittsdatum = dto.eintrittsdatum
        entity.jahresUrlaub = dto.jahresUrlaub
        entity.aktiv = dto.aktiv ?: true

        val beschaeftigungsart = dto.beschaeftigungsart
        if (!beschaeftigungsart.isNullOrBlank()) {
            try {
                entity.beschaeftigungsart = Beschaeftigungsart.valueOf(beschaeftigungsart)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Unbekannte Beschaeftigungsart: $beschaeftigungsart")
            }
        } else if (entity.beschaeftigungsart == null) {
            entity.beschaeftigungsart = Beschaeftigungsart.REGULAER
        }

        entity.krankenkasse = dto.krankenkasseId?.let {
            krankenkasseRepository.findById(it).orElseThrow { IllegalArgumentException("Krankenkasse nicht gefunden: $it") }
        }
        entity.kinderlos = dto.kinderlos == true
        entity.istGeschaeftsfuehrer = dto.istGeschaeftsfuehrer == true
        if (dto.istGeschaeftsfuehrer == true) {
            validateGeschaeftsfuehrerFelder(dto)
            entity.kalkulatorischerLohnMonat = dto.kalkulatorischerLohnMonat
            entity.geldwertVorteilMonat = dto.geldwertVorteilMonat
        } else {
            entity.kalkulatorischerLohnMonat = null
            entity.geldwertVorteilMonat = null
        }

        if (!dto.abteilungIds.isNullOrEmpty()) {
            val abteilungen = HashSet<Abteilung>()
            for (abteilungId in dto.abteilungIds) {
                abteilungen.add(abteilungRepository.findById(abteilungId).orElseThrow { RuntimeException("Abteilung nicht gefunden: $abteilungId") })
            }
            entity.abteilungen = abteilungen
        } else {
            entity.abteilungen.clear()
        }

        return mapToDto(repository.save(entity))
    }

    @Transactional
    fun delete(id: Long) {
        repository.deleteById(id)
    }

    @Transactional
    fun uploadDokument(mitarbeiterId: Long, file: MultipartFile, gruppe: DokumentGruppe?): MitarbeiterDokumentResponseDto {
        val mitarbeiter = repository.findById(mitarbeiterId).orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }
        try {
            val uploadPath = Path.of(uploadDir).toAbsolutePath().normalize()
            Files.createDirectories(uploadPath)
            val originalFileName = Path.of(StringUtils.cleanPath(requireNotNull(file.originalFilename))).fileName.toString()
            val savedFileName = UUID.randomUUID().toString() + "_" + originalFileName
            val targetLocation = uploadPath.resolve(savedFileName).normalize()
            if (!targetLocation.startsWith(uploadPath)) {
                throw SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt")
            }
            Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)

            val dok = MitarbeiterDokument()
            dok.originalDateiname = originalFileName
            dok.gespeicherterDateiname = savedFileName
            dok.dateityp = file.contentType
            dok.dateigroesse = file.size
            dok.uploadDatum = LocalDate.now()
            dok.dokumentGruppe = gruppe ?: DokumentGruppe.DIVERSE_DOKUMENTE
            dok.mitarbeiter = mitarbeiter
            mitarbeiter.dokumente.add(dok)
            return mapToDokumentDto(dokumentRepository.save(dok))
        } catch (ex: IOException) {
            throw RuntimeException("Konnte Datei nicht speichern", ex)
        }
    }

    @Transactional(readOnly = true)
    fun listDokumente(mitarbeiterId: Long): List<MitarbeiterDokumentResponseDto> =
        dokumentRepository.findByMitarbeiterId(mitarbeiterId).map { mapToDokumentDto(it) }

    private fun mapToDto(m: Mitarbeiter): MitarbeiterDto {
        val dto = MitarbeiterDto()
        dto.id = m.id
        dto.vorname = m.vorname
        dto.nachname = m.nachname
        dto.strasse = m.strasse
        dto.plz = m.plz
        dto.ort = m.ort
        dto.email = m.email
        dto.telefon = m.telefon
        dto.festnetz = m.festnetz
        dto.qualifikation = m.qualifikation?.bezeichnung
        dto.stundenlohn = m.stundenlohn
        dto.geburtstag = m.geburtstag
        dto.eintrittsdatum = m.eintrittsdatum
        dto.jahresUrlaub = m.jahresUrlaub
        dto.aktiv = m.aktiv
        dto.loginToken = m.loginToken
        if (m.abteilungen.isNotEmpty()) {
            dto.abteilungIds = m.abteilungen.mapNotNull { it.id }
            dto.abteilungNames = m.abteilungen.mapNotNull { it.name }.joinToString(", ")
        }
        m.beschaeftigungsart?.let {
            dto.beschaeftigungsart = it.name
            dto.beschaeftigungsartLabel = it.bezeichnung
        }
        m.krankenkasse?.let {
            dto.krankenkasseId = it.id
            dto.krankenkasseName = it.name
        }
        dto.kinderlos = m.kinderlos == true
        dto.istGeschaeftsfuehrer = m.istGeschaeftsfuehrer == true
        dto.kalkulatorischerLohnMonat = m.kalkulatorischerLohnMonat
        dto.geldwertVorteilMonat = m.geldwertVorteilMonat
        return dto
    }

    @Transactional
    fun generateLoginToken(id: Long): String {
        val mitarbeiter = repository.findById(id).orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }
        val token = UUID.randomUUID().toString()
        mitarbeiter.loginToken = token
        repository.save(mitarbeiter)
        return token
    }

    fun generateQrCode(id: Long, width: Int, height: Int): ByteArray {
        val mitarbeiter = repository.findById(id).orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }
        val token = mitarbeiter.loginToken
        if (token.isNullOrEmpty()) {
            throw RuntimeException("Kein Login-Token vorhanden. Bitte erst Token generieren.")
        }
        val qrContent = "$zeiterfassungBaseUrl/zeiterfassung/?token=$token"
        try {
            val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, width, height)
            val outputStream = ByteArrayOutputStream()
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
            return outputStream.toByteArray()
        } catch (e: WriterException) {
            throw RuntimeException("Fehler beim Generieren des QR-Codes", e)
        } catch (e: IOException) {
            throw RuntimeException("Fehler beim Generieren des QR-Codes", e)
        }
    }

    private fun mapToDokumentDto(d: MitarbeiterDokument): MitarbeiterDokumentResponseDto {
        val dto = MitarbeiterDokumentResponseDto()
        dto.id = d.id
        dto.originalDateiname = d.originalDateiname
        dto.dateityp = d.dateityp
        dto.dateigroesse = d.dateigroesse
        dto.uploadDatum = d.uploadDatum
        dto.dokumentGruppe = d.dokumentGruppe
        dto.url = "/api/dokumente/${d.gespeicherterDateiname}"
        return dto
    }

    @Transactional(readOnly = true)
    fun listNotizen(mitarbeiterId: Long): List<MitarbeiterNotizDto> =
        notizRepository.findByMitarbeiterIdOrderByErstelltAmDesc(mitarbeiterId)
            .map { mapNotizDto(it) }

    @Transactional
    fun createNotiz(mitarbeiterId: Long, inhalt: String): MitarbeiterNotizDto {
        val mitarbeiter = repository.findById(mitarbeiterId).orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }
        val saved = notizRepository.save(MitarbeiterNotiz().apply {
            this.inhalt = inhalt
            this.mitarbeiter = mitarbeiter
        })
        return mapNotizDto(saved)
    }

    @Transactional
    fun deleteNotiz(notizId: Long) {
        notizRepository.deleteById(notizId)
    }

    @Transactional(readOnly = true)
    fun listStundenloehne(mitarbeiterId: Long): List<MitarbeiterStundenlohnDto> =
        stundenlohnRepository.findByMitarbeiterIdOrderByGueltigAbDesc(mitarbeiterId).map { mapStundenlohnDto(it) }

    @Transactional(readOnly = true)
    fun getStundenlohnAm(mitarbeiterId: Long, stichtag: LocalDate): BigDecimal? =
        stundenlohnRepository
            .findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(mitarbeiterId, stichtag)
            .map(MitarbeiterStundenlohn::stundenlohn)
            .orElse(null)

    @Transactional
    fun addStundenlohn(mitarbeiterId: Long, dto: MitarbeiterStundenlohnDto): MitarbeiterStundenlohnDto {
        val mitarbeiter = repository.findById(mitarbeiterId).orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $mitarbeiterId") }
        validateStundenlohnDto(dto)
        val saved = stundenlohnRepository.save(MitarbeiterStundenlohn().apply {
            this.mitarbeiter = mitarbeiter
            this.stundenlohn = dto.stundenlohn
            this.gueltigAb = dto.gueltigAb
            this.bemerkung = dto.bemerkung
        })
        syncAktuellenStundenlohn(mitarbeiter)
        return mapStundenlohnDto(saved)
    }

    @Transactional
    fun updateStundenlohn(eintragId: Long, dto: MitarbeiterStundenlohnDto): MitarbeiterStundenlohnDto {
        val entity = stundenlohnRepository.findById(eintragId)
            .orElseThrow { IllegalArgumentException("Stundenlohn-Eintrag nicht gefunden: $eintragId") }
        validateStundenlohnDto(dto)
        entity.stundenlohn = dto.stundenlohn
        entity.gueltigAb = dto.gueltigAb
        entity.bemerkung = dto.bemerkung
        val saved = stundenlohnRepository.save(entity)
        syncAktuellenStundenlohn(requireNotNull(entity.mitarbeiter) { "Mitarbeiter fehlt am Stundenlohn-Eintrag." })
        return mapStundenlohnDto(saved)
    }

    @Transactional
    fun deleteStundenlohn(eintragId: Long) {
        val entity = stundenlohnRepository.findById(eintragId)
            .orElseThrow { IllegalArgumentException("Stundenlohn-Eintrag nicht gefunden: $eintragId") }
        val mitarbeiter = requireNotNull(entity.mitarbeiter) { "Mitarbeiter fehlt am Stundenlohn-Eintrag." }
        stundenlohnRepository.delete(entity)
        syncAktuellenStundenlohn(mitarbeiter)
    }

    private fun mapNotizDto(entity: MitarbeiterNotiz): MitarbeiterNotizDto =
        MitarbeiterNotizDto(
            requireNotNull(entity.id) { "Notiz-ID fehlt." },
            requireNotNull(entity.inhalt) { "Notiz-Inhalt fehlt." },
            requireNotNull(entity.erstelltAm) { "Erstellzeit fehlt." },
            requireNotNull(entity.mitarbeiter?.id) { "Mitarbeiter-ID fehlt." },
        )

    private fun syncAktuellenStundenlohn(mitarbeiter: Mitarbeiter) {
        val aktuell = stundenlohnRepository
            .findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(mitarbeiter.id, LocalDate.now())
            .map(MitarbeiterStundenlohn::stundenlohn)
            .orElse(null)
        mitarbeiter.stundenlohn = aktuell
        repository.save(mitarbeiter)
    }

    companion object {
        private fun validateStundenlohnDto(dto: MitarbeiterStundenlohnDto) {
            val stundenlohn = dto.stundenlohn
            if (stundenlohn == null || stundenlohn.signum() < 0) {
                throw IllegalArgumentException("Stundenlohn ist Pflicht und darf nicht negativ sein.")
            }
            if (dto.gueltigAb == null) {
                throw IllegalArgumentException("Gueltig-ab-Datum ist Pflicht.")
            }
        }

        private fun validateGeschaeftsfuehrerFelder(dto: MitarbeiterErstellenDto) {
            val kalkulatorischerLohnMonat = dto.kalkulatorischerLohnMonat
            if (kalkulatorischerLohnMonat == null || kalkulatorischerLohnMonat.signum() < 0) {
                throw IllegalArgumentException("Kalkulatorischer Lohn pro Monat ist Pflicht und darf nicht negativ sein, wenn die Person als Geschaeftsfuehrer markiert ist.")
            }
            val geldwertVorteilMonat = dto.geldwertVorteilMonat
            if (geldwertVorteilMonat != null && geldwertVorteilMonat.signum() < 0) {
                throw IllegalArgumentException("Geldwerte Vorteile pro Monat duerfen nicht negativ sein.")
            }
        }

        private fun mapStundenlohnDto(e: MitarbeiterStundenlohn): MitarbeiterStundenlohnDto {
            val dto = MitarbeiterStundenlohnDto()
            dto.id = e.id
            dto.mitarbeiterId = e.mitarbeiter?.id
            dto.stundenlohn = e.stundenlohn
            dto.gueltigAb = e.gueltigAb
            dto.bemerkung = e.bemerkung
            return dto
        }
    }
}
