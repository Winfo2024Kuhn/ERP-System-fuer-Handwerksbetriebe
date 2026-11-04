package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.EmailBezug;

/**
 * Feste SQL-Bausteine des E-Mail-Reiters (natives SQL auf {@code email e}, PostgreSQL). Nutzereingaben
 * stehen hier nie drin – nur benannte Parameter ({@value #BEZUG_ID}, {@value #RICHTUNG}, {@code w0…},
 * {@value #WURZELN}, {@link EmailSichtbarkeitSql#PARAMETER}).
 *
 * <p><b>Verlauf:</b> die Kette über {@code parent_email_id} bis zur obersten Mail (Wurzel), auch wenn
 * die Wurzel selbst nicht zum Bereich gehört. Der Weg nach oben läuft per {@code WITH RECURSIVE} und
 * bricht nach {@value #MAX_TIEFE} Schritten ab – so endet auch ein Kreis in der Kette (A antwortet auf
 * B, B auf A). Erreicht ein Weg keine Mail ohne Vorgänger, ist die Wurzel die kleinste ID der letzten
 * {@value #KREIS_FENSTER} Schritte: bei einem Kreis (bis {@value #KREIS_FENSTER} Mails lang) genau die
 * kleinste ID im Kreis, für jede Mail des Kreises dieselbe.</p>
 *
 * <p><b>Abfrageplan:</b> PostgreSQL schätzt die Zwischentabelle einer Rekursion auf ein Vielfaches des
 * Starts und nähme für einen Join im Rekursionsschritt einen Hash-Join über alle Mails – je Tiefe einmal.
 * Deshalb holt der Schritt nach oben den Vorgänger per Unterabfrage auf den Primärschlüssel, der Schritt
 * nach unten die Antworten per {@code LATERAL … OFFSET 0} (wird nicht aufgelöst, also je Zeile über
 * den Index auf {@code parent_email_id}). Geprüft mit {@code EXPLAIN} in
 * {@code DatenbankKompatibilitaetEmailReiterTest}.</p>
 */
final class EmailReiterSql {

    static final String BEZUG_ID = "bezugId";
    static final String RICHTUNG = "richtung";
    static final String WURZELN = "wurzeln";
    static final int MAX_TIEFE = 200;
    static final int KREIS_FENSTER = MAX_TIEFE / 2;

    /**
     * Kunde: Mails seiner Projekte, seiner Anfragen, der Anfragen seiner Projekte und der Anfragen mit seiner
     * Kundennummer – als einzelne Teile, die {@code UNION ALL} verbindet. Jeder Teil kommt über einen Index
     * ({@code idx_email_projekt}, {@code idx_email_anfrage}); ein {@code OR} über die Teile würde alle Mails
     * durchlaufen ({@code DatenbankKompatibilitaetEmailReiterTest#abfragenNutzenIndizes}). Dieselbe Mail darf in
     * mehreren Teilen vorkommen: {@link #zuordnung} fasst je Mail zusammen.
     */
    private static final List<String> KUNDE = List.of(
            "FROM email e JOIN projekt p ON p.id = e.projekt_id WHERE p.kunden_id = :" + BEZUG_ID,
            "FROM email e JOIN anfrage a ON a.id = e.anfrage_id WHERE a.kunde_id = :" + BEZUG_ID,
            "FROM email e JOIN anfrage a ON a.id = e.anfrage_id JOIN projekt p ON p.id = a.projekt_id"
                    + " WHERE p.kunden_id = :" + BEZUG_ID,
            "FROM email e JOIN anfrage a ON a.id = e.anfrage_id JOIN kunde k2 ON k2.id = a.kunde_id"
                    + " JOIN kunde k1 ON LOWER(k2.kundennummer) = LOWER(k1.kundennummer)"
                    + " WHERE k1.id = :" + BEZUG_ID + " AND TRIM(k1.kundennummer) <> ''");

    private static final String UNZUSTELLBAR = "e.direction = 'OUT' AND e.zustell_status = 'UNZUSTELLBAR'";

    private EmailReiterSql() {
    }

    /**
     * Die Mails {@code e} des Bereichs als Teile {@code FROM … WHERE …}; an jeden Teil hängt der Aufrufer
     * seine Filter mit {@code AND} an und verbindet die Teile mit {@code UNION ALL}.
     */
    static List<String> bezugTeile(EmailBezug bezug) {
        return switch (bezug) {
            case PROJEKT -> List.of("FROM email e WHERE e.projekt_id = :" + BEZUG_ID);
            case ANFRAGE -> List.of("FROM email e WHERE e.anfrage_id = :" + BEZUG_ID);
            case LIEFERANT -> List.of("FROM email e WHERE e.lieferant_id = :" + BEZUG_ID);
            case KUNDE -> KUNDE;
        };
    }

    /** IDs der Mails des Bereichs (ggf. mehrfach). */
    static String bezugIds(EmailBezug bezug) {
        return bezugTeile(bezug).stream().map(teil -> "SELECT e.id " + teil).collect(Collectors.joining(" UNION ALL "));
    }

    /** Gibt es den Datensatz? Liefert eine Anzahl (0 oder 1). */
    static String vorhanden(EmailBezug bezug) {
        String tabelle = switch (bezug) {
            case PROJEKT -> "projekt";
            case ANFRAGE -> "anfrage";
            case KUNDE -> "kunde";
            case LIEFERANT -> "lieferanten";
        };
        return "SELECT COUNT(*) FROM " + tabelle + " WHERE id = :" + BEZUG_ID;
    }

