package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DatensatzLock;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DatensatzLockRepository extends JpaRepository<DatensatzLock, Long>, DatensatzLockRepositoryErweiterung {

    Optional<DatensatzLock> findByEntitaetTypAndEntitaetId(SperrbarerTyp entitaetTyp, Long entitaetId);

    /**
     * Liest das Lock mit Zeilensperre - und damit den neuesten Stand, auch wenn
     * ein anderer User es gerade erst angelegt hat (MySQL liest ohne Sperre den
     * Stand vom Beginn der Transaktion).
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DatensatzLock l WHERE l.entitaetTyp = :typ AND l.entitaetId = :entitaetId")
    Optional<DatensatzLock> findGesperrt(@Param("typ") SperrbarerTyp entitaetTyp, @Param("entitaetId") Long entitaetId);

    void deleteByEntitaetTypAndEntitaetId(SperrbarerTyp entitaetTyp, Long entitaetId);
}
