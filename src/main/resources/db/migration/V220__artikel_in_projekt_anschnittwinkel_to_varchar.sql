-- Schema-Validation-Fix: Die Entity ArtikelInProjekt deklariert
-- anschnittWinkelLinks/Rechts als String (siehe auch alle DTOs und das
-- Frontend), die DB-Spalten waren historisch jedoch als DECIMAL angelegt.
-- Hibernate (ddl-auto=validate) verweigert daher beim Start die
-- Initialisierung des EntityManagerFactory.
--
-- MODIFY COLUMN konvertiert bestehende DECIMAL-Werte automatisch in deren
-- String-Repräsentation; NULL-Werte bleiben NULL.
ALTER TABLE artikel_in_projekt
    MODIFY COLUMN anschnitt_winkel_links VARCHAR(255) NULL;

ALTER TABLE artikel_in_projekt
    MODIFY COLUMN anschnitt_winkel_rechts VARCHAR(255) NULL;
