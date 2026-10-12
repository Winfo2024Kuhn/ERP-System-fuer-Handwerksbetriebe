-- Einmalige Korrektur: "Bezahlt" an Projekten, das noch nach der alten Regel
-- gesetzt wurde - schon beim Schreiben der Rechnung, vor jedem Zahlungseingang.
--
-- Seit der zentralen Regel in AusgangsGeschaeftsDokumentService ist ein Projekt
-- erst bezahlt, wenn keine Rechnung mehr ein Entwurf ist und im Offene-Posten-Center
-- alle Rechnungen abgehakt sind. Der Haken am Projekt wird aber nur neu berechnet,
-- wenn sich ein Dokument aendert oder jemand das Projekt oeffnet. Altfaelle zeigten
-- deshalb in der Projektliste weiter "Bezahlt".
--
-- Korrigiert werden nur die eindeutigen Faelle: ein Rechnungsentwurf oder ein
-- unbezahlter Posten (Mahnungen an einer Rechnung zaehlen wie in der Regel nicht
-- extra). Der Haken "Beendet" bleibt unangetastet.
--
-- Idempotent: Es werden nur Projekte mit bezahlt = TRUE gewaehlt, ein zweiter Lauf
-- trifft nichts mehr.
UPDATE projekt p
SET bezahlt = FALSE
WHERE p.bezahlt = TRUE
  AND (
        EXISTS (
            SELECT 1
            FROM ausgangs_geschaeftsdokument d
            WHERE d.projekt_id = p.id
              AND d.typ IN ('RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG')
              AND d.storniert = FALSE
              AND d.gebucht = FALSE
        )
     OR EXISTS (
            SELECT 1
            FROM projekt_geschaeftsdokument g
            JOIN projekt_dokument pd ON pd.id = g.id
            WHERE pd.projekt = p.id
              AND g.bezahlt = FALSE
              AND g.referenz_dokument_id IS NULL
              AND (
                    LOWER(g.geschaeftsdokumentart) LIKE '%rechnung%'
                 OR LOWER(g.geschaeftsdokumentart) LIKE '%mahn%'
                 OR LOWER(g.geschaeftsdokumentart) LIKE '%erinnerung%'
              )
        )
  );
