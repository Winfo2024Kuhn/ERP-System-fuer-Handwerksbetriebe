import { AlertCircle, CheckCircle2, Users } from 'lucide-react';
import { cn } from '../../lib/utils';
import {
    EINZELVERSAND_HINWEIS_AB, EINZELVERSAND_MAX_EMPFAENGER, EINZELVERSAND_ZU_VIELE,
    type EinzelversandErgebnis,
} from './einzelversand';

interface EinzelversandSchalterProps {
    id: string;
    aktiv: boolean;
    onChange: (aktiv: boolean) => void;
    /** Anzahl der Empfänger im „An“. */
    anzahlEmpfaenger: number;
    disabled?: boolean;
    className?: string;
}

/**
 * Schalter „Einzeln verschicken“: Jeder Empfänger bekommt eine eigene Mail und
 * sieht nur sich selbst. Ab {@link EINZELVERSAND_HINWEIS_AB} Empfängern weist der
 * Schalter von sich aus darauf hin; über {@link EINZELVERSAND_MAX_EMPFAENGER}
 * sagt er, warum nicht gesendet werden kann.
 */
export function EinzelversandSchalter({ id, aktiv, onChange, anzahlEmpfaenger, disabled, className }: EinzelversandSchalterProps) {
    const zuViele = aktiv && anzahlEmpfaenger > EINZELVERSAND_MAX_EMPFAENGER;
    const hinweisId = `${id}-hinweis`;
    let hinweis: { text: string; warnung: boolean } | null = null;
    if (zuViele) {
        hinweis = { text: `${EINZELVERSAND_ZU_VIELE} Eingetragen sind ${anzahlEmpfaenger}.`, warnung: true };
    } else if (aktiv) {
        hinweis = {
            text: anzahlEmpfaenger > 0
                ? `${anzahlEmpfaenger} ${anzahlEmpfaenger === 1 ? 'Empfänger bekommt' : 'Empfänger bekommen'} je eine eigene E-Mail. Kopie (CC) ist dabei nicht möglich.`
                : 'Jeder Empfänger bekommt eine eigene E-Mail. Kopie (CC) ist dabei nicht möglich.',
            warnung: false,
        };
    } else if (anzahlEmpfaenger >= EINZELVERSAND_HINWEIS_AB) {
        hinweis = {
            text: `${anzahlEmpfaenger} Empfänger sehen sich gegenseitig. Für Rundschreiben besser einzeln verschicken.`,
            warnung: false,
        };
    }

    return (
        <div className={cn('flex flex-col gap-1', className)}>
            <label htmlFor={id} className="inline-flex w-fit cursor-pointer items-center gap-2 text-sm text-slate-700 select-none">
                <input
                    id={id}
                    type="checkbox"
                    checked={aktiv}
                    disabled={disabled}
                    onChange={(e) => onChange(e.target.checked)}
                    aria-describedby={hinweis ? hinweisId : undefined}
                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                />
                <Users className="h-4 w-4 text-slate-400" aria-hidden="true" />
                Einzeln verschicken – jeder sieht nur sich selbst
            </label>
            {hinweis && (
                <p id={hinweisId} className={cn('text-xs', hinweis.warnung ? 'font-medium text-rose-700' : 'text-slate-500')}>
                    {hinweis.text}
                </p>
            )}
        </div>
    );
}

/**
 * Ergebnis eines Einzelversands: wie viele rausgingen und welche Adressen
 * nicht – jeweils mit Grund, damit der Anwender sie korrigieren kann.
 */
export function EinzelversandErgebnisAnzeige({ ergebnis, className }: { ergebnis: EinzelversandErgebnis; className?: string }) {
    const alleOk = ergebnis.fehlgeschlagen.length === 0;
    const nichtAbgelegt = ergebnis.nichtGespeichert ?? [];
    return (
        <div role={alleOk ? 'status' : 'alert'} data-testid="einzelversand-ergebnis"
            className={cn('rounded-xl border p-4 text-sm', alleOk ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800', className)}>
            <p className="flex items-center gap-2 font-semibold">
                {alleOk
                    ? <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
                    : <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />}
                {ergebnis.verschickt} verschickt
                {!alleOk && `, ${ergebnis.fehlgeschlagen.length} nicht`}
            </p>
            {!alleOk && (
                <ul className="mt-2 space-y-1 pl-6">
                    {ergebnis.fehlgeschlagen.map(fehler => (
                        <li key={fehler.adresse} className="break-all">
                            <span className="font-medium">{fehler.adresse}</span>
                            {fehler.grund && <span className="text-rose-700"> – {fehler.grund}</span>}
                        </li>
                    ))}
                </ul>
            )}
            {nichtAbgelegt.length > 0 && (
                <div data-testid="einzelversand-nicht-abgelegt" className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-900">
                    <p className="font-semibold">Verschickt, aber im System nicht abgelegt – bitte NICHT erneut senden:</p>
                    <ul className="mt-1 space-y-1 pl-4">
                        {nichtAbgelegt.map(adresse => (
                            <li key={adresse} className="break-all">{adresse}</li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}
