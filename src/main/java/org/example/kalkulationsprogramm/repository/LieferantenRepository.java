package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LieferantenRepository extends JpaRepository<Lieferanten, Long>, JpaSpecificationExecutor<Lieferanten> {

    List<Lieferanten> findAllByLieferantennameIgnoreCaseOrderByIdAsc(String lieferantenname);

    /**
     * Lieferant zu einem Namen, ohne Gross-/Kleinschreibung - "würth" findet "Würth".
     *
     * <p>MySQL vergleicht ueber die Kollation ohnehin so, und sein Unique-Index laesst
     * keine zweite Schreibweise zu. PostgreSQL unterscheidet beides; dort koennen
     * "Würth" und "WÜRTH" nebeneinander stehen. Dann gewinnt die exakte Schreibweise,
     * sonst der aelteste Eintrag - statt eines Fehlers wegen mehrerer Treffer.
     */
    default Optional<Lieferanten> findByLieferantennameIgnoreCase(String lieferantenname) {
        if (lieferantenname == null) {
            return Optional.empty();
        }
        List<Lieferanten> treffer = findAllByLieferantennameIgnoreCaseOrderByIdAsc(lieferantenname.trim());
        return treffer.stream()
                .filter(l -> lieferantenname.trim().equals(l.getLieferantenname()))
                .findFirst()
                .or(() -> treffer.stream().findFirst());
    }

    // lower(): ohne Gross-/Kleinschreibung (PostgreSQL vergleicht Text sonst genau)
    @Query("select l from Lieferanten l join l.kundenEmails e where lower(e) = lower(:email)")
    Optional<Lieferanten> findByEmail(@Param("email") String email);

    /**
     * Findet einen Lieferanten anhand der E-Mail-Domain.
     * Sucht alle Lieferanten deren hinterlegte E-Mail-Adressen mit der angegebenen
     * Domain enden.
     * z.B. domain = "hoffmann-werkzeuge.de" findet Lieferanten mit E-Mail
     * "bestellung@hoffmann-werkzeuge.de"
     */
    @Query("select distinct l from Lieferanten l join l.kundenEmails e where lower(e) like concat('%@', lower(:domain))")
    List<Lieferanten> findByEmailDomain(@Param("domain") String domain);

    /**
     * Prüft ob mindestens ein Lieferant mit der angegebenen Email-Domain existiert.
     */
    @Query("select case when count(l) > 0 then true else false end from Lieferanten l join l.kundenEmails e where lower(e) like concat('%@', lower(:domain))")
    boolean existsByEmailDomain(@Param("domain") String domain);

    @Query("select distinct l from Lieferanten l join l.artikelpreise ap join ap.artikel a")
    List<Lieferanten> findAllWithArtikel();

    @EntityGraph(attributePaths = "kundenEmails")
    @Query("select l from Lieferanten l")
    List<Lieferanten> findAllWithEmails();

    List<Lieferanten> findByIstAktivTrueOrderByLieferantennameAsc();

    /**
     * Freitextsuche ueber offiziellen Namen, zweiten Namen (Alias) und E-Mail-Adressen.
     */
    @Query("select distinct l from Lieferanten l left join l.kundenEmails e where lower(l.lieferantenname) like lower(concat('%', :query, '%')) or lower(coalesce(l.aliasName, '')) like lower(concat('%', :query, '%')) or lower(e) like lower(concat('%', :query, '%'))")
    List<Lieferanten> searchByNameOrEmail(@Param("query") String query);

    /**
     * Schlanke Zeilen für das Rufnummern-Verzeichnis der Telefon-Zuordnung:
     * [id, lieferantenname, ort, telefon, mobiltelefon].
     */
    @Query("SELECT l.id, l.lieferantenname, l.ort, l.telefon, l.mobiltelefon FROM Lieferanten l "
            + "WHERE (l.telefon IS NOT NULL AND l.telefon <> '') OR (l.mobiltelefon IS NOT NULL AND l.mobiltelefon <> '')")
    List<Object[]> findeTelefonverzeichnis();

    /**
     * Aktive Lieferanten (ohne Angabe gilt als aktiv) für das FRITZ!Box-Telefonbuch, nur mit Nummer.
     * Je Zeile {@code [id, lieferantenname, telefon, mobiltelefon]}.
     */
    @Query("SELECT l.id, l.lieferantenname, l.telefon, l.mobiltelefon FROM Lieferanten l "
            + "WHERE (l.istAktiv IS NULL OR l.istAktiv = true) "
            + "AND ((l.telefon IS NOT NULL AND l.telefon <> '') OR (l.mobiltelefon IS NOT NULL AND l.mobiltelefon <> ''))")
    List<Object[]> findeFuerTelefonbuch();
}
