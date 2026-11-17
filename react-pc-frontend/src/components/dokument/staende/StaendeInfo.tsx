import { InfoAbschnitt, InfoButton } from '../../ui/info-button';

/** Info neben der Überschrift „Stände“. */
export function StaendeInfoButton() {
    return (
        <InfoButton titel="Stände">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Jedes Mal, wenn Sie das Dokument drucken, als PDF speichern oder per E-Mail verschicken, hält das
                    Programm den Inhalt fest: alle Positionen, Preise, Rabatte und Texte, mit Uhrzeit und Namen. Das
                    automatische Speichern zwischendurch zählt nicht als neuer Stand. Hat sich seit dem letzten Export
                    nichts geändert, entsteht auch kein neuer Stand.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Sie sehen auf einen Blick, was sich zwischen zwei Fassungen geändert hat, die rausgegangen sind.
                    Alte Stände bleiben erhalten, solange das Dokument besteht, und lassen sich nicht mehr verändern.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Info neben „Wiederherstellen“ (Karte im Verlauf und Band der Stand-Ansicht). */
export function WiederherstellenInfoButton({ variante }: { variante?: 'standard' | 'hell' }) {
    return (
        <InfoButton titel="Stand wiederherstellen" variante={variante}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Der Inhalt dieses Stands wird wieder der aktuelle Inhalt: Positionen, Preise, Rabatt, Betreff,
                    Zahlungsziel und Rechnungsadresse. Das Datum des Dokuments bleibt. Gespeicherte, noch nicht
                    exportierte Änderungen sichert das Programm vorher als eigenen Stand.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Sie arbeiten danach mit diesem Inhalt weiter. Ein neuer Stand entsteht erst beim nächsten Drucken
                    oder Verschicken. Gebuchte, angenommene oder stornierte Dokumente lassen sich nicht mehr ändern,
                    dort ist Wiederherstellen gesperrt und Sie können ältere Stände nur ansehen.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
