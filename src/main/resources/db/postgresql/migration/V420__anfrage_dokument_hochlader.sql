-- Anfrage-Dokumente: wer hat die Datei hochgeladen?
--
-- WARUM
-- Bei Projekt-Dokumenten steht der Hochlader in der Tabelle, bei Anfrage-Dokumenten fehlte er.
-- Wer eine Datei zu einer Anfrage hochlaedt, soll nachvollziehbar sein - wie beim Projekt.
--
-- WAS
-- anfrage_dokument.uploaded_by_id verweist auf den Mitarbeiter. Alte Dokumente und Uploads von
-- Benutzern ohne zugeordneten Mitarbeiter bleiben leer (NULL).
--
-- ROBUSTHEIT
-- Die Spalte wird nur angelegt, wenn sie fehlt. Wird ein Mitarbeiter geloescht, bleibt das
-- Dokument erhalten und der Hochlader wird leer (ON DELETE SET NULL).

ALTER TABLE anfrage_dokument
    ADD COLUMN IF NOT EXISTS uploaded_by_id BIGINT REFERENCES mitarbeiter (id) ON DELETE SET NULL;
