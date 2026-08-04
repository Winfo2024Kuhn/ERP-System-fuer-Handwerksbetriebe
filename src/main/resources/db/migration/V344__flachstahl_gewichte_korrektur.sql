-- Korrektur offensichtlich falscher Gewichtsangaben bei Flachstahl.
--
-- Hintergrund: Eine Nachrechnung aller Halbzeuge gegen Abmessung und
-- Werkstoffdichte hat gezeigt, dass die gespeicherten Gewichte ganz ueberwiegend
-- sehr genau sind - Rundrohr, Rundstab, Winkel und Vierkantstahl weichen im
-- Mittel unter 0,5 Prozent ab.
--
-- Bei einer kleinen Gruppe von Flachstaehlen liegen die Werte dagegen um den
-- Faktor 20 bis 55 daneben:
--     Fl 130x35   gespeichert 0,63 kg/m   tatsaechlich 35,7 kg/m
--     Fl 130x60   gespeichert 1,11 kg/m   tatsaechlich 61,2 kg/m
--     Fl 140x20   gespeichert 1,88 kg/m   tatsaechlich 22,0 kg/m
--
-- Das ist kein Rundungs- oder Formelthema, sondern ein Datenfehler: Ein
-- Flachstahl 130 x 35 mm aus Baustahl wiegt zwangslaeufig 130 * 35 * 7,85 / 1000
-- = 35,7 kg je Meter. Mit den bisherigen Werten wurden Materialkosten dieser
-- Positionen massiv zu niedrig kalkuliert.
--
-- Bewusst eng gefasst: Korrigiert werden nur Faelle, bei denen der berechnete
-- Wert mehr als das Dreifache des gespeicherten betraegt. Kleinere Abweichungen
-- bleiben unangetastet, weil dort die Rechenformel die Naeherung sein kann und
-- nicht die Daten.
--
-- Die Altwerte werden gesichert und lassen sich jederzeit zurueckholen:
--   UPDATE artikel_werkstoffe aw JOIN artikel_bereinigung_backup b
--       ON b.artikel_id = aw.id AND b.migration = 'V344'
--   SET aw.masse_pro_meter = CAST(b.alt_wert AS DECIMAL(12,4));
--
-- Idempotent: Nach dem ersten Lauf greift die Bedingung nicht mehr.

-- --------------------------------------------------------------------------
-- 1. Altwerte sichern
-- --------------------------------------------------------------------------

INSERT INTO artikel_bereinigung_backup (artikel_id, feld, alt_wert, neu_wert, grund, migration)
SELECT a.id,
       'masse_pro_meter',
       CAST(aw.masse_pro_meter AS CHAR),
       CAST(ROUND(aw.breite * aw.wandstaerke * w.dichte / 1000, 4) AS CHAR),
       CONCAT('Gewicht unplausibel: ', a.produktname, ' (',
              zahl_kurz(aw.breite), ' x ', zahl_kurz(aw.wandstaerke), ' mm, ',
              w.name, ')'),
       'V344'
FROM artikel a
JOIN artikel_werkstoffe aw ON aw.id = a.id
JOIN werkstoff w ON w.id = a.werkstoff_id
WHERE a.profilform IN ('FLACHSTAB','BREITFLACHSTAHL')
  AND aw.breite IS NOT NULL AND aw.wandstaerke IS NOT NULL
  AND w.dichte IS NOT NULL
  AND aw.masse_pro_meter IS NOT NULL AND aw.masse_pro_meter > 0
  AND (aw.breite * aw.wandstaerke * w.dichte / 1000) / aw.masse_pro_meter > 3
  AND NOT EXISTS (SELECT 1 FROM artikel_bereinigung_backup b
                  WHERE b.artikel_id = a.id AND b.migration = 'V344');

-- --------------------------------------------------------------------------
-- 2. Gewicht und Oberflaeche neu berechnen
-- --------------------------------------------------------------------------
-- Flachstahl ist Vollmaterial: Breite mal Dicke mal Dichte. Die abzuwickelnde
-- Oberflaeche ist der Umfang des Rechtecks.

UPDATE artikel a
JOIN artikel_werkstoffe aw ON aw.id = a.id
JOIN werkstoff w ON w.id = a.werkstoff_id
SET aw.masse_pro_meter = ROUND(aw.breite * aw.wandstaerke * w.dichte / 1000, 4),
    aw.mantelflaeche   = ROUND(2 * (aw.breite + aw.wandstaerke) / 1000, 4),
    aw.querschnittsflaeche = ROUND(aw.breite * aw.wandstaerke / 100, 3)
WHERE a.profilform IN ('FLACHSTAB','BREITFLACHSTAHL')
  AND aw.breite IS NOT NULL AND aw.wandstaerke IS NOT NULL
  AND w.dichte IS NOT NULL
  AND aw.masse_pro_meter IS NOT NULL AND aw.masse_pro_meter > 0
  AND (aw.breite * aw.wandstaerke * w.dichte / 1000) / aw.masse_pro_meter > 3;
