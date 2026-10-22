package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /** Telefonverzeichnis für die Anrufzuordnung: je Zeile {@code [id, name, telefon]} der Kanzlei. */
    @Query("SELECT s.id, s.name, s.telefon FROM SteuerberaterKontakt s WHERE s.telefon IS NOT NULL AND s.telefon <> ''")
    List<Object[]> findeTelefonverzeichnis();

    /**
     * Nummern der Ansprechpartner für die Anrufzuordnung – sie zählen zur Kanzlei:
     * je Zeile {@code [kanzleiId, kanzleiName, telefon, vorname, nachname]}.
     */
    @Query("SELECT s.id, s.name, a.telefon, a.vorname, a.nachname FROM SteuerberaterAnsprechpartner a "
            + "JOIN a.steuerberater s WHERE a.telefon IS NOT NULL AND a.telefon <> ''")
    List<Object[]> findeAnsprechpartnerTelefone();

    /** Auswahl beim Zuordnen: alle Kanzleien samt Ansprechpartnern in einer Abfrage, aktive zuerst. */
    @Query("SELECT DISTINCT s FROM SteuerberaterKontakt s LEFT JOIN FETCH s.ansprechpartnerListe "
            + "ORDER BY s.aktiv DESC, s.name ASC")
    List<SteuerberaterKontakt> findAllMitAnsprechpartnernFuerAuswahl();

    /**
     * Aktive, nicht abgelaufene Kanzleien mit Telefonnummer für das FRITZ!Box-Telefonbuch:
     * je Zeile {@code [id, name, telefon]}.
     */
    @Query("SELECT s.id, s.name, s.telefon FROM SteuerberaterKontakt s "
            + "WHERE s.aktiv = true AND (s.gueltigBis IS NULL OR s.gueltigBis >= :heute) "
            + "AND s.telefon IS NOT NULL AND s.telefon <> ''")
    List<Object[]> findeFuerTelefonbuch(@Param("heute") LocalDate heute);

    /**
     * Ansprechpartner mit Telefonnummer aktiver, nicht abgelaufener Kanzleien für das
     * FRITZ!Box-Telefonbuch: je Zeile {@code [id, vorname, nachname, telefon, kanzleiName]}.
     */
    @Query("SELECT a.id, a.vorname, a.nachname, a.telefon, s.name FROM SteuerberaterAnsprechpartner a "
            + "JOIN a.steuerberater s WHERE s.aktiv = true AND (s.gueltigBis IS NULL OR s.gueltigBis >= :heute) "
            + "AND a.telefon IS NOT NULL AND a.telefon <> ''")
    List<Object[]> findeAnsprechpartnerFuerTelefonbuch(@Param("heute") LocalDate heute);
}
