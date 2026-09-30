import { useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronRight, FolderOpen, Headset, Phone, PhoneCall, PhoneMissed, Voicemail, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DialogEbene } from '../../components/ui/dialog';
import { cn } from '../../lib/utils';
import { aktenPfad } from './api';
import { AnrufKontaktDetails } from './AnrufKontaktDetails';
import { formatLaufzeit } from './format';
import { KontaktKennzeichen } from './KontaktKennzeichen';
import type { KontaktKurz } from './types';
import type { UeberblickZustand } from './useKontaktUeberblick';
import type { LiveAnrufAnzeige } from './useTelefonLive';
import { ZurueckrufenKnopf } from './ZurueckrufenKnopf';

/**
 * Großes Anruf-Fenster über dem halben Bildschirm.
 *
 * <p>Auf einen Blick lesbar: wer ruft an, Kunde oder Lieferant, Nummer,
 * Ansprechpartner und Adresse. Bei Kunden stehen darunter alle Projekte und
 * Anfragen – ein Klick springt direkt hinein. Ein Knopf öffnet die Akte,
 * einer schließt. Steuerberater haben keine eigene Akte, dort gibt es nur
 * „Schließen".</p>
 *
 * <p><strong>Stiehlt bewusst keinen Tastatur-Fokus.</strong> Wer gerade im
 * Hintergrund tippt (z. B. im Dokumenteditor), tippt einfach weiter – ohne
 * dass ein Leerzeichen oder Enter versehentlich „Akte öffnen" auslöst.
 * Deshalb keine Fokusfalle, kein Autofokus und `aria-modal="false"`.
 * Escape oder ein Klick auf den abgedunkelten Hintergrund schließen.</p>
 *
 * <p>Beim Klingeln steht ein Hinweis, wo man annimmt (Headset oder
 * Telefon-Programm – im ERP selbst geht das nicht). Nach einem verpassten
 * Anruf ist „Zurückrufen" die Hauptaktion. Zeigt man mit der Maus auf das
 * verpasste Fenster oder ruft zurück, bleibt es offen (`onFesthalten`), statt
 * nach drei Sekunden zu verschwinden.</p>
 */

/** Ebene des Anruf-Fensters – einzige Quelle; dort geöffnete Dialoge liegen darüber (siehe DialogEbene). */
const FENSTER_Z_INDEX = 70;

interface AnrufFensterProps {
    anruf: LiveAnrufAnzeige;
    /** Wie viele Anrufe außer diesem gerade noch laufen. */
    weitere: number;
    onSchliessen: () => void;
    onKontaktOeffnen: (kontakt: KontaktKurz) => void;
    /** Überblick zum Anrufer; null, solange keiner zugeordnet ist. */
    ueberblick: UeberblickZustand | null;
    /** Springt in ein Projekt oder eine Anfrage. */
    onOeffnen: (pfad: string) => void;
    /** Verpassten Anruf offen halten, statt ihn gleich auszublenden. */
    onFesthalten?: () => void;
}

function statusText(anruf: LiveAnrufAnzeige, laufzeit: string): string {
    if (anruf.verpasst) return 'Verpasst';
    switch (anruf.status) {
        case 'IM_GESPRAECH': return `Im Gespräch · ${laufzeit}`;
        case 'ANRUFBEANTWORTER': return 'Anrufbeantworter nimmt auf';
        case 'BEENDET': return 'Aufgelegt';
        default: return 'ruft an';
    }
}

function useLaufendeSekunden(seit: number | null): number {
    const [jetzt, setJetzt] = useState(() => Date.now());
    useEffect(() => {
        if (seit === null) return;
        const intervall = window.setInterval(() => setJetzt(Date.now()), 1000);
        return () => window.clearInterval(intervall);
    }, [seit]);
    return seit === null ? 0 : Math.max(0, (jetzt - seit) / 1000);
}

