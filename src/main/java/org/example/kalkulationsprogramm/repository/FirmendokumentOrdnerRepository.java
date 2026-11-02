package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FirmendokumentOrdnerRepository extends JpaRepository<FirmendokumentOrdner, Long> {

    /** Alle Ordner nach Name; die Obergrenze kommt über {@code pageable}. */
    @Query("SELECT o FROM FirmendokumentOrdner o ORDER BY lower(o.name), o.id")
    List<FirmendokumentOrdner> findeSortiert(Pageable pageable);

    List<FirmendokumentOrdner> findByParentIsNullAndNameIgnoreCase(String name);

    List<FirmendokumentOrdner> findByParentIdAndNameIgnoreCase(Long parentId, String name);

    boolean existsByParentId(Long parentId);
}
