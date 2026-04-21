-- ═══════════════════════════════════════════════════════════════
-- V232: wps_lage.typ als MySQL-ENUM
-- ═══════════════════════════════════════════════════════════════
-- Die Entity WpsLage nutzt @Enumerated(EnumType.STRING) auf WpsLage.Typ.
-- Hibernate 6 (MySQLDialect) erzeugt dafür ENUM-Spalten und bricht bei
-- Schema-Validation ab, wenn es VARCHAR vorfindet. Diese Migration
-- konvertiert die Spalte auf ENUM — gleiches Pattern wie V231.

ALTER TABLE wps_lage
    MODIFY COLUMN typ
        ENUM('WURZEL','FUELL','DECK') NOT NULL;
