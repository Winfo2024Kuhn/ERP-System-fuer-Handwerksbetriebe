package org.example.kalkulationsprogramm.repository;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class WerkstoffRepositoryErweiterungImpl implements WerkstoffRepositoryErweiterung {

    /**
     * Pflichtfelder ausdruecklich mitgeben (wie ein neuer Werkstoff in Java);
     * Dublette auf uk_werkstoff_name: nichts tun.
     */
    private static final String ANLEGEN = "INSERT INTO werkstoff (name, verzinkungsgeeignet, pulverbeschichtungsgeeignet) "
            + "VALUES (:name, FALSE, FALSE) ON CONFLICT (name) DO NOTHING";

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void legeAnFallsNeu(String name) {
        entityManager.createNativeQuery(ANLEGEN)
                .setParameter("name", name)
                .executeUpdate();
    }
}
