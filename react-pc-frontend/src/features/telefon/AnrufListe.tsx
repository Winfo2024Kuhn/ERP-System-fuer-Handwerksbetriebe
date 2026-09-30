import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2, PhoneOff, RefreshCw, Search, Voicemail, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Select } from '../../components/ui/select-custom';
import { cn } from '../../lib/utils';
import { ladeAnrufe } from './api';
import { ArtSymbol } from './ArtSymbol';
import { FilterChips } from './FilterChips';
import { TagFilter } from './TagFilter';
import { anrufbeantworterName, formatDauerMinuten, formatWann, tagAnzeige, tagAusAdresse } from './format';
import type { Anrufbeantworter, KontaktTyp, TelefonAnruf, ZuordnenZiel } from './types';
import { useZuordnen } from './useZuordnen';
import { WerAnzeige, ZuordnungsMenue } from './WerAnzeige';
import { ZurueckrufenKnopf } from './ZurueckrufenKnopf';

/**
 * Reiter „Anrufe": die Anrufliste der FRITZ!Box.
 *
 * <p>Filter stehen in der Adresse (`?art=VERPASST`, `?offen=1`, `?unbekannt=1`,
 * `?tag=2026-09-29`, `?kontakt=STEUERBERATER`),
 * damit der Link aus der Glocke direkt die offenen Rückrufe zeigt – genau die
 * Anrufe, die sie zählt. `?anruf=12` hebt einen
 * Anruf hervor und scrollt zu ihm. Es werden 50 Anrufe auf einmal geladen,
 * weitere per Knopf.</p>
 */

type Filter = 'alle' | 'verpasst' | 'offen' | 'unbekannt';

const SEITENGROESSE = 50;

const KONTAKTARTEN: { value: 'alle' | KontaktTyp; label: string }[] = [
    { value: 'alle', label: 'Alle Kontakte' },
    { value: 'KUNDE', label: 'Kunden' },
    { value: 'LIEFERANT', label: 'Lieferanten' },
    { value: 'STEUERBERATER', label: 'Steuerberater' },
];

/** Für Leer-Texte: „Keine Anrufe von Kunden/Lieferanten/Steuerberatern" (Dativ Plural). */
const VON_KONTAKTART: Record<KontaktTyp, string> = {
    KUNDE: 'von Kunden',
    LIEFERANT: 'von Lieferanten',
    STEUERBERATER: 'von Steuerberatern',
};

function kontaktartAusAdresse(params: URLSearchParams): KontaktTyp | null {
    const wert = params.get('kontakt');
    return wert === 'KUNDE' || wert === 'LIEFERANT' || wert === 'STEUERBERATER' ? wert : null;
}

const KONTAKTART_GESPERRT = 'Bei „Rückruf offen“ und „Unbekannt“ gibt es keine Auswahl nach Kontaktart.';

