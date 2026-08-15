package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;

import java.util.Date;
import java.util.List;

/**
 * Meldung, dass aus einem analysierten Lieferantendokument Preise uebernommen
 * werden sollen.
 *
 * <p>Bewusst eine eigene Datei und keine verschachtelte Klasse im
 * {@link PreisUebernahmeService}: Das Event ist der Vertrag zwischen der
 * Dokumentanalyse und allen, die daran haengen. Wer spaeter mithoeren will - eine
 * Benachrichtigung, ein Protokoll -, soll dafuer nicht ausgerechnet den Dienst
 * importieren muessen, der es heute zufaellig als einziger verarbeitet.
 *
 * <p><b>Der Lieferant muss beim Veroeffentlichen geladen sein.</b> Der Empfaenger
 * arbeitet nach dem Commit in einer neuen Transaktion; ein noch nicht
 * aufgeloester Lazy-Proxy waere dort abgehaengt und beim ersten Zugriff auf den
 * Namen nicht mehr nachladbar.
 *
 * @param lieferant     Absender des Dokuments; ohne ihn ist keine Zuordnung moeglich
 * @param quelle        Herkunft des Preises, macht ihn in der Historie nachvollziehbar
 * @param dokumentDatum fachliches Datum des Belegs, {@code null} = unbekannt
 * @param belegnummer   Nummer des Belegs, aus dem die Preise stammen; {@code null},
 *                      wenn das Dokument keine ausweist
 * @param positionen    die ausgelesenen Rechnungspositionen
 */
public record PreisUebernahmeEvent(Lieferanten lieferant, PreisQuelle quelle, Date dokumentDatum,
                                   String belegnummer, List<PreisUebernahmeService.Position> positionen) {
}
