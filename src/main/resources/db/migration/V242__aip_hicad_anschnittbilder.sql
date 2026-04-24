-- ═══════════════════════════════════════════════════════════════
-- V242: HiCAD-Anschnittbilder direkt auf ArtikelInProjekt
-- ═══════════════════════════════════════════════════════════════
-- Beim HiCAD-Import liegen pro Sägeliste-Zeile bereits Schnittbilder
-- in der Excel (Steg + Flansch). Wir wollen diese 1:1 übernehmen
-- statt auf Stamm-Schnittbilder zu mappen. Die Bilder sind pro
-- Position individuell und gehören daher nicht in den Stamm.
--
-- Neue Spalten halten die URL unter der die Datei serverseitig liegt
-- (uploads/hicad-schnittbilder/<uuid>.png). Die bestehende Relation
-- schnittbild_id bleibt für manuelle Zuordnung aus dem Stamm.
--
-- Zusätzlich werden die Rohtexte der Excel-Zellen mitgeführt, weil pro
-- Zelle zwei Winkel stehen (z.B. "27.6°   27.6°" bzw. "26.9°   13.4°").
-- Die bestehenden Double-Felder anschnitt_winkel_links/_rechts reichen
-- dafür nicht (nur zwei Werte insgesamt, hier brauchen wir vier: Steg
-- Anfang/Ende, Flansch Anfang/Ende). Rohtext-Speicherung ist die
-- einfachste verlustfreie Lösung und matcht exakt die Darstellung in HiCAD.
--
-- MySQL 8.

ALTER TABLE artikel_in_projekt
    ADD COLUMN anschnittbild_steg_url    VARCHAR(500) NULL AFTER schnittbild_id,
    ADD COLUMN anschnittbild_flansch_url VARCHAR(500) NULL AFTER anschnittbild_steg_url,
    ADD COLUMN anschnitt_steg_text       VARCHAR(80)  NULL AFTER anschnittbild_flansch_url,
    ADD COLUMN anschnitt_flansch_text    VARCHAR(80)  NULL AFTER anschnitt_steg_text;
