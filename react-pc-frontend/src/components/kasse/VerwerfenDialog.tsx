import { useState } from 'react';
import { Loader2, Trash2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, FehlerHinweis } from './KassenbuchAbschlussLeiste';

/**
 * Verwirft einen noch nicht festgeschriebenen Beleg.
 *
 * Der Grund ist Pflicht und wandert unveränderlich ins Kassenbuch-Protokoll —
 * ohne ihn ließe sich später nicht mehr sagen, warum ein Betrag aus der
 * Buchhaltung verschwunden ist. Genau deshalb ein richtiger Dialog statt eines
 * Browser-`prompt`: Der Text braucht eine Längenbegrenzung, sichtbare
 * Vorschläge und muss auch auf Geräten funktionieren, die `prompt`
 * unterdrücken.
 */
interface Props {
    belegId: number;
    beschreibung?: string | null;
    onClose: () => void;
    onVerworfen: () => void;
}

/** Die drei Fälle, die in der Praxis fast immer zutreffen. */
const VORSCHLAEGE = [
    'Doppelt gescannt',
    'Foto unlesbar, wird neu gescannt',
    'Gehört ins Private, nicht in die Firma',
];

export function VerwerfenDialog({ belegId, beschreibung, onClose, onVerworfen }: Props) {
    const [grund, setGrund] = useState('');
    const [speichern, setSpeichern] = useState(false);
    const [fehler, setFehler] = useState<{ text: string; hinweis?: string } | null>(null);

    const absenden = async () => {
        if (!grund.trim()) return;
        setSpeichern(true);
        setFehler(null);
        try {
            const res = await fetch(
                `/api/buchhaltung/belege/${belegId}?grund=${encodeURIComponent(grund.trim())}`,
                { method: 'DELETE' });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                setFehler({
                    text: body.message ?? 'Der Beleg konnte nicht verworfen werden.',
                    hinweis: body.hinweis,
                });
                return;
            }
            onVerworfen();
        } catch {
            setFehler({ text: 'Keine Verbindung zum Server.' });
        } finally {
            setSpeichern(false);
        }
    };

    return (
        <Dialog titel="Beleg verwerfen" onClose={onClose}>
            {beschreibung && (
                <p className="text-sm text-slate-500">
                    Betrifft: <span className="text-slate-800">{beschreibung}</span>
                </p>
            )}
            <p className="mt-2 text-sm text-slate-600">
                Der Beleg zählt danach nicht mehr in der Buchhaltung. Die Bilddatei bleibt
                erhalten – ein Prüfer darf auch sehen, was aussortiert wurde.
            </p>

            <label className="block mt-4">
                <span className="text-sm font-medium text-slate-700">
                    Warum wird verworfen? <span className="text-rose-600">*</span>
                </span>
                <input
                    type="text"
                    value={grund}
                    onChange={e => setGrund(e.target.value)}
                    maxLength={500}
                    autoFocus
                    placeholder="z. B. doppelt gescannt"
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
                <span className="mt-1 block text-xs text-slate-500">
                    Der Grund steht dauerhaft im Protokoll und lässt sich später nicht mehr ändern.
                </span>
            </label>

            <div className="mt-3 flex flex-wrap gap-2">
                {VORSCHLAEGE.map(v => (
                    <button
                        key={v}
                        type="button"
                        onClick={() => setGrund(v)}
                        className="px-3 py-1 rounded-full text-xs border border-slate-200 text-slate-600 hover:border-rose-300 hover:text-rose-700 transition-colors"
                    >
                        {v}
                    </button>
                ))}
            </div>

            {fehler && <FehlerHinweis fehler={fehler} />}

            <div className="flex justify-end gap-2 pt-4">
                <Button variant="secondary" size="sm" onClick={onClose} disabled={speichern}>
                    Abbrechen
                </Button>
                <Button size="sm" onClick={absenden} disabled={speichern || !grund.trim()}>
                    {speichern ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden /> : <Trash2 className="w-4 h-4" aria-hidden />}
                    Verwerfen
                </Button>
            </div>
        </Dialog>
    );
}
