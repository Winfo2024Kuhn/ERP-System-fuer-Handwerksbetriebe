-- System-Standard-Signatur fuer automatisch versendete E-Mails
-- (Auto-Auftragsbestaetigung, Mahnverfahren, kuenftige automatische
-- Versender). Genau eine Signatur traegt das Flag is_system_default = 1
-- und wird vom Backend angehaengt, wenn eine Mail ohne eingeloggten
-- Mitarbeiter rausgeht.
--
-- Wir koppeln das bewusst NICHT an einen Pseudo-Mitarbeiter — die System-
-- Signatur lebt direkt auf email_signature und ist im Signaturen-UI
-- editierbar.

-- 1. Spalte idempotent hinzufuegen (Pattern wie V214).
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email_signature'
      AND COLUMN_NAME  = 'is_system_default'
);

SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE email_signature ADD COLUMN is_system_default TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Seed-Zeile genau dann einspielen, wenn noch keine System-Default-Signatur existiert.
--    INSERT ... SELECT ... WHERE NOT EXISTS ist atomar und idempotent.
--
--    Der Seed enthaelt BEWUSST keinen fertigen Signatur-Text — das ERP wird
--    von vielen Betrieben genutzt, jeder Handwerker pflegt seinen eigenen
--    Wortlaut und sein eigenes Logo. Die Seed-Zeile ist nur ein Platzhalter,
--    der den Inhaber im Signaturen-UI klar dazu auffordert, die System-
--    Signatur selbst zu hinterlegen. Solange dieser Platzhalter unveraendert
--    ist, haengt das Backend keine Signatur an Auto-Mails an (siehe
--    EmailSignatureService.getSystemDefaultSignature + isPlatzhalter).
INSERT INTO email_signature (name, html, is_system_default, created_at, updated_at)
SELECT
    'System (automatische E-Mails)',
    CONCAT(
        '<div class="email-signature" data-system-placeholder="1" ',
             'style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#888;',
                    'border:1px dashed #cbd5e1;background:#f8fafc;padding:12px;border-radius:6px;">',
            '<p style="margin:0 0 6px 0;font-weight:600;color:#475569;">',
                'Hier kann Ihre System-Signatur eingetragen werden.',
            '</p>',
            '<p style="margin:0;">',
                'Diese Signatur wird an alle automatisch versendeten E-Mails ',
                '(Auftragsbest&auml;tigungen, Mahnungen, ...) angeh&auml;ngt. ',
                'Bitte im Bereich „E-Mail-Signaturen" anpassen.',
            '</p>',
        '</div>'
    ),
    1,
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM email_signature WHERE is_system_default = 1
);
