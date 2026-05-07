-- Manipulationssichere Hash-Kette für den Audit-Log.
--
-- Jeder Audit-Eintrag bekommt einen entry_hash (SHA-256 über alle relevanten
-- Felder + previous_hash). Wer einen alten Eintrag manipuliert, bricht die
-- Kette aller nachfolgenden Einträge -> ein Steuerprüfer kann das in Sekunden
-- maschinell verifizieren.
--
-- Eindeutige Reihenfolge wird über chain_index sichergestellt; eine
-- Singleton-Tabelle audit_chain_state hält den letzten Hash und Index, damit
-- wir beim Insert per FOR UPDATE atomar anhängen können.

ALTER TABLE ausgangs_geschaeftsdokument_audit
    ADD COLUMN chain_index BIGINT NULL AFTER id,
    ADD COLUMN previous_hash CHAR(64) NULL AFTER inhalt_hash,
    ADD COLUMN entry_hash CHAR(64) NULL AFTER previous_hash;

-- chain_index wird nach Backfill UNIQUE; während Backfill darf NULL sein.
CREATE UNIQUE INDEX uq_audit_chain_index
    ON ausgangs_geschaeftsdokument_audit (chain_index);

-- Singleton-Tabelle für den Kettenkopf. id = 1 ist Pflicht.
CREATE TABLE IF NOT EXISTS audit_chain_state (
    id INT NOT NULL,
    last_chain_index BIGINT NOT NULL DEFAULT -1,
    last_entry_hash CHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_audit_chain_state_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO audit_chain_state (id, last_chain_index, last_entry_hash, updated_at)
VALUES (1, -1, NULL, NOW(6))
ON DUPLICATE KEY UPDATE id = id;
