ALTER TABLE materialkosten
    ADD COLUMN IF NOT EXISTS artikel_id_snapshot BIGINT NULL,
    ADD COLUMN IF NOT EXISTS lieferanten_artikel_preis_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS lieferantenname_snapshot VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS menge_snapshot DECIMAL(19,6) NULL,
    ADD COLUMN IF NOT EXISTS einheit_snapshot ENUM('STUECK','METER','KILOGRAMM','TONNE','QUADRATMETER') NULL,
    ADD COLUMN IF NOT EXISTS preis_je_einheit_snapshot DECIMAL(19,6) NULL,
    ADD COLUMN IF NOT EXISTS preisquelle_snapshot VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS preisnotiz_snapshot VARCHAR(1000) NULL;
