package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.config.DatenbankArt;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DatensatzLockRepositoryErweiterungImpl implements DatensatzLockRepositoryErweiterung {

    private static final String SPALTEN = "(entitaet_typ, entitaet_id, user_id, user_display_name, acquired_at, last_heartbeat_at) "
            + "VALUES (:typ, :entitaetId, :userId, :name, :zeitpunkt, :zeitpunkt)";
    /** Dublette auf uk_datensatz_lock_target: nichts tun, 0 Zeilen. */
    static final String ANLEGEN_MYSQL = "INSERT IGNORE INTO datensatz_lock " + SPALTEN;
    static final String ANLEGEN_POSTGRES = "INSERT INTO datensatz_lock " + SPALTEN
            + " ON CONFLICT (entitaet_typ, entitaet_id) DO NOTHING";

    private final EntityManager entityManager;

    @Override
    @Transactional
    public boolean legeAnFallsFrei(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId, String userDisplayName,
            LocalDateTime zeitpunkt) {
        return entityManager.createNativeQuery(DatenbankArt.istPostgres(entityManager) ? ANLEGEN_POSTGRES : ANLEGEN_MYSQL)
                .setParameter("typ", entitaetTyp.name())
                .setParameter("entitaetId", entitaetId)
                .setParameter("userId", userId)
                .setParameter("name", userDisplayName)
                .setParameter("zeitpunkt", zeitpunkt)
                .executeUpdate() > 0;
    }
}
