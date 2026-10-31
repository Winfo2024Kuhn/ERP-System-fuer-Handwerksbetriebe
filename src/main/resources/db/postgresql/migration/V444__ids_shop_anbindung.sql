-- Shop-Anbindung über IDS-Connect (Standard der Großhändler, z. B. Würth).
--
-- WARUM
-- Wer Material aus dem Lager verrechnen will, soll die richtigen Einkaufspreise haben, ohne den
-- Artikelstamm vorher sauber zu pflegen: Im Dokumenteditor öffnet das ERP den Online-Shop des
-- Lieferanten, der Nutzer stellt dort einen Warenkorb zusammen, der Shop schickt ihn über den
-- Browser zurück. Daraus werden Positionen; auf Wunsch werden Preise im Artikelstamm nachgezogen.
-- Der Warenkorb ist bewusst fachneutral gespeichert, damit ein späteres Beschaffungsmodul denselben
-- Shop-Besuch für Bestellungen nutzen kann.
--
-- WAS
--   ids_zugang                 Shop-Zugang je Lieferant (höchstens einer). Das Passwort liegt
--                              verschlüsselt (MailSecretService, mail.credentials.encryption-key).
--                              protokoll = Java-Enum IdsProtokoll, daher VARCHAR ohne CHECK.
--   ids_vorgang                ein Shop-Besuch: wer ihn gestartet hat, mit welchem Protokoll, bis wann
--                              er gilt, ob der Warenkorb angekommen ist. Der Rücksprung-Schlüssel steht nur als
--                              SHA-256-Hash in der Datenbank. status = Java-Enum IdsVorgangStatus.
--                              rohdaten = der Warenkorb, wie der Shop ihn geschickt hat (zur Fehlersuche,
--                              z. B. wie ein Shop Preise meint); geht nach 30 Tagen mit dem Vorgang weg.
--   ids_warenkorb_position     die zurückgeschickten Artikel eines Shop-Besuchs.
--   system_setting ids.aktiv   Grundschalter (fehlt die Zeile, ist die Anbindung aus).
--
-- ROBUSTHEIT
-- CREATE TABLE / INDEX IF NOT EXISTS - ein zweiter Lauf ändert nichts.

CREATE TABLE IF NOT EXISTS ids_zugang (
    id                      BIGSERIAL    PRIMARY KEY,
    lieferant_id            BIGINT       NOT NULL UNIQUE REFERENCES lieferanten (id) ON DELETE CASCADE,
    aktiv                   BOOLEAN      NOT NULL DEFAULT FALSE,
    protokoll               VARCHAR(40)  NOT NULL DEFAULT 'IDS_CONNECT_2_5',
    shop_url                VARCHAR(500),
    kundennummer            VARCHAR(50),
    benutzername            VARCHAR(50),
    passwort_verschluesselt VARCHAR(500),
    geaendert_am            TIMESTAMP,
    version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ids_vorgang (
    id                BIGSERIAL    PRIMARY KEY,
    lieferant_id      BIGINT       NOT NULL REFERENCES lieferanten (id) ON DELETE CASCADE,
    frontend_user_id  BIGINT       NOT NULL,
    protokoll         VARCHAR(40)  NOT NULL,
    schluessel_hash   VARCHAR(64)  NOT NULL UNIQUE,
    status            VARCHAR(20)  NOT NULL,
    erstellt_am       TIMESTAMP    NOT NULL,
    gueltig_bis       TIMESTAMP    NOT NULL,
    empfangen_am      TIMESTAMP,
    mit_bestellung    BOOLEAN      NOT NULL DEFAULT FALSE,
    fehlertext        VARCHAR(500),
    rohdaten          TEXT
);

CREATE INDEX IF NOT EXISTS idx_ids_vorgang_erstellt ON ids_vorgang (erstellt_am);

CREATE TABLE IF NOT EXISTS ids_warenkorb_position (
    id               BIGSERIAL     PRIMARY KEY,
    vorgang_id       BIGINT        NOT NULL REFERENCES ids_vorgang (id) ON DELETE CASCADE,
    position_index   INTEGER       NOT NULL,
    artikelnummer    VARCHAR(100)  NOT NULL,
    bezeichnung      VARCHAR(255)  NOT NULL,
    langtext         VARCHAR(2000),
    menge            NUMERIC(15,3) NOT NULL,
    einheit          VARCHAR(20)   NOT NULL,
    einkaufspreis    NUMERIC(19,4),
    hinweis          VARCHAR(1000),
    ean              VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_ids_warenkorb_position_vorgang ON ids_warenkorb_position (vorgang_id);
