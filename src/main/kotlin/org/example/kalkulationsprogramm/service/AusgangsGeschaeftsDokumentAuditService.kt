package org.example.kalkulationsprogramm.service

import jakarta.persistence.EntityManager
import org.example.kalkulationsprogramm.domain.AuditChainState
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.LinkedHashMap

@Service
class AusgangsGeschaeftsDokumentAuditService(
    private val auditRepository: AusgangsGeschaeftsDokumentAuditRepository,
    private val chainStateRepository: AuditChainStateRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun protokolliereErstellung(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        ipAdresse: String?,
    ) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT, bearbeiter, "Initiale Erstellung", ipAdresse)
    }

    @Transactional
    fun protokolliereAenderung(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        aenderungsgrund: String?,
        ipAdresse: String?,
    ) {
        if (aenderungsgrund.isNullOrBlank()) {
            throw IllegalArgumentException("Änderungsgrund ist Pflicht (GoBD)")
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GEAENDERT, bearbeiter, aenderungsgrund, ipAdresse)
    }

    @Transactional
    fun protokolliereBuchung(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        ipAdresse: String?,
    ) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GEBUCHT, bearbeiter, "Festschreibung/Buchung", ipAdresse)
    }

    @Transactional
    fun protokolliereVersand(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        ipAdresse: String?,
    ) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.VERSENDET, bearbeiter, "Versand an Kunden", ipAdresse)
    }

    @Transactional
    fun protokolliereStornierung(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        grund: String?,
        ipAdresse: String?,
    ) {
        if (grund.isNullOrBlank()) {
            throw IllegalArgumentException("Stornierungsgrund ist Pflicht")
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.STORNIERT, bearbeiter, grund, ipAdresse)
    }

    @Transactional
    fun protokolliereLoeschung(
        dokument: AusgangsGeschaeftsDokument,
        bearbeiter: FrontendUserProfile?,
        begruendung: String?,
        ipAdresse: String?,
    ) {
        if (begruendung.isNullOrBlank()) {
            throw IllegalArgumentException("Begründung für Löschung ist Pflicht (GoBD)")
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GELOESCHT, bearbeiter, begruendung, ipAdresse)
    }

    @Transactional
    fun protokolliereDigitaleAnnahme(dokument: AusgangsGeschaeftsDokument, ipAdresse: String?) {
        save(
            dokument,
            AusgangsGeschaeftsDokumentAuditAktion.DIGITAL_ANGENOMMEN,
            null,
            "Digitale Annahme durch Kunden",
            ipAdresse,
        )
    }

    @Transactional(readOnly = true)
    fun getHistorie(dokumentId: Long?): List<Map<String, Any?>> =
        auditRepository.findByDokumentIdOrderByGeaendertAmDesc(dokumentId).map(::toMap)

    @Transactional(readOnly = true)
    fun getHistorieByNummer(dokumentNummer: String): List<Map<String, Any?>> =
        auditRepository.findByDokumentNummerOrderByGeaendertAmDesc(dokumentNummer).map(::toMap)

    private fun save(
        dokument: AusgangsGeschaeftsDokument,
        aktion: AusgangsGeschaeftsDokumentAuditAktion,
        bearbeiter: FrontendUserProfile?,
        grund: String?,
        ipAdresse: String?,
    ) {
        val audit = AusgangsGeschaeftsDokumentAudit.fromDokument(dokument, aktion, bearbeiter, grund, ipAdresse)
        appendToChain(audit)
        log.info(
            "Audit-Eintrag #{} (chain={}): {} für Dokument {} (Nr: {}) durch {} – {}",
            audit.id,
            audit.chainIndex,
            aktion,
            dokument.id,
            dokument.dokumentNummer,
            bearbeiter?.id ?: "system",
            grund,
        )
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun appendToChain(audit: AusgangsGeschaeftsDokumentAudit): AusgangsGeschaeftsDokumentAudit {
        var state = chainStateRepository.lockState()
        if (state == null) {
            state = AuditChainState()
            state.id = 1
            state.lastChainIndex = -1L
            state.lastEntryHash = null
            state.updatedAt = LocalDateTime.now()
            state = chainStateRepository.saveAndFlush(state)
        }

        val nextIndex = (state.lastChainIndex ?: -1L) + 1
        audit.chainIndex = nextIndex
        audit.previousHash = state.lastEntryHash

        val saved = auditRepository.saveAndFlush(audit)
        entityManager.refresh(saved)
        saved.entryHash = saved.computeEntryHash()
        auditRepository.saveAndFlush(saved)

        state.lastChainIndex = nextIndex
        state.lastEntryHash = saved.entryHash
        state.updatedAt = LocalDateTime.now()
        chainStateRepository.saveAndFlush(state)

        return saved
    }

    private fun toMap(audit: AusgangsGeschaeftsDokumentAudit): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = audit.id
        map["chainIndex"] = audit.chainIndex
        map["aktion"] = audit.aktion?.name
        map["dokumentId"] = audit.dokumentId
        map["dokumentNummer"] = audit.dokumentNummer
        map["typ"] = audit.typ?.name
        map["betragNetto"] = audit.betragNetto
        map["betragBrutto"] = audit.betragBrutto
        map["gebucht"] = audit.isGebucht()
        map["storniert"] = audit.isStorniert()
        map["digitalAngenommen"] = audit.isDigitalAngenommen()
        map["inhaltHash"] = audit.inhaltHash
        map["previousHash"] = audit.previousHash
        map["entryHash"] = audit.entryHash
        map["geaendertVon"] = audit.geaendertVon?.displayName
        map["geaendertVonId"] = audit.geaendertVon?.id
        map["geaendertAm"] = audit.geaendertAm?.toString()
        map["aenderungsgrund"] = audit.aenderungsgrund
        map["ipAdresse"] = audit.ipAdresse
        return map
    }

    companion object {
        private val log = LoggerFactory.getLogger(AusgangsGeschaeftsDokumentAuditService::class.java)
    }
}
