package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.En1090Rolle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface En1090RolleRepository extends JpaRepository<En1090Rolle, Long> {
    List<En1090Rolle> findAllByOrderBySortierungAsc();
}
