package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProduktkategorieRepository extends JpaRepository<Produktkategorie, Long> {
    // Findet alle Kategorien, die keine übergeordnete Kategorie haben (die oberste Ebene).
    @EntityGraph(attributePaths = {"unterkategorien"})
    List<Produktkategorie> findByUebergeordneteKategorieIsNull();

    // Findet alle direkten Kinder einer bestimmten Kategorie-ID.
    @EntityGraph(attributePaths = {"unterkategorien"})
    List<Produktkategorie> findByUebergeordneteKategorieId(Long parentId);

}
