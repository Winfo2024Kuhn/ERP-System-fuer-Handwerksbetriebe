--
-- Nebenkosten, die nur zu einzelnen Positionen einer Lieferantenrechnung gehoeren (Issue #242)
--
-- WARUM
-- Bei der Aufteilung einer Rechnung nach Positionen wurden alle Nebenkosten und Rabatte
-- anteilig nach Warenwert auf alle Projekte verteilt. Ein Legierungszuschlag "zu Pos. 3"
-- gehoert aber nur zum Projekt von Position 3.
--
-- WAS
-- Je Nebenkosten- bzw. Rabattposition die Warenpositionen desselben Belegs, zu denen sie
-- gehoert. Keine Zeile = gilt fuer alle Positionen (anteilig wie bisher). Die KI traegt den
-- Bezug beim Auslesen ein, im Zuordnen-Dialog laesst er sich aendern.
--
-- ROBUSTHEIT
-- Tabelle und Index werden nur angelegt, wenn sie fehlen. Wird eine der beiden Positionen
-- geloescht (z. B. beim erneuten Auslesen), verschwindet auch der Bezug (ON DELETE CASCADE).

CREATE TABLE IF NOT EXISTS lieferant_dokument_position_bezug (
    position_id BIGINT NOT NULL,
    bezug_position_id BIGINT NOT NULL,
    CONSTRAINT pk_ld_position_bezug PRIMARY KEY (position_id, bezug_position_id),
    CONSTRAINT fk_ld_position_bezug_position FOREIGN KEY (position_id)
        REFERENCES lieferant_dokument_position (id) ON DELETE CASCADE,
    CONSTRAINT fk_ld_position_bezug_ware FOREIGN KEY (bezug_position_id)
        REFERENCES lieferant_dokument_position (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ld_position_bezug_ware
    ON lieferant_dokument_position_bezug (bezug_position_id);
