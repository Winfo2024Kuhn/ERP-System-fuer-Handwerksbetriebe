package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface EinkaufVersandauftragRepository extends JpaRepository<EinkaufVersandauftrag, Long> {
    Optional<EinkaufVersandauftrag> findByIdempotenzKey(UUID idempotenzKey);
    // A locking current read also sees the winning transaction under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EinkaufVersandauftrag> findFirstByTypAndVorgangIdAndRevisionIdAndBeteiligungIdOrderByIdAsc(
            String typ, Long vorgangId, Long revisionId, Long beteiligungId);
    List<EinkaufVersandauftrag> findAllByStatus(EinkaufVersandauftrag.Status status);
    @Query("select a.id from EinkaufVersandauftrag a where a.status = :status order by a.erstelltAm, a.id")
    List<Long> findeIdsByStatus(@Param("status") EinkaufVersandauftrag.Status status, Pageable pageable);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EinkaufVersandauftrag a set a.archivClaimAm = null, a.version = a.version + 1 "
            + "where a.status = :angenommen "
            + "and a.archiviertAm is null and a.archivClaimAm is not null")
    int loeseUnterbrocheneArchivClaims(@Param("angenommen") EinkaufVersandauftrag.Status angenommen);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EinkaufVersandauftrag a where a.id = :id")
    Optional<EinkaufVersandauftrag> sperreById(@Param("id") Long id);
}
