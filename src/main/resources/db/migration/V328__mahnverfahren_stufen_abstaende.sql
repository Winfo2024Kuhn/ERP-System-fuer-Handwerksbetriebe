-- Mahnverfahren: Stufen-Schwellen werden zu Abstaenden zwischen den Stufen.
--
-- Bisher: tage_bis_erste_mahnung / tage_bis_zweite_mahnung zaehlten ab
-- Faelligkeit der Original-Rechnung (z.B. 7 / 14 / 21).
-- Neu: tage_bis_erste_mahnung = Tage NACH Versand der Zahlungserinnerung,
--      tage_bis_zweite_mahnung = Tage NACH Versand der 1. Mahnung.
-- Bestehende Absolut-Werte werden daher in Differenzen umgerechnet und auf
-- mindestens 1 geklemmt, damit nie am selben Tag eskaliert wird.
--
-- WICHTIG zur Reihenfolge der SET-Klauseln: MySQL wertet SET links→rechts
-- mit bereits aktualisierten Werten aus. tage_bis_zweite_mahnung muss
-- deshalb ZUERST zugewiesen werden, weil die Rechnung den ALTEN Wert von
-- tage_bis_erste_mahnung braucht.
UPDATE firmeninformation
SET tage_bis_zweite_mahnung = GREATEST(1, tage_bis_zweite_mahnung - tage_bis_erste_mahnung),
    tage_bis_erste_mahnung  = GREATEST(1, tage_bis_erste_mahnung - tage_bis_zahlungserinnerung);

-- Spalten-Defaults an die neue Abstands-Semantik anpassen: Die alten
-- Defaults 14/21 stammten aus der Absolut-Semantik (Tage ab Faelligkeit).
-- Als Abstand zwischen den Stufen sind jeweils 7 Tage der sinnvolle Default.
ALTER TABLE firmeninformation
    ALTER COLUMN tage_bis_erste_mahnung SET DEFAULT 7,
    ALTER COLUMN tage_bis_zweite_mahnung SET DEFAULT 7;
