package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProduktkategorieRepository extends JpaRepository<Produktkategorie, Long> {
    // Findet alle Kategorien, die keine übergeordnete Kategorie haben (die oberste Ebene).
    @EntityGraph(attributePaths = {"unterkategorien"})
    List<Produktkategorie> findByUebergeordneteKategorieIsNull();

    // Findet alle direkten Kinder einer bestimmten Kategorie-ID.
    @EntityGraph(attributePaths = {"unterkategorien"})
    List<Produktkategorie> findByUebergeordneteKategorieId(Long parentId);

    // Lädt alle Kategorien mit Parent in einer Query (kein N+1 bei bauePfad / parentId)
    @Query("SELECT k FROM Produktkategorie k LEFT JOIN FETCH k.uebergeordneteKategorie")
    List<Produktkategorie> findAllWithParent();

    // Liefert alle IDs, die als Parent referenziert werden (zur isLeaf-Berechnung ohne Collection-Load)
    @Query("SELECT k.uebergeordneteKategorie.id FROM Produktkategorie k WHERE k.uebergeordneteKategorie IS NOT NULL")
    List<Long> findAllParentIds();

    // Sucht Leaf-Kategorien (ohne Unterkategorien) nach Bezeichnung
    @Query("SELECT pk FROM Produktkategorie pk WHERE pk.unterkategorien IS EMPTY AND LOWER(pk.bezeichnung) LIKE LOWER(CONCAT('%', :suchbegriff, '%'))")
    List<Produktkategorie> sucheLeafKategorienNachBezeichnung(@Param("suchbegriff") String suchbegriff);

}
