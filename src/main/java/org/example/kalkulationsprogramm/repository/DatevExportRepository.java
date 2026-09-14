package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @RequiredArgsConstructor
public class DatevExportRepository {
 private final EntityManager em;
 /** Called only inside the export's dedicated REQUIRES_NEW transaction. */
 public DatevKonfiguration konfiguration() {
  em.clear(); // Do not reuse an older OSIV / first-level-cache snapshot.
  return em.find(DatevKonfiguration.class,1L,LockModeType.OPTIMISTIC);
 }
 public List<MonatsSaldo> salden(Set<Long> ids,Set<Integer> perioden) {
  return em.createQuery("select s from MonatsSaldo s where s.mitarbeiter.id in :ids and (s.jahr * 100 + s.monat) in :perioden",MonatsSaldo.class)
   .setParameter("ids",ids).setParameter("perioden",perioden).getResultList();
 }
 public void standSichern(MonatsSaldo saldo) { em.lock(saldo,LockModeType.OPTIMISTIC); }
 public List<DatevPersonalnummer> personalnummern(Set<Long> ids) {
  return em.createQuery("select n from DatevPersonalnummer n where n.mitarbeiterId in :ids",DatevPersonalnummer.class)
   .setParameter("ids",ids).getResultList();
 }
}
