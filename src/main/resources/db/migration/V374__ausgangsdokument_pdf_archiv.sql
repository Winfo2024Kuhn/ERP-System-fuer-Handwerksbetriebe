-- PDF-Referenz am Ausgangsdokument: auch Stornos/Gutschriften haben keinen
-- zwingenden ProjektGeschaeftsdokument-Eintrag. Wiederholt ausführbar.
SET @pdf_spalte_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ausgangs_geschaeftsdokument'
      AND COLUMN_NAME = 'pdf_dateiname'
);
SET @pdf_add_spalte = IF(@pdf_spalte_exists = 0,
    'ALTER TABLE ausgangs_geschaeftsdokument ADD COLUMN pdf_dateiname VARCHAR(255) NULL',
    'SELECT 1');
PREPARE pdf_spalte_stmt FROM @pdf_add_spalte;
EXECUTE pdf_spalte_stmt;
DEALLOCATE PREPARE pdf_spalte_stmt;

-- Bereits archivierte PDFs übernehmen; synthetische Editor-Platzhalter auslassen.
UPDATE ausgangs_geschaeftsdokument a
JOIN (
    SELECT g.dokumentid, MIN(p.gespeicherter_dateiname) AS dateiname
    FROM projekt_geschaeftsdokument g
    JOIN projekt_dokument p ON p.id = g.id
    WHERE p.gespeicherter_dateiname NOT LIKE 'ausgangs-dok-%'
      AND LOWER(p.gespeicherter_dateiname) LIKE '%.pdf'
    GROUP BY g.dokumentid
    HAVING COUNT(DISTINCT p.gespeicherter_dateiname) = 1
) archiv ON archiv.dokumentid = a.dokument_nummer
SET a.pdf_dateiname = archiv.dateiname
WHERE a.pdf_dateiname IS NULL;
