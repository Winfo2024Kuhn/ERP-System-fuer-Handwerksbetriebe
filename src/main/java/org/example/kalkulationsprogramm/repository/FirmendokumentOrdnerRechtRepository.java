package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Rechte der Abteilungen an den obersten Ordnern der Firmendokumente. */
public interface FirmendokumentOrdnerRechtRepository extends JpaRepository<FirmendokumentOrdnerRecht, Long> {

    /** Alle Einträge eines Ordners (höchstens einer je Abteilung). */
    @Query("SELECT r FROM FirmendokumentOrdnerRecht r WHERE r.ordner.id = :ordnerId ORDER BY r.id")
    List<FirmendokumentOrdnerRecht> findeFuerOrdner(@Param("ordnerId") Long ordnerId);

    /** Einträge eines Ordners für die Abteilungen des Angemeldeten; {@code abteilungIds} nie leer übergeben. */
    @Query("SELECT r FROM FirmendokumentOrdnerRecht r WHERE r.ordner.id = :ordnerId AND r.abteilung.id IN :abteilungIds")
    List<FirmendokumentOrdnerRecht> findeFuerOrdnerUndAbteilungen(@Param("ordnerId") Long ordnerId,
            @Param("abteilungIds") Collection<Long> abteilungIds);

    /**
     * Alle Einträge der Abteilungen des Angemeldeten (für Baum, Suche, Fällige). Begrenzt durch Ordner × Abteilungen
     * des Benutzers; {@code abteilungIds} nie leer übergeben.
     */
    @Query("SELECT r FROM FirmendokumentOrdnerRecht r WHERE r.abteilung.id IN :abteilungIds")
    List<FirmendokumentOrdnerRecht> findeFuerAbteilungen(@Param("abteilungIds") Collection<Long> abteilungIds);
}
