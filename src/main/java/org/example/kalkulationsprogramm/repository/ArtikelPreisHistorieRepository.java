package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtikelPreisHistorieRepository extends JpaRepository<ArtikelPreisHistorie, Long> {

    List<ArtikelPreisHistorie> findByArtikel_IdOrderByErfasstAmDesc(Long artikelId);

    List<ArtikelPreisHistorie> findByArtikel_IdAndQuelleOrderByErfasstAmDesc(Long artikelId, PreisQuelle quelle);
}
