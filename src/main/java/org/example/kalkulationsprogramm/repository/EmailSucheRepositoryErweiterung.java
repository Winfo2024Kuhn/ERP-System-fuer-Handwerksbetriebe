package org.example.kalkulationsprogramm.repository;

import java.util.List;

/** Natives SQL der E-Mail-Suche (PostgreSQL, Trigramm-Index auf {@code email.suchtext}). */
public interface EmailSucheRepositoryErweiterung {

    /**
     * IDs der Treffer einer Seite, sortiert nach {@code sent_at DESC NULLS LAST, id DESC}.
     * Ordner, Sichtbarkeit und Suchwörter wirken in der Abfrage – vor dem Seitenschnitt.
     */
    List<Long> sucheIds(EmailSuchAbfrage abfrage);
}
