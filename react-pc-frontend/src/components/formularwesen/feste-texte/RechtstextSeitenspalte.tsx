import type { ReactNode } from 'react';
import { History, Plus } from 'lucide-react';
import { Button } from '../../ui/button';
import { cn } from '../../../lib/utils';
import { ErklaerInfo } from './ErklaerInfo';
import { formatiereDatum, formatiereZeitpunkt, type RechtstextFassungKurz, type RechtstextPlatzhalter } from './rechtstextApi';
import { PLATZHALTER_INFO, type TextVorschlag } from './rechtstextTexte';

export interface RechtstextSeitenspalteProps {
    platzhalter: RechtstextPlatzhalter[];
    vorschlaege: TextVorschlag[];
    /** null = wird noch geladen. */
    fassungen: RechtstextFassungKurz[] | null;
    fassungenFehler: string | null;
    /** Gerade angezeigte frühere Fassung (hervorgehoben). */
    gewaehlteFassung: number | null;
    /** Fassung, die gerade gilt – bekommt den Vermerk „gilt jetzt“. */
    aktuelleFassung: number;
    onPlatzhalter: (token: string) => void;
    onVorschlag: (html: string) => void;
    onFassung: (fassung: number) => void;
    onFassungenNeuLaden: () => void;
}

/** Rechte Spalte im Dialog: Platzhalter, Vorschläge und frühere Fassungen. */
export function RechtstextSeitenspalte({
    platzhalter, vorschlaege, fassungen, fassungenFehler, gewaehlteFassung, aktuelleFassung,
    onPlatzhalter, onVorschlag, onFassung, onFassungenNeuLaden,
}: RechtstextSeitenspalteProps) {
    return (
        <aside aria-label="Hilfen zum Text" className="-m-1 flex w-72 shrink-0 flex-col gap-5 overflow-y-auto p-1">
            {platzhalter.length > 0 && (
                <Abschnitt titel="Platzhalter" info={<ErklaerInfo titel="Platzhalter" info={PLATZHALTER_INFO} />}>
                    <p className="text-xs text-slate-500">Ein Klick setzt den Platzhalter dort ein, wo Sie gerade schreiben.</p>
                    <ul className="flex flex-col gap-1.5">
                        {platzhalter.map(p => (
                            <li key={p.token}>
                                <button
                                    type="button"
                                    title={`Beispiel: ${p.beispiel}`}
                                    onClick={() => onPlatzhalter(p.token)}
                                    className="group flex w-full items-start gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-left transition-colors hover:border-rose-300 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    <Plus aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400 group-hover:text-rose-600" />
                                    <span className="min-w-0">
                                        <span className="block text-sm text-slate-700">{p.beschreibung}</span>
                                        <span className="mt-0.5 inline-block break-all rounded bg-slate-100 px-1 font-mono text-[11px] text-slate-700">
                                            {p.token}
                                        </span>
                                    </span>
                                </button>
                            </li>
                        ))}
                    </ul>
                </Abschnitt>
            )}

            {vorschlaege.map(v => (
                <Abschnitt key={v.titel} titel={v.titel} info={<ErklaerInfo titel={v.titel} info={v.info} />}>
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="self-start border-rose-300"
                        onClick={() => onVorschlag(v.html)}
                    >
                        <Plus aria-hidden="true" className="mr-1 inline h-4 w-4" />{v.knopf}
                    </Button>
                </Abschnitt>
            ))}

            <Abschnitt titel="Frühere Fassungen">
                {fassungenFehler ? (
                    <div className="text-sm text-slate-600">
                        <p>{fassungenFehler}</p>
                        <button type="button" onClick={onFassungenNeuLaden} className="mt-1 text-rose-700 underline hover:text-rose-800">
                            Erneut versuchen
                        </button>
                    </div>
                ) : fassungen === null ? (
                    <div className="flex flex-col gap-1.5" aria-label="Fassungen werden geladen">
                        {[0, 1].map(i => <div key={i} className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
                    </div>
                ) : fassungen.length === 0 ? (
                    <p className="text-sm text-slate-500">Noch nicht gespeichert – es gilt der Standardtext.</p>
                ) : (
                    <ul className="flex flex-col gap-1.5">
                        {fassungen.map(f => (
                            <li key={f.fassung}>
                                <button
                                    type="button"
                                    aria-current={gewaehlteFassung === f.fassung ? 'true' : undefined}
                                    onClick={() => onFassung(f.fassung)}
                                    className={cn(
                                        'flex w-full items-start gap-2 rounded-lg border px-2.5 py-2 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                                        gewaehlteFassung === f.fassung
                                            ? 'border-rose-300 bg-rose-50'
                                            : 'border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50',
                                    )}
                                >
                                    <History aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" />
                                    <span className="min-w-0">
                                        <span className="block text-sm font-medium text-slate-800">
                                            Fassung {f.fassung} · Stand {formatiereDatum(f.stand)}
                                            {f.fassung === aktuelleFassung && (
                                                <span className="ml-1.5 inline-block whitespace-nowrap rounded-full bg-emerald-50 px-1.5 py-0.5 text-[11px] font-medium text-emerald-700">gilt jetzt</span>
                                            )}
                                        </span>
                                        {f.geaendertAm && (
                                            <span className="block text-xs text-slate-500">geändert am {formatiereZeitpunkt(f.geaendertAm)}</span>
                                        )}
                                    </span>
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </Abschnitt>
        </aside>
    );
}

function Abschnitt({ titel, info, children }: { titel: string; info?: ReactNode; children: ReactNode }) {
    return (
        <section className="flex flex-col gap-2">
            <div className="flex items-center gap-1">
                <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{titel}</h4>
                {info}
            </div>
            {children}
        </section>
    );
}
