-- Leere Versionsnummern (optimistisches Sperren, @Version) auf 0 setzen.
--
-- Zeilen, die Migrationen angelegt haben, bevor die Spalte version kam (z. B.
-- die Stammdaten-Artikel aus V343), stehen mit version = NULL da. Hibernate
-- kann solche Datensaetze weder aendern (NullPointerException beim Hochzaehlen)
-- noch auf PostgreSQL sperren. Betrifft jede Neuinstallation und evtl. alte
-- Zeilen auf bestehenden Servern. Idempotent: aendert nur noch leere Werte.
-- Zwilling: db/migration/V407__leere_versionen_auffuellen.sql

UPDATE anfrage SET version = 0 WHERE version IS NULL;
UPDATE arbeitsgang SET version = 0 WHERE version IS NULL;
UPDATE artikel SET version = 0 WHERE version IS NULL;
UPDATE ausgangs_geschaeftsdokument SET version = 0 WHERE version IS NULL;
UPDATE firmeninformation SET version = 0 WHERE version IS NULL;
UPDATE kunde SET version = 0 WHERE version IS NULL;
UPDATE lieferant_dokument SET version = 0 WHERE version IS NULL;
UPDATE lieferant_reklamation SET version = 0 WHERE version IS NULL;
UPDATE lieferanten SET version = 0 WHERE version IS NULL;
UPDATE mitarbeiter SET version = 0 WHERE version IS NULL;
UPDATE produktkategorie SET version = 0 WHERE version IS NULL;
UPDATE projekt SET version = 0 WHERE version IS NULL;
UPDATE textbaustein SET version = 0 WHERE version IS NULL;
