-- Rückbau der in V253 angelegten Zwei-Faktor-Spalten.
-- Entscheidung: SMS/PIN-Verifizierung ist für einen 5-Mann-Handwerksbetrieb Overkill —
-- der bestehende Workflow (Token-Link in der Mail + Checkbox-Annahme + sofortige
-- Auto-Auftragsbestätigung als PDF) deckt die Beweislage gerichtsfest ab.
--
-- V253 wurde lokal bereits angewendet, die Spalten existieren also in der DB. Diese
-- Migration räumt sie wieder weg, damit das Schema konsistent zum Code-Stand bleibt.

ALTER TABLE dokument_freigabe
    DROP COLUMN verifizierungs_modus,
    DROP COLUMN code_hash,
    DROP COLUMN code_versuche,
    DROP COLUMN code_gueltig_bis,
    DROP COLUMN code_letzter_versand,
    DROP COLUMN handynummer_snapshot;

ALTER TABLE kunde
    DROP COLUMN freigabe_pin;
