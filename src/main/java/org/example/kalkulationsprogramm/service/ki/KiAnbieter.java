package org.example.kalkulationsprogramm.service.ki;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ein angebundener KI-Anbieter. Ein neuer Anbieter braucht genau eine neue
 * Umsetzung dieser Schnittstelle (als Spring-Bean) und einen Wert in
 * {@link KiAnbieterTyp}; Einstellungen, Schlüsselablage, Modellwahl und die
 * Zuordnung der Funktionen übernimmt {@link KiDienst}.
 */
public interface KiAnbieter {

    KiAnbieterTyp typ();

    /** Name für die Oberfläche, z. B. „Mistral“. */
    String name();

    /** Ein Satz für die Oberfläche: wer, und wo die Daten verarbeitet werden. */
    String beschreibung();

    KiDatenstandort datenstandort();

    /** Was diese Umsetzung kann – nur, was hier wirklich gebaut ist. */
    Set<KiFaehigkeit> faehigkeiten();

    /**
     * Wird der Schlüssel verschlüsselt abgelegt? Für neue Anbieter immer true
     * (CLAUDE.md: keine neuen Klartext-Geheimnisse in {@code system_setting}).
     */
    boolean schluesselVerschluesselt();

    /** Modell je Stufe, wenn der Betrieb nichts anderes eingestellt hat. */
    Map<KiModellStufe, String> standardModelle();

    /**
     * Ein eigenes Modell für Funktionen mit diesen Fähigkeiten, statt des Modells der Stufe –
     * etwa ein Sprachmodell, wenn das Chat-Modell keinen Ton versteht. Ein Modell, das der
     * Betrieb für die Funktion selbst gewählt hat, geht vor ({@link KiDienst}).
     */
    default Optional<String> modellFuer(Set<KiFaehigkeit> gebraucht) {
        return Optional.empty();
    }

    /**
     * Schickt die Anfrage und liefert die Antwort.
     *
     * @throws KiAnbieterFehler wenn der Anbieter nicht oder mit einem Fehler antwortet
     */
    KiErgebnis sende(KiAnfrage anfrage, String modell, String schluessel);

    /**
     * Die Chat-Modelle, die der Anbieter für diesen Schlüssel anbietet.
     *
     * @throws KiAnbieterFehler wenn die Liste nicht geladen werden kann
     */
    List<KiModellInfo> ladeModelle(String schluessel);

    /**
     * Bekannte Grenze dieses Anbieters bei einer Funktion, die die Einstellungsseite
     * dazuschreibt, auch wenn die Funktion läuft (z. B. nicht lesbare Aufnahmeformate).
     */
    default Optional<String> einschraenkung(KiFunktion funktion) {
        return Optional.empty();
    }

    /** Prüft Schlüssel und Modellnamen, ohne Inhalte zu schicken. */
    default TestResult teste(String schluessel, Collection<String> modelle) {
        if (schluessel == null || schluessel.isBlank()) {
            return TestResult.failure("Kein Schlüssel angegeben.");
        }
        List<KiModellInfo> bekannt;
        try {
            bekannt = ladeModelle(schluessel.strip());
        } catch (KiAnbieterFehler e) {
            return TestResult.failure(e.getMessage());
        }
        return KiModellPruefung.ergebnis(name(), KiModellPruefung.namen(bekannt), modelle);
    }
}
