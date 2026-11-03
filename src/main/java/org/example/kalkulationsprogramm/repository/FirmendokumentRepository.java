package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerZaehler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirmendokumentRepository extends JpaRepository<Firmendokument, Long> {

    @Query(value = "SELECT d FROM Firmendokument d WHERE d.ordner.id = :ordnerId ORDER BY lower(d.name), d.id",
            countQuery = "SELECT count(d) FROM Firmendokument d WHERE d.ordner.id = :ordnerId")
    Page<Firmendokument> findeImOrdner(@Param("ordnerId") Long ordnerId, Pageable pageable);

    /** {@code muster} kommt aus {@code LikeMuster.enthaelt(…)}. */
    @Query(value = "SELECT d FROM Firmendokument d WHERE lower(d.name) LIKE :muster ESCAPE '\\' "
            + "OR lower(d.originalDateiname) LIKE :muster ESCAPE '\\' ORDER BY lower(d.name), d.id",
            countQuery = "SELECT count(d) FROM Firmendokument d WHERE lower(d.name) LIKE :muster ESCAPE '\\' "
                    + "OR lower(d.originalDateiname) LIKE :muster ESCAPE '\\'")
    Page<Firmendokument> suche(@Param("muster") String muster, Pageable pageable);

    @Query(value = "SELECT d FROM Firmendokument d WHERE d.faelligAm IS NOT NULL AND d.faelligAm <= :grenze "
            + "ORDER BY d.faelligAm ASC NULLS FIRST, lower(d.name), d.id",
            countQuery = "SELECT count(d) FROM Firmendokument d WHERE d.faelligAm IS NOT NULL AND d.faelligAm <= :grenze")
    Page<Firmendokument> findeFaelligBis(@Param("grenze") LocalDate grenze, Pageable pageable);

    /** Zähler je Ordner (nur direkt enthaltene Dokumente) in einer Abfrage. */
    @Query("SELECT new org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerZaehler("
            + "d.ordner.id, count(d), "
            + "sum(CASE WHEN d.faelligAm < :heute THEN 1L ELSE 0L END), "
            + "sum(CASE WHEN d.faelligAm >= :heute AND d.faelligAm <= :grenze THEN 1L ELSE 0L END)) "
            + "FROM Firmendokument d GROUP BY d.ordner.id")
    List<FirmendokumentOrdnerZaehler> zaehleJeOrdner(@Param("heute") LocalDate heute,
            @Param("grenze") LocalDate grenze);

    /** Wie {@link #suche}, nur in den sichtbaren Ordnern ({@code ordnerIds} nie leer übergeben). */
    @Query(value = "SELECT d FROM Firmendokument d WHERE d.ordner.id IN :ordnerIds AND (lower(d.name) LIKE :muster "
            + "ESCAPE '\\' OR lower(d.originalDateiname) LIKE :muster ESCAPE '\\') ORDER BY lower(d.name), d.id",
            countQuery = "SELECT count(d) FROM Firmendokument d WHERE d.ordner.id IN :ordnerIds AND "
                    + "(lower(d.name) LIKE :muster ESCAPE '\\' OR lower(d.originalDateiname) LIKE :muster ESCAPE '\\')")
    Page<Firmendokument> sucheInOrdnern(@Param("muster") String muster, @Param("ordnerIds") Collection<Long> ordnerIds,
            Pageable pageable);

    /** Wie {@link #findeFaelligBis}, nur in den sichtbaren Ordnern ({@code ordnerIds} nie leer übergeben). */
    @Query(value = "SELECT d FROM Firmendokument d WHERE d.ordner.id IN :ordnerIds AND d.faelligAm IS NOT NULL "
            + "AND d.faelligAm <= :grenze ORDER BY d.faelligAm ASC NULLS FIRST, lower(d.name), d.id",
            countQuery = "SELECT count(d) FROM Firmendokument d WHERE d.ordner.id IN :ordnerIds "
                    + "AND d.faelligAm IS NOT NULL AND d.faelligAm <= :grenze")
    Page<Firmendokument> findeFaelligBisInOrdnern(@Param("grenze") LocalDate grenze,
            @Param("ordnerIds") Collection<Long> ordnerIds, Pageable pageable);

    boolean existsByOrdnerId(Long ordnerId);
}
