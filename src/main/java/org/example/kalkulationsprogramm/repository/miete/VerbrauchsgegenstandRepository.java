package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Raum;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsart;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerbrauchsgegenstandRepository extends JpaRepository<Verbrauchsgegenstand, Long> {
    List<Verbrauchsgegenstand> findByRaumOrderByNameAsc(Raum raum);

    List<Verbrauchsgegenstand> findByRaumMietobjektId(Long mietobjektId);

    List<Verbrauchsgegenstand> findByRaumMietobjektIdAndVerbrauchsart(Long mietobjektId, Verbrauchsart verbrauchsart);
}
