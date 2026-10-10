package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LieferantBildRepository extends JpaRepository<LieferantBild, Long> {
    // Als @Query statt abgeleitet: nur so laesst sich festlegen, wohin leere Werte sortieren
    // (wie MySQL: aufsteigend zuerst, absteigend zuletzt) - siehe DatenbankKompatibilitaetSortierungTest
    @Query("SELECT b FROM LieferantBild b WHERE b.lieferant.id = :lieferantId ORDER BY b.erstelltAm DESC NULLS LAST")
    List<LieferantBild> findByLieferantIdOrderByErstelltAmDesc(@Param("lieferantId") Long lieferantId);

    java.util.Optional<LieferantBild> findByGespeicherterDateiname(String gespeicherterDateiname);
}
