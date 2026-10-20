import { InfoAbschnitt, InfoButton } from './ui/info-button';

interface RechnungstypInfoProps {
    /**
     * Absatz zur einfachen Rechnung zeigen. Nur dort, wo sie auch zur Auswahl
     * steht – sonst erklärte das (i) einen Knopf, den es gar nicht gibt.
     */
    mitEinfacherRechnung: boolean;
}

/**
 * (i) neben der Rechnungstyp-Auswahl: erklärt die Rechnungsarten und wie sie
 * mit dem Angebot bzw. der Auftragsbestätigung verrechnet werden.
 *
 * Geteilt zwischen dem Rechnungs-Dialog im Projekt und dem in der
 * Dokumentenübersicht, damit beide Stellen dasselbe erklären.
 */
export function RechnungstypInfo({ mitEinfacherRechnung }: RechnungstypInfoProps) {
    return (
        <InfoButton titel="Welche Rechnung passt?">
            {mitEinfacherRechnung && (
                <InfoAbschnitt titel="Einfache Rechnung">
                    <p>Rechnet alles auf einmal ab und gilt als Schlussrechnung: Gelöschtes (z. B. Rüstkosten) steht als „Nicht angefallen“ darauf, danach kommt keine Rechnung mehr.</p>
                </InfoAbschnitt>
            )}
            <InfoAbschnitt titel="Teilrechnung">
                <p>Rechnet nur fertige Positionen ab; der Rest folgt später.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Abschlagsrechnung">
                <p>Ein Betrag oder Prozentsatz vorab, z. B. für Material.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Schlussrechnung">
                <p>Schließt den Auftrag nach Teil- oder Abschlagsrechnungen ab und zieht Berechnetes ab.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
