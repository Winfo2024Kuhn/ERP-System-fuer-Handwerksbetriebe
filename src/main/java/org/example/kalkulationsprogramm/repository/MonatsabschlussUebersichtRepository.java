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
    @Query("select m.id as id, m.vorname as vorname, m.nachname as nachname from Mitarbeiter m where m.art = org.example.kalkulationsprogramm.domain.MitarbeiterArt.MENSCH and (:id is null or m.id=:id) and (:abteilung is null or exists (select a.id from Mitarbeiter x join x.abteilungen a where x.id=m.id and a.id=:abteilung)) order by m.nachname, m.vorname, m.id")
    List<Person> personen(@Param("id") Long id, @Param("abteilung") Long abteilung, Pageable limit);
    @Query("select m.id as mitarbeiterId, a.id as abteilungId from Mitarbeiter m join m.abteilungen a where m.id in :ids order by a.id")
    List<Abteilung> abteilungen(@Param("ids") List<Long> ids);
    @Query("select s from MonatsSaldo s where s.mitarbeiter.id in :ids and (s.jahr*12+s.monat) between :von and :bis")
    List<MonatsSaldo> salden(@Param("ids") List<Long> ids, @Param("von") int von, @Param("bis") int bis);
}
