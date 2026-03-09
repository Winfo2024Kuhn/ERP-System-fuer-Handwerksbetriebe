-- V204: Sicherheitsnetz für monats_saldo Tabelle (Performance-Cache).
--
-- Die Tabelle wird primär durch Spring JPA (ddl-auto=update) erstellt.
-- Dieses Script dient als Fallback und erstellt die Tabelle nur, wenn sie noch nicht existiert.
-- Die Befüllung mit Daten erfolgt automatisch beim Anwendungsstart über MonatsSaldoWarmupService.
--
-- Die Tabelle dient ausschließlich als Performance-Cache. Alle rechtsgültigen Quelldaten
-- (zeitbuchung, abwesenheit, zeitkonto_korrektur) bleiben unverändert mit ihrem Audit-Trail bestehen.

CREATE TABLE IF NOT EXISTS monats_saldo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mitarbeiter_id BIGINT NOT NULL,
    jahr INT NOT NULL,
    monat INT NOT NULL,
    ist_stunden DECIMAL(10,2) NOT NULL DEFAULT 0,
    soll_stunden DECIMAL(10,2) NOT NULL DEFAULT 0,
    abwesenheits_stunden DECIMAL(10,2) NOT NULL DEFAULT 0,
    feiertags_stunden DECIMAL(10,2) NOT NULL DEFAULT 0,
    korrektur_stunden DECIMAL(10,2) NOT NULL DEFAULT 0,
    gueltig BOOLEAN NOT NULL DEFAULT TRUE,
    berechnet_am DATETIME NOT NULL,
    CONSTRAINT uk_monats_saldo_mitarbeiter_jahr_monat UNIQUE (mitarbeiter_id, jahr, monat),
    CONSTRAINT fk_monats_saldo_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id)
);

-- Index wird durch JPA ddl-auto=update über @Table(indexes=...) erstellt.
