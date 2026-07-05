package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

@Service
class EmailAutoAssignmentService(
    private val emailRepository: EmailRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val projektRepository: ProjektRepository,
    private val anfrageRepository: AnfrageRepository,
    private val emailKiClassificationService: EmailKiClassificationService,
) {
    @Transactional
    fun tryAutoAssign(email: Email): Boolean {
        if (email.zuordnungTyp != EmailZuordnungTyp.KEINE) {
            return false
        }

        if (tryAssignToLieferant(email)) {
            return true
        }
        if (tryAssignToKundeEntity(email)) {
            return true
        }
        return false
    }

    @Transactional
    fun tryAssignToLieferant(email: Email): Boolean {
        val senderDomain = email.senderDomain
        if (senderDomain.isNullOrBlank()) {
            return false
        }

        val matches = lieferantenRepository.findByEmailDomain(senderDomain)
        if (matches.isNotEmpty()) {
            val lieferant = matches.first()
            email.assignToLieferant(lieferant)
            emailRepository.save(email)
            log.info(
                "[AutoAssign] Email {} -> Lieferant {} (Domain: {})",
                email.id,
                lieferant.lieferantenname,
                senderDomain,
            )
            return true
        }

        return false
    }

    @Transactional
    fun tryAssignToKundeEntity(email: Email): Boolean {
        val fromAddress = email.fromAddress
        if (fromAddress.isNullOrBlank()) {
            return false
        }

        val emailLower = fromAddress.lowercase(Locale.getDefault()).trim()
        val emailDate = email.sentAt ?: LocalDateTime.now()
        val rangeStart = emailDate.minusMonths(1)
        val rangeEnd = emailDate.plusMonths(1)

        val matchingProjekte = projektRepository.findByKundenEmail(emailLower)
            .filter { isInTimeRange(it.anlegedatum, rangeStart, rangeEnd) }
        val matchingAnfragen = anfrageRepository.findByKundenEmail(emailLower)
            .filter { isInTimeRange(it.anlegedatum, rangeStart, rangeEnd) }

        val totalMatches = matchingProjekte.size + matchingAnfragen.size

        if (totalMatches == 1) {
            return assignToSingleMatch(email, matchingProjekte, matchingAnfragen, "Zeitfenster-Match")
        }

        if (totalMatches > 1) {
            if (tryAssignByKeywords(email, matchingProjekte, matchingAnfragen)) {
                return true
            }
            if (tryAssignByKi(email, matchingProjekte, matchingAnfragen)) {
                return true
            }
        }

        if (totalMatches == 0) {
            val allProjekte = projektRepository.findByKundenEmail(emailLower)
            val allAnfragen = anfrageRepository.findByKundenEmail(emailLower)
            val globalMatches = allProjekte.size + allAnfragen.size

            if (globalMatches == 1) {
                return assignToSingleMatch(email, allProjekte, allAnfragen, "Globaler-Match")
            }
            if (globalMatches > 1) {
                if (tryAssignByKeywords(email, allProjekte, allAnfragen)) {
                    return true
                }
                return tryAssignByKi(email, allProjekte, allAnfragen)
            }
        }

        return false
    }

    private fun assignToSingleMatch(
        email: Email,
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
        reason: String,
    ): Boolean {
        if (projekte.isNotEmpty()) {
            val projekt = projekte.first()
            email.assignToProjekt(projekt)
            emailRepository.save(email)
            log.info("[AutoAssign] Email {} -> Projekt {} ({})", email.id, projekt.bauvorhaben, reason)
            return true
        }

        val anfrage = anfragen.first()
        email.assignToAnfrage(anfrage)
        emailRepository.save(email)
        log.info("[AutoAssign] Email {} -> Anfrage {} ({})", email.id, anfrage.bauvorhaben, reason)
        return true
    }

    fun tryAssignByKeywords(
        email: Email,
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
    ): Boolean {
        val subject = email.subject
        val body = email.body
        val searchText = "${subject ?: ""} ${body ?: ""}".lowercase(Locale.getDefault())

        for (projekt in projekte) {
            if (matchesKeywords(searchText, projekt.bauvorhaben, projekt.kurzbeschreibung)) {
                email.assignToProjekt(projekt)
                emailRepository.save(email)
                log.info("[AutoAssign] Email {} -> Projekt {} (Schlagwort-Match)", email.id, projekt.bauvorhaben)
                return true
            }
        }

        for (anfrage in anfragen) {
            if (matchesKeywords(searchText, anfrage.bauvorhaben, anfrage.kurzbeschreibung)) {
                email.assignToAnfrage(anfrage)
                emailRepository.save(email)
                log.info("[AutoAssign] Email {} -> Anfrage {} (Schlagwort-Match)", email.id, anfrage.bauvorhaben)
                return true
            }
        }

        return false
    }

    private fun matchesKeywords(
        searchText: String,
        bauvorhaben: String?,
        kurzbeschreibung: String?,
    ): Boolean {
        val keywords = ArrayList<String>()
        if (!bauvorhaben.isNullOrBlank()) {
            bauvorhaben.split("\\s+".toRegex())
                .filter { it.length >= 4 }
                .mapTo(keywords) { it.lowercase(Locale.getDefault()) }
        }
        if (!kurzbeschreibung.isNullOrBlank()) {
            kurzbeschreibung.split("\\s+".toRegex())
                .filter { it.length >= 4 }
                .mapTo(keywords) { it.lowercase(Locale.getDefault()) }
        }

        val matchCount = keywords.count { searchText.contains(it) }
        return matchCount >= 2
    }

    @Transactional(readOnly = true)
    fun findPossibleAssignments(email: Email): PossibleAssignments {
        val fromAddress = email.fromAddress
        val result = PossibleAssignments()
        if (fromAddress.isNullOrBlank()) {
            return result
        }

        val emailLower = fromAddress.lowercase(Locale.getDefault()).trim()
        result.projekte = projektRepository.findByKundenEmail(emailLower)
            .map { EntityOption(it.id, it.bauvorhaben, "PROJEKT", it.auftragsnummer, null) }
        result.anfragen = anfrageRepository.findByKundenEmail(emailLower)
            .map { EntityOption(it.id, it.bauvorhaben, "ANFRAGE", null, "A-${it.id}") }
        return result
    }

    @Transactional
    fun tryAssignByKi(
        email: Email,
        projekte: List<Projekt>,
        anfragen: List<Anfrage>,
    ): Boolean {
        try {
            val result = emailKiClassificationService.classify(email, projekte, anfragen)
            if (!result.isAssigned()) {
                log.info("[AutoAssign] KI konnte Email {} nicht zuordnen: {}", email.id, result.reason())
                return false
            }

            if (result.zuordnungTyp() == EmailZuordnungTyp.PROJEKT) {
                val match = projekte.firstOrNull { it.id == result.entityId() }
                if (match != null) {
                    email.assignToProjekt(match)
                    emailRepository.save(email)
                    log.info(
                        "[AutoAssign] Email {} -> Projekt {} (KI-Zuordnung, confidence={}, reason={})",
                        email.id,
                        match.bauvorhaben,
                        result.confidence(),
                        result.reason(),
                    )
                    return true
                }
            } else if (result.zuordnungTyp() == EmailZuordnungTyp.ANFRAGE) {
                val match = anfragen.firstOrNull { it.id == result.entityId() }
                if (match != null) {
                    email.assignToAnfrage(match)
                    emailRepository.save(email)
                    log.info(
                        "[AutoAssign] Email {} -> Anfrage {} (KI-Zuordnung, confidence={}, reason={})",
                        email.id,
                        match.bauvorhaben,
                        result.confidence(),
                        result.reason(),
                    )
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            log.warn("[AutoAssign] KI-Zuordnung fehlgeschlagen für Email {}: {}", email.id, e.message)
            return false
        }
    }

    private fun isInTimeRange(date: LocalDate?, start: LocalDateTime, end: LocalDateTime): Boolean {
        if (date == null) {
            return true
        }
        val dateTime = date.atStartOfDay()
        return !dateTime.isBefore(start) && !dateTime.isAfter(end)
    }

    class PossibleAssignments {
        @JvmField
        var projekte: List<EntityOption> = ArrayList()

        @JvmField
        var anfragen: List<EntityOption> = ArrayList()
    }

    class EntityOption(
        @JvmField var id: Long?,
        @JvmField var name: String?,
        @JvmField var type: String?,
        @JvmField var projektNummer: String?,
        @JvmField var anfrageNummer: String?,
    )

    private companion object {
        private val log = LoggerFactory.getLogger(EmailAutoAssignmentService::class.java)
    }
}
