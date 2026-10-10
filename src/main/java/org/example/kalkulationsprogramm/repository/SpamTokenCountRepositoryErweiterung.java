package org.example.kalkulationsprogramm.repository;

/** Datenbankabhaengiger Teil von {@link SpamTokenCountRepository} (MySQL/PostgreSQL). */
public interface SpamTokenCountRepositoryErweiterung {

    /** Legt das Token an oder erhoeht seine Zaehler - atomar, ohne Race bei parallelen Importen. */
    void upsertToken(String token, int spamIncrement, int hamIncrement);
}
