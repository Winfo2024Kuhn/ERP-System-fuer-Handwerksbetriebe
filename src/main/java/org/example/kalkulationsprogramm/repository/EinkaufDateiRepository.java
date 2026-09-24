package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufDatei;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufDateiRepository extends JpaRepository<EinkaufDatei, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from EinkaufDatei d where d.sha256 = :sha256")
    Optional<EinkaufDatei> sperreBySha256(@org.springframework.data.repository.query.Param("sha256") String sha256);
    Optional<EinkaufDatei> findBySha256(String sha256);
}
