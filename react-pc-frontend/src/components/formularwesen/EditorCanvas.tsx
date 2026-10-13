import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type RefObject } from 'react';
import { Copy, Trash2 } from 'lucide-react';
import type { FormBlock } from '../../types';
import { cn } from '../../lib/utils';
import { MIN_HEIGHT, MIN_WIDTH, PAGE_FORMATS } from './constants';
import { BlockInhalt } from './BlockInhalt';
import { NormOverlay } from './NormOverlay';
import { AnschriftHinweis } from './AnschriftHinweis';
import { sichereBildQuelle } from './bildQuelle';
import { einrastZiele, rasteEin, rasteWert, type Fuehrungslinien } from './einrasten';
import { BAUSTEIN_ZIEH_TYP, bausteinName, istBausteinArt, type BausteinArt } from './bausteinVorlagen';
import { formatiereMm, type AnschriftStatus, type Briefform } from './normMasse';

const SEITE = PAGE_FORMATS.A4;
/** Fangbereich fürs Einrasten in Bildschirmpixeln. */
const FANG_PX = 6;
/** Erst ab dieser Mausbewegung (px) wird aus einem Klick ein Verschieben. */
const ZUG_SCHWELLE_PX = 3;

type Richtung = 'n' | 's' | 'o' | 'w' | 'no' | 'nw' | 'so' | 'sw';
const GRIFFE: { richtung: Richtung; links: string; oben: string; cursor: string }[] = [
    { richtung: 'nw', links: '0%', oben: '0%', cursor: 'nwse-resize' },
    { richtung: 'n', links: '50%', oben: '0%', cursor: 'ns-resize' },
    { richtung: 'no', links: '100%', oben: '0%', cursor: 'nesw-resize' },
    { richtung: 'o', links: '100%', oben: '50%', cursor: 'ew-resize' },
    { richtung: 'so', links: '100%', oben: '100%', cursor: 'nwse-resize' },
    { richtung: 's', links: '50%', oben: '100%', cursor: 'ns-resize' },
    { richtung: 'sw', links: '0%', oben: '100%', cursor: 'nesw-resize' },
    { richtung: 'w', links: '0%', oben: '50%', cursor: 'ew-resize' },
];

interface Zug {
    art: 'ziehen' | 'groesse';
    id: string;
    richtung?: Richtung;
    startX: number;
    startY: number;
    ursprung: { x: number; y: number; width: number; height: number };
    ziele: Fuehrungslinien;
    gruppe: string;
    bewegt: boolean;
}

const KEINE_LINIEN: Fuehrungslinien = { senkrecht: [], waagerecht: [] };

export interface EditorCanvasProps {
    items: FormBlock[];
    seite: number;
    ausgewaehltId: string | null;
    zoom: number;
    /** Scroll-Bereich um die Seite – daran misst der Zoom „Einpassen“. */
    bereichRef: RefObject<HTMLDivElement | null>;
    briefpapier: string | null;
    daten: Record<string, string>;
    normAn: boolean;
    normForm: Briefform;
    anschriftStatus: AnschriftStatus;
    onAuswaehlen: (id: string | null) => void;
    onAendern: (id: string, updates: Partial<FormBlock>, gruppe?: string) => void;
    onLoeschen: (id: string) => void;
    onDuplizieren: () => void;
    onAblegen: (art: BausteinArt, mitte: { x: number; y: number }) => void;
    onAnschriftInsFenster: () => void;
}

