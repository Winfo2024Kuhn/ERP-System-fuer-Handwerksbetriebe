package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ZaehlerstandRepository extends JpaRepository<Zaehlerstand, Long> {
    Optional<Zaehlerstand> findByVerbrauchsgegenstandAndAbrechnungsJahr(Verbrauchsgegenstand verbrauchsgegenstand, Integer abrechnungsJahr);

    List<Zaehlerstand> findByVerbrauchsgegenstandOrderByAbrechnungsJahrDesc(Verbrauchsgegenstand verbrauchsgegenstand);

    List<Zaehlerstand> findByVerbrauchsgegenstandInAndAbrechnungsJahr(Collection<Verbrauchsgegenstand> gegenstaende, Integer abrechnungsJahr);
}
