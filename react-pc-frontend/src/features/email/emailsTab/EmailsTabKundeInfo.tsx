import { InfoAbschnitt, InfoButton } from '../../../components/ui/info-button';

/** Erklärung im E-Mail-Reiter des Kunden: woher die E-Mails kommen und was hier fehlt. */
export function EmailsTabKundeInfo() {
    return (
        <InfoButton titel="E-Mails des Kunden">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Hier stehen die E-Mails aus allen Projekten und Anfragen dieses Kunden. Antworten sind
                    zu einem Verlauf zusammengefasst, der Verlauf mit der neuesten Nachricht steht oben.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    E-Mails, die noch keinem Projekt und keiner Anfrage zugeordnet sind, stehen hier nicht.
                    Die finden Sie im E-Mail-Center. Neue E-Mails und Antworten schreiben Sie im Projekt
                    oder in der Anfrage.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
