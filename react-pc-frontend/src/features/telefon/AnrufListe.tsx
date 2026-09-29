import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2, PhoneOff, RefreshCw, Search, Voicemail, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';
import { ladeAnrufe } from './api';
import { ArtSymbol } from './ArtSymbol';
import { FilterChips } from './FilterChips';
import { anrufbeantworterName, formatDauerMinuten, formatWann } from './format';
import type { Anrufbeantworter, TelefonAnruf, ZuordnenZiel } from './types';
import { useZuordnen } from './useZuordnen';
import { WerAnzeige, ZuordnungsMenue } from './WerAnzeige';

/**
 * Reiter „Anrufe": die Anrufliste der FRITZ!Box.
 *
 * <p>Filter stehen in der Adresse (`?art=VERPASST`, `?offen=1`, `?unbekannt=1`),
 * damit der Link aus der Glocke direkt die offenen Rückrufe zeigt – genau die
 * Anrufe, die sie zählt. `?anruf=12` hebt einen
 * Anruf hervor und scrollt zu ihm. Es werden 50 Anrufe auf einmal geladen,
 * weitere per Knopf.</p>
 */

type Filter = 'alle' | 'verpasst' | 'offen' | 'unbekannt';

const SEITENGROESSE = 50;

const FILTER_CHIPS: { wert: Filter; text: string }[] = [
    { wert: 'alle', text: 'Alle' },
    { wert: 'verpasst', text: 'Verpasst' },
    { wert: 'offen', text: 'Rückruf offen' },
    { wert: 'unbekannt', text: 'Unbekannt' },
];

function filterAusAdresse(params: URLSearchParams): Filter {
    if (params.get('art') === 'VERPASST') return 'verpasst';
    if (params.get('offen') === '1') return 'offen';
    if (params.get('unbekannt') === '1') return 'unbekannt';
    return 'alle';
}

interface AnrufListeProps {
    anrufbeantworter: Anrufbeantworter[];
    /** Wird nach „Jetzt abholen“ hochgezählt – dann lädt die Liste neu. */
    aktualisierung: number;
}

