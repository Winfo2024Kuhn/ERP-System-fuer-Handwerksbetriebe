ALTER TABLE materialkosten
    ADD COLUMN artikel_id_snapshot BIGINT NULL,
    ADD COLUMN lieferanten_artikel_preis_id BIGINT NULL,
    ADD COLUMN lieferantenname_snapshot VARCHAR(255) NULL,
    ADD COLUMN menge_snapshot DECIMAL(19,6) NULL,
    ADD COLUMN einheit_snapshot ENUM('STUECK','METER','KILOGRAMM','TONNE','QUADRATMETER') NULL,
    ADD COLUMN preis_je_einheit_snapshot DECIMAL(19,6) NULL,
    ADD COLUMN preisquelle_snapshot VARCHAR(255) NULL,
    ADD COLUMN preisnotiz_snapshot VARCHAR(1000) NULL;
