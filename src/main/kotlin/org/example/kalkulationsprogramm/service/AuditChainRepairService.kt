package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuditChainRepairService(
    private val auditRepository: AusgangsGeschaeftsDokumentAuditRepository,
    private val chainStateRepository: AuditChainStateRepository,
    private val auditChainVerifier: AuditChainVerifier,
) {
    @Transactional
    fun rebuildChain(): Int {
        val state = chainStateRepository.lockState()
            ?: throw IllegalStateException("audit_chain_state fehlt — V255-Migration noch nicht gelaufen?")

        val ohneIndex = auditRepository.findByChainIndexIsNullOrderByGeaendertAmAscIdAsc().size.toLong()
        if (ohneIndex > 0) {
            throw IllegalStateException(
                "Audit-Ketten-Reparatur abgebrochen: $ohneIndex Einträge ohne " +
                    "chain_index. Der Backfill-Runner muss zuerst laufen.",
            )
        }

        val alle = auditRepository.findAllByOrderByChainIndexAsc()
        for (i in alle.indices) {
            val idx = alle[i].longValue("getChainIndex")
            if (idx == null || idx != i.toLong()) {
                throw IllegalStateException(
                    "Audit-Ketten-Reparatur abgebrochen: chain_index nicht lückenlos ab 0 " +
                        "(Position $i hat chain_index=$idx). Eine echte " +
                        "Index-Lücke deutet auf gelöschte Einträge hin und wird bewusst " +
                        "nicht überschrieben.",
                )
            }
        }

        log.warn(
            "Audit-Ketten-Reparatur startet: {} Einträge, alter Kettenkopf index={} hash={}",
            alle.size,
            state.value<Long>("getLastChainIndex"),
            state.value<String>("getLastEntryHash"),
        )
        for (a in alle) {
            log.warn(
                "  [pre-rebuild] index={} dok={} alt_entry_hash={} alt_previous_hash={}",
                a.longValue("getChainIndex"),
                a.stringValue("getDokumentNummer"),
                a.stringValue("getEntryHash"),
                a.stringValue("getPreviousHash"),
            )
        }

        var previousHash: String? = null
        var letzterIndex = -1L
        for (a in alle) {
            a.setValue("setPreviousHash", String::class.java, previousHash)
            a.setValue("setEntryHash", String::class.java, a.computeEntryHash())
            previousHash = a.stringValue("getEntryHash")
            letzterIndex = a.longValue("getChainIndex") ?: -1L
        }
        auditRepository.saveAllAndFlush(alle)

        state.setValue("setLastChainIndex", java.lang.Long::class.java, java.lang.Long.valueOf(letzterIndex))
        state.setValue("setLastEntryHash", String::class.java, previousHash)
        state.setValue("setUpdatedAt", LocalDateTime::class.java, LocalDateTime.now())
        chainStateRepository.saveAndFlush(state)

        val bericht = auditChainVerifier.verify()
        if (!bericht.isIntakt()) {
            throw IllegalStateException(
                "Audit-Ketten-Reparatur fehlgeschlagen — Kette nach Neuaufbau weiterhin " +
                    "gebrochen: ${bericht.fehler}",
            )
        }

        log.warn(
            "Audit-Ketten-Reparatur abgeschlossen: {} Einträge neu verkettet, neuer Kettenkopf index={} hash={}",
            alle.size,
            letzterIndex,
            previousHash,
        )
        return alle.size
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T

    private fun Any.longValue(getter: String): Long? =
        javaClass.getMethod(getter).invoke(this) as? Long

    private fun Any.stringValue(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as? String

    private fun Any.setValue(setter: String, parameterType: Class<*>, value: Any?) {
        javaClass.getMethod(setter, parameterType).invoke(this, value)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuditChainRepairService::class.java)
    }
}
