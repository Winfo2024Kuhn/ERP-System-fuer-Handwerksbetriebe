package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FrontendUserProfileRepository extends JpaRepository<FrontendUserProfile, Long> {

    Optional<FrontendUserProfile> findByDisplayNameIgnoreCase(String displayName);
}
