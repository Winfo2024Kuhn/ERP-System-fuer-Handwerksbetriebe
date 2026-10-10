package org.example.kalkulationsprogramm.repository;

/** Teil von {@link SpamTokenCountRepository} mit eigenem (PostgreSQL-)SQL. */
public interface SpamTokenCountRepositoryErweiterung {

    /** Legt das Token an oder erhoeht seine Zaehler - atomar, ohne Race bei parallelen Importen. */
    void upsertToken(String token, int spamIncrement, int hamIncrement);
}
