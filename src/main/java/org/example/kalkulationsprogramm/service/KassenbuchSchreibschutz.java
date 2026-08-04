package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Die eine Stelle, an der geprüft wird, ob eine Kassenbuch-Buchung überhaupt
 * noch geschrieben werden darf.
 *
 * <p>Vorher lag diese Prüfung nur in {@code BelegService.updateBeleg}. Das
 * reichte nicht: Es gibt mehrere Wege, auf denen ein Beleg entsteht oder sich
 * ändert — die Kassen-Schnellbuchungen (Bank-Abhebung, Privateinlage,
 * Privatentnahme, Lohn), die Positions-Auswahl vom Handy, der
 * Kostenstellen-Dialog. Jeder dieser Wege, der die Prüfung nicht kennt, ist
 * ein Loch in der Festschreibung. Und ein Loch, von dem der Prüfer nichts
 * weiß, ist schlimmer als gar keine Festschreibung: Die
 * Verfahrensdokumentation behauptet dann eine Unveränderbarkeit, die es nicht
 * gibt.</p>
 *
 * <p>Deshalb liegt die Prüfung hier zentral, und alle schreibenden Dienste
 * rufen sie auf. Wer künftig einen neuen Schreibpfad auf {@code beleg}
 * baut, muss hier vorbei.</p>
 */
@Component
@RequiredArgsConstructor
public class KassenbuchSchreibschutz {

    private final KassenbuchMonatsabschlussRepository abschlussRepository;

    /**
     * Wirft, wenn der Monat dieses Belegdatums bereits abgeschlossen ist.
     *
     * <p>Ein abgeschlossener Monat hat einen festgehaltenen Endbestand und
     * eine feste Nummernfolge. Eine nachträglich eingeschobene Buchung würde
     * beides ungültig machen, ohne dass es irgendwo auffiele.</p>
     *
     * @param datum Belegdatum; {@code null} passiert nichts — dass ein Datum
     *              fehlt, fangen die Aufrufer selbst ab
     */
    @Transactional(readOnly = true)
    public void assertMonatOffen(LocalDate datum) {
        if (datum == null) return;
        if (abschlussRepository.existsByJahrAndMonat(datum.getYear(), datum.getMonthValue())) {
            throw new KassenbuchGesperrtException(
                    "Der Monat dieses Datums ist bereits abgeschlossen.",
                    "Buche stattdessen im laufenden Monat – abgeschlossene Monate bleiben unverändert.");
        }
    }

    /**
     * Wirft, wenn der Beleg festgeschrieben ist.
     *
     * <p>Für Schreibpfade, die keine feinere Unterscheidung treffen können als
     * "darf ich diesen Beleg überhaupt anfassen". Der Prüf-Dialog geht einen
     * genaueren Weg und erlaubt die Kontierung weiterhin — siehe
     * {@code BelegService.assertNurKontierungGeaendert}.</p>
     *
     * @param was Klartext dessen, was geändert werden sollte; steht in der
     *            Meldung, die der Nutzer zu sehen bekommt
     */
    public void assertNichtFestgeschrieben(Beleg beleg, String was) {
        if (beleg != null && beleg.istFestgeschrieben()) {
            throw new KassenbuchGesperrtException(
                    "Dieser Beleg ist festgeschrieben – " + was + " kann nicht mehr geändert werden.",
                    "Storniere den Beleg und buche ihn richtig neu. "
                            + "Die alte Buchung bleibt dabei sichtbar stehen – so verlangt es das Finanzamt.");
        }
    }
}
