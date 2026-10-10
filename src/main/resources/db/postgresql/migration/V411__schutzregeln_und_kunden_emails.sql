-- =============================================================================
-- V411 (PostgreSQL): Schutzregeln wie in MySQL, Kunden-E-Mails mehrfach erlaubt
-- =============================================================================
-- 1. Hoechstens EINE laufende Zeitbuchung pro Mitarbeiter. MySQL erzwingt das seit
--    V321 ueber die berechnete Spalte aktiver_mitarbeiter + UNIQUE; in der
--    PostgreSQL-Basis fehlte die Regel. Ein partieller Index ist hier dasselbe:
--    eindeutig nur fuer Zeilen ohne Ende.
-- 2. Hoechstens EINE offene Pause (Zeitkonto) pro Mitarbeiter (MySQL: V370,
--    berechnete Spalte offene_pause_mitarbeiter_id).
--    Verletzen vorhandene Daten 1. oder 2., scheitert die Migration bewusst
--    (Nachtupdate rollt zurueck und meldet sich): Zeitbuchungen werden nie
--    stillschweigend geaendert.
-- 3. Dieselbe E-Mail-Adresse darf bei mehreren Kunden stehen, z. B. eine
--    Hausverwaltung fuer mehrere WEGs. Bisher eindeutig, jetzt normaler Index
--    (Kunde.kundenEmails ohne unique). Der eigene Server hat die Regel schon beim
--    Umzug auf PostgreSQL (10.10.2026) gelockert - dort nur umbenennen.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_zeitbuchung_aktiv_pro_mitarbeiter
    ON zeitbuchung (mitarbeiter_id) WHERE ende_zeit IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_zeitkonto_pause_offen
    ON zeitkonto_pause (mitarbeiter_id) WHERE gueltig_bis IS NULL;

ALTER TABLE kunden_emails DROP CONSTRAINT IF EXISTS kunden_emails_email_key;
ALTER INDEX IF EXISTS kunden_emails_email_key_mehrfach_erlaubt RENAME TO idx_kunden_emails_email;
CREATE INDEX IF NOT EXISTS idx_kunden_emails_email ON kunden_emails (email);
