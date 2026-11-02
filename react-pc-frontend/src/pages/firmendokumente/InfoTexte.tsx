import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

/** Info neben dem Seitentitel „Firmendokumente“. */
export function SeitenInfo() {
    return (
        <InfoButton titel="Firmendokumente">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Die PDFs liegen auf dem ERP-Server und werden mit der nächtlichen Sicherung mitgesichert. Die Ordner gibt es nur hier im Programm.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Jeder, der sich am PC anmeldet, sieht alle Firmendokumente und kann sie drucken, hochladen, umbenennen, verschieben und löschen. In der Zeiterfassungs-App auf dem Handy erscheinen sie nicht.</p>
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
