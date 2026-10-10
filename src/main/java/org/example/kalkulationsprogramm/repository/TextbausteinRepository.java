package org.example.kalkulationsprogramm.repository;

import java.util.List;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TextbausteinRepository extends JpaRepository<Textbaustein, Long> {
    // Als @Query statt abgeleitet: nur so laesst sich festlegen, wohin leere Werte sortieren
    // (wie bisher: aufsteigend zuerst, absteigend zuletzt) - siehe DatenbankKompatibilitaetSortierungTest
    @Query("SELECT t FROM Textbaustein t WHERE t.typ = :typ ORDER BY t.sortOrder ASC NULLS FIRST, t.name ASC")
    List<Textbaustein> findByTypOrderBySortOrderAscNameAsc(@Param("typ") TextbausteinTyp typ);

    @Query("SELECT DISTINCT t FROM Textbaustein t LEFT JOIN FETCH t.dokumenttypen LEFT JOIN FETCH t.placeholders")
    List<Textbaustein> findAllWithDokumenttypen();
}
