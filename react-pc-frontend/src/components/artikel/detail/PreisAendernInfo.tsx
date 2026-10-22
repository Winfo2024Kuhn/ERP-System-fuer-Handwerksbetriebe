import { InfoButton } from "../../ui/info-button";

/** Erklärt, was beim Ändern eines Lieferantenpreises passiert - steht an jeder Preistabelle. */
export function PreisAendernInfo() {
    return (
        <InfoButton titel="Preis ändern">
            <p className="font-semibold text-slate-900">Was passiert im Hintergrund?</p>
            <p>
                Der neue Preis gilt ab sofort für diesen Lieferanten. Der alte Preis wird nicht gelöscht,
                sondern bleibt im Preisverlauf stehen.
            </p>
            <p className="mt-2 font-semibold text-slate-900">Was bewirkt das?</p>
            <p>
                Neue Angebotspositionen rechnen mit dem günstigsten aktuellen Preis plus Aufschlag.
                Angebote und Rechnungen, die schon geschrieben sind, ändern sich nicht.
            </p>
        </InfoButton>
    );
}
