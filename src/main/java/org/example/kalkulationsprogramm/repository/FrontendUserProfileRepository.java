package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FrontendUserProfileRepository extends JpaRepository<FrontendUserProfile, Long> {

    Optional<FrontendUserProfile> findByDisplayNameIgnoreCase(String displayName);

    Optional<FrontendUserProfile> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByUsernameIsNotNull();

    @Query("SELECT COUNT(p) FROM FrontendUserProfile p " +
           "JOIN p.roleSet r " +
           "WHERE p.active = true AND r = :role AND p.id <> :excludedId")
    long countActiveByRoleExcludingId(@Param("role") FrontendUserRole role, @Param("excludedId") Long excludedId);
}
