-- ═══════════════════════════════════════════════════════════════
-- V234: Verknüpfung Leistung ↔ WPS (N:M) + Audit für Auto-Zuordnung
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: Eine Leistung (z.B. "Kehlnaht Stahl") braucht u.U.
-- mehrere Schweißanweisungen (WPS), und eine WPS kann für mehrere
-- Leistungen verwendet werden. Beim Anlegen eines Dokuments
-- (Angebot/Auftrag) werden über diese Verknüpfung die benötigten
-- WPS automatisch dem Projekt zugeordnet.
--
-- Entities:
--   - Leistung.verknuepfteWps (@ManyToMany) -> Tabelle leistung_wps
--   - Separate Audit-Tabelle wps_projekt_auto_source hält fest,
--     welche WPS durch welche Leistung automatisch einem Projekt
--     zugeordnet wurde. Die eigentliche Zuordnung bleibt in wps_projekt
--     (pure @ManyToMany), damit die JPA-Mapping-Struktur einfach bleibt.

CREATE TABLE IF NOT EXISTS leistung_wps (
    leistung_id BIGINT NOT NULL,
    wps_id      BIGINT NOT NULL,
    PRIMARY KEY (leistung_id, wps_id),
    CONSTRAINT fk_leistung_wps_leistung
        FOREIGN KEY (leistung_id) REFERENCES leistung(id) ON DELETE CASCADE,
    CONSTRAINT fk_leistung_wps_wps
        FOREIGN KEY (wps_id) REFERENCES wps(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wps_projekt_auto_source (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    wps_id      BIGINT NOT NULL,
    projekt_id  BIGINT NOT NULL,
    leistung_id BIGINT NOT NULL,
    erstellt_am DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wps_projekt_auto (wps_id, projekt_id, leistung_id),
    CONSTRAINT fk_wpas_wps
        FOREIGN KEY (wps_id) REFERENCES wps(id) ON DELETE CASCADE,
    CONSTRAINT fk_wpas_projekt
        FOREIGN KEY (projekt_id) REFERENCES projekt(id) ON DELETE CASCADE,
    CONSTRAINT fk_wpas_leistung
        FOREIGN KEY (leistung_id) REFERENCES leistung(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
