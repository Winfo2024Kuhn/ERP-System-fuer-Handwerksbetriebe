-- Zweiter Name ("Alias") fuer Lieferanten.
--
-- Hintergrund: Viele Firmen sind im Betrieb unter einem anderen Namen bekannt als
-- unter ihrem offiziellen Firmennamen (z.B. "-Service-Center-" wird intern nur
-- "Kfz Meier" genannt). Bisher fand die Freitextsuche solche Lieferanten nicht,
-- weil nur der offizielle Name durchsucht wurde.
--
-- Mit diesem Feld kann ein zweiter, alltagstauglicher Name hinterlegt werden. Er
-- wird bei der Freitextsuche mitdurchsucht und auf der Lieferanten-Karte angezeigt.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'lieferanten'
      AND column_name = 'alias_name');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE lieferanten ADD COLUMN alias_name VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
