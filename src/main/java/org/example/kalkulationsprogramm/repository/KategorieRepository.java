package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Kategorie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KategorieRepository extends JpaRepository<Kategorie, Integer> {

    List<Kategorie> findByParentKategorieIsNull();

    List<Kategorie> findByParentKategorie_Id(Integer parentId);

    boolean existsByParentKategorie_Id(Integer parentId);
}
