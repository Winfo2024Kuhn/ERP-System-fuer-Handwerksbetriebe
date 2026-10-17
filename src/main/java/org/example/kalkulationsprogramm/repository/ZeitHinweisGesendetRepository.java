package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;

import org.example.kalkulationsprogramm.domain.ZeitHinweisGesendet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZeitHinweisGesendetRepository extends JpaRepository<ZeitHinweisGesendet, Long> {

    boolean existsByMitarbeiterIdAndTagAndArt(Long mitarbeiterId, LocalDate tag, ZeitHinweisGesendet.Art art);

    /** Alte Einträge braucht niemand mehr – der Job schaut nur auf den heutigen Tag. */
    @Modifying
    @Query("DELETE FROM ZeitHinweisGesendet z WHERE z.tag < :vor")
    int loescheAelterAls(@Param("vor") LocalDate vor);
}
