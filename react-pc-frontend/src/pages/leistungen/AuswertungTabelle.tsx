import { ChevronRight, Folder } from 'lucide-react';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { InfoStundenJeEinheit } from './InfoTexte';
import { einheitJe, einheitKurz, formatZahl } from './ordnerLogik';
import type { AuswertungZeile } from './typen';

export interface AuswertungTabelleProps {
    zeilen: AuswertungZeile[];
    beschriftung: string;
    onWaehlen: (kategorieId: number) => void;
}

/**
 * Tabelle mit waagrechten Balken (Stunden) – für die Übersicht aller
 * Hauptordner und für die Aufteilung eines Ordners nach Unterordnern.
 * Jede Zeile führt per Klick in die Auswertung dieses Ordners.
 */
export function AuswertungTabelle({ zeilen, beschriftung, onWaehlen }: AuswertungTabelleProps) {
    const maxStunden = Math.max(0, ...zeilen.map((z) => z.stunden));

    return (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm" aria-label={beschriftung}>
                <thead className="bg-slate-50">
                    <tr className="text-left text-xs font-semibold text-slate-500">
                        <th className="px-4 py-2.5">Ordner</th>
                        <th className="px-3 py-2.5 text-right">Projekte</th>
                        <th className="w-[30%] px-3 py-2.5">Stunden</th>
                        <th className="px-3 py-2.5 text-right">Menge</th>
                        <th className="px-3 py-2.5 text-right">
                            <span className="inline-flex items-center justify-end gap-0.5">Std. je Einheit <InfoStundenJeEinheit /></span>
                        </th>
                        <th className="px-4 py-2.5 text-right">Leistungen</th>
                    </tr>
                </thead>
                <tbody>
                    {zeilen.map((z) => {
                        const anteil = maxStunden > 0 ? (z.stunden / maxStunden) * 100 : 0;
                        const bild = toSafeResourceUrl(z.bildUrl);
                        return (
                            <tr key={z.kategorieId} onClick={() => onWaehlen(z.kategorieId)}
                                className="cursor-pointer border-t border-slate-100 hover:bg-rose-50/50">
                                <td className="px-4 py-2">
                                    <button type="button" onClick={(e) => { e.stopPropagation(); onWaehlen(z.kategorieId); }}
                                        className="flex items-center gap-2 rounded text-left font-medium text-slate-800 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                        {bild ? <img src={bild} alt="" className="h-7 w-7 rounded object-cover" />
                                            : <span className="flex h-7 w-7 items-center justify-center rounded bg-rose-50" aria-hidden="true"><Folder className="h-4 w-4 text-rose-500" /></span>}
                                        <span>{z.bezeichnung}</span>
                                        <ChevronRight className="h-4 w-4 text-slate-400" aria-hidden="true" />
                                    </button>
                                </td>
                                <td className="px-3 py-2 text-right tabular-nums text-slate-700">{formatZahl(z.projektAnzahl, 0)}</td>
                                <td className="px-3 py-2">
                                    <div className="flex items-center gap-2">
                                        <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
                                            <div className="h-full rounded-full bg-rose-500" style={{ width: `${anteil}%` }} />
                                        </div>
                                        <span className="w-16 shrink-0 text-right tabular-nums text-slate-800">{formatZahl(z.stunden, 1)}</span>
                                    </div>
                                </td>
                                <td className="px-3 py-2 text-right tabular-nums text-slate-700">
                                    {z.menge > 0 ? `${formatZahl(z.menge, 1)} ${einheitKurz(z.verrechnungseinheit)}` : '—'}
                                </td>
                                <td className="px-3 py-2 text-right tabular-nums font-medium text-rose-700">
                                    {z.stundenProEinheit !== null ? (
                                        <span title={`Stunden je ${einheitJe(z.verrechnungseinheit)}`}>{formatZahl(z.stundenProEinheit, 2)}</span>
                                    ) : <span className="font-normal text-slate-400">—</span>}
                                </td>
                                <td className="px-4 py-2 text-right tabular-nums text-slate-700">{formatZahl(z.leistungAnzahl, 0)}</td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
