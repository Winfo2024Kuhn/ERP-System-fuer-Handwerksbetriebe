-- ═══════════════════════════════════════════════════════════════
-- V288: Kategorie für Projekt-Notizen (EN-1090-Bautagebuch)
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: Für die EN-1090-Akte muss nachvollziehbar sein,
-- welche Verbindungsmittel (HV-Schrauben, Dübel, Anker), Schweiß-
-- nähte, Korrosionsschutzschichten an einem Projekt verbaut/aus-
-- geführt wurden. Statt einer separaten Datenstruktur nutzen wir
-- das vorhandene Bautagebuch (projekt_notiz + projekt_notiz_bild)
-- und kategorisieren die Einträge. Foto vom Etikett (ETA-Nummer
-- und Charge stehen drauf) + strukturierte Notiz reichen für den
-- Audit. Beim EN-1090-Akte-Export werden Notizen mit den ent-
-- sprechenden Kategorien automatisch eingesammelt.
--
-- Mögliche Werte (frei erweiterbar, in Java als Enum gespiegelt):
--   ALLGEMEIN        – default, normaler Bautagebuch-Eintrag
--   VERBINDUNGSMITTEL – HV-Schrauben, Dübel, Anker mit Etikett-Foto
--   SCHWEISSUNG      – Schweißnaht-Doku (Schweißer, WPS, Position)
--   KORROSIONSSCHUTZ – Beschichtungssystem, Schichtdicke
--   OBERFLAECHE      – Strahlgrad, Vorbereitung
--
-- MySQL 8 unterstützt kein "ADD COLUMN IF NOT EXISTS" – wir nutzen
-- daher den gleichen INFORMATION_SCHEMA-Workaround wie V259, damit
-- die Migration auch idempotent ist (z. B. nach einem fehlge-
-- schlagenen Vorlauf, der die Spalte schon angelegt hatte).
-- ═══════════════════════════════════════════════════════════════

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'projekt_notiz'
                   AND COLUMN_NAME = 'kategorie');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE projekt_notiz ADD COLUMN kategorie VARCHAR(40) NOT NULL DEFAULT ''ALLGEMEIN''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
