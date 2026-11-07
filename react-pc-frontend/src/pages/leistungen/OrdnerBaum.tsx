import { Fragment, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { FileText, Folder, Plus, Search, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { EingabeZeile } from './OrdnerNamensEingabe';
import { OrdnerZeile, UebersichtZeile } from './OrdnerZeile';
import { pfadText, sichtbareZeilen, suche, type Baum, type BaumZeile, type Zaehler } from './ordnerLogik';
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
                        <Fragment key={zeile.ordner.id}>
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
                        </Fragment>
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
