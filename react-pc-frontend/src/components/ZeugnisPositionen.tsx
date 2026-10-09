import { useEffect, useState } from 'react';
import { AlertCircle, FileBadge } from 'lucide-react';
import { useToast } from './ui/toast';
import { formatMenge, type DokumentPosition } from './zuordnung/positionen';

interface ZeugnisPositionenProps {
    /** ID des Lieferantendokuments (= Geschäftsdokument-ID). */
    dokumentId: number;
}

type Zustand =
    | { art: 'laden' }
    | { art: 'fertig'; positionen: DokumentPosition[] }
    | { art: 'fehler' };

const FEHLER_TEXT = 'Positionen des Werkstoffzeugnisses konnten nicht geladen werden.';

const leer = (wert: string | null | undefined) => (wert && wert.trim() ? wert : '–');

function mengeMitEinheit(position: DokumentPosition): string {
    if (position.menge == null) return '–';
    const menge = formatMenge(position.menge);
    return position.mengeneinheit ? `${menge} ${position.mengeneinheit}` : menge;
}

/**
 * Was im Werkstoffzeugnis steht: je Erzeugnis Werkstoff, Charge, Abmessung und
 * Menge. Liest die gespeicherten Positionen des Dokuments.
 */
export function ZeugnisPositionen({ dokumentId }: ZeugnisPositionenProps) {
    const toast = useToast();
    // Ergebnis gehört immer zu einem Dokument – für ein anderes Dokument gilt „wird geladen“
    const [ergebnis, setErgebnis] = useState<{ dokumentId: number; zustand: Zustand } | null>(null);
    const zustand: Zustand = ergebnis?.dokumentId === dokumentId ? ergebnis.zustand : { art: 'laden' };

    useEffect(() => {
        const controller = new AbortController();
        fetch(`/api/bestellungen-uebersicht/positionen/${encodeURIComponent(String(dokumentId))}`, { signal: controller.signal })
            .then(async res => {
                // 404: zu diesem Dokument gibt es (noch) keine Positionen
                if (res.status === 404) return [];
                if (!res.ok) throw new Error(FEHLER_TEXT);
                const daten = await res.json();
                return Array.isArray(daten?.positionen) ? (daten.positionen as DokumentPosition[]) : [];
            })
            .then(positionen => {
                if (!controller.signal.aborted) setErgebnis({ dokumentId, zustand: { art: 'fertig', positionen } });
            })
            .catch((err: unknown) => {
                if (controller.signal.aborted) return;
                setErgebnis({ dokumentId, zustand: { art: 'fehler' } });
                toast.error(err instanceof Error && err.message !== 'Failed to fetch' ? err.message : FEHLER_TEXT);
            });
        return () => controller.abort();
        // toast ist bei jedem Render ein neues Objekt – nur bei neuem Dokument neu laden
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [dokumentId]);

    return (
        <section aria-labelledby={`zeugnis-positionen-${dokumentId}`} className="space-y-2">
            <h3 id={`zeugnis-positionen-${dokumentId}`} className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                <FileBadge className="w-4 h-4 text-slate-500" aria-hidden="true" />
                Inhalt des Werkstoffzeugnisses
            </h3>

            {zustand.art === 'laden' && (
                <div className="space-y-1.5" aria-label="Positionen werden geladen" role="status">
                    {[0, 1, 2].map(i => (
                        <div key={i} className="h-7 rounded bg-slate-100 motion-safe:animate-pulse" />
                    ))}
                </div>
            )}

            {zustand.art === 'fehler' && (
                <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                    <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
                    {FEHLER_TEXT}
                </div>
            )}

            {zustand.art === 'fertig' && zustand.positionen.length === 0 && (
                <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-500">
                    Noch keine Positionen ausgelesen.
                </p>
            )}

            {zustand.art === 'fertig' && zustand.positionen.length > 0 && (
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-xs">
                        <thead className="bg-slate-50 text-slate-500">
                            <tr>
                                <th scope="col" className="px-2 py-1.5 text-left font-medium">Erzeugnis</th>
                                <th scope="col" className="px-2 py-1.5 text-left font-medium">Werkstoff</th>
                                <th scope="col" className="px-2 py-1.5 text-left font-medium">Charge</th>
                                <th scope="col" className="px-2 py-1.5 text-left font-medium">Abmessung</th>
                                <th scope="col" className="px-2 py-1.5 text-right font-medium">Menge</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 text-slate-700">
                            {zustand.positionen.map(position => (
                                <tr key={position.id}>
                                    <td className="px-2 py-1.5 font-medium text-slate-900 break-words">{leer(position.bezeichnung)}</td>
                                    <td className="px-2 py-1.5 break-words">{leer(position.werkstoff)}</td>
                                    <td className="px-2 py-1.5 tabular-nums whitespace-nowrap">{leer(position.charge)}</td>
                                    <td className="px-2 py-1.5 whitespace-nowrap">{leer(position.abmessung)}</td>
                                    <td className="px-2 py-1.5 text-right tabular-nums whitespace-nowrap">{mengeMitEinheit(position)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
