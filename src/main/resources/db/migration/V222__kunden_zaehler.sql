-- Atomarer Zähler für Kundennummern.
-- Garantiert mit row-level PESSIMISTIC_WRITE-Lock fortlaufende, eindeutige
-- Kundennummern – auch bei gleichzeitigen manuellen Anlagen via
-- KundeController und automatischen Anlagen via AnfrageFunnelService.
-- Initialwert: bestehendes MAX(kundennummer) + 1, mindestens 1000.
CREATE TABLE IF NOT EXISTS kunden_zaehler (
    id INT NOT NULL PRIMARY KEY,
    naechste_nummer BIGINT NOT NULL
);

INSERT INTO kunden_zaehler (id, naechste_nummer)
SELECT 1, GREATEST(
    COALESCE((SELECT MAX(CAST(kundennummer AS UNSIGNED)) FROM kunde), 0) + 1,
    1000
)
WHERE NOT EXISTS (SELECT 1 FROM kunden_zaehler WHERE id = 1);
