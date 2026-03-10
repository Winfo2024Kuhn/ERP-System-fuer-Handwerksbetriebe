package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LieferantBildRepository extends JpaRepository<LieferantBild, Long> {
    List<LieferantBild> findByLieferantIdOrderByErstelltAmDesc(Long lieferantId);

    java.util.Optional<LieferantBild> findByGespeicherterDateiname(String gespeicherterDateiname);
}
