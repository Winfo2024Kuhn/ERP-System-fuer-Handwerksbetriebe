package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SteuerberaterKontaktRepository extends JpaRepository<SteuerberaterKontakt, Long> {

    List<SteuerberaterKontakt> findByAktivTrue();

    Optional<SteuerberaterKontakt> findByEmailIgnoreCase(String email);

    /**
     * Findet alle Steuerberater mit aktivierter E-Mail-Verarbeitung.
     */
    List<SteuerberaterKontakt> findByAktivTrueAndAutoProcessEmailsTrue();

    /**
     * Prüft ob eine E-Mail-Adresse zu einem Steuerberater gehört.
     */
    boolean existsByEmailIgnoreCaseAndAktivTrue(String email);
}
