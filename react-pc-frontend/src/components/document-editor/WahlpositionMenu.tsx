import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, CircleCheck, Diamond, Plus } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { DocBlock } from './types';

/** Die drei Zustaende, die eine Leistung fuer den Kunden haben kann. */
export type Wahlmodus = 'fest' | 'optional' | 'alternativ';

/** Leitet den Modus aus den beiden Feldern ab, in denen er gespeichert ist. */
function wahlmodusVon(block: DocBlock): Wahlmodus {
    if (!block.optional) return 'fest';
    return block.alternativGruppe ? 'alternativ' : 'optional';
}

interface WahlpositionMenuProps {
    block: DocBlock;
    isLocked: boolean;
    /** Setzt die Position auf "fest beauftragt" oder "optional". */
    onModusWechsel: (id: string, modus: 'fest' | 'optional') => void;
    /** Oeffnet den Dialog, in dem die Gruppe zusammengestellt wird. */
    onAlternativOeffnen: (id: string) => void;
}

const EINTRAEGE: {
    modus: Wahlmodus;
    label: string;
    hinweis: string;
    Icon: typeof Plus;
}[] = [
    { modus: 'fest', label: 'Fest beauftragt', hinweis: 'Zählt ganz normal in die Summe', Icon: CircleCheck },
    { modus: 'optional', label: 'Optional', hinweis: 'Kunde kann sie dazubuchen', Icon: Plus },
    { modus: 'alternativ', label: 'Alternativ', hinweis: 'Kunde wählt eine aus mehreren', Icon: Diamond },
];

/**
 * Ein Knopf pro Leistung, der bestimmt, was der Kunde damit tun darf.
 *
 * Bewusst ein Menue mit genau einer aktiven Zeile statt mehrerer Schalter: die
 * drei Zustaende schliessen sich gegenseitig aus, auch wenn sie im Datenmodell
 * auf zwei Feldern liegen (`optional` + `alternativGruppe`).
 */
export function WahlpositionMenu({
    block, isLocked, onModusWechsel, onAlternativOeffnen,
}: WahlpositionMenuProps) {
    const [offen, setOffen] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const modus = wahlmodusVon(block);

    useEffect(() => {
        if (!offen) return;
        const beiKlick = (e: MouseEvent) => {
            if (!wrapperRef.current?.contains(e.target as Node)) setOffen(false);
        };
        const beiTaste = (e: KeyboardEvent) => { if (e.key === 'Escape') setOffen(false); };
        document.addEventListener('mousedown', beiKlick);
        document.addEventListener('keydown', beiTaste);
        return () => {
            document.removeEventListener('mousedown', beiKlick);
            document.removeEventListener('keydown', beiTaste);
        };
    }, [offen]);

    const waehle = (ziel: Wahlmodus) => {
        setOffen(false);
        if (ziel === 'alternativ') onAlternativOeffnen(block.id);
        else onModusWechsel(block.id, ziel);
    };

    const knopfText = modus === 'alternativ' ? 'Alternative'
        : modus === 'optional' ? 'Optional'
        : 'Kunde wählt';

    return (
        <div className="relative" ref={wrapperRef}>
            <button
                type="button"
                onClick={(e) => { e.stopPropagation(); if (!isLocked) setOffen(o => !o); }}
                disabled={isLocked}
                aria-haspopup="menu"
                aria-expanded={offen}
                title={modus === 'alternativ'
                    ? `Variante der Auswahl „${block.alternativGruppe}" — der Kunde wählt genau eine`
                    : modus === 'optional'
                        ? 'Optional: der Kunde kann diese Position dazubuchen (zählt nicht in die Summe)'
                        : 'Festlegen, ob der Kunde diese Leistung wählen darf'}
                className={cn(
                    "h-7 pl-2 pr-1.5 inline-flex items-center gap-1 text-[11px] font-medium rounded-md transition-colors",
                    "focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40 disabled:opacity-50 disabled:cursor-not-allowed",
                    modus === 'fest'
                        ? "text-slate-400 hover:text-slate-600 hover:bg-slate-100"
                        : "text-amber-600 bg-amber-50 hover:bg-amber-100"
                )}
            >
                {knopfText}
                <ChevronDown className={cn("w-3 h-3 transition-transform duration-200", offen && "rotate-180")} />
            </button>

            {offen && !isLocked && (
                <div
                    role="menu"
                    aria-label="Was darf der Kunde wählen?"
                    className="absolute right-0 top-full mt-1 z-30 w-60 bg-white rounded-xl border border-slate-200 shadow-lg p-1 animate-in fade-in zoom-in-95 duration-150"
                >
                    {EINTRAEGE.map(({ modus: eintrag, label, hinweis, Icon }) => (
                        <button
                            key={eintrag}
                            type="button"
                            role="menuitemradio"
                            aria-checked={modus === eintrag}
                            onClick={(e) => { e.stopPropagation(); waehle(eintrag); }}
                            className={cn(
                                "w-full flex items-start gap-2.5 px-2.5 py-2 rounded-lg text-left transition-colors",
                                "focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40",
                                modus === eintrag ? "bg-amber-50" : "hover:bg-slate-50"
                            )}
                        >
                            <Icon className={cn(
                                "w-3.5 h-3.5 mt-0.5 flex-shrink-0",
                                modus === eintrag ? "text-amber-600" : "text-slate-400"
                            )} />
                            <span className="flex-1 min-w-0">
                                <span className={cn(
                                    "block text-xs font-semibold",
                                    modus === eintrag ? "text-amber-900" : "text-slate-700"
                                )}>
                                    {label}
                                </span>
                                <span className="block text-[10px] text-slate-400 leading-snug">{hinweis}</span>
                            </span>
                            {modus === eintrag && (
                                <Check className="w-3.5 h-3.5 text-amber-600 flex-shrink-0 mt-0.5" />
                            )}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
