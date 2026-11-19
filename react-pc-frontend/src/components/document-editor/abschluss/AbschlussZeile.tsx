import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Pencil, EyeOff, Eye } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { cn } from '../../../lib/utils';
import {
    BEZEICHNUNG_MAX,
    UNTERZEILE_MAX,
    istTextAngepasst,
    normalisiereZeile,
    type AbschlussZeileAnpassung,
} from './abschlussZeilen';

interface AbschlussZeileProps {
    /** Kleines Symbol links (Lucide in farbigem Kästchen). */
    symbol: ReactNode;
    automatischeBezeichnung: string;
    /** Automatische Unterzeile; leer = keine. */
    automatischeUnterzeile?: string | null;
    anpassung?: AbschlussZeileAnpassung;
    /** Betrag rechts (brutto groß, netto klein). Bleibt immer automatisch. */
    betrag: ReactNode;
    /** Nur gesetzt, wenn die Rechnung bearbeitbar ist. undefined = keine Bedienelemente. */
    onAendern?: (neu: AbschlussZeileAnpassung | undefined) => void;
}

const ZEILE = 'flex items-center justify-between gap-2 px-3 py-2 rounded-lg border';
const ICON_KNOPF = '-my-1.5 h-11 w-11 shrink-0 p-0 text-slate-500 hover:text-rose-700';

function gleich(a: AbschlussZeileAnpassung | undefined, b: AbschlussZeileAnpassung | undefined): boolean {
    return JSON.stringify(normalisiereZeile(a) ?? null) === JSON.stringify(normalisiereZeile(b) ?? null);
}

/**
 * Eine Zeile im Abrechnungsstand, deren Text der Handwerker ändern oder die er
 * von der Rechnung nehmen kann. Ausgeblendete Zeilen bleiben hier gedämpft
 * sichtbar, damit man sie wiederfindet; Beträge ändern sich nie.
 */
export function AbschlussZeile({ symbol, automatischeBezeichnung, automatischeUnterzeile, anpassung, betrag, onAendern }: AbschlussZeileProps) {
    const [bearbeiten, setBearbeiten] = useState(false);
    const stiftRef = useRef<HTMLButtonElement>(null);
    const fokusZurueck = useRef(false);

    useEffect(() => {
        if (!bearbeiten && fokusZurueck.current) {
            fokusZurueck.current = false;
            stiftRef.current?.focus();
        }
    }, [bearbeiten]);

    const melde = (neu: AbschlussZeileAnpassung | undefined) => {
        if (!gleich(neu, anpassung)) onAendern?.(normalisiereZeile(neu));
    };
    const beenden = (neu: AbschlussZeileAnpassung | undefined, fokus: boolean) => {
        fokusZurueck.current = fokus;
        setBearbeiten(false);
        if (neu !== anpassung) melde(neu);
    };

    const ausgeblendet = anpassung?.ausgeblendet === true;
    const bezeichnung = anpassung?.bezeichnung || automatischeBezeichnung;
    const unterzeile = anpassung?.unterzeile || automatischeUnterzeile || '';

    if (bearbeiten && onAendern) {
        return (
            <div className={cn(ZEILE, 'items-start bg-white border-rose-200')}>
                <ZeilenEditor
                    symbol={symbol}
                    automatischeBezeichnung={automatischeBezeichnung}
                    automatischeUnterzeile={automatischeUnterzeile}
                    anpassung={anpassung}
                    onFertig={beenden}
                />
                <div className="shrink-0 pt-2">{betrag}</div>
            </div>
        );
    }

    return (
        <div className={cn(ZEILE, ausgeblendet ? 'border-dashed border-slate-300 bg-slate-50' : 'bg-white border-slate-100')}>
            <div className="flex items-center gap-2.5 min-w-0">
                <div className={cn(ausgeblendet && 'opacity-60')}>{symbol}</div>
                <div className="min-w-0">
                    <span className={cn('text-xs font-medium text-slate-600', ausgeblendet && 'line-through opacity-60')}>{bezeichnung}</span>
                    {ausgeblendet ? (
                        // Volle Deckkraft: der Hinweis muss lesbar bleiben, gedämpft sind nur Bezeichnung und Betrag.
                        <p className="text-[11px] font-medium text-slate-600">Steht nicht auf der Rechnung</p>
                    ) : unterzeile && (
                        <p className="text-[10px] text-slate-400 truncate" title={unterzeile}>{unterzeile}</p>
                    )}
                </div>
            </div>
            <div className="flex items-center gap-1 shrink-0">
                <div className={cn(ausgeblendet && 'opacity-60')}>{betrag}</div>
                {onAendern && ausgeblendet && (
                    <Button variant="outline" size="sm" className="ml-1 min-h-[44px] text-xs" onClick={() => melde({ ...anpassung, ausgeblendet: false })}>
                        <Eye className="w-4 h-4" aria-hidden="true" />
                        Wieder einblenden
                    </Button>
                )}
                {onAendern && !ausgeblendet && (
                    <>
                        <Button ref={stiftRef} variant="ghost" className={ICON_KNOPF} aria-label="Text ändern" title="Text ändern" onClick={() => setBearbeiten(true)}>
                            <Pencil className="w-4 h-4" aria-hidden="true" />
                        </Button>
                        <Button variant="ghost" className={ICON_KNOPF} aria-label="Zeile von der Rechnung entfernen" title="Zeile von der Rechnung entfernen" onClick={() => melde({ ...anpassung, ausgeblendet: true })}>
                            <EyeOff className="w-4 h-4" aria-hidden="true" />
                        </Button>
                    </>
                )}
            </div>
        </div>
    );
}

