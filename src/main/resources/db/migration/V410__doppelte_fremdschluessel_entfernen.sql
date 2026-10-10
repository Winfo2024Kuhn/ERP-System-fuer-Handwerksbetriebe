-- Doppelte Fremdschluessel entfernen.
--
-- Neun Spalten tragen zwei Fremdschluessel aufs selbe Ziel: einen alten von
-- Hibernate ohne Loeschregel (FK...) und einen aus einer Migration (fk_...),
-- meist mit ON DELETE SET NULL. MySQL laesst dem strengeren den Vortritt - das
-- gewollte "beim Loeschen auf leer setzen" griff nie, das Loeschen von z. B.
-- Krankenkasse, Postfach oder Beleg wurde blockiert, solange es noch benutzt war.
-- Danach traegt jede Spalte genau den fk_... mit der gewollten Regel (bei
-- monats_saldo ohne Loeschregel, wie in V368 - dort war es nur ein Duplikat).
-- Der Index des alten Schluessels bleibt, der verbleibende Schluessel nutzt ihn.
--
-- Idempotent, je Spalte:
--   1. Fehlt der fk_... (Installation, auf der die alte Migration ihn nicht
--      anlegen konnte), wird er angelegt - vorher werden Verweise ins Leere
--      geleert, damit das Anlegen nicht am Altbestand scheitert.
--   2. Erst dann faellt der alte von Hibernate weg. Keine Spalte bleibt ohne Schluessel.
-- Zwilling: db/postgresql/migration/V410__doppelte_fremdschluessel_entfernen.sql

