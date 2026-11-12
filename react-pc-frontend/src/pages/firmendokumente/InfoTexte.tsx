import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

/** Info neben dem Seitentitel „Firmendokumente“. */
export function SeitenInfo() {
    return (
        <InfoButton titel="Firmendokumente">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Die Dateien liegen auf dem ERP-Server und werden mit der nächtlichen Sicherung mitgesichert. Die Ordner gibt es nur hier im Programm.</p>
                <p>Abgelegt werden kann jede Art von Datei: PDFs, Fotos, Word- und Excel-Dateien, Zeichnungen. PDFs und Bilder öffnen sich direkt in der Vorschau, andere Dateien werden heruntergeladen. Programmdateien wie .exe oder .bat sind aus Sicherheitsgründen gesperrt.</p>
                <p>Dateien lassen sich aus dem Explorer direkt auf einen Ordner ziehen, auch mehrere auf einmal. Ein Dokument (Zeile oder Kachel) auf einen anderen Ordner ziehen verschiebt es.</p>
                <p>Ordner verschieben: Einen Ordner auf einen anderen ziehen macht ihn zu dessen Unterordner, auf die Überschrift „Ordner“ gezogen wird er zum Hauptordner. Dokumente und Unterordner ziehen mit um. Kommt ein Hauptordner in einen anderen Ordner, gilt dort, wer den neuen Hauptordner sehen darf. Wird ein Unterordner zum Hauptordner, behält er die Sichtbarkeit seines bisherigen Hauptordners. Kommt ein Hauptordner mit eingeschränkter Sichtbarkeit in einen anderen Ordner, geht seine eigene Einstellung „Wer darf das sehen?“ verloren. Aus einem Ordner, der nur für bestimmte Abteilungen sichtbar ist, darf nur ein Admin in einen anderen Hauptordner verschieben.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Wer sich am PC anmeldet, sieht die Firmendokumente und kann sie drucken, hochladen, umbenennen, verschieben und löschen, außer ein Admin hat einen Ordner auf bestimmte Abteilungen beschränkt. Solche Ordner haben im Baum ein Schloss. In der Zeiterfassungs-App auf dem Handy erscheinen die Dokumente nicht.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben dem Feld „Fällig am“. */
export function FaelligAmInfo() {
    return (
        <InfoButton titel="Fällig am">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Das Datum wird nur am Dokument gespeichert. Es geht keine Erinnerung per Glocke oder E-Mail raus.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Ab 30 Tagen vor dem Datum ist das Dokument gelb markiert, danach rot. Ordner mit überfälligen Dokumenten bekommen im Baum einen roten Punkt, und die Übersicht listet alle fälligen Dokumente.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben der Überschrift im Dialog „Wer darf … sehen?“. */
export function RechteInfo() {
    return (
        <InfoButton titel="Wer darf das sehen?">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Die Einstellung gilt für diesen Ordner und alle Unterordner darin, samt Dokumenten. Wer keinen Zugriff hat, sieht den Ordner gar nicht, auch nicht in der Suche und bei „Bald fällig“.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>„Ansehen“ heißt öffnen, herunterladen und drucken. „Ändern“ heißt zusätzlich hochladen, umbenennen, verschieben und löschen. Admins sehen und ändern immer alles. Ist jemand in mehreren Abteilungen, gilt das höhere Recht. Wer in keiner Abteilung ist, sieht eingeschränkte Ordner nicht.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben der Auswahl „Alle Mitarbeiter / Nur bestimmte Abteilungen“. */
export function RechteAuswahlInfo() {
    return (
        <InfoButton titel="Alle oder nur bestimmte Abteilungen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Die Abteilungs-Einstellungen bleiben gespeichert, auch wenn der Ordner wieder auf „Alle Mitarbeiter“ steht.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Bei „Alle Mitarbeiter“ darf jeder angemeldete Mitarbeiter den Ordner sehen und ändern, so wie bisher. Abteilungen werden unter Einstellungen → Abteilungen &amp; Rechte gepflegt.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben der Überschrift beim Verschieben eines Ordners. */
export function OrdnerVerschiebenInfo() {
    return (
        <InfoButton titel="Ordner verschieben">
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Ein Ordner übernimmt die Rechte des obersten Ordners, in den er verschoben wird. Kommt er ganz nach oben, behält er die Rechte, die er bisher hatte. Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben, sonst könnten Dokumente versehentlich für alle sichtbar werden.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben dem Umschalter „Liste | Vorschau“. */
export function AnsichtInfo() {
    return (
        <InfoButton titel="Liste oder Vorschau">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Für Fotos und PDFs erstellt der Server beim ersten Anzeigen ein kleines Vorschaubild, bei PDFs von der ersten Seite. Die Originaldatei bleibt unverändert.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Auch Ordner mit vielen Fotos öffnen schnell. Word-, Excel- und andere Dateien zeigen nur ihr Symbol. Ein Klick auf eine Kachel öffnet die Datei in voller Größe. Ansicht und Größe merkt sich dieser Browser.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben dem Knopf „Datei hochladen“. */
export function HochladenInfo() {
    return (
        <InfoButton titel="Mehrere Dateien hochladen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Werden mehrere Dateien auf einmal gewählt oder gezogen, legt das Programm alle ohne Nachfrage in diesem Ordner ab. Als Name nimmt es den Dateinamen, ein „Fällig am“ wird nicht gesetzt.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Viele Fotos sind so in einem Schritt abgelegt. Name und Fällig am lassen sich danach je Dokument über „Bearbeiten …“ ändern. Bei einer einzelnen Datei fragt das Programm wie gewohnt nach Name und Fällig am.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
