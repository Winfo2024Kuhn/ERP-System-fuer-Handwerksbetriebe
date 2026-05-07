-- Gesendete Mails (OUT) gelten automatisch als gelesen.
-- Verhindert, dass falsche unread-Counts in den Ordnern Projekte/Anfragen/Lieferanten
-- und Gesendet erscheinen, obwohl das Listing keine ungelesenen Mails mehr zeigt
-- (OUT wird ab sofort aus diesen Ordnern gefiltert).
UPDATE email
SET is_read = TRUE
WHERE direction = 'OUT'
  AND is_read = FALSE;
