import { useCallback, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Plus } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { platzhalterNachGruppe } from './platzhalter';
import type { Platzhalter } from './typen';

interface PlatzhalterLeisteProps {
    katalog: readonly Platzhalter[];
    onEinfuegen: (platzhalter: Platzhalter) => void;
    /** Gesperrt, solange der Editor noch nicht bereit ist. */
    gesperrt?: boolean;
}

/** Rechte Leiste im Bearbeiten-Dialog: Platzhalter nach Gruppen, Klick fügt an der Cursorstelle ein. */
export function PlatzhalterLeiste({ katalog, onEinfuegen, gesperrt = false }: PlatzhalterLeisteProps) {
    const gruppen = useMemo(() => platzhalterNachGruppe(katalog), [katalog]);
    const ueberschriftId = useId();
    const listeRef = useRef<HTMLDivElement>(null);
    const [weitereUnten, setWeitereUnten] = useState(false);

    const pruefeEnde = useCallback(() => {
        const liste = listeRef.current;
        if (liste) setWeitereUnten(liste.scrollTop + liste.clientHeight < liste.scrollHeight - 4);
    }, []);

    useLayoutEffect(() => {
        const liste = listeRef.current;
        if (!liste) return;
        pruefeEnde();
        const beobachter = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(pruefeEnde);
        beobachter?.observe(liste);
        return () => beobachter?.disconnect();
    }, [pruefeEnde, gruppen]);

    return (
        <section aria-labelledby={ueberschriftId} className="flex min-h-0 flex-1 flex-col">
            <div className="flex items-center gap-1.5">
                <h4 id={ueberschriftId} className="text-sm font-semibold text-slate-800">Platzhalter</h4>
                <InfoButton titel="Platzhalter">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Platzhalter sind Lücken im Text. Das Programm füllt sie im Dokument automatisch mit den echten Daten – zum Beispiel mit Name und Adresse des Kunden oder der Dokumentnummer.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Der Knopf zeigt nur ein Beispiel, damit Sie sehen, was dort später steht. Gespeichert wird die Lücke, nicht das Beispiel. Fehlt ein Wert (etwa kein Ansprechpartner hinterlegt), bleibt die Stelle im Dokument leer.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">Klicken fügt an der Cursorstelle ein.</p>
            {katalog.length === 0 ? (
                <p className="mt-3 text-sm text-slate-500">Keine Platzhalter verfügbar.</p>
            ) : (
                <div className="relative mt-2 flex min-h-0 flex-1 flex-col">
                    {/* Eigener Scrollbereich; -mx-1 px-1 / pb: Platz für die Fokus-Ringe. */}
                    <div ref={listeRef} onScroll={pruefeEnde} data-platzhalter-liste className="-mx-1 min-h-0 flex-1 space-y-3 overflow-y-auto px-1 pb-2 pt-1">
                        {gruppen.map(({ gruppe, eintraege }) => (
                            <div key={gruppe} role="group" aria-label={gruppe}>
                                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{gruppe}</p>
                                <div className="flex flex-col gap-1">
                                    {eintraege.map((eintrag) => (
                                        <PlatzhalterKnopf key={eintrag.token} eintrag={eintrag} gesperrt={gesperrt} onEinfuegen={onEinfuegen} />
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                    {weitereUnten && (
                        // Zeigt, dass es weitergeht – macOS blendet den Scrollbalken aus.
                        <div data-weitere-platzhalter className="pointer-events-none absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-slate-50 to-transparent" aria-hidden="true" />
                    )}
                </div>
            )}
        </section>
    );
}

function PlatzhalterKnopf({ eintrag, gesperrt, onEinfuegen }: { eintrag: Platzhalter; gesperrt: boolean; onEinfuegen: (p: Platzhalter) => void }) {
    return (
        <button
            type="button"
            disabled={gesperrt}
            title={eintrag.beschreibung}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => onEinfuegen(eintrag)}
            className="group flex w-full items-start gap-2 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-left hover:border-rose-200 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
            <span className="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2 leading-snug">
                <span className="text-sm font-medium text-slate-800">{eintrag.label}</span>
                <span className="break-words text-xs text-slate-500">z. B. {eintrag.beispiel}</span>
            </span>
            <Plus className="mt-0.5 h-4 w-4 shrink-0 text-slate-300 group-hover:text-rose-600" aria-hidden="true" />
        </button>
    );
}
