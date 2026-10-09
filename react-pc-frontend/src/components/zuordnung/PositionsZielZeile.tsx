import { useId } from 'react';
import { useDndContext, useDroppable } from '@dnd-kit/core';
import { Briefcase, CircleDashed, Package, Trash2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { ablageId, formatEuro, formatProzent, type VorschauZiel, type ZielSchluessel } from './positionen';
import type { ZielFarbe } from './zielFarben';

/** Wie viele Positionen gerade gezogen werden (0 = es wird nichts gezogen). */
function useGezogeneAnzahl(): number {
    const { active } = useDndContext();
    if (!active) return 0;
    const anzahl = active.data.current?.anzahl;
    return typeof anzahl === 'number' ? anzahl : 1;
}

const positionenText = (anzahl: number) => `${anzahl} ${anzahl === 1 ? 'Position' : 'Positionen'}`;

interface PositionsZielZeileProps {
    schluessel: ZielSchluessel;
    name: string;
    istKostenstelle: boolean;
    farbe: ZielFarbe;
    /** Zugeordnete Warenpositionen. */
    anzahlPositionen: number;
    /** Live-Vorschau für dieses Ziel; fehlt, solange keine Position darauf zeigt. */
    vorschau: VorschauZiel | undefined;
    beschreibung: string;
    streckungJahre: number;
    rechnungsJahr: number;
    onBeschreibung: (wert: string) => void;
    onStreckung: (jahre: number) => void;
    onEntfernen: () => void;
}

/**
 * Ziel (Projekt oder Kostenstelle) im Modus „Nach Positionen“ – zugleich
 * Ablagefläche: Positionen hierher ziehen ordnet sie zu. Betrag und Anteil
 * kommen aus der Vorschau des Backends.
 */
export function PositionsZielZeile({
    schluessel, name, istKostenstelle, farbe, anzahlPositionen, vorschau, beschreibung, streckungJahre, rechnungsJahr,
    onBeschreibung, onStreckung, onEntfernen,
}: PositionsZielZeileProps) {
    const streckungId = useId();
    const { setNodeRef, isOver } = useDroppable({ id: ablageId(schluessel) });
    const gezogen = useGezogeneAnzahl();
    const Icon = istKostenstelle ? Package : Briefcase;
    const jahre = streckungJahre > 0 ? streckungJahre : 1;
    const betrag = vorschau?.betrag ?? null;

    return (
        <div
            ref={setNodeRef}
            role="group"
            aria-label={`Ziel ${name}`}
            className={cn(
                'rounded-lg border bg-white px-3 py-2 transition-colors',
                isOver ? 'border-rose-400 bg-rose-50 ring-2 ring-inset ring-rose-200' : gezogen > 0 ? 'border-dashed border-slate-400' : 'border-slate-200',
            )}
        >
            <div className="flex items-center gap-3">
                <span className={cn('h-3 w-3 shrink-0 rounded-full', farbe.punkt)} aria-hidden="true" />
                <Icon className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                    <p className="break-words text-sm font-medium text-slate-900">
                        {name}
                        {istKostenstelle && (
                            <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 align-middle text-xs font-normal text-slate-600">Kostenstelle</span>
                        )}
                    </p>
                    <p className={cn('text-xs', isOver ? 'font-medium text-rose-700' : 'text-slate-500')}>
                        {isOver
                            ? `${positionenText(gezogen)} hierher`
                            : anzahlPositionen > 0
                                ? `${positionenText(anzahlPositionen)}${vorschau ? ` · ${formatProzent(vorschau.anteilProzent)} % der Ware` : ''}`
                                : 'Noch keine Position'}
                    </p>
                </div>
                <div className="shrink-0 text-right">
                    <p className="text-sm font-semibold tabular-nums text-slate-900">{betrag != null ? `${formatEuro(betrag)} €` : '–'}</p>
                    {betrag != null && <p className="text-xs text-slate-500">{istKostenstelle ? 'netto' : 'brutto'}</p>}
                </div>
                <input
                    type="text"
                    value={beschreibung}
                    onChange={e => onBeschreibung(e.target.value)}
                    placeholder="Beschreibung (optional)"
                    aria-label={`Beschreibung für ${name}`}
                    maxLength={255}
                    className="w-48 shrink-0 rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-inset focus:ring-rose-500"
                />
                <button
                    type="button"
                    onClick={onEntfernen}
                    aria-label={`${name} entfernen`}
                    title="Entfernen – die Positionen sind danach wieder offen"
                    className="shrink-0 rounded p-1 text-slate-400 hover:bg-rose-50 hover:text-red-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                >
                    <Trash2 className="h-4 w-4" />
                </button>
            </div>
            {istKostenstelle && (
                <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-2 pl-10">
                    <label htmlFor={streckungId} className="text-xs text-slate-600">Kosten verteilen über</label>
                    <input
                        id={streckungId}
                        type="text"
                        inputMode="numeric"
                        value={String(jahre)}
                        onFocus={e => e.target.select()}
                        onChange={e => {
                            const zahl = Number.parseInt(e.target.value.replace(/\D/g, ''), 10);
                            onStreckung(Number.isFinite(zahl) ? Math.max(1, Math.min(20, zahl)) : 1);
                        }}
                        className="w-12 rounded-lg border border-slate-200 px-2 py-1 text-center text-sm focus:outline-none focus:ring-2 focus:ring-inset focus:ring-rose-500"
                    />
                    <span className="text-xs text-slate-600">{jahre === 1 ? 'Jahr' : 'Jahre'}</span>
                    {jahre > 1 && betrag != null && (
                        <span className="ml-1 text-xs font-medium text-rose-600">≈ {formatEuro(betrag / jahre)} € / Jahr ab {rechnungsJahr}</span>
                    )}
                </div>
            )}
        </div>
    );
}

/** Ablagefläche „Nicht zugeordnet“: hierher ziehen hebt die Zuordnung auf. */
export function NichtZugeordnetAblage({ anzahlOffen }: { anzahlOffen: number }) {
    const { setNodeRef, isOver } = useDroppable({ id: ablageId('') });
    const gezogen = useGezogeneAnzahl();
    return (
        <div
            ref={setNodeRef}
            role="group"
            aria-label="Nicht zugeordnet"
            className={cn(
                'flex items-center gap-3 rounded-lg border border-dashed px-3 py-2 text-sm transition-colors',
                isOver ? 'border-rose-400 bg-rose-50 text-rose-700 ring-2 ring-inset ring-rose-200' : 'border-slate-300 bg-slate-50 text-slate-600',
            )}
        >
            <CircleDashed className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <span className="font-medium">Nicht zugeordnet</span>
            <span className="text-xs">
                {isOver
                    ? `${positionenText(gezogen)} – Zuordnung aufheben`
                    : gezogen > 0 ? 'Hierher ziehen hebt die Zuordnung auf' : positionenText(anzahlOffen)}
            </span>
        </div>
    );
}
