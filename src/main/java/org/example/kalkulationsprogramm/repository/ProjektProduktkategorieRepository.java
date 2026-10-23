package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjektProduktkategorieRepository extends JpaRepository<ProjektProduktkategorie, Long> {

    /** Projekt-Zuordnungen der Ordner, Projekt gleich mitgeladen (Bauvorhaben, Auftragsnummer). */
    @Query("SELECT ppk FROM ProjektProduktkategorie ppk JOIN FETCH ppk.projekt WHERE ppk.produktkategorie.id IN :kategorieIds")
    List<ProjektProduktkategorie> findeMitProjekt(@Param("kategorieIds") Collection<Long> kategorieIds);

    Optional<ProjektProduktkategorie> findFirstByProjektIdAndProduktkategorieId(Long projektId, Long produktkategorieId);

    /**
     * Kategorie-ID, Projekt-ID und Menge je Zuordnung abgeschlossener Projekte;
     * mit {@code von}/{@code bis} nur Projekte, die in diesem Zeitraum abgeschlossen wurden.
     */
    @Query("SELECT ppk.produktkategorie.id, p.id, ppk.menge FROM ProjektProduktkategorie ppk JOIN ppk.projekt p "
            + "WHERE p.abgeschlossen = true AND (CAST(:von AS LocalDate) IS NULL "
            + "OR (p.abschlussdatum >= :von AND p.abschlussdatum <= :bis))")
    List<Object[]> findeMengenAbgeschlossenerProjekte(@Param("von") LocalDate von, @Param("bis") LocalDate bis);
}
