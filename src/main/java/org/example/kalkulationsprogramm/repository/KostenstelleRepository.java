package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KostenstelleRepository extends JpaRepository<Kostenstelle, Long> {

    // Als @Query statt abgeleitet: nur so laesst sich festlegen, wohin leere Werte sortieren
    // (wie bisher: aufsteigend zuerst, absteigend zuletzt) - siehe DatenbankKompatibilitaetSortierungTest
    @Query("SELECT k FROM FirmaKostenstelle k WHERE k.aktiv = true ORDER BY k.sortierung ASC NULLS FIRST")
    List<Kostenstelle> findByAktivTrueOrderBySortierungAsc();

    List<Kostenstelle> findByTypAndAktivTrue(KostenstellenTyp typ);

    Optional<Kostenstelle> findByBezeichnung(String bezeichnung);

    /**
     * Findet alle Gemeinkosten-Kostenstellen.
     */
    default List<Kostenstelle> findGemeinkosten() {
        return findByTypAndAktivTrue(KostenstellenTyp.GEMEINKOSTEN);
    }

    /**
     * Findet alle Lager-Kostenstellen (Investitionen).
     */
    default List<Kostenstelle> findLager() {
        return findByTypAndAktivTrue(KostenstellenTyp.LAGER);
    }
}
