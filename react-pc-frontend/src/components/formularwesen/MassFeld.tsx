import { useId, useRef, useState } from 'react';
import { DecimalInput } from '../ui/decimal-input';
import { useToast } from '../ui/toast';
import { validateDecimalInput } from '../../lib/numberInput';

interface MassFeldProps {
    label: string;
    /** Angezeigter Wert, bereits umgerechnet (z. B. „45,0“). */
    anzeige: string;
    einheit: string;
    min: number;
    max: number;
    /** Wird erst beim Abschluss (Enter oder Feld verlassen) mit dem geprüften Wert aufgerufen. */
    onUebernehmen: (wert: number) => void;
}

/**
 * Zahlenfeld mit Dezimalkomma, das erst beim Abschluss übernimmt – so entsteht
 * pro Eingabe ein Rückgängig-Schritt, und halbe Eingaben verschieben nichts.
 * Escape verwirft die Eingabe, ein leeres Feld lässt den Wert stehen.
 */
export function MassFeld({ label, anzeige, einheit, min, max, onUebernehmen }: MassFeldProps) {
    const id = useId();
    const toast = useToast();
    // null = niemand tippt gerade: dann zeigt das Feld immer den aktuellen Wert
    // (auch nach Ziehen, Pfeiltasten oder Rückgängig).
    const [eingabe, setEingabe] = useState<string | null>(null);
    const [fehler, setFehler] = useState('');
    const verwerfen = useRef(false);
    const entwurf = eingabe ?? anzeige;

    /** Prüft und übernimmt; gibt die Fehlermeldung zurück oder `null`. */
    const uebernehmen = (): string | null => {
        const ergebnis = validateDecimalInput(entwurf, { label, required: true, min, max });
        if (!ergebnis.valid) return ergebnis.message;
        if (ergebnis.value !== null && ergebnis.value !== Number(anzeige.replace(',', '.'))) onUebernehmen(ergebnis.value);
        return null;
    };

    const abschliessen = () => {
        setEingabe(null);
        setFehler('');
        if (verwerfen.current || !entwurf.trim()) {
            verwerfen.current = false;
            return;
        }
        const meldung = uebernehmen();
        if (meldung) toast.error(meldung);
    };

    return (
        <div>
            <label htmlFor={id} className="mb-1 block text-[11px] font-medium text-slate-500">{label}</label>
            <div className="relative">
                <DecimalInput
                    id={id}
                    value={entwurf}
                    onChange={wert => { setEingabe(wert); setFehler(''); }}
                    min={min}
                    max={max}
                    error={fehler}
                    aria-label={`${label} in ${einheit}`}
                    className="h-8 pr-9 text-sm tabular-nums"
                    onFocus={() => setEingabe(e => e ?? anzeige)}
                    onBlur={abschliessen}
                    onKeyDown={e => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            setFehler(uebernehmen() ?? '');
                        }
                        if (e.key === 'Escape') {
                            e.preventDefault();
                            verwerfen.current = true;
                            (e.target as HTMLInputElement).blur();
                        }
                    }}
                />
                <span className="pointer-events-none absolute right-2.5 top-0 flex h-8 items-center text-xs text-slate-400">{einheit}</span>
            </div>
        </div>
    );
}
