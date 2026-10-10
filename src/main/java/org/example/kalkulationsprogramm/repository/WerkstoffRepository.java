package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WerkstoffRepository extends JpaRepository<Werkstoff, Long>, WerkstoffRepositoryErweiterung {
	Optional<Werkstoff> findByNameIgnoreCase(String name);

	/**
	 * Wie {@link #findByNameIgnoreCase}, aber mit Zeilensperre - liest damit den
	 * neuesten Stand, auch wenn ein paralleler Import den Werkstoff gerade erst
	 * angelegt hat (MySQL liest ohne Sperre den Stand vom Transaktionsbeginn).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Werkstoff w WHERE LOWER(w.name) = LOWER(:name)")
	Optional<Werkstoff> findByNameGesperrt(@Param("name") String name);
}