export function AnrufFenster({ anruf, weitere, onSchliessen, onKontaktOeffnen, ueberblick, onOeffnen, onFesthalten }: AnrufFensterProps) {
    const titelId = useId();
    const laufzeit = formatLaufzeit(useLaufendeSekunden(anruf.status === 'IM_GESPRAECH' ? anruf.gespraechSeit : null));

    useEffect(() => {
        const beiTaste = (ereignis: KeyboardEvent) => {
            if (ereignis.key !== 'Escape' || ereignis.defaultPrevented) return;
            // Escape in einem Dialog (z. B. Telefon-Auswahl beim Zurückrufen) schließt nur diesen.
            const ziel = ereignis.target instanceof Element ? ereignis.target : null;
            if (ziel?.closest('[role="dialog"][aria-modal="true"]')) return;
            onSchliessen();
        };
        document.addEventListener('keydown', beiTaste);
        return () => document.removeEventListener('keydown', beiTaste);
    }, [onSchliessen]);

    const kontakt = anruf.kontakt;
    const hatAkte = (k: KontaktKurz) => aktenPfad(k.typ, k.id) !== null;
    const mehrdeutig = !kontakt && anruf.kandidaten.length > 1;
    const unterdrueckt = !anruf.nummer.trim();
    const klingelt = !anruf.verpasst && anruf.status === 'KLINGELT';
    const imGespraech = !anruf.verpasst && anruf.status === 'IM_GESPRAECH';
    const ab = !anruf.verpasst && anruf.status === 'ANRUFBEANTWORTER';
    const zurueckrufen = anruf.verpasst && !unterdrueckt;
    const festhalten = anruf.verpasst ? onFesthalten : undefined;
    const StatusSymbol = anruf.verpasst ? PhoneMissed : ab ? Voicemail : imGespraech ? PhoneCall : Phone;

    const ueberschrift = kontakt
        ? kontakt.name
        : mehrdeutig
            ? 'Einer dieser Kontakte'
            : unterdrueckt ? 'Nummer unterdrückt' : 'Unbekannte Nummer';

    return createPortal(
        <div className="fixed inset-0 flex items-center justify-center p-4" style={{ zIndex: FENSTER_Z_INDEX }} data-testid="anruf-fenster">
            {/* Hintergrund: Klick schließt. Kein Knopf, damit er nie Fokus bekommt. */}
            <div
                aria-hidden="true"
                data-testid="anruf-fenster-hintergrund"
                className="absolute inset-0 bg-slate-900/50 motion-safe:[animation:fadeIn_0.2s_ease-out]"
                onClick={onSchliessen}
            />
            <section
                role="dialog"
                aria-modal="false"
                aria-labelledby={titelId}
                onPointerEnter={festhalten}
                onFocus={festhalten}
                className="relative flex max-h-[92vh] w-full max-w-[1100px] min-h-[50vh] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl md:w-[80vw] xl:w-[55vw] motion-safe:[animation:scaleIn_0.25s_cubic-bezier(0.32,0.72,0,1)]"
            >
                {/* Statusleiste */}
                <div
                    className={cn(
                        'flex items-center gap-4 px-8 py-5',
                        anruf.verpasst ? 'bg-red-50' : imGespraech ? 'bg-emerald-50' : ab ? 'bg-amber-50' : 'bg-rose-50',
                    )}
                >
                    <span className="relative flex h-14 w-14 shrink-0 items-center justify-center">
                        {klingelt && (
                            <span aria-hidden="true" className="absolute -inset-1.5 rounded-full bg-rose-200 motion-safe:animate-pulse" />
                        )}
                        <span
                            className={cn(
                                'relative flex h-14 w-14 items-center justify-center rounded-full text-white',
                                anruf.verpasst ? 'bg-red-600' : imGespraech ? 'bg-emerald-600' : ab ? 'bg-amber-500' : 'bg-rose-600',
                            )}
                        >
                            <StatusSymbol aria-hidden="true" className="h-7 w-7" />
                        </span>
                    </span>
                    <div className="min-w-0">
                        <p
                            role="status"
                            aria-live="polite"
                            className={cn(
                                'text-2xl font-semibold tabular-nums',
                                anruf.verpasst ? 'text-red-700' : imGespraech ? 'text-emerald-800' : ab ? 'text-amber-800' : 'text-rose-700',
                            )}
                        >
                            {statusText(anruf, laufzeit)}
                        </p>
                        {klingelt && (
                            <p className="mt-1 flex items-center gap-1.5 text-sm text-slate-600">
                                <Headset aria-hidden="true" className="h-4 w-4 shrink-0" />
                                Am Headset oder im Telefon-Programm annehmen
                            </p>
                        )}
                    </div>
                </div>

                {/* Wer ruft an */}
                {/* Zentriert per my-auto statt justify-center: wird der Inhalt höher als
                    das Fenster, bleibt der Name oben erreichbar und nichts wird abgeschnitten. */}
                <div className="flex min-h-0 flex-1 flex-col overflow-y-auto px-8 py-8">
                    <div className="my-auto flex flex-col gap-4">
                        <h2 id={titelId} className="break-words text-4xl font-bold leading-tight text-slate-900 lg:text-5xl">
                            {ueberschrift}
                        </h2>
                        {kontakt && (
                            <div className="flex flex-wrap items-center gap-3 text-lg text-slate-600">
                                <KontaktKennzeichen typ={kontakt.typ} gross />
                                {kontakt.nummer && <span>Kunden-Nr. {kontakt.nummer}</span>}
                            </div>
                        )}
                        {!unterdrueckt && (
                            <p className="text-3xl font-semibold tabular-nums tracking-wide text-slate-700">{anruf.nummer}</p>
                        )}
                        {kontakt && (
                            <div className="mt-4 border-t border-slate-100 pt-6">
                                <AnrufKontaktDetails
                                    kontakt={kontakt}
                                    zustand={ueberblick ?? { status: 'laedt' }}
                                    onOeffnen={onOeffnen}
                                />
                            </div>
                        )}
                        {mehrdeutig && (
                            <ul className="mt-2 grid gap-2 sm:grid-cols-2" aria-label="Mögliche Kontakte">
                                {anruf.kandidaten.map((k) => {
                                    const inhalt = (
                                        <span className="min-w-0 flex-1">
                                            <span className="block break-words text-lg font-semibold text-slate-900">{k.name}</span>
                                            <span className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-500">
                                                <KontaktKennzeichen typ={k.typ} />
                                                {k.ort && <span>{k.ort}</span>}
                                            </span>
                                        </span>
                                    );
                                    return (
                                        <li key={`${k.typ}-${k.id}`}>
                                            {hatAkte(k) ? (
                                                <button
                                                    type="button"
                                                    onClick={() => onKontaktOeffnen(k)}
                                                    className="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-left transition-colors hover:border-rose-300 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                                >
                                                    {inhalt}
                                                    <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-400" />
                                                </button>
                                            ) : (
                                                <div className="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                                                    {inhalt}
                                                </div>
                                            )}
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                </div>

                {/* Knöpfe – normal per Tab erreichbar, aber nie automatisch fokussiert. */}
                <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-100 bg-slate-50 px-8 py-5">
                    {weitere > 0 && (
                        <p className="mr-auto text-base font-medium text-slate-600">
                            +{weitere} {weitere === 1 ? 'weiterer Anruf' : 'weitere Anrufe'}
                        </p>
                    )}
                    <Button type="button" variant="outline" onClick={onSchliessen} className="px-5 py-3 text-base">
                        <X aria-hidden="true" className="h-5 w-5" />
                        Schließen
                    </Button>
                    {kontakt && hatAkte(kontakt) && (
                        <Button
                            type="button"
                            variant={zurueckrufen ? 'outline' : 'default'}
                            onClick={() => onKontaktOeffnen(kontakt)}
                            className="px-5 py-3 text-base"
                        >
                            <FolderOpen aria-hidden="true" className="h-5 w-5" />
                            Akte öffnen
                        </Button>
                    )}
                    {zurueckrufen && (
                        <DialogEbene ueberZIndex={FENSTER_Z_INDEX}>
                            <ZurueckrufenKnopf
                                nummer={anruf.nummer}
                                wer={kontakt?.name || anruf.nummer}
                                gross
                                onStart={onFesthalten}
                                onGestartet={onSchliessen}
                            />
                        </DialogEbene>
                    )}
                </div>
            </section>
        </div>,
        document.body,
    );
}
