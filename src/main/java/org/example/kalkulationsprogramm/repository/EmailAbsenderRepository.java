package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailAbsenderRepository extends JpaRepository<EmailAbsender, Long> {

    List<EmailAbsender> findAllByOrderBySortierungAscIdAsc();

    List<EmailAbsender> findByAktivTrueOrderBySortierungAscIdAsc();

    Optional<EmailAbsender> findFirstByAktivTrueOrderBySortierungAscIdAsc();

    Optional<EmailAbsender> findByEmailAdresseIgnoreCase(String emailAdresse);

    Optional<EmailAbsender> findFirstByHauptpostfachTrueOrderByIdAsc();

    Optional<EmailAbsender> findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc();

    List<EmailAbsender> findByHauptpostfachTrue();

    List<EmailAbsender> findByFuerGeschaeftsdokumenteTrue();

    /** Postfächer, die dem Benutzer einzeln freigegeben sind (unabhängig von „für alle sichtbar“). */
    @Query("SELECT DISTINCT p.id FROM EmailAbsender p JOIN p.sichtbarFuerBenutzer b WHERE b.id = :benutzerId")
    List<Long> findIdsFreigegebenFuerBenutzer(@Param("benutzerId") Long benutzerId);

    /** Postfächer, die mindestens einer der Abteilungen freigegeben sind. */
    @Query("SELECT DISTINCT p.id FROM EmailAbsender p JOIN p.sichtbarFuerAbteilungen a WHERE a.id IN :abteilungIds")
    List<Long> findIdsFreigegebenFuerAbteilungen(@Param("abteilungIds") Collection<Long> abteilungIds);
}
