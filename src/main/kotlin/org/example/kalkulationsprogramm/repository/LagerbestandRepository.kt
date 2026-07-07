package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lagerbestand
import org.example.kalkulationsprogramm.domain.Lagerort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LagerbestandRepository : JpaRepository<Lagerbestand, Long> {
    fun findByArtikelAndLagerort(artikel: Artikel, lagerort: Lagerort): Lagerbestand?

    @Query(
        """
        select lb from Lagerbestand lb
        join fetch lb.artikel a
        join fetch lb.lagerort lo
        where (:q is null or :q = ''
            or lower(coalesce(a.produktname, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(a.produktlinie, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(a.produkttext, '')) like lower(concat('%', :q, '%'))
            or lower(lo.code) like lower(concat('%', :q, '%'))
            or lower(lo.name) like lower(concat('%', :q, '%')))
        order by a.produktname asc, lo.code asc
        """
    )
    fun suche(@Param("q") query: String?): List<Lagerbestand>
}
