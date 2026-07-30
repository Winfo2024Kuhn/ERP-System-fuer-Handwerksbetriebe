-- Zustellstatus fuer versendete E-Mails (Bounce-/Rueckläufer-Erkennung).
--
-- Hintergrund: Transport.send() gilt als erfolgreich, sobald der Relay des
-- Providers die Mail annimmt (250 OK). Lehnt der Zielserver sie danach ab
-- ("unknown user"), kommt der Fehler asynchron als Unzustellbarkeits-Mail
-- (DSN, RFC 3464) zurueck. Bisher landete diese Mail als gewoehnliche
-- Eingangsmail im Posteingang und wurde nirgends mit dem Ausgangs-Dokument
-- verknuepft — im ERP stand weiterhin "versendet", obwohl beim Kunden nie
-- etwas ankam. Diese Spalten halten das Ergebnis der DSN-Auswertung fest.
--
-- Nur Schema, keine personenbezogenen Daten.
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich (Spalten nur wenn nicht vorhanden).

-- zustell_status: Java-Enum ZustellStatus -> native MySQL-ENUM-Spalte, sonst
-- scheitert ddl-auto=validate (siehe BACKEND_ARCH.md).
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'zustell_status');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE email ADD COLUMN zustell_status ENUM(''OFFEN'',''UNZUSTELLBAR'') NOT NULL DEFAULT ''OFFEN''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Klartext-Grund aus dem DSN, z.B. "550 5.1.1 unknown user / Teilnehmer existiert nicht".
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'zustell_fehler');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE email ADD COLUMN zustell_fehler VARCHAR(500) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Zeitpunkt, zu dem der Rueckläufer ausgewertet wurde.
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'zustell_geprueft_am');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE email ADD COLUMN zustell_geprueft_am DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Kein zusaetzlicher Index auf message_id noetig: die Spalte traegt bereits
-- einen UNIQUE-Constraint (Email#messageId), ueber den der Bounce-Abgleich
-- die Original-Mail findet.
