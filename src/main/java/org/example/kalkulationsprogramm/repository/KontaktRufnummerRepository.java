package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
