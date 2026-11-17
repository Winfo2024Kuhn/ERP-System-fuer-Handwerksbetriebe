package org.example.kalkulationsprogramm.domain;

/** Anlass, zu dem ein {@link AusgangsDokumentStand} festgehalten wurde. */
public enum AusgangsDokumentStandArt {
    /** Das Dokument wurde gedruckt, als PDF gespeichert oder per E-Mail verschickt. */
    EXPORT,
    /** Sicherung nicht exportierter Änderungen, bevor ein älterer Stand zurückgeholt wurde. */
    VOR_WIEDERHERSTELLEN
}
