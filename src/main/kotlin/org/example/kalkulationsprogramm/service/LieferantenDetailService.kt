package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.LieferantNotiz
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAttachmentViewDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantEmailDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantKommunikationDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantNotizDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailFileDto
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Service
class LieferantenDetailService(
    private val lieferantenRepository: LieferantenRepository,
    private val emailRepository: EmailRepository,
    private val artikelpreisMapper: LieferantArtikelpreisMapper,
    private val lieferantDokumentService: LieferantDokumentService,
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val notizRepository: LieferantNotizRepository,
) {
    private val log = LoggerFactory.getLogger(LieferantenDetailService::class.java)

    @Transactional(readOnly = true)
    fun loadDetails(id: Long): LieferantDetailDto? {
        val lieferant = lieferantenRepository.findById(id).orElse(null) ?: return null
        val dto = LieferantDetailDto()
        dto.id = lieferant.id
        dto.lieferantenname = lieferant.lieferantenname
        dto.eigeneKundennummer = lieferant.eigeneKundennummer
        dto.lieferantenTyp = lieferant.lieferantenTyp
        dto.vertreter = lieferant.vertreter
        dto.strasse = lieferant.strasse
        dto.plz = lieferant.plz
        dto.ort = lieferant.ort
        dto.telefon = lieferant.telefon
        dto.mobiltelefon = lieferant.mobiltelefon
        dto.istAktiv = lieferant.istAktiv
        dto.startZusammenarbeit = toLocalDate(lieferant.startZusammenarbeit)
        dto.kundenEmails = ArrayList(lieferant.kundenEmails)
        lieferant.standardKostenstelle?.let {
            dto.standardKostenstelleId = it.id
            dto.standardKostenstelleName = it.bezeichnung
        }

        dto.artikelpreise = artikelpreisMapper.toDtoList(lieferant.artikelpreise)
        dto.statistik = buildStatistik(lieferant)

        val emails = loadEmailsEntities(id, EMAIL_LIMIT_DEFAULT)
        dto.kommunikation = emails.map { toKommunikation(it, id) }
        dto.emails = emails.map(::toProjektEmailDto)
        dto.dokumente = lieferantDokumentService.getDokumenteByLieferant(id, null)
        dto.notizen = notizRepository.findByLieferantIdOrderByErstelltAmDesc(id).map(::toNotizDto)

        return dto
    }

    private fun toNotizDto(notiz: LieferantNotiz): LieferantNotizDto =
        LieferantNotizDto(
            id = notiz.id,
            text = notiz.text,
            erstelltAm = notiz.erstelltAm,
        )

    private fun toKommunikation(email: Email, lieferantId: Long): LieferantKommunikationDto {
        val body = email.body ?: ""
        val textOnly = body.trim()
        val dto = LieferantKommunikationDto()
        dto.id = email.id
        dto.referenzId = lieferantId
        dto.referenzTyp = "LIEFERANT"
        dto.referenzName = "Lieferant"
        dto.subject = email.subject
        dto.absender = email.fromAddress
        dto.empfaenger = email.recipient
        dto.zeitpunkt = email.sentAt
        dto.direction = email.direction
        dto.snippet = if (textOnly.length > 100) textOnly.substring(0, 100) + "..." else textOnly
        dto.body = email.htmlBody ?: email.body
        dto.attachments = email.attachments.map { toAttachmentView(it, lieferantId, email.id) }
        return dto
    }

    private fun toAttachmentView(att: EmailAttachment, lieferantId: Long, emailId: Long?): LieferantAttachmentViewDto =
        LieferantAttachmentViewDto(
            id = att.id,
            filename = att.originalFilename,
            url = "/api/emails/$emailId/attachments/${att.id}",
        )

    @Transactional(readOnly = true)
    fun loadEmails(lieferantId: Long, limit: Int, query: String?): List<LieferantEmailDto> =
        loadEmailsEntities(lieferantId, limit).map(::toEmailDto)

    private fun loadEmailsEntities(lieferantId: Long, limit: Int): List<Email> =
        emailRepository.findByLieferantIdOrderBySentAtDesc(lieferantId)

    private fun toEmailDto(email: Email): LieferantEmailDto =
        LieferantEmailDto().also {
            it.id = email.id
            it.subject = email.subject
            it.from = email.fromAddress
            it.to = email.recipient
            it.sentAt = email.sentAt
            it.direction = email.direction
            it.bodyHtml = email.htmlBody
        }

    private fun toProjektEmailDto(email: Email): ProjektEmailDto {
        val dto = ProjektEmailDto()
        dto.id = email.id
        dto.direction = email.direction
        dto.from = email.fromAddress
        dto.to = email.recipient
        dto.subject = email.subject
        dto.sentAt = email.sentAt
        dto.bodyHtml = email.htmlBody ?: email.body
        dto.parentEmailId = email.parentEmail?.id
        dto.replyCount = countAncestors(email) + countAllReplies(email)

        val emailId = email.id
        dto.attachments = email.attachments.map { att ->
            ProjektEmailFileDto().also {
                it.id = att.id
                it.originalFilename = att.originalFilename
                it.storedFilename = att.storedFilename
                it.contentId = att.contentId
                it.isInline = att.isInline()
                it.url = "/api/emails/$emailId/attachments/${att.id}"
            }
        }
        return dto
    }

    private fun countAllReplies(email: Email): Int =
        email.replies.sumOf { 1 + countAllReplies(it) }

    private fun countAncestors(email: Email): Int {
        var count = 0
        val visited = HashSet<Long?>()
        var current = email.parentEmail
        while (current != null && !visited.contains(current.id)) {
            visited.add(current.id)
            count++
            current = current.parentEmail
        }
        return count
    }

    private fun buildStatistik(lieferant: Lieferanten): LieferantStatistikDto {
        val statistik = LieferantStatistikDto()
        statistik.artikelAnzahl = lieferant.artikelpreise
            .asSequence()
            .mapNotNull { it.artikel?.id }
            .distinct()
            .count()

        val lieferantId = lieferant.id ?: 0L
        val emailCount = emailRepository.countByLieferantId(lieferantId)
        statistik.emailAnzahl = emailCount
        emailRepository.findFirstByLieferantIdOrderBySentAtDesc(lieferantId)
            .ifPresent { statistik.letzteEmail = it.sentAt }

        statistik.emailDomains = lieferant.kundenEmails
            .mapNotNull(::extractDomain)
            .distinct()

        try {
            val bestellungen = geschaeftsdokumentRepository.countBestellungenByLieferantId(lieferantId)
            if (bestellungen != null) {
                statistik.bestellungAnzahl = bestellungen.toInt()
            }

            val avgLieferzeit = geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(lieferantId)
            if (avgLieferzeit != null && avgLieferzeit > 0) {
                statistik.lieferzeit = avgLieferzeit.toInt()
            }

            val gesamtKosten = geschaeftsdokumentRepository.sumGesamtkostenByLieferantId(lieferantId)
            if (gesamtKosten != null) {
                statistik.gesamtKosten = gesamtKosten
            }
        } catch (e: Exception) {
            log.warn("Fehler bei Statistik-Berechnung fuer Lieferant {}", lieferantId, e)
        }

        return statistik
    }

    private fun toLocalDate(date: java.util.Date?): LocalDate? {
        if (date == null) {
            return null
        }
        val instant: Instant = date.toInstant()
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun extractDomain(email: String?): String? {
        val normalized = email?.trim()?.lowercase(Locale.ROOT) ?: return null
        val at = normalized.indexOf('@')
        if (at < 0 || at == normalized.length - 1) {
            return null
        }
        return normalized.substring(at + 1)
    }

    companion object {
        private const val EMAIL_LIMIT_DEFAULT = 50
    }
}
