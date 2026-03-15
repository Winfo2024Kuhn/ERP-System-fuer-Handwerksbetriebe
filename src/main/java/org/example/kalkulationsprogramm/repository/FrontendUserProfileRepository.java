package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrontendUserProfileRepository extends JpaRepository<FrontendUserProfile, Long> {

    Optional<FrontendUserProfile> findByDisplayNameIgnoreCase(String displayName);

    Optional<FrontendUserProfile> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByUsernameIsNotNull();
}
