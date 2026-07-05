package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.dto.Kunde.KundeAggregierteEmailDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnfrageKurzDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeEmailAttachmentDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeEmailQuelleDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeKommunikationDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeProjektKurzDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeStatistikDto
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.Optional

@Service
class KundenDetailService(
    private val kundeRepository: KundeRepository,
    private val projektRepository: ProjektRepository,
    private val anfrageRepository: AnfrageRepository,
    private val emailRepository: EmailRepository,
    private val dateiSpeicherService: DateiSpeicherService,
    private val ausgangsGeschaeftsDokumentService: AusgangsGeschaeftsDokumentService,
) {
    @Transactional(readOnly = true)
    fun loadDetails(kundenId: Long): Optional<KundeDetailDto> =
        kundeRepository.findById(kundenId).map { kunde ->
            val projekte = projektRepository.findByKundenId_Id(kunde.id)
            val anfragen = loadAnfragenForKunde(kunde, projekte)
            val projektEmails = if (projekte.isEmpty()) emptyList() else emailRepository.findByProjektInOrderBySentAtDesc(projekte)
            val anfrageEmails = if (anfragen.isEmpty()) emptyList() else emailRepository.findByAnfrageInOrderBySentAtDesc(anfragen)
            buildDetailDto(kunde, projekte, anfragen, projektEmails, anfrageEmails)
        }

    private fun buildDetailDto(
        kunde: org.example.kalkulationsprogramm.domain.Kunde,
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
        projektEmails: List<Email>,
        anfrageEmails: List<Email>,
    ): KundeDetailDto {
        val dto = KundeDetailDto()
        dto.id = kunde.id
        dto.kundennummer = kunde.kundennummer
        dto.name = kunde.name
        dto.anrede = kunde.anrede?.name
        dto.ansprechspartner = kunde.ansprechspartner
        dto.strasse = kunde.strasse
        dto.plz = kunde.plz
        dto.ort = kunde.ort
        dto.telefon = kunde.telefon
        dto.mobiltelefon = kunde.mobiltelefon
        dto.kundenEmails = ArrayList(kunde.kundenEmails ?: emptyList())
        dto.projekte = mapProjekte(projekte)
        dto.anfragen = mapAnfragen(anfragen)
        dto.aggregierteEmails = aggregateEmails(kunde, projekte, anfragen)
        try {
            dto.kommunikation = buildKommunikationsHistorie(projektEmails, anfrageEmails)
        } catch (ex: Exception) {
            LOG.warn("Kommunikationsverlauf fuer Kunden {} konnte nicht erzeugt werden: {}", kunde.id, ex.message, ex)
            dto.kommunikation = emptyList()
        }
        dto.statistik = buildStatistik(projekte, anfragen, dto.aggregierteEmails ?: emptyList())
        return dto
    }

    private fun loadAnfragenForKunde(
        kunde: org.example.kalkulationsprogramm.domain.Kunde,
        projekte: List<Projekt>,
    ): List<Anfrage> {
        val seenIds = LinkedHashSet<Long>()
        val result = ArrayList<Anfrage>()
        val projektIds = projekte.mapNotNull { it.id }
        if (projektIds.isNotEmpty()) {
            anfrageRepository.findByProjektIdIn(projektIds).forEach { anfrage ->
                val id = anfrage.id
                if (id != null && seenIds.add(id)) {
                    result.add(anfrage)
                }
            }
        }
        if (StringUtils.hasText(kunde.kundennummer)) {
            anfrageRepository.findByKunde_KundennummerIgnoreCase(kunde.kundennummer!!).forEach { anfrage ->
                val id = anfrage.id
                if (id != null && seenIds.add(id)) {
                    result.add(anfrage)
                }
            }
        }
        return result
    }

    private fun mapProjekte(projekte: List<Projekt>?): List<KundeProjektKurzDto> =
        projekte.orEmpty()
            .sortedWith { left, right ->
                compareNullableDatesDescending(left.abschlussdatum ?: left.anlegedatum, right.abschlussdatum ?: right.anlegedatum)
            }
            .map { projekt ->
                KundeProjektKurzDto().apply {
                    id = projekt.id
                    bauvorhaben = projekt.bauvorhaben
                    auftragsnummer = projekt.auftragsnummer
                    anlegedatum = projekt.anlegedatum
                    abschlussdatum = projekt.abschlussdatum
                    isBezahlt = projekt.isBezahlt()
                    bruttoPreis = projekt.bruttoPreis
                }
            }

    private fun mapAnfragen(anfragen: List<Anfrage>?): List<KundeAnfrageKurzDto> =
        anfragen.orEmpty()
            .sortedWith { left, right -> compareNullableDatesDescending(left.anlegedatum, right.anlegedatum) }
            .map { anfrage ->
                KundeAnfrageKurzDto().apply {
                    id = anfrage.id
                    bauvorhaben = anfrage.bauvorhaben
                    anfragesnummer = resolveAnfragesnummer(anfrage)
                    anlegedatum = anfrage.anlegedatum
                    betrag = anfrage.betrag
                }
            }

    private fun resolveAnfragesnummer(anfrage: Anfrage): String? {
        val nummerNeu = ausgangsGeschaeftsDokumentService.resolveAnfragesnummer(anfrage.id)
        if (nummerNeu != null) {
            return nummerNeu
        }
        return anfrage.dokumente
            ?.filterIsInstance<AnfrageGeschaeftsdokument>()
            ?.filter { doc ->
                val art = doc.geschaeftsdokumentart
                art != null && art.lowercase(Locale.GERMAN).contains("angebot")
            }
            ?.mapNotNull { it.dokumentid }
            ?.firstOrNull { StringUtils.hasText(it) }
    }

    private fun aggregateEmails(
        kunde: org.example.kalkulationsprogramm.domain.Kunde,
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
    ): List<KundeAggregierteEmailDto> {
        val map = LinkedHashMap<String, KundeAggregierteEmailDto>()
        addEmails(map, kunde.kundenEmails ?: emptyList(), QUELLE_STAMMDATEN, kunde.id, "Stammdaten", true)
        projekte.forEach { projekt ->
            addEmails(map, projekt.kundenEmails ?: emptyList(), QUELLE_PROJEKT, projekt.id, buildProjektBeschreibung(projekt), false)
        }
        anfragen.forEach { anfrage ->
            val emails = ArrayList<String>()
            anfrage.kunde?.kundenEmails?.let { emails.addAll(it) }
            anfrage.kundenEmails?.let { emails.addAll(it) }
            addEmails(map, emails, QUELLE_ANFRAGE, anfrage.id, buildAnfrageBeschreibung(anfrage), false)
        }
        return ArrayList(map.values)
    }

    private fun addEmails(
        map: MutableMap<String, KundeAggregierteEmailDto>,
        emails: List<String>,
        typ: String,
        referenzId: Long?,
        beschreibung: String,
        markAsMaster: Boolean,
    ) {
        for (email in emails) {
            if (!StringUtils.hasText(email)) continue
            val normalized = email.trim().lowercase(Locale.GERMAN)
            val aggregate = map.computeIfAbsent(normalized) {
                KundeAggregierteEmailDto().apply {
                    this.email = email.trim()
                    quellen = ArrayList()
                }
            }
            val quelle = KundeEmailQuelleDto().apply {
                this.typ = typ
                this.referenzId = referenzId
                this.beschreibung = beschreibung
            }
            aggregate.quellen.add(quelle)
            if (markAsMaster) {
                aggregate.isAusStammdaten = true
            }
        }
    }

    private fun buildProjektBeschreibung(projekt: Projekt?): String {
        if (projekt == null) return "Projekt"
        if (StringUtils.hasText(projekt.auftragsnummer)) return "Projekt ${projekt.auftragsnummer}"
        if (StringUtils.hasText(projekt.bauvorhaben)) return projekt.bauvorhaben!!
        return "Projekt #${projekt.id}"
    }

    private fun buildAnfrageBeschreibung(anfrage: Anfrage?): String {
        if (anfrage == null) return "Anfrage"
        val nummer = resolveAnfragesnummer(anfrage)
        if (StringUtils.hasText(nummer)) return "Anfrage $nummer"
        if (StringUtils.hasText(anfrage.bauvorhaben)) return anfrage.bauvorhaben!!
        return "Anfrage #${anfrage.id}"
    }

    private fun buildKommunikationsHistorie(projektEmails: List<Email>, anfrageEmails: List<Email>): List<KundeKommunikationDto> {
        val timeline = ArrayList<KundeKommunikationDto>()
        for (email in projektEmails) {
            val projekt = requireNotNull(email.projekt)
            timeline.add(createKommunikationDto(email, QUELLE_PROJEKT, projekt.id, buildProjektBeschreibung(projekt)))
        }
        for (email in anfrageEmails) {
            val anfrage = requireNotNull(email.anfrage)
            timeline.add(createKommunikationDto(email, QUELLE_ANFRAGE, anfrage.id, buildAnfrageBeschreibung(anfrage)))
        }
        timeline.sortByDescending { it.zeitpunkt ?: LocalDateTime.MIN }
        return if (timeline.size > MAX_KOMMUNIKATION_EINTRAEGE) ArrayList(timeline.subList(0, MAX_KOMMUNIKATION_EINTRAEGE)) else timeline
    }

    private fun createKommunikationDto(email: Email, referenzTyp: String, referenzId: Long?, referenzName: String): KundeKommunikationDto =
        KundeKommunikationDto().apply {
            id = email.id
            this.referenzId = referenzId
            this.referenzTyp = referenzTyp
            this.referenzName = referenzName
            subject = email.subject
            absender = email.fromAddress
            empfaenger = combineRecipients(email.recipient, email.cc)
            zeitpunkt = email.sentAt
            direction = email.direction
            snippet = buildSnippet(email.htmlBody, email.body)
            body = extractPlainText(email.htmlBody, email.body)
            attachments = mapAttachments(email.attachments, referenzTyp, referenzId, email.id)
            parentEmailId = email.parentEmail?.id
            replyCount = countAncestors(email) + countAllReplies(email)
        }

    private fun countAllReplies(email: Email): Int {
        val replies = email.replies
        if (replies == null || replies.isEmpty()) return 0
        var count = replies.size
        for (reply in replies) {
            count += countAllReplies(reply)
        }
        return count
    }

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

    private fun combineRecipients(recipient: String?, cc: String?): String? {
        val parts = ArrayList<String>()
        if (StringUtils.hasText(recipient)) parts.add(recipient!!.trim())
        if (StringUtils.hasText(cc)) parts.add("CC: ${cc!!.trim()}")
        return if (parts.isEmpty()) null else parts.joinToString(" | ")
    }

    private fun buildSnippet(html: String?, fallbackText: String?): String? {
        var text: String? = null
        if (StringUtils.hasText(html)) {
            text = Jsoup.parse(html!!).text()
        }
        if (!StringUtils.hasText(text) && StringUtils.hasText(fallbackText)) {
            text = fallbackText!!
        }
        if (!StringUtils.hasText(text)) {
            return null
        }
        val condensed = text!!.trim().replace("\\s+".toRegex(), " ")
        return if (condensed.length > 220) condensed.substring(0, 217).trim() + "..." else condensed
    }

    private fun mapAttachments(
        attachments: List<EmailAttachment>?,
        referenzTyp: String,
        referenzId: Long?,
        emailId: Long?,
    ): List<KundeEmailAttachmentDto> =
        attachments.orEmpty()
            .filter { it.inlineAttachment != true }
            .map { attachment ->
                KundeEmailAttachmentDto().apply {
                    id = attachment.id
                    filename = attachment.originalFilename
                    url = "/api/emails/$emailId/attachments/${attachment.id}"
                }
            }

    private fun extractPlainText(html: String?, fallbackText: String?): String? {
        var text: String? = null
        if (StringUtils.hasText(html)) {
            text = Jsoup.parse(html!!).text()
        }
        if (!StringUtils.hasText(text) && StringUtils.hasText(fallbackText)) {
            text = fallbackText!!
        }
        return text
    }

    private fun buildStatistik(
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
        aggregierteEmails: List<KundeAggregierteEmailDto>,
    ): KundeStatistikDto =
        KundeStatistikDto().apply {
            projektAnzahl = projekte.size.toLong()
            anfrageAnzahl = anfragen.size.toLong()
            emailAdresseAnzahl = aggregierteEmails.size.toLong()
            letzteAktivitaet = resolveLetzteAktivitaet(projekte, anfragen)
            gesamtUmsatz = berechneGesamtUmsatz(projekte)
            gesamtGewinn = berechneGesamtGewinn(projekte)
        }

    private fun berechneGesamtGewinn(projekte: List<Projekt>): BigDecimal {
        val umsatz = berechneGesamtUmsatz(projekte)
        val kosten = projekte.sumOf { dateiSpeicherService.berechneProjektKosten(it) }
        return umsatz.subtract(BigDecimal.valueOf(kosten))
    }

    private fun berechneGesamtUmsatz(projekte: List<Projekt>): BigDecimal =
        projekte.mapNotNull { it.bruttoPreis }.fold(BigDecimal.ZERO, BigDecimal::add)

    private fun resolveLetzteAktivitaet(projekte: List<Projekt>, anfragen: List<Anfrage>): LocalDate? {
        val letzteProjektAktivitaet = projekte
            .flatMap { listOfNotNull(it.abschlussdatum, it.anlegedatum) }
            .maxOrNull()
        val letztesAnfrage = anfragen.mapNotNull { it.anlegedatum }.maxOrNull()
        return listOfNotNull(letzteProjektAktivitaet, letztesAnfrage).maxOrNull()
    }

    private fun compareNullableDatesDescending(left: LocalDate?, right: LocalDate?): Int =
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> right.compareTo(left)
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(KundenDetailService::class.java)
        private const val QUELLE_STAMMDATEN = "STAMMDATEN"
        private const val QUELLE_PROJEKT = "PROJEKT"
        private const val QUELLE_ANFRAGE = "ANFRAGE"
        private const val MAX_KOMMUNIKATION_EINTRAEGE = 250
    }
}
