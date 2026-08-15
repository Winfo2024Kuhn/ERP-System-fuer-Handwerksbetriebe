-- Einmalige Bestandskorrektur: Preise der 100er-Staffel auf je-Stueck-Niveau.
--
-- Muss NACH V359 laufen. Vorher hat die Preisspalte nur zwei Nachkommastellen -
-- das Teilen durch 100 wuerde die Preise reihenweise auf 0,00 EUR runden und die
-- Korrektur waere schlimmer als der Fehler.
--
-- ---------------------------------------------------------------------------
-- Befund (lokale Datenbank, 15.08.2026)
-- ---------------------------------------------------------------------------
-- 1745 Artikel tragen preiseinheit = '100' und haben einen aktuellen
-- Lieferantenpreis. Der Bestand zerfaellt sauber in zwei Gruppen:
--
--   973 Zeilen mit Preis >= 0,10 EUR - hier steht noch der Preis je 100 Stueck
--       in der Spalte. Die Kalkulation multipliziert ihn mit der Stueckzahl und
--       rechnet damit hundertfach zu hoch.
--   772 Zeilen mit Preis <  0,10 EUR - bereits korrekt auf je ein Stueck
--       normiert. 12 davon stehen auf 0,00 EUR, weil sie an der alten
--       Zwei-Stellen-Grenze weggerundet wurden; die holt diese Migration nicht
--       zurueck, dafuer fehlt der Ausgangswert.
--
-- ---------------------------------------------------------------------------
-- Die Schwelle 0,10 EUR - und was sie kostet
-- ---------------------------------------------------------------------------
-- Sie trennt genau diese beiden vermessenen Gruppen. Eine praezisere Regel gibt
-- es nicht: Der Datenbank sieht man einer Zahl nicht an, ob sie fuer 1 oder fuer
-- 100 Stueck gilt.
--
-- Der Preis dafuer ist benannt: Ein echter Cent-Artikel, der bereits korrekt je
-- Stueck bei 0,12 EUR steht, wuerde hier faelschlich durch 100 geteilt und
-- landete bei 0,0012 EUR. Im vermessenen Bestand kommt dieser Fall nicht vor -
-- die Luecke zwischen den beiden Gruppen ist eindeutig. Sicher ist das nicht,
-- deshalb die Backup-Tabelle unten.
--
-- Bewusst NICHT eingebaut: ein Plausibilitaets- oder Ausreisser-Riegel. Genau so
-- ein Korridor (0,50-10,00 EUR je Kilogramm) hat frueher in der Preisuebernahme
-- Stueckpreise stillschweigend verfaelscht. Diese Migration ist eine einmalige,
-- dokumentierte und umkehrbare Bestandsbereinigung - keine laufende Automatik.
--
-- ---------------------------------------------------------------------------
-- Rueckfahrkarte
-- ---------------------------------------------------------------------------
-- lieferanten_artikel_preise_korrektur_v360 haelt jeden angefassten Preis mit
-- altem und neuem Wert fest. Die Korrektur laesst sich damit vollstaendig
-- zuruecknehmen:
--
--   UPDATE lieferanten_artikel_preise p
--   JOIN lieferanten_artikel_preise_korrektur_v360 k ON k.preis_id = p.id
--   SET p.preis = k.preis_alt
--   WHERE p.preis = k.preis_neu;
--
-- ---------------------------------------------------------------------------
-- Idempotent
-- ---------------------------------------------------------------------------
-- Der zweite Lauf aendert nichts. Das Sicherungsnetz ist die Backup-Tabelle:
-- INSERT IGNORE laesst bereits gesicherte Zeilen liegen (preis_id ist
-- Primaerschluessel), und das UPDATE fasst nur Zeilen an, die noch auf ihrem
-- alten Wert stehen. Ohne diese Bindung an preis_alt wuerde ein zweiter Lauf
-- teurere Zeilen ein zweites Mal durch 100 teilen.

-- ---------------------------------------------------------------------------
-- 1. Backup-Tabelle
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lieferanten_artikel_preise_korrektur_v360 (
    preis_id      BIGINT NOT NULL PRIMARY KEY,
    artikel_id    BIGINT NULL,
    preis_alt     DECIMAL(19,4) NULL,
    preis_neu     DECIMAL(19,4) NULL,
    korrigiert_am DATETIME(6) NULL
);

-- ---------------------------------------------------------------------------
-- 2. Betroffene Zeilen sichern - VOR dem UPDATE
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO lieferanten_artikel_preise_korrektur_v360
    (preis_id, artikel_id, preis_alt, preis_neu, korrigiert_am)
SELECT p.id, p.artikel_id, p.preis, p.preis / 100, NOW(6)
FROM lieferanten_artikel_preise p
JOIN artikel a ON a.id = p.artikel_id
WHERE p.aktuell = 1
  AND TRIM(a.preiseinheit) = '100'
  AND p.preis >= 0.10;

-- ---------------------------------------------------------------------------
-- 3. Korrektur
-- ---------------------------------------------------------------------------
-- Der neue Wert kommt aus der Backup-Tabelle statt aus einer erneuten Rechnung:
-- so kann das Ergebnis nicht von dem abweichen, was als Rueckfahrkarte
-- protokolliert ist. Die Bedingung p.preis = k.preis_alt macht den Schritt
-- wiederholbar - beim zweiten Lauf trifft sie auf keine Zeile mehr zu.

UPDATE lieferanten_artikel_preise p
JOIN lieferanten_artikel_preise_korrektur_v360 k ON k.preis_id = p.id
SET p.preis = k.preis_neu
WHERE p.preis = k.preis_alt;
