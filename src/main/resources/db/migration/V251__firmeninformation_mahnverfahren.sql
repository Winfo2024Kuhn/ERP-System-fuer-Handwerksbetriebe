-- Globale Konfiguration des automatischen Mahnverfahrens.
-- Wird vom AutoMahnVersandService taeglich ausgewertet, um faellige Mahnstufen
-- (Zahlungserinnerung, 1. Mahnung, 2. Mahnung) zu erzeugen und per E-Mail zu
-- versenden. Per Default deaktiviert (Opt-In) — Handwerksbetriebe sollen erst
-- bewusst zustimmen, bevor automatisch Mahnungen rausgehen.

ALTER TABLE firmeninformation
    ADD COLUMN mahnverfahren_aktiv TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN tage_bis_zahlungserinnerung INT NOT NULL DEFAULT 7,
    ADD COLUMN tage_bis_erste_mahnung INT NOT NULL DEFAULT 14,
    ADD COLUMN tage_bis_zweite_mahnung INT NOT NULL DEFAULT 21,
    ADD COLUMN mahnverfahren_neues_zahlungsziel_tage INT NOT NULL DEFAULT 7;
