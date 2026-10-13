import { InfoAbschnitt, InfoButton } from '../../../ui/info-button';

/**
 * Info-Knöpfe des Reiters Einstellungen → Zeiterfassung: Was passiert im
 * Hintergrund, und was bewirkt eine Einstellung? Jeder steht direkt neben der
 * Überschrift bzw. dem Feld, um das es geht. Texte bewusst kurz und in
 * Handwerker-Sprache.
 */

const HINTERGRUND = 'Was passiert im Hintergrund?';
const WIRKUNG = 'Was bewirkt das?';

export function ResturlaubInfo() {
    return (
        <InfoButton titel="Resturlaub ins neue Jahr">
            <InfoAbschnitt titel={HINTERGRUND}>
                <p>
                    Zum Jahreswechsel rechnet das Programm für jeden Mitarbeiter aus, wie viel Urlaub im alten Jahr
                    übrig ist, und nimmt den Rest ins neue Jahr mit. Urlaub im neuen Jahr zählt zuerst gegen diesen
                    Rest. Was bis zum Verfallstag nicht genommen ist, fällt danach weg.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel={WIRKUNG}>
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Resturlaub, Urlaubsanträge und Kalender rechnen mit dieser Regel. Die Handy-App erinnert zwei Monate vorher an den Verfall.</li>
                    <li>Beim Mitarbeiter können Sie den übernommenen Rest von Hand ändern, z. B. wenn früher auf Papier geplant wurde.</li>
                    <li>Gebuchte Urlaubstage ändern sich nicht. Den ersten Übertrag gibt es ins Jahr nach dem Update.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

export function StartDerZeiterfassungInfo() {
    return (
        <InfoButton titel="Start der Zeiterfassung">
            <InfoAbschnitt titel={HINTERGRUND}>
                <p>
                    Die Glocke („Zeiten prüfen“) sucht für jeden Mitarbeiter alle Monate, die noch nicht
                    abgeschlossen sind – ab Eintritt bzw. erster Buchung. Mit diesem Datum beginnt die Suche
                    frühestens im Monat dieses Tages.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel={WIRKUNG}>
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Monate davor erscheinen nicht mehr als „abschließen“. Der Monat des Einführungstags selbst schon.</li>
                    <li>Gebuchte Zeiten und schon abgeschlossene Monate bleiben, wie sie sind. Frühere Monate können Sie im Monatsabschluss weiter von Hand prüfen.</li>
                    <li>Leer lassen heißt: keine Grenze.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

export function StundenartInfo() {
    return (
        <InfoButton titel="Stunden ans Steuerbüro">
            <InfoAbschnitt titel={HINTERGRUND}>
                <p>
                    Beim DATEV-Export wird der abgeschlossene Monatsstand gelesen. Bei „Soll“ werden vom
                    Monats-Soll laut Arbeitszeitkonto Urlaub, Krankheit, Feiertage usw. abgezogen – der Rest geht
                    als „Arbeit“ in die Datei. Bei „Ist“ die tatsächlich gestempelten Stunden (ohne Pausen).
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel={WIRKUNG}>
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Gilt nur für künftige Exporte, nicht für schon heruntergeladene Dateien.</li>
                    <li>Urlaub, Krankheit und Feiertage gehen mit ihrer eigenen Lohnart raus. Zusammen mit „Arbeit“ ergibt das bei „Soll“ genau das Soll – nichts wird doppelt gezählt. Steht eine Art auf „Nicht exportieren“, fehlen ihre Stunden in der Datei.</li>
                    <li>Mitarbeiter ohne Soll (z. B. Aushilfen) auf „Ist“ stellen – sonst zeigt die Vorprüfung ihre Stunden als „wird nicht exportiert“.</li>
                    <li>Arbeitszeitkonto, Überstunden und Abschlüsse bleiben unverändert.</li>
                    <li>War DATEV schon vorher eingerichtet, steht hier „Ist“ – so hat der Export bisher gerechnet.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

export function DatevEinrichtenInfo() {
    return (
        <InfoButton titel="DATEV einrichten">
            <InfoAbschnitt titel={HINTERGRUND}>
                <p>
                    Es wird nichts automatisch an DATEV geschickt. Im Monatsabschluss erstellt „Für DATEV
                    exportieren“ nach einer Vorprüfung eine LODAS-Textdatei. Die importiert Ihr Steuerbüro.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel={WIRKUNG}>
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Jede Änderung hier macht eine laufende Vorprüfung ungültig – danach im Monatsabschluss neu prüfen.</li>
                    <li>Ändern dürfen nur Admins. Exportieren darf, wer Monate abschließen darf.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

export function NichtExportierenInfo() {
    return (
        <InfoButton titel="Nicht exportieren">
            <InfoAbschnitt titel={WIRKUNG}>
                <p>
                    Diese Stunden kommen nicht in die Datei. In der Vorprüfung tauchen sie als ausgelassen auf
                    und müssen dort bewusst bestätigt werden.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

export function PersonalnummerInfo() {
    return (
        <InfoButton titel="Personalnummer">
            <InfoAbschnitt titel={WIRKUNG}>
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Muss genau mit der Nummer im DATEV-Programm Ihres Steuerbüros übereinstimmen. Führende Nullen bleiben erhalten.</li>
                    <li>Ohne Personalnummer kann der Mitarbeiter nicht exportiert werden und bekommt keine eigene Soll/Ist-Auswahl.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}
