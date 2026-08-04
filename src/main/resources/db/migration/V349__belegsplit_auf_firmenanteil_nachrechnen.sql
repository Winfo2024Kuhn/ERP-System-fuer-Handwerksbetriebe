-- Kostenstellen-Aufteilungen von Mischbelegen auf den Firmenanteil nachrechnen.
--
-- Ausgangslage: Bei einem Beleg mit aufteilungs_modus = 'TEILWEISE' (Mischbon
-- mit privatem Einkauf) hat der PC-Pfad (BelegService.persistiereSplits) die
-- Prozent-Aufteilungen bisher auf den VOLLEN Belegbetrag gerechnet, der
-- Handy-Pfad dagegen korrekt auf den Firmenanteil. Dadurch floss der private
-- Anteil in die Gemeinkosten und trieb den Stundensatz hoch.
--
-- Der Code rechnet ab sofort ueberall mit Beleg.getBuchungsbetragNetto().
-- Diese Migration zieht die bereits gespeicherten Werte einmalig nach, damit
-- Rest-Betrag und Aufteilung wieder zusammenpassen.
--
-- Betroffen sind ausschliesslich PROZENT-Aufteilungen: absolute Betraege hat
-- der Nutzer bewusst so eingetippt und bleiben unangetastet.
--
-- Idempotent: die Neuberechnung liefert bei erneutem Lauf denselben Wert.

UPDATE beleg_kostenstellen_anteil a
JOIN beleg b ON b.id = a.beleg_id
SET a.berechneter_betrag = ROUND(
        COALESCE(b.betrag_firma_netto, b.betrag_firma_brutto) * a.prozent / 100, 2)
WHERE b.aufteilungs_modus = 'TEILWEISE'
  AND a.prozent IS NOT NULL
  AND a.absoluter_betrag IS NULL
  AND COALESCE(b.betrag_firma_netto, b.betrag_firma_brutto) IS NOT NULL;

-- Verwaiste Firmenbetraege abraeumen.
--
-- Invariante ab jetzt: betrag_firma_* ist NUR bei aufteilungs_modus='TEILWEISE'
-- gesetzt. BelegSplitService.recomputeFirmaSummen leert die Felder beim
-- Zurueckschalten auf VOLLSTAENDIG schon, aeltere Datensaetze aus der Zeit davor
-- koennen aber noch Reste tragen. Die wuerden sonst je nach Auswertung mal
-- gelesen und mal nicht.
UPDATE beleg
SET betrag_firma_netto = NULL,
    betrag_firma_brutto = NULL,
    betrag_firma_mwst = NULL
WHERE aufteilungs_modus = 'VOLLSTAENDIG'
  AND (betrag_firma_netto IS NOT NULL
    OR betrag_firma_brutto IS NOT NULL
    OR betrag_firma_mwst IS NOT NULL);
