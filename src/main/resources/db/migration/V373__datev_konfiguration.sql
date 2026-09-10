CREATE TABLE IF NOT EXISTS datev_konfiguration (
 id BIGINT NOT NULL PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0,
 ziel VARCHAR(10) NOT NULL DEFAULT 'LODAS',
 berater_nr VARCHAR(7) NOT NULL DEFAULT '',
 mandanten_nr VARCHAR(5) NOT NULL DEFAULT '',
 zuordnungen_json TEXT NOT NULL,
 aenderungszaehler BIGINT NOT NULL DEFAULT 0
);
INSERT INTO datev_konfiguration (id, version, ziel, berater_nr, mandanten_nr, zuordnungen_json, aenderungszaehler)
SELECT 1, 0, 'LODAS', '', '', '[]', 0
WHERE NOT EXISTS (SELECT 1 FROM datev_konfiguration WHERE id = 1);
CREATE TABLE IF NOT EXISTS datev_personalnummer (
 mitarbeiter_id BIGINT NOT NULL PRIMARY KEY,
 personalnummer VARCHAR(5) NOT NULL,
 normalisiert VARCHAR(5) NOT NULL,
 CONSTRAINT uk_datev_personalnummer_normalisiert UNIQUE (normalisiert),
 CONSTRAINT fk_datev_personalnummer_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter (id)
);
