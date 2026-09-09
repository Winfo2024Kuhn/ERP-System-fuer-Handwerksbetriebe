-- Übergangsschritt: Altzeitkonto bleibt bis zum vollständigen Aufruferumbau bestehen.
-- Keinerlei UPDATE auf monats_saldo: weder Zahlen noch gueltig/Abschluss ändern.
UPDATE mitarbeiter SET art = 'SYSTEM', aktiv = TRUE, fuehrt_zeitkonto = FALSE
WHERE login_token = '__SYSTEM_FUNNEL__';

-- Eintritt hat Vorrang. Fehlt er, beginnt die technische Übernahme mit dem
-- frühesten belegten Saldo-/Abwesenheits-/Korrektur-/Buchungsdatum (auch storniert).
-- Fehlen sämtliche Belege, gilt 1000-01-01, die MySQL-DATE-Untergrenze.
-- Das ist ein technischer Gültigkeitsanker für historische Berechnungen,
-- KEINE Behauptung über einen tatsächlichen Vertrags- oder Beschäftigungsbeginn.
-- NULL-Stunden und NULL-Zeitfenster werden unverändert übernommen.
INSERT INTO zeitkonto_version (
    mitarbeiter_id, gueltig_von, gueltig_bis, vorlage_id, version,
    montag_stunden, dienstag_stunden, mittwoch_stunden, donnerstag_stunden, freitag_stunden, samstag_stunden, sonntag_stunden, buchung_start_zeit, buchung_ende_zeit
)
SELECT z.mitarbeiter_id,
       COALESCE(m.eintrittsdatum, historie.beginn, CAST('1000-01-01' AS DATE)),
       NULL, NULL, 0,
       z.montag_stunden, z.dienstag_stunden, z.mittwoch_stunden, z.donnerstag_stunden, z.freitag_stunden, z.samstag_stunden, z.sonntag_stunden, z.buchung_start_zeit, z.buchung_ende_zeit
FROM zeitkonto z
JOIN mitarbeiter m ON m.id = z.mitarbeiter_id
LEFT JOIN (
    SELECT mitarbeiter_id, MIN(datum) AS beginn FROM (
        SELECT mitarbeiter_id, STR_TO_DATE(CONCAT(jahr, '-', LPAD(monat, 2, '0'), '-01'), '%Y-%m-%d') AS datum
        FROM monats_saldo
        UNION ALL
        SELECT mitarbeiter_id, datum FROM abwesenheit
        UNION ALL
        SELECT mitarbeiter_id, datum FROM zeitkonto_korrektur
        UNION ALL
        SELECT mitarbeiter_id, DATE(start_zeit) FROM zeitbuchung
    ) belege GROUP BY mitarbeiter_id
) historie ON historie.mitarbeiter_id = m.id
WHERE m.art = 'MENSCH'
  AND NOT EXISTS (SELECT 1 FROM zeitkonto_version v WHERE v.mitarbeiter_id = z.mitarbeiter_id);
