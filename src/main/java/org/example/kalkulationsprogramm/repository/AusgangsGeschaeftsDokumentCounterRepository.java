package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface AusgangsGeschaeftsDokumentCounterRepository extends JpaRepository<AusgangsGeschaeftsDokumentCounter, Long> {

    /**
     * Findet den Counter für einen Monat mit pessimistischem Lock
     * um Race Conditions bei der Nummernvergabe zu verhindern.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AusgangsGeschaeftsDokumentCounter c WHERE c.monatKey = :monatKey")
    Optional<AusgangsGeschaeftsDokumentCounter> findByMonatKeyForUpdate(String monatKey);

    Optional<AusgangsGeschaeftsDokumentCounter> findByMonatKey(String monatKey);
}
