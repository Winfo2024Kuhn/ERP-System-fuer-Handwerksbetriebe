-- Merkt sich, ob der Haken "Beendet" von Hand gesetzt oder entfernt wurde.
--
-- Hintergrund: Sobald die komplette Auftragssumme eines Projekts bezahlt ist,
-- setzt das System das Projekt automatisch auf "Beendet". Bisher konnte diese
-- Automatik eine Entscheidung des Benutzers ueberschreiben - der Haken sprang
-- wieder zurueck.
--
-- Mit diesem Flag gilt: Wer den Haken einmal selbst angefasst hat, behaelt die
-- Oberhand. Die Automatik fasst "abgeschlossen" dann nicht mehr an.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'projekt'
      AND column_name = 'abgeschlossen_manuell');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE projekt ADD COLUMN abgeschlossen_manuell BIT(1) NOT NULL DEFAULT b''0''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
