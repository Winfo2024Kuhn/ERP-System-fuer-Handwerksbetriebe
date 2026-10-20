-- Feiertage je Bundesland statt fest Bayern (Issue #222).
--
-- WARUM
-- Bisher galten überall die bayerischen Feiertage. Betriebe in anderen Ländern bekamen
-- dadurch falsches Soll, falsche Urlaubstage und einen falschen Lohnexport. Außerdem
-- sollen Betriebe regionale Feiertage abschalten, halbe Tage markieren und eigene
-- Feiertage anlegen können, ohne dass das Neu-Laden es wieder überschreibt.
--
-- WAS
--   firmeninformation.bundesland  Amtliches Kürzel (BY, BW, …); bestehende Installationen bleiben BY
--   feiertag.aktiv                FALSE = abgeschaltet, zählt nirgends als Feiertag
--   feiertag.eigener              TRUE = vom Betrieb angelegt (nur diese lassen sich löschen)
--   feiertag.angepasst            TRUE = vom Betrieb geändert; Neu-Laden lässt die Zeile stehen
--
-- ROBUSTHEIT
-- ADD COLUMN IF NOT EXISTS mit Standardwert, ein zweiter Lauf ändert nichts.

ALTER TABLE firmeninformation ADD COLUMN IF NOT EXISTS bundesland VARCHAR(2) NOT NULL DEFAULT 'BY';
ALTER TABLE feiertag ADD COLUMN IF NOT EXISTS aktiv BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE feiertag ADD COLUMN IF NOT EXISTS eigener BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE feiertag ADD COLUMN IF NOT EXISTS angepasst BOOLEAN NOT NULL DEFAULT FALSE;
