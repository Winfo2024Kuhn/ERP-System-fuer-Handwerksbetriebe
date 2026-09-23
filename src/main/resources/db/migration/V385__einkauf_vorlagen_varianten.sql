-- Mehrere Fassungen je Vorlagenart und eine separat eindeutige Standardzuordnung.
-- Bestehende Vorlagen bleiben unverändert erhalten und werden jeweils Standard.

SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_text_template'
      AND INDEX_NAME = 'uk_email_text_template_doktyp'
);
SET @sql := IF(@idx_exists > 0,
    'ALTER TABLE email_text_template DROP INDEX uk_email_text_template_doktyp', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_text_template'
      AND COLUMN_NAME = 'standard'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE email_text_template ADD COLUMN standard TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_text_template'
      AND COLUMN_NAME = 'version'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE email_text_template ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @enum_sql := 'ALTER TABLE email_text_template MODIFY COLUMN kategorie ENUM(''DOKUMENT'',''MAHNWESEN'',''WEBSITE'',''EINKAUF'',''SYSTEM'') NULL';
PREPARE stmt FROM @enum_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS email_text_template_standard (
    dokument_typ VARCHAR(40) NOT NULL,
    template_id BIGINT NOT NULL,
    PRIMARY KEY (dokument_typ),
    UNIQUE KEY uk_email_text_template_standard_template (template_id),
    CONSTRAINT fk_email_text_template_standard_template
        FOREIGN KEY (template_id) REFERENCES email_text_template (id)
) ENGINE=InnoDB;

UPDATE email_text_template t
JOIN (
    SELECT dokument_typ, MIN(id) AS template_id
      FROM email_text_template
     GROUP BY dokument_typ
    HAVING COUNT(*) = 1
) einzelvorlage ON einzelvorlage.template_id = t.id
LEFT JOIN email_text_template_standard s ON s.dokument_typ = t.dokument_typ
   SET t.standard = 1
 WHERE t.standard = 0 AND s.dokument_typ IS NULL;

UPDATE email_text_template SET kategorie = 'EINKAUF'
 WHERE dokument_typ LIKE 'EINKAUF\_%';

-- Neutrale, editierbare Startfassungen nur für Typen ohne vorhandene Fassung.
INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_ANFRAGE','EINKAUF','Lieferantenanfrage','Preisanfrage {{ANFRAGENUMMER}} – Lieferung bis {{LIEFERTERMIN}}',
       '<p>{{ANREDE}},</p><p>bitte bieten Sie uns die aufgeführten Positionen bis zum {{ANTWORTFRIST}} an. Bitte nennen Sie Liefertermin, Fracht-, Zuschnitt- und Zeugniskosten getrennt.</p><p>{{POSITIONEN}}</p><p>Antworten Sie gerne direkt auf diese E-Mail.</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_ANFRAGE');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_BESTELLUNG','EINKAUF','Bestellung','Bestellung {{BESTELLNUMMER}} zur Anfrage {{ANFRAGENUMMER}}',
       '<p>{{ANREDE}},</p><p>hiermit bestellen wir die folgenden Positionen.</p><p>{{POSITIONEN}}</p><p>Bitte bestätigen Sie den Liefertermin {{LIEFERTERMIN}}.</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_BESTELLUNG');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_DIREKTBESTELLUNG','EINKAUF','Direktbestellung','Bestellung {{BESTELLNUMMER}}',
       '<p>{{ANREDE}},</p><p>hiermit bestellen wir die folgenden Positionen.</p><p>{{POSITIONEN}}</p><p>Bitte bestätigen Sie den Liefertermin {{LIEFERTERMIN}}.</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_DIREKTBESTELLUNG');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_NACHFRAGE','EINKAUF','Nachfrage zum Angebot','Rückfrage zur Anfrage {{ANFRAGENUMMER}}',
       '<p>{{ANREDE}},</p><p>bitte geben Sie uns eine kurze Rückmeldung zu Ihrem Angebot {{LIEFERANTEN_ANGEBOTSNUMMER}}.</p><p>{{POSITIONEN}}</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_NACHFRAGE');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_ZEUGNIS_NACHFORDERUNG','EINKAUF','Werkstoffzeugnis nachfordern','Werkstoffzeugnis zu Bestellung {{BESTELLNUMMER}}',
       '<p>{{ANREDE}},</p><p>bitte senden Sie uns die noch fehlenden Werkstoffzeugnisse zu.</p><p>{{ZEUGNISSE}}</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_ZEUGNIS_NACHFORDERUNG');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_BESTAETIGUNG_NACHFRAGE','EINKAUF','Auftragsbestätigung nachfragen','Auftragsbestätigung zu Bestellung {{BESTELLNUMMER}}',
       '<p>{{ANREDE}},</p><p>bitte senden Sie uns die Auftragsbestätigung zu unserer Bestellung.</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_BESTAETIGUNG_NACHFRAGE');

INSERT INTO email_text_template
    (dokument_typ, kategorie, name, subject_template, html_body, aktiv, standard, version, created_at, updated_at)
SELECT 'EINKAUF_LIEFERUNG_NACHFRAGE','EINKAUF','Liefertermin nachfragen','Liefertermin zu Bestellung {{BESTELLNUMMER}}',
       '<p>{{ANREDE}},</p><p>bitte teilen Sie uns den aktuellen Liefertermin für unsere Bestellung mit.</p>',
       1,1,0,NOW(6),NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM email_text_template WHERE dokument_typ='EINKAUF_LIEFERUNG_NACHFRAGE');

-- Je Dokumenttyp einen bisherigen Standard bevorzugen, sonst eine bestehende
-- Fassung deterministisch wählen. Der Unique-Key verhindert weitere Zuordnungen.
INSERT IGNORE INTO email_text_template_standard (dokument_typ, template_id)
SELECT dokument_typ,
       COALESCE(MIN(CASE WHEN standard = 1 THEN id END), MIN(id))
  FROM email_text_template
 GROUP BY dokument_typ;

-- Das separate Mapping ist die Quelle der Eindeutigkeit; Boolean-Flags angleichen.
UPDATE email_text_template t
JOIN email_text_template_standard s ON s.dokument_typ = t.dokument_typ
   SET t.standard = IF(t.id = s.template_id, 1, 0);
