import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Undo2, Redo2, ChevronDown } from 'lucide-react';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';

/** Ein Eintrag der Rueckgaengig-Liste -- neuester Schritt zuerst. */
export interface VerlaufsEintrag {
    id: number;
    bezeichnung: string;
}

export interface VerlaufKnoepfeProps {
    kannRueckgaengig: boolean;
    kannWiederholen: boolean;
    /** Bezeichnung des naechsten Rueckgaengig-Schritts, z. B. "Position gelöscht". */
    naechstesRueckgaengig: string | null;
    naechstesWiederholen: string | null;
    /** Neuester Schritt zuerst -- genau die Reihenfolge der Dropdown-Liste. */
    schritte: VerlaufsEintrag[];
    /** anzahl = wie viele Schritte auf einmal zurueckgenommen werden (Liste). */
    onRueckgaengig: (anzahl: number) => void;
    onWiederholen: () => void;
}

const MENU_BREITE = 260;

/**
 * Zwei Symbol-Knoepfe fuer Rueckgaengig/Wiederholen plus ein Word-artiges
 * Dropdown am Rueckgaengig-Knopf: es zeigt die letzten Schritte (neuester
 * oben), markiert beim Ueberfahren alle Schritte bis zur Mausposition und
 * nimmt per Klick mehrere auf einmal zurueck.
 *
 * Symbol-Knoepfe statt Text, weil die Kopfleiste auf dem 14-Zoll-Laptop sonst
 * ueberlaeuft. Die Liste haengt per Portal an `document.body` und
 * positioniert sich `fixed` -- dieselbe Loesung wie `WahlpositionMenu`,
 * inline gerendert wuerde sie in der schmalen Kopfleiste abgeschnitten.
 */
