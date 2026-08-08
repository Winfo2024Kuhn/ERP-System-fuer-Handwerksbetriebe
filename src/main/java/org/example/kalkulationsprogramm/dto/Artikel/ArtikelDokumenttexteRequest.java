package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Body des Endpunkts, mit dem die Angebotsfelder eines Artikels gepflegt werden.
 *
 * <p>Alle Felder sind optional: Ein {@code null}-Wert loescht das jeweilige Feld
 * bewusst. Das Backfill-Skript nutzt denselben Endpunkt und schickt nur die
 * Felder, die es setzen will - es muss deshalb die anderen mit ihrem bisherigen
 * Wert mitsenden.
 */
@Getter
@Setter
public class ArtikelDokumenttexteRequest {

    /** Innensicht fuer den DocumentEditor, max. 255 Zeichen. */
    private String kurzbeschreibung;

    /** Rich-Text-HTML fuer das Kundendokument, max. 10.000 Zeichen. */
    private String beschreibung;

    /** Aufschlag auf den Einkaufspreis in Prozent, 0 bis 999,99. */
    private BigDecimal verkaufsaufschlagProzent;
}
