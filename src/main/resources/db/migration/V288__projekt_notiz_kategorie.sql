-- ═══════════════════════════════════════════════════════════════
-- V288: Kategorie für Projekt-Notizen (EN-1090-Bautagebuch)
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: Für die EN-1090-Akte muss nachvollziehbar sein,
-- welche Verbindungsmittel (HV-Schrauben, Dübel, Anker), Schweiß-
-- nähte, Korrosionsschutzschichten an einem Projekt verbaut/aus-
-- geführt wurden. Statt einer separaten Datenstruktur nutzen wir
-- das vorhandene Bautagebuch (projekt_notiz + projekt_notiz_bild)
-- und kategorisieren die Einträge. Foto vom Etikett (ETA-Nummer
-- und Charge stehen drauf) + strukturierte Notiz reichen für den
-- Audit. Beim EN-1090-Akte-Export werden Notizen mit den ent-
-- sprechenden Kategorien automatisch eingesammelt.
--
-- Mögliche Werte (frei erweiterbar, in Java als Enum gespiegelt):
--   ALLGEMEIN        – default, normaler Bautagebuch-Eintrag
--   VERBINDUNGSMITTEL – HV-Schrauben, Dübel, Anker mit Etikett-Foto
--   SCHWEISSUNG      – Schweißnaht-Doku (Schweißer, WPS, Position)
--   KORROSIONSSCHUTZ – Beschichtungssystem, Schichtdicke
--   OBERFLAECHE      – Strahlgrad, Vorbereitung
--
-- Index: Es existiert bereits ein FK-Index auf projekt_id. Ein
-- zusätzlicher Composite-Index (projekt_id, kategorie) wird erst
-- angelegt, falls der Akte-Export zeigt, dass die Filterung lahmt –
-- bei den typischen Mengen pro Projekt reicht der bestehende Index.
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE projekt_notiz
    ADD COLUMN IF NOT EXISTS kategorie VARCHAR(40) NOT NULL DEFAULT 'ALLGEMEIN';
