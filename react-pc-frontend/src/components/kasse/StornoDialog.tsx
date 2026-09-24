import { useState } from 'react';
import { Loader2, Undo2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, FehlerHinweis } from './KassenbuchAbschlussLeiste';

/**
 * Storniert eine festgeschriebene Buchung.
 *
 * Anders als "löschen": Die falsche Buchung bleibt sichtbar im Kassenbuch
 * stehen und bekommt eine Gegenbuchung mit umgekehrter Wirkung im laufenden
 * Monat. Genau das verlangt das Finanzamt — es soll nachvollziehbar bleiben,
 * dass hier etwas korrigiert wurde und was ursprünglich dastand.
 *
 * Der Text im Dialog sagt das ausdrücklich, weil die Erwartung "Storno =
 * verschwindet" sonst zu Rückfragen führt, wenn die alte Zeile weiter im
 * Kassenbuch steht.
 */
interface Props {
    belegId: number;
    laufendeNummer?: number | null;
    beschreibung?: string | null;
    betragBrutto?: number | null;
    onClose: () => void;
    /** Storno erfolgreich – der Aufrufer lädt Liste und Kassenbuch neu. */
    onStorniert: () => void;
}

const euro = (n: number) => n.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function StornoDialog({
    belegId, laufendeNummer, beschreibung, betragBrutto, onClose, onStorniert,
}: Props) {
    const [grund, setGrund] = useState('');
    const [speichern, setSpeichern] = useState(false);
    const [fehler, setFehler] = useState<{ text: string; hinweis?: string } | null>(null);

    const absenden = async () => {
        if (!grund.trim()) return;
        setSpeichern(true);
        setFehler(null);
        try {
            const res = await fetch(`/api/buchhaltung/kassenbuch/belege/${belegId}/storno`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ grund: grund.trim() }),
            });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                setFehler({
                    text: body.message ?? 'Die Buchung konnte nicht storniert werden.',
                    hinweis: body.hinweis,
                });
                return;
            }
            onStorniert();
        } catch {
            setFehler({ text: 'Keine Verbindung zum Server.' });
        } finally {
            setSpeichern(false);
        }
    };

    return (
        <Dialog titel="Buchung stornieren" onClose={onClose}>
            <dl className="rounded-lg bg-slate-50 p-3 text-sm space-y-1">
                {laufendeNummer != null && (
                    <div className="flex justify-between gap-4">
                        <dt className="text-slate-500">Belegnummer</dt>
                        <dd className="font-semibold text-slate-900 tabular-nums">Nr. {laufendeNummer}</dd>
                    </div>
                )}
                <div className="flex justify-between gap-4">
                    <dt className="text-slate-500">Verwendungszweck</dt>
                    <dd className="text-slate-800 text-right">{beschreibung || '–'}</dd>
                </div>
                {betragBrutto != null && (
                    <div className="flex justify-between gap-4">
                        <dt className="text-slate-500">Betrag</dt>
                        <dd className="font-semibold text-slate-900 tabular-nums">{euro(betragBrutto)} €</dd>
                    </div>
                )}
            </dl>

            <p className="mt-4 text-sm text-slate-600">
                Diese Buchung bleibt im Kassenbuch stehen. Es entsteht zusätzlich eine
                Gegenbuchung im laufenden Monat, die sie wieder aufhebt. Anschließend
                kannst du den Vorgang richtig neu erfassen.
            </p>

            <label className="block mt-4">
                <span className="text-sm font-medium text-slate-700">
                    Warum wird storniert? <span className="text-rose-600">*</span>
                </span>
                <input
                    type="text"
                    value={grund}
                    onChange={e => setGrund(e.target.value)}
                    maxLength={500}
                    autoFocus
                    placeholder="z. B. Betrag falsch abgetippt, Beleg gehört ins Private"
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
                <span className="mt-1 block text-xs text-slate-500">
                    Der Grund steht später im Kassenbuch und im Protokoll.
                </span>
            </label>

            {fehler && <FehlerHinweis fehler={fehler} />}

            <div className="flex justify-end gap-2 pt-4">
                <Button variant="secondary" size="sm" onClick={onClose} disabled={speichern}>
                    Abbrechen
                </Button>
                <Button size="sm" onClick={absenden} disabled={speichern || !grund.trim()}>
                    {speichern ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden /> : <Undo2 className="w-4 h-4" aria-hidden />}
                    Gegenbuchung erstellen
                </Button>
            </div>
        </Dialog>
    );
}
