package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OutOfOfficeScheduleRepository extends JpaRepository<OutOfOfficeSchedule, Long> {

    /** Alle Notizen, die neueste zuerst. */
    List<OutOfOfficeSchedule> findAllByOrderByStartAtDescIdDesc();

    /** Aktive Notizen für dieses Postfach, die {@code tag} einschließen – die zuletzt beginnende zuerst. */
    @Query("SELECT s FROM OutOfOfficeSchedule s JOIN s.postfaecher p "
            + "WHERE p.id = :postfachId AND s.active = true AND s.startAt <= :tag AND s.endAt >= :tag "
            + "ORDER BY s.startAt DESC, s.id DESC")
    List<OutOfOfficeSchedule> findAktiveFuerPostfach(@Param("postfachId") Long postfachId, @Param("tag") LocalDate tag);

    /**
     * Aktive Notizen ohne Postfach, die {@code tag} einschließen: alte Notizen aus Betrieben ganz
     * ohne Postfach, sie gelten für Mails des Standard-Kontos.
     */
    @Query("SELECT s FROM OutOfOfficeSchedule s "
            + "WHERE s.postfaecher IS EMPTY AND s.active = true AND s.startAt <= :tag AND s.endAt >= :tag "
            + "ORDER BY s.startAt DESC, s.id DESC")
    List<OutOfOfficeSchedule> findAktiveOhnePostfach(@Param("tag") LocalDate tag);
}
