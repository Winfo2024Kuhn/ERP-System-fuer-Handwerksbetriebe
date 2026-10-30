-- Wortlaut des freiwilligen Häkchens „Beginn vor Ablauf der Widerrufsfrist“.
--
-- WARUM
-- Seit V423 speichert die Freigabe nur, OB der Kunde den vorzeitigen Beginn verlangt hat. Für
-- einen lückenlosen Nachweis muss auch feststehen, WELCHEN Text er dabei gesehen hat – genau wie
-- beim Pflicht-Häkchen (bestaetigung_text). Die Firmen-Website meldet den Wortlaut mit.
--
-- WAS
--   vorzeitiger_beginn_text  Wortlaut des freiwilligen Häkchens, angehakt oder nicht.
--                            NULL = Annahme vor dieser Erweiterung.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts. Alte Annahmen bleiben NULL.

ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS vorzeitiger_beginn_text VARCHAR(600);
