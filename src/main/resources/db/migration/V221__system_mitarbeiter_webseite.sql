-- System-Mitarbeiter "System Webseite" für automatisch erzeugte Anfragen
-- aus dem öffentlichen Funnel der Marketing-Webseite.
-- aktiv = 0, damit er nicht in Mitarbeiter-Auswahlen auftaucht.
-- login_token dient als stabiler Lookup-Schlüssel im Code.
INSERT INTO mitarbeiter (vorname, nachname, aktiv, login_token)
SELECT 'System', 'Webseite', 0, '__SYSTEM_FUNNEL__'
WHERE NOT EXISTS (
    SELECT 1 FROM mitarbeiter WHERE login_token = '__SYSTEM_FUNNEL__'
);
