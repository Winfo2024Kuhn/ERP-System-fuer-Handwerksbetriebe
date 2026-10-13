package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.domain.RechtstextFassung;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RechtstextFassungRepository extends JpaRepository<RechtstextFassung, Long>
{
    /** Jüngste Fassung einer Art – das ist der gültige Text. */
    Optional<RechtstextFassung> findFirstByArtOrderByFassungDesc(RechtstextArt art);

    /** Alle Fassungen einer Art, neueste zuerst. */
    List<RechtstextFassung> findByArtOrderByFassungDesc(RechtstextArt art);

    Optional<RechtstextFassung> findByArtAndFassung(RechtstextArt art, int fassung);
}
