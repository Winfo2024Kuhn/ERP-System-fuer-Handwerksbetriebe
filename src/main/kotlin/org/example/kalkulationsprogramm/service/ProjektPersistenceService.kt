package org.example.kalkulationsprogramm.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.hibernate.exception.LockAcquisitionException
import org.springframework.dao.DataAccessException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class ProjektPersistenceService(
    private val projektRepository: ProjektRepository,
    private val transactionManager: PlatformTransactionManager,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    fun saveProjektWithRetry(projekt: Projekt): Projekt {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            val gespeichertesProjekt = projektRepository.saveAndFlush(projekt)
            refreshProjekt(gespeichertesProjekt)
            return gespeichertesProjekt
        }
        return saveInNewTransactionWithRetry(projekt)
    }

    private fun saveInNewTransactionWithRetry(projekt: Projekt): Projekt {
        var attempts = 0
        val backoffMs = longArrayOf(200, 500, 1000, 2000, 5000)
        while (true) {
            try {
                return executeInRequiresNew(projekt)
            } catch (e: PessimisticLockingFailureException) {
                if (attempts >= backoffMs.size) {
                    throw e
                }
            } catch (e: LockAcquisitionException) {
                if (attempts >= backoffMs.size) {
                    throw e
                }
            } catch (e: DataAccessException) {
                val msg = e.mostSpecificCause.message ?: e.message
                val lower = msg?.lowercase().orEmpty()
                if (!lower.contains("deadlock") && !lower.contains("lock wait timeout")) {
                    throw e
                }
                if (attempts >= backoffMs.size) {
                    throw e
                }
            }
            try {
                Thread.sleep(backoffMs[attempts])
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException(ie)
            }
            attempts++
        }
    }

    private fun executeInRequiresNew(projekt: Projekt): Projekt {
        val template = TransactionTemplate(transactionManager)
        template.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        return template.execute {
            val gespeichertesProjekt = projektRepository.saveAndFlush(projekt)
            refreshProjekt(gespeichertesProjekt)
            gespeichertesProjekt
        }!!
    }

    private fun refreshProjekt(gespeichertesProjekt: Projekt?) {
        if (gespeichertesProjekt == null) {
            return
        }
        try {
            entityManager.refresh(gespeichertesProjekt)
        } catch (_: IllegalArgumentException) {
            // Entity ist nicht im aktuellen Persistence Context.
        }
    }
}
