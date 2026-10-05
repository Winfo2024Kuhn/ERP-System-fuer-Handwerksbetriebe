-- Startwert-Absender eines einzelnen Betriebs wieder entfernen.
--
-- V290 legt bei jeder neuen Installation 'bauschlosserei-kuhn@t-online.de'
-- als ersten Absender an (frueher fest im Code). Fuer jeden anderen Betrieb
-- ist das eine fremde Adresse: Mails gingen mit falschem From-Header raus.
-- V290 selbst darf nicht mehr geaendert werden, deshalb raeumt diese
-- Migration den Eintrag nachtraeglich weg.
--
-- Geloescht wird nur, solange die Adresse nie benutzt wurde - also keine
-- Mail (ein- oder ausgehend) und kein Entwurf mit dieser Absenderadresse
-- existiert. In der Installation, die die Adresse tatsaechlich nutzt,
-- bleibt der Eintrag unveraendert.
--
-- Profile, denen der Eintrag zugewiesen war, fallen ueber den Fremdschluessel
-- (ON DELETE SET NULL) auf "kein Absender" zurueck; der Versand nutzt dann
-- die SMTP-Einstellungen. Neue Absender pflegt der Betrieb unter "Firma".
--
-- Idempotent: Ein zweiter Lauf findet nichts mehr oder laesst den genutzten
-- Eintrag weiterhin stehen.

DELETE FROM email_absender
WHERE LOWER(email_adresse) = 'bauschlosserei-kuhn@t-online.de'
  AND NOT EXISTS (
      SELECT 1 FROM email e
      WHERE LOWER(e.from_address) LIKE '%bauschlosserei-kuhn@t-online.de%'
  )
  AND NOT EXISTS (
      SELECT 1 FROM email_draft d
      WHERE LOWER(d.from_address) LIKE '%bauschlosserei-kuhn@t-online.de%'
  );
