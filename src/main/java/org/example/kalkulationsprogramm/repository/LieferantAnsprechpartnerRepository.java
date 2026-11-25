package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Ansprechpartner der Lieferanten. Sortiert wird überall gleich: Nachname, dann Vorname
 * (leer zuerst), ohne Groß-/Kleinschreibung, zuletzt nach ID.
 */
public interface LieferantAnsprechpartnerRepository extends JpaRepository<LieferantAnsprechpartner, Long> {

    /** Ansprechpartner eines Lieferanten; die Obergrenze kommt über {@code seite}. */
    @Query("SELECT a FROM LieferantAnsprechpartner a WHERE a.lieferant.id = :lieferantId "
            + "ORDER BY lower(a.nachname) ASC, lower(a.vorname) ASC NULLS FIRST, a.id ASC")
    List<LieferantAnsprechpartner> findeFuerLieferant(@Param("lieferantId") Long lieferantId, Pageable seite);

    /**
     * Ansprechpartner mehrerer Lieferanten auf einmal (eine Seite der Liste, Handy-App),
     * nach Lieferant gruppiert und je Lieferant wie {@link #findeFuerLieferant} sortiert.
     */
    @Query("SELECT a FROM LieferantAnsprechpartner a WHERE a.lieferant.id IN :lieferantIds "
            + "ORDER BY a.lieferant.id ASC, lower(a.nachname) ASC, lower(a.vorname) ASC NULLS FIRST, a.id ASC")
    List<LieferantAnsprechpartner> findeFuerLieferanten(@Param("lieferantIds") Collection<Long> lieferantIds);

    Optional<LieferantAnsprechpartner> findByIdAndLieferantId(Long id, Long lieferantId);

    long countByLieferantId(Long lieferantId);

    /**
     * Nummern der Ansprechpartner für die Anruferkennung – sie zählen zum Lieferanten:
     * je Zeile {@code [lieferantId, lieferantenname, ort, telefon, mobiltelefon, vorname, nachname]}.
     */
    @Query("SELECT l.id, l.lieferantenname, l.ort, a.telefon, a.mobiltelefon, a.vorname, a.nachname "
            + "FROM LieferantAnsprechpartner a JOIN a.lieferant l "
            + "WHERE (a.telefon IS NOT NULL AND a.telefon <> '') OR (a.mobiltelefon IS NOT NULL AND a.mobiltelefon <> '')")
    List<Object[]> findeTelefonverzeichnis();

    /**
     * Ansprechpartner aktiver Lieferanten (ohne Angabe gilt als aktiv) mit Nummer für das
     * FRITZ!Box-Telefonbuch: je Zeile {@code [id, vorname, nachname, telefon, mobiltelefon, lieferantenname]}.
     */
    @Query("SELECT a.id, a.vorname, a.nachname, a.telefon, a.mobiltelefon, l.lieferantenname "
            + "FROM LieferantAnsprechpartner a JOIN a.lieferant l "
            + "WHERE (l.istAktiv IS NULL OR l.istAktiv = true) "
            + "AND ((a.telefon IS NOT NULL AND a.telefon <> '') OR (a.mobiltelefon IS NOT NULL AND a.mobiltelefon <> ''))")
    List<Object[]> findeFuerTelefonbuch();
}
