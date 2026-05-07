-- Entfernt die ungenutzten Tabellen `geschaeftsdokument` und `zahlung`.
-- Hintergrund: Der Dokumenten-Workflow läuft ausschließlich über
-- `ausgangs_geschaeftsdokument` (Ausgang) und `lieferant_geschaeftsdokument` (Eingang).
-- Die generische `Geschaeftsdokument`-Entity samt Zahlungs-Tabelle war nie produktiv
-- angebunden (kein Frontend-Aufruf, Tabellen leer) und wird jetzt zusammen mit dem
-- zugehörigen Java-Code entfernt.
-- Idempotent: nur droppen, wenn die Tabellen existieren.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS zahlung;
DROP TABLE IF EXISTS geschaeftsdokument;
SET FOREIGN_KEY_CHECKS = 1;
