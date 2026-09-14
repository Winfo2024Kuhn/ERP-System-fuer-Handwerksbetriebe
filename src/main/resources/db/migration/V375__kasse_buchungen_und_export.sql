-- Kasse & Belege: Buchungserfassung (sechs Kacheln am Handy/PC) und
-- Steuerberater-Export (DATEV-CSV + PDF-ZIP) brauchen zusaetzliche Spalten
-- an beleg und kasse_einstellung, dazu einen neuen Stammdaten-Eintrag fuer
-- Online-Bezahldienste und einen Backfill der bisherigen Freitext-
-- Zahlungsart auf die Stammdaten-Bezeichnungen aus V308.
--
-- Idempotent ueber INFORMATION_SCHEMA, Muster: V351__kassenbuch_festschreibung.sql
-- (SET @c = ...; SET @s = IF(@c = 0, 'ALTER TABLE ...', 'SELECT 1'); PREPARE/EXECUTE/DEALLOCATE).
-- FK-Absicherung ueber TABLE_CONSTRAINTS, Muster: V305__lieferant_dokument_beleg_fk.sql.

-- =====================================================================
-- 1) beleg.quelle -- Herkunft des Belegs (BelegQuelle-Enum)
-- =====================================================================
-- Native ENUM-Spalte, nicht VARCHAR: Hibernate 6 mit MySQL-Dialekt erwartet
-- fuer @Enumerated(EnumType.STRING) einen nativen ENUM-Typ, sonst schlaegt
-- ddl-auto=validate beim Start fehl (siehe V309__beleg_sachkonto_enums.sql).
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'quelle');
SET @s = IF(@c = 0,
    'ALTER TABLE beleg ADD COLUMN quelle ENUM(''SCAN'',''QUITTUNG'',''EIGENBELEG'',''TRANSFER'') NOT NULL DEFAULT ''SCAN''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- 2) beleg.gegenpartei -- wer hat gezahlt / wer wurde bezahlt (Freitext)
-- =====================================================================
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'gegenpartei');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN gegenpartei VARCHAR(120) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- 3) beleg.ausgangsrechnung_id -- Verknuepfung zur bezahlten Ausgangsrechnung
-- =====================================================================
-- Tabellenname bestaetigt ueber V327__projekt_geschaeftsdokument_system_generiert.sql
-- (JOINED-Inheritance: projekt_geschaeftsdokument.id ist die PK).
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'ausgangsrechnung_id');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN ausgangsrechnung_id BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @fk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
           WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg'
             AND CONSTRAINT_NAME = 'fk_beleg_ausgangsrechnung');