    /**
     * {@code WITH RECURSIVE hoch …, zuordnung(email_id, sent_at, wurzel_id)}: jede Mail aus {@code anker}
     * (Abfrage mit den Spalten {@code id, parent_id, sent_at}, Mails dürfen mehrfach vorkommen) mit der
     * Wurzel ihres Verlaufs. {@code sent_at} reist mit, damit die Seite nicht noch einmal alle Mails
     * nachschlagen muss.
     */
    static String zuordnung(String anker) {
        return "WITH RECURSIVE hoch(start_id, start_sent_at, id, parent_id, tiefe) AS ("
                + " SELECT b.id, b.sent_at, b.id, b.parent_id, 0 FROM (" + anker + ") b"
                + " UNION ALL"
                + " SELECT h.start_id, h.start_sent_at, h.parent_id,"
                + " (SELECT p.parent_email_id FROM email p WHERE p.id = h.parent_id), h.tiefe + 1 FROM hoch h"
                + " WHERE h.parent_id IS NOT NULL AND h.tiefe < " + MAX_TIEFE + "),"
                + " zuordnung(email_id, sent_at, wurzel_id) AS ("
                + " SELECT h.start_id, MAX(h.start_sent_at), COALESCE(MAX(CASE WHEN h.parent_id IS NULL THEN h.id END),"
                + " MIN(CASE WHEN h.tiefe >= " + KREIS_FENSTER + " THEN h.id END), MIN(h.id))"
                + " FROM hoch h GROUP BY h.start_id)";
    }

    /** Verläufe einer Seite: Wurzel und neueste passende Mail, neueste zuerst ({@code anker} wie bei {@link #zuordnung}). */
    static String seite(String anker) {
        return zuordnung(anker)
                + ", passend(wurzel_id, id, sent_at, rang) AS ("
                + " SELECT z.wurzel_id, z.email_id, z.sent_at, ROW_NUMBER() OVER (PARTITION BY z.wurzel_id"
                + " ORDER BY z.sent_at DESC NULLS LAST, z.email_id DESC)"
                + " FROM zuordnung z)"
                + " SELECT p.wurzel_id, p.id FROM passend p WHERE p.rang = 1"
                + " ORDER BY p.sent_at DESC NULLS LAST, p.wurzel_id DESC";
    }

    /** Anzahl der Verläufe (gleicher Anker wie {@link #seite}). */
    static String anzahl(String anker) {
        return zuordnung(anker) + " SELECT COUNT(DISTINCT z.wurzel_id) FROM zuordnung z";
    }

    /** {@code WITH RECURSIVE runter …, mitglied(wurzel_id, id)}: alle Mails der Verläufe {@value #WURZELN}. */
    private static String mitglieder() {
        return "WITH RECURSIVE runter(wurzel_id, id, tiefe) AS ("
                + " SELECT e.id, e.id, 0 FROM email e WHERE e.id IN (:" + WURZELN + ")"
                + " UNION ALL"
                + " SELECT r.wurzel_id, k.id, r.tiefe + 1 FROM runter r"
                + " CROSS JOIN LATERAL (SELECT c.id FROM email c WHERE c.parent_email_id = r.id OFFSET 0) k"
                + " WHERE r.tiefe < " + MAX_TIEFE + "),"
                + " mitglied(wurzel_id, id) AS (SELECT DISTINCT r.wurzel_id, r.id FROM runter r)";
    }

    /**
     * Je Verlauf: Anzahl der Mails, die {@code lesbar} erfüllen, ob eine Ausgangsmail unzustellbar war,
     * und der Grund der neuesten unzustellbaren.
     */
    static String kennzahlen(String lesbar) {
        return mitglieder()
                + ", gelesen(wurzel_id, unzustellbar, zustell_fehler, rang) AS ("
                + " SELECT m.wurzel_id, CASE WHEN " + UNZUSTELLBAR + " THEN 1 ELSE 0 END, e.zustell_fehler,"
                + " ROW_NUMBER() OVER (PARTITION BY m.wurzel_id ORDER BY CASE WHEN " + UNZUSTELLBAR
                + " THEN 0 ELSE 1 END, e.sent_at DESC NULLS LAST, e.id DESC)"
                + " FROM mitglied m JOIN email e ON e.id = m.id WHERE " + lesbar + ")"
                + " SELECT g.wurzel_id, COUNT(*), MAX(g.unzustellbar),"
                + " MAX(CASE WHEN g.rang = 1 AND g.unzustellbar = 1 THEN g.zustell_fehler END)"
                + " FROM gelesen g GROUP BY g.wurzel_id";
    }

    /** Nicht eingebettete Anhänge an Mails des Bereichs in den Verläufen {@value #WURZELN}. */
    static String anhaenge(EmailBezug bezug) {
        return mitglieder()
                + " SELECT DISTINCT mg.wurzel_id, att.id FROM mitglied mg"
                + " JOIN email_attachment att ON att.email_id = mg.id"
                + " WHERE mg.id IN (" + bezugIds(bezug) + ")"
                + " AND (att.inline_attachment IS NULL OR att.inline_attachment = FALSE)"
                + " ORDER BY mg.wurzel_id, att.id";
    }
}
