package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.Lagerbewegung
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LagerbewegungRepository : JpaRepository<Lagerbewegung, Long> {
    @Query(
        """
        select b from Lagerbewegung b
        join fetch b.artikel a
        left join fetch b.vonLagerort
        left join fetch b.nachLagerort
        order by b.erstelltAm desc, b.id desc
        """
    )
    fun findeNeueste(): List<Lagerbewegung>
}
