-- ═══════════════════════════════════════════════════════════════
-- V240: Schnittbild als echte FK + Winkel als DECIMAL(5,2)
-- ═══════════════════════════════════════════════════════════════
-- Issue #52: Metallbau-Profile haben ein Schnittbild (z. B. „LG") und
-- zwei Winkel (links + rechts), weil ein Profil links gerade und
-- rechts schräg angeschnitten sein kann. Das Bild selbst reicht
-- einmal pro Position.
--
-- Bisher:
--   - schnitt_form: freier String-Code (z. B. "LG", "TD", "A")
--   - anschnitt_winkel_*: VARCHAR(50)
--
-- Neu:
--   - schnittbild_id: FK auf schnittbilder(id) auf den drei
--     Positions-Aggregaten (artikel_in_projekt, preisanfrage_position,
--     bestellposition)
--   - anschnitt_winkel_links/rechts: DECIMAL(5,2) fuer Nachkommawerte
--     (z. B. 45.5°)
--
-- Datenmigration: der Alt-Code wird via Join gegen schnittbilder.form
-- auf die neue FK aufgeloest, danach wird die Alt-Spalte entfernt.
--
-- MySQL 8.

-- ───────────────────────────────────────────────────────────────
-- 1) artikel_in_projekt
-- ───────────────────────────────────────────────────────────────
ALTER TABLE artikel_in_projekt
    ADD COLUMN schnittbild_id BIGINT NULL,
    ADD CONSTRAINT fk_aip_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;

UPDATE artikel_in_projekt aip
    JOIN schnittbilder sb ON sb.form = aip.schnitt_form
SET aip.schnittbild_id = sb.id
WHERE aip.schnitt_form IS NOT NULL;

-- Winkel: nicht-numerische Reste bereinigen, dann Typ umstellen
UPDATE artikel_in_projekt
SET anschnitt_winkel_links = NULL
WHERE anschnitt_winkel_links IS NOT NULL
  AND anschnitt_winkel_links NOT REGEXP '^-?[0-9]+(\\.[0-9]+)?$';
UPDATE artikel_in_projekt
SET anschnitt_winkel_rechts = NULL
WHERE anschnitt_winkel_rechts IS NOT NULL
  AND anschnitt_winkel_rechts NOT REGEXP '^-?[0-9]+(\\.[0-9]+)?$';

ALTER TABLE artikel_in_projekt
    MODIFY COLUMN anschnitt_winkel_links  DECIMAL(5,2) NULL,
    MODIFY COLUMN anschnitt_winkel_rechts DECIMAL(5,2) NULL,
    DROP COLUMN schnitt_form;

-- ───────────────────────────────────────────────────────────────
-- 2) bestellposition
-- ───────────────────────────────────────────────────────────────
ALTER TABLE bestellposition
    ADD COLUMN schnittbild_id BIGINT NULL,
    ADD CONSTRAINT fk_bp_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;

UPDATE bestellposition bp
    JOIN schnittbilder sb ON sb.form = bp.schnitt_form
SET bp.schnittbild_id = sb.id
WHERE bp.schnitt_form IS NOT NULL;

UPDATE bestellposition
SET anschnitt_winkel_links = NULL
WHERE anschnitt_winkel_links IS NOT NULL
  AND anschnitt_winkel_links NOT REGEXP '^-?[0-9]+(\\.[0-9]+)?$';
UPDATE bestellposition
SET anschnitt_winkel_rechts = NULL
WHERE anschnitt_winkel_rechts IS NOT NULL
  AND anschnitt_winkel_rechts NOT REGEXP '^-?[0-9]+(\\.[0-9]+)?$';

ALTER TABLE bestellposition
    MODIFY COLUMN anschnitt_winkel_links  DECIMAL(5,2) NULL,
    MODIFY COLUMN anschnitt_winkel_rechts DECIMAL(5,2) NULL,
    DROP COLUMN schnitt_form;

-- ───────────────────────────────────────────────────────────────
-- 3) preisanfrage_position (bisher keine Schnitt-Daten)
-- ───────────────────────────────────────────────────────────────
ALTER TABLE preisanfrage_position
    ADD COLUMN schnittbild_id          BIGINT       NULL,
    ADD COLUMN anschnitt_winkel_links  DECIMAL(5,2) NULL,
    ADD COLUMN anschnitt_winkel_rechts DECIMAL(5,2) NULL,
    ADD CONSTRAINT fk_pap_schnittbild
        FOREIGN KEY (schnittbild_id) REFERENCES schnittbilder(id) ON DELETE SET NULL;
