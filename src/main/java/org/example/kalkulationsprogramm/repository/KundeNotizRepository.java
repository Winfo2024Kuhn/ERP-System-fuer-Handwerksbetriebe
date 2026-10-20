package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KundeNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KundeNotizRepository extends JpaRepository<KundeNotiz, Long> {

    List<KundeNotiz> findByKundeIdOrderByErstelltAmDesc(Long kundeId);

    List<KundeNotiz> findByKundeIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(
            Long kundeId, String query);

    @Modifying
    @Query("DELETE FROM KundeNotiz n WHERE n.kunde.id = :kundeId")
    int loescheAlleVonKunde(@Param("kundeId") Long kundeId);
}
