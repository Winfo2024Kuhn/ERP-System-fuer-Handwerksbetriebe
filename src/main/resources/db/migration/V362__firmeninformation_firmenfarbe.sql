-- ---------------------------------------------------------------------------
-- Firmenfarbe des Betriebs.
--
-- Der Abrechnungsstand auf Abschlags-, Teil- und Schlussrechnungen setzt einen
-- farbigen Akzent: den Fortschrittsbalken und die kleinen Nettobetraege neben
-- den Bruttosummen. Bisher stand die Farbe fest im Code, was bei mehreren
-- Betrieben nicht funktioniert - deshalb gehoert sie zu den Firmeninformationen.
--
-- Format: Hex mit fuehrendem #, also "#500010". Drei- und sechsstellig sind
-- erlaubt, der Service normalisiert die Eingabe. NULL oder leer bedeutet: das
-- Dokument nimmt die Standardfarbe.
--
-- MySQL kennt kein "ADD COLUMN IF NOT EXISTS", deshalb der Umweg ueber
-- information_schema - wie in den vorherigen Migrationen.
-- ---------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'firmeninformation' AND column_name = 'firmenfarbe');
SET @sql := IF(@c = 0,
    'ALTER TABLE firmeninformation ADD COLUMN firmenfarbe VARCHAR(7) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
