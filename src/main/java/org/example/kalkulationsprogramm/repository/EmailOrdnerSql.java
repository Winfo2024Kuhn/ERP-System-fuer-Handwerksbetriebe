package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailOrdner;

/**
 * Welche Mails gehören in welchen Ordner – als native SQL-Bedingung auf {@code email e}
 * (PostgreSQL). Grundlage der Suche „In diesem Ordner“: Treffer sind genau die Mails, die die
 * Ordnerliste ({@code GET /api/emails/{ordner}}) zeigt, eingeschränkt auf die Suchwörter.
 *
 * <p>Die Ordnerlisten selbst laufen über die JPQL-Abfragen in {@link EmailRepository}
 * ({@code findInboxFiltered}, {@code findSpamOrdner}, …). Wer dort eine Regel ändert, ändert sie
 * hier mit – {@code DatenbankKompatibilitaetEmailSucheTest} prüft je Ordner, dass Liste und Suche
 * dieselben Mails meinen. „Nicht zugeordnet“ ({@link #NICHT_ZUGEORDNET}) gibt es nur einmal: auch
 * {@code EmailRepository.findUnassigned} nutzt diese Konstante.</p>
 */
final class EmailOrdnerSql {

    /** Benannter Parameter des Steuerberater-Ordners: Regex der Domains, siehe {@link EmailSuchAbfrage}. */
    static final String STEUERBERATER_MUSTER = "steuerberaterMuster";

    /**
     * „Nicht zugeordnet“: unzugeordnete eingehende Mails von BEKANNTEN Kunden (Absender exakt in
     * kunden_emails, anfrage_kunden_emails oder projekt_kunden_emails), ohne Newsletter, und das
     * Sendedatum liegt innerhalb von ±1 Monat um Anlege- bzw. Abschlussdatum eines passenden
     * Projekts oder einer passenden Anfrage (Karenzzeit). Drei EXISTS-Zweige: Projekt-Kunden,
     * Anfrage-Kunden, Kunden über ihre Projekte. Ohne SQL-Kommentare, weil die Bedingung in andere
     * Abfragen eingesetzt wird.
     */
    static final String NICHT_ZUGEORDNET = """
            e.zuordnung_typ = 'KEINE'
              AND e.direction = 'IN'
              AND e.projekt_id IS NULL
              AND e.anfrage_id IS NULL
              AND e.lieferant_id IS NULL
              AND e.deleted_at IS NULL
              AND (e.is_spam IS NULL OR e.is_spam = FALSE)
              AND LOWER(COALESCE(e.subject, '')) NOT LIKE '%newsletter%'
              AND LOWER(COALESCE(e.body, '')) NOT LIKE '%newsletter%'
              AND (
                  EXISTS (
                      SELECT 1 FROM projekt_kunden_emails pke
                      JOIN projekt p ON pke.projekt_id = p.id
                      WHERE (LOWER(pke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(pke.email), '>%'))
                      AND (
                          (CAST(e.sent_at AS DATE) BETWEEN p.anlegedatum - INTERVAL '1' MONTH AND COALESCE(p.abschlussdatum, p.anlegedatum) + INTERVAL '1' MONTH)
                      )
                  )
                  OR
                  EXISTS (
                      SELECT 1 FROM anfrage_kunden_emails ake
                      JOIN anfrage a ON ake.anfrage_id = a.id
                      WHERE (LOWER(ake.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ake.email), '>%'))
                      AND (
                          CAST(e.sent_at AS DATE) BETWEEN a.anlegedatum - INTERVAL '1' MONTH AND COALESCE(a.anlegedatum, CURRENT_DATE) + INTERVAL '1' MONTH
                      )
                  )
                  OR
                  EXISTS (
                      SELECT 1 FROM kunden_emails ke
                      JOIN kunde k ON ke.kunden_id = k.id
                      JOIN projekt p ON p.kunden_id = k.id
                      WHERE (LOWER(ke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ke.email), '>%'))
                      AND (
                          (CAST(e.sent_at AS DATE) BETWEEN p.anlegedatum - INTERVAL '1' MONTH AND COALESCE(p.abschlussdatum, p.anlegedatum) + INTERVAL '1' MONTH)
                      )
                  )
              )
            """;

    private static final String OHNE_ZUORDNUNG = "e.zuordnung_typ = 'KEINE' AND e.projekt_id IS NULL "
            + "AND e.anfrage_id IS NULL AND e.lieferant_id IS NULL";

    /** {@code findInboxFiltered} minus „Nicht zugeordnet“ (wie {@code GET /api/emails/inbox}). */
    private static final String POSTEINGANG = "e.direction = 'IN' AND " + OHNE_ZUORDNUNG
            + " AND e.deleted_at IS NULL AND e.is_spam = FALSE AND e.is_newsletter = FALSE"
            + " AND e.is_potential_inquiry = FALSE AND NOT COALESCE((" + NICHT_ZUGEORDNET + "), FALSE)";

    /**
     * Steuerberater-Ordner: eine Adresse in Absender, Empfänger oder CC aus genau einer der Domains
     * (Regex {@code @(domain1|domain2)($|[^a-z0-9.-])} – „kanzlei.de“ trifft nicht
     * „kanzlei.de.example.org“), wie {@code SteuerberaterOrdner.emails()}.
     */
    private static final String STEUERBERATER = "e.deleted_at IS NULL AND ("
            + "LOWER(COALESCE(e.from_address, '')) ~ CAST(:" + STEUERBERATER_MUSTER + " AS text) "
            + "OR LOWER(COALESCE(e.recipient, '')) ~ CAST(:" + STEUERBERATER_MUSTER + " AS text) "
            + "OR LOWER(COALESCE(e.cc, '')) ~ CAST(:" + STEUERBERATER_MUSTER + " AS text))";

    private EmailOrdnerSql() {
    }

    /** SQL-Bedingung des Ordners; {@link EmailOrdner#ALLE} = alle Mails (auch Papierkorb). */
    static String bedingung(EmailOrdner ordner) {
        return switch (ordner) {
            case ALLE -> "TRUE";
            case INBOX -> POSTEINGANG;
            case SENT -> "e.direction = 'OUT' AND e.deleted_at IS NULL";
            case TRASH -> "e.deleted_at IS NOT NULL";
            case SPAM -> "e.is_spam = TRUE AND e.deleted_at IS NULL AND " + OHNE_ZUORDNUNG;
            case NEWSLETTER -> "e.is_newsletter = TRUE AND e.is_spam = FALSE AND e.deleted_at IS NULL AND "
                    + OHNE_ZUORDNUNG;
            case STARRED -> "e.is_starred = TRUE AND e.deleted_at IS NULL";
            case PROJECTS -> "e.zuordnung_typ = 'PROJEKT' AND e.direction = 'IN' AND e.deleted_at IS NULL";
            case OFFERS -> "e.zuordnung_typ = 'ANFRAGE' AND e.direction = 'IN' AND e.deleted_at IS NULL";
            case SUPPLIERS -> "e.zuordnung_typ = 'LIEFERANT' AND e.direction = 'IN' AND e.deleted_at IS NULL";
            case TAX_ADVISORS -> STEUERBERATER;
            case UNASSIGNED -> "(" + NICHT_ZUGEORDNET + ")";
            case INQUIRIES -> "e.is_potential_inquiry = TRUE AND e.is_spam = FALSE AND e.deleted_at IS NULL"
                    + " AND e.direction = 'IN' AND " + OHNE_ZUORDNUNG;
        };
    }
}
