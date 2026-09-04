package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DatensatzLock;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatensatzLockRepository extends JpaRepository<DatensatzLock, Long> {

    Optional<DatensatzLock> findByEntitaetTypAndEntitaetId(SperrbarerTyp entitaetTyp, Long entitaetId);

    void deleteByEntitaetTypAndEntitaetId(SperrbarerTyp entitaetTyp, Long entitaetId);
}
