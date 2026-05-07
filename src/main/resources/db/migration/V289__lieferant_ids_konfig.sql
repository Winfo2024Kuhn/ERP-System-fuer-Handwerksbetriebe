-- ═══════════════════════════════════════════════════════════════
-- V289: IDS-Connect-Konfiguration pro Lieferant
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund: IDS-CONNECT (ZVSHK-Standard) erlaubt Punchout in
-- den Online-Shop des Lieferanten. Der Bauleiter waehlt Material
-- im Lieferanten-Shop aus, der Cart wird automatisch als Bestellung
-- ins ERP zurueckgespielt. Das Protokoll ist standardisiert; pro
-- Lieferant sind aber unterschiedliche URL/Credentials noetig.
-- Die Konfig wird daher NICHT in application-local.properties
-- abgelegt, sondern in der DB durch den Admin pflegbar gemacht.
--
-- Sicherheit: passwort_verschluesselt enthaelt das IDS-Passwort
-- AES-GCM-verschluesselt (Master-Key in application-local.properties
-- als ids.encryption.key). Klartext landet nie in der DB und nie
-- im Frontend (Read-Endpoint maskiert das Passwort als "********").
--
-- Eine Konfig pro Lieferant (1:1, daher UNIQUE auf lieferant_id).
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS lieferant_ids_konfig (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    lieferant_id             BIGINT       NOT NULL,
    aktiviert                BOOLEAN      NOT NULL DEFAULT FALSE,
    protokoll                VARCHAR(40)  NOT NULL DEFAULT 'IDS_CONNECT_2_5',
    punchout_url             VARCHAR(500) NULL,
    kundennummer             VARCHAR(100) NULL,
    login_name               VARCHAR(100) NULL,
    passwort_verschluesselt  VARCHAR(500) NULL,
    notizen                  TEXT         NULL,
    geaendert_am             DATETIME     NULL,
    geaendert_von_id         BIGINT       NULL,
    erstellt_am              DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ids_konfig_lieferant (lieferant_id),
    CONSTRAINT fk_ids_konfig_lieferant
        FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE CASCADE,
    CONSTRAINT fk_ids_konfig_geaendert_von
        FOREIGN KEY (geaendert_von_id) REFERENCES mitarbeiter(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
