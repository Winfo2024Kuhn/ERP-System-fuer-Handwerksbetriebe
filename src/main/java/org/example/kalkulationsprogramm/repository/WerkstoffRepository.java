package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WerkstoffRepository extends JpaRepository<Werkstoff, Long>, WerkstoffRepositoryErweiterung {
	/** Wie viele Artikel einen Werkstoff benutzen: {id, Anzahl} je Werkstoff, der benutzt wird. */
	@Query("select a.werkstoff.id, count(a) from Artikel a where a.werkstoff is not null group by a.werkstoff.id")
	List<Object[]> zaehleArtikelJeWerkstoff();

	@Query("select count(a) from Artikel a where a.werkstoff.id = :id")
	long zaehleArtikel(@Param("id") Long id);

	Optional<Werkstoff> findByNameIgnoreCase(String name);

	/**
	 * Wie {@link #findByNameIgnoreCase}, aber mit Zeilensperre - liest damit den
	 * neuesten Stand, auch wenn ein paralleler Import den Werkstoff gerade erst
	 * angelegt hat (ohne Sperre koennte je nach Isolationsstufe ein aelterer Stand gelesen werden).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Werkstoff w WHERE LOWER(w.name) = LOWER(:name)")
	Optional<Werkstoff> findByNameGesperrt(@Param("name") String name);
}
