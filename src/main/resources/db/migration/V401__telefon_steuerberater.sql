-- Telefon: Steuerberater als dritte Kontaktart neben Kunde und Lieferant.
-- Anrufe, Anrufbeantworter-Nachrichten und gemerkte Rufnummern koennen jetzt
-- auch einer Steuerberater-Kanzlei zugeordnet sein. Idempotent.
--
-- Bewusst KEIN CHECK "genau ein Kontakt": MySQL 8 verbietet CHECK-Constraints
-- auf Spalten mit ON DELETE/ON UPDATE-Aktion (Fehler 3823). Die Regel sichert
-- der RufnummernZuordnungService ab.
--
-- Engine-Kompatibilitaet (wie V322): MariaDB erwartet DROP CONSTRAINT, MySQL DROP CHECK.

SET @dropkw := IF(VERSION() LIKE '%MariaDB%', 'DROP CONSTRAINT', 'DROP CHECK');

SET @c := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'telefon_anruf' AND column_name = 'steuerberater_id');
SET @sql := IF(@c = 0,
    'ALTER TABLE telefon_anruf ADD COLUMN steuerberater_id BIGINT NULL, ADD CONSTRAINT fk_telefon_anruf_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sprachnachricht' AND column_name = 'steuerberater_id');
SET @sql := IF(@c = 0,
    'ALTER TABLE sprachnachricht ADD COLUMN steuerberater_id BIGINT NULL, ADD CONSTRAINT fk_sprachnachricht_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Alter CHECK aus V400 (nur Kunde/Lieferant) wuerde gemerkte Steuerberater-Nummern ablehnen.
SET @c := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'kontakt_rufnummer'
      AND constraint_name = 'ck_kontakt_rufnummer_genau_ein_kontakt');
SET @sql := IF(@c > 0, CONCAT('ALTER TABLE kontakt_rufnummer ', @dropkw, ' ck_kontakt_rufnummer_genau_ein_kontakt'), 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'kontakt_rufnummer' AND column_name = 'steuerberater_id');
SET @sql := IF(@c = 0,
    'ALTER TABLE kontakt_rufnummer ADD COLUMN steuerberater_id BIGINT NULL, ADD CONSTRAINT fk_kontakt_rufnummer_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
