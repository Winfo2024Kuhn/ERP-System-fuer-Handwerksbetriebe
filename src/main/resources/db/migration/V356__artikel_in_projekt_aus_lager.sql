-- Trennt "kam aus dem eigenen Lager" von "wurde bestellt".
--
-- Bisher trug `bestellt` beide Bedeutungen: Der Lagerartikel-Dialog setzte
-- bestellt=true, damit die Position nicht in der offenen Bestellliste
-- (findByBestelltFalse) auftaucht. Dieselbe Flagge setzt aber auch der
-- Einkaeufer, wenn er eine Bestellung abhakt. Fuer die Nachkalkulation sind
-- das zwei verschiedene Faelle: Lagerware ist bereits bezahlt und zaehlt
-- sofort als Materialkosten, bestellte Ware kommt spaeter per
-- Lieferantenrechnung ins Projekt und wuerde sonst doppelt zaehlen.
--
-- Bestandsdaten bleiben unangetastet: `bestellt` behaelt seine Bedeutung
-- fuers Bestellmodul, die neue Spalte startet fuer alle alten Zeilen auf
-- false (= wie bisher nicht als Lagerware gezaehlt).
--
-- BIT(1) wie lieferanten.vorauskasse in V350: Hibernate mappt ein
-- boolean-Feld darauf, und ddl-auto=validate besteht auf genau diesem Typ.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich. MySQL kennt kein
-- "ADD COLUMN IF NOT EXISTS", deshalb der Umweg ueber information_schema.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'artikel_in_projekt' AND column_name = 'aus_lager');
SET @sql := IF(@c = 0,
    "ALTER TABLE artikel_in_projekt ADD COLUMN aus_lager BIT(1) NOT NULL DEFAULT b'0'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