export function VerlaufKnoepfe({
    kannRueckgaengig,
    kannWiederholen,
    naechstesRueckgaengig,
    naechstesWiederholen,
    schritte,
    onRueckgaengig,
    onWiederholen,
}: VerlaufKnoepfeProps) {
    const [offen, setOffen] = useState(false);
    const [markiertBis, setMarkiertBis] = useState(1);
    const [position, setPosition] = useState({ top: 0, left: 0 });
    const pfeilRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);

    // Spiegelt die Callback-Prop in ein Ref: der Tastatur-Listener unten haengt
    // in einem Effekt mit Dep-Array [offen, waehle] und bleibt bestehen,
    // solange die Liste offen ist. Ohne den Spiegel wuerde ein Enter nach
    // einem Rerender mit neuer Prop noch den alten Schluss von damals rufen
    // (Vorbild: Ref-Spiegel ohne Dep-Array in useIdleTimer.ts:64-75).
    const onRueckgaengigRef = useRef(onRueckgaengig);
    useEffect(() => {
        onRueckgaengigRef.current = onRueckgaengig;
    });

    /** Rechnet die Liste an den Pfeil und klappt bei Platzmangel nach oben. */
    const berechnePosition = useCallback((hoehe: number) => {
        const rect = pfeilRef.current?.getBoundingClientRect();
        if (!rect) return;
        const passtNachUnten = rect.bottom + 4 + hoehe <= window.innerHeight;
        setPosition({
            top: passtNachUnten ? rect.bottom + 4 : Math.max(8, rect.top - hoehe - 4),
            left: Math.max(8, Math.min(rect.left, window.innerWidth - MENU_BREITE - 8)),
        });
    }, []);

    /**
     * Gemessen wird im Ref-Callback, nicht in einem Effect: React ruft ihn im
     * Commit auf, also vor dem Paint -- die Liste erscheint direkt an der
     * richtigen Stelle, mit ihrer echten Hoehe statt einem Schaetzwert.
     */
    const menuMounten = useCallback((node: HTMLDivElement | null) => {
        menuRef.current = node;
        if (node) berechnePosition(node.offsetHeight);
    }, [berechnePosition]);

    /** Schliesst die Liste, gibt den Fokus an den Pfeil zurueck und meldet die Auswahl. */
    const waehle = useCallback((anzahl: number) => {
        setOffen(false);
        pfeilRef.current?.focus();
        onRueckgaengigRef.current(anzahl);
    }, []);

    useEffect(() => {
        if (!offen) return;
        const beiKlick = (e: MouseEvent) => {
            const ziel = e.target as Node;
            if (pfeilRef.current?.contains(ziel) || menuRef.current?.contains(ziel)) return;
            setOffen(false);
        };
        const beiTaste = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                setOffen(false);
                pfeilRef.current?.focus();
                return;
            }
            if (!menuRef.current) return;
            const punkte = Array.from(
                menuRef.current.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')
            );
            if (punkte.length === 0) return;
            const aktuell = punkte.indexOf(document.activeElement as HTMLButtonElement);

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                // aktuell === -1 heisst: Fokus steht noch auf dem Pfeil.
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
            } else if (e.key === 'Enter') {
                if (aktuell !== -1) {
                    e.preventDefault();
                    waehle(aktuell + 1);
                }
            } else if (e.key === 'Tab') {
                // Ein offenes Menue faengt Tab ab (ARIA Application Mode) — ohne das
                // Schliessen landet der Fokus unsichtbar hinter der Liste.
                setOffen(false);
            }
        };
        // Scrollt der Nutzer die Kopfleiste weg, wandert der Pfeil unter der
        // fixed positionierten Liste weg. Dann lieber schliessen als falsch zeigen.
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
    }, [offen, waehle]);

    const toggeln = () => {
        if (!kannRueckgaengig) return;
        if (offen) {
            setOffen(false);
        } else {
            setMarkiertBis(1);
            setOffen(true);
        }
    };

    const rueckgaengigTitel = kannRueckgaengig && naechstesRueckgaengig
        ? `Rückgängig: ${naechstesRueckgaengig} (Strg+Z)`
        : 'Nichts zum Rückgängigmachen';
    const wiederholenTitel = kannWiederholen && naechstesWiederholen
        ? `Wiederholen: ${naechstesWiederholen} (Strg+Y)`
        : 'Nichts zum Wiederholen';

    const anzahlMarkiert = Math.min(markiertBis, schritte.length);
    const anzahlText = anzahlMarkiert === 1 ? '1 Schritt' : `${anzahlMarkiert} Schritte`;

    const menu = (
        <div
            ref={menuMounten}
            role="menu"
            aria-label="Letzte Änderungen"
            style={{ position: 'fixed', top: position.top, left: position.left, width: MENU_BREITE, zIndex: 99999 }}
            className="bg-white rounded-xl border border-slate-200 shadow-lg p-1 max-h-72 overflow-y-auto animate-in fade-in zoom-in-95 duration-150"
        >
            {schritte.map((eintrag, i) => {
                const markiert = i < markiertBis;
                return (
                    <button
                        key={eintrag.id}
                        type="button"
                        role="menuitem"
                        onMouseEnter={() => setMarkiertBis(i + 1)}
                        onFocus={() => setMarkiertBis(i + 1)}
                        onClick={(e) => { e.stopPropagation(); waehle(i + 1); }}
                        className={cn(
                            "w-full flex items-start gap-2 px-2.5 py-1.5 rounded-lg text-left text-xs transition-colors",
                            "focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40",
                            markiert ? "bg-rose-50 text-rose-700" : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <span className="flex-1 min-w-0 break-words leading-snug">{eintrag.bezeichnung}</span>
                    </button>
                );
            })}
            <div className="border-t border-slate-100 mt-1 pt-1.5 px-2.5 pb-1 text-[10px] text-slate-400">
                {anzahlText} rückgängig machen
            </div>
        </div>
    );

    return (
        <div className="flex items-center gap-0.5">
            <Button
                variant="ghost"
                size="sm"
                aria-label="Rückgängig"
                title={rueckgaengigTitel}
                disabled={!kannRueckgaengig}
                onClick={() => onRueckgaengig(1)}
                className="h-7 w-7 p-0 rounded-md text-slate-500 hover:text-slate-700"
            >
                <Undo2 className="w-3.5 h-3.5" />
            </Button>
            <button
                ref={pfeilRef}
                type="button"
                aria-label="Liste der letzten Änderungen"
                title="Liste der letzten Änderungen"
                aria-haspopup="menu"
                aria-expanded={offen}
                disabled={!kannRueckgaengig}
                onClick={toggeln}
                className={cn(
                    "h-7 w-4 flex-shrink-0 inline-flex items-center justify-center rounded-md transition-colors",
                    "text-slate-500 hover:text-slate-700",
                    "focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
            >
                <ChevronDown className={cn("w-3 h-3 transition-transform duration-200", offen && "rotate-180")} />
            </button>
            <Button
                variant="ghost"
                size="sm"
                aria-label="Wiederholen"
                title={wiederholenTitel}
                disabled={!kannWiederholen}
                onClick={() => onWiederholen()}
                className="h-7 w-7 p-0 rounded-md text-slate-500 hover:text-slate-700"
            >
                <Redo2 className="w-3.5 h-3.5" />
            </Button>
            {offen && kannRueckgaengig && createPortal(menu, document.body)}
        </div>
    );
}
