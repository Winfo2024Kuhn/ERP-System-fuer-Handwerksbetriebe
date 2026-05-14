package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface MitarbeiterRepository extends JpaRepository<Mitarbeiter, Long> {
    Optional<Mitarbeiter> findByLoginToken(String loginToken);

    // Für Zeiterfassung: nur aktive Mitarbeiter dürfen sich einloggen
    Optional<Mitarbeiter> findByLoginTokenAndAktivTrue(String loginToken);

    // Für Lohnabrechnung-Zuweisung: alle aktiven Mitarbeiter
    java.util.List<Mitarbeiter> findByAktivTrue();

    /**
     * Lädt den Mitarbeiter und sperrt die Zeile (SELECT ... FOR UPDATE).
     * Wird in Start/Stop/Pause-Operationen verwendet, um konkurrierende
     * Buchungs-Mutationen pro Mitarbeiter zu serialisieren und Race
     * Conditions zu verhindern (z. B. zwei Start-Requests, die beide
     * "keine aktive Buchung" sehen und beide eine neue Buchung anlegen).
     *
     * Muss innerhalb einer aktiven @Transactional aufgerufen werden,
     * sonst hat der Lock keine Wirkung.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mitarbeiter m WHERE m.loginToken = :token AND m.aktiv = true")
    Optional<Mitarbeiter> findByLoginTokenAndAktivTrueForUpdate(@Param("token") String token);
}