-- abwesenheit.langzeitkrankmeldung_id -> langzeitkrankmeldung: bleibt fk_abwesenheit_langzeitkrankmeldung, weg FKp1m4m9m370l0ekoohchtq9p95
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_abwesenheit_langzeitkrankmeldung');
SET @sql := IF(@neu = 0, 'UPDATE abwesenheit SET langzeitkrankmeldung_id = NULL WHERE langzeitkrankmeldung_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM langzeitkrankmeldung z WHERE z.id = abwesenheit.langzeitkrankmeldung_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung
    FOREIGN KEY (langzeitkrankmeldung_id) REFERENCES langzeitkrankmeldung (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FKp1m4m9m370l0ekoohchtq9p95');
SET @sql := IF(@alt > 0, 'ALTER TABLE abwesenheit DROP FOREIGN KEY FKp1m4m9m370l0ekoohchtq9p95', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- abwesenheit.langzeitkrankmeldung_phase_id -> langzeitkrankmeldung_phase: bleibt fk_abwesenheit_langzeitkrankmeldung_phase, weg FKt0ubrnf7d1gpsymykqurc95ih
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_abwesenheit_langzeitkrankmeldung_phase');
SET @sql := IF(@neu = 0, 'UPDATE abwesenheit SET langzeitkrankmeldung_phase_id = NULL WHERE langzeitkrankmeldung_phase_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM langzeitkrankmeldung_phase z WHERE z.id = abwesenheit.langzeitkrankmeldung_phase_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung_phase
    FOREIGN KEY (langzeitkrankmeldung_phase_id) REFERENCES langzeitkrankmeldung_phase (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FKt0ubrnf7d1gpsymykqurc95ih');
SET @sql := IF(@alt > 0, 'ALTER TABLE abwesenheit DROP FOREIGN KEY FKt0ubrnf7d1gpsymykqurc95ih', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- firmeninformation.gewerk_id -> gewerk: bleibt fk_firmeninformation_gewerk, weg FK1cut60jy9u292yx9x339p43ay
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'firmeninformation'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_firmeninformation_gewerk');
SET @sql := IF(@neu = 0, 'UPDATE firmeninformation SET gewerk_id = NULL WHERE gewerk_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM gewerk z WHERE z.id = firmeninformation.gewerk_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE firmeninformation ADD CONSTRAINT fk_firmeninformation_gewerk
    FOREIGN KEY (gewerk_id) REFERENCES gewerk (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'firmeninformation'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FK1cut60jy9u292yx9x339p43ay');
SET @sql := IF(@alt > 0, 'ALTER TABLE firmeninformation DROP FOREIGN KEY FK1cut60jy9u292yx9x339p43ay', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- frontend_user_profile.email_absender_id -> email_absender: bleibt fk_frontend_user_email_absender, weg FKgy9nu6luuoih1woj77ivgqyoi
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'frontend_user_profile'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_frontend_user_email_absender');
SET @sql := IF(@neu = 0, 'UPDATE frontend_user_profile SET email_absender_id = NULL WHERE email_absender_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM email_absender z WHERE z.id = frontend_user_profile.email_absender_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE frontend_user_profile ADD CONSTRAINT fk_frontend_user_email_absender
    FOREIGN KEY (email_absender_id) REFERENCES email_absender (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'frontend_user_profile'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FKgy9nu6luuoih1woj77ivgqyoi');
SET @sql := IF(@alt > 0, 'ALTER TABLE frontend_user_profile DROP FOREIGN KEY FKgy9nu6luuoih1woj77ivgqyoi', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferant_dokument.beleg_id -> beleg: bleibt fk_lieferant_dokument_beleg, weg FKn3js7qygrabjp3p8v9dvnrmoa
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_dokument'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_lieferant_dokument_beleg');
SET @sql := IF(@neu = 0, 'UPDATE lieferant_dokument SET beleg_id = NULL WHERE beleg_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM beleg z WHERE z.id = lieferant_dokument.beleg_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE lieferant_dokument ADD CONSTRAINT fk_lieferant_dokument_beleg
    FOREIGN KEY (beleg_id) REFERENCES beleg (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_dokument'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FKn3js7qygrabjp3p8v9dvnrmoa');
SET @sql := IF(@alt > 0, 'ALTER TABLE lieferant_dokument DROP FOREIGN KEY FKn3js7qygrabjp3p8v9dvnrmoa', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferant_dokument_projekt_anteil.zugeordnet_von_user_id -> frontend_user_profile: bleibt fk_projekt_anteil_zugeordnet_von, weg FK8e8msmb5hg51mtnyy9wjdgyve
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_dokument_projekt_anteil'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_projekt_anteil_zugeordnet_von');
SET @sql := IF(@neu = 0, 'UPDATE lieferant_dokument_projekt_anteil SET zugeordnet_von_user_id = NULL WHERE zugeordnet_von_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM frontend_user_profile z WHERE z.id = lieferant_dokument_projekt_anteil.zugeordnet_von_user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD CONSTRAINT fk_projekt_anteil_zugeordnet_von
    FOREIGN KEY (zugeordnet_von_user_id) REFERENCES frontend_user_profile (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_dokument_projekt_anteil'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FK8e8msmb5hg51mtnyy9wjdgyve');
SET @sql := IF(@alt > 0, 'ALTER TABLE lieferant_dokument_projekt_anteil DROP FOREIGN KEY FK8e8msmb5hg51mtnyy9wjdgyve', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferanten.standard_kostenstelle_id -> firma_kostenstelle: bleibt fk_lieferanten_standard_kostenstelle, weg FKgopne9hwl7n945f7ghdqc21mw
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferanten'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_lieferanten_standard_kostenstelle');
SET @sql := IF(@neu = 0, 'UPDATE lieferanten SET standard_kostenstelle_id = NULL WHERE standard_kostenstelle_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM firma_kostenstelle z WHERE z.id = lieferanten.standard_kostenstelle_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE lieferanten ADD CONSTRAINT fk_lieferanten_standard_kostenstelle
    FOREIGN KEY (standard_kostenstelle_id) REFERENCES firma_kostenstelle (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'lieferanten'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FKgopne9hwl7n945f7ghdqc21mw');
SET @sql := IF(@alt > 0, 'ALTER TABLE lieferanten DROP FOREIGN KEY FKgopne9hwl7n945f7ghdqc21mw', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mitarbeiter.krankenkasse_id -> krankenkasse: bleibt fk_mitarbeiter_krankenkasse, weg FK28s44gxbnll5cni8jjse68nq1
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'mitarbeiter'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_mitarbeiter_krankenkasse');
SET @sql := IF(@neu = 0, 'UPDATE mitarbeiter SET krankenkasse_id = NULL WHERE krankenkasse_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM krankenkasse z WHERE z.id = mitarbeiter.krankenkasse_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@neu = 0, 'ALTER TABLE mitarbeiter ADD CONSTRAINT fk_mitarbeiter_krankenkasse
    FOREIGN KEY (krankenkasse_id) REFERENCES krankenkasse (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'mitarbeiter'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FK28s44gxbnll5cni8jjse68nq1');
SET @sql := IF(@alt > 0, 'ALTER TABLE mitarbeiter DROP FOREIGN KEY FK28s44gxbnll5cni8jjse68nq1', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- monats_saldo.festgeschrieben_von_mitarbeiter_id -> mitarbeiter: bleibt fk_monats_saldo_festgeschrieben_von, weg FK1iflfii6yokov9l2cmv36m6a3
SET @neu := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_monats_saldo_festgeschrieben_von');
SET @sql := IF(@neu = 0, 'ALTER TABLE monats_saldo ADD CONSTRAINT fk_monats_saldo_festgeschrieben_von
    FOREIGN KEY (festgeschrieben_von_mitarbeiter_id) REFERENCES mitarbeiter (id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @alt := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo'
      AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'FK1iflfii6yokov9l2cmv36m6a3');
SET @sql := IF(@alt > 0, 'ALTER TABLE monats_saldo DROP FOREIGN KEY FK1iflfii6yokov9l2cmv36m6a3', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
