package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AbteilungRepository extends JpaRepository<Abteilung, Long> {
    Optional<Abteilung> findByName(String name);

    /** Gibt es den Namen schon? Groß-/Kleinschreibung zählt nicht – „Montage“ und „montage“ wären sonst zwei Abteilungen. */
    boolean existsByNameIgnoreCase(String name);

    /** Wie {@link #existsByNameIgnoreCase}, nur ohne die Abteilung, die gerade umbenannt wird. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Abteilungen nach Name (ohne Groß/Klein); die Obergrenze kommt über {@code pageable}. */
    @Query("SELECT a FROM Abteilung a ORDER BY lower(a.name), a.id")
    List<Abteilung> findeSortiert(Pageable pageable);
}
