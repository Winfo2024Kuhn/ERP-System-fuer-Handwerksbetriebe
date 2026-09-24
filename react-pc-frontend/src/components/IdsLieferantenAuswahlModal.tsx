import type { PunchoutForm } from '../types/ids';
import { submitPunchoutForm } from '../lib/idsPunchout';
import { useEffect, useState } from 'react';
import { FolderOpen, Loader2, Plug, ShoppingCart, X } from 'lucide-react';
import { Button } from './ui/button';
import { useToast } from './ui/toast';

interface IdsLieferant {
    id: number;
    name: string;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    /** Aus dem Projektbedarf geöffnet: Der zurückgegebene Warenkorb wird an diesem Projekt geparkt. */
    projektId?: number;
    projektName?: string;
}

/**
 * Modal zur Auswahl eines IDS-aktivierten Lieferanten. Bei Klick auf
 * "Shop öffnen" baut das Modal eine versteckte Auto-Submit-Form mit
 * den vom Backend gelieferten Punchout-Feldern und submittet sie in
 * einem neuen Browser-Fenster — Login-Daten gehen direkt an den Shop,
 * der Bauleiter sieht den eingeloggten Warenkorb.
 *
 * Der Shop posted den fertigen Cart später an /api/ids/punchout/.../return
 * zurück, das Backend legt eine Bestellung im Status ENTWURF an. Mit
 * projektId wird der Warenkorb am Projekt geparkt und später von dort bestellt.
 */
export function IdsLieferantenAuswahlModal({ isOpen, onClose, projektId, projektName }: Props) {
    const toast = useToast();
    const [lieferanten, setLieferanten] = useState<IdsLieferant[]>([]);
    const [loading, setLoading] = useState(false);
    const [starting, setStarting] = useState<number | null>(null);

    useEffect(() => {
        if (!isOpen) return;
        let aborted = false;
        setLoading(true);
        fetch('/api/ids/lieferanten')
            .then(res => (res.ok ? res.json() : []))
            .then((arr: IdsLieferant[]) => {
                if (aborted) return;
                setLieferanten(Array.isArray(arr) ? arr : []);
            })
            .catch(err => {
                console.error('IDS-Lieferanten konnten nicht geladen werden', err);
                if (!aborted) toast.error('Lieferanten konnten nicht geladen werden.');
            })
            .finally(() => { if (!aborted) setLoading(false); });
        return () => { aborted = true; };
    }, [isOpen, toast]);

    const handleStart = async (lieferantId: number) => {
        setStarting(lieferantId);
        try {
            const res = await fetch(`/api/ids/punchout/${lieferantId}/start`, projektId != null
                ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projektId, projektName }) }
                : { method: 'POST' });
            if (res.status === 404) {
                toast.error('Für diesen Lieferanten ist keine Schnittstelle hinterlegt.');
                return;
            }
            if (res.status === 409) {
                toast.error('Schnittstelle ist nicht vollständig konfiguriert (Punchout-URL?).');
                return;
            }
            if (res.status === 400 && projektId != null) {
                toast.error('Das Projekt konnte dem Warenkorb nicht zugeordnet werden.');
                return;
            }
            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`);
            }
            const form: PunchoutForm = await res.json();
            submitPunchoutForm(form);
            onClose();
        } catch (err) {
            console.error('Punchout konnte nicht gestartet werden', err);
            toast.error('Shop konnte nicht geöffnet werden.');
        } finally {
            setStarting(null);
        }
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
            onClick={onClose}
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center">
                            <Plug className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900">
                                Lieferanten-Shop öffnen
                            </h3>
                            <p className="text-xs text-slate-500">
                                Du wirst automatisch beim Shop angemeldet.
                            </p>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="px-6 py-4 max-h-[60vh] overflow-y-auto">
                    {projektId != null && (
                        <p className="mb-3 flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                            <FolderOpen className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
                            <span>
                                Der Warenkorb wird am Projekt{' '}
                                <strong className="font-semibold text-slate-900">{projektName || `#${projektId}`}</strong>{' '}
                                geparkt. Bestellt wird erst, wenn du ihn später dort abschickst.
                            </span>
                        </p>
                    )}
                    {loading ? (
                        <div className="flex items-center justify-center py-10 text-slate-500">
                            <Loader2 className="w-5 h-5 animate-spin mr-2" />
                            Lieferanten werden geladen…
                        </div>
                    ) : lieferanten.length === 0 ? (
                        <div className="py-10 text-center text-sm text-slate-500">
                            <p className="font-medium text-slate-700 mb-1">
                                Noch keine Schnittstelle eingerichtet.
                            </p>
                            <p>
                                Ein Admin kann pro Lieferant unter „Schnittstelle"
                                die Zugangsdaten hinterlegen.
                            </p>
                        </div>
                    ) : (
                        <ul className="divide-y divide-slate-100">
                            {lieferanten.map(l => (
                                <li key={l.id}>
                                    <button
                                        type="button"
                                        onClick={() => handleStart(l.id)}
                                        disabled={starting != null}
                                        className="w-full flex items-center justify-between gap-3 px-2 py-3 rounded-lg text-left hover:bg-rose-50/50 transition-colors disabled:opacity-50"
                                    >
                                        <span className="font-medium text-slate-900">{l.name}</span>
                                        {starting === l.id ? (
                                            <Loader2 className="w-4 h-4 animate-spin text-rose-600" />
                                        ) : (
                                            <ShoppingCart className="w-4 h-4 text-slate-400" />
                                        )}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div className="px-6 py-3 border-t border-slate-100 bg-slate-50/60 flex justify-end">
                    <Button variant="outline" onClick={onClose}>
                        Abbrechen
                    </Button>
                </div>
            </div>
        </div>
    );
}
