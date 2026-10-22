package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface KontaktRufnummerRepository extends JpaRepository<KontaktRufnummer, Long> {

    @Query("SELECT r FROM KontaktRufnummer r LEFT JOIN FETCH r.kunde LEFT JOIN FETCH r.lieferant LEFT JOIN FETCH r.steuerberater")
    List<KontaktRufnummer> findAllMitKontakt();

    List<KontaktRufnummer> findByKundeIdOrderByAngelegtAmAsc(Long kundeId);

    List<KontaktRufnummer> findByLieferantIdOrderByAngelegtAmAsc(Long lieferantId);

    boolean existsByKundeIdAndNummerNormalisiert(Long kundeId, String nummerNormalisiert);

    boolean existsByLieferantIdAndNummerNormalisiert(Long lieferantId, String nummerNormalisiert);

    boolean existsBySteuerberaterIdAndNummerNormalisiert(Long steuerberaterId, String nummerNormalisiert);

    @Modifying
    @Query("DELETE FROM KontaktRufnummer r WHERE r.kunde.id = :kundeId")
    int loescheAlleVonKunde(@Param("kundeId") Long kundeId);

    /**
     * Gemerkte Zusatznummern von Kunden für das FRITZ!Box-Telefonbuch (ohne gesperrte und
     * anonymisierte): je Zeile {@code [kundeId, name, anrede, nummerRoh]}.
     */
    @Query("SELECT k.id, k.name, k.anrede, r.nummerRoh FROM KontaktRufnummer r JOIN r.kunde k "
            + "WHERE k.anonymisiertAm IS NULL AND k.gesperrtAm IS NULL ORDER BY r.angelegtAm ASC, r.id ASC")
    List<Object[]> findeKundenNummernFuerTelefonbuch();

    /** Gemerkte Zusatznummern aktiver Lieferanten: je Zeile {@code [lieferantId, lieferantenname, nummerRoh]}. */
    @Query("SELECT l.id, l.lieferantenname, r.nummerRoh FROM KontaktRufnummer r JOIN r.lieferant l "
            + "WHERE (l.istAktiv IS NULL OR l.istAktiv = true) ORDER BY r.angelegtAm ASC, r.id ASC")
    List<Object[]> findeLieferantenNummernFuerTelefonbuch();

    /** Gemerkte Zusatznummern aktiver, nicht abgelaufener Kanzleien: je Zeile {@code [steuerberaterId, name, nummerRoh]}. */
    @Query("SELECT s.id, s.name, r.nummerRoh FROM KontaktRufnummer r JOIN r.steuerberater s "
            + "WHERE s.aktiv = true AND (s.gueltigBis IS NULL OR s.gueltigBis >= :heute) ORDER BY r.angelegtAm ASC, r.id ASC")
    List<Object[]> findeSteuerberaterNummernFuerTelefonbuch(@Param("heute") LocalDate heute);
}
