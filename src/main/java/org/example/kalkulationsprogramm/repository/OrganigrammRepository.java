package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Organigramm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganigrammRepository extends JpaRepository<Organigramm, Long> {

    Optional<Organigramm> findByName(String name);

    boolean existsByName(String name);

    void deleteByName(String name);
}
