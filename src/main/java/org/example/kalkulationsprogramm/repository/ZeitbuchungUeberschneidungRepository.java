package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Meldungen zu Überschneidungen zwischen Handy-Buchungen und Büro-Einträgen. */
@Repository
public interface ZeitbuchungUeberschneidungRepository extends JpaRepository<ZeitbuchungUeberschneidung, Long> {

    /** Die Meldung zu einem Büro-Eintrag – über den Eintrag selbst oder seinen abgeteilten Rest. */
    @Query("SELECT u FROM ZeitbuchungUeberschneidung u WHERE u.bueroBuchungId = :buchungId "
            + "OR u.restBuchungId = :buchungId ORDER BY u.id DESC")
    List<ZeitbuchungUeberschneidung> findZuBuchung(@Param("buchungId") Long buchungId);

    /** Für die Glocke am PC: alle Meldungen ab einem Stichtag, neueste zuerst, mit Mitarbeiter. */
    @Query("SELECT u FROM ZeitbuchungUeberschneidung u JOIN FETCH u.mitarbeiter "
            + "WHERE u.geaendertAm >= :ab ORDER BY u.geaendertAm DESC")
    List<ZeitbuchungUeberschneidung> findGeaendertAb(@Param("ab") LocalDateTime ab);

    /** Für die Nachrichten am Handy: noch nicht gelesene Meldungen des Mitarbeiters. */
    List<ZeitbuchungUeberschneidung> findByMitarbeiterIdAndMitarbeiterGesehenAmIsNullOrderByGeaendertAmDesc(
            Long mitarbeiterId);

    Optional<ZeitbuchungUeberschneidung> findByIdAndMitarbeiterId(Long id, Long mitarbeiterId);

    /** Meldungen, die beim Anstechen dieser Handy-Buchung entstanden sind (Ende stand da noch nicht fest). */
    List<ZeitbuchungUeberschneidung> findByHandyBuchungId(Long handyBuchungId);
}
