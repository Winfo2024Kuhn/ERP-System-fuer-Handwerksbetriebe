-- GoBD-/Tamper-Schutz für die digitale Annahme mit Alternativ-Auswahl:
-- Beim Versand des Freigabe-Links wird der Positions-Stand des Quelldokuments als
-- Snapshot festgehalten. Ansicht, Validierung der mitbeauftragten Alternativen und die
-- automatisch erzeugte Auftragsbestätigung leiten sich daraus ab — NICHT aus dem
-- (ggf. zwischenzeitlich bearbeiteten) Live-Dokument. So kann der angenommene Inhalt
-- nicht mehr von der versendeten PDF abweichen.
--
--   positionen_snapshot : positionenJson zum Versand-Zeitpunkt (LONGTEXT, JSON).
--   basis_netto         : Netto-Basisbetrag (ohne Alternativen) zum Versand-Zeitpunkt.
--   mwst_satz           : MwSt-Faktor (z.B. 0.1900) zum Versand-Zeitpunkt.
--
-- Alle NULLABLE: Vor dieser Migration versendete Freigaben kennen den Snapshot nicht;
-- der Service fällt für diese auf das Live-Dokument zurück (abwärtskompatibel).
-- Idempotent: jede Spalte nur hinzufügen, wenn sie noch nicht existiert.

SET @col_snap := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dokument_freigabe'
      AND column_name = 'positionen_snapshot'
);
SET @sql := IF(@col_snap = 0,
    'ALTER TABLE dokument_freigabe ADD COLUMN positionen_snapshot LONGTEXT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_netto := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dokument_freigabe'
      AND column_name = 'basis_netto'
);
SET @sql := IF(@col_netto = 0,
    'ALTER TABLE dokument_freigabe ADD COLUMN basis_netto DECIMAL(12,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_mwst := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dokument_freigabe'
      AND column_name = 'mwst_satz'
);
SET @sql := IF(@col_mwst = 0,
    'ALTER TABLE dokument_freigabe ADD COLUMN mwst_satz DECIMAL(5,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
