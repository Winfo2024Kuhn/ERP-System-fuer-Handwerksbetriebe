CREATE TABLE IF NOT EXISTS einkauf_bedarf (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    position_snapshot JSON NOT NULL,
    liefergruppe_snapshot JSON NOT NULL,
    projekt_id BIGINT NULL,
    interne_kennung VARCHAR(128) NULL,
    bezeichnung VARCHAR(255) NOT NULL,
    artikel_in_projekt_id BIGINT NULL,
    bedarf_menge DECIMAL(19,6) NULL,
    lagergedeckt DECIMAL(19,6) NOT NULL DEFAULT 0,
    reserviert DECIMAL(19,6) NOT NULL DEFAULT 0,
    bestellt DECIMAL(19,6) NOT NULL DEFAULT 0,
    geliefert DECIMAL(19,6) NOT NULL DEFAULT 0,
    storniert DECIMAL(19,6) NOT NULL DEFAULT 0,
    nachpflege_erforderlich BIT(1) NOT NULL DEFAULT b'0',
    historisch_bestellt BIT(1) NOT NULL DEFAULT b'0',
    historisch_aus_lager BIT(1) NOT NULL DEFAULT b'0',
    historischer_hinweis VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_einkauf_bedarf_aip (artikel_in_projekt_id),
    UNIQUE KEY uk_einkauf_bedarf_projekt_kennung (projekt_id, interne_kennung),
    KEY ix_einkauf_bedarf_projekt (projekt_id),
    CONSTRAINT fk_einkauf_bedarf_projekt FOREIGN KEY (projekt_id) REFERENCES projekt (id),
    CONSTRAINT fk_einkauf_bedarf_aip FOREIGN KEY (artikel_in_projekt_id) REFERENCES artikel_in_projekt (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkauf_mengenbuchung (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bedarf_id BIGINT NOT NULL,
    aktion ENUM('RESERVIEREN','RESERVIERUNG_FREIGEBEN','BESTELLEN','LAGER_ENTNEHMEN','STORNO_BESTAETIGEN','LIEFERN') NOT NULL,
    menge DECIMAL(19,6) NOT NULL,
    vorgangsschluessel VARCHAR(128) NOT NULL,
    idempotenz_key VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    akteur_id BIGINT NOT NULL,
    zeitpunkt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_einkauf_mengen_idempotenz (idempotenz_key, bedarf_id),
    KEY ix_einkauf_mengen_bedarf (bedarf_id, id),
    CONSTRAINT fk_einkauf_mengen_bedarf FOREIGN KEY (bedarf_id) REFERENCES einkauf_bedarf (id)
) ENGINE=InnoDB;

-- Übernahme der vorhandenen Projektpositionen. Historische Flags bleiben explizit
-- Altbestand und werden nicht in erfundene Bestell-/Versandbuchungen umgewandelt.
INSERT INTO einkauf_bedarf (
    version, position_snapshot, liefergruppe_snapshot, projekt_id, interne_kennung,
    bezeichnung, artikel_in_projekt_id, bedarf_menge, lagergedeckt, reserviert, bestellt,
    geliefert, storniert, nachpflege_erforderlich,
    historisch_bestellt, historisch_aus_lager, historischer_hinweis
)
SELECT 0,
       JSON_OBJECT(
           'art', 'ARTIKEL', 'artikelId', aip.artikel_id, 'interneReferenz', a.artikelnummer,
           'zeichnungsnummer', NULL, 'zeichnungsrevision', NULL, 'bezeichnung', a.produktname,
           'werkstoff', NULL, 'abmessung', NULL,
           'basis', JSON_OBJECT(
               'menge', CASE a.verrechnungseinheit
                   WHEN 'KILOGRAMM' THEN IF(aip.kilogramm > 0, aip.kilogramm, NULL)
                   WHEN 'LAUFENDE_METER' THEN IF(aip.meter > 0, aip.meter, NULL)
                   WHEN 'QUADRATMETER' THEN IF(aip.meter > 0, aip.meter, NULL)
                   WHEN 'STUECK' THEN IF(aip.stueckzahl > 0, aip.stueckzahl, NULL) ELSE NULL END,
               'einheit', CASE a.verrechnungseinheit
                   WHEN 'KILOGRAMM' THEN 'KILOGRAMM'
                   WHEN 'LAUFENDE_METER' THEN 'METER'
                   WHEN 'QUADRATMETER' THEN 'QUADRATMETER'
                   WHEN 'STUECK' THEN 'STUECK' ELSE NULL END,
               'stueckzahl', aip.stueckzahl, 'einzelLaengeMm', NULL, 'kgJeMeter', NULL, 'faktorQuelle', NULL
           ),
           'schnittForm', aip.schnitt_form, 'winkelLinks', aip.anschnitt_winkel_links,
           'winkelRechts', aip.anschnitt_winkel_rechts, 'bearbeitung', aip.kommentar,
           'oberflaeche', NULL, 'dokumente', JSON_ARRAY(), 'anlageVersionIds', JSON_ARRAY()
       ),
       JSON_OBJECT('lieferadresse', NULL, 'bedarfstermin', NULL, 'projektId', aip.projekt_id, 'lagerzweck', NULL),
       aip.projekt_id, NULL, 'Historische Projektposition', aip.id,
       CASE a.verrechnungseinheit
           WHEN 'KILOGRAMM' THEN IF(aip.kilogramm > 0, aip.kilogramm, NULL)
           WHEN 'LAUFENDE_METER' THEN IF(aip.meter > 0, aip.meter, NULL)
           WHEN 'QUADRATMETER' THEN IF(aip.meter > 0, aip.meter, NULL)
           WHEN 'STUECK' THEN IF(aip.stueckzahl > 0, aip.stueckzahl, NULL) ELSE NULL END,
       0, 0, 0, 0, 0,
       CASE WHEN aip.artikel_id IS NULL OR a.id IS NULL OR a.produktname IS NULL OR a.artikelnummer IS NULL OR
                 CASE a.verrechnungseinheit
                     WHEN 'KILOGRAMM' THEN IF(aip.kilogramm > 0, aip.kilogramm, NULL)
                     WHEN 'LAUFENDE_METER' THEN IF(aip.meter > 0, aip.meter, NULL)
                     WHEN 'QUADRATMETER' THEN IF(aip.meter > 0, aip.meter, NULL)
                     WHEN 'STUECK' THEN IF(aip.stueckzahl > 0, aip.stueckzahl, NULL) ELSE NULL END IS NULL
            THEN b'1' ELSE b'0' END,
       CASE WHEN aip.bestellt = b'1' AND aip.aus_lager = b'0' THEN b'1' ELSE b'0' END,
       CASE WHEN aip.aus_lager = b'1' THEN b'1' ELSE b'0' END,
       CASE WHEN aip.bestellt = b'1' AND aip.aus_lager = b'0'
            THEN 'Historisch als bestellt markiert; bitte Beleg und offenen Rest prüfen.'
            WHEN aip.aus_lager = b'1' THEN 'Historisch als Lagerentnahme markiert; bitte Lagerdeckung prüfen.'
            ELSE NULL END
FROM artikel_in_projekt aip
LEFT JOIN artikel a ON a.id = aip.artikel_id
WHERE NOT EXISTS (
    SELECT 1 FROM einkauf_bedarf eb WHERE eb.artikel_in_projekt_id = aip.id
);

UPDATE einkauf_bedarf
SET nachpflege_erforderlich = b'1'
WHERE historisch_bestellt = b'1' OR historisch_aus_lager = b'1';
