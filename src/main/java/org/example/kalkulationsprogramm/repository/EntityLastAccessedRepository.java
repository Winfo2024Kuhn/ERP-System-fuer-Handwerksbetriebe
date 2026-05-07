package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.EntityLastAccessed;
import org.example.kalkulationsprogramm.domain.EntityLastAccessed.EntityLastAccessedId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntityLastAccessedRepository extends JpaRepository<EntityLastAccessed, EntityLastAccessedId> {

    @Query("SELECT e FROM EntityLastAccessed e " +
            "WHERE e.id.userId = :userId AND e.id.entityType = :entityType " +
            "ORDER BY e.zugegriffenAm DESC")
    List<EntityLastAccessed> findAllByUserAndType(@Param("userId") Long userId,
                                                  @Param("entityType") String entityType);
}
