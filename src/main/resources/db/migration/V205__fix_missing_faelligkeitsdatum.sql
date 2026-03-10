-- Fälligkeitsdatum nachträglich setzen für Offene-Posten-Einträge,
-- bei denen es fehlt (z.B. weil zahlungszielTage auf dem AusgangsGeschaeftsDokument nicht gesetzt war).
-- Berechnung: rechnungsdatum + zahlungsziel des Kunden (oder Default 8 Tage).

UPDATE projekt_geschaeftsdokument pg
JOIN projekt_dokument pd ON pg.id = pd.id
JOIN projekt p ON pd.projekt = p.id
LEFT JOIN kunde k ON p.kunden_id = k.id
SET pg.faelligkeitsdatum = DATE_ADD(pg.rechnungsdatum, INTERVAL COALESCE(k.zahlungsziel, 8) DAY)
WHERE pg.faelligkeitsdatum IS NULL
  AND pg.rechnungsdatum IS NOT NULL
  AND pd.dokument_gruppe = 'GESCHAEFTSDOKUMENTE';
