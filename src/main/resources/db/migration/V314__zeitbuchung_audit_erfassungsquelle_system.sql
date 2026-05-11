-- Adds the SYSTEM value to the geaendert_via ENUM columns in both audit tables.
-- Background: ErfassungsQuelle.SYSTEM was added to the Java enum after the tables were
-- initially created, causing MySQL error 1265 ("Data truncated") when ZeitbuchungAutoStopService
-- writes audit entries with quelle=SYSTEM.
-- Hibernate 6.x maps @Enumerated(EnumType.STRING) to native MySQL ENUM columns, so an
-- ALTER TABLE is required whenever the Java enum gains a new constant.

ALTER TABLE zeitbuchung_audit
    MODIFY COLUMN geaendert_via
        ENUM('MOBILE_APP','DESKTOP','ADMIN_KORREKTUR','IMPORT','SYSTEM') NOT NULL;

ALTER TABLE zeitkonto_korrektur_audit
    MODIFY COLUMN geaendert_via
        ENUM('MOBILE_APP','DESKTOP','ADMIN_KORREKTUR','IMPORT','SYSTEM') NOT NULL;
