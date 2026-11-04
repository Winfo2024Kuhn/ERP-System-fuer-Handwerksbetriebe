package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailBezug;

/**
 * Natives SQL des E-Mail-Reiters (PostgreSQL): Verläufe eines Projekts, einer Anfrage, eines Kunden
 * oder Lieferanten, sortiert, gefiltert und seitenweise in der Datenbank. Aufbau: {@link EmailReiterSql}.
 */
public interface EmailReiterRepositoryErweiterung {

    /**
     * Verläufe einer Seite, sortiert nach der angezeigten Mail ({@code sent_at DESC NULLS LAST}, dann
     * Wurzel-ID absteigend). Suche, Richtung und Postfach-Sichtbarkeit wirken vor dem Seitenschnitt.
     */
    List<EmailVerlaufZeile> verlaeufe(EmailReiterAbfrage abfrage);

    /** Anzahl der Verläufe zur Abfrage (Seite und Größe zählen nicht). */
    long zaehleVerlaeufe(EmailReiterAbfrage abfrage);

    /**
     * Kennzahlen der ganzen Verläufe (alle Mails ab der Wurzel, nicht nur die des Bereichs).
     *
     * @param sichtbarePostfachIds {@code null} = alle Mails zählen (Admin), sonst nur lesbare
     */
    List<EmailVerlaufKennzahl> kennzahlen(Collection<Long> wurzelIds, Set<Long> sichtbarePostfachIds);

    /** Nicht eingebettete Anhänge an Mails des Bereichs aus {@code abfrage} in diesen Verläufen. */
    List<EmailVerlaufAnhang> anhaenge(EmailReiterAbfrage abfrage, Collection<Long> wurzelIds);

    /** Gibt es den Datensatz (Projekt, Anfrage, Kunde, Lieferant) mit dieser ID? */
    boolean bezugVorhanden(EmailBezug bezug, long id);
}
