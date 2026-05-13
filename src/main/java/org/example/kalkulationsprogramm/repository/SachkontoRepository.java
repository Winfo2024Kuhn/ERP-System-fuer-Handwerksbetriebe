package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SachkontoRepository extends JpaRepository<Sachkonto, Long> {
    List<Sachkonto> findAllByOrderBySortierungAscBezeichnungAsc();
    List<Sachkonto> findByAktivTrueOrderBySortierungAscBezeichnungAsc();
    Optional<Sachkonto> findByNummer(String nummer);
}
