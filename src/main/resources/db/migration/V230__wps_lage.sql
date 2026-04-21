-- V230: Mehrlagen-Unterstützung für WPS (Schweißanweisung nach EN ISO 15609-1).
-- Jede WPS kann mehrere Schweißlagen haben (Wurzel, Füll-, Decklage) mit jeweils
-- eigenen Parameterbereichen. Die Darstellung auf dem WPS-Ausdruck erfolgt als Matrix.

CREATE TABLE IF NOT EXISTS wps_lage (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    wps_id         BIGINT NOT NULL,
    nummer         INT NOT NULL                                 COMMENT 'Reihenfolge ab 1 (1 = erste zu schweißende Lage)',
    typ            VARCHAR(20) NOT NULL                         COMMENT 'WURZEL | FUELL | DECK',
    current_a      DECIMAL(6,1)                                 COMMENT 'Stromstärke Zielwert in A',
    voltage_v      DECIMAL(5,2)                                 COMMENT 'Spannung Zielwert in V',
    wire_speed     DECIMAL(5,2)                                 COMMENT 'Drahtvorschub in m/min (nur MAG/MIG)',
    filler_dia_mm  DECIMAL(4,2)                                 COMMENT 'Zusatzwerkstoff-Durchmesser in mm',
    gas_flow       DECIMAL(5,1)                                 COMMENT 'Schutzgasmenge in l/min',
    bemerkung      VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT fk_wps_lage_wps FOREIGN KEY (wps_id) REFERENCES wps (id) ON DELETE CASCADE,
    INDEX idx_wps_lage_wps (wps_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
