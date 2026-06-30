package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Objects

@Service
class AuditChainVerifier(
    private val auditRepository: AusgangsGeschaeftsDokumentAuditRepository,
) {
    @Transactional(readOnly = true)
    fun verify(): Bericht {
        val alle = auditRepository.findAllByOrderByChainIndexAsc()
        val bericht = Bericht()
        bericht.gesamtAnzahl = alle.size

        if (alle.isEmpty()) {
            bericht.intakt = true
            return bericht
        }

        var erwarteterPreviousHash: String? = null
        var erwarteterIndex = 0L

        for (audit in alle) {
            val chainIndex = longValue(audit, "getChainIndex")
            if (chainIndex == null || chainIndex != erwarteterIndex) {
                bericht.fehler.add(
                    Fehler(
                        longValue(audit, "getId"),
                        chainIndex,
                        stringValue(audit, "getDokumentNummer"),
                        "INDEX_LUECKE",
                        "Erwartet chain_index=$erwarteterIndex, gefunden $chainIndex",
                    ),
                )
                bericht.intakt = false
                return bericht
            }

            if (!Objects.equals(stringValue(audit, "getPreviousHash"), erwarteterPreviousHash)) {
                bericht.fehler.add(
                    Fehler(
                        longValue(audit, "getId"),
                        chainIndex,
                        stringValue(audit, "getDokumentNummer"),
                        "KETTE_GEBROCHEN",
                        "previous_hash passt nicht zum entry_hash des Vorgängers",
                    ),
                )
                bericht.intakt = false
                return bericht
            }

            val berechnet = audit.computeEntryHash()
            val entryHash = stringValue(audit, "getEntryHash")
            if (!Objects.equals(berechnet, entryHash)) {
                bericht.fehler.add(
                    Fehler(
                        longValue(audit, "getId"),
                        chainIndex,
                        stringValue(audit, "getDokumentNummer"),
                        "EINTRAG_MANIPULIERT",
                        "Inhalt des Eintrags wurde nachträglich geändert (Hash stimmt nicht)",
                    ),
                )
                bericht.intakt = false
                return bericht
            }

            erwarteterPreviousHash = entryHash
            erwarteterIndex++
        }

        bericht.intakt = true
        bericht.letzterEntryHash = erwarteterPreviousHash
        bericht.letzterChainIndex = erwarteterIndex - 1
        return bericht
    }

    class Bericht {
        var intakt: Boolean = false
        var gesamtAnzahl: Int = 0
        var letzterChainIndex: Long? = null
        var letzterEntryHash: String? = null
        val fehler: MutableList<Fehler> = ArrayList()

        fun isIntakt(): Boolean = intakt
    }

    class Fehler(
        private val auditId: Long?,
        private val chainIndex: Long?,
        private val dokumentNummer: String?,
        private val typ: String?,
        private val beschreibung: String?,
    ) {
        fun auditId(): Long? = auditId
        fun chainIndex(): Long? = chainIndex
        fun dokumentNummer(): String? = dokumentNummer
        fun typ(): String? = typ
        fun beschreibung(): String? = beschreibung
    }

    private fun longValue(target: Any, getter: String): Long? =
        invokeGetter(target, getter) as? Long

    private fun stringValue(target: Any, getter: String): String? =
        invokeGetter(target, getter) as? String

    private fun invokeGetter(target: Any, getter: String): Any? =
        target.javaClass.getMethod(getter).invoke(target)
}
