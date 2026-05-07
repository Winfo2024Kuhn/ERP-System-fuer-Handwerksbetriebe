-- ═══════════════════════════════════════════════════════════════
-- V231: artikel_preis_historie — einheit & quelle als MySQL-ENUM
-- ═══════════════════════════════════════════════════════════════
-- Die Entity ArtikelPreisHistorie nutzt @Enumerated(EnumType.STRING)
-- auf Verrechnungseinheit und PreisQuelle. Hibernate 6 (MySQLDialect)
-- erzeugt dafür ENUM-Spalten und schlägt bei der Schema-Validation
-- fehl, wenn es VARCHAR sieht (gleiches Pattern wie artikel.verrechnungseinheit).
-- Diese Migration konvertiert beide Spalten idempotent zu ENUM.

-- Spalte einheit (Verrechnungseinheit)
ALTER TABLE artikel_preis_historie
    MODIFY COLUMN einheit
        ENUM('LAUFENDE_METER','QUADRATMETER','KILOGRAMM','STUECK') NOT NULL;

-- Spalte quelle (PreisQuelle)
ALTER TABLE artikel_preis_historie
    MODIFY COLUMN quelle
        ENUM('RECHNUNG','ANGEBOT','KATALOG','MANUELL','VORSCHLAG') NOT NULL;
