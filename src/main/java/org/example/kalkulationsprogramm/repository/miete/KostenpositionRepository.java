package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KostenpositionRepository extends JpaRepository<Kostenposition, Long> {
    List<Kostenposition> findByKostenstelle(Kostenstelle kostenstelle);

    List<Kostenposition> findByKostenstelleAndAbrechnungsJahr(Kostenstelle kostenstelle, Integer abrechnungsJahr);

    List<Kostenposition> findByKostenstelleMietobjektIdAndAbrechnungsJahr(Long mietobjektId, Integer abrechnungsJahr);
}
