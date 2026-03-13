package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Geschaeftsdokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GeschaeftsdokumentRepository extends JpaRepository<Geschaeftsdokument, Long> {

        /**
         * Findet alle Dokumente eines Projekts, sortiert nach Datum.
         */
        @Query("SELECT DISTINCT g FROM Geschaeftsdokument g LEFT JOIN FETCH g.zahlungen LEFT JOIN FETCH g.projekt LEFT JOIN FETCH g.kunde LEFT JOIN FETCH g.vorgaengerDokument WHERE g.projekt.id = :projektId ORDER BY g.datum DESC")
        List<Geschaeftsdokument> findByProjektIdOrderByDatumDesc(@Param("projektId") Long projektId);

        /**
         * Findet alle Dokumente eines Kunden, sortiert nach Datum.
         */
        @Query("SELECT DISTINCT g FROM Geschaeftsdokument g LEFT JOIN FETCH g.zahlungen LEFT JOIN FETCH g.projekt LEFT JOIN FETCH g.kunde LEFT JOIN FETCH g.vorgaengerDokument WHERE g.kunde.id = :kundeId ORDER BY g.datum DESC")
        List<Geschaeftsdokument> findByKundeIdOrderByDatumDesc(@Param("kundeId") Long kundeId);

        /**
         * Findet alle Dokumente eines bestimmten Dokumenttyps.
         */
        List<Geschaeftsdokument> findByDokumenttypOrderByDatumDesc(Dokumenttyp dokumenttyp);

        /**
         * Findet alle Nachfolger eines Dokuments.
         */
        List<Geschaeftsdokument> findByVorgaengerDokumentId(Long vorgaengerId);

        /**
         * Findet ein Dokument anhand der Dokumentnummer.
         */
        Optional<Geschaeftsdokument> findByDokumentNummer(String dokumentNummer);

        /**
         * Findet alle unbezahlten Rechnungen (offene Posten) basierend auf
         * Dokumenttypen.
         */
        @Query("SELECT g FROM Geschaeftsdokument g WHERE g.dokumenttyp IN :typen " +
                        "AND g.storniert = false " +
                        "AND (SELECT COALESCE(SUM(z.betrag), 0) FROM Zahlung z WHERE z.geschaeftsdokument = g) < g.betragBrutto")
        List<Geschaeftsdokument> findOffeneRechnungen(@Param("typen") List<Dokumenttyp> typen);

        /**
         * Findet die höchste Dokumentnummer für ein Jahr und Kürzel.
         */
        @Query("SELECT MAX(CAST(SUBSTRING(g.dokumentNummer, LENGTH(:prefix) + 1) AS int)) " +
                        "FROM Geschaeftsdokument g WHERE g.dokumentNummer LIKE :prefix%")
        Optional<Integer> findMaxNummer(@Param("prefix") String prefix);
}
