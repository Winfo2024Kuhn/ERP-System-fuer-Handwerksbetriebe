import { useId, useState } from 'react';
import { Check, ChevronRight, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';

const PUNKT_VERZOEGERUNGEN_MS = [0, 150, 300];

/**
 * Lade-Blase der KI-Hilfe: zeigt live, wo die KI gerade nachschaut.
 *
 * <p>Vor dem ersten Schritt steht „Denkt nach …“. Danach sind alle Schritte
 * bis auf den letzten erledigt (Häkchen); der letzte dreht sich, bis der
 * nächste kommt oder die Antwort da ist. Die Liste ist eine höfliche
 * Live-Region: Screenreader lesen neue Schritte vor, ohne zu unterbrechen.
 * Die Schritttexte sind reiner Text vom Server und werden nur als Text
 * ausgegeben.</p>
 *
 * <p>Der Knopf „Abbrechen“ steht bewusst außerhalb der Live-Region, damit er
 * nicht mit jedem Schritt erneut vorgelesen wird.</p>
 */
export function KiSchritteLive({ schritte, onAbbrechen }: { schritte: string[]; onAbbrechen: () => void }) {
    return (
        <div className="flex justify-start">
            <div className="max-w-[85%] rounded-2xl rounded-bl-md bg-slate-100 px-4 py-3">
                <ul aria-live="polite" aria-label="Was die KI gerade tut" className="space-y-1.5 text-xs">
                    {schritte.length === 0 ? (
                        <li className="flex items-center gap-2 text-slate-600">
                            <span aria-hidden="true" className="flex gap-1">
                                {PUNKT_VERZOEGERUNGEN_MS.map((verzoegerung) => (
                                    <span
                                        key={verzoegerung}
                                        className="h-1.5 w-1.5 rounded-full bg-rose-400 motion-safe:animate-bounce"
                                        style={{ animationDelay: `${verzoegerung}ms` }}
                                    />
                                ))}
                            </span>
                            Denkt nach …
                        </li>
                    ) : (
                        schritte.map((schritt, index) => {
                            const aktuell = index === schritte.length - 1;
                            return (
                                <li key={index} aria-current={aktuell ? 'step' : undefined} className="flex items-start gap-2">
                                    {aktuell ? (
                                        <Loader2 aria-hidden="true" className="h-4 w-4 shrink-0 text-rose-500 motion-safe:animate-spin" />
                                    ) : (
                                        <Check aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-500" />
                                    )}
                                    <span className={cn('min-w-0 break-words', aktuell ? 'font-medium text-slate-700' : 'text-slate-600')}>
                                        {schritt}
                                    </span>
                                </li>
                            );
                        })
                    )}
                </ul>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={onAbbrechen}
                    className="-mb-1 -ml-2 mt-2 px-2 py-1 text-xs"
                >
                    Abbrechen
                </Button>
            </div>
        </div>
    );
}

/**
 * Zugeklappte Zeile „So habe ich nachgeschaut (n)“ unter einer Antwort.
 * Aufgeklappt zeigt sie die gespeicherten Schritte als schlichte Liste.
 * Ohne Schritte (z. B. ältere Nachrichten) erscheint nichts.
 */
export function KiSchritteVerlauf({ schritte }: { schritte: string[] }) {
    const [offen, setOffen] = useState(false);
    const listenId = useId();
    if (schritte.length === 0) return null;

    return (
        <div className="mt-1 max-w-[85%] pl-2">
            <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-expanded={offen}
                aria-controls={offen ? listenId : undefined}
                onClick={() => setOffen((vorher) => !vorher)}
                className="-ml-2 gap-1 px-2 py-1 text-xs text-slate-600 hover:text-rose-700"
            >
                <ChevronRight
                    aria-hidden="true"
                    className={cn('h-4 w-4 shrink-0 motion-safe:transition-transform', offen && 'rotate-90')}
                />
                So habe ich nachgeschaut ({schritte.length})
            </Button>
            {offen && (
                <ol id={listenId} className="ml-1.5 mt-1 space-y-1 border-l border-slate-200 pl-3 text-xs text-slate-600">
                    {schritte.map((schritt, index) => (
                        <li key={index} className="flex items-start gap-1.5">
                            <Check aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-500" />
                            <span className="min-w-0 break-words">{schritt}</span>
                        </li>
                    ))}
                </ol>
            )}
        </div>
    );
}
