package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EmailSignatureRepository extends JpaRepository<EmailSignature, Long> {
    List<EmailSignature> findAllByOrderByUpdatedAtDesc();

    /** Alle Signaturen mit Besitzer und Bildern in einer Abfrage (Liste des Admins). */
    @EntityGraph(attributePaths = {"besitzer", "images"})
    @Query("SELECT s FROM EmailSignature s ORDER BY s.updatedAt DESC")
    List<EmailSignature> findAllMitBesitzer();

    /** Signaturen eines Benutzers, die zuletzt geänderte zuerst – mit Besitzer und Bildern. */
    @EntityGraph(attributePaths = {"besitzer", "images"})
    List<EmailSignature> findAllByBesitzerIdOrderByUpdatedAtDesc(Long besitzerId);

    Optional<EmailSignature> findFirstByIsSystemDefaultTrue();

    /** Firmen-Vorlagen, alphabetisch – Ausgangspunkt für die Signaturen der Benutzer. */
    @EntityGraph(attributePaths = {"besitzer", "images"})
    List<EmailSignature> findAllByFirmenVorlageTrueOrderByNameAsc();

    boolean existsByFirmenVorlageTrue();

    @Modifying
    @Query("UPDATE EmailSignature s SET s.isSystemDefault = false WHERE s.isSystemDefault = true AND s.id <> :keepId")
    int clearSystemDefaultExcept(@Param("keepId") Long keepId);
}

