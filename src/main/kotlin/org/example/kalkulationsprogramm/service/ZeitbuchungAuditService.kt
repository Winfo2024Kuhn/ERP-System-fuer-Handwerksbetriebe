package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AuditAktion
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.domain.ZeitbuchungAudit
import org.example.kalkulationsprogramm.repository.AenderungsgrundKatalogRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungAuditRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.LinkedHashMap

@Service
class ZeitbuchungAuditService(
    private val auditRepository: ZeitbuchungAuditRepository,
    private val aenderungsgrundRepository: AenderungsgrundKatalogRepository,
) {
    @Transactional
    fun protokolliereErstellung(buchung: Zeitbuchung, bearbeiter: Mitarbeiter, quelle: ErfassungsQuelle) {
        val audit = ZeitbuchungAudit.fromZeitbuchung(
            buchung,
            AuditAktion.ERSTELLT,
            bearbeiter,
            quelle,
            "Initiale Erfassung",
        )
        auditRepository.save(audit)
    }

    @Transactional
    fun protokolliereAenderung(
        buchung: Zeitbuchung,
        bearbeiter: Mitarbeiter,
        quelle: ErfassungsQuelle,
        aenderungsgrund: String?,
    ) {
        if (aenderungsgrund.isNullOrBlank()) {
            throw IllegalArgumentException("Änderungsgrund ist ein Pflichtfeld für GoBD-Konformität")
        }

        val audit = ZeitbuchungAudit.fromZeitbuchung(
            buchung,
            AuditAktion.GEAENDERT,
            bearbeiter,
            quelle,
            aenderungsgrund,
        )
        auditRepository.save(audit)
    }

    @Transactional
    fun protokolliereStorno(
        buchung: Zeitbuchung,
        bearbeiter: Mitarbeiter,
        quelle: ErfassungsQuelle,
        grund: String?,
    ) {
        if (grund.isNullOrBlank()) {
            throw IllegalArgumentException("Stornierungsgrund ist ein Pflichtfeld")
        }

        val audit = ZeitbuchungAudit.fromZeitbuchung(
            buchung,
            AuditAktion.STORNIERT,
            bearbeiter,
            quelle,
            grund,
        )
        auditRepository.save(audit)
    }

    @Transactional(readOnly = true)
    fun getHistorie(zeitbuchungId: Long?): List<Map<String, Any?>> =
        auditRepository.findByZeitbuchungIdOrderByVersionDesc(zeitbuchungId)
            .map { auditToMap(it) }

    @Transactional(readOnly = true)
    fun getAenderungsgruende(): List<Map<String, Any?>> =
        aenderungsgrundRepository.findAllByOrderByBezeichnungAsc()
            .map { g ->
                val map = LinkedHashMap<String, Any?>()
                map["code"] = g.value<String>("getCode")
                map["bezeichnung"] = g.value<String>("getBezeichnung")
                map["erfordertFreitext"] = g.value<Boolean>("getErfordertFreitext")
                map
            }

    @Transactional(readOnly = true)
    fun hatHistorie(zeitbuchungId: Long?): Boolean =
        auditRepository.existsByZeitbuchungId(zeitbuchungId)

    private fun auditToMap(audit: ZeitbuchungAudit): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = audit.id
        map["version"] = audit.version
        map["aktion"] = audit.aktion?.name
        map["startZeit"] = audit.startZeit?.toString()
        map["endeZeit"] = audit.endeZeit?.toString()
        map["anzahlInStunden"] = audit.anzahlInStunden
        map["notiz"] = audit.notiz
        map["geaendertVon"] = audit.geaendertVon?.let { "${it.vorname} ${it.nachname}" }
        map["geaendertAm"] = audit.geaendertAm?.toString()
        map["geaendertVia"] = audit.geaendertVia?.name
        map["aenderungsgrund"] = audit.aenderungsgrund
        return map
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T
}
