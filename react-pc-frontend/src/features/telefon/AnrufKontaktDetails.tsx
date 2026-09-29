import type { ComponentType, ReactNode } from 'react';
import { AlertCircle, Briefcase, ChevronRight, FileCheck, MapPin, UserRound } from 'lucide-react';
import { cn } from '../../lib/utils';
import { anfragePfad, projektPfad } from './api';
import type { KontaktKurz, UeberblickAnfrage, UeberblickProjekt } from './types';
import type { UeberblickZustand } from './useKontaktUeberblick';

/**
 * Der untere Teil des Anruf-Fensters für bekannte Anrufer: Ansprechpartner,
 * Adresse und – bei Kunden – alle Projekte und Anfragen. Bei Steuerberatern
 * stehen die Ansprechpartner der Kanzlei. Jeder Eintrag ist
 * ein Knopf, der direkt ins Projekt bzw. in die Anfrage springt.
 *
 * <p>Den Ort kennt das Fenster schon aus dem Anruf selbst; er steht deshalb
 * sofort da, während der Rest lädt.</p>
 */

interface AnrufKontaktDetailsProps {
    kontakt: KontaktKurz;
    zustand: UeberblickZustand;
    onOeffnen: (pfad: string) => void;
}

export function AnrufKontaktDetails({ kontakt, zustand, onOeffnen }: AnrufKontaktDetailsProps) {
    const daten = zustand.status === 'fertig' ? zustand.daten : null;
    const laedt = zustand.status === 'laedt';
    const istKunde = kontakt.typ === 'KUNDE';
    // Beim Lieferanten heißt der Ansprechpartner „Vertreter"; Kunden und Kanzleien haben Ansprechpartner.
    const ansprechpartnerTitel = kontakt.typ === 'LIEFERANT' ? 'Vertreter' : 'Ansprechpartner';

    const ortZeile = [daten?.plz, daten?.ort ?? kontakt.ort].filter(Boolean).join(' ');
    const hatAdresse = Boolean(daten?.strasse || ortZeile);
    // Ruft ein Ansprechpartner der Kanzlei von seiner eigenen Nummer an, steht genau er da – nicht alle.
    const ansprechpartner = kontakt.ansprechpartner || daten?.ansprechpartner;
    // Den Namen aus dem Anruf gibt es sofort – dafür muss der Überblick nicht erst laden.
    const ansprechpartnerLaedt = laedt && !kontakt.ansprechpartner;
    const hatAnsprechpartner = ansprechpartnerLaedt || Boolean(ansprechpartner);

    return (
        <div className="flex flex-col gap-6" data-testid="anruf-kontakt-details">
            {(hatAnsprechpartner || hatAdresse) && (
                <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
                    {ansprechpartnerLaedt ? (
                        <Angabe symbol={UserRound} titel={ansprechpartnerTitel}>
                            <span aria-hidden="true" className="mt-1 block h-6 w-48 rounded bg-slate-200 motion-safe:animate-pulse" />
                            <span className="sr-only">Wird geladen …</span>
                        </Angabe>
                    ) : ansprechpartner ? (
                        <Angabe symbol={UserRound} titel={ansprechpartnerTitel}>
                            {ansprechpartner}
                        </Angabe>
                    ) : null}
                    {hatAdresse && (
                        <Angabe symbol={MapPin} titel="Adresse">
                            {daten?.strasse && <span className="block">{daten.strasse}</span>}
                            {ortZeile && <span className="block">{ortZeile}</span>}
                        </Angabe>
                    )}
                </dl>
            )}

            {zustand.status === 'fehler' && (
                <p role="alert" className="flex items-center gap-2 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-base text-rose-800">
                    <AlertCircle aria-hidden="true" className="h-5 w-5 shrink-0" />
                    {zustand.meldung}
                </p>
            )}

            {istKunde && zustand.status !== 'fehler' && (
                <div className="grid gap-6 lg:grid-cols-2">
                    <VorgangListe
                        titel="Projekte"
                        symbol={Briefcase}
                        laedt={laedt}
                        leer="Noch keine Projekte."
                        eintraege={(daten?.projekte ?? []).map((p) => projektEintrag(p))}
                        gesamt={daten?.projekteGesamt ?? 0}
                        onOeffnen={onOeffnen}
                    />
                    <VorgangListe
                        titel="Anfragen"
                        symbol={FileCheck}
                        laedt={laedt}
                        leer="Keine Anfragen."
                        eintraege={(daten?.anfragen ?? []).map((a) => anfrageEintrag(a))}
                        gesamt={daten?.anfragenGesamt ?? 0}
                        onOeffnen={onOeffnen}
                    />
                </div>
            )}
        </div>
    );
}

