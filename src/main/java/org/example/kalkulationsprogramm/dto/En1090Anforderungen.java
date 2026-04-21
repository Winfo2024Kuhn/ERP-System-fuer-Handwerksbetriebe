package org.example.kalkulationsprogramm.dto;

/**
 * Zentrale Antwort aus {@code En1090AnforderungenService}: Gilt fuer ein Projekt
 * der volle EN-1090-2-Ablauf (Werkstoffzeugnis-Pflicht, Schweisser-Pruefbescheinigung,
 * WPS, SAP-Freigabe, Rueckverfolgbarkeit, ZfP) — ja oder nein?
 *
 * <p>Die Unterscheidung EXC 1 vs. EXC 2 bleibt in der DB fuer interne Auswertungen
 * erhalten, wird hier aber nicht differenziert: Beide loesen den vollen Ablauf nach
 * DIN EN 1090-2 aus. EXC 3 / EXC 4 werden wie {@code null} behandelt.
 */
public record En1090Anforderungen(boolean en1090Pflichtig) {

    public static final En1090Anforderungen KEINE = new En1090Anforderungen(false);
    public static final En1090Anforderungen PFLICHTIG = new En1090Anforderungen(true);
}
