package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

/** Datenbankabhaengiger Teil von {@link MonatsSaldoRepository} (MySQL/PostgreSQL). */
public interface MonatsSaldoRepositoryErweiterung {

    /** Offene (nicht festgeschriebene) Monatsabschluesse je Monat vor {@code aktuellerMonat}. */
    List<MonatsSaldoRepository.OffenerMonat> findOffeneAbschlussMonate(LocalDate aktuellerMonat);
}
