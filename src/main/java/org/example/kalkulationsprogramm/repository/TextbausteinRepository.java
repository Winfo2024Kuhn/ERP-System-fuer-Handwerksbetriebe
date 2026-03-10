package org.example.kalkulationsprogramm.repository;

import java.util.List;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TextbausteinRepository extends JpaRepository<Textbaustein, Long> {
    List<Textbaustein> findByTypOrderBySortOrderAscNameAsc(TextbausteinTyp typ);

    @Query("SELECT DISTINCT t FROM Textbaustein t LEFT JOIN FETCH t.dokumenttypen LEFT JOIN FETCH t.placeholders")
    List<Textbaustein> findAllWithDokumenttypen();
}
