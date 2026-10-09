-- Rechnungs-Dokumentrechte fuer bestehende Buchhaltungs-/Buero-Abteilungen vorbelegen
--
-- WARUM
-- Offene Posten (/api/offene-posten), Rechnungsuebersicht (/api/rechnungsuebersicht/eingang),
-- Bestellungsuebersicht und Kostenstellen-Auswertung pruefen seit "Dokumentrechte
-- serverseitig durchsetzen" ZUSAETZLICH zum Abteilungs-Flag
-- (abteilung.darf_rechnungen_sehen / abteilung.darf_rechnungen_genehmigen) auch die
-- Dokumenttyp-Rechte in abteilung_dokument_berechtigung (Typ RECHNUNG bzw. GUTSCHRIFT,
-- Spalte darf_sehen). Bestehende Abteilungen, die Rechnungen bisher ueber das Flag
-- sehen durften, haben fuer diese beiden Typen aber keine Zeile - sie wuerden nach dem
-- Update nur noch leere Listen sehen.
-- darf_sehen fuer RECHNUNG/GUTSCHRIFT wirkt dabei nicht nur auf diese Endpoints, sondern auch
-- auf die Lieferanten-Dokumente, die Mail-Anhaenge, die Gesamtkosten-Kennzahl und die
-- Bestell-/Rechnungsuebersicht: ohne das Recht verschwinden dort die entsprechenden Eintraege.
--
-- WAS
-- Fuer jede Abteilung mit darf_rechnungen_sehen = TRUE ODER darf_rechnungen_genehmigen = TRUE
-- passiert in dieser Reihenfolge:
--   1. UPDATE: Vorhandene Zeilen der Typen 'RECHNUNG', 'GUTSCHRIFT' und 'EINGANGSRECHNUNG'
--      mit darf_sehen = FALSE werden auf darf_sehen = TRUE gesetzt. darf_scannen bleibt
--      unveraendert.
--   2. INSERT: Fehlt fuer 'RECHNUNG' bzw. 'GUTSCHRIFT' die Zeile, wird sie angelegt:
--      darf_sehen = TRUE, darf_scannen = FALSE
--      (Sehen wie bisher, Hochladen/Scannen bleibt bewusst aus - das war nie Teil des Flags.)
-- dokument_typ wird als String gespeichert (Enum-Name, siehe LieferantDokumentTypConverter).
-- Der Alt-Name 'EINGANGSRECHNUNG' wird vom Code als RECHNUNG gelesen (LieferantDokumentTypConverter,
-- LieferantDokumentService.safeValueOf): Er wird im UPDATE wie RECHNUNG behandelt, und eine
-- vorhandene EINGANGSRECHNUNG-Zeile zaehlt im INSERT als vorhandene RECHNUNG-Zeile (keine
-- zweite RECHNUNG-Zeile).
--
-- BEKANNTE EINSCHRAENKUNG: BEWUSST GESETZTE VERBOTE GEHEN VERLOREN
-- Die Admin-Oberflaeche (Einstellungen -> Berechtigungen) speichert beim Speichern fuer ALLE
-- Dokumenttypen eine Zeile, nicht gesetzte Haken landen als darf_sehen = FALSE. Ein bewusst
-- gesetztes Verbot ist von diesem automatischen FALSE nicht unterscheidbar. Entscheidung: Bei
-- Abteilungen mit Rechnungs-Flag wird darf_sehen = FALSE fuer RECHNUNG/GUTSCHRIFT/EINGANGSRECHNUNG
-- deshalb auf TRUE gesetzt; ein dort bewusst gesetztes Verbot geht dabei verloren. Admins koennen
-- es unter Einstellungen -> Berechtigungen wieder abhaken. (Wer das spaeter anders will, muss den
-- UPDATE-Schritt unten einschraenken oder entfernen.)
--
-- WAS NICHT PASSIERT
-- Es wird nichts geloescht, und darf_scannen wird nie veraendert. Abteilungen ohne eines der
-- beiden Rechnungs-Flags bekommen nichts und behalten ihre Zeilen unveraendert (auch
-- darf_sehen = FALSE). Andere Dokumenttypen (z. B. BELEG, LIEFERSCHEIN) bleiben unberuehrt.
-- Die Migration kann gefahrlos mehrfach laufen (idempotent: der zweite Lauf aendert nichts,
-- das UPDATE greift nur bei darf_sehen = FALSE, das INSERT nur bei fehlender Zeile).
--
-- ROBUSTHEIT
-- Fehlt die Tabelle abteilung_dokument_berechtigung oder eine der benoetigten Spalten
-- (z. B. frische Installation, Reihenfolge der Migrationen), tut die Migration nichts und
-- schlaegt nicht fehl (information_schema-Pruefung + PREPARE/EXECUTE wie in V302/V405).
-- Ist dokument_typ ein natives ENUM, das RECHNUNG oder GUTSCHRIFT nicht enthaelt, tut die
-- Migration ebenfalls nichts (statt Flyway mit einem Daten-Fehler abzubrechen).
-- UPDATE und INSERT laufen unter derselben Guard-Bedingung.
--
-- NACHHER
-- Admins koennen die Rechte jederzeit unter Einstellungen -> Berechtigungen
-- (Dokumentrechte der Abteilung) anpassen, z. B. Gutschriften wieder ausblenden.

