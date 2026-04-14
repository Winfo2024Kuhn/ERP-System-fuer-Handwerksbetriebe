-- =========================================================
-- V219: EN 1090 – Rollen & Mitarbeiter-Qualifikationen
-- =========================================================

-- Zentrale Rollenliste (WPK-Leiter, Schweißaufsicht, etc.)
CREATE TABLE IF NOT EXISTS en1090_rolle (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kurztext    VARCHAR(100) NOT NULL,
    beschreibung TEXT,
    sortierung  INT NOT NULL DEFAULT 0,
    aktiv       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_en1090_rolle_kurztext (kurztext)
);

-- N:M – Mitarbeiter ↔ EN-1090-Rollen
CREATE TABLE IF NOT EXISTS mitarbeiter_en1090_rolle (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    mitarbeiter_id  BIGINT NOT NULL,
    rolle_id        BIGINT NOT NULL,
    CONSTRAINT fk_men1090_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id) ON DELETE CASCADE,
    CONSTRAINT fk_men1090_rolle       FOREIGN KEY (rolle_id)       REFERENCES en1090_rolle(id) ON DELETE CASCADE,
    UNIQUE KEY uq_mitarbeiter_rolle (mitarbeiter_id, rolle_id)
);

-- Freie Qualifikationen/Weiterbildungen pro Mitarbeiter
CREATE TABLE IF NOT EXISTS mitarbeiter_qualifikation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    mitarbeiter_id  BIGINT NOT NULL,
    bezeichnung     VARCHAR(500) NOT NULL,
    beschreibung    TEXT,
    datum           DATE,
    dokument_id     BIGINT,
    erstellt_am     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mq_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id) ON DELETE CASCADE,
    CONSTRAINT fk_mq_dokument    FOREIGN KEY (dokument_id)    REFERENCES mitarbeiter_dokument(id) ON DELETE SET NULL
);

-- =========================================================
-- Seed: 10 Standard-Rollen gemäß EN 1090 Personalliste
-- =========================================================
INSERT IGNORE INTO en1090_rolle (kurztext, beschreibung, sortierung) VALUES
('Leiter WPK',                 'Leiter der Werkseigenen Produktionskontrolle – verantwortlich für das gesamte WPK-System nach EN 1090.',                     10),
('Stellvertreter WPK',         'Stellvertretender WPK-Leiter – übernimmt alle Aufgaben und Verantwortlichkeiten des WPK-Leiters bei dessen Abwesenheit.',    20),
('Schweißaufsicht (SAP)',       'Schweiß-Aufsichts-Person nach EN ISO 14731 – überwacht und dokumentiert alle Schweißarbeiten im Betrieb.',                   30),
('Stellvertreter SAP',         'Stellvertretende Schweißaufsicht – vertritt die SAP bei Abwesenheit.',                                                        40),
('Schweißer (mit Prüfung)',     'Schweißer mit gültiger Schweißerprüfung nach DIN EN ISO 9606-1 (3-Jahres-Erneuerung für EXC 2 Pflicht).',                   50),
('Schweißer (ohne Prüfung)',    'Schweißer ohne aktuelle Schweißerprüfung – nur für einfache, nicht prüfungspflichtige Schweißaufgaben einsetzbar.',           60),
('Montageleiter',               'Leiter der Montagetätigkeiten auf der Baustelle – verantwortlich für die fachgerechte Montage von Stahlbauteilen.',          70),
('Monteur',                     'Monteur für die Montage von Stahlkonstruktionen auf Baustellen gemäß Montageplänen und EN 1090.',                            80),
('Korrosionsschutzaufsicht',    'Überwacht und dokumentiert alle Korrosionsschutzmaßnahmen (Grundierung, Beschichtung, Verzinkung) gemäß EN ISO 12944.',     90),
('Korrosionsschützer',          'Führt Korrosionsschutzarbeiten aus: Strahlen, Grundieren, Lackieren, Beschichten an Stahlbauteilen.',                       100);
