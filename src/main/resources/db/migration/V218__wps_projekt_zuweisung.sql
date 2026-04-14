-- V218: WPS-Projekt-Zuweisung mit Schweißer-Individualisierung
-- Ermöglicht es, WPS einem Projekt mit konkretem Schweißer, Prüfer und Datum zuzuweisen.

CREATE TABLE IF NOT EXISTS wps_projekt_zuweisung (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    wps_id              BIGINT NOT NULL,
    projekt_id          BIGINT NOT NULL,
    schweisser_id       BIGINT                   COMMENT 'Mitarbeiter der diese WPS ausführt',
    schweisspruefer     VARCHAR(200)             COMMENT 'Name des verantwortlichen Schweißprüfers',
    einsatz_datum       DATE                     COMMENT 'Geplantes/tatsächliches Einsatzdatum',
    bemerkung           TEXT,
    erstellt_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_wpz_wps       FOREIGN KEY (wps_id)        REFERENCES wps        (id) ON DELETE CASCADE,
    CONSTRAINT fk_wpz_projekt   FOREIGN KEY (projekt_id)    REFERENCES projekt     (id) ON DELETE CASCADE,
    CONSTRAINT fk_wpz_schweisser FOREIGN KEY (schweisser_id) REFERENCES mitarbeiter (id) ON DELETE SET NULL,
    INDEX idx_wpz_projekt (projekt_id),
    INDEX idx_wpz_wps     (wps_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
