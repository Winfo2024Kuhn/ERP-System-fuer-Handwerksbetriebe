package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Zahlung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ZahlungRepository extends JpaRepository<Zahlung, Long> {

    /**
     * Findet alle Zahlungen zu einem Geschäftsdokument.
     */
    List<Zahlung> findByGeschaeftsdokumentIdOrderByZahlungsdatumDesc(Long geschaeftsdokumentId);

    /**
     * Berechnet die Summe aller Zahlungen zu einem Dokument.
     */
    @Query("SELECT COALESCE(SUM(z.betrag), 0) FROM Zahlung z WHERE z.geschaeftsdokument.id = :dokumentId")
    BigDecimal summeZahlungenByDokumentId(@Param("dokumentId") Long dokumentId);
}
