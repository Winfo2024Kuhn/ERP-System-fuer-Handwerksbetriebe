package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AngebotRepository extends JpaRepository<Angebot, Long> {
     @Query("SELECT DISTINCT a FROM Angebot a LEFT JOIN FETCH a.kunde k LEFT JOIN FETCH k.kundenEmails WHERE a.projekt.id IN :ids")
     List<Angebot> findByProjektIdIn(List<Long> ids);

     @Query("SELECT DISTINCT a FROM Angebot a LEFT JOIN FETCH a.kunde k LEFT JOIN FETCH k.kundenEmails WHERE a.kunde.id = :kundeId")
     List<Angebot> findByKundeId(@org.springframework.data.repository.query.Param("kundeId") Long kundeId);

     @Query("SELECT DISTINCT a FROM Angebot a LEFT JOIN FETCH a.kunde k LEFT JOIN FETCH k.kundenEmails WHERE LOWER(k.kundennummer) = LOWER(:kundennummer)")
     List<Angebot> findByKunde_KundennummerIgnoreCase(@org.springframework.data.repository.query.Param("kundennummer") String kundennummer);

     @Query("select a.projekt.id, g.dokumentid from Angebot a join a.dokumente g where a.projekt.id in :ids and g.geschaeftsdokumentart = 'Angebot'")
     List<Object[]> findDokumentIdsByProjektIds(List<Long> ids);

     @Query("SELECT DISTINCT a FROM Angebot a LEFT JOIN FETCH a.kunde k LEFT JOIN FETCH k.kundenEmails")
     List<Angebot> findAllWithKundenEmails();

     @Query("""
               SELECT DISTINCT a FROM Angebot a
               LEFT JOIN FETCH a.kunde k
               LEFT JOIN k.kundenEmails e
               WHERE (
                      :kundenname IS NULL OR
                      LOWER(k.name) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(k.kundennummer) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(k.ansprechspartner) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(k.telefon) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(k.mobiltelefon) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(k.ort) LIKE CONCAT('%', LOWER(:kundenname), '%') OR
                      LOWER(e) LIKE CONCAT('%', LOWER(:kundenname), '%')
                 )
                 AND (
                      :bauvorhaben IS NULL OR
                      LOWER(a.bauvorhaben) LIKE CONCAT('%', LOWER(:bauvorhaben), '%')
                 )
                 AND (:startDate IS NULL OR a.anlegedatum >= :startDate)
                 AND (:endDate IS NULL OR a.anlegedatum <= :endDate)
                 AND (
                      :angebotsnummer IS NULL OR EXISTS (
                          SELECT d FROM AusgangsGeschaeftsDokument d
                          WHERE d.projekt = a.projekt
                            AND d.typ = 'ANGEBOT'
                            AND LOWER(d.dokumentNummer) LIKE LOWER(CONCAT('%', :angebotsnummer, '%'))
                      )
                 )
               """)
     List<Angebot> search(String kundenname,
               String bauvorhaben,
               java.time.LocalDate startDate,
               java.time.LocalDate endDate,
               String angebotsnummer);

     List<Angebot> findByAnlegedatumBetween(LocalDate startDatum, LocalDate endDatum);

     @Query("""
               SELECT DISTINCT function('YEAR', a.anlegedatum)
               FROM Angebot a
               WHERE a.anlegedatum IS NOT NULL
               ORDER BY function('YEAR', a.anlegedatum) DESC
               """)
     List<Integer> findDistinctAnlegedatumJahre();

     /**
      * Findet Angebote wo die Email in angebot_kunden_emails ODER kunden_emails
      * vorkommt.
      */
     @Query(value = """
               SELECT DISTINCT a.* FROM angebot a
               LEFT JOIN kunde k ON a.kunde_id = k.id
               WHERE EXISTS (SELECT 1 FROM angebot_kunden_emails ake WHERE ake.angebot_id = a.id AND lower(ake.email) = lower(:email))
                  OR EXISTS (SELECT 1 FROM kunden_emails ke WHERE ke.kunden_id = k.id AND lower(ke.email) = lower(:email))
               """, nativeQuery = true)
     List<Angebot> findByKundenEmail(@org.springframework.data.repository.query.Param("email") String email);

     @Query("""
               SELECT DISTINCT a FROM Angebot a
               LEFT JOIN FETCH a.kunde k
               LEFT JOIN k.kundenEmails e
               LEFT JOIN a.kundenEmails ae
               WHERE LOWER(a.bauvorhaben) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.kundennummer) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.ansprechspartner) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.telefon) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.mobiltelefon) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(k.ort) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(e) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(ae) LIKE LOWER(CONCAT('%', :query, '%'))
               """)
     List<Angebot> searchByBauvorhabenOrKundeOrEmail(
               @org.springframework.data.repository.query.Param("query") String query);
}
