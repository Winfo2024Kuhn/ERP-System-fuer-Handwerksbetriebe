package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Schnittbilder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchnittbilderRepository extends JpaRepository<Schnittbilder, Long> {
    List<Schnittbilder> findByKategorie_Id(Integer kategorieId);
    Schnittbilder findByForm(String form);
}

