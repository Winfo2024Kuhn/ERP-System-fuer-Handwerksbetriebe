-- Digitale Annahme mit Alternativ-Auswahl: Der Kunde kann auf der Freigabe-Seite
-- optionale (alternative) Positionen eines Angebots per Checkbox mitbeauftragen.
-- Die getroffene Auswahl und der daraus resultierende verbindliche Brutto-Betrag
-- werden auf der Freigabe gespeichert — als Beweissicherung und damit sie in den
-- Acceptance-Hash einfließen.
--
--   akzeptierte_alternativen : JSON-Array der gewählten blockIds (z.B. ["a1","b2"]).
--                              NULL/leer = es wurden keine Alternativen gewählt.
--   akzeptierter_betrag      : Verbindlicher Brutto-Endbetrag inkl. gewählter
--                              Alternativen. NULL = wie dokument_betrag (keine Wahl).
--
-- Bewusst NULLABLE: Vor dieser Migration akzeptierte Freigaben kennen das Feld nicht.
-- Idempotent: jede Spalte nur hinzufügen, wenn sie noch nicht existiert.

SET @col_alt := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dokument_freigabe'
      AND column_name = 'akzeptierte_alternativen'
);
SET @sql := IF(@col_alt = 0,
    'ALTER TABLE dokument_freigabe ADD COLUMN akzeptierte_alternativen LONGTEXT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_betrag := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dokument_freigabe'
      AND column_name = 'akzeptierter_betrag'
);
SET @sql := IF(@col_betrag = 0,
    'ALTER TABLE dokument_freigabe ADD COLUMN akzeptierter_betrag DECIMAL(12,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
