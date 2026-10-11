package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BenachrichtigungErledigt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenachrichtigungErledigtRepository extends JpaRepository<BenachrichtigungErledigt, Long> {

    @Query("SELECT e FROM BenachrichtigungErledigt e WHERE e.benutzer.id = :benutzerId")
    List<BenachrichtigungErledigt> findByBenutzerId(@Param("benutzerId") Long benutzerId);

    @Query("SELECT e FROM BenachrichtigungErledigt e WHERE e.benutzer.id = :benutzerId AND e.schluessel IN :schluessel")
    List<BenachrichtigungErledigt> findByBenutzerIdAndSchluesselIn(@Param("benutzerId") Long benutzerId,
            @Param("schluessel") Collection<String> schluessel);

    @Modifying
    @Query("DELETE FROM BenachrichtigungErledigt e WHERE e.erledigtAm < :grenze")
    int deleteAelterAls(@Param("grenze") LocalDateTime grenze);
}
