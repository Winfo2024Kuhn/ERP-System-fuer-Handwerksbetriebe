package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.Lagerort
import org.springframework.data.jpa.repository.JpaRepository

interface LagerortRepository : JpaRepository<Lagerort, Long> {
    fun findByCodeIgnoreCase(code: String): Lagerort?
    fun findByAktivTrueOrderByCodeAsc(): List<Lagerort>
}
