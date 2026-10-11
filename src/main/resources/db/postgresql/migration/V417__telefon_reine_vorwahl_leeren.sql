-- Reine Ortsvorwahl in Telefon- und Mobilnummern von Kunden und Lieferanten leeren.
--
-- WARUM
-- Das Büro schlägt beim Eingeben der Adresse die Ortsvorwahl vor (z. B. "0931 "). Wurde das
-- Feld danach nicht mehr angefasst, steht seitdem nur die Vorwahl als Telefonnummer im
-- Kunden bzw. Lieferanten – die Handy-App zeigte sie als anrufbare Nummer an. Seit dieser
-- Version speichert das Backend so etwas gar nicht mehr (util/Telefonnummer.ohneReineVorwahl);
-- diese Migration räumt den Altbestand mit derselben Regel auf.
--
-- WAS
-- Als reine Vorwahl gilt eine Nummer, deren Ziffern (nach +49 / 0049 -> 0) mit 0 beginnen und
-- weniger als 7 sind: "0931", "0931 ", "+49 931". Solche Felder werden NULL.
-- Unverändert bleiben vollständige Nummern und kurze Ortsnummern ohne Vorwahl ("12345") –
-- die ergänzt die Anrufer-Erkennung mit der eigenen Ortsvorwahl.
--
-- ROBUSTHEIT
-- Reine UPDATEs ohne Schemaänderung; ein zweiter Lauf findet nichts mehr und ändert nichts.

UPDATE kunde SET telefon = NULL
WHERE telefon IS NOT NULL
  AND regexp_replace(regexp_replace(btrim(telefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g') LIKE '0%'
  AND length(regexp_replace(regexp_replace(btrim(telefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g')) < 7;

UPDATE kunde SET mobiltelefon = NULL
WHERE mobiltelefon IS NOT NULL
  AND regexp_replace(regexp_replace(btrim(mobiltelefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g') LIKE '0%'
  AND length(regexp_replace(regexp_replace(btrim(mobiltelefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g')) < 7;

UPDATE lieferanten SET telefon = NULL
WHERE telefon IS NOT NULL
  AND regexp_replace(regexp_replace(btrim(telefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g') LIKE '0%'
  AND length(regexp_replace(regexp_replace(btrim(telefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g')) < 7;

UPDATE lieferanten SET mobiltelefon = NULL
WHERE mobiltelefon IS NOT NULL
  AND regexp_replace(regexp_replace(btrim(mobiltelefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g') LIKE '0%'
  AND length(regexp_replace(regexp_replace(btrim(mobiltelefon), '^([+]|00)49', '0'), '[^0-9]', '', 'g')) < 7;
