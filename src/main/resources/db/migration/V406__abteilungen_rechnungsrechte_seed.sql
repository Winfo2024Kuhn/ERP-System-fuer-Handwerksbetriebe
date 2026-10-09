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
-- wird fuer die Typen 'RECHNUNG' und 'GUTSCHRIFT' je eine Zeile angelegt:
--   darf_sehen = TRUE, darf_scannen = FALSE
-- (Sehen wie bisher, Hochladen/Scannen bleibt bewusst aus - das war nie Teil des Flags.)
-- dokument_typ wird als String gespeichert (Enum-Name, siehe LieferantDokumentTypConverter).
-- Der Alt-Name 'EINGANGSRECHNUNG' wird vom Code als RECHNUNG gelesen (LieferantDokumentTypConverter,
-- LieferantDokumentService.safeValueOf) und zaehlt beim Vorhandensein-Check deshalb als RECHNUNG.
--
-- WAS NICHT PASSIERT / BEKANNTE EINSCHRAENKUNG (bitte bei einer spaeteren Erweiterung pruefen)
-- Es wird nichts ueberschrieben oder geloescht: Existiert fuer (abteilung_id, dokument_typ)
-- bereits eine Zeile - egal mit welchen Flags -, bleibt sie unangetastet (NOT EXISTS).
-- Das gilt auch fuer Zeilen mit darf_sehen = FALSE. Die Admin-Oberflaeche (Einstellungen ->
-- Berechtigungen) legt beim Speichern fuer ALLE Dokumenttypen eine Zeile an, nicht gesetzte
-- Haken landen als darf_sehen = FALSE. Eine Abteilung, die dort schon einmal gespeichert wurde,
-- hat damit bereits RECHNUNG-/GUTSCHRIFT-Zeilen mit darf_sehen = FALSE; diese bleiben
-- unveraendert. Solche Abteilungen sehen Offene Posten und Co. nach dem Update WEITERHIN NICHT,
-- bis ein Admin den Haken unter Einstellungen -> Berechtigungen setzt. Die Migration kann nicht
-- unterscheiden, ob FALSE bewusst gesetzt wurde oder nur ein Speicher-Artefakt der Oberflaeche
-- ist. Soll das spaeter anders sein, muesste ein eigener UPDATE-Schritt (z. B. darf_sehen = TRUE
-- fuer Abteilungen mit Rechnungs-Flag) ergaenzt werden - bewusst nicht Teil dieser Migration.
-- Abteilungen ohne eines der beiden Rechnungs-Flags bekommen nichts. Die Migration kann
-- gefahrlos mehrfach laufen (idempotent).
--
-- ROBUSTHEIT
-- Fehlt die Tabelle abteilung_dokument_berechtigung oder eine der benoetigten Spalten
-- (z. B. frische Installation, Reihenfolge der Migrationen), tut die Migration nichts und
-- schlaegt nicht fehl (information_schema-Pruefung + PREPARE/EXECUTE wie in V302/V405).
-- Ist dokument_typ ein natives ENUM, das RECHNUNG oder GUTSCHRIFT nicht enthaelt, tut die
-- Migration ebenfalls nichts (statt Flyway mit einem Daten-Fehler abzubrechen).
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
