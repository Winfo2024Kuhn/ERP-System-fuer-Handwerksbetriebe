-- Texte und Verkaufsaufschlag am Artikel, damit Material im DocumentEditor
-- als Position eingefuegt werden kann.
--
-- kurzbeschreibung ist Innensicht: sie hilft dem Bediener, den Artikel im
-- Editor wiederzufinden, und wird dem Kunden NICHT gezeigt - genauso wie der
-- Kurztext einer Leistung seit Commit 48dd24a nicht mehr ins PDF gedruckt wird.
-- beschreibung ist der Kundentext (Rich-Text-HTML) und landet auf PDF und
-- Freigabe-Website.
--
-- verkaufsaufschlag_prozent ist der Aufschlag auf den Einkaufspreis. Der Name
-- ist bewusst lang: ArtikelDetailDto.LieferantEintragDto fuehrt bereits ein
-- Feld "aufschlagProzent", das die Abweichung zum guenstigsten Lieferanten
-- meint. Die beiden duerfen nie verwechselt werden.
--
-- Alle drei bleiben NULL-bar: fehlende Pflege blockiert nichts, sie erzeugt im
-- Editor lediglich einen Hinweis.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'artikel' AND column_name = 'kurzbeschreibung');
SET @sql := IF(@c = 0,
    'ALTER TABLE artikel ADD COLUMN kurzbeschreibung VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'artikel' AND column_name = 'beschreibung');
SET @sql := IF(@c = 0,
    'ALTER TABLE artikel ADD COLUMN beschreibung TEXT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'artikel' AND column_name = 'verkaufsaufschlag_prozent');
SET @sql := IF(@c = 0,
    'ALTER TABLE artikel ADD COLUMN verkaufsaufschlag_prozent DECIMAL(5,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
