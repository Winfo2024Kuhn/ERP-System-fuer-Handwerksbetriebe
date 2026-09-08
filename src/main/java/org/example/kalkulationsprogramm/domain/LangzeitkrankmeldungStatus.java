package org.example.kalkulationsprogramm.domain;

/**
 * Status einer {@link Langzeitkrankmeldung}.
 *
 * <p>Erlaubte Uebergaenge (alles andere ist fachlich ungueltig):
 * <ul>
 *   <li>(neu) -&gt; {@link #LAUFEND}</li>
 *   <li>{@link #LAUFEND} -&gt; {@link #BEENDET} (Buero setzt "Wieder voll im
 *       Einsatz", {@code ende} wird gesetzt)</li>
 *   <li>{@link #LAUFEND} -&gt; {@link #ABGEBROCHEN}</li>
 *   <li>{@link #BEENDET} -&gt; {@link #LAUFEND} (Korrektur: Rueckkehr war zu
 *       frueh, {@code ende} wird geleert)</li>
 *   <li>{@link #ABGEBROCHEN} ist ein Endzustand -- keine Rueckkehr.</li>
 * </ul>
 * Kein {@code GEPLANT}, kein {@code PAUSIERT}: es gibt keinen Hintergrundjob,
 * der solche Zwischenzustaende jemals aufloesen wuerde.
 */
public enum LangzeitkrankmeldungStatus {
    LAUFEND,
    BEENDET,
    ABGEBROCHEN
}
