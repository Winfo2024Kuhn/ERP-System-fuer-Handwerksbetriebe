package org.example.kalkulationsprogramm.repository

import java.time.LocalDate
import java.util.Optional
import org.example.kalkulationsprogramm.domain.ProjektDokument
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProjektDokumentRepository : JpaRepository<ProjektDokument, Long> {
    fun findByProjektId(projektId: Long?): List<ProjektDokument>

    @Query("SELECT g FROM ProjektGeschaeftsdokument g")
    fun findAllGeschaeftsdokumente(): List<ProjektGeschaeftsdokument>

    @Query(
        """
      SELECT g FROM ProjektGeschaeftsdokument g
      WHERE g.bezahlt = false
        AND (
              LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
        )
      """,
    )
    fun findOffeneGeschaeftsdokumente(): List<ProjektGeschaeftsdokument>

    @Query(
        """
      SELECT DISTINCT g FROM ProjektGeschaeftsdokument g
      LEFT JOIN FETCH g.mahnungen
      LEFT JOIN FETCH g.projekt p
      LEFT JOIN FETCH p.kundenId
      WHERE g.bezahlt = false
        AND (
              LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
        )
      """,
    )
    fun findOffeneGeschaeftsdokumenteFuerMahnlauf(): List<ProjektGeschaeftsdokument>

    @Query("SELECT g FROM ProjektGeschaeftsdokument g WHERE LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%' AND g.rechnungsdatum BETWEEN :start AND :end")
    fun findGeschaeftsdokumenteByRechnungsdatumBetween(
        start: LocalDate,
        end: LocalDate,
    ): List<ProjektGeschaeftsdokument>

    @Query("SELECT g FROM ProjektGeschaeftsdokument g WHERE g.projekt.id = :projektId AND g.geschaeftsdokumentart = 'Rechnung'")
    fun findRechnungenByProjektId(@Param("projektId") projektId: Long?): List<ProjektGeschaeftsdokument>

    @Query(
        """
      SELECT g FROM ProjektGeschaeftsdokument g
      WHERE g.projekt.id = :projektId
        AND g.mahnstufe IS NOT NULL
      """,
    )
    fun findMahnungenByProjektId(@Param("projektId") projektId: Long?): List<ProjektGeschaeftsdokument>

    @Query("select g.projekt.id, g.dokumentid from ProjektGeschaeftsdokument g where g.projekt.id in :ids")
    fun findDokumentIdsByProjektIds(ids: List<Long>): List<Array<Any>>

    @Query("SELECT g.id, g.projekt.id FROM ProjektGeschaeftsdokument g WHERE g.projekt.id IN :projektIds")
    fun findGeschaeftsdokumentIdMappingByProjektIds(
        @Param("projektIds") projektIds: List<Long>,
    ): List<Array<Any>>

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM ProjektGeschaeftsdokument g WHERE g.dokumentid = :dokumentid")
    fun existsByDokumentid(@Param("dokumentid") dokumentid: String): Boolean

    @Query(
        """
      SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
      FROM ProjektGeschaeftsdokument g
      WHERE g.projekt.id = :projektId
        AND g.bezahlt = false
        AND (
              LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
        )
      """,
    )
    fun existsOffenePostenByProjektId(@Param("projektId") projektId: Long?): Boolean

    fun findByGespeicherterDateiname(gespeicherterDateiname: String): Optional<ProjektDokument>

    fun findByGespeicherterDateinameIgnoreCase(gespeicherterDateiname: String): Optional<ProjektDokument>

    fun findByOriginalDateinameIgnoreCase(originalDateiname: String): Optional<ProjektDokument>
}
