import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { ChevronDown, Headset, Lightbulb, Phone, RotateCcw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { SettingsCard } from '../../components/settings/settingsUi';
import { cn } from '../../lib/utils';
import { TelefonAuswahlDialog } from './TelefonAuswahlDialog';
import { useWaehlTelefon } from './useWaehlTelefon';

/**
 * Einstellung „Telefon an diesem Rechner": zeigt das gespeicherte Telefon,
 * lässt es ändern (gleiche Auswahl wie beim ersten Zurückrufen) oder
 * zurücksetzen. Darunter eine aufklappbare Anleitung in Alltagssprache, wie
 * man am PC mit Headset telefoniert.
 *
 * <p>Gilt nur für diesen Rechner – gespeichert im Browser, nicht auf dem Server.</p>
 */

const ANLEITUNG: ReactNode[] = [
    <>
        Telefon-Programm installieren, zum Beispiel <strong className="text-slate-900">MicroSIP</strong> (Windows)
        oder <strong className="text-slate-900">Zoiper</strong> (Mac und Windows), und das Headset anschließen.
    </>,
    <>
        In der FRITZ!Box unter <strong className="text-slate-900">Telefonie → Telefoniegeräte</strong> auf
        „Neues Gerät einrichten“ klicken, dann „Telefon (mit und ohne Anrufbeantworter)“ und „LAN/WLAN (IP-Telefon)“
        wählen. Benutzername und Kennwort des neuen Geräts ins Telefon-Programm eintragen, als Server{' '}
        <code className="rounded bg-slate-100 px-1 font-mono text-xs">fritz.box</code>.
    </>,
    <>
        Beim neuen Gerät als ausgehende Rufnummer die Firmennummer wählen und bei „reagiert auf“ die Firmennummer anhaken.
    </>,
    <>
        In der FRITZ!Box die Wählhilfe einschalten: <strong className="text-slate-900">Telefonie → Anrufe → Wählhilfe</strong>.
    </>,
    <>
        Hier oben bei „Telefon an diesem Rechner“ das neue Telefon auswählen.
    </>,
    <>
        Abnehmen: mit der Taste am Headset oder im Fenster des Telefon-Programms. Direkt im Programm hier abnehmen geht nicht.
    </>,
];

export function WaehlTelefonEinstellung() {
    const { telefon, setzeTelefon, vergiss } = useWaehlTelefon();
    const [auswahlOffen, setAuswahlOffen] = useState(false);
    const [anleitungOffen, setAnleitungOffen] = useState(false);
    const anleitungId = useId();

    return (
        <SettingsCard
            icon={<Headset aria-hidden="true" className="h-5 w-5 text-rose-600" />}
            title="Telefon an diesem Rechner"
            description={(
                <p>
                    Bei „Zurückrufen“ klingelt zuerst dieses Telefon. Nehmen Sie ab, wählt die FRITZ!Box die Nummer.
                    Die Auswahl gilt nur für diesen Rechner.
                </p>
            )}
        >
            <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center">
                <div className="flex min-w-0 flex-1 items-center gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-rose-600 shadow-sm">
                        <Phone aria-hidden="true" className="h-5 w-5" />
                    </span>
                    {telefon ? (
                        <span className="min-w-0">
                            <span className="block text-xs text-slate-500">Aktuelles Telefon</span>
                            <span className="block break-words font-semibold text-slate-900">{telefon}</span>
                        </span>
                    ) : (
                        <span className="text-sm text-slate-600">
                            Noch kein Telefon ausgewählt – beim ersten „Zurückrufen“ wird gefragt.
                        </span>
                    )}
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={() => setAuswahlOffen(true)}>
                        <Phone aria-hidden="true" className="h-4 w-4" />
                        {telefon ? 'Ändern' : 'Telefon auswählen'}
                    </Button>
                    {telefon && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={vergiss}
                            title="Auswahl vergessen – beim nächsten „Zurückrufen“ wird wieder gefragt"
                        >
                            <RotateCcw aria-hidden="true" className="h-4 w-4" />
                            Zurücksetzen
                        </Button>
                    )}
                </div>
            </div>

            <div className="mt-4">
                <button
                    type="button"
                    aria-expanded={anleitungOffen}
                    aria-controls={anleitungId}
                    onClick={() => setAnleitungOffen((o) => !o)}
                    className="flex items-center gap-2 rounded-lg px-1 py-1 text-sm font-medium text-rose-700 transition-colors hover:text-rose-800 focus:outline-none focus:ring-2 focus:ring-rose-500"
                >
                    <ChevronDown aria-hidden="true" className={cn('h-4 w-4 motion-safe:transition-transform', anleitungOffen && 'rotate-180')} />
                    So telefonieren Sie am PC mit Headset
                </button>
                {anleitungOffen && (
                    <div id={anleitungId} className="mt-3 space-y-4">
                        <ol aria-label="Anleitung: am PC mit Headset telefonieren" className="space-y-3 text-sm text-slate-600">
                            {ANLEITUNG.map((schritt, i) => (
                                <li key={i} className="flex gap-3">
                                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-xs font-bold text-rose-700">{i + 1}</span>
                                    <span>{schritt}</span>
                                </li>
                            ))}
                        </ol>
                        <p className="flex gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                            <Lightbulb aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
                            <span>
                                Tipp: In der FRITZ!Box unter <strong className="text-slate-900">Telefonie → Rufbehandlung → Rufsperren</strong>{' '}
                                teure Sondernummern sperren (z. B. 0900, 0137, 118…).
                            </span>
                        </p>
                    </div>
                )}
            </div>

            <TelefonAuswahlDialog
                offen={auswahlOffen}
                aktuell={telefon}
                bestaetigenText="Übernehmen"
                onWaehlen={(name) => {
                    setzeTelefon(name);
                    setAuswahlOffen(false);
                }}
                onSchliessen={() => setAuswahlOffen(false)}
            />
        </SettingsCard>
    );
}
