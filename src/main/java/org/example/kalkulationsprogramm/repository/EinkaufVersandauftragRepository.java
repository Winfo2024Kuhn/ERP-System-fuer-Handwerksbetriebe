package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface EinkaufVersandauftragRepository extends JpaRepository<EinkaufVersandauftrag, Long> {
    Optional<EinkaufVersandauftrag> findByIdempotenzKey(UUID idempotenzKey);
    List<EinkaufVersandauftrag> findAllByStatus(EinkaufVersandauftrag.Status status);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EinkaufVersandauftrag a set a.archivClaimAm = null, a.version = a.version + 1 "
            + "where a.status = :angenommen "
            + "and a.archiviertAm is null and a.archivClaimAm is not null")
    int loeseUnterbrocheneArchivClaims(@Param("angenommen") EinkaufVersandauftrag.Status angenommen);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EinkaufVersandauftrag a where a.id = :id")
    Optional<EinkaufVersandauftrag> sperreById(@Param("id") Long id);
}
