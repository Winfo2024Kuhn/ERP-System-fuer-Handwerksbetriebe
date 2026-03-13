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
    Optional<Lieferanten> findByLieferantenname(String lieferantenname);

    Optional<Lieferanten> findByLieferantennameIgnoreCase(String lieferantenname);

    @Query("select l from Lieferanten l join l.kundenEmails e where e = :email")
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

    @Query("select distinct l from Lieferanten l left join l.kundenEmails e where lower(l.lieferantenname) like lower(concat('%', :query, '%')) or lower(e) like lower(concat('%', :query, '%'))")
    List<Lieferanten> searchByNameOrEmail(@Param("query") String query);
}
