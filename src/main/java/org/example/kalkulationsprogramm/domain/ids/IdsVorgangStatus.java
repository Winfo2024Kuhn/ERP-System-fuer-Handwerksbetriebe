package org.example.kalkulationsprogramm.domain.ids;

/** Stand eines Shop-Besuchs. */
public enum IdsVorgangStatus {
    /** Shop ist offen, der Warenkorb ist noch nicht zurückgekommen. */
    WARTET,
    /** Der Shop hat den Warenkorb zurückgeschickt. */
    EMPFANGEN,
    /** Der Shop-Besuch war zu lange offen; ein später Warenkorb wird nicht mehr angenommen. */
    ABGELAUFEN,
    /** Der Shop hat etwas geschickt, das nicht als Warenkorb lesbar war. */
    FEHLER
}
