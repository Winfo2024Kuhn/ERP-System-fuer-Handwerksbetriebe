-- ═══════════════════════════════════════════════════════════════
-- V235: Digitale SAP-Freigabe auf WPS-Vorlagen
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: EN ISO 15614-1 / EN ISO 14731 verlangt, dass eine
-- Schweißanweisung (WPS) vor dem produktiven Einsatz von der
-- Schweißaufsichtsperson (SAP) freigegeben und mit Unterschrift
-- „Unterschrift SAP" versehen wird. Statt Papier-Unterschrift
-- wird das hier GoBD-konform als digitale Freigabe abgebildet
-- (wer, wann, unveränderlich außer vom Urheber zurückgezogen).
-- Bei inhaltlicher Änderung der WPS werden alle Freigaben zurück-
-- gesetzt, damit eine signierte WPS nicht unbemerkt nachträglich
-- verändert werden kann.
--
-- Nur SAP-User signieren – der Schweißer unterschreibt formal
-- nichts (EN ISO 14731: Schweißer ist Konsument der WPS). Deshalb
-- braucht die Tabelle auch kein Rollen-Feld.
--
-- Entities:
--   - WpsFreigabe (OneToMany auf Wps)
--
-- MySQL 8 / idempotent: CREATE TABLE IF NOT EXISTS analog V234.

CREATE TABLE IF NOT EXISTS wps_freigabe (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    wps_id             BIGINT       NOT NULL,
    mitarbeiter_id     BIGINT       NULL,
    mitarbeiter_name   VARCHAR(255) NOT NULL,
    zeitpunkt          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wps_freigabe_wps (wps_id),
    KEY idx_wps_freigabe_mitarbeiter (mitarbeiter_id),
    CONSTRAINT fk_wps_freigabe_wps
        FOREIGN KEY (wps_id) REFERENCES wps(id) ON DELETE CASCADE,
    CONSTRAINT fk_wps_freigabe_mitarbeiter
        FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
