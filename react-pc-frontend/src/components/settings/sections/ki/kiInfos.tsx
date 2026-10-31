import { InfoAbschnitt, InfoButton } from '../../../ui/info-button';

/**
 * Alle Info-Knöpfe der KI-Einstellungen an einer Stelle. Die Texte müssen zum
 * tatsächlichen Verhalten des Servers passen – wer dort etwas ändert, findet
 * hier alle Erklärungen, die mitwandern müssen.
 */

/** Neben dem Statuskasten oben: Was heißt „KI-Funktionen an/aus“? */
export function KiFunktionenInfo() {
    return (
        <InfoButton titel="KI-Funktionen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Mit Schlüssel schickt das Programm Belege, Texte und Sprachaufnahmen zum Lesen und für
                    Vorschläge an den gewählten KI-Anbieter. Ohne Schlüssel verlässt nichts davon den Betrieb.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Mit Schlüssel stehen die KI-Funktionen allen Benutzern zur Verfügung – am PC und in der Handy-App.</li>
                    <li>Ohne Schlüssel sind KI-Knöpfe gesperrt oder ausgeblendet. Angebote, Rechnungen, Zeiterfassung und E-Mails funktionieren unverändert.</li>
                    <li>Schon ausgelesene Belege und gespeicherte Texte bleiben erhalten.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Neben der Überschrift „KI-Anbieter“. */
export function KiAnbieterInfo() {
    return (
        <InfoButton titel="KI-Anbieter">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Wenn eine KI-Funktion läuft – zum Beispiel eine Rechnung auslesen –, schickt das Programm genau
                    diesen Inhalt (das Dokument, das Bild oder den Text) an den gewählten Anbieter. Der wertet ihn aus
                    und schickt das Ergebnis zurück. Gespeichert wird das Ergebnis nur in Ihrem Programm.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Der Wechsel gilt sofort für alle neuen Anfragen, ohne Neustart. Schon ausgelesene Dokumente
                    bleiben, wie sie sind. Mistral verarbeitet die Daten nach eigener Angabe in der EU, Google Gemini
                    auch außerhalb der EU.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Neben dem Schalter „Ersatz-Anbieter erlauben“. */
export function KiErsatzInfo() {
    return (
        <InfoButton titel="Ersatz-Anbieter">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Nicht jeder Anbieter kann alles – Mistral kann in diesem Programm zum Beispiel noch keine
                    Spracheingabe. Ist der Schalter an, springt für solche Funktionen ein anderer Anbieter ein, für den
                    ein Schlüssel hinterlegt ist.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    An: Alle Funktionen laufen, aber die betroffenen Inhalte gehen an den Ersatz-Anbieter – welche,
                    sehen Sie in der Liste „Welche Funktion nutzt was?“. Aus: Es geht nichts an andere Anbieter; diese
                    Funktionen sind dann ausgeschaltet.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Neben „Modelle“ in jeder Anbieter-Karte. */
export function KiModelleInfo({ anbieterName }: { anbieterName: string }) {
    return (
        <InfoButton titel={`Modelle von ${anbieterName}`}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Jeder Anbieter hat mehrere KI-Modelle. „Schnell“ nutzt das Programm für kurze Aufgaben wie das
                    Zuordnen von E-Mails, „Standard“ für Dokumente und Texte, „Gründlich“ für einen zweiten Versuch,
                    wenn ein Dokument schwer lesbar ist.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Die Änderung gilt sofort. Ein leeres Feld nimmt das Standard-Modell des Programms. Ein falsch
                    geschriebener Name lässt die KI-Funktionen scheitern – prüfen Sie nach dem Ändern mit „Verbindung
                    testen“.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Neben dem Schlüssel-Feld eines Anbieters, dessen Schlüssel verschlüsselt gespeichert wird. */
export function KiSchluesselInfo({ anbieterName }: { anbieterName: string }) {
    return (
        <InfoButton titel={`Schlüssel für ${anbieterName}`}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Der Schlüssel wird verschlüsselt gespeichert und danach nie wieder angezeigt.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Ohne Schlüssel kann dieser Anbieter nicht genutzt werden. Entfernen schaltet ihn sofort ab; die
                    Funktionen springen dann auf einen Ersatz um oder sind aus.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Neben der Überschrift „Welche Funktion nutzt was?“. */
export function KiFunktionenListeInfo() {
    return (
        <InfoButton titel="Welche Funktion nutzt was?">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Die Liste zeigt für jede KI-Funktion, welcher Anbieter und welches Modell sie in diesem Moment
                    nutzt.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    „Läuft“: über Ihren gewählten Anbieter. „Ersatz“: Ihr Anbieter kann das noch nicht, ein anderer
                    springt ein. „Aus“: Kein eingerichteter Anbieter kann es – der Hinweis daneben sagt, was fehlt.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Im Bereich „Ändern“ einer Funktion: Anbieter und Modell je Funktion. */
export function KiFunktionWahlInfo() {
    return (
        <InfoButton titel="Anbieter je Funktion">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Sie können für jede Funktion festlegen, welcher Anbieter und welches Modell sie übernimmt – zum
                    Beispiel ein besonders gründliches Modell für Rechnungen ohne maschinenlesbare Daten.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Gilt sofort für neue Anfragen. „Standard“ heißt: die Funktion folgt dem oben gewählten Anbieter.
                    Kann der gewählte Anbieter die Funktion nicht oder fehlt sein Schlüssel, springt – wenn erlaubt –
                    ein Ersatz ein; das steht dann in der Liste.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
