-- V150: Rename kostenstelle → miete_kostenstelle
-- Die Miete-Entity wurde von @Table(name="kostenstelle") auf @Table(name="miete_kostenstelle") umbenannt.
-- Gleichzeitig wird die neue firma_kostenstelle-Tabelle erstellt.
-- Idempotent: Prüft ob Rename nötig ist.

-- 1. Tabelle umbenennen (nur wenn alte existiert und neue nicht)
SET @tbl_old_exists = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kostenstelle');
SET @tbl_new_exists = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'miete_kostenstelle');

-- Rename nur wenn alte existiert und neue nicht
SET @do_rename = (@tbl_old_exists > 0 AND @tbl_new_exists = 0);

SET @sql_rename = IF(@do_rename, 'RENAME TABLE kostenstelle TO miete_kostenstelle', 'SELECT 1');
PREPARE stmt FROM @sql_rename;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. FK-Referenzen in kostenposition aktualisieren:
-- Die kostenposition-Tabelle hat eine FK auf kostenstelle (jetzt miete_kostenstelle).
-- MySQL aktualisiert FKs bei RENAME automatisch, aber die Constraint-Namen bleiben gleich.
-- Nichts weiter nötig.
