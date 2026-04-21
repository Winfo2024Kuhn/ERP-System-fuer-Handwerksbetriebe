-- ═══════════════════════════════════════════════════════════════
-- V233: wps.quelle – Unterscheidung Editor-WPS vs. importierte PDF-WPS
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: Kleine Betriebe wollen bereits existierende
-- Schweißanweisungen (aus anderen Programmen oder vom Schweißfach-
-- ingenieur) als PDF hochladen. Diese sind read-only (kein Editor),
-- werden aber mit den gleichen Metadaten (Nummer, Werkstoff,
-- Gültigkeit) gepflegt und gedruckt.
--
-- Entity: Wps.Quelle (Enum, STRING-basiert, analog WpsLage.Typ)

ALTER TABLE wps
    ADD COLUMN quelle
        ENUM('EDITOR','UPLOAD') NOT NULL DEFAULT 'EDITOR'
        AFTER gespeicherter_dateiname;

-- Alle vorhandenen Datensätze sind editor-basiert (DEFAULT 'EDITOR' greift).
