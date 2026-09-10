package org.example.kalkulationsprogramm.repository;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface MonatsabschlussUebersichtRepository extends Repository<MonatsSaldo, Long> {
    interface Person { Long getId(); String getVorname(); String getNachname(); }
    interface Abteilung { Long getMitarbeiterId(); Long getAbteilungId(); }
    @Query("""
        select m.id as id, m.vorname as vorname, m.nachname as nachname
        from Mitarbeiter m
        where m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH
          and (:id is null or m.id = :id)
          and (:abteilung is null or exists (select a.id from Mitarbeiter x join x.abteilungen a where x.id = m.id and a.id = :abteilung))
          and (m.istGeschaeftsfuehrer is null or m.istGeschaeftsfuehrer = false)
          and (
            (m.aktiv = true and m.fuehrtZeitkonto = true
             and (m.eintrittsdatum is null or m.eintrittsdatum <= :bisDatum))
            or exists (select 1 from MonatsSaldo ms where ms.mitarbeiter.id = m.id and (ms.jahr * 12 + ms.monat) between :vonYM and :bisYM and ms.festgeschrieben = true)
            or exists (select 1 from Zeitbuchung zb where zb.mitarbeiter.id = m.id and zb.startZeit >= :vonDT and zb.startZeit <= :bisDT)
            or exists (select 1 from Abwesenheit ab where ab.mitarbeiter.id = m.id and ab.datum >= :vonDatum and ab.datum <= :bisDatum)
          )
        order by m.nachname, m.vorname, m.id
    """)
    List<Person> personen(
        @Param("id") Long id,
        @Param("abteilung") Long abteilung,
        @Param("vonDatum") java.time.LocalDate vonDatum,
        @Param("bisDatum") java.time.LocalDate bisDatum,
        @Param("vonDT") java.time.LocalDateTime vonDT,
        @Param("bisDT") java.time.LocalDateTime bisDT,
        @Param("vonYM") int vonYM,
        @Param("bisYM") int bisYM,
        Pageable limit
    );
    @Query("select m.id as mitarbeiterId, a.id as abteilungId from Mitarbeiter m join m.abteilungen a where m.id in :ids order by a.id")
    List<Abteilung> abteilungen(@Param("ids") List<Long> ids);
    @Query("select s from MonatsSaldo s where s.mitarbeiter.id in :ids and (s.jahr*12+s.monat) between :von and :bis")
    List<MonatsSaldo> salden(@Param("ids") List<Long> ids, @Param("von") int von, @Param("bis") int bis);
}
