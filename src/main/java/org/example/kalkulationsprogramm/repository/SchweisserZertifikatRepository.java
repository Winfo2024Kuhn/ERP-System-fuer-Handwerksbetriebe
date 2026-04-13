package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SchweisserZertifikatRepository extends JpaRepository<SchweisserZertifikat, Long> {

    List<SchweisserZertifikat> findByMitarbeiterId(Long mitarbeiterId);

    @Query("SELECT z FROM SchweisserZertifikat z WHERE z.ablaufdatum IS NOT NULL AND z.ablaufdatum <= :bis")
    List<SchweisserZertifikat> findAblaufendBis(LocalDate bis);
}
