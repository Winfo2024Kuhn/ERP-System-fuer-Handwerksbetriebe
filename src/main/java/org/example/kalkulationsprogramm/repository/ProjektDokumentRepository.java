package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjektDokumentRepository extends JpaRepository<ProjektDokument, Long> {
  List<ProjektDokument> findByProjektId(Long projektId);

  @Query("SELECT g FROM ProjektGeschaeftsdokument g")
  List<ProjektGeschaeftsdokument> findAllGeschaeftsdokumente();

  @Query("""
      SELECT g FROM ProjektGeschaeftsdokument g
      WHERE g.bezahlt = false
        AND (
              LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
        )
      """)
  List<ProjektGeschaeftsdokument> findOffeneGeschaeftsdokumente();

  @Query("SELECT g FROM ProjektGeschaeftsdokument g WHERE LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%' AND g.rechnungsdatum BETWEEN :start AND :end")
  List<ProjektGeschaeftsdokument> findGeschaeftsdokumenteByRechnungsdatumBetween(java.time.LocalDate start,
      java.time.LocalDate end);

  @Query("SELECT g FROM ProjektGeschaeftsdokument g WHERE g.projekt.id = :projektId AND g.geschaeftsdokumentart = 'Rechnung'")
  List<ProjektGeschaeftsdokument> findRechnungenByProjektId(@Param("projektId") Long projektId);

  @Query("select g.projekt.id, g.dokumentid from ProjektGeschaeftsdokument g where g.projekt.id in :ids")
  List<Object[]> findDokumentIdsByProjektIds(List<Long> ids);

  /**
   * Prüft ob ein Geschäftsdokument mit der Dokumentnummer bereits existiert.
   */
  @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM ProjektGeschaeftsdokument g WHERE g.dokumentid = :dokumentid")
  boolean existsByDokumentid(@Param("dokumentid") String dokumentid);

  /**
   * Prüft ob es noch unbezahlte Rechnungen für ein Projekt gibt.
   */
  @Query("""
      SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
      FROM ProjektGeschaeftsdokument g
      WHERE g.projekt.id = :projektId
        AND g.bezahlt = false
        AND (
              LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
           OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
        )
      """)
  boolean existsOffenePostenByProjektId(@Param("projektId") Long projektId);

  Optional<ProjektDokument> findByGespeicherterDateiname(String gespeicherterDateiname);

  Optional<ProjektDokument> findByGespeicherterDateinameIgnoreCase(String gespeicherterDateiname);

  Optional<ProjektDokument> findByOriginalDateinameIgnoreCase(String originalDateiname);
}
