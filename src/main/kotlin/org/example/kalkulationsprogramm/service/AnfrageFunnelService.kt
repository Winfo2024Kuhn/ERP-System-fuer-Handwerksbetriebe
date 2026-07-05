package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.AnfrageNotiz
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class AnfrageFunnelService(
    private val kundeRepository: KundeRepository,
    private val anfrageRepository: AnfrageRepository,
    private val anfrageNotizRepository: AnfrageNotizRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val kundennummerService: KundennummerService,
    private val dateiSpeicherService: DateiSpeicherService,
    private val spamFilterService: AnfrageFunnelSpamFilterService,
    private val anfrageBestaetigungVersandService: AnfrageBestaetigungVersandService,
    private val webPushService: WebPushService,
    private val taskExecutor: TaskExecutor,
) {
    @Transactional
    fun verarbeiteFunnelAnfrage(
        dto: AnfrageFunnelRequestDto,
        bilder: List<MultipartFile>?,
    ): Anfrage {
        val systemMitarbeiter = mitarbeiterRepository.findByLoginToken(SYSTEM_MITARBEITER_TOKEN)
            .orElseThrow {
                IllegalStateException("System-Mitarbeiter 'Webseite' nicht gefunden. Migration V221 ausgeführt?")
            }

        val email = normalizeEmail(dto.email)
        val kunde = findeOderErstelleKunde(dto, email)

        var anfrage = erstelleAnfrage(dto, kunde, email)
        anfrage = anfrageRepository.save(anfrage)

        val notiz = erstelleNotiz(dto, anfrage, systemMitarbeiter, bilder)
        anfrageNotizRepository.save(notiz)

        log.info(
            "Funnel-Anfrage angelegt: anfrageId={}, kundeId={}, bilder={}",
            anfrage.id,
            kunde.id,
            bilder?.size ?: 0,
        )

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            val anfrageId = anfrage.id ?: throw IllegalStateException("Anfrage wurde ohne ID gespeichert")
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        taskExecutor.execute { pruefSpamAsync(anfrageId, dto) }
                    }
                },
            )
        } else {
            log.warn("Spam-Check übersprungen — keine aktive TX-Synchronisation für anfrageId={}", anfrage.id)
        }

        anfrageBestaetigungVersandService.versendeBestaetigung(
            anfrage,
            dto.vorname,
            dto.nachname,
            dto.nachricht,
        )

        try {
            var kundenName = "${dto.vorname?.trim() ?: ""} ${dto.nachname?.trim() ?: ""}".trim()
            if (kundenName.isEmpty()) {
                kundenName = "Unbekannt"
            }
            val body =
                if (anfrage.bauvorhaben.isNullOrBlank()) {
                    "Neue Anfrage über die Webseite"
                } else {
                    anfrage.bauvorhaben.orEmpty()
                }
            webPushService.notifyWebseitenAnfrage(
                "Neue Anfrage: $kundenName",
                body,
                "/zeiterfassung/anfragen?id=${anfrage.id}",
            )
        } catch (e: Exception) {
            log.warn("Sperrbildschirm-Push fuer anfrageId={} fehlgeschlagen: {}", anfrage.id, e.message)
        }

        return anfrage
    }

    private fun findeOderErstelleKunde(dto: AnfrageFunnelRequestDto, email: String): Kunde {
        val bestand = kundeRepository.findByKundenEmailIgnoreCase(email)
        if (bestand.isNotEmpty()) {
            val existing = bestand[0]
            if (!StringUtils.hasText(existing.telefon) && StringUtils.hasText(dto.telefon)) {
                existing.telefon = dto.telefon?.trim()
            }
            return kundeRepository.save(existing)
        }

        val neu = Kunde()
        neu.kundennummer = kundennummerService.reserviereNaechsteKundennummer()
        neu.name = "${dto.vorname.orEmpty().trim()} ${dto.nachname.orEmpty().trim()}".trim()
        neu.ansprechspartner = "${dto.vorname.orEmpty().trim()} ${dto.nachname.orEmpty().trim()}"
        if (StringUtils.hasText(dto.telefon)) {
            neu.telefon = dto.telefon?.trim()
        }

        val rechnungsadresse = AdressTeile.parse(rechnungsAnschriftRoh(dto))
        neu.strasse = rechnungsadresse.strasse
        neu.plz = rechnungsadresse.plz
        neu.ort = rechnungsadresse.ort
        neu.kundenEmails = arrayListOf(email)
        return kundeRepository.save(neu)
    }

    private fun rechnungsAnschriftRoh(dto: AnfrageFunnelRequestDto): String? =
        if (dto.isRechnungsAnschriftGleichProjekt) dto.projektAnschrift else dto.rechnungsAnschrift

    private fun erstelleAnfrage(dto: AnfrageFunnelRequestDto, kunde: Kunde, email: String): Anfrage {
        val adresse = AdressTeile.parse(dto.projektAnschrift)
        return Anfrage().apply {
            this.kunde = kunde
            bauvorhaben = buildBauvorhaben(dto)
            kurzbeschreibung = buildKurzbeschreibung(dto)
            anlegedatum = LocalDate.now()
            projektStrasse = adresse.strasse
            projektPlz = adresse.plz
            projektOrt = adresse.ort
            kundenEmails = arrayListOf(email)
        }
    }

    private fun erstelleNotiz(
        dto: AnfrageFunnelRequestDto,
        anfrage: Anfrage,
        systemMitarbeiter: Mitarbeiter,
        bilder: List<MultipartFile>?,
    ): AnfrageNotiz {
        val notiz = AnfrageNotiz().apply {
            this.anfrage = anfrage
            mitarbeiter = systemMitarbeiter
            this.notiz = buildNotizText(dto)
            mobileSichtbar = true
            nurFuerErsteller = false
        }

        bilder?.forEach { bild ->
            if (bild.isEmpty) {
                return@forEach
            }
            val url = dateiSpeicherService.speichereBild(bild)
            val gespeicherterName = url.substring(url.lastIndexOf('/') + 1)
            val notizBild = AnfrageNotizBild().apply {
                this.notiz = notiz
                gespeicherterDateiname = gespeicherterName
                originalDateiname = bild.originalFilename
                dateityp = bild.contentType
            }
            notiz.bilder.add(notizBild)
        }

        return notiz
    }

    private fun buildBauvorhaben(dto: AnfrageFunnelRequestDto): String {
        val service = dto.serviceTyp?.trim().orEmpty()
        val projektarten = joinProjektarten(dto.projektarten)
        if (projektarten.isEmpty()) {
            return service
        }
        if (service.isEmpty()) {
            return projektarten
        }
        return "$service - $projektarten"
    }

    private fun buildKurzbeschreibung(dto: AnfrageFunnelRequestDto): String {
        val sb = StringBuilder()
        sb.append(buildBauvorhaben(dto))
        if (StringUtils.hasText(dto.nachricht)) {
            sb.append("\n\n").append(dto.nachricht.orEmpty().trim())
        }
        val text = sb.toString()
        return if (text.length > 1000) text.substring(0, 1000) else text
    }

    private fun buildNotizText(dto: AnfrageFunnelRequestDto): String {
        val jetzt = LocalDateTime.now()
        val sb = StringBuilder()
        sb.append("Anfrage über Webseite vom ").append(DATUM_DE.format(jetzt.toLocalDate())).append("\n\n")
        sb.append("Kontakt:\n")
        sb.append("- ").append(dto.vorname.orEmpty().trim()).append(" ").append(dto.nachname.orEmpty().trim()).append("\n")
        sb.append("- ").append(normalizeEmail(dto.email)).append("\n")
        if (StringUtils.hasText(dto.telefon)) {
            sb.append("- Tel.: ").append(dto.telefon.orEmpty().trim()).append("\n")
        }
        if (StringUtils.hasText(dto.projektAnschrift)) {
            sb.append("- Projekt-Anschrift: ").append(dto.projektAnschrift.orEmpty().trim()).append("\n")
        }
        if (dto.isRechnungsAnschriftGleichProjekt) {
            sb.append("- Rechnungs-Anschrift: identisch mit Projekt-Anschrift\n")
        } else if (StringUtils.hasText(dto.rechnungsAnschrift)) {
            sb.append("- Rechnungs-Anschrift: ").append(dto.rechnungsAnschrift.orEmpty().trim()).append("\n")
        }

        sb.append("\nService: ").append(dto.serviceTyp.orEmpty().trim()).append("\n")
        val projektarten = joinProjektarten(dto.projektarten)
        if (projektarten.isNotEmpty()) {
            sb.append("Projektarten: ").append(projektarten).append("\n")
        }

        sb.append("\nNachricht:\n").append(dto.nachricht.orEmpty().trim()).append("\n")
        sb.append("\nDatenschutz akzeptiert: Ja (am ").append(DATUM_ZEIT_DE.format(jetzt))
        if (StringUtils.hasText(dto.consentIp)) {
            sb.append(", IP: ").append(dto.consentIp.orEmpty().trim())
        }
        if (StringUtils.hasText(dto.datenschutzVersion)) {
            sb.append(", Version: ").append(dto.datenschutzVersion.orEmpty().trim())
        }
        sb.append(")")

        val text = sb.toString()
        return if (text.length > 4000) text.substring(0, 4000) else text
    }

    private fun joinProjektarten(projektarten: List<String>?): String {
        if (projektarten.isNullOrEmpty()) {
            return ""
        }
        return projektarten
            .filter { StringUtils.hasText(it) }
            .joinToString(", ") { it.trim() }
    }

    private fun normalizeEmail(email: String?): String =
        email?.trim()?.lowercase(Locale.GERMAN).orEmpty()

    private fun pruefSpamAsync(anfrageId: Long, dto: AnfrageFunnelRequestDto) {
        try {
            val spamCheck = spamFilterService.pruefe(dto)
            if (spamCheck.spam()) {
                log.warn("Spam-Nachprüfung: anfrageId={} als Spam erkannt – {}", anfrageId, spamCheck.grund())
            }
        } catch (e: Exception) {
            log.warn("Async-Spam-Check für anfrageId={} fehlgeschlagen: {}", anfrageId, e.message)
        }
    }

    private data class AdressTeile(
        val strasse: String?,
        val plz: String?,
        val ort: String?,
    ) {
        companion object {
            fun parse(anschrift: String?): AdressTeile {
                if (!StringUtils.hasText(anschrift)) {
                    return AdressTeile(null, null, null)
                }

                val teile = anschrift!!.split(",", limit = 2)
                val strasse = teile[0].trim()
                if (teile.size < 2) {
                    return AdressTeile(emptyToNull(strasse), null, null)
                }

                val rest = teile[1].trim()
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx < 0) {
                    return AdressTeile(emptyToNull(strasse), null, emptyToNull(rest))
                }

                val maybePlz = rest.substring(0, spaceIdx).trim()
                val maybeOrt = rest.substring(spaceIdx + 1).trim()
                if (maybePlz.matches("\\d{4,5}".toRegex())) {
                    return AdressTeile(emptyToNull(strasse), emptyToNull(maybePlz), emptyToNull(maybeOrt))
                }
                return AdressTeile(emptyToNull(strasse), null, emptyToNull(rest))
            }

            private fun emptyToNull(value: String): String? =
                value.takeIf { StringUtils.hasText(it) }
        }
    }

    companion object {
        const val SYSTEM_MITARBEITER_TOKEN: String = "__SYSTEM_FUNNEL__"
        private val DATUM_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val DATUM_ZEIT_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        private val log = LoggerFactory.getLogger(AnfrageFunnelService::class.java)
    }
}
