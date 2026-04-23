-- ═══════════════════════════════════════════════════════════════
-- V241: SchnittAchse als Zwischenstufe zwischen Kategorie und Schnittbild
-- ═══════════════════════════════════════════════════════════════
-- Issue #52 Folge-Commit: Der bisherige Aufbau
--   Kategorie 1──N Schnittbild
-- passt nicht, weil ein Profil (z.B. U-Stahl) mehrere Achsen hat,
-- entlang derer geschnitten werden kann, und pro Achse jeweils
-- eigene Schnittbilder sinnvoll sind.
--
-- Neuer Aufbau:
--   Kategorie 1──N SchnittAchse 1──N Schnittbild
--
-- Beim Bedarf waehlt der User erst die Achse (Bild), dann das
-- konkrete Schnittbild (Bild) und zuletzt die Winkel links/rechts.
-- Der PDF-Plot zeigt pro Position mit Sonderzuschnitt beides:
-- Achsen-Bild + Schnittbild-Icon + Winkel.
--
-- Umbau-Strategie: Beide Schnitt-Tabellen werden aktuell noch nicht
-- produktiv befuellt (Seed kommt danach per Admin-Modal), also
-- droppen wir die Alt-Tabelle und legen sie sauber neu an.
--
-- MySQL 8.

-- ───────────────────────────────────────────────────────────────
-- 1) FK-Constraints auf Positionstabellen temporaer loesen
-- ───────────────────────────────────────────────────────────────
ALTER TABLE artikel_in_projekt      DROP FOREIGN KEY fk_aip_schnittbild;
ALTER TABLE bestellposition         DROP FOREIGN KEY fk_bp_schnittbild;
ALTER TABLE preisanfrage_position   DROP FOREIGN KEY fk_pap_schnittbild;

-- ───────────────────────────────────────────────────────────────
-- 2) Alt-Tabelle droppen (leer)
-- ───────────────────────────────────────────────────────────────
DROP TABLE schnittbilder;

-- ───────────────────────────────────────────────────────────────
-- 3) Neue Achsen-Tabelle
-- ───────────────────────────────────────────────────────────────
CREATE TABLE schnitt_achse (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    bild_url        VARCHAR(500)  NOT NULL,
    kategorie_id    INT           NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schnitt_achse_kategorie
        FOREIGN KEY (kategorie_id) REFERENCES kategorie(id) ON DELETE CASCADE,
    INDEX idx_schnitt_achse_kategorie (kategorie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ───────────────────────────────────────────────────────────────
-- 4) Schnittbilder neu: FK jetzt auf Achse, "form" entfaellt
-- ───────────────────────────────────────────────────────────────
CREATE TABLE schnittbilder (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    bild_url_schnittbild    VARCHAR(500)  NOT NULL,
    schnitt_achse_id        BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_schnittbilder_bild_url (bild_url_schnittbild),
    CONSTRAINT fk_schnittbilder_achse
        FOREIGN KEY (schnitt_achse_id) REFERENCES schnitt_achse(id) ON DELETE CASCADE,
    INDEX idx_schnittbilder_achse (schnitt_achse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ───────────────────────────────────────────────────────────────
-- 5) FK-Constraints der Positionen wiederherstellen
-- ───────────────────────────────────────────────────────────────
ALTER TABLE artikel_in_projekt
    ADD CONSTRAINT fk_aip_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;

ALTER TABLE bestellposition
    ADD CONSTRAINT fk_bp_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;

ALTER TABLE preisanfrage_position
    ADD CONSTRAINT fk_pap_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;
