package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/** Teil von {@link EmailGelesenRepository} mit eigenem (PostgreSQL-)SQL. */
public interface EmailGelesenRepositoryErweiterung {

    /** Welche dieser Mails hat der Benutzer schon gelesen? */
    Set<Long> geleseneAus(Long benutzerId, Collection<Long> emailIds);

    /**
     * Merkt die Mails für den Benutzer als gelesen – in der Transaktion des Aufrufers. Schon
     * gemerkte bleiben, wie sie sind, auch wenn eine zweite Anfrage (Doppelklick, zweiter Tab)
     * dieselbe Mail gleichzeitig merkt.
     */
    void alsGelesenMerken(Long benutzerId, Collection<Long> emailIds, LocalDateTime jetzt);
}