/** Die A4-Seite zum Gestalten: Bausteine ziehen, Größe ändern, einrasten, Norm-Hilfen. */
export default function EditorCanvas({
    items, seite, ausgewaehltId, zoom, bereichRef, briefpapier, daten,
    normAn, normForm, anschriftStatus,
    onAuswaehlen, onAendern, onLoeschen, onDuplizieren, onAblegen, onAnschriftInsFenster,
}: EditorCanvasProps) {
    const seiteRef = useRef<HTMLDivElement>(null);
    const zugRef = useRef<Zug | null>(null);
    const zugNummer = useRef(0);
    const [linien, setLinien] = useState<Fuehrungslinien>(KEINE_LINIEN);
    const [ablageAktiv, setAblageAktiv] = useState(false);

    // Der Fenster-Listener braucht immer die neuesten Werte, ohne sich neu anzumelden.
    const aktuell = useRef({ zoom, onAendern });
    useEffect(() => { aktuell.current = { zoom, onAendern }; });

    useEffect(() => {
        const bewegen = (e: PointerEvent) => {
            const zug = zugRef.current;
            if (!zug) return;
            const { zoom: z, onAendern: aendern } = aktuell.current;
            const dxPx = e.clientX - zug.startX;
            const dyPx = e.clientY - zug.startY;
            if (!zug.bewegt && Math.hypot(dxPx, dyPx) < ZUG_SCHWELLE_PX) return;
            zug.bewegt = true;
            const dx = dxPx / z;
            const dy = dyPx / z;
            const fang = FANG_PX / z;
            const u = zug.ursprung;

            if (zug.art === 'ziehen') {
                const ergebnis = rasteEin(u.x + dx, u.y + dy, u.width, u.height, zug.ziele, fang);
                aendern(zug.id, { x: ergebnis.x, y: ergebnis.y }, zug.gruppe);
                setLinien(ergebnis.linien);
                return;
            }

            const r = zug.richtung ?? 'so';
            let links = u.x;
            let oben = u.y;
            let rechts = u.x + u.width;
            let unten = u.y + u.height;
            const senkrecht: number[] = [];
            const waagerecht: number[] = [];
            const kante = (wert: number, ziele: number[], sammeln: number[]) => {
                const ergebnis = rasteWert(wert, ziele, fang);
                if (ergebnis.linie !== null) sammeln.push(ergebnis.linie);
                return ergebnis.wert;
            };
            if (r.includes('w')) links = Math.min(kante(u.x + dx, zug.ziele.senkrecht, senkrecht), rechts - MIN_WIDTH);
            if (r.includes('o')) rechts = Math.max(kante(rechts + dx, zug.ziele.senkrecht, senkrecht), links + MIN_WIDTH);
            if (r.includes('n')) oben = Math.min(kante(u.y + dy, zug.ziele.waagerecht, waagerecht), unten - MIN_HEIGHT);
            if (r.includes('s')) unten = Math.max(kante(unten + dy, zug.ziele.waagerecht, waagerecht), oben + MIN_HEIGHT);
            aendern(zug.id, { x: links, y: oben, width: Math.round((rechts - links) * 100) / 100, height: Math.round((unten - oben) * 100) / 100 }, zug.gruppe);
            setLinien({ senkrecht, waagerecht });
        };
        const loslassen = () => {
            if (!zugRef.current) return;
            zugRef.current = null;
            setLinien(KEINE_LINIEN);
        };
        window.addEventListener('pointermove', bewegen);
        window.addEventListener('pointerup', loslassen);
        window.addEventListener('pointercancel', loslassen);
        return () => {
            window.removeEventListener('pointermove', bewegen);
            window.removeEventListener('pointerup', loslassen);
            window.removeEventListener('pointercancel', loslassen);
        };
    }, []);

    const zugStarten = (e: ReactPointerEvent, item: FormBlock, art: Zug['art'], richtung?: Richtung) => {
        if (e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        onAuswaehlen(item.id);
        zugNummer.current += 1;
        zugRef.current = {
            art, id: item.id, richtung,
            startX: e.clientX, startY: e.clientY,
            ursprung: { x: item.x, y: item.y, width: item.width, height: item.height },
            ziele: einrastZiele(items, item.id, seite, normAn ? normForm : null),
            gruppe: `zug-${zugNummer.current}`,
            bewegt: false,
        };
    };

    const ablegen = (e: React.DragEvent) => {
        setAblageAktiv(false);
        const art = e.dataTransfer.getData(BAUSTEIN_ZIEH_TYP);
        const rahmen = seiteRef.current?.getBoundingClientRect();
        if (!istBausteinArt(art) || !rahmen) return;
        e.preventDefault();
        onAblegen(art, { x: (e.clientX - rahmen.left) / zoom, y: (e.clientY - rahmen.top) / zoom });
    };

    const seitenItems = items.filter(i => i.page === seite).sort((a, b) => a.z - b.z);
    const ausgewaehlt = seitenItems.find(i => i.id === ausgewaehltId) ?? null;
    const bild = sichereBildQuelle(briefpapier);
    const zeigeNorm = normAn && seite === 1;

    return (
        <div className="flex min-h-0 flex-1 flex-col">
            <div
                ref={bereichRef}
                data-testid="editor-arbeitsflaeche"
                // isolate: Auswahlrahmen und Griffe (z-index 1000) bleiben unter Dialogen wie der Vorschau.
                className="relative isolate min-h-0 flex-1 overflow-auto bg-slate-100"
                onPointerDown={() => onAuswaehlen(null)}
            >
                <div className="flex min-h-full min-w-full w-max p-6">
                    <div
                        ref={seiteRef}
                        data-testid="editor-seite"
                        aria-label={`Seite ${seite === 1 ? '1' : '2 und folgende'} der Vorlage`}
                        className={cn(
                            'relative m-auto shrink-0 bg-white shadow-[0_1px_3px_rgba(15,23,42,0.08),0_12px_32px_-8px_rgba(15,23,42,0.18)] ring-1 ring-slate-200',
                            ablageAktiv && 'ring-2 ring-rose-400',
                        )}
                        style={{ width: SEITE.width * zoom, height: SEITE.height * zoom, '--r1': `${1 / zoom}px` } as CSSProperties}
                        onPointerDown={e => { e.stopPropagation(); onAuswaehlen(null); }}
                        onDragOver={e => {
                            if (!e.dataTransfer.types.includes(BAUSTEIN_ZIEH_TYP)) return;
                            e.preventDefault();
                            e.dataTransfer.dropEffect = 'copy';
                            setAblageAktiv(true);
                        }}
                        onDragLeave={() => setAblageAktiv(false)}
                        onDrop={ablegen}
                    >
                        {/* Seite in pt, auf den Zoom skaliert */}
                        <div className="absolute left-0 top-0 overflow-hidden" style={{ width: SEITE.width, height: SEITE.height, transform: `scale(${zoom})`, transformOrigin: 'top left' }}>
                            {bild && (
                                <img src={bild} alt="" draggable={false} className="pointer-events-none absolute inset-0 h-full w-full select-none object-cover" />
                            )}
                            {seitenItems.map(item => {
                                const name = bausteinName(item);
                                const gewaehlt = item.id === ausgewaehltId;
                                return (
                                    <div
                                        key={item.id}
                                        // Kein Knopf, sondern ein verschiebbares Objekt auf der Seite: Gruppe mit eigenem Namen,
                                        // per Tab erreichbar, Enter/Leertaste wählt aus.
                                        role="group"
                                        aria-roledescription="Baustein"
                                        tabIndex={0}
                                        aria-label={gewaehlt ? `${name} (ausgewählt)` : name}
                                        data-testid="editor-baustein"
                                        data-baustein={name}
                                        className={cn(
                                            'absolute cursor-move select-none outline-offset-0 focus:outline-none',
                                            !gewaehlt && 'hover:outline-dashed hover:outline-slate-400 hover:[outline-width:var(--r1)] focus-visible:outline-dashed focus-visible:outline-rose-500 focus-visible:[outline-width:var(--r1)]',
                                        )}
                                        style={{ left: item.x, top: item.y, width: item.width, height: item.height, zIndex: item.z + 1 }}
                                        onPointerDown={e => zugStarten(e, item, 'ziehen')}
                                        onKeyDown={e => {
                                            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onAuswaehlen(item.id); }
                                        }}
                                    >
                                        <div className="h-full w-full overflow-hidden">
                                            <BlockInhalt item={item} daten={daten} imEditor />
                                        </div>
                                    </div>
                                );
                            })}
                            {zeigeNorm && <NormOverlay form={normForm} zoom={zoom} status={anschriftStatus} />}
                        </div>

                        {/* Bildschirm-Ebene: Auswahlrahmen, Griffe, Hilfslinien bleiben bei jedem Zoom gleich groß */}
                        <div className="pointer-events-none absolute inset-0" style={{ zIndex: 1000 }}>
                            {linien.senkrecht.map(x => (
                                <div key={`s-${x}`} className="absolute bottom-0 top-0 w-px bg-rose-500" style={{ left: x * zoom }} />
                            ))}
                            {linien.waagerecht.map(y => (
                                <div key={`w-${y}`} className="absolute left-0 right-0 h-px bg-rose-500" style={{ top: y * zoom }} />
                            ))}
                            {ausgewaehlt && (
                                <Auswahl
                                    item={ausgewaehlt}
                                    zoom={zoom}
                                    onGroesse={(e, richtung) => zugStarten(e, ausgewaehlt, 'groesse', richtung)}
                                    onDuplizieren={onDuplizieren}
                                    onLoeschen={() => onLoeschen(ausgewaehlt.id)}
                                />
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {zeigeNorm && (
                <div className="border-t border-slate-200 bg-white px-3 py-2">
                    <AnschriftHinweis kompakt status={anschriftStatus} onInsFenster={onAnschriftInsFenster} />
                </div>
            )}
        </div>
    );
}

interface AuswahlProps {
    item: FormBlock;
    zoom: number;
    onGroesse: (e: ReactPointerEvent, richtung: Richtung) => void;
    onDuplizieren: () => void;
    onLoeschen: () => void;
}

/** Rahmen, acht Griffe, Name mit Maßen und kleine Aktionsleiste am gewählten Baustein. */
function Auswahl({ item, zoom, onGroesse, onDuplizieren, onLoeschen }: AuswahlProps) {
    const oben = item.y * zoom;
    const untenBeschriften = oben < 34;
    const leiste = 'pointer-events-auto inline-flex h-7 w-7 items-center justify-center rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';
    return (
        <div
            data-testid="editor-auswahl"
            className="absolute rounded-[1px] outline outline-2 outline-rose-500"
            style={{ left: item.x * zoom, top: oben, width: item.width * zoom, height: item.height * zoom }}
        >
            <div
                className={cn('absolute left-0 flex items-center gap-1 whitespace-nowrap', untenBeschriften ? 'top-full mt-2' : 'bottom-full mb-2')}
            >
                <span className="rounded-md bg-slate-900 px-2 py-1 text-[11px] font-medium text-white shadow-sm">
                    {bausteinName(item)}
                    <span className="ml-1.5 font-normal tabular-nums text-slate-300">
                        {formatiereMm(item.width)} × {formatiereMm(item.height)} mm
                    </span>
                </span>
                <span className="flex items-center rounded-lg border border-slate-200 bg-white p-0.5 shadow-sm">
                    <button
                        type="button"
                        className={leiste}
                        aria-label="Baustein duplizieren"
                        title="Duplizieren (Strg+D)"
                        onPointerDown={e => e.stopPropagation()}
                        onClick={onDuplizieren}
                    >
                        <Copy aria-hidden="true" className="h-3.5 w-3.5" />
                    </button>
                    <button
                        type="button"
                        className={cn(leiste, 'hover:bg-red-50 hover:text-red-600')}
                        aria-label="Baustein löschen"
                        title="Löschen (Entf)"
                        onPointerDown={e => e.stopPropagation()}
                        onClick={onLoeschen}
                    >
                        <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                    </button>
                </span>
            </div>
            {GRIFFE.map(g => (
                <div
                    key={g.richtung}
                    data-griff={g.richtung}
                    aria-hidden="true"
                    className="pointer-events-auto absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-[2px] border border-rose-500 bg-white shadow-sm"
                    style={{ left: g.links, top: g.oben, cursor: g.cursor }}
                    onPointerDown={e => onGroesse(e, g.richtung)}
                />
            ))}
        </div>
    );
}
