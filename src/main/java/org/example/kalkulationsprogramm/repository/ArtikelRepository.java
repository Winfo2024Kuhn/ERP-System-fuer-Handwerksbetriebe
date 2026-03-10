package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtikelRepository extends JpaRepository<Artikel, Long>, JpaSpecificationExecutor<Artikel> {
    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer))")
    Optional<Artikel> findByExterneArtikelnummer(@Param("nummer") String nummer);

    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer)) and n.lieferant.id = :lieferantId")
    Optional<Artikel> findByExterneArtikelnummerAndLieferantId(@Param("nummer") String nummer,
                                                               @Param("lieferantId") Long lieferantId);

    @Query("select distinct a.produktlinie from Artikel a left join a.artikelpreis ap where (ap is null or ap.lieferant.id <> :lieferantId) and a.produktlinie is not null")
    List<String> findDistinctProduktlinieExcludingLieferant(@Param("lieferantId") Long lieferantId);
}

