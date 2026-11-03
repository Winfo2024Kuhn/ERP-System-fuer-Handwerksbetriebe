import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

/** Info neben dem Seitentitel „Firmendokumente“. */
export function SeitenInfo() {
    return (
        <InfoButton titel="Firmendokumente">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Die Dateien liegen auf dem ERP-Server und werden mit der nächtlichen Sicherung mitgesichert. Die Ordner gibt es nur hier im Programm.</p>
                <p>Du kannst jede Art von Datei ablegen: PDFs, Fotos, Word- und Excel-Dateien, Zeichnungen. PDFs und Bilder kannst du direkt ansehen, andere Dateien werden heruntergeladen. Programmdateien wie .exe oder .bat sind aus Sicherheitsgründen gesperrt.</p>
                <p>Du kannst Dateien aus dem Explorer direkt auf einen Ordner ziehen. Ein Dokument aus der Liste ziehst du auf einen anderen Ordner, um es zu verschieben.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Wer sich am PC anmeldet, sieht die Firmendokumente und kann sie drucken, hochladen, umbenennen, verschieben und löschen – außer ein Admin hat einen Ordner auf bestimmte Abteilungen beschränkt. Solche Ordner haben im Baum ein Schloss. In der Zeiterfassungs-App auf dem Handy erscheinen die Dokumente nicht.</p>
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
                <p>Die Einstellung gilt für diesen Ordner und alle Unterordner darin, samt Dokumenten. Wer keinen Zugriff hat, sieht den Ordner gar nicht – auch nicht in der Suche und bei „Bald fällig“.</p>
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
                <p>Die Abteilungs-Einstellungen bleiben gespeichert, auch wenn du auf „Alle Mitarbeiter“ zurückstellst.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Bei „Alle Mitarbeiter“ darf jeder angemeldete Mitarbeiter den Ordner sehen und ändern – so wie bisher. Abteilungen pflegst du unter Einstellungen → Abteilungen &amp; Rechte.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben der Überschrift beim Verschieben eines Ordners. */
export function OrdnerVerschiebenInfo() {
    return (
        <InfoButton titel="Ordner verschieben">
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Ein Ordner übernimmt die Rechte des obersten Ordners, in den er verschoben wird. Holst du ihn ganz nach oben, behält er die Rechte, die er bisher hatte. Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben – sonst könnten Dokumente versehentlich für alle sichtbar werden.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