SET @sfk = IF(@fk = 0,
    'ALTER TABLE beleg ADD CONSTRAINT fk_beleg_ausgangsrechnung FOREIGN KEY (ausgangsrechnung_id) REFERENCES projekt_geschaeftsdokument(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE st FROM @sfk; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- 4) beleg: KI-Rohwerte der Zahlungsart-/Betrags-/Datums-Erkennung
-- =====================================================================
-- Vorschau-Werte, bevor ZahlungsartMapper.zuStammdaten() sie auf eine
-- Stammdaten-Bezeichnung abbildet bzw. der Buchhalter sie beim Pruefen
-- bestaetigt.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'ki_zahlungsart');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN ki_zahlungsart VARCHAR(40) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'ki_belegdatum');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN ki_belegdatum DATE NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'ki_betrag_brutto');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN ki_betrag_brutto DECIMAL(15,2) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'ki_kostenkonto_hinweis');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN ki_kostenkonto_hinweis VARCHAR(255) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- 5) kasse_einstellung: Angaben fuer den Steuerberater-Export (DATEV)
-- =====================================================================
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kasse_einstellung' AND COLUMN_NAME = 'datev_beraternummer');
SET @s = IF(@c = 0, 'ALTER TABLE kasse_einstellung ADD COLUMN datev_beraternummer VARCHAR(7) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kasse_einstellung' AND COLUMN_NAME = 'datev_mandantennummer');
SET @s = IF(@c = 0, 'ALTER TABLE kasse_einstellung ADD COLUMN datev_mandantennummer VARCHAR(5) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- wirtschaftsjahr_beginn_monat: bewusst INT statt der in der Spec
-- genannten TINYINT. Hibernate mappt das Entity-Feld (Integer) auf den
-- generischen SQL-Typ INTEGER; ein TINYINT wuerde ddl-auto=validate beim
-- Start mit "wrong column type" reissen -- dasselbe Grundproblem wie bei
-- den ENUM-Spalten (Punkt 1), nur mit umgekehrtem Vorzeichen: hier erwartet
-- Hibernate den breiteren, nicht den engeren Typ.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kasse_einstellung' AND COLUMN_NAME = 'wirtschaftsjahr_beginn_monat');
SET @s = IF(@c = 0, 'ALTER TABLE kasse_einstellung ADD COLUMN wirtschaftsjahr_beginn_monat INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kasse_einstellung' AND COLUMN_NAME = 'kassenkonto_nummer');
SET @s = IF(@c = 0, 'ALTER TABLE kasse_einstellung ADD COLUMN kassenkonto_nummer VARCHAR(8) NULL DEFAULT ''1000''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kasse_einstellung' AND COLUMN_NAME = 'bankkonto_nummer');
SET @s = IF(@c = 0, 'ALTER TABLE kasse_einstellung ADD COLUMN bankkonto_nummer VARCHAR(8) NULL DEFAULT ''1200''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- 6) Stammdaten: neue Zahlungsart fuer Online-Bezahldienste (Amazon Pay & Co.)
-- =====================================================================
-- zahlungsart hat nur bezeichnung/aktiv/sortierung (V308) -- keine
-- Kategorie-Spalte. Die Zuordnung "Online-Zahlung -> BANK" lebt in
-- ZahlungsartMapper. INSERT IGNORE + UNIQUE(bezeichnung) macht den Seed
-- idempotent (Muster V308).
INSERT IGNORE INTO zahlungsart (bezeichnung, aktiv, sortierung) VALUES ('Online-Zahlung', TRUE, 65);

-- =====================================================================
-- 7) Backfill: beleg.zahlungsart-Codes -> Stammdaten-Bezeichnung
-- =====================================================================
-- ELSE zahlungsart laesst bereits korrekte Klartext-Werte ("Bar",
-- "Überweisung", ...) unangetastet -- macht den Backfill idempotent, ein
-- zweiter Lauf faellt fuer bereits migrierte Zeilen nur noch in den
-- ELSE-Zweig. SONSTIGE wird bewusst NULL: der Nutzer muss beim Pruefen eine
-- echte Zahlungsart waehlen (Pflichtfeld), siehe ZahlungsartMapper.zuStammdaten.
UPDATE beleg SET zahlungsart = CASE UPPER(TRIM(zahlungsart))
    WHEN 'BAR' THEN 'Bar'
    WHEN 'EC' THEN 'EC-Karte'
    WHEN 'EC_KARTE' THEN 'EC-Karte'
    WHEN 'GIROCARD' THEN 'EC-Karte'
    WHEN 'UEBERWEISUNG' THEN 'Überweisung'
    WHEN 'SEPA_LASTSCHRIFT' THEN 'Lastschrift'
    WHEN 'LASTSCHRIFT' THEN 'Lastschrift'
    WHEN 'KREDITKARTE' THEN 'Kreditkarte'
    WHEN 'PAYPAL' THEN 'PayPal'
    WHEN 'AMAZON_PAY' THEN 'Online-Zahlung'
    WHEN 'VORAUSKASSE' THEN 'Überweisung'
    WHEN 'RECHNUNG' THEN 'Rechnung'
    WHEN 'SONSTIGE' THEN NULL
    ELSE zahlungsart
END
WHERE zahlungsart IS NOT NULL;

-- =====================================================================
-- 8) Backfill: bestehende Umbuchungen als TRANSFER kennzeichnen
-- =====================================================================
-- Nur Belege, die noch auf dem Default SCAN stehen -- ein zweiter Lauf
-- aendert nichts mehr (idempotent).
UPDATE beleg SET quelle = 'TRANSFER' WHERE ist_umbuchung = TRUE AND quelle = 'SCAN';
