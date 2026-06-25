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

  /**
   * Liefert alle Mahn-Dokumente eines Projekts (Zahlungserinnerung, 1./2. Mahnung).
   * Mahnungen leben weiterhin ausschließlich in {@code ProjektGeschaeftsdokument},
   * werden aber im AusgangsGeschaeftsDokument-Tab als virtuelle Children der
   * zugehörigen Rechnung sichtbar gemacht.
   */
  @Query("""
      SELECT g FROM ProjektGeschaeftsdokument g
      WHERE g.projekt.id = :projektId
        AND g.mahnstufe IS NOT NULL
      """)
  List<ProjektGeschaeftsdokument> findMahnungenByProjektId(@Param("projektId") Long projektId);

  @Query("select g.projekt.id, g.dokumentid from ProjektGeschaeftsdokument g where g.projekt.id in :ids")
  List<Object[]> findDokumentIdsByProjektIds(List<Long> ids);

  /**
   * Liefert für eine Liste von Projekt-IDs ein Mapping
   * [ProjektGeschaeftsdokument.id, Projekt.id], damit der DokumentFreigabeService
   * den jüngsten Freigabe-Status pro Projekt finden kann.
   */
  @Query("SELECT g.id, g.projekt.id FROM ProjektGeschaeftsdokument g WHERE g.projekt.id IN :projektIds")
  List<Object[]> findGeschaeftsdokumentIdMappingByProjektIds(@Param("projektIds") List<Long> projektIds);

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

  /**
   * Summe der Brutto-Beträge aller als bezahlt markierten Rechnungen eines Projekts
   * (Mahnungen/Zahlungserinnerungen ausgenommen). Dient der Prüfung, ob die komplette
   * Auftragssumme beglichen wurde, bevor ein Projekt als abgeschlossen gilt – ein
   * bezahlter Abschlag allein reicht nicht.
   */
  @Query("""
      SELECT COALESCE(SUM(g.bruttoBetrag), 0)
      FROM ProjektGeschaeftsdokument g
      WHERE g.projekt.id = :projektId
        AND g.bezahlt = true
        AND g.mahnstufe IS NULL
        AND LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
      """)
  java.math.BigDecimal sumBezahlteRechnungenByProjektId(@Param("projektId") Long projektId);

  Optional<ProjektDokument> findByGespeicherterDateiname(String gespeicherterDateiname);

  Optional<ProjektDokument> findByGespeicherterDateinameIgnoreCase(String gespeicherterDateiname);

  Optional<ProjektDokument> findByOriginalDateinameIgnoreCase(String originalDateiname);
}
