package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SeenSenderDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeenSenderDomainRepository extends JpaRepository<SeenSenderDomain, String>, SeenSenderDomainRepositoryErweiterung {

    boolean existsByDomain(String domain);
}
