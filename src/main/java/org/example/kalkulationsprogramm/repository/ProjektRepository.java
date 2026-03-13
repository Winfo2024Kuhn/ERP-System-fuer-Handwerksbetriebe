package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjektRepository extends JpaRepository<Projekt, Long>, JpaSpecificationExecutor<Projekt> {
        @Query("SELECT COUNT(p) FROM Projekt p JOIN p.projektProduktkategorien pk WHERE pk.produktkategorie.id = :kategorieId")
        long countByProduktkategorieId(@Param("kategorieId") Long produktkategorieId);

        @Query("SELECT pk.produktkategorie.id, COUNT(DISTINCT p.id) FROM Projekt p JOIN p.projektProduktkategorien pk GROUP BY pk.produktkategorie.id")
        List<Object[]> getProjectCountsGroupedByKategorie();

        @Query("SELECT pk.produktkategorie.id, p.id FROM Projekt p JOIN p.projektProduktkategorien pk")
        List<Object[]> getKategorieProjektPairs();

        @Query("SELECT p FROM Projekt p JOIN p.projektProduktkategorien pk WHERE pk.produktkategorie.id = :kategorieId")
        List<Projekt> findByProduktkategorieId(@Param("kategorieId") Long produktkategorieId);

        @Query("SELECT p FROM Projekt p JOIN p.projektProduktkategorien pk WHERE pk.produktkategorie.id = :kategorieId AND p.abschlussdatum BETWEEN :start AND :end")
        List<Projekt> findByProduktkategorieIdAndAbschlussdatumBetween(@Param("kategorieId") Long kategorieId,
                        @Param("start") LocalDate start, @Param("end") LocalDate end);

        @Query("""
                        SELECT DISTINCT p FROM Projekt p
                        LEFT JOIN p.kundenId k
                        LEFT JOIN k.kundenEmails e
                        LEFT JOIN p.kundenEmails pe
                        WHERE LOWER(p.bauvorhaben) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.kundennummer) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.ansprechspartner) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.telefon) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.mobiltelefon) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.ort) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(e) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(pe) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        List<Projekt> searchByBauvorhabenOrKundeOrEmail(@Param("query") String query);

        @Query("SELECT DISTINCT p FROM Projekt p LEFT JOIN FETCH p.zeitbuchungen JOIN p.projektProduktkategorien pk WHERE pk.produktkategorie.id IN :kategorieIds")
        List<Projekt> findByProduktkategorieIds(@Param("kategorieIds") List<Long> kategorieIds);

        @Query("SELECT DISTINCT p FROM Projekt p LEFT JOIN FETCH p.zeitbuchungen JOIN p.projektProduktkategorien pk WHERE pk.produktkategorie.id IN :kategorieIds AND p.abschlussdatum BETWEEN :start AND :end")
        List<Projekt> findByProduktkategorieIdsAndAbschlussdatumBetween(@Param("kategorieIds") List<Long> kategorieIds,
                        @Param("start") LocalDate start, @Param("end") LocalDate end);

        List<Projekt> findAll(Specification<Projekt> spec, Sort sort);

        @EntityGraph(attributePaths = "zeitbuchungen")
        Optional<Projekt> findWithZeitenInProjektById(Long id);
        // Hier können später benutzerdefinierte Abfragen hinzugefügt werden,
        // z.B. um Projekte nach einer Produktkategorie zu finden.

        @Query("SELECT p.id as id, k.id as kundenIdValue, k.kundenEmails as kundenEmails FROM Projekt p LEFT JOIN p.kundenId k")
        List<IdEmailOnly> findIdsAndKundenEmails();

        @Query("SELECT DISTINCT p FROM Projekt p LEFT JOIN FETCH p.kundenId k LEFT JOIN FETCH k.kundenEmails")
        List<Projekt> findAllWithKundenEmails();

        List<Projekt> findByKundenId_Id(Long kundenId);

        List<Projekt> findByAnlegedatumBetween(LocalDate start, LocalDate ende);

        interface IdEmailOnly {
                Long getId();

                Long getKundenIdValue();

                List<String> getKundenEmails();
        }

        /**
         * Findet die höchste Auftragsnummer, die mit einem bestimmten Präfix beginnt.
         * Beispiel: Präfix "2024/12/" findet alle Auftragsnummern wie "2024/12/00001",
         * "2024/12/00002" etc.
         */
        @Query("SELECT p.auftragsnummer FROM Projekt p WHERE p.auftragsnummer LIKE CONCAT(:prefix, '%') ORDER BY p.auftragsnummer DESC")
        List<String> findAuftragsnummernByPrefix(@Param("prefix") String prefix);

        /**
         * Prüft ob eine Auftragsnummer bereits existiert.
         */
        boolean existsByAuftragsnummer(String auftragsnummer);

        /**
         * Prüft ob eine Auftragsnummer bereits von einem anderen Projekt verwendet
         * wird.
         */
        @Query("SELECT COUNT(p) > 0 FROM Projekt p WHERE p.auftragsnummer = :auftragsnummer AND p.id != :projektId")
        boolean existsByAuftragsnummerAndIdNot(@Param("auftragsnummer") String auftragsnummer,
                        @Param("projektId") Long projektId);

        Optional<Projekt> findByBauvorhaben(String bauvorhaben);

        /**
         * Findet Projekte wo die Email in kunden_emails ODER projekt_kunden_emails
         * vorkommt.
         */
        @Query(value = """
                        SELECT DISTINCT p.* FROM projekt p
                        LEFT JOIN kunde k ON p.kunden_id = k.id
                        WHERE EXISTS (SELECT 1 FROM kunden_emails ke WHERE ke.kunden_id = k.id AND lower(ke.email) = lower(:email))
                           OR EXISTS (SELECT 1 FROM projekt_kunden_emails pke WHERE pke.projekt_id = p.id AND lower(pke.email) = lower(:email))
                        """, nativeQuery = true)
        List<Projekt> findByKundenEmail(@Param("email") String email);

        /**
         * Schlanke Projektion für schnelle Dropdown-Suche.
         * Lädt nur id, bauvorhaben, auftragsnummer und kunde - keine E-Mails oder Kilogramm.
         */
        interface ProjektSimple {
                Long getId();
                String getBauvorhaben();
                String getAuftragsnummer();
                String getKunde();
                boolean isAbgeschlossen();
        }

        /**
         * Schnelle Projektsuche für Dropdowns. Gibt nur die wichtigsten Felder zurück.
         */
        @Query("""
                        SELECT p.id as id, p.bauvorhaben as bauvorhaben, p.auftragsnummer as auftragsnummer, k.name as kunde, p.abgeschlossen as abgeschlossen
                        FROM Projekt p LEFT JOIN p.kundenId k
                        WHERE (:query IS NULL OR :query = ''
                           OR LOWER(p.bauvorhaben) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.kundennummer) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(k.ansprechspartner) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(p.auftragsnummer) LIKE LOWER(CONCAT('%', :query, '%')))
                        ORDER BY p.anlegedatum DESC
                        """)
        List<ProjektSimple> findSimpleByQuery(@Param("query") String query,
                        org.springframework.data.domain.Pageable pageable);
}
