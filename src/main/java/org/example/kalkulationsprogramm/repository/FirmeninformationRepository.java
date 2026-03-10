package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FirmeninformationRepository extends JpaRepository<Firmeninformation, Long> {

    /**
     * Lädt die Firmeninformation (Singleton mit id=1).
     */
    default Optional<Firmeninformation> findFirmeninformation() {
        return findById(1L);
    }

    /**
     * Lädt oder erstellt die Firmeninformation.
     */
    default Firmeninformation getOrCreate() {
        return findById(1L).orElseGet(() -> {
            Firmeninformation fi = new Firmeninformation();
            fi.setId(1L);
            fi.setFirmenname("Neue Firma");
            return save(fi);
        });
    }
}
