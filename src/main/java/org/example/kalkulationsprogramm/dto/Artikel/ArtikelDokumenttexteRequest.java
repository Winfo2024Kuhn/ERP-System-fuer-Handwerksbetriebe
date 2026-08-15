package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Body des Endpunkts, mit dem die Angebotsfelder eines Artikels gepflegt werden.
 *
 * <p>Echtes Teil-Update: Ein Feld, das im JSON-Body gar nicht vorkommt, bleibt
 * am Artikel unveraendert. Ein Feld, das ausdruecklich mit {@code null} oder
 * leer mitgesendet wird, wird geloescht. Jackson ruft einen Setter nur fuer
 * Schluessel auf, die im JSON tatsaechlich stehen - die *Gesetzt-Flags
 * unterscheiden damit "nicht dabei" von "ausdruecklich null".
 */
@Getter
public class ArtikelDokumenttexteRequest {

    /** Innensicht fuer den DocumentEditor, max. 255 Zeichen. */
    private String kurzbeschreibung;
    private boolean kurzbeschreibungGesetzt;

    /** Rich-Text-HTML fuer das Kundendokument, max. 10.000 Zeichen. */
    private String beschreibung;
    private boolean beschreibungGesetzt;

    /** Aufschlag auf den Einkaufspreis in Prozent, 0 bis 999,99. */
    private BigDecimal verkaufsaufschlagProzent;
    private boolean verkaufsaufschlagProzentGesetzt;

    public void setKurzbeschreibung(String kurzbeschreibung) {
        this.kurzbeschreibung = kurzbeschreibung;
        this.kurzbeschreibungGesetzt = true;
    }

    public void setBeschreibung(String beschreibung) {
        this.beschreibung = beschreibung;
        this.beschreibungGesetzt = true;
    }

    public void setVerkaufsaufschlagProzent(BigDecimal verkaufsaufschlagProzent) {
        this.verkaufsaufschlagProzent = verkaufsaufschlagProzent;
        this.verkaufsaufschlagProzentGesetzt = true;
    }
}
