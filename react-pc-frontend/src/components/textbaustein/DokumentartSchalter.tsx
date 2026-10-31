import { useId, useMemo } from 'react';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { Schalter } from '../ui/schalter';
import type { DokumenttypGruppe } from './typen';

interface DokumentartSchalterProps {
    gruppen: readonly DokumenttypGruppe[];
    ausgewaehlt: string[];
    onChange: (ausgewaehlt: string[]) => void;
}

/**
 * „Verwendet in“: alle Dokumentarten als An/Aus-Schalter, nach Gruppen.
 * Gespeicherte Dokumentarten, die das Programm nicht (mehr) kennt, stehen
 * unter „Weitere“, damit man sie abschalten kann und nichts still verloren geht.
 */
export function DokumentartSchalter({ gruppen, ausgewaehlt, onChange }: DokumentartSchalterProps) {
    const alleGruppen = useMemo(() => {
        const bekannt = new Set(gruppen.flatMap((g) => g.dokumenttypen));
        const weitere = ausgewaehlt.filter((art) => !bekannt.has(art));
        return weitere.length > 0 ? [...gruppen, { gruppe: 'Weitere', dokumenttypen: weitere }] : [...gruppen];
    }, [gruppen, ausgewaehlt]);

    const ueberschriftId = useId();

    const schalte = (art: string, an: boolean) =>
        onChange(an ? [...ausgewaehlt, art] : ausgewaehlt.filter((a) => a !== art));

    const schalteGruppe = (dokumenttypen: string[], an: boolean) => {
        const ohneGruppe = ausgewaehlt.filter((a) => !dokumenttypen.includes(a));
        onChange(an ? [...ohneGruppe, ...dokumenttypen] : ohneGruppe);
    };

    return (
        <section aria-labelledby={ueberschriftId} className="space-y-3">
            <div className="flex items-center gap-1.5">
                <h4 id={ueberschriftId} className="text-sm font-semibold text-slate-800">Verwendet in</h4>
                <InfoButton titel="Verwendet in">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Wenn Sie ein Dokument dieser Art schreiben, zeigt die Auswahl „Textbaustein einfügen“ diesen Textbaustein ganz oben als passenden Vorschlag.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Die Auswahl sortiert nur vor: Alle anderen Textbausteine bleiben darunter weiter auswählbar. Ohne Auswahl steht der Textbaustein bei jeder Dokumentart unter den übrigen. Bereits geschriebene Dokumente ändern sich nicht.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {alleGruppen.map(({ gruppe, dokumenttypen }) => {
                    const alleAn = dokumenttypen.every((art) => ausgewaehlt.includes(art));
                    return (
                        <div key={gruppe} role="group" aria-label={gruppe} className="rounded-lg border border-slate-200 bg-slate-50/60 p-3">
                            <div className="mb-1 flex items-center justify-between gap-2">
                                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{gruppe}</p>
                                <button
                                    type="button"
                                    onClick={() => schalteGruppe(dokumenttypen, !alleAn)}
                                    aria-label={`Ganze Gruppe „${gruppe}“ ${alleAn ? 'ausschalten' : 'einschalten'}`}
                                    className="shrink-0 rounded px-1.5 py-0.5 text-xs font-medium text-rose-700 hover:bg-rose-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    {alleAn ? 'Ganze Gruppe aus' : 'Ganze Gruppe an'}
                                </button>
                            </div>
                            <div className="flex flex-col">
                                {dokumenttypen.map((art) => (
                                    <Schalter
                                        key={art}
                                        an={ausgewaehlt.includes(art)}
                                        onChange={(an) => schalte(art, an)}
                                        beschriftung={art}
                                        className="self-start"
                                    />
                                ))}
                            </div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
}
