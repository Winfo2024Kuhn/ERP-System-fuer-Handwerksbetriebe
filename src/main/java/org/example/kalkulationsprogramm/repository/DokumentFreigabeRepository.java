package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DokumentFreigabeRepository extends JpaRepository<DokumentFreigabe, Long>
{
    Optional<DokumentFreigabe> findByUuid(String uuid);

    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.quellTyp = :quellTyp AND f.quellDokumentId IN :ids
            """)
    List<DokumentFreigabe> findByQuelle(@Param("quellTyp") FreigabeQuellTyp quellTyp,
                                        @Param("ids") List<Long> ids);

    /**
     * Liefert alle Freigaben, die seit dem angegebenen Zeitpunkt akzeptiert wurden,
     * neueste zuerst. Wird vom NotificationController genutzt.
     */
    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.status = org.example.kalkulationsprogramm.domain.FreigabeStatus.ACCEPTED
              AND f.akzeptiertAm IS NOT NULL
              AND f.akzeptiertAm >= :seit
            ORDER BY f.akzeptiertAm DESC
            """)
    List<DokumentFreigabe> findKuerzlichAkzeptiert(@Param("seit") LocalDateTime seit);
}