interface ZeilenEditorProps {
    symbol: ReactNode;
    automatischeBezeichnung: string;
    automatischeUnterzeile?: string | null;
    anpassung?: AbschlussZeileAnpassung;
    /** neu === anpassung heißt verworfen. */
    onFertig: (neu: AbschlussZeileAnpassung | undefined, fokusZurueck: boolean) => void;
}

/** Zwei Eingaben für Bezeichnung und Unterzeile. Enter/Verlassen übernimmt, Escape verwirft. */
function ZeilenEditor({ symbol, automatischeBezeichnung, automatischeUnterzeile, anpassung, onFertig }: ZeilenEditorProps) {
    const [bezeichnung, setBezeichnung] = useState(anpassung?.bezeichnung ?? '');
    const [unterzeile, setUnterzeile] = useState(anpassung?.unterzeile ?? '');
    const ersteRef = useRef<HTMLInputElement>(null);
    useEffect(() => { ersteRef.current?.focus(); }, []);

    const entwurf = (): AbschlussZeileAnpassung => ({ ...anpassung, bezeichnung, unterzeile });
    const tasten = (e: KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            onFertig(entwurf(), true);
        } else if (e.key === 'Escape') {
            // Nicht weiterreichen: der Editor soll nichts anderes schließen.
            e.preventDefault();
            e.stopPropagation();
            onFertig(anpassung, true);
        }
    };

    return (
        <div
            className="flex items-start gap-2.5 min-w-0 flex-1"
            // Strg+Z in den Feldern gilt dem Feld, nicht dem Dokument-Verlauf.
            data-eigenes-rueckgaengig=""
            onBlur={(e) => {
                if (!e.currentTarget.contains(e.relatedTarget as Node | null)) onFertig(entwurf(), false);
            }}
        >
            {symbol}
            <div className="flex-1 min-w-0 space-y-1.5">
                <Input
                    ref={ersteRef}
                    aria-label="Bezeichnung"
                    value={bezeichnung}
                    maxLength={BEZEICHNUNG_MAX}
                    placeholder={automatischeBezeichnung}
                    onChange={(e) => setBezeichnung(e.target.value)}
                    onKeyDown={tasten}
                    className="h-11 text-xs"
                />
                <Input
                    aria-label="Unterzeile"
                    value={unterzeile}
                    maxLength={UNTERZEILE_MAX}
                    placeholder={automatischeUnterzeile || 'Unterzeile (optional)'}
                    onChange={(e) => setUnterzeile(e.target.value)}
                    onKeyDown={tasten}
                    className="h-11 text-xs"
                />
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="text-[11px] text-slate-500">Enter übernimmt, Esc verwirft</span>
                    {istTextAngepasst(anpassung) && (
                        <Button
                            variant="ghost"
                            size="sm"
                            className="min-h-[44px] px-2 text-xs"
                            // Fokus im Feld lassen, sonst übernimmt das Verlassen vorher den Entwurf.
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={() => onFertig({ ausgeblendet: anpassung?.ausgeblendet }, true)}
                        >
                            Automatischen Text verwenden
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
