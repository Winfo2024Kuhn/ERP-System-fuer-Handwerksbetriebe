package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnfrageRepository extends JpaRepository<Anfrage, Long> {
     List<Anfrage> findByProjektIdIn(List<Long> ids);

     List<Anfrage> findByKundeId(Long kundeId);

     List<Anfrage> findByKunde_KundennummerIgnoreCase(String kundennummer);

     @Query("select a.projekt.id, g.dokumentid from Anfrage a join a.dokumente g where a.projekt.id in :ids and g.geschaeftsdokumentart = 'Anfrage'")
     List<Object[]> findDokumentIdsByProjektIds(List<Long> ids);

     @Query("SELECT DISTINCT a FROM Anfrage a LEFT JOIN FETCH a.kunde k LEFT JOIN FETCH k.kundenEmails")
     List<Anfrage> findAllWithKundenEmails();

     @Query("""
               SELECT DISTINCT a FROM Anfrage a
               LEFT JOIN a.kunde k
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
                      :anfragesnummer IS NULL OR EXISTS (
                          SELECT d FROM AusgangsGeschaeftsDokument d
                          WHERE d.anfrage = a
                            AND d.typ = 'ANFRAGE'
                            AND LOWER(d.dokumentNummer) LIKE LOWER(CONCAT('%', :anfragesnummer, '%'))
                      )
                 )
               """)
     List<Anfrage> search(String kundenname,
               String bauvorhaben,
               java.time.LocalDate startDate,
               java.time.LocalDate endDate,
               String anfragesnummer);

     List<Anfrage> findByAnlegedatumBetween(LocalDate startDatum, LocalDate endDatum);

     @Query("""
               SELECT DISTINCT function('YEAR', a.anlegedatum)
               FROM Anfrage a
               WHERE a.anlegedatum IS NOT NULL
               ORDER BY function('YEAR', a.anlegedatum) DESC
               """)
     List<Integer> findDistinctAnlegedatumJahre();

     /**
      * Findet Anfragen wo die Email in anfrage_kunden_emails ODER kunden_emails
      * vorkommt.
      */
     @Query(value = """
               SELECT DISTINCT a.* FROM anfrage a
               LEFT JOIN kunde k ON a.kunde_id = k.id
               WHERE EXISTS (SELECT 1 FROM anfrage_kunden_emails ake WHERE ake.anfrage_id = a.id AND lower(ake.email) = lower(:email))
                  OR EXISTS (SELECT 1 FROM kunden_emails ke WHERE ke.kunden_id = k.id AND lower(ke.email) = lower(:email))
               """, nativeQuery = true)
     List<Anfrage> findByKundenEmail(@org.springframework.data.repository.query.Param("email") String email);

     @Query("""
               SELECT DISTINCT a FROM Anfrage a
               LEFT JOIN a.kunde k
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
     List<Anfrage> searchByBauvorhabenOrKundeOrEmail(
               @org.springframework.data.repository.query.Param("query") String query);
}
