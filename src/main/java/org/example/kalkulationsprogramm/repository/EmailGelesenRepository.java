package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailGelesen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailGelesenRepository extends JpaRepository<EmailGelesen, Long>, EmailGelesenRepositoryErweiterung {

    /** IDs aller Mails, die der Benutzer gelesen hat. */
    @Query("SELECT g.email.id FROM EmailGelesen g WHERE g.benutzer.id = :benutzerId")
    Set<Long> findEmailIdsByBenutzerId(@Param("benutzerId") Long benutzerId);

    /**
     * Neuer Benutzer: alle schon vorhandenen Mails der Richtung {@code richtung} gelten für ihn
     * als gelesen – sonst stünde er am ersten Tag vor Tausenden „neuen“ Mails.
     */
    @Modifying
    @Transactional
    @Query("INSERT INTO EmailGelesen (benutzer, email, gelesenAm) "
            + "SELECT b, e, :jetzt FROM FrontendUserProfile b, Email e "
            + "WHERE b.id = :benutzerId AND e.direction = :richtung "
            + "AND NOT EXISTS (SELECT 1 FROM EmailGelesen g WHERE g.benutzer = b AND g.email = e)")
    int alleMailsAlsGelesen(@Param("benutzerId") Long benutzerId, @Param("richtung") EmailDirection richtung,
            @Param("jetzt") LocalDateTime jetzt);
}
