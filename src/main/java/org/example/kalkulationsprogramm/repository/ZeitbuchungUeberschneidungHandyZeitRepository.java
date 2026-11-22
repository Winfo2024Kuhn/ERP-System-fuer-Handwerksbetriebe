package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Festgehaltene Handy-Zeiten der Überschneidungs-Meldungen. */
@Repository
public interface ZeitbuchungUeberschneidungHandyZeitRepository
        extends JpaRepository<ZeitbuchungUeberschneidungHandyZeit, Long> {

    Optional<ZeitbuchungUeberschneidungHandyZeit> findByUeberschneidungIdAndHandyBuchungId(Long ueberschneidungId,
            Long handyBuchungId);

    /** Alle Meldungen, die diese Handy-Buchung festhalten (Ende nachtragen). */
    List<ZeitbuchungUeberschneidungHandyZeit> findByHandyBuchungId(Long handyBuchungId);

    /** Die Handy-Zeiten mehrerer Meldungen in einer Abfrage (Glocke, Handy-Nachrichten), nach Uhrzeit. */
    @Query("SELECT h FROM ZeitbuchungUeberschneidungHandyZeit h WHERE h.ueberschneidung.id IN :meldungIds "
            + "ORDER BY h.von ASC, h.id ASC")
    List<ZeitbuchungUeberschneidungHandyZeit> findZuMeldungen(@Param("meldungIds") Collection<Long> meldungIds);
}
