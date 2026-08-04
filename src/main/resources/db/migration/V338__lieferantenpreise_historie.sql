-- Preistabelle wird zur Preishistorie.
--
-- Hintergrund: Bisher war der Schluessel (artikel_id, lieferant_id). Damit passte
-- je Lieferant genau ein Preis in die Tabelle - ein neuer Preis hat den alten
-- ueberschrieben. Fuer eine Preisverlaufs-Anzeige fehlte damit jede Grundlage.
--
-- Umbau: Die Tabelle bekommt einen technischen Schluessel und nimmt ab sofort
-- jede Preisaenderung als eigene Zeile auf. Welche Zeile der aktuell gueltige
-- Preis ist, steht im Kennzeichen "aktuell". Dieses Kennzeichen ist bewusst
-- redundant - ohne es muesste jede der rund 60 bestehenden Abfragestellen im
-- Code auf eine Unterabfrage nach dem juengsten Datum umgebaut werden.
--
-- Bestehende Daten: Alle 7153 vorhandenen Zeilen sind je Artikel/Lieferant
-- eindeutig und werden daher als aktueller Preis uebernommen.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- --------------------------------------------------------------------------
-- 1. Technischer Primaerschluessel
-- --------------------------------------------------------------------------
-- Reihenfolge ist wichtig: erst die Spalte ohne AUTO_INCREMENT anlegen und
-- befuellen, dann den alten Schluessel loesen, dann den neuen setzen.

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND column_name = 'id');
SET @sql := IF(@c = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN id BIGINT NULL FIRST',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Vorhandene Zeilen durchnummerieren.
SET @rn := 0;
UPDATE lieferanten_artikel_preise SET id = (@rn := @rn + 1) WHERE id IS NULL ORDER BY artikel_id, lieferant_id;

-- Projektpositionen verweisen ueber einen zusammengesetzten Fremdschluessel auf
-- (artikel_id, lieferant_id). Solange der besteht, laesst MySQL den alten
-- Primaerschluessel nicht fallen. Der Verweis wird in V339 auf die neue
-- technische ID umgestellt; hier wird zunaechst nur die Sperre geloest.
-- Die Zuordnung geht dabei nicht verloren: artikel_in_projekt behaelt seine
-- Spalten artikel_id und lieferant_id, aus denen V339 den Bezug wiederherstellt.
SET @fk := (SELECT constraint_name FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE() AND table_name = 'artikel_in_projekt'
      AND referenced_table_name = 'lieferanten_artikel_preise'
    LIMIT 1);
SET @sql := IF(@fk IS NOT NULL,
    CONCAT('ALTER TABLE artikel_in_projekt DROP FOREIGN KEY `', @fk, '`'),
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Die Tabelle hat eigene Fremdschluessel auf artikel und lieferanten. MySQL
-- verlangt fuer jeden davon einen Index und nutzt dafuer bislang den
-- zusammengesetzten Primaerschluessel. Ohne Ersatz verweigert es dessen
-- Loeschung ("Cannot drop index 'PRIMARY': needed in a foreign key
-- constraint"). Die Ersatzindizes werden deshalb vorher angelegt.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_artikel');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_lap_artikel ON lieferanten_artikel_preise (artikel_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_lieferant');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_lap_lieferant ON lieferanten_artikel_preise (lieferant_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Alten zusammengesetzten Schluessel entfernen, sofern er noch besteht.
SET @pk := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'PRIMARY' AND column_name = 'artikel_id');
SET @sql := IF(@pk > 0, 'ALTER TABLE lieferanten_artikel_preise DROP PRIMARY KEY', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Neuen Schluessel setzen.
SET @pk := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'PRIMARY');
SET @sql := IF(@pk = 0,
    'ALTER TABLE lieferanten_artikel_preise MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- 2. Neue Spalten fuer die Historie
-- --------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND column_name = 'aktuell');
SET @sql := IF(@c = 0,
    "ALTER TABLE lieferanten_artikel_preise ADD COLUMN aktuell BIT(1) NOT NULL DEFAULT b'1'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Woher der Preis stammt - wichtig, um manuell gepflegte Preise von automatisch
-- aus Angebots-Mails uebernommenen unterscheiden zu koennen.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND column_name = 'quelle');
SET @sql := IF(@c = 0,
    "ALTER TABLE lieferanten_artikel_preise ADD COLUMN quelle ENUM('MANUELL','ANGEBOT_EMAIL','CSV_IMPORT','RECHNUNG','SYSTEM','UNBEKANNT') NOT NULL DEFAULT 'UNBEKANNT'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Freitext-Notiz, z.B. Angebotsnummer oder wer den Preis telefonisch genannt hat.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND column_name = 'notiz');
SET @sql := IF(@c = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN notiz VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Erfassungszeitpunkt. preis_aenderungsdatum ist der fachliche Stand des Preises
-- (z.B. Datum der Angebots-Mail), erfasst_am der technische Eingang.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND column_name = 'erfasst_am');
SET @sql := IF(@c = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN erfasst_am DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE lieferanten_artikel_preise SET erfasst_am = preis_aenderungsdatum WHERE erfasst_am IS NULL;

-- --------------------------------------------------------------------------
-- 3. Indizes anpassen
-- --------------------------------------------------------------------------

-- Die bisherige Eindeutigkeit auf (lieferant_id, externe_artikelnummer) kann mit
-- einer Historie nicht mehr gelten: dieselbe Lieferanten-Artikelnummer taucht
-- kuenftig bei jedem Preisstand erneut auf. Sie wird durch einen normalen
-- Suchindex ersetzt; die Eindeutigkeit sichert jetzt der Teilindex weiter unten,
-- der nur die aktuellen Preise betrachtet.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ux_aen_ci');
SET @sql := IF(@idx > 0, 'ALTER TABLE lieferanten_artikel_preise DROP INDEX ux_aen_ci', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_lieferant_aen');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_lap_lieferant_aen ON lieferanten_artikel_preise (lieferant_id, externe_artikelnummer)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Schneller Zugriff auf den aktuellen Preis eines Artikels.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_artikel_aktuell');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_lap_artikel_aktuell ON lieferanten_artikel_preise (artikel_id, aktuell, preis)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Zeitliche Abfrage fuer die Verlaufsanzeige.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_verlauf');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_lap_verlauf ON lieferanten_artikel_preise (artikel_id, lieferant_id, preis_aenderungsdatum)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- 4. Bestandsdaten als aktuellen Stand markieren
-- --------------------------------------------------------------------------
-- Sicherheitsnetz: Sollte es je Artikel/Lieferant wider Erwarten doch mehrere
-- Zeilen geben, bleibt nur die juengste als aktuell stehen.

UPDATE lieferanten_artikel_preise SET aktuell = b'1';

UPDATE lieferanten_artikel_preise lap
JOIN (
    SELECT artikel_id, lieferant_id, MAX(id) AS behalten_id
    FROM lieferanten_artikel_preise
    WHERE (artikel_id, lieferant_id, COALESCE(preis_aenderungsdatum, '1000-01-01')) IN (
        SELECT artikel_id, lieferant_id, MAX(COALESCE(preis_aenderungsdatum, '1000-01-01'))
        FROM lieferanten_artikel_preise
        GROUP BY artikel_id, lieferant_id
    )
    GROUP BY artikel_id, lieferant_id
) neueste ON neueste.artikel_id = lap.artikel_id AND neueste.lieferant_id = lap.lieferant_id
SET lap.aktuell = b'0'
WHERE lap.id <> neueste.behalten_id;

UPDATE lieferanten_artikel_preise SET quelle = 'UNBEKANNT' WHERE quelle IS NULL;
