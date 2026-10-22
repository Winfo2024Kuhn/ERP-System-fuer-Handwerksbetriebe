-- Schottet eine lokale Kopie der Produktions-Datenbank nach außen ab.
-- Wird von scripts/prodkopie/prodkopie-starten.sh nach jedem Import, vor jedem Start
-- und nach dem Backend-Start (also auch nach neuen Flyway-Migrationen) ausgeführt.
--
-- Ziel: Ein Backend gegen diese Kopie darf niemanden erreichen – keine Mahnung,
-- keine E-Mail, kein Anruf-Abgleich mit der FRITZ!Box, keine KI-Anfrage, kein
-- Push an Handys, keine Freigabe nach außen. Deshalb werden alle gespeicherten
-- Zieladressen und Zugänge geleert und alle Automatiken ausgeschaltet. Das
-- Startskript schaltet die übrigen Außen-Funktionen zusätzlich per Startparameter ab.
--
-- Kommt im ERP eine neue Außen-Verbindung dazu (neuer Zugang in system_setting,
-- neuer @Scheduled-Dienst, neue Schnittstelle), gehört sie hier bzw. ins Startskript.

BEGIN;

-- Automatischer Mahnlauf (täglich 09:00) würde echte Mahnungen verschicken.
UPDATE firmeninformation SET mahnverfahren_aktiv = false;

-- Zugänge und Ziele aus den Einstellungen: SMTP/IMAP, FRITZ!Box, KI-Schlüssel und
-- vorsorglich alles, was nach Adresse, Schlüssel oder Zugang aussieht.
-- datei.ordner-network-url ist ein lokaler Dateipfad, kein Außen-Ziel.
UPDATE system_setting
   SET setting_value = ''
 WHERE (setting_key ~* '(host|username|password|passwort|benutzer|token|secret|from-address)'
        -- Kurze Woerter nur als ganzer Namensteil, sonst traefe "url" auch "urlaub.*".
        OR setting_key ~* '(^|[.-])(url|endpoint|key|client-id|webhook)($|[.-])')
   AND setting_key <> 'datei.ordner-network-url';

-- Schalter, die nach außen wirken, fest auf aus – auch wenn die Zeile noch fehlt.
INSERT INTO system_setting (setting_key, setting_value)
VALUES ('telefon.aktiv', 'false'),
       ('telefon.telefonbuch.aktiv', 'false'),
       ('smtp.dokumente.aktiv', 'false'),
       ('mail.sent-kopie.aktiv', 'false'),
       ('zeiterfassung.tailscale-funnel.freigegeben', 'false')
ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value;

-- Postfächer der Mitarbeiter: kein Server, kein Passwort, ausgeschaltet.
-- Die Spalten gibt es erst ab V407 – ältere Sicherungen überspringen das.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'email_absender' AND column_name = 'passwort_verschluesselt') THEN
        UPDATE email_absender
           SET aktiv = false, smtp_host = NULL, imap_host = NULL, passwort_verschluesselt = NULL;
    END IF;
END $$;

-- Push-Abos zeigen auf die Push-Dienste von Apple/Google und damit auf echte Handys.
DELETE FROM push_subscription;

COMMIT;
