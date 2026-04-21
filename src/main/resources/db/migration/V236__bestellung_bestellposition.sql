-- ═══════════════════════════════════════════════════════════════
-- V236: Bestellwesen sauber modellieren (Kopf + Positionen)
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: Bisher war eine „Bestellung" nur ein aggregierter
-- Zustand aus `artikel_in_projekt.bestellt=true` — es gab keinen
-- Bestellkopf mit Nummer, Status oder Liefertermin. Für IDS /
-- IDS-CONNECT (Integrierte Datenschnittstelle des ZVSHK) und für
-- die Wareneingangskontrolle nach DIN EN 1090 brauchen wir echte
-- Bestellungs-Datensätze mit laufender Positionsnummer.
--
-- Commit A (dieser): Neue Entities `bestellung` + `bestellposition`.
--   `artikel_in_projekt` bleibt unverändert (Kalkulationsposition),
--   eine Bestellposition verweist per FK auf ihre Ursprungs-AiP.
-- Commit B (Folge-Migration): Wareneingangskontrolle hängt an
--   `bestellung` / `bestellposition`.
-- Späteres Aufräumen: `artikel_in_projekt.bestellt`/`.bestelltAm`/
--   `.exportiertAm`/`.lieferant_id` werden später entfernt, wenn
--   das neue Modell stabil läuft und alle Konsumenten umgestellt
--   sind. Bis dahin laufen beide Welten parallel.
--
-- MySQL 8 / idempotent: CREATE TABLE IF NOT EXISTS.

