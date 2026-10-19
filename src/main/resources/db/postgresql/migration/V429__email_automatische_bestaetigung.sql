-- Automatische Eingangsbestätigung einer Webseiten-Anfrage kennzeichnen.
--
-- WARUM
-- Kommt eine Anfrage über das Formular der Firmen-Website, schickt das ERP dem Kunden
-- sofort eine Eingangsbestätigung und legt sie als gesendete E-Mail bei der Anfrage ab.
-- Das Löschen einer Anfrage ist gesperrt, sobald E-Mails an ihr hängen. Damit war jede
-- Webseiten-Anfrage unlöschbar, auch offensichtlicher Spam (Issue #272). Künftig sperrt
-- nur echte Korrespondenz; die automatische Bestätigung bleibt beim Löschen unter
-- „Gesendet“ stehen und verliert nur die Zuordnung.
--
-- WAS
--   email.automatische_bestaetigung  true = vom ERP selbst verschickte Eingangsbestätigung
--
-- NACHTRAG FÜR ALTE BESTÄTIGUNGEN (vorsichtig)
-- Markiert wird je Anfrage höchstens eine Mail, und nur wenn alles passt:
--   * die Anfrage kam über die Website (Notiz des System-Mitarbeiters "__SYSTEM_FUNNEL__"),
--   * die Mail ist ausgehend, ohne IMAP-Ordner/UID (rein lokaler Ausgangsdatensatz),
--     ohne Anhänge und keine Antwort auf eine andere Mail,
--   * sie ging innerhalb von 30 Minuten nach dem Anlegen der Anfrage raus,
--   * und sie ist die früheste ausgehende Mail dieser Anfrage.
-- Im Zweifel bleibt eine Mail unmarkiert – dann sperrt sie das Löschen wie bisher.
--
-- ROBUSTHEIT
-- ADD COLUMN IF NOT EXISTS; der Nachtrag setzt nur false -> true und ist wiederholbar.

ALTER TABLE email ADD COLUMN IF NOT EXISTS automatische_bestaetigung BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE email e
SET automatische_bestaetigung = TRUE
FROM anfrage a
WHERE e.anfrage_id = a.id
  AND e.automatische_bestaetigung = FALSE
  AND e.direction = 'OUT'
  AND e.imap_folder IS NULL
  AND e.imap_uid IS NULL
  AND e.parent_email_id IS NULL
  AND e.sent_at >= a.created_at
  AND e.sent_at <= a.created_at + INTERVAL '30 minutes'
  AND NOT EXISTS (SELECT 1 FROM email_attachment ea WHERE ea.email_id = e.id)
  AND EXISTS (
      SELECT 1
      FROM anfrage_notiz n
      JOIN mitarbeiter m ON m.id = n.mitarbeiter_id
      WHERE n.anfrage_id = a.id
        AND m.login_token = '__SYSTEM_FUNNEL__')
  AND NOT EXISTS (
      SELECT 1
      FROM email frueher
      WHERE frueher.anfrage_id = a.id
        AND frueher.direction = 'OUT'
        AND frueher.id <> e.id
        AND (frueher.sent_at < e.sent_at OR (frueher.sent_at = e.sent_at AND frueher.id < e.id)));
