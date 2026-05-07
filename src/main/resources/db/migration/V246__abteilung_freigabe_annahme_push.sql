-- Steuert pro Abteilung, ob Mitarbeiter eine Push-Benachrichtigung bekommen,
-- wenn ein Kunde ein Dokument (Angebot/Auftragsbestätigung) digital annimmt.
-- Default true: bisheriges Verhalten ("alle bekommen Push") bleibt unverändert,
-- bis ein Admin den Schalter pro Abteilung deaktiviert.
-- Idempotent: nur hinzufügen wenn die Spalte fehlt.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'abteilung'
      AND column_name = 'darf_freigabe_annahme_pushen'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE abteilung ADD COLUMN darf_freigabe_annahme_pushen BOOLEAN NOT NULL DEFAULT TRUE',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
