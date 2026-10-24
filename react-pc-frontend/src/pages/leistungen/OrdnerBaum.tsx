import { useEffect, useMemo, useRef, useState, type DragEvent, type KeyboardEvent, type ReactNode } from 'react';
import {
    Briefcase, ChevronDown, ChevronRight, FileText, Folder, FolderInput, FolderOpen, FolderPlus, LayoutGrid, List,
    Loader2, MoreHorizontal, Pencil, Plus, Search, Trash2, X,
} from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import {
    DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import {
    ersteDatei, formatZahl, leistungenWandernHinweis, pfadText, sichtbareZeilen, suche, zaehlerAriaLabel,
    type Baum, type BaumZeile, type Zaehler,
} from './ordnerLogik';
import type { Leistung, ZiehObjekt } from './typen';

/** Schlüssel der Zeile „Alle Ordner (Übersicht)“ in der Tastatur-Bedienung. */
const UEBERSICHT = 'uebersicht';
type ZeilenSchluessel = number | typeof UEBERSICHT;

export interface OrdnerBaumProps {
    baum: Baum;
    zaehler: Map<number, Zaehler>;
    leistungen: Leistung[];
    gewaehlt: number | null;
    aufgeklappt: Set<number>;
    onAufklappen: (id: number, offen: boolean) => void;
    /** `null` = Übersicht. */
    onWaehlen: (id: number | null) => void;
    suchtext: string;
    onSuchtext: (text: string) => void;

    /** Eingabezeile für einen neuen Ordner (`parentId` null = Hauptordner). */
    neuerOrdner: { parentId: number | null } | null;
    onNeuerOrdner: (parentId: number | null) => void;
    onNeuerOrdnerAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint an der Zeile. */
    onNeuerOrdnerSpeichern: (parentId: number | null, name: string) => Promise<void>;

    umbenennenId: number | null;
    onUmbenennen: (id: number) => void;
    onUmbenennenAbbrechen: () => void;
    onUmbenennenSpeichern: (id: number, name: string) => Promise<void>;

    onVerschieben: (id: number) => void;
    onLoeschen: (id: number) => void;

    /** Was gerade innerhalb der Seite gezogen wird (Ordner oder Leistung). */
    ziehen: ZiehObjekt | null;
    onZiehenStart: (objekt: ZiehObjekt) => void;
    onZiehenEnde: () => void;
    /** Darf das gezogene Objekt hier abgelegt werden? (`null` = oberste Ebene) */
    ablageErlaubt: (zielId: number | null) => boolean;
    onAblegen: (zielId: number | null) => void;
    onBildAblegen: (zielId: number, datei: File) => void;

    /** Klick auf einen Leistungs-Treffer der Suche. */
    onLeistungOeffnen: (leistung: Leistung) => void;
}

/**
 * Ordnerbaum links auf der Seite „Leistungen & Kategorien“.
 *
 * Bedienung wie ein Dateibaum: Pfeiltasten wandern (rechts/links klappen auf
 * und zu), Enter wählt, F2 benennt um, Entf löscht. An jeder Zeile stehen bei
 * Hover und Tastatur-Fokus „+ Unterordner“ und ein „…“-Menü. Ordner und
 * Leistungen lassen sich auf Ordner ziehen, Bilddateien aus dem Finder werden
 * zum Ordnerbild. Ungültige Ziele nehmen nichts an (Cursor „verboten“).
 */
export function OrdnerBaum(props: OrdnerBaumProps) {
    const {
        baum, zaehler, leistungen, gewaehlt, aufgeklappt, onAufklappen, onWaehlen, suchtext, onSuchtext,
        neuerOrdner, onNeuerOrdner, ziehen, ablageErlaubt, onLeistungOeffnen,
    } = props;
    const zeilenRefs = useRef(new Map<ZeilenSchluessel, HTMLDivElement>());
    const [aktiv, setAktiv] = useState<ZeilenSchluessel>(gewaehlt ?? UEBERSICHT);
    const [fokusWunsch, setFokusWunsch] = useState<ZeilenSchluessel | null>(null);

    const treffer = useMemo(() => suche(baum, leistungen, suchtext), [baum, leistungen, suchtext]);
    const zeilen = useMemo(() => {
        const offen = treffer ? new Set([...aufgeklappt, ...treffer.aufklappen]) : aufgeklappt;
        if (neuerOrdner?.parentId != null) offen.add(neuerOrdner.parentId);
        return sichtbareZeilen(baum, offen, treffer?.ordnerIds);
    }, [baum, aufgeklappt, treffer, neuerOrdner]);
    const schluessel: ZeilenSchluessel[] = useMemo(() => [UEBERSICHT, ...zeilen.map((z) => z.ordner.id)], [zeilen]);

    // Gewählter Ordner wird zur aktiven Zeile und rückt ins Bild (z. B. nach dem Anlegen).
    useEffect(() => {
        const ziel = gewaehlt ?? UEBERSICHT;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setAktiv(ziel);
        zeilenRefs.current.get(ziel)?.scrollIntoView?.({ block: 'nearest' });
    }, [gewaehlt]);

    useEffect(() => {
        if (fokusWunsch === null) return;
        const element = zeilenRefs.current.get(fokusWunsch);
        if (!element) return;
        element.focus();
        // Erlaubt im Effekt: Der Fokus-Wunsch ist erst nach dem DOM-Zugriff (Ref) erledigt.
        setFokusWunsch(null);
    }, [fokusWunsch, zeilen]);

    // Die aktive Zeile muss sichtbar bleiben (z. B. wenn ihr Eltern-Ordner zugeklappt wurde).
    const aktivSichtbar = schluessel.includes(aktiv) ? aktiv : UEBERSICHT;

    const fokussiere = (ziel: ZeilenSchluessel) => {
        setAktiv(ziel);
        setFokusWunsch(ziel);
    };

    const beiTaste = (event: KeyboardEvent<HTMLDivElement>, zeile: BaumZeile | null) => {
        if (event.target !== event.currentTarget) return;
        const eigener: ZeilenSchluessel = zeile ? zeile.ordner.id : UEBERSICHT;
        const index = schluessel.indexOf(eigener);
        const nimm = (i: number) => { event.preventDefault(); fokussiere(schluessel[Math.max(0, Math.min(schluessel.length - 1, i))]); };
        switch (event.key) {
            case 'ArrowDown': return nimm(index + 1);
            case 'ArrowUp': return nimm(index - 1);
            case 'Home': return nimm(0);
            case 'End': return nimm(schluessel.length - 1);
            case 'ArrowRight':
                event.preventDefault();
                if (zeile?.hatKinder && !zeile.aufgeklappt) onAufklappen(zeile.ordner.id, true);
                else if (zeile?.aufgeklappt) fokussiere(schluessel[index + 1]);
                return;
            case 'ArrowLeft':
                event.preventDefault();
                if (zeile?.aufgeklappt) onAufklappen(zeile.ordner.id, false);
                else if (zeile && zeile.ordner.parentId !== null && baum.byId.has(zeile.ordner.parentId)) fokussiere(zeile.ordner.parentId);
                else if (zeile) fokussiere(UEBERSICHT);
                return;
            case 'Enter':
            case ' ':
                event.preventDefault();
                onWaehlen(zeile ? zeile.ordner.id : null);
                return;
            case 'F2':
                if (zeile) { event.preventDefault(); props.onUmbenennen(zeile.ordner.id); }
                return;
            case 'Delete':
                if (zeile) { event.preventDefault(); props.onLoeschen(zeile.ordner.id); }
                return;
        }
    };

    // Callback-Ref je Zeile: Die Map wird erst beim Einhängen/Aushängen gefüllt, nicht beim Rendern.
    const refSetzen = (key: ZeilenSchluessel, element: HTMLDivElement | null) => {
        if (element) zeilenRefs.current.set(key, element);
        else zeilenRefs.current.delete(key);
    };

    const leistungsTreffer = treffer?.leistungen ?? [];

    return (
        <div className="flex h-full min-h-0 flex-col">
            <div className="flex items-center justify-between gap-2 px-3 pt-3">
                <h2 className="text-sm font-semibold text-slate-900">Ordner</h2>
                <Button size="sm" variant="outline" onClick={() => onNeuerOrdner(null)}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Ordner
                </Button>
            </div>
            <div className="relative px-3 pb-2 pt-2">
                <Search className="pointer-events-none absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <Input
                    type="text"
                    value={suchtext}
                    onChange={(e) => onSuchtext(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Escape' && suchtext) { e.preventDefault(); onSuchtext(''); } }}
                    placeholder="Ordner und Leistungen suchen …"
                    aria-label="Ordner und Leistungen suchen"
                    className="rounded-lg py-1.5 pl-8 pr-8"
                />
                {suchtext && (
                    <button type="button" onClick={() => onSuchtext('')} aria-label="Suche leeren"
                        className="absolute right-5 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                        <X className="h-4 w-4" aria-hidden="true" />
                    </button>
                )}
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
                <div role="tree" aria-label="Ordner">
                    <UebersichtZeile
                        refSetzen={(element) => refSetzen(UEBERSICHT, element)}
                        gewaehlt={gewaehlt === null}
                        aktiv={aktivSichtbar === UEBERSICHT}
                        onFokus={() => setAktiv(UEBERSICHT)}
                        onKlick={() => onWaehlen(null)}
                        onTaste={(e) => beiTaste(e, null)}
                        ziehen={ziehen}
                        ablageErlaubt={ziehen?.art === 'ordner' && ablageErlaubt(null)}
                        onAblegen={() => props.onAblegen(null)}
                    />
                    {neuerOrdner && neuerOrdner.parentId === null && (
                        <EingabeZeile ebene={0} elternName={null} wandern={0} onAbbrechen={props.onNeuerOrdnerAbbrechen}
                            onSpeichern={async (name) => {
                                await props.onNeuerOrdnerSpeichern(null, name);
                            }} />
                    )}
                    {zeilen.map((zeile) => (
                        <OrdnerZeileMitKindern key={zeile.ordner.id}>
                            <OrdnerZeile
                                {...props}
                                zeile={zeile}
                                zaehler={zaehler.get(zeile.ordner.id) ?? { direkt: 0, gesamt: 0 }}
                                gewaehlt={gewaehlt === zeile.ordner.id}
                                aktiv={aktivSichtbar === zeile.ordner.id}
                                refSetzen={(element) => refSetzen(zeile.ordner.id, element)}
                                onFokus={() => setAktiv(zeile.ordner.id)}
                                onTaste={(e) => beiTaste(e, zeile)}
                            />
                            {neuerOrdner && neuerOrdner.parentId === zeile.ordner.id && (
                                <EingabeZeile
                                    ebene={zeile.ebene + 1}
                                    elternName={zeile.ordner.name}
                                    wandern={zaehler.get(zeile.ordner.id)?.direkt ?? 0}
                                    onAbbrechen={() => { props.onNeuerOrdnerAbbrechen(); fokussiere(zeile.ordner.id); }}
                                    onSpeichern={async (name) => {
                                        await props.onNeuerOrdnerSpeichern(zeile.ordner.id, name);
                                    }}
                                />
                            )}
                        </OrdnerZeileMitKindern>
                    ))}
                </div>

                {baum.byId.size === 0 && !neuerOrdner && (
                    <div className="mx-1 mt-2 rounded-lg border border-dashed border-slate-200 p-4 text-center text-sm text-slate-500">
                        <Folder className="mx-auto mb-2 h-8 w-8 text-rose-200" aria-hidden="true" />
                        Noch keine Ordner. Legen Sie mit „+ Ordner“ den ersten an, z. B. „Metallbau“.
                    </div>
                )}
                {treffer && zeilen.length === 0 && (
                    <p className="px-2 py-4 text-sm text-slate-500">Keine Ordner oder Leistungen zu „{suchtext.trim()}“.</p>
                )}

                {leistungsTreffer.length > 0 && (
                    <div className="mt-3 border-t border-slate-100 pt-2">
                        <p className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Leistungen ({leistungsTreffer.length})
                        </p>
                        <ul className="space-y-0.5">
                            {leistungsTreffer.slice(0, 8).map((l) => (
                                <li key={l.id}>
                                    <button type="button" onClick={() => onLeistungOeffnen(l)}
                                        className="flex w-full items-start gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500">
                                        <FileText className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" aria-hidden="true" />
                                        <span className="min-w-0">
                                            <span className="block truncate text-slate-800" title={l.name} data-kuerzung-erlaubt>{l.name}</span>
                                            <span className="block truncate text-xs text-slate-500" data-kuerzung-erlaubt>{l.ordnerId !== null ? pfadText(baum, l.ordnerId) : ''}</span>
                                        </span>
                                    </button>
                                </li>
                            ))}
                        </ul>
                        {leistungsTreffer.length > 8 && (
                            <p className="px-2 pt-1 text-xs text-slate-500">und {leistungsTreffer.length - 8} weitere – Suchbegriff genauer fassen</p>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

function OrdnerZeileMitKindern({ children }: { children: ReactNode }) {
    return <>{children}</>;
}

// ---------------------------------------------------------------------------
// Ziehen & Ablegen
// ---------------------------------------------------------------------------

function hatDateien(event: DragEvent) {
    return Array.from(event.dataTransfer.types).includes('Files');
}

/** Gemeinsame Handler für eine Zeile, auf die etwas gezogen werden kann. */
function useAblage(optionen: {
    ziehen: ZiehObjekt | null;
    erlaubt: boolean;
    bilderErlaubt: boolean;
    onAblegen: () => void;
    onBild?: (datei: File) => void;
}) {
    const [drueber, setDrueber] = useState<'objekt' | 'bild' | null>(null);
    const handler = {
        onDragOver: (event: DragEvent<HTMLDivElement>) => {
            if (optionen.ziehen) {
                if (!optionen.erlaubt) { event.dataTransfer.dropEffect = 'none'; return; }
                event.preventDefault();
                event.dataTransfer.dropEffect = 'move';
                setDrueber('objekt');
            } else if (optionen.bilderErlaubt && hatDateien(event)) {
                event.preventDefault();
                event.dataTransfer.dropEffect = 'copy';
                setDrueber('bild');
            }
        },
        onDragLeave: (event: DragEvent<HTMLDivElement>) => {
            if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
            setDrueber(null);
        },
        onDrop: (event: DragEvent<HTMLDivElement>) => {
            event.preventDefault();
            setDrueber(null);
            if (optionen.ziehen) {
                if (optionen.erlaubt) optionen.onAblegen();
                return;
            }
            const datei = ersteDatei(event.dataTransfer.files);
            if (datei && optionen.onBild) optionen.onBild(datei);
        },
    };
    return { drueber: optionen.ziehen && !optionen.erlaubt ? null : drueber, handler };
}

// ---------------------------------------------------------------------------
// Zeilen
// ---------------------------------------------------------------------------

const ZEILE = cn(
    'group/zeile relative flex items-center gap-1.5 rounded-md py-1.5 pr-1 text-sm outline-none select-none',
    'focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
);

function UebersichtZeile({ refSetzen, ...props }: {
    refSetzen: (element: HTMLDivElement | null) => void;
    gewaehlt: boolean;
    aktiv: boolean;
    onFokus: () => void;
    onKlick: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
    ziehen: ZiehObjekt | null;
    ablageErlaubt: boolean;
    onAblegen: () => void;
}) {
    const { drueber, handler } = useAblage({
        ziehen: props.ziehen, erlaubt: props.ablageErlaubt, bilderErlaubt: false, onAblegen: props.onAblegen,
    });
    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={1}
            aria-selected={props.gewaehlt}
            tabIndex={props.aktiv ? 0 : -1}
            onFocus={props.onFokus}
            onClick={props.onKlick}
            onKeyDown={props.onTaste}
            {...handler}
            className={cn(
                ZEILE, 'cursor-pointer pl-2',
                props.gewaehlt ? 'bg-rose-50 font-semibold text-rose-700' : 'text-slate-700 hover:bg-slate-50',
                drueber && 'bg-rose-50 ring-2 ring-inset ring-rose-400',
                props.ziehen && !props.ablageErlaubt && 'opacity-50',
            )}
        >
            <LayoutGrid className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            <span className="flex-1 truncate">Alle Ordner (Übersicht)</span>
            {drueber && <span className="text-xs font-medium text-rose-700">In oberste Ebene</span>}
        </div>
    );
}

interface OrdnerZeileProps extends Omit<OrdnerBaumProps, 'zaehler' | 'gewaehlt'> {
    zeile: BaumZeile;
    zaehler: Zaehler;
    gewaehlt: boolean;
    aktiv: boolean;
    refSetzen: (element: HTMLDivElement | null) => void;
    onFokus: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
}

function OrdnerZeile({ refSetzen, ...props }: OrdnerZeileProps) {
    const { zeile, gewaehlt, aktiv, ziehen } = props;
    const { ordner, ebene, hatKinder, aufgeklappt: offen } = zeile;
    const [menueOffen, setMenueOffen] = useState(false);
    const erlaubt = ziehen ? props.ablageErlaubt(ordner.id) : false;
    const { drueber, handler } = useAblage({
        ziehen,
        erlaubt,
        bilderErlaubt: true,
        onAblegen: () => props.onAblegen(ordner.id),
        onBild: (datei) => props.onBildAblegen(ordner.id, datei),
    });
    const bild = toSafeResourceUrl(ordner.bildUrl);
    const umbenennen = props.umbenennenId === ordner.id;
    const aktionenSichtbar = gewaehlt || menueOffen;

    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={ebene + 1}
            aria-selected={gewaehlt}
            aria-expanded={hatKinder ? offen : undefined}
            aria-label={`${ordner.name}, ${zaehlerAriaLabel(props.zaehler.gesamt, ordner.projektAnzahl)}`}
            tabIndex={aktiv ? 0 : -1}
            draggable={!umbenennen}
            onDragStart={(e) => {
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', ordner.name);
                props.onZiehenStart({ art: 'ordner', id: ordner.id });
            }}
            onDragEnd={props.onZiehenEnde}
            onFocus={(e) => { if (e.target === e.currentTarget) props.onFokus(); }}
            onClick={() => { if (!umbenennen) props.onWaehlen(ordner.id); }}
            onKeyDown={props.onTaste}
            {...handler}
            className={cn(
                ZEILE, 'cursor-pointer',
                gewaehlt ? 'bg-rose-50 text-rose-800' : 'text-slate-700 hover:bg-slate-50',
                drueber && 'bg-rose-50 ring-2 ring-inset ring-rose-400',
                ziehen && !erlaubt && 'opacity-50',
            )}
            style={{ paddingLeft: 4 + ebene * 16 }}
        >
            <span
                aria-hidden="true"
                onClick={(e) => { if (hatKinder) { e.stopPropagation(); props.onAufklappen(ordner.id, !offen); } }}
                className={cn('flex h-5 w-5 shrink-0 items-center justify-center rounded text-slate-400', hatKinder && 'hover:bg-slate-200 hover:text-slate-700')}
            >
                {hatKinder && (offen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />)}
            </span>
            {bild ? (
                <img src={bild} alt="" className="h-6 w-6 shrink-0 rounded object-cover" draggable={false} />
            ) : (
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-rose-50" aria-hidden="true">
                    {offen ? <FolderOpen className="h-4 w-4 text-rose-500" /> : <Folder className="h-4 w-4 text-rose-500" />}
                </span>
            )}

            {umbenennen ? (
                <NamensEingabe
                    start={ordner.name}
                    beschriftung={`Neuer Name für „${ordner.name}“`}
                    onAbbrechen={() => { props.onUmbenennenAbbrechen(); }}
                    onSpeichern={(name) => props.onUmbenennenSpeichern(ordner.id, name)}
                />
            ) : (
                <>
                    <span className={cn('min-w-0 flex-1 truncate', gewaehlt && 'font-semibold')} title={ordner.name} data-kuerzung-erlaubt>{ordner.name}</span>
                    {drueber ? (
                        <span className="shrink-0 text-xs font-medium text-rose-700">{drueber === 'bild' ? 'Als Ordnerbild' : 'Hier ablegen'}</span>
                    ) : (
                        <span className={cn('flex shrink-0 items-center gap-2 text-xs tabular-nums text-slate-500', aktionenSichtbar ? 'hidden' : 'group-hover/zeile:hidden group-focus-within/zeile:hidden')} aria-hidden="true">
                            <span className="inline-flex items-center gap-0.5" title="Leistungen"><List className="h-3 w-3" />{formatZahl(props.zaehler.gesamt, 0)}</span>
                            <span className="inline-flex items-center gap-0.5" title="Projekte"><Briefcase className="h-3 w-3" />{formatZahl(ordner.projektAnzahl, 0)}</span>
                        </span>
                    )}
                    {!drueber && (
                        <span className={cn('shrink-0 items-center gap-0.5', aktionenSichtbar ? 'flex' : 'hidden group-hover/zeile:flex group-focus-within/zeile:flex')}>
                            <ZeilenKnopf
                                label={`Unterordner in „${ordner.name}“ anlegen`}
                                tabIndex={aktiv ? 0 : -1}
                                onClick={() => { props.onAufklappen(ordner.id, true); props.onNeuerOrdner(ordner.id); }}
                            >
                                <FolderPlus className="h-4 w-4" aria-hidden="true" />
                            </ZeilenKnopf>
                            <DropdownMenu modal={false} onOpenChange={setMenueOffen}>
                                <DropdownMenuTrigger asChild>
                                    <button
                                        type="button"
                                        aria-label={`Weitere Aktionen für „${ordner.name}“`}
                                        title="Weitere Aktionen"
                                        tabIndex={aktiv ? 0 : -1}
                                        onClick={(e) => e.stopPropagation()}
                                        onKeyDown={(e) => e.stopPropagation()}
                                        className="rounded p-1 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                    >
                                        <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                                    </button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()} onCloseAutoFocus={(e) => e.preventDefault()}>
                                    <DropdownMenuItem onSelect={() => props.onUmbenennen(ordner.id)}>
                                        <Pencil className="h-4 w-4" aria-hidden="true" /> Umbenennen
                                    </DropdownMenuItem>
                                    <DropdownMenuItem onSelect={() => props.onVerschieben(ordner.id)}>
                                        <FolderInput className="h-4 w-4" aria-hidden="true" /> Verschieben nach …
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem onSelect={() => props.onLoeschen(ordner.id)} className="text-red-700 focus:bg-red-50 focus:text-red-700">
                                        <Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen …
                                    </DropdownMenuItem>
                                </DropdownMenuContent>
                            </DropdownMenu>
                        </span>
                    )}
                </>
            )}
        </div>
    );
}

function ZeilenKnopf({ label, tabIndex, onClick, children }: { label: string; tabIndex: number; onClick: () => void; children: ReactNode }) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            tabIndex={tabIndex}
            onClick={(e) => { e.stopPropagation(); onClick(); }}
            onKeyDown={(e) => e.stopPropagation()}
            className="rounded p-1 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            {children}
        </button>
    );
}

// ---------------------------------------------------------------------------
// Eingaben direkt im Baum
// ---------------------------------------------------------------------------

function NamensEingabe({ start, beschriftung, onAbbrechen, onSpeichern, hinweis }: {
    start: string;
    beschriftung: string;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint unter der Eingabe. */
    onSpeichern: (name: string) => Promise<void>;
    hinweis?: ReactNode;
}) {
    const [name, setName] = useState(start);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);

    const speichern = async () => {
        const sauber = name.trim();
        if (!sauber) { setFehler('Bitte geben Sie dem Ordner einen Namen.'); return; }
        if (sauber === start.trim() && start) { onAbbrechen(); return; }
        setSendet(true);
        setFehler(null);
        try {
            await onSpeichern(sauber);
        } catch (err) {
            setFehler(err instanceof Error ? err.message : 'Der Ordner konnte nicht gespeichert werden.');
            setSendet(false);
        }
    };

    return (
        <div className="min-w-0 flex-1" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-1">
                <input
                    autoFocus
                    value={name}
                    disabled={sendet}
                    onChange={(e) => { setName(e.target.value); setFehler(null); }}
                    onFocus={(e) => e.currentTarget.select()}
                    onKeyDown={(e) => {
                        e.stopPropagation();
                        if (e.key === 'Enter') { e.preventDefault(); void speichern(); }
                        if (e.key === 'Escape') { e.preventDefault(); onAbbrechen(); }
                    }}
                    onBlur={() => { if (!sendet && (!name.trim() || name.trim() === start.trim())) onAbbrechen(); }}
                    aria-label={beschriftung}
                    aria-invalid={fehler ? true : undefined}
                    maxLength={255}
                    placeholder="Name des Ordners"
                    className={cn(
                        'w-full min-w-0 rounded border bg-white px-2 py-0.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500',
                        fehler ? 'border-red-400' : 'border-rose-300',
                    )}
                />
                {sendet && <Loader2 className="h-4 w-4 shrink-0 text-rose-500 motion-safe:animate-spin" aria-label="Wird gespeichert" />}
            </div>
            {hinweis}
            {fehler && <p role="alert" className="mt-1 text-xs text-red-600">{fehler}</p>}
            <p className="mt-0.5 text-[11px] text-slate-500">Enter speichert · Esc bricht ab</p>
        </div>
    );
}

function EingabeZeile({ ebene, elternName, wandern, onAbbrechen, onSpeichern }: {
    ebene: number;
    elternName: string | null;
    /** Leistungen, die direkt im Eltern-Ordner liegen und mitwandern. */
    wandern: number;
    onAbbrechen: () => void;
    onSpeichern: (name: string) => Promise<void>;
}) {
    return (
        <div role="treeitem" aria-level={ebene + 1} aria-selected={false} aria-label="Neuer Ordner"
            className="flex items-start gap-1.5 rounded-md bg-rose-50/60 py-1.5 pr-1" style={{ paddingLeft: 4 + ebene * 16 }}>
            <span className="h-5 w-5 shrink-0" aria-hidden="true" />
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-rose-100" aria-hidden="true">
                <FolderPlus className="h-4 w-4 text-rose-600" />
            </span>
            <NamensEingabe
                start=""
                beschriftung={elternName ? `Name des neuen Unterordners in „${elternName}“` : 'Name des neuen Ordners'}
                onAbbrechen={onAbbrechen}
                onSpeichern={onSpeichern}
                hinweis={wandern > 0 && elternName ? (
                    <p className="mt-1 rounded border border-amber-200 bg-amber-50 px-2 py-1 text-xs text-amber-800">
                        {leistungenWandernHinweis(wandern, elternName)}
                    </p>
                ) : null}
            />
        </div>
    );
}
