package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ProjektPersistenceService {

    private final ProjektRepository projektRepository;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    public Projekt saveProjektWithRetry(Projekt projekt) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Projekt gespeichertesProjekt = projektRepository.saveAndFlush(projekt);
            refreshProjekt(gespeichertesProjekt);
            return gespeichertesProjekt;
        }
        return saveInNewTransactionWithRetry(projekt);
    }

    private Projekt saveInNewTransactionWithRetry(Projekt projekt) {
        int attempts = 0;
        long[] backoffMs = new long[]{200, 500, 1000, 2000, 5000};
        while (true) {
            try {
                return executeInRequiresNew(projekt);
            } catch (PessimisticLockingFailureException | LockAcquisitionException e) {
                if (attempts >= backoffMs.length) {
                    throw e;
                }
            } catch (DataAccessException e) {
                String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
                String lower = msg != null ? msg.toLowerCase() : "";
                if (!(lower.contains("deadlock") || lower.contains("lock wait timeout"))) {
                    throw e;
                }
                if (attempts >= backoffMs.length) {
                    throw e;
                }
            }
            try {
                Thread.sleep(backoffMs[attempts]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
            attempts++;
        }
    }

    private Projekt executeInRequiresNew(Projekt projekt) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            Projekt gespeichertesProjekt = projektRepository.saveAndFlush(projekt);
            refreshProjekt(gespeichertesProjekt);
            return gespeichertesProjekt;
        });
    }

    private void refreshProjekt(Projekt gespeichertesProjekt) {
        if (entityManager == null || gespeichertesProjekt == null) {
            return;
        }
        try {
            entityManager.refresh(gespeichertesProjekt);
        } catch (IllegalArgumentException ignored) {
            // Entity ist nicht im aktuellen Persistence Context – Ignorieren.
        }
    }
}
