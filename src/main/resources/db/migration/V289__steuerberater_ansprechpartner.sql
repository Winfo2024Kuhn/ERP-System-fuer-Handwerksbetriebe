-- Mehrere Ansprechpartner pro Steuerberater (z.B. einer für Lohnbuchhaltung,
-- einer für BWA). Im Modal "Stundenübermittlung" wird der Ansprechpartner
-- mit istLohnAnsprechpartner=TRUE als Empfänger der Stundenaufstellung
-- vorausgewählt.

SET @tbl_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'steuerberater_ansprechpartner'
);

SET @create_tbl = IF(@tbl_exists = 0,
    'CREATE TABLE steuerberater_ansprechpartner (
        id BIGINT NOT NULL AUTO_INCREMENT,
        steuerberater_id BIGINT NOT NULL,
        anrede VARCHAR(32) NULL,
        vorname VARCHAR(255) NULL,
        nachname VARCHAR(255) NOT NULL,
        email VARCHAR(255) NULL,
        telefon VARCHAR(64) NULL,
        ist_lohn_ansprechpartner BOOLEAN NOT NULL DEFAULT FALSE,
        notizen VARCHAR(500) NULL,
        PRIMARY KEY (id),
        CONSTRAINT fk_sb_ap_steuerberater FOREIGN KEY (steuerberater_id)
            REFERENCES steuerberater_kontakt (id) ON DELETE CASCADE
    ) ENGINE=InnoDB',
    'SELECT 1'
);
PREPARE stmt FROM @create_tbl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_sb_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'steuerberater_ansprechpartner'
      AND INDEX_NAME   = 'idx_sb_ap_steuerberater'
);

SET @add_idx_sb = IF(@idx_sb_exists = 0,
    'CREATE INDEX idx_sb_ap_steuerberater ON steuerberater_ansprechpartner (steuerberater_id)',
    'SELECT 1'
);
PREPARE stmt2 FROM @add_idx_sb;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Bestehenden Einzel-Ansprechpartner (steuerberater_kontakt.ansprechpartner)
-- als ersten Eintrag in die neue Tabelle migrieren, damit nichts verloren geht.
-- Heuristik: alles vor dem ERSTEN Leerzeichen = Vorname, Rest = Nachname
-- (d.h. "Hans Peter Müller" -> Vorname "Hans", Nachname "Peter Müller").
-- Nicht perfekt für Doppelnamen/Titel, aber gut genug als Default; der User
-- kann später im FirmaEditor manuell aufteilen. TODO: partieller Unique-Index
-- "(steuerberater_id) WHERE ist_lohn_ansprechpartner = TRUE" wäre wünschens-
-- wert, MySQL unterstützt das nur über Generated Column / Trigger.
INSERT INTO steuerberater_ansprechpartner
    (steuerberater_id, vorname, nachname, email, telefon, ist_lohn_ansprechpartner)
SELECT
    sk.id,
    CASE
        WHEN LOCATE(' ', TRIM(sk.ansprechpartner)) > 0
            THEN TRIM(SUBSTRING_INDEX(TRIM(sk.ansprechpartner), ' ', 1))
        ELSE NULL
    END AS vorname,
    CASE
        WHEN LOCATE(' ', TRIM(sk.ansprechpartner)) > 0
            THEN TRIM(SUBSTRING(TRIM(sk.ansprechpartner), LOCATE(' ', TRIM(sk.ansprechpartner)) + 1))
        ELSE TRIM(sk.ansprechpartner)
    END AS nachname,
    sk.email,
    sk.telefon,
    TRUE AS ist_lohn_ansprechpartner
FROM steuerberater_kontakt sk
WHERE sk.ansprechpartner IS NOT NULL
  AND TRIM(sk.ansprechpartner) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM steuerberater_ansprechpartner sap
      WHERE sap.steuerberater_id = sk.id
  );