-- Alle benoetigten Spalten beider Tabellen vorhanden? (3 in abteilung + 4 in
-- abteilung_dokument_berechtigung = 7). Fehlt Tabelle oder Spalte, ist der Zaehler kleiner.
SET @spalten_ok = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND (   (TABLE_NAME = 'abteilung'
               AND COLUMN_NAME IN ('id', 'darf_rechnungen_sehen', 'darf_rechnungen_genehmigen'))
           OR (TABLE_NAME = 'abteilung_dokument_berechtigung'
               AND COLUMN_NAME IN ('abteilung_id', 'dokument_typ', 'darf_sehen', 'darf_scannen')))
);

-- dokument_typ darf kein natives ENUM sein, dem RECHNUNG oder GUTSCHRIFT fehlt.
-- 1 = Spalte vorhanden und passend (VARCHAR o. ae. oder ENUM mit beiden Werten), 0 = nicht.
SET @typ_ok = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'abteilung_dokument_berechtigung'
      AND COLUMN_NAME = 'dokument_typ'
      AND (   DATA_TYPE <> 'enum'
           OR (COLUMN_TYPE LIKE '%''RECHNUNG''%' AND COLUMN_TYPE LIKE '%''GUTSCHRIFT''%'))
);

-- Schritt 1: vorhandene Zeilen freischalten (nur Abteilungen mit Rechnungs-Flag, nur darf_sehen = FALSE).
SET @sql_rechnungsrechte_update = IF(@spalten_ok = 7 AND @typ_ok = 1,
    'UPDATE abteilung_dokument_berechtigung
     SET darf_sehen = TRUE
     WHERE darf_sehen = FALSE
       AND dokument_typ IN (''RECHNUNG'', ''GUTSCHRIFT'', ''EINGANGSRECHNUNG'')
       AND abteilung_id IN (
         SELECT a.id FROM abteilung a
         WHERE a.darf_rechnungen_sehen = TRUE OR a.darf_rechnungen_genehmigen = TRUE
       )',
    'SELECT 1'
);
PREPARE stmt_rechnungsrechte_update FROM @sql_rechnungsrechte_update;
EXECUTE stmt_rechnungsrechte_update;
DEALLOCATE PREPARE stmt_rechnungsrechte_update;

-- Schritt 2: fehlende Zeilen anlegen.
SET @sql_rechnungsrechte = IF(@spalten_ok = 7 AND @typ_ok = 1,
    'INSERT INTO abteilung_dokument_berechtigung (abteilung_id, dokument_typ, darf_sehen, darf_scannen)
     SELECT a.id, t.dokument_typ, TRUE, FALSE
     FROM abteilung a
     CROSS JOIN (SELECT ''RECHNUNG'' AS dokument_typ, ''EINGANGSRECHNUNG'' AS alt_typ
                 UNION ALL SELECT ''GUTSCHRIFT'', ''GUTSCHRIFT'') t
     WHERE (a.darf_rechnungen_sehen = TRUE OR a.darf_rechnungen_genehmigen = TRUE)
       AND NOT EXISTS (
         SELECT 1 FROM abteilung_dokument_berechtigung b
         WHERE b.abteilung_id = a.id AND b.dokument_typ IN (t.dokument_typ, t.alt_typ)
       )',
    'SELECT 1'
);
PREPARE stmt_rechnungsrechte FROM @sql_rechnungsrechte;
EXECUTE stmt_rechnungsrechte;
DEALLOCATE PREPARE stmt_rechnungsrechte;
