import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronUp, Copy } from 'lucide-react';
import { sanitizeHtml } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import { kuerzelZuChipHtml } from './platzhalter';
import type { Platzhalter, TextbausteinDto } from './typen';

interface TextbausteinKarteProps {
    baustein: TextbausteinDto;
    katalog: readonly Platzhalter[];
    onBearbeiten: (baustein: TextbausteinDto) => void;
    onKopieren: (baustein: TextbausteinDto) => void;
}

/** Ab dieser Höhe (ca. 10 Zeilen) wird der Text abgeblendet. */
const GEKUERZT_HOEHE = 'max-h-[15rem]';

/**
 * Karte eines Textbausteins: Name, „Verwendet in“ und der Text mit
 * Beispielwerten in den Platzhaltern. Ein über die ganze Karte gespannter
 * Knopf öffnet das Bearbeiten (Maus, Enter, Leertaste); die kleinen Knöpfe
 * liegen darüber und bleiben einzeln bedienbar.
 */
export function TextbausteinKarte({ baustein, katalog, onBearbeiten, onKopieren }: TextbausteinKarteProps) {
    const name = baustein.name || 'Ohne Namen';
    const html = useMemo(
        () => sanitizeHtml(kuerzelZuChipHtml(baustein.html, katalog)),
        [baustein.html, katalog],
    );

    return (
        <article className="group relative flex flex-col rounded-lg border border-slate-200 bg-white shadow-sm transition-shadow hover:border-rose-200 hover:shadow-md focus-within:border-rose-200">
            <button
                type="button"
                onClick={() => onBearbeiten(baustein)}
                aria-label={`Textbaustein „${name}“ bearbeiten`}
                className="absolute inset-0 z-0 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2"
            />
            <div className="pointer-events-none relative flex flex-1 flex-col gap-3 p-5">
                <div className="flex items-start justify-between gap-3">
                    <h3 className="text-base font-semibold text-slate-900 break-words group-hover:text-rose-700">{name}</h3>
                    <button
                        type="button"
                        onClick={() => onKopieren(baustein)}
                        aria-label={`Kopie von „${name}“ anlegen`}
                        title="Kopie anlegen"
                        className="pointer-events-auto relative z-10 -mr-1 -mt-1 shrink-0 rounded-md p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                        <Copy className="h-4 w-4" aria-hidden="true" />
                    </button>
                </div>
                <VerwendetIn dokumenttypen={baustein.dokumenttypen} />
                <KartenText html={html} />
            </div>
        </article>
    );
}

function VerwendetIn({ dokumenttypen }: { dokumenttypen: string[] }) {
    return (
        <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-xs text-slate-500">Verwendet in:</span>
            {dokumenttypen.length === 0 ? (
                <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">Ohne Zuordnung</span>
            ) : dokumenttypen.map((art) => (
                <span key={art} className="rounded-md bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">{art}</span>
            ))}
        </div>
    );
}

/** Text der Karte; lange Texte blendet er nach ca. 10 Zeilen ab und bietet „Ganzen Text zeigen“. */
function KartenText({ html }: { html: string }) {
    const textRef = useRef<HTMLDivElement>(null);
    const [zuLang, setZuLang] = useState(false);
    const [ganz, setGanz] = useState(false);

    useLayoutEffect(() => {
        const element = textRef.current;
        if (!element || ganz) return;
        const pruefe = () => setZuLang(element.scrollHeight > element.clientHeight + 1);
        pruefe();
        const beobachter = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(pruefe);
        beobachter?.observe(element);
        return () => beobachter?.disconnect();
    }, [html, ganz]);

    if (!html.trim()) return <p className="text-sm italic text-slate-400">Noch kein Text</p>;

    return (
        <div className="flex flex-col gap-1">
            <div className="relative">
                <div
                    ref={textRef}
                    data-kuerzung-erlaubt={ganz ? undefined : ''}
                    className={cn(
                        'rich-text-einheitlich prose prose-sm max-w-none text-sm leading-relaxed text-slate-700 break-words [&_p]:my-1',
                        !ganz && `${GEKUERZT_HOEHE} overflow-hidden`,
                    )}
                    // Bereinigt im useMemo der Karte (sanitizeHtml nach dem Einsetzen der Chips).
                    dangerouslySetInnerHTML={{ __html: html }}
                />
                {zuLang && !ganz && (
                    <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-white to-transparent" aria-hidden="true" />
                )}
            </div>
            {(zuLang || ganz) && (
                <button
                    type="button"
                    onClick={() => setGanz((alt) => !alt)}
                    aria-expanded={ganz}
                    className="pointer-events-auto relative z-10 inline-flex items-center gap-1 self-start rounded-md px-1 py-0.5 text-sm font-medium text-rose-700 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    {ganz ? <ChevronUp className="h-4 w-4" aria-hidden="true" /> : <ChevronDown className="h-4 w-4" aria-hidden="true" />}
                    {ganz ? 'Weniger zeigen' : 'Ganzen Text zeigen'}
                </button>
            )}
        </div>
    );
}
