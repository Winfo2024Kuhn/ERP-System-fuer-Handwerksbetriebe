package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Telefonverzeichnis für die Anrufzuordnung: je Zeile {@code [id, name, telefon]} –
     * die Kanzleinummer und die Nummern aller Ansprechpartner, beide zählen zur Kanzlei.
     */
    @Query("SELECT s.id, s.name, s.telefon FROM SteuerberaterKontakt s WHERE s.telefon IS NOT NULL AND s.telefon <> '' "
            + "UNION ALL SELECT s.id, s.name, a.telefon FROM SteuerberaterAnsprechpartner a JOIN a.steuerberater s "
            + "WHERE a.telefon IS NOT NULL AND a.telefon <> ''")
    List<Object[]> findeTelefonverzeichnis();

    /** Auswahl beim Zuordnen: alle Kanzleien, aktive zuerst. */
    @Query("SELECT s FROM SteuerberaterKontakt s ORDER BY s.aktiv DESC, s.name ASC")
    List<SteuerberaterKontakt> findAllFuerAuswahl();
}
