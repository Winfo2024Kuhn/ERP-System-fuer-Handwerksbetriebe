import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { RefreshCw, Voicemail } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { ladeSprachnachrichten } from './api';
import { FilterChips } from './FilterChips';
import { TagFilter } from './TagFilter';
import { anrufbeantworterName, tagAnzeige, tagAusAdresse } from './format';
import { SprachnachrichtEintrag } from './SprachnachrichtEintrag';
import type { Anrufbeantworter, Sprachnachricht, ZuordnenZiel } from './types';
import { useZuordnen } from './useZuordnen';

/**
 * Reiter „Anrufbeantworter": alle Nachrichten, neueste zuerst.
 *
 * <p>Ein Chip pro Anrufbeantworter mit dessen Namen aus der FRITZ!Box
 * (z. B. „AB Tag", „AB Nacht"), dazu ein Tagesfilter (`?tag=2026-09-29`).
 * `?nachricht=12` hebt eine Nachricht hervor
 * und scrollt zu ihr – so landet ein Klick in der Glocke oder in der
 * Anrufliste direkt beim Abspieler.</p>
 */

interface AnrufbeantworterListeProps {
    anrufbeantworter: Anrufbeantworter[];
    aktualisierung: number;
}

export function AnrufbeantworterListe({ anrufbeantworter, aktualisierung }: AnrufbeantworterListeProps) {
    const [params, setParams] = useSearchParams();
    const abParam = params.get('ab');
    const abFilter = abParam !== null && abParam !== '' && Number.isInteger(Number(abParam)) ? Number(abParam) : null;
    const hervorgehobenId = Number(params.get('nachricht')) || null;
    const tag = tagAusAdresse(params);

    const [nachrichten, setNachrichten] = useState<Sprachnachricht[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);
    const [neuLaden, setNeuLaden] = useState(0);
    const gescrollt = useRef<number | null>(null);
    const eintraegeRef = useRef(new Map<number, HTMLLIElement>());

    const anfrageRef = useRef(0);
    const lade = useCallback(async () => {
        const nummer = ++anfrageRef.current;
        setLaedt(true);
        setFehler(null);
        try {
            const liste = await ladeSprachnachrichten({ anrufbeantworter: abFilter, tag: tag || undefined });
            if (nummer === anfrageRef.current) setNachrichten(liste);
        } catch (e) {
            if (nummer === anfrageRef.current) {
                setFehler(e instanceof Error ? e.message : 'Die Nachrichten konnten nicht geladen werden.');
            }
        } finally {
            if (nummer === anfrageRef.current) setLaedt(false);
        }
    }, [abFilter, tag]);

    useEffect(() => {
        void lade();
    }, [lade, aktualisierung, neuLaden]);

    useEffect(() => {
        if (!hervorgehobenId || gescrollt.current === hervorgehobenId) return;
        const eintrag = eintraegeRef.current.get(hervorgehobenId);
        if (!eintrag) return;
        gescrollt.current = hervorgehobenId;
        eintrag.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    }, [nachrichten, hervorgehobenId]);

    const wechsleAb = (wert: string) => {
        const naechste = new URLSearchParams(params);
        naechste.delete('nachricht');
        if (wert === 'alle') naechste.delete('ab'); else naechste.set('ab', wert);
        setParams(naechste, { replace: true });
    };

    const wechsleTag = (neu: string) => {
        const naechste = new URLSearchParams(params);
        naechste.delete('nachricht');
        if (neu) naechste.set('tag', neu); else naechste.delete('tag');
        setParams(naechste, { replace: true });
    };

    const ersetze = useCallback((neu: Sprachnachricht) => {
        setNachrichten((alt) => alt.map((n) => (n.id === neu.id ? neu : n)));
    }, []);
    const zuordnen = useZuordnen<Sprachnachricht>(useCallback((_ziel: ZuordnenZiel, neu: Sprachnachricht) => ersetze(neu), [ersetze]));

    const chips = [
        { wert: 'alle', text: 'Alle' },
        ...anrufbeantworter.map((ab) => ({ wert: String(ab.index), text: anrufbeantworterName(anrufbeantworter, ab.index) })),
    ];

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                {anrufbeantworter.length > 0 && (
                    <FilterChips beschriftung="Anrufbeantworter wählen" chips={chips} aktiv={abFilter === null ? 'alle' : String(abFilter)} onWechsel={wechsleAb} />
                )}
                <TagFilter tag={tag} onWechsel={wechsleTag} className="sm:ml-auto" />
            </div>

            {fehler ? (
                <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border border-slate-200 bg-white p-10 text-center shadow-sm">
                    <p className="text-rose-800">{fehler}</p>
                    <Button variant="outline" size="sm" onClick={() => setNeuLaden((n) => n + 1)}>
                        <RefreshCw aria-hidden="true" className="h-4 w-4" />
                        Erneut laden
                    </Button>
                </div>
            ) : laedt && nachrichten.length === 0 ? (
                <div role="status" aria-label="Nachrichten werden geladen" className="space-y-3">
                    {Array.from({ length: 3 }, (_, i) => (
                        <div key={i} className="h-24 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                            <div className="h-4 w-1/3 rounded bg-slate-200 motion-safe:animate-pulse" />
                            <div className="mt-3 h-3 w-1/2 rounded bg-slate-100 motion-safe:animate-pulse" />
                        </div>
                    ))}
                </div>
            ) : nachrichten.length === 0 ? (
                <div className="flex flex-col items-center gap-2 rounded-lg border border-slate-200 bg-white p-12 text-center text-slate-500 shadow-sm">
                    <Voicemail aria-hidden="true" className="h-10 w-10 text-slate-300" />
                    <p className="font-medium text-slate-600">
                        {tag ? `Keine Nachrichten am ${tagAnzeige(tag)}.` : 'Keine Nachrichten auf dem Anrufbeantworter.'}
                    </p>
                </div>
            ) : (
                <ul className="space-y-3" aria-label="Nachrichten auf dem Anrufbeantworter" aria-busy={laedt}>
                    {nachrichten.map((nachricht) => (
                        <SprachnachrichtEintrag
                            key={nachricht.id}
                            ref={(el) => {
                                if (el) eintraegeRef.current.set(nachricht.id, el);
                                else eintraegeRef.current.delete(nachricht.id);
                            }}
                            nachricht={nachricht}
                            anrufbeantworterName={anrufbeantworterName(anrufbeantworter, nachricht.anrufbeantworter)}
                            hervorgehoben={nachricht.id === hervorgehobenId}
                            onGeaendert={ersetze}
                            zuordnen={zuordnen}
                        />
                    ))}
                </ul>
            )}

            {zuordnen.dialogElement}
        </div>
    );
}
