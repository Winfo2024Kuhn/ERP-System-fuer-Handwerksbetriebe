-- Konfigurierbare E-Mail-Textvorlagen pro Dokumenttyp.
-- Ersetzt die in org.example.email.EmailService hartcodierten Texte fuer
-- Rechnungen, Mahnungen, Auftragsbestaetigungen, Anfragen und Zeichnungen.
-- Platzhalter {{TOKEN}} werden zur Laufzeit aus dem Versand-Kontext ersetzt.
--
-- Idempotent: Tabelle und Seed-Zeilen nur anlegen, falls noch nicht vorhanden.

CREATE TABLE IF NOT EXISTS email_text_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dokument_typ VARCHAR(40) NOT NULL,
    name VARCHAR(150) NOT NULL,
    subject_template VARCHAR(500) NOT NULL,
    html_body LONGTEXT NOT NULL,
    aktiv TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_text_template_doktyp (dokument_typ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed-Inserts via INSERT IGNORE: laufende Edits der User werden nicht ueberschrieben.

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'RECHNUNG',
    'Rechnung',
    'Rechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei sende ich Ihnen die Rechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br>',
        '<strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>',
        '<p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span> auf das in der Rechnung angegebene Konto.<br>',
        '<strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'TEILRECHNUNG',
    'Teilrechnung',
    'Teilrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei sende ich Ihnen eine Teilrechnung für unsere bereits erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br>',
        '<strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>',
        '<p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br>',
        '<strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'SCHLUSSRECHNUNG',
    'Schlussrechnung',
    'Schlussrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei sende ich Ihnen die Schlussrechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p>',
        '<p>Wir würden uns sehr über eine Bewertung freuen: {{REVIEW_LINK}}</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br>',
        '<strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>',
        '<p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br>',
        '<strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'ABSCHLAGSRECHNUNG',
    'Abschlagsrechnung',
    'Abschlagsrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei sende ich Ihnen eine Abschlagsrechnung gemäß unserer Vereinbarung. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br>',
        '<strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>',
        '<p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br>',
        '<strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'MAHNUNG',
    'Mahnung',
    'Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>leider haben wir festgestellt, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p>',
        '<p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p>',
        '<p>Bitte überweisen Sie den ausstehenden Betrag umgehend, um zusätzliche Mahngebühren zu vermeiden.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br>',
        '<strong>Offener Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'ANGEBOT',
    'Anfrage / Angebot',
    'Anfrage: (BV: {{BAUVORHABEN}}) Anfragesnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>im Anhang finden Sie das besprochene Angebot.<br>',
        'Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.</p>',
        '<p>Bei Auftragserteilung wird von uns eine 3D-Zeichnung mit genauen Maßen erstellt.<br>',
        'Nach Freigabe der Zeichnung gehen wir in die Produktion.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Anfragesnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'AUFTRAGSBESTAETIGUNG',
    'Auftragsbestätigung',
    'Auftragsbestätigung: (BV: {{BAUVORHABEN}}) Auftragsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei sende ich Ihnen die Auftragsbestätigung. Die detaillierte Auftragsbestätigung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Auftragsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Auftragssumme:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'ZEICHNUNG',
    'Zeichnung / Entwurf',
    'Kundenzeichnung BV: ({{BAUVORHABEN}})',
    CONCAT(
        '<p>{{ANREDE}},</p>',
        '<p>anbei finden Sie die PDF mit dem ersten Entwurf Ihres Bauprojekts.<br>',
        'Bitte nehmen Sie sich etwas Zeit, um das Design sorgfältig zu überprüfen.<br>',
        'Sollten Sie weitere Änderungswünsche haben oder Fragen auftauchen, stehe ich Ihnen gerne zur Verfügung.</p>',
        '<p>Wir möchten Sie darauf hinweisen, dass größere Zeichnungsänderungen, die gravierend vom ursprünglichen Angebot abweichen, aufgrund des damit verbundenen Zeitaufwands zusätzliche Kosten verursachen können. Wir bitten um Ihr Verständnis dafür.</p>',
        '<p>Falls dies im Angebot so vereinbart war, wird nach Abschluss der Planung eine Abschlagsrechnung erstellt.<br>',
        'Bei Fragen oder weiteren Anliegen stehe ich Ihnen jederzeit zur Verfügung.</p>',
        '<p>Vielen Dank für Ihre Zusammenarbeit und Ihr Verständnis.</p>'
    ),
    1, NOW(6), NOW(6)
);