function Angabe({ symbol: Symbol, titel, children }: { symbol: ComponentType<{ className?: string }>; titel: string; children: ReactNode }) {
    return (
        <div className="flex min-w-0 items-start gap-3">
            <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                <Symbol aria-hidden="true" className="h-5 w-5" />
            </span>
            <div className="min-w-0">
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{titel}</dt>
                <dd className="break-words text-lg font-medium text-slate-900">{children}</dd>
            </div>
        </div>
    );
}

interface Eintrag {
    schluessel: string;
    pfad: string;
    titel: string;
    zusatz: string;
    abgeschlossen: boolean;
}

function projektEintrag(p: UeberblickProjekt): Eintrag {
    return {
        schluessel: `projekt-${p.id}`,
        pfad: projektPfad(p.id),
        titel: p.bauvorhaben?.trim() || 'Projekt ohne Bauvorhaben',
        zusatz: [p.auftragsnummer && `Auftrags-Nr. ${p.auftragsnummer}`, p.ort].filter(Boolean).join(' · '),
        abgeschlossen: p.abgeschlossen,
    };
}

function anfrageEintrag(a: UeberblickAnfrage): Eintrag {
    return {
        schluessel: `anfrage-${a.id}`,
        pfad: anfragePfad(a.id),
        titel: a.bauvorhaben?.trim() || 'Anfrage ohne Bauvorhaben',
        zusatz: [a.angebotsnummer ? `Angebots-Nr. ${a.angebotsnummer}` : 'Noch kein Angebot', a.ort].filter(Boolean).join(' · '),
        abgeschlossen: a.abgeschlossen,
    };
}

interface VorgangListeProps {
    titel: string;
    symbol: ComponentType<{ className?: string }>;
    laedt: boolean;
    leer: string;
    eintraege: Eintrag[];
    /** Alle Einträge, auch die nicht mitgelieferten (das Backend liefert höchstens 50). */
    gesamt: number;
    onOeffnen: (pfad: string) => void;
}

function VorgangListe({ titel, symbol: Symbol, laedt, leer, eintraege, gesamt, onOeffnen }: VorgangListeProps) {
    const weitere = Math.max(0, gesamt - eintraege.length);
    return (
        <section aria-label={titel} className="flex min-w-0 flex-col gap-2">
            <h3 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
                <Symbol aria-hidden="true" className="h-4 w-4" />
                {titel}
                {!laedt && <span className="tabular-nums text-slate-400">({Math.max(gesamt, eintraege.length)})</span>}
            </h3>
            {laedt ? (
                <div aria-hidden="true" className="flex flex-col gap-2">
                    <span className="block h-14 rounded-xl bg-slate-100 motion-safe:animate-pulse" />
                    <span className="block h-14 rounded-xl bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ) : eintraege.length === 0 ? (
                <p className="rounded-xl border border-dashed border-slate-200 px-4 py-3 text-base text-slate-500">{leer}</p>
            ) : (
                <ul className="flex max-h-64 flex-col gap-2 overflow-y-auto pr-1">
                    {eintraege.map((e) => (
                        <li key={e.schluessel}>
                            <button
                                type="button"
                                onClick={() => onOeffnen(e.pfad)}
                                className={cn(
                                    'flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition-colors hover:border-rose-300 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500',
                                    e.abgeschlossen ? 'border-slate-200 bg-slate-50' : 'border-slate-200 bg-white',
                                )}
                            >
                                <span className="min-w-0 flex-1">
                                    <span className={cn('block break-words text-base font-semibold', e.abgeschlossen ? 'text-slate-600' : 'text-slate-900')}>
                                        {e.titel}
                                    </span>
                                    <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-slate-500">
                                        {e.zusatz && <span className="tabular-nums">{e.zusatz}</span>}
                                        {e.abgeschlossen && (
                                            <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-medium text-slate-500">
                                                Abgeschlossen
                                            </span>
                                        )}
                                    </span>
                                </span>
                                <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-400" />
                            </button>
                        </li>
                    ))}
                    {weitere > 0 && (
                        <li className="px-1 py-1 text-sm text-slate-500">
                            … und {weitere} weitere – alle stehen in der Akte.
                        </li>
                    )}
                </ul>
            )}
        </section>
    );
}
