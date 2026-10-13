-- Rechtsnachweis der digitalen Freigabe.
--
-- WARUM
-- Bei einer Online-Annahme muss der Betrieb im Streitfall belegen, WAS der Kunde gesehen und
-- WOMIT er beauftragt hat: Beschriftung des auslösenden Knopfs (Button-Lösung, § 312j Abs. 3
-- BGB), Wortlaut der Bestätigung, die Fassung der Widerrufsbelehrung und der
-- Datenschutzerklärung. Dazu kommt die freiwillige Zustimmung zum vorzeitigen Beginn vor Ablauf
-- der Widerrufsfrist (§ 356 Abs. 5 Nr. 2, § 357a Abs. 2 BGB): Nur mit ihr darf der Betrieb sofort
-- Material bestellen und beginnen.
--
-- WAS
--   vorzeitiger_beginn      TRUE = Kunde verlangt ausdrücklich Beginn vor Fristablauf,
--                           FALSE = nicht verlangt, NULL = Annahme vor dieser Erweiterung.
--   widerruf_stand          Stand der Widerrufsbelehrung bei der Annahme (z. B. 2026-10-13).
--   datenschutz_stand       Stand der Datenschutzerklärung bei der Annahme.
--   bestaetigung_text       Wortlaut des Pflicht-Häkchens, das der Kunde gesetzt hat.
--   knopf_text              Beschriftung des Knopfs, der die Annahme ausgelöst hat.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts. Alte Annahmen bleiben NULL.

ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS vorzeitiger_beginn BOOLEAN;
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS widerruf_stand VARCHAR(20);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS datenschutz_stand VARCHAR(20);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS bestaetigung_text VARCHAR(500);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS knopf_text VARCHAR(100);
