package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ArtikelRepository extends JpaRepository<Artikel, Long>, JpaSpecificationExecutor<Artikel> {
    Optional<Artikel> findByEan(String ean);

    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer))")
    Optional<Artikel> findByExterneArtikelnummer(@Param("nummer") String nummer);

    /**
     * Die hoechsten automatischen Artikelnummern ("A-" und nur Ziffern), hoechste zuerst: laengere zuerst,
     * bei gleicher Laenge entspricht die Textsortierung der Zahl ("A-010000" vor "A-009999").
     * Von Hand vergebene Nummern wie "A-Schraube-M8" fallen durch das Muster heraus.
     */
    @Query("select a.artikelnummer from Artikel a "
            + "where function('regexp_like', a.artikelnummer, '^A-[0-9]{1,12}$') = true "
            + "order by length(a.artikelnummer) desc nulls last, a.artikelnummer desc nulls last")
    List<String> findeHoechsteAutomatischeArtikelnummern(Pageable pageable);

    /** Hat ein anderer Artikel schon diese Nummer (Groß-/Kleinschreibung egal)? */
    boolean existsByArtikelnummerIgnoreCaseAndIdNot(String artikelnummer, Long id);

    /** Ist die betriebseigene Artikelnummer schon vergeben (Groß-/Kleinschreibung egal)? */
    boolean existsByArtikelnummerIgnoreCase(String artikelnummer);

    /**
     * Laedt den Artikel mit einem PESSIMISTIC_WRITE-Lock auf die Zeile.
     *
     * <p>Dient als Serialisierungspunkt fuer das Ersetzen des Vorschaubilds in
     * {@code ArtikelDokumentService.ladeHoch}: Der Artikel existiert garantiert
     * schon (anders als das evtl. noch gar nicht vorhandene Vorschaubild-
     * Dokument, auf das sich also nicht sperren laesst). Zwei gleichzeitige
     * Vorschaubild-Uploads fuer denselben Artikel (Doppelklick, zwei Tabs,
     * Retry) serialisieren sich dadurch, statt beide eine eigene
     * VORSCHAUBILD-Zeile anzulegen. Gleiches Muster wie
     * {@code KundenZaehlerRepository.lockAndGet()}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artikel a where a.id = :id")
    Optional<Artikel> findByIdForUpdate(@Param("id") Long id);

    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer)) and n.lieferant.id = :lieferantId")
    Optional<Artikel> findByExterneArtikelnummerAndLieferantId(@Param("nummer") String nummer,
                                                               @Param("lieferantId") Long lieferantId);

    @Query("select distinct a.produktlinie from Artikel a left join a.artikelpreis ap where (ap is null or ap.lieferant.id <> :lieferantId) and a.produktlinie is not null")
    List<String> findDistinctProduktlinieExcludingLieferant(@Param("lieferantId") Long lieferantId);
}

