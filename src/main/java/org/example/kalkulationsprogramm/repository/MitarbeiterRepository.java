package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

@Repository
public interface MitarbeiterRepository extends JpaRepository<Mitarbeiter, Long> {
    Optional<Mitarbeiter> findByLoginToken(String loginToken);

    // Für Zeiterfassung: nur aktive Menschen dürfen sich einloggen, auch ohne Zeitkonto.
    @Query("SELECT m FROM Mitarbeiter m WHERE m.loginToken = :token AND m.aktiv = true AND m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH")
    Optional<Mitarbeiter> findByLoginTokenAndAktivTrue(@Param("token") String loginToken);

    /** Menschenstamm einschließlich ausgeschiedener Personen und Personen ohne Zeitkonto. */
    @Query("SELECT m FROM Mitarbeiter m WHERE m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH")
    List<Mitarbeiter> findMenschen();

    @Query("SELECT m FROM Mitarbeiter m WHERE m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH AND m.aktiv = true")
    List<Mitarbeiter> findAktiveMenschen();

    /** Heutige Kontoführung; nicht für historische Auswertungen verwenden. */
    @Query("SELECT m FROM Mitarbeiter m WHERE m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH AND m.aktiv = true AND m.fuehrtZeitkonto = true")
    List<Mitarbeiter> findAktiveMenschenMitZeitkonto();

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
    @Query("SELECT m FROM Mitarbeiter m WHERE m.loginToken = :token AND m.aktiv = true AND m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH")
    Optional<Mitarbeiter> findByLoginTokenAndAktivTrueForUpdate(@Param("token") String token);
}