const SUCHE_GESPERRT = 'Bei „Rückruf offen“ stehen immer alle offenen Rückrufe – Suche dort nicht nötig.';

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
    const tag = filter === 'offen' ? '' : tagAusAdresse(params);
    // „Unbekannt" heißt: kein Kontakt – eine Kontaktart passt dort nicht.
    const kontaktartGesperrt = filter === 'offen' || filter === 'unbekannt';
    const kontaktart = kontaktartGesperrt ? null : kontaktartAusAdresse(params);
    const kontaktartGrundId = useId();
    const hervorgehobenId = Number(params.get('anruf')) || null;
    const sucheGrundId = useId();

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
                tag: tag || undefined,
                kontaktart: kontaktart ?? undefined,
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
    }, [filter, suche, tag, kontaktart]);

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

    const wechsleKontaktart = (neu: string) => {
        const naechste = new URLSearchParams(params);
        naechste.delete('anruf');
        if (neu === 'alle') naechste.delete('kontakt'); else naechste.set('kontakt', neu);
        setParams(naechste, { replace: true });
    };

    const wechsleTag = (neu: string) => {
        const naechste = new URLSearchParams(params);
        naechste.delete('anruf');
        if (neu) naechste.set('tag', neu); else naechste.delete('tag');
        setParams(naechste, { replace: true });
    };

    const ersetze = useCallback((ziel: ZuordnenZiel, neu: TelefonAnruf) => {
        setAnrufe((alt) => alt.map((a) => (a.id === ziel.id ? neu : a)));
    }, []);
    const zuordnen = useZuordnen<TelefonAnruf>(ersetze);

    const amTag = tag ? ` am ${tagAnzeige(tag)}` : '';
    const vonArt = kontaktart ? ` ${VON_KONTAKTART[kontaktart]}` : '';
    const leerText = suche && filter !== 'offen'
        ? `Keine Anrufe${vonArt} zu „${suche}“${amTag} gefunden.`
        : filter === 'verpasst' ? `Keine verpassten Anrufe${vonArt}${amTag}.`
            : filter === 'offen' ? 'Keine offenen Rückrufe – alles erledigt.'
                : filter === 'unbekannt' ? `Keine Anrufe von unbekannten Nummern${amTag}.`
                    : tag || kontaktart ? `Keine Anrufe${vonArt}${amTag}.` : 'Noch keine Anrufe abgeholt.';

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <FilterChips beschriftung="Anrufe filtern" chips={FILTER_CHIPS} aktiv={filter} onWechsel={wechsleFilter} />
                <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center lg:w-auto">
                    <div className="w-full sm:w-44" title={kontaktartGesperrt ? KONTAKTART_GESPERRT : undefined}>
                        <Select
                            options={KONTAKTARTEN}
                            value={kontaktart ?? 'alle'}
                            onChange={wechsleKontaktart}
                            disabled={kontaktartGesperrt}
                            aria-label="Kontaktart"
                            aria-describedby={kontaktartGesperrt ? kontaktartGrundId : undefined}
                        />
                        {kontaktartGesperrt && <span id={kontaktartGrundId} className="sr-only">{KONTAKTART_GESPERRT}</span>}
                    </div>
                    <TagFilter
                        tag={tag}
                        onWechsel={wechsleTag}
                        disabled={filter === 'offen'}
                        gesperrtGrund="Bei „Rückruf offen“ stehen immer alle offenen Rückrufe – ein Tag ist dort nicht nötig."
                    />
                    <div className="relative w-full lg:w-96">
                        <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            type="text"
                            value={sucheEingabe}
                            onChange={(e) => setSucheEingabe(e.target.value)}
                            placeholder="Name, Ort, Bauvorhaben, Nummer …"
                            aria-label="Anrufe durchsuchen"
                            disabled={filter === 'offen'}
                            aria-describedby={filter === 'offen' ? sucheGrundId : undefined}
                            title={filter === 'offen'
                                ? SUCHE_GESPERRT
                                : 'Sucht in allen Angaben von Kunden und Lieferanten: Vor- und Nachname, Ansprechpartner, Ort, Straße, Bauvorhaben, Auftragsnummer, Telefon. Mehrere Wörter grenzen weiter ein, z. B. „Max Würzburg“.'}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-9 text-sm text-slate-900 placeholder-slate-400 focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
                        />
                        {filter === 'offen' && <span id={sucheGrundId} className="sr-only">{SUCHE_GESPERRT}</span>}
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
                                                {anruf.nummer ? (
                                                    // -my-1.5: der Knopf ist höher als die Textzeile, die Zeile soll dadurch nicht wachsen.
                                                    <span className="-my-1.5 flex items-center gap-1">
                                                        <span>{anruf.nummer}</span>
                                                        <ZurueckrufenKnopf nummer={anruf.nummer} wer={wer} />
                                                    </span>
                                                ) : <span className="text-slate-400">–</span>}
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
