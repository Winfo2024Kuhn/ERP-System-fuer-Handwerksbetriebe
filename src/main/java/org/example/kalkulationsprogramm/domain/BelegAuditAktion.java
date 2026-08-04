package org.example.kalkulationsprogramm.domain;

/**
 * Was am Kassenbuch passiert ist. Jeder Wert erzeugt genau einen
 * unveraenderlichen {@link BelegAudit}-Eintrag in der Hash-Kette.
 *
 * <p>Die Reihenfolge der Konstanten ist bedeutungslos, die Namen dagegen
 * nicht: sie stehen woertlich in der kanonischen Form, ueber die der Hash
 * gebildet wird. Ein Umbenennen wuerde jede bestehende Kette brechen.</p>
 */
public enum BelegAuditAktion {

    /** Beleg wurde hochgeladen oder als Umbuchung von Hand erfasst. */
    ERFASST,

    /** Inhalt wurde geaendert (vor der Festschreibung frei, danach nur noch Kontierung). */
    GEAENDERT,

    /** Buchhalter hat den Beleg geprueft und auf VALIDIERT gesetzt. */
    VALIDIERT,

    /** Beleg wurde als Schrott/Duplikat verworfen. Nur vor der Festschreibung moeglich. */
    VERWORFEN,

    /** Beleg ist mit dem Monatsabschluss festgeschrieben und hat seine laufende Nummer bekommen. */
    FESTGESCHRIEBEN,

    /** Ein festgeschriebener Beleg wurde durch eine Gegenbuchung storniert. */
    STORNIERT,

    /** Die Gegenbuchung selbst -- steht als eigene Zeile im Kassenbuch. */
    STORNO_ERFASST,

    /** Kassensturz: Bargeld wurde gezaehlt und mit dem rechnerischen Bestand verglichen. */
    KASSE_GEZAEHLT,

    /** Monat abgeschlossen. Ab hier sind alle Belege des Monats gesperrt. */
    MONAT_ABGESCHLOSSEN
}
