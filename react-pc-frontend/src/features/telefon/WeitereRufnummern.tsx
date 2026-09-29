import { useEffect, useState } from 'react';
import { PhoneForwarded, Trash2 } from 'lucide-react';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { ladeKontaktRufnummern, loescheKontaktRufnummer } from './api';
import type { KontaktRufnummer, AktenTyp } from './types';

/**
 * „Weitere Rufnummern" unter den Telefonfeldern der Akte: Nummern, die beim
 * Zuordnen eines Anrufs gemerkt wurden. Für alle sichtbar; entfernen darf
 * nur, wer das Telefon-Recht hat. Ist die Liste leer, erscheint nichts.
 */
interface WeitereRufnummernProps {
    typ: AktenTyp;
    kontaktId: number;
    darfLoeschen: boolean;
}

export function WeitereRufnummern({ typ, kontaktId, darfLoeschen }: WeitereRufnummernProps) {
    const [nummern, setNummern] = useState<KontaktRufnummer[]>([]);

    useEffect(() => {
        const abbruch = new AbortController();
        ladeKontaktRufnummern(typ, kontaktId, abbruch.signal)
            .then((liste) => {
                if (!abbruch.signal.aborted) setNummern(liste);
            })
            // Zusatzangabe – fehlt sie, bleibt der Bereich einfach weg.
            .catch(() => undefined);
        return () => abbruch.abort();
    }, [typ, kontaktId]);

    if (nummern.length === 0) return null;
    return <RufnummernListe nummern={nummern} darfLoeschen={darfLoeschen} onEntfernt={(id) => setNummern((alt) => alt.filter((n) => n.id !== id))} />;
}

function RufnummernListe({ nummern, darfLoeschen, onEntfernt }: {
    nummern: KontaktRufnummer[];
    darfLoeschen: boolean;
    onEntfernt: (id: number) => void;
}) {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [entfernt, setEntfernt] = useState<number | null>(null);

    const entferne = async (eintrag: KontaktRufnummer) => {
        const ok = await bestaetige({
            title: 'Rufnummer entfernen?',
            message: `Die Nummer ${eintrag.nummer} wird nicht mehr diesem Kontakt zugeordnet. Bisherige Anrufe bleiben unverändert.`,
            confirmLabel: 'Entfernen',
            variant: 'danger',
        });
        if (!ok) return;
        setEntfernt(eintrag.id);
        try {
            await loescheKontaktRufnummer(eintrag.id);
            onEntfernt(eintrag.id);
            toast.success('Rufnummer entfernt.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Rufnummer konnte nicht entfernt werden.');
        } finally {
            setEntfernt(null);
        }
    };

    return (
        <div className="p-3 bg-slate-50 rounded-lg flex items-start gap-3">
            <div className="p-2 bg-white rounded-md shadow-sm text-slate-400 shrink-0">
                <PhoneForwarded aria-hidden="true" className="w-4 h-4" />
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">Weitere Rufnummern</p>
                <ul className="mt-0.5 space-y-1">
                    {nummern.map((eintrag) => (
                        <li key={eintrag.id} className="flex items-center gap-2">
                            <span className="min-w-0 flex-1 break-words font-medium tabular-nums text-slate-900">{eintrag.nummer}</span>
                            {darfLoeschen && (
                                <button
                                    type="button"
                                    onClick={() => void entferne(eintrag)}
                                    disabled={entfernt === eintrag.id}
                                    aria-label={`Rufnummer ${eintrag.nummer} entfernen`}
                                    title="Rufnummer entfernen"
                                    className="shrink-0 rounded p-1 text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50"
                                >
                                    <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                                </button>
                            )}
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
}
