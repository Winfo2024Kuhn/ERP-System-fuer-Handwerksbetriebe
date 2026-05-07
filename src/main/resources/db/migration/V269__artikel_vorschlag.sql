-- Review-Queue für KI-generierte Artikel-Vorschläge aus Lieferantenrechnungs-Analyse.
-- Vorschläge werden erzeugt, wenn der KI-Matching-Agent keinen passenden Artikel
-- im Materialstamm findet oder ein Konflikt mit bestehenden externen Nummern besteht.

CREATE TABLE artikel_vorschlag (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    status                          VARCHAR(20)     NOT NULL,
    typ                             VARCHAR(30)     NOT NULL,
    erstellt_am                     DATETIME        NOT NULL,
    bearbeitet_am                   DATETIME        NULL,

    lieferant_id                    BIGINT          NULL,
    quelle_dokument_id              BIGINT          NULL,

    externe_artikelnummer           VARCHAR(255)    NULL,
    produktname                     VARCHAR(500)    NULL,
    produktlinie                    VARCHAR(500)    NULL,
    produkttext                     VARCHAR(2000)   NULL,

    vorgeschlagene_kategorie_id     INT             NULL,
    vorgeschlagener_werkstoff_id    BIGINT          NULL,

    masse                           DECIMAL(19,4)   NULL,
    hoehe                           INT             NULL,
    breite                          INT             NULL,

    einzelpreis                     DECIMAL(19,4)   NULL,
    preiseinheit                    VARCHAR(50)     NULL,

    ki_konfidenz                    DECIMAL(4,3)    NULL,
    ki_begruendung                  VARCHAR(1000)   NULL,

    -- Bei typ = KONFLIKT_EXTERNE_NUMMER: der konkurrierende Artikel
    konflikt_artikel_id             BIGINT          NULL,

    -- Bei typ = MATCH_VORSCHLAG (KI schlägt existierenden Artikel vor,
    -- aber Konfidenz zu niedrig für Auto-Update): der Treffer-Artikel
    treffer_artikel_id              BIGINT          NULL,

    CONSTRAINT fk_av_lieferant
        FOREIGN KEY (lieferant_id) REFERENCES lieferanten (id) ON DELETE SET NULL,
    CONSTRAINT fk_av_quelle_dokument
        FOREIGN KEY (quelle_dokument_id) REFERENCES lieferant_dokument (id) ON DELETE SET NULL,
    CONSTRAINT fk_av_kategorie
        FOREIGN KEY (vorgeschlagene_kategorie_id) REFERENCES kategorie (id) ON DELETE SET NULL,
    CONSTRAINT fk_av_werkstoff
        FOREIGN KEY (vorgeschlagener_werkstoff_id) REFERENCES werkstoff (id) ON DELETE SET NULL,
    CONSTRAINT fk_av_konflikt_artikel
        FOREIGN KEY (konflikt_artikel_id) REFERENCES artikel (id) ON DELETE SET NULL,
    CONSTRAINT fk_av_treffer_artikel
        FOREIGN KEY (treffer_artikel_id) REFERENCES artikel (id) ON DELETE SET NULL
);

CREATE INDEX idx_artikel_vorschlag_status        ON artikel_vorschlag (status, erstellt_am);
CREATE INDEX idx_artikel_vorschlag_lieferant     ON artikel_vorschlag (lieferant_id);
CREATE INDEX idx_artikel_vorschlag_externe_nr    ON artikel_vorschlag (externe_artikelnummer);
