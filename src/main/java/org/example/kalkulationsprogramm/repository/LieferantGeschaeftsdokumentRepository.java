package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LieferantGeschaeftsdokumentRepository extends JpaRepository<LieferantGeschaeftsdokument, Long> {

        /**
         * Findet Geschäftsdaten anhand der Dokumentnummer.
         */
        Optional<LieferantGeschaeftsdokument> findByDokumentNummer(String dokumentNummer);

        /**
         * Findet Geschäftsdaten mit passender Referenznummer beim angegebenen
         * Lieferanten.
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "WHERE gd.dokument.lieferant.id = :lieferantId " +
                        "AND gd.dokumentNummer = :referenzNummer")
        List<LieferantGeschaeftsdokument> findByLieferantIdAndDokumentNummer(
                        @Param("lieferantId") Long lieferantId,
                        @Param("referenzNummer") String referenzNummer);

        /**
         * Prüft ob ein Dokument mit der Dokumentnummer und Bruttobetrag bereits beim
         * Lieferanten existiert.
         */
        @Query("SELECT CASE WHEN COUNT(gd) > 0 THEN true ELSE false END " +
                        "FROM LieferantGeschaeftsdokument gd " +
                        "WHERE gd.dokument.lieferant.id = :lieferantId " +
                        "AND gd.dokumentNummer = :dokumentNummer " +
                        "AND gd.betragBrutto = :betragBrutto")
        boolean existsByLieferantIdAndDokumentNummerAndBetragBrutto(
                        @Param("lieferantId") Long lieferantId,
                        @Param("dokumentNummer") String dokumentNummer,
                        @Param("betragBrutto") java.math.BigDecimal betragBrutto);

        /**
         * Findet alle Eingangsrechnungen die noch nicht bezahlt sind.
         * Inkludiert RECHNUNG und EINGANGSRECHNUNG Typen.
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ IN (org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT) "
                        +
                        "AND gd.bezahlt = false " +
                        "ORDER BY gd.zahlungsziel ASC NULLS LAST, gd.dokumentDatum ASC")
        List<LieferantGeschaeftsdokument> findAllOffeneEingangsrechnungen();

        /**
         * Findet alle offenen Eingangsrechnungen die genehmigt sind (für Buchhaltung).
         * Inkludiert RECHNUNG und EINGANGSRECHNUNG Typen.
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ IN (org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT) "
                        +
                        "AND gd.bezahlt = false " +
                        "AND gd.genehmigt = true " +
                        "ORDER BY gd.zahlungsziel ASC NULLS LAST, gd.dokumentDatum ASC")
        List<LieferantGeschaeftsdokument> findAllOffeneGenehmigte();

        /**
         * Findet alle Eingangsrechnungen (bezahlt und unbezahlt) für Übersicht.
         * Inkludiert RECHNUNG und EINGANGSRECHNUNG Typen.
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ IN (org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT) "
                        +
                        "ORDER BY gd.dokumentDatum DESC")
        List<LieferantGeschaeftsdokument> findAllEingangsrechnungen();

        /**
         * Findet alle genehmigten Eingangsrechnungen (für Buchhaltung).
         * Inkludiert RECHNUNG und EINGANGSRECHNUNG Typen.
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ IN (org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT) "
                        +
                        "AND gd.genehmigt = true " +
                        "ORDER BY gd.dokumentDatum DESC")
        List<LieferantGeschaeftsdokument> findAllGenehmigte();

        /**
         * Findet alle Lieferanten-Rechnungen in einem Datumsbereich (für
         * Erfolgsanalyse).
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ IN (org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT) "
                        +
                        "AND gd.dokumentDatum BETWEEN :startDate AND :endDate")
        List<LieferantGeschaeftsdokument> findRechnungenByDatumBetween(
                        @Param("startDate") java.time.LocalDate startDate,
                        @Param("endDate") java.time.LocalDate endDate);

        /**
         * Findet alle bezahlten Lieferanten-Rechnungen in einem Zeitraum
         * basierend auf dem Bezahlt-Datum (für Erfolgsanalyse - Kosten
         * werden im Monat der tatsächlichen Zahlung angezeigt).
         */
        @Query("SELECT gd FROM LieferantGeschaeftsdokument gd " +
                        "JOIN gd.dokument d " +
                        "WHERE d.typ = org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG " +
                        "AND gd.bezahlt = true " +
                        "AND gd.bezahltAm BETWEEN :startDate AND :endDate")
        List<LieferantGeschaeftsdokument> findBezahlteRechnungenByBezahltAmBetween(
                        @Param("startDate") java.time.LocalDate startDate,
                        @Param("endDate") java.time.LocalDate endDate);

        /**
         * Berechnet die durchschnittliche Lieferzeit in Tagen für einen Lieferanten
         * basierend auf Auftragsbestätigungen (dokumentDatum -> liefertermin).
         * Native Query weil DATEDIFF MySQL-spezifisch ist.
         */
        @Query(value = "SELECT AVG(DATEDIFF(gd.liefertermin, gd.dokument_datum)) " +
                        "FROM lieferant_geschaeftsdokument gd " +
                        "JOIN lieferant_dokument d ON gd.id = d.id " +
                        "WHERE d.lieferant_id = :lieferantId " +
                        "AND d.typ = 'AUFTRAGSBESTAETIGUNG' " +
                        "AND gd.dokument_datum IS NOT NULL " +
                        "AND gd.liefertermin IS NOT NULL", nativeQuery = true)
        Double calculateAverageLieferzeitByLieferantId(@Param("lieferantId") Long lieferantId);

        /**
         * Zählt die Anzahl der Bestellungen (Auftragsbestätigungen) für einen
         * Lieferanten.
         */
        @Query(value = "SELECT COUNT(*) FROM lieferant_dokument d " +
                        "WHERE d.lieferant_id = :lieferantId " +
                        "AND d.typ = 'AUFTRAGSBESTAETIGUNG'", nativeQuery = true)
        Long countBestellungenByLieferantId(@Param("lieferantId") Long lieferantId);

        /**
         * Berechnet die Gesamtkosten (Summe aller Rechnungen Netto) für einen
         * Lieferanten.
         */
        @Query(value = "SELECT COALESCE(SUM(gd.betrag_netto), 0) " +
                        "FROM lieferant_geschaeftsdokument gd " +
                        "JOIN lieferant_dokument d ON gd.id = d.id " +
                        "WHERE d.lieferant_id = :lieferantId " +
                        "AND d.typ = 'RECHNUNG'", nativeQuery = true)
        Double sumGesamtkostenByLieferantId(@Param("lieferantId") Long lieferantId);

        /**
         * Lieferantenkosten pro Jahr (für Erfolgsanalyse).
         * Gibt Jahreszahl und Summe Netto aller Rechnungen zurück.
         */
        @Query(value = "SELECT YEAR(gd.dokument_datum) as jahr, " +
                        "COUNT(DISTINCT d.id) as bestellungen, " +
                        "COALESCE(SUM(gd.betrag_netto), 0) as netto " +
                        "FROM lieferant_geschaeftsdokument gd " +
                        "JOIN lieferant_dokument d ON gd.id = d.id " +
                        "WHERE d.typ = 'RECHNUNG' " +
                        "AND gd.dokument_datum IS NOT NULL " +
                        "GROUP BY YEAR(gd.dokument_datum) " +
                        "ORDER BY YEAR(gd.dokument_datum) DESC", nativeQuery = true)
        java.util.List<Object[]> getLieferantenkostenProJahr();

        /**
         * Lieferanten-Performance: Statistik pro Lieferant (Bestellungen und Summe
         * Netto).
         */
        @Query(value = "SELECT l.lieferantenname, " +
                        "COUNT(CASE WHEN d.typ = 'AUFTRAGSBESTAETIGUNG' THEN 1 END) as bestellungen, " +
                        "SUM(CASE WHEN d.typ = 'RECHNUNG' THEN gd.betrag_netto ELSE 0 END) as netto " +
                        "FROM lieferanten l " +
                        "LEFT JOIN lieferant_dokument d ON d.lieferant_id = l.id " +
                        "LEFT JOIN lieferant_geschaeftsdokument gd ON gd.id = d.id " +
                        "GROUP BY l.id, l.lieferantenname " +
                        "HAVING COUNT(d.id) > 0 " +
                        "ORDER BY netto DESC", nativeQuery = true)
        java.util.List<Object[]> getLieferantenPerformance();

        /**
         * Lieferanten-Performance gefiltert nach Jahr und optionalem Monat.
         */
        @Query(value = "SELECT l.lieferantenname, " +
                        "COUNT(CASE WHEN d.typ = 'AUFTRAGSBESTAETIGUNG' THEN 1 END) as bestellungen, " +
                        "SUM(CASE WHEN d.typ = 'RECHNUNG' THEN gd.betrag_netto ELSE 0 END) as netto " +
                        "FROM lieferanten l " +
                        "LEFT JOIN lieferant_dokument d ON d.lieferant_id = l.id " +
                        "LEFT JOIN lieferant_geschaeftsdokument gd ON gd.id = d.id " +
                        "WHERE (gd.dokument_datum IS NULL OR " +
                        "       (YEAR(gd.dokument_datum) = :jahr AND (:monat IS NULL OR MONTH(gd.dokument_datum) = :monat))) " +
                        "GROUP BY l.id, l.lieferantenname " +
                        "HAVING SUM(CASE WHEN d.typ = 'RECHNUNG' THEN gd.betrag_netto ELSE 0 END) > 0 " +
                        "ORDER BY netto DESC", nativeQuery = true)
        java.util.List<Object[]> getLieferantenPerformanceFiltered(
                        @Param("jahr") Integer jahr,
                        @Param("monat") Integer monat);

        /**
         * Prüft ob ein Dokument mit der Dokumentnummer bereits beim Lieferanten existiert.
         * Strikte Prüfung: Pro Lieferant + Dokumentnummer darf nur 1 Dokument existieren.
         */
        @Query("SELECT CASE WHEN COUNT(gd) > 0 THEN true ELSE false END " +
                        "FROM LieferantGeschaeftsdokument gd " +
                        "WHERE gd.dokument.lieferant.id = :lieferantId " +
                        "AND gd.dokumentNummer = :dokumentNummer")
        boolean existsByLieferantIdAndDokumentNummer(
                        @Param("lieferantId") Long lieferantId,
                        @Param("dokumentNummer") String dokumentNummer);

        /**
         * Findet alle Duplikate: dokumentNummer + lieferantId Kombinationen mit mehr als 1 Vorkommen.
         * Gibt die IDs gruppiert zurück – das älteste (kleinste ID) wird behalten, die anderen gelöscht.
         */
        @Query(value = "SELECT gd.id, gd.dokument_nummer, d.lieferant_id, l.lieferantenname " +
                        "FROM lieferant_geschaeftsdokument gd " +
                        "JOIN lieferant_dokument d ON gd.id = d.id " +
                        "JOIN lieferanten l ON d.lieferant_id = l.id " +
                        "WHERE gd.dokument_nummer IS NOT NULL " +
                        "AND (d.lieferant_id, gd.dokument_nummer) IN (" +
                        "   SELECT d2.lieferant_id, gd2.dokument_nummer " +
                        "   FROM lieferant_geschaeftsdokument gd2 " +
                        "   JOIN lieferant_dokument d2 ON gd2.id = d2.id " +
                        "   WHERE gd2.dokument_nummer IS NOT NULL " +
                        "   GROUP BY d2.lieferant_id, gd2.dokument_nummer " +
                        "   HAVING COUNT(*) > 1" +
                        ") " +
                        "ORDER BY d.lieferant_id, gd.dokument_nummer, gd.id", nativeQuery = true)
        java.util.List<Object[]> findAllDuplicates();
}
