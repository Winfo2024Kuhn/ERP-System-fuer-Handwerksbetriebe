-- V203: Nicht-Rechnungsdokumente entsperren
--
-- Bisherige Logik: Alle Dokumenttypen wurden beim Export/Versand gebucht (gesperrt).
-- Neue Logik: Nur Rechnungstypen (RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG,
--             GUTSCHRIFT, STORNO) bleiben nach Buchung gesperrt.
--             Angebote und Auftragsbestätigungen werden entsperrt, damit sie weiterhin bearbeitet werden können.
--
-- Betroffene Typen: ANGEBOT, AUFTRAGSBESTAETIGUNG
-- Nicht betroffene Typen: RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG, GUTSCHRIFT, STORNO

UPDATE ausgangs_geschaeftsdokument
SET gebucht = false,
    gebucht_am = NULL
WHERE typ IN ('ANGEBOT', 'AUFTRAGSBESTAETIGUNG')
  AND gebucht = true
  AND storniert = false;
