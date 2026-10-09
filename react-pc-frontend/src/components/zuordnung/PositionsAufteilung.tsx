import { memo, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import {
    DndContext,
    DragOverlay,
    PointerSensor,
    pointerWithin,
    useDraggable,
    useSensor,
    useSensors,
    type DragEndEvent,
    type DragStartEvent,
} from '@dnd-kit/core';
import { AlertTriangle, Briefcase, CheckCircle2, GripVertical, ListChecks, RefreshCw, Sparkles } from 'lucide-react';
import { Button } from '../ui/button';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import { cn } from '../../lib/utils';
import {
    formatEuro,
    formatMenge,
    gezogenePositionen,
    istWare,
    zielAusAblage,
    type DokumentPosition,
    type ZielSchluessel,
} from './positionen';
import { NichtZugeordnetAblage } from './PositionsZielZeile';
import type { ZielFarbe } from './zielFarben';
import type { PositionsAufteilung as Aufteilung } from './usePositionsAufteilung';

/** Ein Ziel, das im Dialog schon hinzugefügt ist (Projekt oder Kostenstelle). */
export interface AufteilungsZiel {
    schluessel: ZielSchluessel;
    name: string;
    art: 'projekt' | 'kostenstelle';
    farbe: ZielFarbe;
}

interface PositionsAufteilungProps {
    aufteilung: Aufteilung;
    ziele: AufteilungsZiel[];
    /** Die Zielkarten (Ablageflächen) – baut der Dialog, weil dort Beschreibung und Streckung liegen. */
    zielKarten: ReactNode;
    /** Öffnet die Projektsuche. */
    onProjektHinzufuegen: () => void;
}

const OFFEN = '__offen__';

/** Spaltenraster – Kopf und Zeilen teilen es, damit der Kopf beim Scrollen stehen bleiben kann. */
const RASTER = 'grid grid-cols-[1rem_1.5rem_2rem_minmax(0,1fr)_4.5rem_5.75rem_6rem_14rem] items-center gap-x-2';

interface ZiehDaten {
    positionId: number;
    anzahl: number;
}

/**
 * Aufteilung einer Lieferanten-Rechnung nach Positionen: Jede Warenposition
 * geht ganz an genau ein Projekt oder eine Kostenstelle – per Ziehen auf die
 * Zielkarte oder per Auswahlliste in der Zeile. Nebenkosten und Rabatte
 * verteilt das Backend anteilig nach Warenwert.
 */
export function PositionsAufteilung({ aufteilung, ziele, zielKarten, onProjektHinzufuegen }: PositionsAufteilungProps) {
    const toast = useToast();
    const { uebersicht, positionen, zuweisungen, weiseZu, weiseOffeneZu, anzahlOffen, anzahlWare } = aufteilung;
    const [ausgewaehlt, setAusgewaehlt] = useState<Set<number>>(() => new Set());
    const [nurOffene, setNurOffene] = useState(false);
    const [gezogen, setGezogen] = useState<ZiehDaten | null>(null);

    // Fehler der Live-Vorschau einmal melden, nicht bei jedem neuen Versuch erneut.
    const { vorschauFehler } = aufteilung;
    useEffect(() => {
        if (vorschauFehler) toast.error(vorschauFehler);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [vorschauFehler]);

    const sensoren = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

    const farbeJeZiel = useMemo(() => new Map(ziele.map(z => [z.schluessel, z])), [ziele]);
    const zielOptionen = useMemo(() => ziele.map(z => ({
        value: z.schluessel,
        label: z.name,
        gruppe: z.art === 'projekt' ? 'Projekte' : 'Kostenstellen',
    })), [ziele]);
    const zeilenOptionen = useMemo(() => [{ value: OFFEN, label: 'Nicht zugeordnet' }, ...zielOptionen], [zielOptionen]);

    const sichtbar = useMemo(
        () => (nurOffene ? positionen.filter(p => !istWare(p) || !zuweisungen[p.id]) : positionen),
        [nurOffene, positionen, zuweisungen],
    );
    const sichtbareWare = useMemo(() => sichtbar.filter(istWare).map(p => p.id), [sichtbar]);
    const alleSichtbarenGewaehlt = sichtbareWare.length > 0 && sichtbareWare.every(id => ausgewaehlt.has(id));
    const anzahlMarkiert = useMemo(
        () => positionen.filter(p => istWare(p) && ausgewaehlt.has(p.id)).length,
        [positionen, ausgewaehlt],
    );
    const gezogeneIds = useMemo(
        () => new Set(gezogen ? gezogenePositionen(gezogen.positionId, ausgewaehlt, positionen) : []),
        [gezogen, ausgewaehlt, positionen],
    );

    const waehleZiel = useCallback((positionId: number, ziel: string) => {
        weiseZu([positionId], ziel === OFFEN ? '' : ziel);
    }, [weiseZu]);

    const schalteAuswahl = useCallback((positionId: number, an: boolean) => {
        setAusgewaehlt(alt => {
            const neu = new Set(alt);
            if (an) neu.add(positionId);
            else neu.delete(positionId);
            return neu;
        });
    }, []);

    const schalteAlle = (an: boolean) => {
        setAusgewaehlt(alt => {
            const neu = new Set(alt);
            for (const id of sichtbareWare) {
                if (an) neu.add(id);
                else neu.delete(id);
            }
            return neu;
        });
    };

    const ausgewaehlteZuordnen = (ziel: string) => {
        if (!ziel || ausgewaehlt.size === 0) return;
        weiseZu([...ausgewaehlt], ziel);
        setAusgewaehlt(new Set());
    };

    const ziehenBeginnt = (event: DragStartEvent) => {
        const daten = event.active.data.current as ZiehDaten | undefined;
        if (daten) setGezogen(daten);
    };

    const ablegen = (event: DragEndEvent) => {
        setGezogen(null);
        const daten = event.active.data.current as ZiehDaten | undefined;
        const ziel = zielAusAblage(event.over?.id);
        if (!daten || ziel === null) return;
        const ids = gezogenePositionen(daten.positionId, ausgewaehlt, positionen);
        if (ids.length === 0) return;
        weiseZu(ids, ziel);
        if (ausgewaehlt.has(daten.positionId)) setAusgewaehlt(new Set());
    };

    const auslesen = async () => {
        try {
            const ergebnis = await aufteilung.auslesen(ziele.length === 1 ? ziele[0].schluessel : undefined);
            if (ergebnis && ergebnis.positionen.length === 0) {
                toast.error('Auf dem Dokument wurden keine Positionen gefunden.');
            }
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Positionen konnten nicht ausgelesen werden.');
        }
    };

    if (!uebersicht) return null;

    const keineZiele = ziele.length === 0;
    const zielGesperrtGrund = keineZiele ? 'Wählen Sie oben zuerst ein Projekt oder eine Kostenstelle aus.' : undefined;

    const zieleBereich = (
        <div className="space-y-2">
            <div className="flex items-baseline justify-between gap-2">
                <h4 className="text-sm font-semibold text-slate-700">Projekte und Kostenstellen</h4>
                {!keineZiele && positionen.length > 0 && (
                    <p className="text-xs text-slate-500">Positionen am Griff hierher ziehen oder rechts in der Zeile auswählen</p>
                )}
            </div>
            {keineZiele ? (
                <div className="rounded-lg border-2 border-dashed border-rose-200 bg-rose-50/50 px-4 py-4 text-center">
                    <p className="text-sm font-medium text-slate-700">Zuerst die Projekte auswählen, die zu diesem Beleg gehören.</p>
                    <p className="mt-0.5 text-xs text-slate-500">Danach ziehen Sie die Positionen auf das passende Projekt.</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={onProjektHinzufuegen}>
                        <Briefcase className="h-4 w-4" aria-hidden="true" />
                        Projekte auswählen
                    </Button>
                </div>
            ) : (
                <div className="max-h-[35vh] space-y-2 overflow-y-auto">
                    {zielKarten}
                    {positionen.length > 0 && <NichtZugeordnetAblage anzahlOffen={anzahlOffen} />}
                </div>
            )}
        </div>
    );

    const abweichungsHinweis = uebersicht.abweichungAuffaellig && positionen.length > 0 && (
        <div role="note" className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p>
                Die Positionen ergeben {formatEuro(uebersicht.summePositionen)} €, die Rechnung {formatEuro(uebersicht.betragNetto)} € netto
                – die Differenz wird anteilig verteilt.
            </p>
        </div>
    );

    if (positionen.length === 0) {
        return (
            <DndContext sensors={sensoren}>
                <div className="space-y-4">
                    <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">{zieleBereich}</div>
                    <section aria-label="Aufteilung nach Positionen" className="rounded-xl border-2 border-dashed border-slate-200 px-6 py-8 text-center">
                        {aufteilung.auslesenLaeuft ? (
                            <div role="status" className="space-y-2">
                                <RefreshCw className="mx-auto mb-1 h-7 w-7 text-rose-600 motion-safe:animate-spin" aria-hidden="true" />
                                <p className="font-medium text-slate-700">Die KI liest die Positionen …</p>
                                <p className="text-sm text-slate-500">Das kann bis zu einer Minute dauern.</p>
                            </div>
                        ) : (
                            <>
                                <ListChecks className="mx-auto mb-3 h-8 w-8 text-slate-400" aria-hidden="true" />
                                <p className="font-medium text-slate-700">Für diese Rechnung sind noch keine Positionen ausgelesen.</p>
                                <p className="mt-1 text-sm text-slate-500">
                                    Die KI liest Artikel, Mengen und Preise aus dem Dokument. Danach ordnen Sie jede Position einem Projekt zu.
                                </p>
                                {aufteilung.auslesenFehler && (
                                    <p role="alert" className="mx-auto mt-3 max-w-md rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
                                        {aufteilung.auslesenFehler}
                                    </p>
                                )}
                                <Button variant="outline" size="sm" className="mt-4" onClick={auslesen}>
                                    <Sparkles className="h-4 w-4" aria-hidden="true" />
                                    {aufteilung.auslesenFehler ? 'Nochmal versuchen' : 'Positionen auslesen'}
                                </Button>
                            </>
                        )}
                    </section>
                </div>
            </DndContext>
        );
    }

    const gezogenePosition = gezogen ? positionen.find(p => p.id === gezogen.positionId) : undefined;

    return (
        <DndContext
            sensors={sensoren}
            collisionDetection={pointerWithin}
            autoScroll={false}
            onDragStart={ziehenBeginnt}
            onDragEnd={ablegen}
            onDragCancel={() => setGezogen(null)}
        >
            <section aria-label="Aufteilung nach Positionen" className="space-y-3">
                {abweichungsHinweis}

                <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
                    {/* Ziele, Werkzeugleiste und Spaltenkopf bleiben beim Scrollen stehen – so sind
                        Ablageflächen und Positionen beim Ziehen gleichzeitig im Blick.
                        -top-6 gleicht das p-6 der Dialogspalte aus. */}
                    <div className="sticky -top-6 z-10 rounded-t-xl border-b border-slate-200 bg-white">
                        <div className="px-3 pt-3">{zieleBereich}</div>

                        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-3 pt-3">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-semibold text-slate-700">Positionen</h4>
                                {anzahlOffen > 0 ? (
                                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                                        Noch {anzahlOffen} {anzahlOffen === 1 ? 'Position' : 'Positionen'} nicht zugeordnet
                                    </span>
                                ) : (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800">
                                        <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                                        Alle {anzahlWare} zugeordnet
                                    </span>
                                )}
                            </div>
                            <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
                                <input type="checkbox" className="h-4 w-4" checked={nurOffene} onChange={e => setNurOffene(e.target.checked)} />
                                Nur offene zeigen
                            </label>
                        </div>

                        {!keineZiele && (
                            <div className="grid grid-cols-2 gap-2 px-3 pt-2">
                                <div title={ausgewaehlt.size === 0 ? 'Erst Positionen über die Kästchen links markieren.' : undefined}>
                                    <Select
                                        options={zielOptionen}
                                        value=""
                                        onChange={ausgewaehlteZuordnen}
                                        disabled={ausgewaehlt.size === 0}
                                        placeholder={anzahlMarkiert > 0 ? `${anzahlMarkiert} markierte zuordnen zu …` : 'Ausgewählte zuordnen zu …'}
                                        aria-label="Ausgewählte zuordnen zu"
                                    />
                                </div>
                                <div title={anzahlOffen === 0 ? 'Alle Positionen sind schon zugeordnet.' : undefined}>
                                    <Select
                                        options={zielOptionen}
                                        value=""
                                        onChange={weiseOffeneZu}
                                        disabled={anzahlOffen === 0}
                                        placeholder="Alle übrigen zu …"
                                        aria-label="Alle übrigen zu"
                                    />
                                </div>
                            </div>
                        )}

                        <div className={cn(RASTER, 'mt-2 px-3 py-2 text-xs font-medium uppercase tracking-wide text-slate-500')}>
                            <span />
                            <span>
                                <input
                                    type="checkbox"
                                    className="h-4 w-4"
                                    aria-label="Alle Warenpositionen markieren"
                                    checked={alleSichtbarenGewaehlt}
                                    disabled={sichtbareWare.length === 0 || keineZiele}
                                    onChange={e => schalteAlle(e.target.checked)}
                                />
                            </span>
                            <span>Nr.</span>
                            <span>Bezeichnung</span>
                            <span className="text-right">Menge</span>
                            <span className="text-right">Einzelpreis</span>
                            <span className="text-right">Gesamt</span>
                            <span>Projekt / Kostenstelle</span>
                        </div>
                    </div>

                    <div role="list" aria-label="Positionen der Rechnung" className="divide-y divide-slate-100">
                        {sichtbar.length === 0 ? (
                            <p className="px-3 py-6 text-center text-sm text-slate-500">Alle Positionen sind zugeordnet.</p>
                        ) : sichtbar.map(position => {
                            const ziel = zuweisungen[position.id] ?? '';
                            const markiert = ausgewaehlt.has(position.id);
                            return (
                                <PositionsZeile
                                    key={position.id}
                                    position={position}
                                    ziel={ziel}
                                    farbe={farbeJeZiel.get(ziel)?.farbe}
                                    zielName={farbeJeZiel.get(ziel)?.name}
                                    ausgewaehlt={markiert}
                                    anzahlBeimZiehen={markiert ? anzahlMarkiert : 1}
                                    wirdGezogen={gezogeneIds.has(position.id)}
                                    optionen={zeilenOptionen}
                                    gesperrtGrund={zielGesperrtGrund}
                                    onZiel={waehleZiel}
                                    onAuswahl={schalteAuswahl}
                                />
                            );
                        })}
                    </div>
                </div>

                <VorschauZusammenfassung aufteilung={aufteilung} />
            </section>

            {createPortal(
                <DragOverlay dropAnimation={null} zIndex={70}>
                    {gezogen && (
                        <div className="inline-flex max-w-xs items-center gap-2 rounded-lg border border-rose-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 shadow-lg">
                            <GripVertical className="h-4 w-4 shrink-0 text-rose-500" aria-hidden="true" />
                            <span className="truncate">
                                {gezogen.anzahl > 1 ? `${gezogen.anzahl} Positionen` : (gezogenePosition?.bezeichnung || `Position ${gezogenePosition?.positionNr ?? ''}`)}
                            </span>
                        </div>
                    )}
                </DragOverlay>,
                document.body,
            )}
        </DndContext>
    );
}

interface PositionsZeileProps {
    position: DokumentPosition;
    ziel: ZielSchluessel;
    farbe: ZielFarbe | undefined;
    zielName: string | undefined;
    ausgewaehlt: boolean;
    /** Wie viele Positionen mitwandern, wenn diese Zeile gezogen wird. */
    anzahlBeimZiehen: number;
    wirdGezogen: boolean;
    optionen: { value: string; label: string; gruppe?: string }[];
    gesperrtGrund?: string;
    onZiel: (positionId: number, ziel: string) => void;
    onAuswahl: (positionId: number, an: boolean) => void;
}

/** Eine Zeile; memo, damit bei 100+ Positionen nur die geänderte Zeile neu zeichnet. */
const PositionsZeile = memo(function PositionsZeile({
    position, ziel, farbe, zielName, ausgewaehlt, anzahlBeimZiehen, wirdGezogen, optionen, gesperrtGrund, onZiel, onAuswahl,
}: PositionsZeileProps) {
    const ware = istWare(position);
    const name = position.bezeichnung || `Position ${position.positionNr}`;
    const { setNodeRef, listeners } = useDraggable({
        id: `position:${position.id}`,
        data: { positionId: position.id, anzahl: anzahlBeimZiehen } satisfies ZiehDaten,
        disabled: !ware || !!gesperrtGrund,
    });
    return (
        <div
            ref={setNodeRef}
            role="listitem"
            aria-label={`Position ${position.positionNr}: ${name}`}
            className={cn(
                RASTER,
                'border-l-4 px-3 py-2 text-sm transition-opacity',
                ware && farbe ? farbe.rand : 'border-l-transparent',
                ware ? 'text-slate-800' : 'bg-slate-50 text-slate-500',
                ausgewaehlt && 'bg-rose-50',
                wirdGezogen && 'opacity-40',
            )}
        >
            <span>
                {ware && !gesperrtGrund && (
                    <span
                        {...listeners}
                        aria-hidden="true"
                        data-griff
                        title="Ziehen, um die Position zuzuordnen"
                        className="flex h-8 cursor-grab touch-none items-center text-slate-400 hover:text-rose-600 active:cursor-grabbing"
                    >
                        <GripVertical className="h-4 w-4" />
                    </span>
                )}
            </span>
            <span>
                {ware && (
                    <input
                        type="checkbox"
                        className="h-4 w-4"
                        aria-label={`Position ${position.positionNr} markieren`}
                        checked={ausgewaehlt}
                        disabled={!!gesperrtGrund}
                        onChange={e => onAuswahl(position.id, e.target.checked)}
                    />
                )}
            </span>
            <span className="tabular-nums text-slate-500">{position.positionNr}</span>
            <span className="min-w-0">
                <span className="block break-words">{name}</span>
                {position.externeArtikelnummer && (
                    <span className="block text-xs text-slate-400">Art.-Nr. {position.externeArtikelnummer}</span>
                )}
            </span>
            <span className="text-right tabular-nums">
                {formatMenge(position.menge)}{position.mengeneinheit ? ` ${position.mengeneinheit}` : ''}
            </span>
            <span className="text-right tabular-nums">
                {position.einzelpreis != null ? `${formatEuro(position.einzelpreis)} €` : '–'}
                {position.preiseinheit && <span className="block text-xs text-slate-400">je {position.preiseinheit}</span>}
            </span>
            <span className="text-right font-medium tabular-nums">{formatEuro(position.gesamtpreisNetto)} €</span>
            <span>
                {ware ? (
                    // Lange Projektnamen dürfen gekürzt werden – der volle Name steht im Tooltip und in der Zielliste.
                    <div className="flex items-center gap-2" title={gesperrtGrund ?? zielName} data-kuerzung-erlaubt>
                        <span
                            aria-hidden="true"
                            className={cn('h-2.5 w-2.5 shrink-0 rounded-full', farbe ? farbe.punkt : 'border border-dashed border-slate-300')}
                        />
                        <Select
                            options={optionen}
                            value={ziel}
                            onChange={wert => onZiel(position.id, wert)}
                            disabled={!!gesperrtGrund}
                            placeholder="Zuordnen zu …"
                            aria-label={`Ziel für Position ${position.positionNr}`}
                        />
                    </div>
                ) : (
                    <span className="text-xs italic">
                        {position.positionsArt === 'RABATT' ? 'Rabatt' : 'Nebenkosten'} – wird anteilig verteilt
                    </span>
                )}
            </span>
        </div>
    );
});

function VorschauZusammenfassung({ aufteilung }: { aufteilung: Aufteilung }) {
    const { vorschau, vorschauLaeuft, uebersicht } = aufteilung;
    const verteilt = vorschau?.ziele.reduce((s, z) => s + (z.betragNetto ?? 0), 0) ?? 0;
    return (
        <div
            aria-live="polite"
            aria-busy={vorschauLaeuft}
            className={cn(
                'rounded-xl border p-4 text-sm transition-opacity',
                vorschauLaeuft && 'opacity-70',
                aufteilung.speicherbar ? 'border-green-200 bg-green-50' : 'border-amber-200 bg-amber-50',
            )}
        >
            <div className="flex items-center justify-between">
                <span>Ware</span>
                <span className="tabular-nums">{formatEuro(vorschau?.warenwert)} €</span>
            </div>
            <div className="mt-1 flex items-center justify-between">
                <span>Nebenkosten und Rabatte <span className="text-slate-500">(anteilig verteilt)</span></span>
                <span className="tabular-nums">{formatEuro(vorschau?.nebenkosten)} €</span>
            </div>
            <div className="mt-1 flex items-center justify-between font-semibold">
                <span className="flex items-center gap-2">
                    Verteilt (netto)
                    {vorschauLaeuft && <RefreshCw className="h-3.5 w-3.5 text-slate-400 motion-safe:animate-spin" aria-label="Wird berechnet" />}
                </span>
                <span className="tabular-nums">
                    {formatEuro(verteilt)} € <span className="font-normal text-slate-500">von {formatEuro(uebersicht?.betragNetto)} €</span>
                </span>
            </div>
            {vorschau?.hinweis && !vorschauLaeuft && (
                <p className="mt-2 text-amber-800">{vorschau.hinweis}</p>
            )}
            {aufteilung.vorschauFehler && (
                <p role="alert" className="mt-2 text-rose-700">{aufteilung.vorschauFehler}</p>
            )}
        </div>
    );
}
