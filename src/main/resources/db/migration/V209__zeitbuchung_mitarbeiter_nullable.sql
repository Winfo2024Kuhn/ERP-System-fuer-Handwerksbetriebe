-- Zeitbuchungen ohne Mitarbeiter/Startzeit erlauben (Projekt-Planpositionen/Kalkulationen)
ALTER TABLE zeitbuchung MODIFY COLUMN mitarbeiter_id BIGINT NULL;
ALTER TABLE zeitbuchung MODIFY COLUMN start_zeit DATETIME(6) NULL;
