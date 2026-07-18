package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.AnfrageDokument
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageSeiteResponseDto
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class AnfrageService(
    private val anfrageRepository: AnfrageRepository,
    private val dateiSpeicherService: DateiSpeicherService,
    private val anfrageDokumentRepository: AnfrageDokumentRepository,
    private val kundeRepository: KundeRepository,
    private val emailRepository: EmailRepository,
    private val projektRepository: ProjektRepository,
    private val ausgangsGeschaeftsDokumentRepository: AusgangsGeschaeftsDokumentRepository,
    @Value("\${file.mail-attachment-dir}") mailAttachmentDir: String?,
    private val eventPublisher: ApplicationEventPublisher,
    private val ausgangsGeschaeftsDokumentService: AusgangsGeschaeftsDokumentService,
) {
    private val mailAttachmentBaseDir: Path? =
        if (mailAttachmentDir.isNullOrBlank()) null else Path.of(mailAttachmentDir).toAbsolutePath().normalize().resolve("email")

    fun erstelleAnfrage(dto: AnfrageErstellenDto): AnfrageResponseDto {
        val anfrage = Anfrage()
        hydrateDtoFromKunde(dto, anfrage)
        fillAnfrage(anfrage, dto)
        anfrageRepository.save(anfrage)
        publishEmailBackfillEvent(anfrage, dto, true)
        return mapToDto(anfrage)
    }

    fun erstelleAnfrage(dto: AnfrageErstellenDto, imageFile: MultipartFile?): AnfrageResponseDto {
        val anfrage = Anfrage()
        hydrateDtoFromKunde(dto, anfrage)
        fillAnfrage(anfrage, dto)
        if (imageFile != null && !imageFile.isEmpty) {
            anfrage.bildUrl = dateiSpeicherService.speichereBild(imageFile)
        }
        anfrageRepository.save(anfrage)
        publishEmailBackfillEvent(anfrage, dto, true)
        return mapToDto(anfrage)
    }

    private fun fillAnfrage(anfrage: Anfrage, dto: AnfrageErstellenDto) {
        anfrage.bauvorhaben = dto.bauvorhaben
        anfrage.anlegedatum = dto.anlegedatum ?: LocalDate.now()
        anfrage.projektStrasse = dto.projektStrasse
        anfrage.projektPlz = dto.projektPlz
        anfrage.projektOrt = dto.projektOrt
        anfrage.kurzbeschreibung = dto.kurzbeschreibung
        dto.abgeschlossen?.let { anfrage.abgeschlossen = it }
        if (!dto.kundenEmails.isNullOrEmpty()) {
            anfrage.kundenEmails.clear()
            anfrage.kundenEmails.addAll(dto.kundenEmails!!)
        }
    }

    fun alle(): List<AnfrageResponseDto> =
        anfrageRepository.findAllWithKundenEmails()
            .filter { it.projekt == null }
            .map { mapToDto(it) }

    fun suche(
        jahr: Int?,
        kundenname: String?,
        bauvorhaben: String?,
        anfragesnummer: String?,
        q: String?,
        nurOhneProjekt: Boolean,
    ): List<AnfrageResponseDto> {
        val freitext = trimToNull(q)
        if (freitext != null) {
            val startDate = jahr?.let { LocalDate.of(it, 1, 1) }
            val endDate = jahr?.let { LocalDate.of(it, 12, 31) }
            return anfrageRepository.searchByBauvorhabenOrKundeOrEmail(freitext)
                .filter { a ->
                    if (nurOhneProjekt && a.projekt != null) return@filter false
                    if (startDate == null || endDate == null) return@filter true
                    val anlegedatum = a.anlegedatum ?: return@filter false
                    !anlegedatum.isBefore(startDate) && !anlegedatum.isAfter(endDate)
                }
                .map { mapToDto(it) }
        }

        val noFilters = jahr == null && kundenname.isNullOrBlank() && bauvorhaben.isNullOrBlank() && anfragesnummer.isNullOrBlank()
        if (noFilters) {
            return if (nurOhneProjekt) {
                alle()
            } else {
                anfrageRepository.findAllWithKundenEmails().map { mapToDto(it) }
            }
        }

        val startDate = jahr?.let { LocalDate.of(it, 1, 1) }
        val endDate = jahr?.let { LocalDate.of(it, 12, 31) }
        return anfrageRepository.search(trimToNull(kundenname), trimToNull(bauvorhaben), startDate, endDate, trimToNull(anfragesnummer))
            .filter { !nurOhneProjekt || it.projekt == null }
            .map { mapToDto(it) }
    }

    fun sucheSeite(
        jahr: Int?,
        kundenname: String?,
        bauvorhaben: String?,
        anfragesnummer: String?,
        q: String?,
        nurOhneProjekt: Boolean,
        page: Int,
        size: Int,
    ): AnfrageSeiteResponseDto {
        val seite = maxOf(0, page)
        val seitenGroesse = minOf(maxOf(1, size), 100)
        val alle = ArrayList(findeGefiltert(jahr, kundenname, bauvorhaben, anfragesnummer, q, nurOhneProjekt))
        alle.sortWith(compareByDescending { it.createdAt ?: LocalDateTime.MIN })
        val gesamt = alle.size
        val von = minOf(seite * seitenGroesse, gesamt)
        val bis = minOf(von + seitenGroesse, gesamt)
        val mapped = alle.subList(von, bis).map { mapToDto(it) }
        return AnfrageSeiteResponseDto(mapped, gesamt.toLong(), seite, seitenGroesse)
    }

    private fun findeGefiltert(
        jahr: Int?,
        kundenname: String?,
        bauvorhaben: String?,
        anfragesnummer: String?,
        q: String?,
        nurOhneProjekt: Boolean,
    ): List<Anfrage> {
        val freitext = trimToNull(q)
        val startDate = jahr?.let { LocalDate.of(it, 1, 1) }
        val endDate = jahr?.let { LocalDate.of(it, 12, 31) }
        if (freitext != null) {
            return anfrageRepository.searchByBauvorhabenOrKundeOrEmail(freitext)
                .filter { a ->
                    if (nurOhneProjekt && a.projekt != null) return@filter false
                    if (startDate == null || endDate == null) return@filter true
                    val anlegedatum = a.anlegedatum ?: return@filter false
                    !anlegedatum.isBefore(startDate) && !anlegedatum.isAfter(endDate)
                }
        }
        val kn = trimToNull(kundenname)
        val bv = trimToNull(bauvorhaben)
        val anr = trimToNull(anfragesnummer)
        val noFilters = jahr == null && kn == null && bv == null && anr == null
        var alle = if (noFilters) anfrageRepository.findAllWithKundenEmails() else anfrageRepository.search(kn, bv, startDate, endDate, anr)
        if (nurOhneProjekt) alle = alle.filter { it.projekt == null }
        return alle
    }

    fun verfuegbareAnlegeJahre(): List<Int> = anfrageRepository.findDistinctAnlegedatumJahre()

    private fun trimToNull(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    fun finde(id: Long?): Anfrage? =
        id?.let { anfrageRepository.findById(it).orElse(null) }

    fun findeDto(id: Long?): AnfrageResponseDto? {
        if (id == null) return null
        ausgangsGeschaeftsDokumentService.aktualisiereAnfragePreisAusDokumenten(id)
        return anfrageRepository.findById(id).map { mapToDto(it) }.orElse(null)
    }

    fun aktualisiereAnfrage(id: Long?, dto: AnfrageErstellenDto): AnfrageResponseDto? =
        id?.let { anfrageRepository.findById(it).map { anfrage -> updateExisting(anfrage, dto, null) }.orElse(null) }

    fun aktualisiereAnfrage(id: Long?, dto: AnfrageErstellenDto, imageFile: MultipartFile?): AnfrageResponseDto? =
        id?.let { anfrageRepository.findById(it).map { anfrage -> updateExisting(anfrage, dto, imageFile) }.orElse(null) }

    private fun updateExisting(a: Anfrage, dto: AnfrageErstellenDto, imageFile: MultipartFile?): AnfrageResponseDto {
        hydrateDtoFromKunde(dto, a)
        a.bauvorhaben = dto.bauvorhaben
        a.projektStrasse = dto.projektStrasse
        a.projektPlz = dto.projektPlz
        a.projektOrt = dto.projektOrt
        a.kurzbeschreibung = dto.kurzbeschreibung
        dto.betrag?.let { a.betrag = it }
        dto.abgeschlossen?.let { a.abgeschlossen = it }
        dto.anlegedatum?.let { a.anlegedatum = it }
        if (imageFile != null && !imageFile.isEmpty) {
            if (!a.bildUrl.isNullOrBlank()) {
                try {
                    dateiSpeicherService.loescheBild(a.bildUrl)
                } catch (_: Exception) {
                }
            }
            a.bildUrl = dateiSpeicherService.speichereBild(imageFile)
        }
        if (dto.kundenEmails != null) {
            a.kundenEmails.clear()
            a.kundenEmails.addAll(dto.kundenEmails!!)
        }
        anfrageRepository.save(a)
        publishEmailBackfillEvent(a, dto, false)
        return mapToDto(a)
    }

    private fun hydrateDtoFromKunde(dto: AnfrageErstellenDto?, anfrage: Anfrage) {
        val kundenId = dto?.kundenId
        if (kundenId == null || kundenId <= 0) return
        val kunde = kundeRepository.findById(kundenId).orElse(null) ?: return
        anfrage.kunde = kunde
        if (!StringUtils.hasText(dto.kunde)) dto.kunde = kunde.name
        if (!StringUtils.hasText(dto.kundennummer)) dto.kundennummer = kunde.kundennummer
        if (dto.kundenEmails.isNullOrEmpty()) {
            dto.kundenEmails = ArrayList(kunde.kundenEmails)
        }
    }

    private fun publishEmailBackfillEvent(anfrage: Anfrage, dto: AnfrageErstellenDto, isNew: Boolean) {
        try {
            val kundenEmails = dto.kundenEmails
            if (!kundenEmails.isNullOrEmpty()) {
                val event = if (isNew) {
                    EmailAddressChangedEvent.forNewEntity(EmailAddressChangedEvent.EntityType.ANFRAGE, requireNotNull(anfrage.id), kundenEmails)
                } else {
                    EmailAddressChangedEvent.forAddressChange(EmailAddressChangedEvent.EntityType.ANFRAGE, requireNotNull(anfrage.id), kundenEmails, kundenEmails)
                }
                eventPublisher.publishEvent(event)
            }
        } catch (_: Exception) {
        }
    }

    fun speichereEmail(anfrage: Anfrage, email: String?) {
        val kunde = anfrage.kunde
        if (kunde != null && !email.isNullOrBlank()) {
            if (!kunde.kundenEmails.contains(email)) {
                kunde.kundenEmails.add(email)
                kundeRepository.save(kunde)
            }
        }
        anfrage.emailVersandDatum = LocalDate.now()
        anfrageRepository.save(anfrage)
    }

    fun speichere(anfrage: Anfrage): Anfrage = anfrageRepository.save(anfrage)

    fun updateAnfrageKurzbeschreibung(id: Long?, kurzbeschreibung: String?): AnfrageResponseDto? =
        id?.let { anfrageId -> anfrageRepository.findById(anfrageId).map {
            it.kurzbeschreibung = kurzbeschreibung
            anfrageRepository.save(it)
            mapToDto(it)
        }.orElse(null) }

    fun loesche(id: Long?): Boolean =
        id?.let { anfrageId -> anfrageRepository.findById(anfrageId).map { a ->
            val docs = anfrageDokumentRepository.findByAnfrageId(a.id)
            for (d in docs) {
                try {
                    dateiSpeicherService.loescheAnfrageDatei(d.id)
                } catch (_: Exception) {
                }
            }
            val projektId = a.projekt?.id
            deleteEmailAttachmentsDirectory(a.id)
            anfrageRepository.delete(a)
            if (projektId != null) dateiSpeicherService.aktualisiereProjektFinanzstatus(projektId)
            true
        }.orElse(false) } ?: false

    enum class LoeschGrund {
        OK,
        NICHT_GEFUNDEN,
        EMAIL_VORHANDEN,
        DATEI_VORHANDEN,
        GESCHAEFTSDOKUMENT_VORHANDEN,
        EMAIL_VERSENDET,
        BENUTZER_NOTIZ_VORHANDEN,
        IN_PROJEKT_UMGEWANDELT,
    }

    data class LoeschResult(val grund: LoeschGrund, val kundeMitgeloescht: Boolean, val hinweis: String) {
        fun ok(): Boolean = grund == LoeschGrund.OK
        fun grund(): LoeschGrund = grund
        fun kundeMitgeloescht(): Boolean = kundeMitgeloescht
        fun hinweis(): String = hinweis
    }

    @Transactional
    fun loescheMitPruefung(id: Long?, cascadeKunde: Boolean): LoeschResult {
        val anfrageId = id ?: return LoeschResult(LoeschGrund.NICHT_GEFUNDEN, false, "Anfrage existiert nicht (mehr).")
        val anfrage = anfrageRepository.findById(anfrageId).orElse(null)
            ?: return LoeschResult(LoeschGrund.NICHT_GEFUNDEN, false, "Anfrage existiert nicht (mehr).")
        if (anfrage.projekt != null) {
            return LoeschResult(LoeschGrund.IN_PROJEKT_UMGEWANDELT, false, "Anfrage ist bereits in ein Projekt umgewandelt – Löschen nicht möglich.")
        }
        if (anfrage.emailVersandDatum != null) {
            return LoeschResult(LoeschGrund.EMAIL_VERSENDET, false, "Es wurde bereits eine E-Mail zu dieser Anfrage versendet.")
        }
        if (emailRepository.findByAnfrageOrderBySentAtDesc(anfrage).isNotEmpty()) {
            return LoeschResult(LoeschGrund.EMAIL_VORHANDEN, false, "An dieser Anfrage hängen bereits E-Mails – nicht löschbar.")
        }

        val docs = anfrageDokumentRepository.findByAnfrageId(anfrage.id)
        if (docs.any { it is AnfrageGeschaeftsdokument }) {
            return LoeschResult(LoeschGrund.GESCHAEFTSDOKUMENT_VORHANDEN, false, "An dieser Anfrage hängen Angebote/Auftragsbestätigungen.")
        }
        if (docs.isNotEmpty()) {
            return LoeschResult(LoeschGrund.DATEI_VORHANDEN, false, "An dieser Anfrage hängen Dateien – bitte zuerst entfernen.")
        }
        if (ausgangsGeschaeftsDokumentRepository.findByAnfrageIdOrderByDatumDesc(anfrage.id).isNotEmpty()) {
            return LoeschResult(LoeschGrund.GESCHAEFTSDOKUMENT_VORHANDEN, false, "An dieser Anfrage hängen ausgehende Geschäftsdokumente.")
        }

        val nurFunnelNotizen = anfrage.notizen.all {
            it.mitarbeiter != null && AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN == it.mitarbeiter!!.loginToken
        }
        if (!nurFunnelNotizen) {
            return LoeschResult(LoeschGrund.BENUTZER_NOTIZ_VORHANDEN, false, "Es wurden bereits Bautagebuch-Notizen erfasst.")
        }

        val kunde = anfrage.kunde
        for (d in docs) {
            try {
                dateiSpeicherService.loescheAnfrageDatei(d.id)
            } catch (_: Exception) {
            }
        }
        anfrage.notizen.flatMap { it.bilder }.forEach {
            try {
                dateiSpeicherService.loescheBild("/api/images/${it.gespeicherterDateiname}")
            } catch (_: Exception) {
            }
        }
        deleteEmailAttachmentsDirectory(anfrage.id)
        anfrageRepository.delete(anfrage)
        anfrageRepository.flush()

        var kundeWeg = false
        if (cascadeKunde && kunde != null) {
            val andereAnfragen = anfrageRepository.findByKundeId(kunde.id).size.toLong()
            val projekteDesKunden = projektRepository.findByKundenId_Id(kunde.id).size.toLong()
            if (andereAnfragen == 0L && projekteDesKunden == 0L) {
                try {
                    kundeRepository.delete(kunde)
                    kundeWeg = true
                } catch (_: Exception) {
                }
            }
        }
        return LoeschResult(LoeschGrund.OK, kundeWeg, if (kundeWeg) "Anfrage und verwaister Kunde gelöscht." else "Anfrage gelöscht.")
    }

    private fun mapToDto(a: Anfrage): AnfrageResponseDto {
        val dto = AnfrageResponseDto()
        dto.id = a.id
        val kunde = a.kunde
        if (kunde != null) {
            dto.kundenId = kunde.id
            dto.kundenName = sanitize(kunde.name)
            dto.kundennummer = kunde.kundennummer
            dto.kundenEmails = kunde.kundenEmails
            if (a.kundenEmails.isNotEmpty()) {
                val merged = LinkedHashSet(dto.kundenEmails ?: emptyList())
                merged.addAll(a.kundenEmails)
                dto.kundenEmails = ArrayList(merged)
            }
            dto.kundenStrasse = kunde.strasse
            dto.kundenPlz = kunde.plz
            dto.kundenOrt = kunde.ort
            dto.kundenTelefon = kunde.telefon
            dto.kundenMobiltelefon = kunde.mobiltelefon
            dto.kundenAnsprechpartner = kunde.ansprechspartner
            dto.kundenAnrede = kunde.anrede?.name
        }
        dto.bauvorhaben = sanitize(a.bauvorhaben)
        dto.betrag = a.betrag
        dto.emailVersandDatum = a.emailVersandDatum
        dto.anlegedatum = a.anlegedatum
        dto.bildUrl = a.bildUrl
        dto.projektStrasse = a.projektStrasse
        dto.projektPlz = a.projektPlz
        dto.projektOrt = a.projektOrt
        dto.kurzbeschreibung = a.kurzbeschreibung
        dto.isAbgeschlossen = a.isAbgeschlossen()
        dto.createdAt = a.createdAt
        dto.projektId = a.projekt?.id

        anfrageDokumentRepository.findByAnfrageId(a.id)
            .filterIsInstance<AnfrageGeschaeftsdokument>()
            .firstOrNull { it.geschaeftsdokumentart?.lowercase()?.contains("anfrage") == true }
            ?.let { dto.anfragesnummer = it.dokumentid }

        val anfragesnummerNeu = ausgangsGeschaeftsDokumentService.resolveAnfragesnummer(a.id)
        if (anfragesnummerNeu != null) dto.anfragesnummer = anfragesnummerNeu
        return dto
    }

    private fun sanitize(s: String?): String? = s?.replace("ß", "ss")?.replace("\uFFFD", "ss")?.replace("?", "")

    private fun deleteEmailAttachmentsDirectory(anfrageId: Long?) {
        val baseDir = mailAttachmentBaseDir ?: return
        if (anfrageId == null) return
        val directory = baseDir.resolve(anfrageId.toString())
        if (!Files.exists(directory)) return
        try {
            Files.walk(directory).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach { path ->
                    try {
                        Files.deleteIfExists(path)
                    } catch (_: IOException) {
                    }
                }
            }
        } catch (_: IOException) {
        }
    }
}
