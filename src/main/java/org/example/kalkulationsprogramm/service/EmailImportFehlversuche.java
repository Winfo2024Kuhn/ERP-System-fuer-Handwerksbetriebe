package org.example.kalkulationsprogramm.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merkt sich, wie oft der Import einer Nachricht gescheitert ist. Der Abruf läuft
 * alle zwei Minuten über alle Nachrichten eines Ordners; ohne diese Grenze würde
 * eine Mail, die sich gar nicht speichern lässt, endlos neu versucht und das Log
 * füllen. Nach {@link #MAX_VERSUCHE} Fehlschlägen wird sie bis zum nächsten
 * Neustart übersprungen – danach bekommt sie wieder ein paar Versuche (z. B.
 * nach einem Update, das den Fehler behebt).
 *
 * <p>Schlüssel ist Postfach, Ordner, UIDVALIDITY und UID – also keine
 * personenbezogenen Daten.
 */
class EmailImportFehlversuche {

    static final int MAX_VERSUCHE = 3;

    private final Map<String, Integer> fehlschlaege = new ConcurrentHashMap<>();

    static String schluessel(Long postfachId, String ordner, long uidValidity, long uid) {
        return postfachId + "|" + ordner + "|" + uidValidity + "|" + uid;
    }

    /** Hat die Nachricht ihre Versuche aufgebraucht? */
    boolean ueberspringen(String schluessel) {
        return fehlschlaege.getOrDefault(schluessel, 0) >= MAX_VERSUCHE;
    }

    /** Zählt einen Fehlschlag und liefert die neue Anzahl. */
    int fehlgeschlagen(String schluessel) {
        return fehlschlaege.merge(schluessel, 1, Integer::sum);
    }

    /** Die Nachricht ging durch – frühere Fehlschläge zählen nicht mehr. */
    void erfolgreich(String schluessel) {
        fehlschlaege.remove(schluessel);
    }
}
