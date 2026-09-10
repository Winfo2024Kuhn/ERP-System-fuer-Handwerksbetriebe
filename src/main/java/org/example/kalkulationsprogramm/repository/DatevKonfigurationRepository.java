package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.DatevKonfiguration;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface DatevKonfigurationRepository extends JpaRepository<DatevKonfiguration, Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from DatevKonfiguration c where c.id = 1")
 Optional<DatevKonfiguration> sperren();
}
