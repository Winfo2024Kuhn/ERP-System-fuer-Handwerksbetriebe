package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.config.DatenbankArt;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class WerkstoffRepositoryErweiterungImpl implements WerkstoffRepositoryErweiterung {

    /**
     * Pflichtfelder ausdruecklich mitgeben (wie ein neuer Werkstoff in Java):
     * Unter H2 (Release-.exe) legt Hibernate das Schema ohne Standardwerte an.
     */
    private static final String WERTE = "(name, verzinkungsgeeignet, pulverbeschichtungsgeeignet) VALUES (:name, FALSE, FALSE)";
    /** Dublette auf uk_werkstoff_name: nichts tun. */
    static final String ANLEGEN_MYSQL = "INSERT IGNORE INTO werkstoff " + WERTE;
    static final String ANLEGEN_POSTGRES = "INSERT INTO werkstoff " + WERTE + " ON CONFLICT (name) DO NOTHING";

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void legeAnFallsNeu(String name) {
        entityManager.createNativeQuery(DatenbankArt.istPostgres(entityManager) ? ANLEGEN_POSTGRES : ANLEGEN_MYSQL)
                .setParameter("name", name)
                .executeUpdate();
    }
}