export function AnrufListe({ anrufbeantworter, aktualisierung }: AnrufListeProps) {
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const filter = filterAusAdresse(params);
    const hervorgehobenId = Number(params.get('anruf')) || null;

    const [sucheEingabe, setSucheEingabe] = useState('');
    const [suche, setSuche] = useState('');
    const [anrufe, setAnrufe] = useState<TelefonAnruf[]>([]);
    const [gesamt, setGesamt] = useState(0);
    const [seiten, setSeiten] = useState(0);
    const [seite, setSeite] = useState(0);
    const [laedt, setLaedt] = useState(true);
    const [laedtMehr, setLaedtMehr] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [neuLaden, setNeuLaden] = useState(0);
    const anfrageRef = useRef(0);
    const gescrollt = useRef<number | null>(null);
    const zeilenRef = useRef(new Map<number, HTMLTableRowElement>());

    // Suche erst nach einer kurzen Tipp-Pause abschicken.
    useEffect(() => {
        const timer = window.setTimeout(() => setSuche(sucheEingabe.trim()), 300);
        return () => window.clearTimeout(timer);
    }, [sucheEingabe]);

    const lade = useCallback(async (zielSeite: number, anhaengen: boolean) => {
        const nummer = ++anfrageRef.current;
        if (anhaengen) setLaedtMehr(true); else setLaedt(true);
        setFehler(null);
        try {
            const ergebnis = await ladeAnrufe({
                art: filter === 'verpasst' ? 'VERPASST' : undefined,
                nurUnbekannt: filter === 'unbekannt',
                nurOffen: filter === 'offen',
                suche: filter === 'offen' ? undefined : suche,
                seite: zielSeite,
                groesse: SEITENGROESSE,
            });
            if (nummer !== anfrageRef.current) return;
            setAnrufe((alt) => (anhaengen ? [...alt, ...ergebnis.inhalt] : ergebnis.inhalt));
            setGesamt(ergebnis.gesamt);
            setSeiten(ergebnis.seiten);
            setSeite(zielSeite);
        } catch (e) {
            if (nummer !== anfrageRef.current) return;
            setFehler(e instanceof Error ? e.message : 'Die Anrufe konnten nicht geladen werden.');
        } finally {
            if (nummer === anfrageRef.current) {
                setLaedt(false);
                setLaedtMehr(false);
            }
        }
    }, [filter, suche]);

    useEffect(() => {
        void lade(0, false);
    }, [lade, aktualisierung, neuLaden]);

    // Hervorgehobenen Anruf einmal in den Blick holen.
    useEffect(() => {
        if (!hervorgehobenId || gescrollt.current === hervorgehobenId) return;
        const zeile = zeilenRef.current.get(hervorgehobenId);
        if (!zeile) return;
        gescrollt.current = hervorgehobenId;
        zeile.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    }, [anrufe, hervorgehobenId]);

    const wechsleFilter = (neu: Filter) => {
        const naechste = new URLSearchParams(params);
        naechste.delete('art');
        naechste.delete('unbekannt');
        naechste.delete('offen');
        naechste.delete('anruf');
        if (neu === 'verpasst') naechste.set('art', 'VERPASST');
        if (neu === 'offen') naechste.set('offen', '1');
        if (neu === 'unbekannt') naechste.set('unbekannt', '1');
        setParams(naechste, { replace: true });
    };

    const ersetze = useCallback((ziel: ZuordnenZiel, neu: TelefonAnruf) => {
        setAnrufe((alt) => alt.map((a) => (a.id === ziel.id ? neu : a)));
    }, []);
    const zuordnen = useZuordnen<TelefonAnruf>(ersetze);

    const leerText = suche && filter !== 'offen'
        ? `Keine Anrufe zu „${suche}“ gefunden.`
        : filter === 'verpasst' ? 'Keine verpassten Anrufe.'
            : filter === 'offen' ? 'Keine offenen Rückrufe – alles erledigt.'
                : filter === 'unbekannt' ? 'Keine Anrufe von unbekannten Nummern.'
                    : 'Noch keine Anrufe abgeholt.';

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <FilterChips beschriftung="Anrufe filtern" chips={FILTER_CHIPS} aktiv={filter} onWechsel={wechsleFilter} />
                <div className="relative w-full lg:w-80">
                    <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                        type="text"
                        value={sucheEingabe}
                        onChange={(e) => setSucheEingabe(e.target.value)}
                        placeholder="Name oder Nummer suchen …"
                        aria-label="Anrufe nach Name oder Nummer durchsuchen"
                        disabled={filter === 'offen'}
                        title={filter === 'offen' ? 'Bei „Rückruf offen“ stehen immer alle offenen Rückrufe – Suche dort nicht nötig.' : undefined}
                        className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-9 text-sm text-slate-900 placeholder-slate-400 focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
                    />
                    {sucheEingabe && (
                        <button
                            type="button"
                            onClick={() => setSucheEingabe('')}
                            aria-label="Suche leeren"
                            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-600 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        >
                            <X aria-hidden="true" className="h-4 w-4" />
                        </button>
                    )}
                </div>
            </div>

            <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
                {fehler ? (
                    <div role="alert" className="flex flex-col items-center gap-3 p-10 text-center">
                        <p className="text-rose-800">{fehler}</p>
                        <Button variant="outline" size="sm" onClick={() => setNeuLaden((n) => n + 1)}>
                            <RefreshCw aria-hidden="true" className="h-4 w-4" />
                            Erneut laden
                        </Button>
                    </div>
                ) : laedt && anrufe.length === 0 ? (
                    <div role="status" aria-label="Anrufe werden geladen" className="divide-y divide-slate-100">
                        {Array.from({ length: 5 }, (_, i) => (
                            <div key={i} className="flex items-center gap-4 px-4 py-4">
                                <div className="h-8 w-8 rounded-full bg-slate-200 motion-safe:animate-pulse" />
                                <div className="h-4 flex-1 rounded bg-slate-200 motion-safe:animate-pulse" />
                                <div className="h-4 w-24 rounded bg-slate-100 motion-safe:animate-pulse" />
                            </div>
                        ))}
                    </div>
                ) : anrufe.length === 0 ? (
                    <div className="flex flex-col items-center gap-2 p-12 text-center text-slate-500">
                        <PhoneOff aria-hidden="true" className="h-10 w-10 text-slate-300" />
                        <p className="font-medium text-slate-600">{leerText}</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className={cn('w-full text-left text-sm', laedt && 'opacity-60')} aria-busy={laedt}>
                            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <tr>
                                    <th scope="col" className="px-4 py-3">Art</th>
                                    <th scope="col" className="px-4 py-3">Wer</th>
                                    <th scope="col" className="px-4 py-3">Nummer</th>
                                    <th scope="col" className="px-4 py-3">Wann</th>
                                    <th scope="col" className="px-4 py-3">Dauer</th>
                                    <th scope="col" className="px-2 py-3"><span className="sr-only">Nachricht</span></th>
                                    <th scope="col" className="px-2 py-3"><span className="sr-only">Aktionen</span></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {anrufe.map((anruf) => {
                                    const ziel: ZuordnenZiel = { art: 'anruf', id: anruf.id };
                                    const hervorgehoben = anruf.id === hervorgehobenId;
                                    const wer = anruf.kontakt?.name || anruf.nameFritzbox || anruf.nummer || 'Unbekannt';
                                    return (
                                        <tr
                                            key={anruf.id}
                                            ref={(el) => {
                                                if (el) zeilenRef.current.set(anruf.id, el);
                                                else zeilenRef.current.delete(anruf.id);
                                            }}
                                            data-hervorgehoben={hervorgehoben || undefined}
                                            className={cn('align-top transition-colors hover:bg-slate-50', hervorgehoben && 'bg-rose-50 hover:bg-rose-50')}
                                        >
                                            <td className="px-4 py-3">
                                                <ArtSymbol
                                                    art={anruf.art}
                                                    zusatz={anruf.art === 'ANRUFBEANTWORTER' ? anrufbeantworterName(anrufbeantworter, anruf.anrufbeantworter) : undefined}
                                                />
                                            </td>
                                            <td className="px-4 py-3 min-w-[14rem]">
                                                <WerAnzeige
                                                    eintrag={anruf}
                                                    beschaeftigt={zuordnen.istBeschaeftigt(ziel)}
                                                    onZuordnen={() => zuordnen.oeffneDialog(ziel, anruf.nummer)}
                                                    onKandidatWaehlen={(k) => zuordnen.kandidatWaehlen(ziel, k)}
                                                />
                                            </td>
                                            <td className="px-4 py-3 whitespace-nowrap tabular-nums text-slate-700">
                                                {anruf.nummer || <span className="text-slate-400">–</span>}
                                            </td>
                                            <td className="px-4 py-3 whitespace-nowrap text-slate-700">{formatWann(anruf.zeitpunkt)}</td>
                                            <td className="px-4 py-3 whitespace-nowrap tabular-nums text-slate-700">{formatDauerMinuten(anruf.dauerMinuten)}</td>
                                            <td className="px-2 py-3">
                                                {anruf.sprachnachrichtId && (
                                                    <button
                                                        type="button"
                                                        onClick={() => navigate(`/telefon/anrufbeantworter?nachricht=${anruf.sprachnachrichtId}`)}
                                                        aria-label={`Nachricht von ${wer} anhören`}
                                                        title="Nachricht anhören"
                                                        className="flex h-8 w-8 items-center justify-center rounded-lg text-amber-600 transition-colors hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                                    >
                                                        <Voicemail aria-hidden="true" className="h-4 w-4" />
                                                    </button>
                                                )}
                                            </td>
                                            <td className="px-2 py-3">
                                                <ZuordnungsMenue
                                                    beschriftung={`Anruf von ${wer}`}
                                                    zugeordnet={!!anruf.kontakt}
                                                    beschaeftigt={zuordnen.istBeschaeftigt(ziel)}
                                                    onAndererKontakt={() => zuordnen.oeffneDialog(ziel, anruf.nummer)}
                                                    onAufheben={() => zuordnen.aufheben(ziel)}
                                                />
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {!fehler && anrufe.length > 0 && (
                <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
                    <span>{anrufe.length} von {gesamt} Anrufen</span>
                    {seite + 1 < seiten && (
                        <Button variant="outline" size="sm" onClick={() => void lade(seite + 1, true)} disabled={laedtMehr}>
                            {laedtMehr && <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />}
                            Weitere Anrufe laden
                        </Button>
                    )}
                </div>
            )}

            {zuordnen.dialogElement}
        </div>
    );
}
