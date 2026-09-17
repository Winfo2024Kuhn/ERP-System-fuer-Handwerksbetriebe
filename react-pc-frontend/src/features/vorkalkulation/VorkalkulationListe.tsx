import { Calculator, ChevronRight } from 'lucide-react';

import { Card } from '../../components/ui/card';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { cn } from '../../lib/utils';
import type { VorkalkulationEintrag } from './types';

interface VorkalkulationListeProps {
    eintraege: VorkalkulationEintrag[];
    onOeffnen: (eintrag: VorkalkulationEintrag) => void;
}

/**
 * Der Reiter "Vor-Kalkulation" im Anfrage- und Projekt-Editor.
 *
 * Zweiter Einstieg neben dem Dokumenteditor: Die Kalkulation haengt an der
 * Leistung, nicht am Dokument — deshalb muss sie auch dann auffindbar
 * bleiben, wenn aus der Anfrage laengst ein Projekt geworden ist.
 */
export function VorkalkulationListe({ eintraege, onOeffnen }: VorkalkulationListeProps) {
    if (eintraege.length === 0) {
        return (
            <Card className="border-dashed p-10 text-center">
                <Calculator className="mx-auto mb-2 h-8 w-8 text-rose-200" aria-hidden="true" />
                <p className="font-medium text-slate-700">Noch keine Vor-Kalkulation angelegt</p>
                <p className="mt-1 text-sm text-slate-500">
                    Vor-Kalkulationen entstehen im Dokumenteditor an der jeweiligen Leistung.
                </p>
            </Card>
        );
    }

    return (
        <Card className="overflow-hidden">
            <table className="w-full text-sm">
                <caption className="sr-only">Vor-Kalkulationen dieser Anfrage oder dieses Projekts</caption>
                <thead className="border-b border-slate-200 bg-slate-50">
                    <tr className="text-left text-[11px] uppercase tracking-wider text-slate-500">
                        <th scope="col" className="px-4 py-2 font-semibold">Dokument</th>
                        <th scope="col" className="px-4 py-2 font-semibold">Pos.</th>
                        <th scope="col" className="px-4 py-2 font-semibold">Leistung</th>
                        <th scope="col" className="px-4 py-2 text-right font-semibold">Kostet uns</th>
                        <th scope="col" className="px-4 py-2 text-right font-semibold">Wir nehmen</th>
                        <th scope="col" className="px-4 py-2 font-semibold">Geändert</th>
                        <th scope="col" className="px-4 py-2"><span className="sr-only">Öffnen</span></th>
                    </tr>
                </thead>
                <tbody>
                    {eintraege.map((eintrag) => (
                        <tr
                            key={eintrag.id}
                            className="group cursor-pointer border-b border-slate-100 transition-colors last:border-b-0 hover:bg-rose-50/40"
                            onClick={() => onOeffnen(eintrag)}
                        >
                            <td className="px-4 py-2.5 text-slate-600">{eintrag.dokument}</td>
                            <td className="px-4 py-2.5">
                                <span className="inline-flex h-6 w-6 items-center justify-center rounded border border-rose-100 bg-rose-50 text-xs font-bold text-rose-600">
                                    {eintrag.position}
                                </span>
                            </td>
                            <td className="px-4 py-2.5">
                                <span className="font-medium text-slate-900">{eintrag.titel}</span>
                                {eintrag.herkunft === 'ANFRAGE' && (
                                    <span className="ml-2 rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[10px] text-slate-500">
                                        aus Anfrage
                                    </span>
                                )}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-slate-600">
                                {formatCurrency(eintrag.herstellkosten)}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums font-semibold text-slate-900">
                                {formatCurrency(eintrag.verkaufspreis)}
                            </td>
                            <td className="px-4 py-2.5 text-slate-500">{eintrag.geaendertAm}</td>
                            <td className="px-4 py-2.5 text-right">
                                <button
                                    type="button"
                                    aria-label={`Vor-Kalkulation für ${eintrag.titel} öffnen`}
                                    onClick={(e) => { e.stopPropagation(); onOeffnen(eintrag); }}
                                    className={cn(
                                        'rounded-md p-1 text-slate-300 transition-colors',
                                        'hover:bg-rose-100 hover:text-rose-600 group-hover:text-rose-500',
                                    )}
                                >
                                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </Card>
    );
}
