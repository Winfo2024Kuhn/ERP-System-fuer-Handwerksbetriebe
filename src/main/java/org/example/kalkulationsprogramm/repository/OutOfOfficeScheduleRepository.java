package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutOfOfficeScheduleRepository extends JpaRepository<OutOfOfficeSchedule, Long> {
    List<OutOfOfficeSchedule> findByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "signature")
    Optional<OutOfOfficeSchedule> findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
            LocalDate start, LocalDate end);
}
