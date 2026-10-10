package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;

/** Teil von {@link SeenSenderDomainRepository} mit eigenem (PostgreSQL-)SQL. */
public interface SeenSenderDomainRepositoryErweiterung {

    /**
     * Markiert eine Domain als "schon gesehen". Beim ersten Auftreten wird ein
     * Eintrag mit emailCount=1 angelegt; bei spaeteren Mails wird nur der
     * Zaehler erhoeht (first_seen bleibt unveraendert). Der Upsert vermeidet
     * einen Race zwischen existsByDomain-Check und Insert bei parallelen Imports.
     */
    void upsertSeen(String domain, LocalDateTime firstSeen);
}
