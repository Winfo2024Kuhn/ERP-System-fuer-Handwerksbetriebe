package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class SeenSenderDomainRepositoryErweiterungImpl implements SeenSenderDomainRepositoryErweiterung {

    private static final String UPSERT = "INSERT INTO seen_sender_domain (domain, first_seen, email_count) "
            + "VALUES (:domain, :firstSeen, 1) "
            + "ON CONFLICT (domain) DO UPDATE SET email_count = seen_sender_domain.email_count + 1";

    private final EntityManager entityManager;

    /**
     * Laeuft in eigener Tx (REQUIRES_NEW): Ein DB-Fehler hier darf NICHT die
     * umgebende Mail-Import-Tx (postProcessEmail) auf rollback-only setzen,
     * sonst scheitert das anschliessende emailRepository.save(email). Das
     * try/catch im Aufrufer faengt die Exception zwar ab, kann aber den
     * rollback-only-Status nicht wieder zuruecknehmen - nur eine separate
     * Tx-Grenze tut das.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSeen(String domain, LocalDateTime firstSeen) {
        entityManager.createNativeQuery(UPSERT)
                .setParameter("domain", domain)
                .setParameter("firstSeen", firstSeen)
                .executeUpdate();
    }
}
