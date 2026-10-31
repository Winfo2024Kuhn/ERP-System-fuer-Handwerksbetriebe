import { useId, useState } from 'react';
import { Check, Loader2, Lock, RotateCcw } from 'lucide-react';
import { Button } from '../../../ui/button';
import { useToast } from '../../../ui/toast';
import { SegmentAuswahl } from '../../../ui/segment-auswahl';
import type { SegmentOption } from '../../../ui/segment-auswahl';
import { anbieterName, grundNichtMoeglich, speichereKiFunktion } from '../../../../features/ki/kiEinstellungenApi';
import type { KiEinstellungen, KiFunktion } from '../../../../features/ki/kiEinstellungenApi';
import type { KiModellListeZustand } from '../../../../features/ki/useKiModellListe';
import { KiModellSuche } from './KiModellSuche';
import { KiFunktionWahlInfo } from './kiInfos';

/** Wert der Segment-Auswahl für „folgt dem Standard-Anbieter“. */
const STANDARD = '';

interface KiFunktionWahlProps {
    id: string;
    funktion: KiFunktion;
    einstellungen: KiEinstellungen;
    modellListe: KiModellListeZustand;
    onAenderung: (stand: KiEinstellungen) => void;
    onSchliessen: () => void;
}

/**
 * „Ändern“ einer Funktion: Wer übernimmt sie (Standard oder ein bestimmter
 * Anbieter) und mit welchem Modell?
 *
 * <p>Wenige Anbieter → Segmente statt Klapp-Menü; viele Modelle → Suche.
 * Anbieter, die die Funktion nicht können, bleiben sichtbar, sind aber
 * gesperrt und sagen warum. Gespeichert wird erst mit „Übernehmen“, weil
 * Anbieter und Modell zusammengehören.</p>
 */
export function KiFunktionWahl({ id, funktion, einstellungen, modellListe, onAenderung, onSchliessen }: KiFunktionWahlProps) {
    const toast = useToast();
    const titelId = useId();
    const modellId = useId();
    const [anbieter, setAnbieter] = useState<string>(funktion.eigeneWahl?.anbieter ?? STANDARD);
    const [modell, setModell] = useState<string>(funktion.eigeneWahl?.modell ?? '');
    const [speichert, setSpeichert] = useState<'uebernehmen' | 'zuruecksetzen' | null>(null);

    const standardName = anbieterName(einstellungen, einstellungen.anbieter);
    const optionen: SegmentOption[] = [
        { wert: STANDARD, text: `Standard (${standardName})`, zusatz: 'folgt der Wahl oben' },
        ...einstellungen.anbieterListe.map((a) => ({
            wert: a.typ,
            text: a.name,
            zusatz: a.schluesselGesetzt ? undefined : 'noch kein Schlüssel',
            gesperrt: grundNichtMoeglich(a, funktion),
        })),
    ];
    const gesperrteGruende = optionen.map((o) => o.gesperrt).filter((g): g is string => !!g);
    const gewaehlterAnbieter = einstellungen.anbieterListe.find((a) => a.typ === anbieter);

    const vorher = { anbieter: funktion.eigeneWahl?.anbieter ?? STANDARD, modell: funktion.eigeneWahl?.modell ?? '' };
    const geaendert = anbieter !== vorher.anbieter || (anbieter !== STANDARD && modell.trim() !== vorher.modell);

    // Ist dieser Anbieter gerade wirksam und ohne eigenes Modell, kennen wir sein Standard-Modell für die Funktion.
    const standardModell = funktion.anbieter === anbieter && !funktion.eigeneWahl?.modell ? funktion.modell : null;
    const standardText = standardModell
        ? `Standard-Modell (${standardModell})`
        : `Standard-Modell von ${gewaehlterAnbieter?.name ?? anbieter}`;

    const senden = (art: 'uebernehmen' | 'zuruecksetzen') => {
        const neuerAnbieter = art === 'zuruecksetzen' || anbieter === STANDARD ? null : anbieter;
        setSpeichert(art);
        speichereKiFunktion(funktion.funktion, neuerAnbieter, neuerAnbieter ? modell : null)
            .then((stand) => {
                const neu = stand.funktionen.find((f) => f.funktion === funktion.funktion);
                const wohin = neu?.anbieterName
                    ? `${neu.anbieterName}${neu.modell ? ` · ${neu.modell}` : ''}`
                    : 'keinen Anbieter – die Funktion ist aus';
                toast.success(`„${funktion.name}“ läuft jetzt über ${wohin}.`);
                onAenderung(stand);
                onSchliessen();
            })
            .catch((e: unknown) => toast.error(e instanceof Error ? e.message : 'Die Auswahl für diese Funktion konnte nicht gespeichert werden.'))
            .finally(() => setSpeichert(null));
    };

    return (
        <div id={id} className="mt-2 rounded-lg border border-slate-200 bg-slate-50 p-3 sm:col-span-2" role="group" aria-labelledby={titelId}>
            <div className="flex items-center gap-1">
                <p id={titelId} className="text-sm font-medium text-slate-800">Wer übernimmt „{funktion.name}“?</p>
                <KiFunktionWahlInfo />
            </div>
            <SegmentAuswahl
                className="mt-2"
                beschriftung={`Anbieter für „${funktion.name}“`}
                optionen={optionen}
                wert={anbieter}
                onChange={(neu) => {
                    setAnbieter(neu);
                    setModell('');
                }}
            />
            {gesperrteGruende.length > 0 && (
                <ul className="mt-1.5 space-y-0.5 text-xs text-slate-500">
                    {gesperrteGruende.map((g) => (
                        <li key={g} className="flex items-start gap-1">
                            <Lock aria-hidden="true" className="mt-0.5 h-3 w-3 shrink-0" />
                            {g}
                        </li>
                    ))}
                </ul>
            )}

            {gewaehlterAnbieter && (
                <div className="mt-3 max-w-md">
                    <label htmlFor={modellId} className="mb-1.5 block text-sm text-slate-800">
                        Modell von {gewaehlterAnbieter.name}
                    </label>
                    <KiModellSuche
                        id={modellId}
                        anbieter={gewaehlterAnbieter}
                        wert={modell}
                        onChange={setModell}
                        standardText={standardText}
                        modellListe={modellListe}
                        braucht={funktion.braucht}
                    />
                </div>
            )}
            {anbieter === STANDARD && (
                <p className="mt-3 text-sm text-slate-600">
                    Die Funktion folgt dem oben gewählten Anbieter ({standardName}) mit dessen Standard-Modell.
                </p>
            )}

            <div className="mt-3 flex flex-wrap items-center justify-end gap-2">
                {funktion.eigeneWahl && (
                    <Button variant="outline" size="sm" onClick={() => senden('zuruecksetzen')} disabled={speichert !== null} className="mr-auto">
                        {speichert === 'zuruecksetzen'
                            ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                            : <RotateCcw aria-hidden="true" className="h-4 w-4" />}
                        Auf Standard zurücksetzen
                    </Button>
                )}
                <Button variant="ghost" size="sm" onClick={onSchliessen} disabled={speichert !== null}>
                    Abbrechen
                </Button>
                <span title={!geaendert ? 'Es wurde nichts geändert.' : undefined}>
                    <Button size="sm" onClick={() => senden('uebernehmen')} disabled={!geaendert || speichert !== null}>
                        {speichert === 'uebernehmen'
                            ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                            : <Check aria-hidden="true" className="h-4 w-4" />}
                        Übernehmen
                    </Button>
                </span>
            </div>
        </div>
    );
}
