import { useState } from 'react';
import { Headset } from 'lucide-react';
import { TelefonAuswahlDialog } from './TelefonAuswahlDialog';
import { useTelefonBerechtigung } from './useTelefonBerechtigung';
import { useWaehlTelefon } from './useWaehlTelefon';

/**
 * Dezente Zeile „Telefon an diesem Rechner: … · Ändern" für die Telefon-Seite.
 *
 * <p>Damit auch Benutzer ohne Administrator-Rechte (die die Einstellungen
 * nicht sehen) ihr Telefon für „Zurückrufen" wechseln können. Öffnet dieselbe
 * Auswahl wie beim ersten Zurückrufen. Ohne Telefon-Recht unsichtbar.</p>
 */
export function WaehlTelefonZeile() {
    const darf = useTelefonBerechtigung();
    const { telefon, setzeTelefon } = useWaehlTelefon();
    const [offen, setOffen] = useState(false);

    if (darf !== true) return null;

    return (
        <>
            <p className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-sm text-slate-500">
                <Headset aria-hidden="true" className="h-4 w-4 shrink-0" />
                {/* Echte Leerzeichen für Vorlesen und Kopieren; den Abstand macht gap. */}
                Telefon an diesem Rechner:{' '}
                {telefon
                    ? <span className="break-words font-medium text-slate-700">{telefon}</span>
                    : <span className="italic">noch nicht gewählt</span>}{' '}
                <span aria-hidden="true">·</span>{' '}
                <button
                    type="button"
                    onClick={() => setOffen(true)}
                    aria-label={telefon ? 'Telefon an diesem Rechner ändern' : 'Telefon an diesem Rechner auswählen'}
                    className="rounded font-medium text-rose-700 underline-offset-2 transition-colors hover:text-rose-800 hover:underline focus:outline-none focus:ring-2 focus:ring-rose-500"
                >
                    {telefon ? 'Ändern' : 'Auswählen'}
                </button>
            </p>
            <TelefonAuswahlDialog
                offen={offen}
                aktuell={telefon}
                bestaetigenText="Übernehmen"
                onWaehlen={(name) => {
                    setzeTelefon(name);
                    setOffen(false);
                }}
                onSchliessen={() => setOffen(false)}
            />
        </>
    );
}
