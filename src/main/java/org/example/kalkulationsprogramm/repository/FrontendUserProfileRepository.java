package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FrontendUserProfileRepository extends JpaRepository<FrontendUserProfile, Long> {

    Optional<FrontendUserProfile> findByDisplayNameIgnoreCase(String displayName);

    Optional<FrontendUserProfile> findByUsernameIgnoreCase(String username);

    Optional<FrontendUserProfile> findByMitarbeiterIdAndActiveTrue(Long mitarbeiterId);

    List<FrontendUserProfile> findByEmailAbsenderId(Long emailAbsenderId);

    List<FrontendUserProfile> findByEmailAbsenderIsNotNull();

    /** Postfächer, die einem Benutzer als eigenes gehören – alle anderen sind allgemeine (info@, buero@). */
    @Query("SELECT DISTINCT p.emailAbsender.id FROM FrontendUserProfile p WHERE p.emailAbsender IS NOT NULL")
    List<Long> findEmailAbsenderIdsMitBesitzer();

    boolean existsByUsernameIgnoreCase(String username);

    /** Nur „aktiv?“ – ohne die eager geladenen Verknüpfungen; läuft bei jeder Anfrage (SitzungsRechteFilter). */
    @Query("SELECT p.active FROM FrontendUserProfile p WHERE p.id = :id")
    Optional<Boolean> findAktivById(@Param("id") Long id);

    /** Nur die Rollen eines Benutzers; läuft bei jeder Anfrage (SitzungsRechteFilter). */
    @Query("SELECT r FROM FrontendUserProfile p JOIN p.roleSet r WHERE p.id = :id")
    Set<FrontendUserRole> findRollenById(@Param("id") Long id);

    long countByUsernameIsNotNull();

    @Query("SELECT COUNT(p) FROM FrontendUserProfile p " +
           "JOIN p.roleSet r " +
           "WHERE p.active = true AND r = :role AND p.id <> :excludedId")
    long countActiveByRoleExcludingId(@Param("role") FrontendUserRole role, @Param("excludedId") Long excludedId);

    /** Löst eine Signatur als Standard-Signatur bei allen Benutzern, bevor sie gelöscht wird. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FrontendUserProfile p SET p.defaultSignature = null WHERE p.defaultSignature.id = :signaturId")
    int entferneStandardSignatur(@Param("signaturId") Long signaturId);

    /** Benutzer, bei denen die Signatur als Standard-Signatur eingetragen ist. */
    List<FrontendUserProfile> findByDefaultSignatureId(Long signaturId);
}
