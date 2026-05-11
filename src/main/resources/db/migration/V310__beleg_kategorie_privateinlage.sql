-- Schema-Fix: PRIVATEINLAGE in beleg_kategorie-ENUM aufnehmen.
--
-- V309 hat die Spalte als natives ENUM neu definiert, aber den Java-Enum-Wert
-- PRIVATEINLAGE vergessen (wurde in Commit 51f8330 nach V302 ergaenzt, V309
-- entstand erst durch Umbenennung in 618e2c7 und wurde dabei nicht ueber den
-- aktuellen Java-Enum-Stand gespiegelt).
--
-- Symptom ohne diesen Fix: jede Privateinlage-Umbuchung scheitert beim INSERT
-- mit "Data truncated for column 'beleg_kategorie'". Reihenfolge der Werte
-- entspricht dem Java-Enum (BelegKategorie).
--
-- Idempotent ueber MODIFY COLUMN — wiederholtes Anwenden setzt denselben Typ.

ALTER TABLE beleg
    MODIFY COLUMN beleg_kategorie
        ENUM('UNZUGEORDNET','KASSE_EINNAHME','KASSE_AUSGABE','PRIVATENTNAHME','PRIVATEINLAGE','BANK','KREDITKARTE','SONSTIGER_BELEG')
        NOT NULL DEFAULT 'UNZUGEORDNET';
