-- Von Hand gelöste Verknüpfungen zwischen Lieferanten-Dokumenten.
-- Hängt jemand in der Bestellübersicht einen Beleg ab ("gehört nicht dazu"),
-- merkt sich das System das Paar. Der automatische Abgleich (Mail-Import,
-- Neu-Verknüpfen beim Start) verknüpft gesperrte Paare nie wieder.
-- Verknüpft jemand das Paar später von Hand, wird die Sperre wieder gelöscht.

CREATE TABLE IF NOT EXISTS lieferant_dokument_verknuepfung_gesperrt (
    dokument_id BIGINT NOT NULL,
    verknuepft_id BIGINT NOT NULL,
    gesperrt_am DATETIME(6) NOT NULL,
    PRIMARY KEY (dokument_id, verknuepft_id),
    CONSTRAINT fk_ld_verkn_gesperrt_dokument FOREIGN KEY (dokument_id)
        REFERENCES lieferant_dokument(id) ON DELETE CASCADE,
    CONSTRAINT fk_ld_verkn_gesperrt_verknuepft FOREIGN KEY (verknuepft_id)
        REFERENCES lieferant_dokument(id) ON DELETE CASCADE,
    INDEX idx_ld_verkn_gesperrt_verknuepft (verknuepft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
