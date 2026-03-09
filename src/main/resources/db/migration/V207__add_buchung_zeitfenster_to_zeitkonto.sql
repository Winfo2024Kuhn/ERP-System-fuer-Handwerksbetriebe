-- Erlaubtes Buchungszeitfenster pro Mitarbeiter.
-- Offene Buchungen außerhalb dieses Fensters werden automatisch beendet.
ALTER TABLE zeitkonto
    ADD COLUMN buchung_start_zeit TIME DEFAULT '05:00:00',
    ADD COLUMN buchung_ende_zeit  TIME DEFAULT '20:00:00';
