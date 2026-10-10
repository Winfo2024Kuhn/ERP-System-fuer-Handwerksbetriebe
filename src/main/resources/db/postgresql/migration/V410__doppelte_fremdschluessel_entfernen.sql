-- Doppelte Fremdschluessel bereinigen - Gegenstueck zur MySQL-Migration.
--
-- In MySQL tragen neun Spalten zwei Fremdschluessel (Hibernate ohne Regel +
-- fk_... aus einer Migration, meist ON DELETE SET NULL); dort entfernt V410 den
-- von Hibernate. Die PostgreSQL-Basis hat je Spalte nur einen - den von
-- Hibernate ohne Regel, damit sie sich wie MySQL verhielt. Hier wird er durch den
-- fk_... mit der gewollten Regel ersetzt, Name und Regel danach wie in MySQL.
-- monats_saldo bleibt ohne Loeschregel (wie in V368), nur der Name gleicht sich an.
--
-- Idempotent: beide Namen erst entfernen, dann neu anlegen. Verweise ins Leere
-- (nur denkbar ohne jeden Schluessel, z. B. nach einem Import) werden bei den
-- SET-NULL-Spalten vorher geleert - genau das, was die Regel ohnehin taete -,
-- damit das Anlegen das Nachtupdate nicht stoppt.
-- Zwilling: db/migration/V410__doppelte_fremdschluessel_entfernen.sql

ALTER TABLE abwesenheit DROP CONSTRAINT IF EXISTS fkp1m4m9m370l0ekoohchtq9p95;
ALTER TABLE abwesenheit DROP CONSTRAINT IF EXISTS fk_abwesenheit_langzeitkrankmeldung;
UPDATE abwesenheit SET langzeitkrankmeldung_id = NULL
    WHERE langzeitkrankmeldung_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM langzeitkrankmeldung z WHERE z.id = abwesenheit.langzeitkrankmeldung_id);
ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung
    FOREIGN KEY (langzeitkrankmeldung_id) REFERENCES langzeitkrankmeldung (id) ON DELETE SET NULL;

ALTER TABLE abwesenheit DROP CONSTRAINT IF EXISTS fkt0ubrnf7d1gpsymykqurc95ih;
ALTER TABLE abwesenheit DROP CONSTRAINT IF EXISTS fk_abwesenheit_langzeitkrankmeldung_phase;
UPDATE abwesenheit SET langzeitkrankmeldung_phase_id = NULL
    WHERE langzeitkrankmeldung_phase_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM langzeitkrankmeldung_phase z WHERE z.id = abwesenheit.langzeitkrankmeldung_phase_id);
ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung_phase
    FOREIGN KEY (langzeitkrankmeldung_phase_id) REFERENCES langzeitkrankmeldung_phase (id) ON DELETE SET NULL;

ALTER TABLE firmeninformation DROP CONSTRAINT IF EXISTS fk1cut60jy9u292yx9x339p43ay;
ALTER TABLE firmeninformation DROP CONSTRAINT IF EXISTS fk_firmeninformation_gewerk;
UPDATE firmeninformation SET gewerk_id = NULL
    WHERE gewerk_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM gewerk z WHERE z.id = firmeninformation.gewerk_id);
ALTER TABLE firmeninformation ADD CONSTRAINT fk_firmeninformation_gewerk
    FOREIGN KEY (gewerk_id) REFERENCES gewerk (id) ON DELETE SET NULL;

ALTER TABLE frontend_user_profile DROP CONSTRAINT IF EXISTS fkgy9nu6luuoih1woj77ivgqyoi;
ALTER TABLE frontend_user_profile DROP CONSTRAINT IF EXISTS fk_frontend_user_email_absender;
UPDATE frontend_user_profile SET email_absender_id = NULL
    WHERE email_absender_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM email_absender z WHERE z.id = frontend_user_profile.email_absender_id);
ALTER TABLE frontend_user_profile ADD CONSTRAINT fk_frontend_user_email_absender
    FOREIGN KEY (email_absender_id) REFERENCES email_absender (id) ON DELETE SET NULL;

ALTER TABLE lieferant_dokument DROP CONSTRAINT IF EXISTS fkn3js7qygrabjp3p8v9dvnrmoa;
ALTER TABLE lieferant_dokument DROP CONSTRAINT IF EXISTS fk_lieferant_dokument_beleg;
UPDATE lieferant_dokument SET beleg_id = NULL
    WHERE beleg_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM beleg z WHERE z.id = lieferant_dokument.beleg_id);
ALTER TABLE lieferant_dokument ADD CONSTRAINT fk_lieferant_dokument_beleg
    FOREIGN KEY (beleg_id) REFERENCES beleg (id) ON DELETE SET NULL;

ALTER TABLE lieferant_dokument_projekt_anteil DROP CONSTRAINT IF EXISTS fk8e8msmb5hg51mtnyy9wjdgyve;
ALTER TABLE lieferant_dokument_projekt_anteil DROP CONSTRAINT IF EXISTS fk_projekt_anteil_zugeordnet_von;
UPDATE lieferant_dokument_projekt_anteil SET zugeordnet_von_user_id = NULL
    WHERE zugeordnet_von_user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM frontend_user_profile z WHERE z.id = lieferant_dokument_projekt_anteil.zugeordnet_von_user_id);
ALTER TABLE lieferant_dokument_projekt_anteil ADD CONSTRAINT fk_projekt_anteil_zugeordnet_von
    FOREIGN KEY (zugeordnet_von_user_id) REFERENCES frontend_user_profile (id) ON DELETE SET NULL;

ALTER TABLE lieferanten DROP CONSTRAINT IF EXISTS fkgopne9hwl7n945f7ghdqc21mw;
ALTER TABLE lieferanten DROP CONSTRAINT IF EXISTS fk_lieferanten_standard_kostenstelle;
UPDATE lieferanten SET standard_kostenstelle_id = NULL
    WHERE standard_kostenstelle_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM firma_kostenstelle z WHERE z.id = lieferanten.standard_kostenstelle_id);
ALTER TABLE lieferanten ADD CONSTRAINT fk_lieferanten_standard_kostenstelle
    FOREIGN KEY (standard_kostenstelle_id) REFERENCES firma_kostenstelle (id) ON DELETE SET NULL;

ALTER TABLE mitarbeiter DROP CONSTRAINT IF EXISTS fk28s44gxbnll5cni8jjse68nq1;
ALTER TABLE mitarbeiter DROP CONSTRAINT IF EXISTS fk_mitarbeiter_krankenkasse;
UPDATE mitarbeiter SET krankenkasse_id = NULL
    WHERE krankenkasse_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM krankenkasse z WHERE z.id = mitarbeiter.krankenkasse_id);
ALTER TABLE mitarbeiter ADD CONSTRAINT fk_mitarbeiter_krankenkasse
    FOREIGN KEY (krankenkasse_id) REFERENCES krankenkasse (id) ON DELETE SET NULL;

ALTER TABLE monats_saldo DROP CONSTRAINT IF EXISTS fk1iflfii6yokov9l2cmv36m6a3;
ALTER TABLE monats_saldo DROP CONSTRAINT IF EXISTS fk_monats_saldo_festgeschrieben_von;
ALTER TABLE monats_saldo ADD CONSTRAINT fk_monats_saldo_festgeschrieben_von
    FOREIGN KEY (festgeschrieben_von_mitarbeiter_id) REFERENCES mitarbeiter (id);
