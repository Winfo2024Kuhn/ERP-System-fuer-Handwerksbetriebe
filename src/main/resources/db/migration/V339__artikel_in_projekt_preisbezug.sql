-- Projektpositionen zeigen auf einen konkreten Preisstand statt auf Artikel+Lieferant.
--
-- Hintergrund: artikel_in_projekt verwies bisher ueber die Spalten (artikel_id,
-- lieferant_id) auf die Preistabelle. Seit V338 ist diese Tabelle eine Historie -
-- diese Kombination ist dort nicht mehr eindeutig, sie trifft jetzt auf jeden
-- vergangenen Preisstand zu.
--
-- Fachlich ist der Umbau ein Gewinn: Die Position haelt damit genau den Preis
-- fest, mit dem kalkuliert wurde. Aendert der Lieferant spaeter seine Preise,
-- bleibt die urspruengliche Kalkulation nachvollziehbar, statt sich still
-- mitzuveraendern.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- --------------------------------------------------------------------------
-- 1. Neue Spalte
-- --------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'artikel_in_projekt' AND column_name = 'lieferanten_artikel_preis_id');
SET @sql := IF(@c = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN lieferanten_artikel_preis_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- 2. Bestehende Positionen auf den jeweils aktuellen Preisstand setzen
-- --------------------------------------------------------------------------
-- Zum Zeitpunkt der Migration gibt es je Artikel/Lieferant genau einen Stand
-- (siehe V338), die Zuordnung ist damit eindeutig.

UPDATE artikel_in_projekt aip
JOIN lieferanten_artikel_preise lap
  ON lap.artikel_id = aip.artikel_id
 AND lap.lieferant_id = aip.lieferant_id
 AND lap.aktuell = b'1'
SET aip.lieferanten_artikel_preis_id = lap.id
WHERE aip.lieferanten_artikel_preis_id IS NULL
  AND aip.artikel_id IS NOT NULL
  AND aip.lieferant_id IS NOT NULL;

-- --------------------------------------------------------------------------
-- 4. Neue Beziehung absichern
-- --------------------------------------------------------------------------

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'artikel_in_projekt' AND index_name = 'ix_aip_preisstand');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_aip_preisstand ON artikel_in_projekt (lieferanten_artikel_preis_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Wird ein Preisstand geloescht, verliert die Position nur den Bezug - die
-- Position selbst muss erhalten bleiben.
SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'artikel_in_projekt'
      AND constraint_name = 'fk_aip_preisstand');
SET @sql := IF(@fk = 0,
    'ALTER TABLE artikel_in_projekt ADD CONSTRAINT fk_aip_preisstand
       FOREIGN KEY (lieferanten_artikel_preis_id) REFERENCES lieferanten_artikel_preise (id)
       ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
