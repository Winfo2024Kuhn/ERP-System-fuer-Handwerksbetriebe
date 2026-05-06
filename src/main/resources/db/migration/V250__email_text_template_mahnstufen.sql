-- Vereinheitlicht die E-Mail-Textvorlagen mit dem Dokumenttyp-Enum.
-- Bisher gab es nur eine generische 'MAHNUNG'-Vorlage; tatsaechlich
-- unterscheidet die Anwendung aber drei Mahnstufen
-- (ZAHLUNGSERINNERUNG, ERSTE_MAHNUNG, ZWEITE_MAHNUNG) sowie STORNORECHNUNG
-- und GUTSCHRIFT.
--
-- Strategie:
--  1. Bestehende 'MAHNUNG'-Zeile auf 'ERSTE_MAHNUNG' migrieren, damit
--     bereits angepasste Texte des Anwenders erhalten bleiben.
--  2. Fuer die uebrigen Mahnstufen sowie STORNORECHNUNG/GUTSCHRIFT
--     INSERT IGNORE-Seeds einspielen.

UPDATE email_text_template
SET dokument_typ = 'ERSTE_MAHNUNG',
    name = '1. Mahnung',
    subject_template = REPLACE(subject_template, 'Mahnung:', '1. Mahnung:'),
    updated_at = NOW(6)
WHERE dokument_typ = 'MAHNUNG';

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'ZAHLUNGSERINNERUNG',
    'Zahlungserinnerung',
    'Zahlungserinnerung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>vermutlich ist es Ihrer Aufmerksamkeit entgangen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p>',
        '<p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p>',
        '<p>Bitte überweisen Sie den ausstehenden Betrag in den nächsten Tagen. Sollte sich Ihre Zahlung mit dieser Erinnerung überschnitten haben, betrachten Sie diese E-Mail bitte als gegenstandslos.</p>',
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
    'ERSTE_MAHNUNG',
    '1. Mahnung',
    '1. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>trotz unserer Zahlungserinnerung haben wir bisher keinen Zahlungseingang für die Rechnung mit der Nummer {{DOKUMENTNUMMER}} (Bauvorhaben {{BAUVORHABEN}}) feststellen können.</p>',
        '<p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p>',
        '<p>Wir bitten Sie hiermit, den ausstehenden Betrag umgehend zu überweisen, um zusätzliche Mahngebühren zu vermeiden.</p>',
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
    'ZWEITE_MAHNUNG',
    '2. Mahnung',
    '2. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>leider mussten wir feststellen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} auch nach unserer 1. Mahnung noch immer nicht beglichen wurde.</p>',
        '<p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war bereits am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p>',
        '<p>Wir fordern Sie hiermit letztmalig auf, den ausstehenden Betrag innerhalb von 7 Tagen zu überweisen. Andernfalls sehen wir uns gezwungen, die Forderung an ein Inkassobüro zu übergeben oder gerichtliche Schritte einzuleiten. Die dadurch entstehenden Kosten gehen zu Ihren Lasten.</p>',
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
    'STORNORECHNUNG',
    'Stornorechnung',
    'Stornorechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei erhalten Sie die Stornorechnung zur Rechnung {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}}.</p>',
        '<p>Mit dieser Stornorechnung wird die ursprüngliche Rechnung in voller Höhe storniert. Bitte ersetzen Sie die ursprüngliche Rechnung in Ihren Unterlagen durch die anliegende Stornorechnung.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Stornorechnung-Nr.:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Stornierter Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>'
    ),
    1, NOW(6), NOW(6)
);

INSERT IGNORE INTO email_text_template (dokument_typ, name, subject_template, html_body, aktiv, created_at, updated_at)
VALUES (
    'GUTSCHRIFT',
    'Gutschrift',
    'Gutschrift: (BV: {{BAUVORHABEN}}) Gutschrift-Nr.: {{DOKUMENTNUMMER}}',
    CONCAT(
        '<p>{{ANREDE}} {{KUNDENNAME}},</p>',
        '<p>anbei erhalten Sie die Gutschrift für das Bauvorhaben {{BAUVORHABEN}}.</p>',
        '<p>Den Gutschriftsbetrag werden wir in den nächsten Tagen auf Ihr Konto überweisen bzw. mit der nächsten Rechnung verrechnen.</p>',
        '<p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br>',
        '<strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br>',
        '<strong>Gutschrift-Nr.:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br>',
        '<strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br>',
        '<strong>Gutschriftsbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>'
    ),
    1, NOW(6), NOW(6)
);
