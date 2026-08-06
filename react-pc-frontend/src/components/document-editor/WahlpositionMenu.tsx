import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown, CircleCheck, Diamond, Plus } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { DocBlock } from './types';

/** Die drei Zustaende, die eine Leistung fuer den Kunden haben kann. */
type Wahlmodus = 'fest' | 'optional' | 'alternativ';

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

const MENU_BREITE = 240;

/**
 * Ein Knopf pro Leistung, der bestimmt, was der Kunde damit tun darf.
 *
 * Bewusst ein Menue mit genau einer aktiven Zeile statt mehrerer Schalter: die
 * drei Zustaende schliessen sich gegenseitig aus, auch wenn sie im Datenmodell
 * auf zwei Feldern liegen (`optional` + `alternativGruppe`).
 *
 * Die Liste haengt per Portal an `document.body` und positioniert sich `fixed` —
 * dieselbe Loesung wie in `ui/select-custom.tsx`. Inline gerendert wuerde sie
 * unter der letzten Leistung eines Bauabschnitts abgeschnitten, weil dessen
 * Karte `overflow-hidden` traegt (SectionHeaderBlock).
 */
export function WahlpositionMenu({
    block, isLocked, onModusWechsel, onAlternativOeffnen,
}: WahlpositionMenuProps) {
    const [offen, setOffen] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0 });
    const triggerRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const modus = wahlmodusVon(block);

    /** Rechnet die Liste an den Trigger und klappt bei Platzmangel nach oben. */
    const berechnePosition = useCallback((hoehe: number) => {
        const rect = triggerRef.current?.getBoundingClientRect();
        if (!rect) return;
        const passtNachUnten = rect.bottom + 4 + hoehe <= window.innerHeight;
        setPosition({
            top: passtNachUnten ? rect.bottom + 4 : Math.max(8, rect.top - hoehe - 4),
            left: Math.max(8, Math.min(rect.right - MENU_BREITE, window.innerWidth - MENU_BREITE - 8)),
        });
    }, []);

    /**
     * Gemessen wird im Ref-Callback, nicht in einem Effect: React ruft ihn im
     * Commit auf, also vor dem Paint — die Liste erscheint direkt an der
     * richtigen Stelle. Und nur hier steht ihre echte Hoehe zur Verfuegung, die
     * bei Browser-Zoom oder grosser Systemschrift deutlich von einem festen
     * Schaetzwert abweicht.
     */
    const menuMounten = useCallback((node: HTMLDivElement | null) => {
        menuRef.current = node;
        if (node) berechnePosition(node.offsetHeight);
    }, [berechnePosition]);

    useEffect(() => {
        if (!offen) return;
        const beiKlick = (e: MouseEvent) => {
            const ziel = e.target as Node;
            if (triggerRef.current?.contains(ziel) || menuRef.current?.contains(ziel)) return;
            setOffen(false);
        };
        const beiTaste = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                setOffen(false);
                triggerRef.current?.focus();
                return;
            }
            if (!menuRef.current) return;
            const punkte = Array.from(
                menuRef.current.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')
            );
            if (punkte.length === 0) return;
            const aktuell = punkte.indexOf(document.activeElement as HTMLButtonElement);

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                // aktuell === -1 heisst: Fokus steht noch auf dem Trigger.
                punkte[aktuell === -1 ? 0 : (aktuell + 1) % punkte.length].focus();
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                punkte[aktuell === -1
                    ? punkte.length - 1
                    : (aktuell - 1 + punkte.length) % punkte.length].focus();
            } else if (e.key === 'Home') {
                e.preventDefault();
                punkte[0].focus();
            } else if (e.key === 'End') {
                e.preventDefault();
                punkte[punkte.length - 1].focus();
            } else if (e.key === 'Tab') {
                // Ein offenes Menue faengt Tab ab (ARIA Application Mode) — ohne das
                // Schliessen landet der Fokus unsichtbar hinter der Liste.
                setOffen(false);
            }
        };
        // Scrollt der Nutzer die Positionsliste, wandert der Trigger unter dem
        // fixed positionierten Menue weg. Dann lieber schliessen als falsch zeigen.
        const beiScroll = () => setOffen(false);

        document.addEventListener('mousedown', beiKlick);
        document.addEventListener('keydown', beiTaste);
        window.addEventListener('scroll', beiScroll, true);
        window.addEventListener('resize', beiScroll);
        return () => {
            document.removeEventListener('mousedown', beiKlick);
            document.removeEventListener('keydown', beiTaste);
            window.removeEventListener('scroll', beiScroll, true);
            window.removeEventListener('resize', beiScroll);
        };
    }, [offen]);

    const waehle = (ziel: Wahlmodus) => {
        setOffen(false);
        triggerRef.current?.focus();
        if (ziel === 'alternativ') onAlternativOeffnen(block.id);
        else onModusWechsel(block.id, ziel);
    };

    const knopfText = modus === 'alternativ' ? 'Alternative'
        : modus === 'optional' ? 'Optional'
        : 'Kunde wählt';

    const menu = (
        <div
            ref={menuMounten}
            role="menu"
            aria-label="Was darf der Kunde wählen?"
            style={{ position: 'fixed', top: position.top, left: position.left, width: MENU_BREITE, zIndex: 99999 }}
            className="bg-white rounded-xl border border-slate-200 shadow-lg p-1 animate-in fade-in zoom-in-95 duration-150"
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
    );

    return (
        <>
            <button
                ref={triggerRef}
                type="button"
                onClick={(e) => { e.stopPropagation(); if (!isLocked) setOffen(o => !o); }}
                disabled={isLocked}
                aria-haspopup="menu"
                aria-expanded={offen}
                title={modus === 'alternativ'
                    ? `Variante der Auswahl „${block.alternativGruppe}“ — der Kunde wählt genau eine`
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
            {offen && !isLocked && createPortal(menu, document.body)}
        </>
    );
}
