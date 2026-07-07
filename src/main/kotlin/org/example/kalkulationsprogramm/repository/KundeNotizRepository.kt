package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.KundeNotiz
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KundeNotizRepository : JpaRepository<KundeNotiz, Long> {
    fun findByKundeIdOrderByErstelltAmDesc(kundeId: Long?): List<KundeNotiz>

    fun findByKundeIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(
        kundeId: Long?,
        query: String?,
    ): List<KundeNotiz>
}
