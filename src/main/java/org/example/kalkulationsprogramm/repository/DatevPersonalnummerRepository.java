package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.DatevPersonalnummer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DatevPersonalnummerRepository extends JpaRepository<DatevPersonalnummer, Long> {
 @Query("select m.id from Mitarbeiter m where m.id in :ids and m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH")
 List<Long> findMenschenIds(@Param("ids") Set<Long> ids);
}
