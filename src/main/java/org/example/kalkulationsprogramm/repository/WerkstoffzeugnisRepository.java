package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoffzeugnis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WerkstoffzeugnisRepository extends JpaRepository<Werkstoffzeugnis, Long> {

    List<Werkstoffzeugnis> findByLieferantId(Long lieferantId);

    Optional<Werkstoffzeugnis> findBySchmelzNummer(String schmelzNummer);

    Optional<Werkstoffzeugnis> findByLieferantDokumentId(Long lieferantDokumentId);

    /** Alle Werkstoffzeugnisse zu einem Lieferschein (1:N) */
    List<Werkstoffzeugnis> findByLieferscheinDokumentId(Long lieferscheinDokumentId);

    @Query("SELECT DISTINCT w FROM Werkstoffzeugnis w JOIN w.projekte p WHERE p.id = :projektId")
    List<Werkstoffzeugnis> findByProjektId(Long projektId);
}
