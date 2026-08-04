-- Kennzeichen "Vorauskasse" am Lieferanten.
--
-- Hintergrund: Bei manchen Lieferanten wird grundsaetzlich im Voraus bezahlt
-- (Vorkasse, Lastschrift, Kreditkarte im Shop). Deren Rechnungen sind beim
-- Eintreffen per E-Mail bereits beglichen. Bisher landeten sie trotzdem in den
-- Offenen Posten - mit der Gefahr, dass der Betrag ein zweites Mal ueberwiesen
-- wird.
--
-- Ist das Kennzeichen gesetzt, markiert der Import eingehende Rechnungen dieses
-- Lieferanten automatisch als "bereits gezahlt"
-- (lieferant_geschaeftsdokument.bereits_gezahlt), wodurch sie aus der Liste der
-- Offenen Posten verschwinden.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten' AND column_name = 'vorauskasse');
SET @sql := IF(@c = 0,
    "ALTER TABLE lieferanten ADD COLUMN vorauskasse BIT(1) NOT NULL DEFAULT b'0'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
