import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import type { SteuerberaterStunden, SteuerberaterVorschau, SteuerberaterZeile } from './api';
import { BerichtInfo } from './BerichtInfo';
import { formatZahl } from './berichtFormat';
import { INFO_RESTURLAUB, INFO_STUNDENKONTO } from './infoTexte';
import { arbeitsstundenInfo, arbeitsstundenTitel, type Stundenart } from './stundenart';

interface SteuerberaterTabelleProps {
    vorschau: SteuerberaterVorschau;
    mitStundenkonto: boolean;
    mitResturlaub: boolean;
}

interface Spalte {
    titel: string;
    info?: ReactNode;
    zelle: (zeile: SteuerberaterZeile) => ReactNode;
    /** Wert in der Summenzeile; ohne Angabe bleibt sie leer. */
    summe?: (summe: SteuerberaterStunden) => ReactNode;
}

const ZAHL = 'px-1.5 py-2 text-right tabular-nums whitespace-nowrap';

function Stundenkonto({ stand }: { stand: number }) {
    return <span className={cn(stand < 0 && 'font-medium text-red-600')}>{formatZahl(stand)}</span>;
}

function Status({ festgeschrieben }: { festgeschrieben: boolean }) {
    return festgeschrieben
        ? <span className="inline-flex rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 ring-1 ring-emerald-200">abgeschlossen</span>
        : <span className="inline-flex rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-amber-200">noch offen</span>;
}

function spalten(art: Stundenart, mitStundenkonto: boolean, mitResturlaub: boolean): Spalte[] {
    return [
        { titel: 'Tagessoll', zelle: z => formatZahl(z.tagessoll) },
        { titel: 'Soll', zelle: z => formatZahl(z.sollStunden), summe: s => formatZahl(s.sollStunden) },
        { titel: arbeitsstundenTitel(art), info: <BerichtInfo info={arbeitsstundenInfo(art)} />, zelle: z => formatZahl(z.arbeitsstunden), summe: s => formatZahl(s.arbeitsstunden) },
        { titel: 'Urlaub', zelle: z => formatZahl(z.urlaubStunden), summe: s => formatZahl(s.urlaubStunden) },
        { titel: 'Feiertage', zelle: z => formatZahl(z.feiertagStunden), summe: s => formatZahl(s.feiertagStunden) },
        { titel: 'Krankheit', zelle: z => formatZahl(z.krankheitStunden), summe: s => formatZahl(s.krankheitStunden) },
        { titel: 'Fortbildung', zelle: z => formatZahl(z.fortbildungStunden), summe: s => formatZahl(s.fortbildungStunden) },
        ...(mitStundenkonto ? [{ titel: 'Stundenkonto', info: <BerichtInfo info={INFO_STUNDENKONTO} />, zelle: (z: SteuerberaterZeile) => <Stundenkonto stand={z.stundenkontoStand} /> }] : []),
        ...(mitResturlaub ? [{ titel: 'Resturlaub (Tage)', info: <BerichtInfo info={INFO_RESTURLAUB} />, zelle: (z: SteuerberaterZeile) => formatZahl(z.resturlaubTage) }] : []),
    ];
}

/**
 * Vorschau der Stundenaufstellung mit denselben Spalten wie das PDF: eine Zeile
 * je Mitarbeiter plus Summe. Alle Werte in Stunden, Resturlaub in Tagen.
 */
export function SteuerberaterTabelle({ vorschau, mitStundenkonto, mitResturlaub }: SteuerberaterTabelleProps) {
    const liste = spalten(vorschau.stundenart, mitStundenkonto, mitResturlaub);
    return (
        // -m-1 p-1: Fokus-Ringe der Info-Knöpfe im Scrollbereich nicht abschneiden.
        <div className="-m-1 overflow-x-auto p-1">
            <table className="w-full min-w-[40rem] border-collapse text-sm">
                <caption className="sr-only">Stundenaufstellung, alle Werte in Stunden</caption>
                <thead>
                    <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
                        <th scope="col" className="px-1.5 py-2 text-left align-bottom">Name</th>
                        {liste.map(s => (
                            <th key={s.titel} scope="col" className="px-1.5 py-2 text-right align-bottom">
                                <span className="inline-flex items-center justify-end gap-0.5">{s.titel}{s.info}</span>
                            </th>
                        ))}
                        <th scope="col" className="px-1.5 py-2 text-left align-bottom">Monat</th>
                    </tr>
                </thead>
                <tbody>
                    {vorschau.zeilen.map(zeile => (
                        <tr key={zeile.mitarbeiterId} className="border-b border-slate-100">
                            <th scope="row" className="whitespace-nowrap px-1.5 py-2 text-left font-medium text-slate-900">{zeile.name}</th>
                            {liste.map(s => <td key={s.titel} className={ZAHL}>{s.zelle(zeile)}</td>)}
                            <td className="px-1.5 py-2"><Status festgeschrieben={zeile.festgeschrieben} /></td>
                        </tr>
                    ))}
                </tbody>
                <tfoot>
                    <tr className="border-t-2 border-slate-300 font-semibold text-slate-900">
                        <th scope="row" className="px-1.5 py-2 text-left">Summe</th>
                        {liste.map(s => <td key={s.titel} className={ZAHL}>{s.summe?.(vorschau.summe) ?? ''}</td>)}
                        <td />
                    </tr>
                </tfoot>
            </table>
        </div>
    );
}
