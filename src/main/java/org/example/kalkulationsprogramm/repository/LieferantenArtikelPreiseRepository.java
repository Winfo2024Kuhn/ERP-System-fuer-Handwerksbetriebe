package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreiseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface LieferantenArtikelPreiseRepository
        extends JpaRepository<LieferantenArtikelPreise, LieferantenArtikelPreiseId>,
        JpaSpecificationExecutor<LieferantenArtikelPreise> {
    Optional<LieferantenArtikelPreise> findByArtikel_IdAndLieferant_Id(Long artikelId, Long lieferantId);

    Optional<LieferantenArtikelPreise> findByExterneArtikelnummerIgnoreCaseAndLieferant_Id(String externeArtikelnummer,
            Long lieferantId);
}
