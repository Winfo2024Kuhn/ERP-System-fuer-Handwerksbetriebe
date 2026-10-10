package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAbsenderRepository extends JpaRepository<EmailAbsender, Long> {

    List<EmailAbsender> findAllByOrderBySortierungAscIdAsc();

    List<EmailAbsender> findByAktivTrueOrderBySortierungAscIdAsc();

    Optional<EmailAbsender> findFirstByAktivTrueOrderBySortierungAscIdAsc();

    Optional<EmailAbsender> findByEmailAdresseIgnoreCase(String emailAdresse);

    Optional<EmailAbsender> findFirstByHauptpostfachTrueOrderByIdAsc();

    Optional<EmailAbsender> findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc();

    List<EmailAbsender> findByHauptpostfachTrue();

    List<EmailAbsender> findByFuerGeschaeftsdokumenteTrue();
}
