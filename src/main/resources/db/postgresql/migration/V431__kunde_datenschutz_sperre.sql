-- Kunden löschen bzw. aus Datenschutzgründen sperren (Issue #264).
--
-- WARUM
-- Ein Kunde kann verlangen, dass seine Daten gelöscht werden (Art. 17 DSGVO). Hängen aber
-- Projekte, Anfragen, Angebote oder Rechnungen an ihm, müssen diese aufbewahrt werden
-- (§ 147 AO / § 257 HGB: Rechnungen 8 Jahre, Geschäftsbriefe 6 Jahre ab Jahresende).
-- Dann wird der Kunde gesperrt (Art. 18 DSGVO): Was nicht aufbewahrt werden muss, wird
-- sofort gelöscht, Name und Anschrift bleiben für die Unterlagen bis zum Fristende.
--
-- WAS
--   kunde.gesperrt_am          Zeitpunkt der Sperre; NULL = nicht gesperrt
--   kunde.aufbewahren_bis      Ende der längsten Aufbewahrungsfrist, danach endgültig löschbar
--   kunde_loeschprotokoll      Nachweis (Art. 5 Abs. 2 DSGVO): was, wann, durch wen, warum.
--                              Bewusst ohne Name und Anschrift, nur Kunden-ID und -nummer,
--                              und ohne Fremdschlüssel, weil der Kunde danach fehlen kann.
--
-- ROBUSTHEIT
-- ADD COLUMN / CREATE TABLE / CREATE INDEX IF NOT EXISTS.

ALTER TABLE kunde ADD COLUMN IF NOT EXISTS gesperrt_am TIMESTAMP;
ALTER TABLE kunde ADD COLUMN IF NOT EXISTS aufbewahren_bis DATE;

CREATE TABLE IF NOT EXISTS kunde_loeschprotokoll (
    id               BIGSERIAL PRIMARY KEY,
    kunde_id         BIGINT       NOT NULL,
    kundennummer     VARCHAR(255),
    aktion           VARCHAR(30)  NOT NULL,
    grund            VARCHAR(500),
    aufbewahren_bis  DATE,
    ausgefuehrt_von  VARCHAR(255),
    ausgefuehrt_am   TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kunde_loeschprotokoll_kunde ON kunde_loeschprotokoll (kunde_id);