-- ───────────────────────────────────────────────────────────────
-- 1) Bestellkopf
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bestellung (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    bestellnummer           VARCHAR(50)  NOT NULL,     -- interne Nr., z. B. B-2026-0042
    externe_bestellnummer   VARCHAR(100) NULL,         -- IDS: unsere Nr. beim Lieferanten
    lieferant_id            BIGINT       NULL,         -- NULL bei Freitext ohne Stammlieferant
    projekt_id              BIGINT       NULL,         -- NULL bei Lagerbestellung
    status                  VARCHAR(30)  NOT NULL DEFAULT 'ENTWURF',
    bestellt_am             DATE         NULL,
    versendet_am            DATETIME     NULL,
    liefertermin_soll       DATE         NULL,
    lieferadresse           TEXT         NULL,
    kommentar               TEXT         NULL,
    exportiert_am           DATETIME     NULL,
    ids_referenz            VARCHAR(100) NULL,         -- Platzhalter für IDS-Vorgangs-ID
    erstellt_von_id         BIGINT       NULL,
    erstellt_von_name       VARCHAR(255) NULL,         -- GoBD-Snapshot
    erstellt_am             DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bestellung_nummer (bestellnummer),
    KEY idx_bestellung_lieferant (lieferant_id),
    KEY idx_bestellung_projekt (projekt_id),
    KEY idx_bestellung_status (status),
    CONSTRAINT fk_bestellung_lieferant
        FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE SET NULL,
    CONSTRAINT fk_bestellung_projekt
        FOREIGN KEY (projekt_id) REFERENCES projekt(id) ON DELETE SET NULL,
    CONSTRAINT fk_bestellung_erstellt_von
        FOREIGN KEY (erstellt_von_id) REFERENCES mitarbeiter(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ───────────────────────────────────────────────────────────────
-- 2) Bestellposition
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bestellposition (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    bestellung_id              BIGINT       NOT NULL,
    positionsnummer            INT          NOT NULL,   -- 1..n pro Bestellung (IDS)
    aus_artikel_in_projekt_id  BIGINT       NULL,       -- Herkunft: Kalkulationsposition
    -- Artikelbezug: entweder Stammartikel ODER Freitext
    artikel_id                 BIGINT       NULL,
    externe_artikelnummer      VARCHAR(100) NULL,       -- Artikelnr. beim Lieferanten (IDS)
    freitext_produktname       VARCHAR(500) NULL,
    freitext_produkttext       TEXT         NULL,
    kategorie_id               INT          NULL,
    -- Menge & Preis (Snapshot zum Bestellzeitpunkt)
    menge                      DECIMAL(19,3) NULL,
    einheit                    VARCHAR(50)  NULL,
    stueckzahl                 INT          NULL,
    preis_pro_einheit          DECIMAL(19,2) NULL,
    kilogramm                  DECIMAL(19,2) NULL,
    -- Fertigungsdetails
    schnitt_form               VARCHAR(255) NULL,
    anschnitt_winkel_links     VARCHAR(50)  NULL,
    anschnitt_winkel_rechts    VARCHAR(50)  NULL,
    fixmass_mm                 INT          NULL,
    -- EN 1090
    zeugnis_anforderung        VARCHAR(30)  NULL,
    kommentar                  TEXT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bestellposition_nr (bestellung_id, positionsnummer),
    KEY idx_bp_bestellung (bestellung_id),
    KEY idx_bp_herkunft (aus_artikel_in_projekt_id),
    KEY idx_bp_artikel (artikel_id),
    CONSTRAINT fk_bp_bestellung
        FOREIGN KEY (bestellung_id) REFERENCES bestellung(id) ON DELETE CASCADE,
    CONSTRAINT fk_bp_herkunft
        FOREIGN KEY (aus_artikel_in_projekt_id) REFERENCES artikel_in_projekt(id) ON DELETE SET NULL,
    CONSTRAINT fk_bp_artikel
        FOREIGN KEY (artikel_id) REFERENCES artikel(id) ON DELETE SET NULL,
    CONSTRAINT fk_bp_kategorie
        FOREIGN KEY (kategorie_id) REFERENCES kategorie(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ───────────────────────────────────────────────────────────────
-- 3) Backfill aus bestehenden `artikel_in_projekt`-Einträgen
-- ───────────────────────────────────────────────────────────────
-- Nur wenn noch keine Bestellung existiert (idempotent bei Re-Run).
-- Für jede Gruppe (lieferant_id, projekt_id) aus bestellten AiPs
-- entsteht eine Bestellung mit Status VERSENDET. Altdaten bekommen
-- eine Bestellnummer „B-ALT-<laufnr>" (leicht als Migrationsdaten
-- erkennbar).
INSERT INTO bestellung (
    bestellnummer, lieferant_id, projekt_id, status,
    bestellt_am, exportiert_am, versendet_am, erstellt_am, erstellt_von_name
)
SELECT
    CONCAT('B-ALT-', LPAD(
        ROW_NUMBER() OVER (ORDER BY
            COALESCE(aip.lieferant_id, 0),
            COALESCE(aip.projekt_id, 0)),
        6, '0')) AS bestellnummer,
    aip.lieferant_id,
    aip.projekt_id,
    'VERSENDET' AS status,
    MIN(aip.bestellt_am)  AS bestellt_am,
    MAX(aip.exportiert_am) AS exportiert_am,
    -- falls schon exportiert, als versendet markieren
    MAX(aip.exportiert_am) AS versendet_am,
    NOW() AS erstellt_am,
    'Migration V236' AS erstellt_von_name
FROM artikel_in_projekt aip
WHERE aip.bestellt = TRUE
  AND NOT EXISTS (SELECT 1 FROM bestellung)     -- idempotent: nur beim ersten Lauf
GROUP BY aip.lieferant_id, aip.projekt_id;

-- Jetzt die Positionen anhängen. Match über (lieferant, projekt)
-- mit NULL-safe-Equal (`<=>`).
INSERT INTO bestellposition (
    bestellung_id, positionsnummer, aus_artikel_in_projekt_id,
    artikel_id, freitext_produktname, freitext_produkttext,
    kategorie_id, menge, einheit, stueckzahl,
    preis_pro_einheit, kilogramm,
    schnitt_form, anschnitt_winkel_links, anschnitt_winkel_rechts,
    fixmass_mm, zeugnis_anforderung, kommentar
)
SELECT
    b.id AS bestellung_id,
    ROW_NUMBER() OVER (PARTITION BY b.id ORDER BY aip.id) AS positionsnummer,
    aip.id AS aus_artikel_in_projekt_id,
    aip.artikel_id,
    aip.freitext_produktname,
    aip.freitext_produkttext,
    aip.kategorie_id,
    -- Mengen-Snapshot: Freitext bevorzugt, sonst meter, sonst Stückzahl
    COALESCE(aip.freitext_menge, aip.meter, CAST(aip.stueckzahl AS DECIMAL(19,3))) AS menge,
    COALESCE(aip.freitext_einheit,
             CASE WHEN aip.meter IS NOT NULL THEN 'm' ELSE 'Stück' END) AS einheit,
    aip.stueckzahl,
    aip.preis_pro_stueck,
    aip.kilogramm,
    aip.schnitt_form,
    aip.anschnitt_winkel_links,
    aip.anschnitt_winkel_rechts,
    aip.fixmass_mm,
    aip.zeugnis_anforderung,
    aip.kommentar
FROM artikel_in_projekt aip
JOIN bestellung b
  ON (b.lieferant_id <=> aip.lieferant_id)
 AND (b.projekt_id   <=> aip.projekt_id)
WHERE aip.bestellt = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM bestellposition bp
      WHERE bp.aus_artikel_in_projekt_id = aip.id
  );
