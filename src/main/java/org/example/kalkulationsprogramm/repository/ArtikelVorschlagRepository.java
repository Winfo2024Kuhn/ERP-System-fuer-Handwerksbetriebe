package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelVorschlag;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtikelVorschlagRepository extends JpaRepository<ArtikelVorschlag, Long> {

    List<ArtikelVorschlag> findByStatusOrderByErstelltAmDesc(ArtikelVorschlagStatus status);

    long countByStatus(ArtikelVorschlagStatus status);
}
