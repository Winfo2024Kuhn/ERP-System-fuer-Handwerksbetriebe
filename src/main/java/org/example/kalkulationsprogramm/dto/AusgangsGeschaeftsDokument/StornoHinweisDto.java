package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

/**
 * Hinweis an den Nutzer nach einer Stornierung, der über die Stornorechnung selbst
 * hinausgeht – etwa weil eine Abschlagsrechnung Folgen für die Schlussrechnung hat.
 *
 * @param art  steuert, wie dringend das Frontend den Hinweis anzeigt
 * @param text fertiger Satz in Handwerker-Sprache
 */
public record StornoHinweisDto(Art art, String text) {

    public enum Art {
        /** Eine gebuchte Schlussrechnung wurde automatisch mitstorniert. */
        MITSTORNIERT,
        /** Ein Schlussrechnungs-Entwurf bleibt stehen und zieht den stornierten Abschlag noch ab. */
        ENTWURF_PRUEFEN
    }
}
