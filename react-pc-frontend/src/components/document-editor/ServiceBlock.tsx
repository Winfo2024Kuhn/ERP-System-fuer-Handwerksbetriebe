import { Trash2, Clock, BarChart3, ChevronRight, AlertTriangle, BookmarkPlus } from 'lucide-react';
import { Button } from '../ui/button';
import { TiptapEditor } from '../TiptapEditor';
import { hatKundentext } from '../artikel/kundentext';
import { cn } from '../../lib/utils';
import { formatCurrency, serviceLineTotal } from './helpers';
import type { TiptapAenderungsArt } from '../tiptapVerlauf';
import type { DocBlock, EditorInstance } from './types';
import type { ZeitprognoseDto } from '../../types';
import { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { KategorieAnalyseModal } from '../KategorieAnalyseModal';
import { AddBelowButton } from './TextBlock';
import { WahlpositionMenu } from './WahlpositionMenu';
import { DecimalInput } from '../ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';

const alsEntwurf = (wert: number | undefined) => (wert ? formatDecimalInput(wert) : '');

/**
 * Haelt den getippten Text eines Zahlenfelds (Menge, EP) und gibt nur
 * vollstaendige Zahlen an den Editor weiter. Zwischenstaende wie „0“ oder
 * „0,“ bleiben stehen, statt dass das Feld sie beim naechsten Render wieder
 * leert. Aendert sich der Wert von aussen (Rueckgaengig, Leistung gewechselt),
 * uebernimmt das Feld ihn.
 */
function useZahlEntwurf(wert: number | undefined, label: string, uebernehmen: (zahl: number) => void) {
    const [entwurf, setEntwurf] = useState(() => alsEntwurf(wert));
    const [bekannterWert, setBekannterWert] = useState(wert);

    // Wert von aussen geaendert: im Render nachziehen (React-Muster
    // „State bei geaenderten Props anpassen“), nicht per Effekt.
    if (!Object.is(wert, bekannterWert)) {
        setBekannterWert(wert);
        const pruefung = validateDecimalInput(entwurf, { label, min: 0 });
        const passt = pruefung.valid && (pruefung.value ?? 0) === (wert ?? 0);
        if (!passt) setEntwurf(alsEntwurf(wert));
    }

    // `aendern` muss ueber Renders hinweg dieselbe Funktion bleiben: DecimalInput
    // leert eine fokussierte „0“, sobald sich onChange aendert und die Aenderung
    // nicht vom Tippen kam. Der Editor rendert nach jeder Eingabe mehrfach – mit
    // wechselnder Funktion verschwand die gerade getippte 0 dann doch wieder.
    // Aktuelle Werte deshalb in einer Ref spiegeln (Vorbild useVerlaufTastatur).
    const aktuell = useRef({ wert, label, uebernehmen });
    useEffect(() => {
        aktuell.current = { wert, label, uebernehmen };
    });

    const aendern = useCallback((text: string) => {
        setEntwurf(text);
        const { wert: geltend, label: name, uebernehmen: weitergeben } = aktuell.current;
        const pruefung = validateDecimalInput(text, { label: name, min: 0 });
        if (!pruefung.valid) return;
        const zahl = pruefung.value ?? 0;
        if (zahl !== (geltend ?? 0)) weitergeben(zahl);
    }, []);

    // Unvollstaendiges („0,“) oder Ungueltiges beim Verlassen auf den geltenden Wert zuruecksetzen.
    const verlassen = () => {
        if (!validateDecimalInput(entwurf, { label, min: 0 }).valid) setEntwurf(alsEntwurf(wert));
    };

    return { entwurf, aendern, verlassen };
}

interface ServiceBlockProps {
    block: DocBlock;
    positionNumber: string;
    isLocked: boolean;
    isActive: boolean;
    editorRefs: React.MutableRefObject<Record<string, EditorInstance | null>>;
    onEditorReady: (editorKey: string, editor: EditorInstance | null) => void;
    onUpdate: (id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => void;
    onRemove: (id: string) => void;
    /** Setzt die Leistung auf "fest beauftragt" oder "optional". */
    onModusWechsel: (id: string, modus: 'fest' | 'optional') => void;
    /** Oeffnet den Dialog, in dem die Entweder-Oder-Gruppe zusammengestellt wird. */
    onAlternativOeffnen: (id: string) => void;
    onFocus: (blockId: string) => void;
    onEditorFocus: (editor: EditorInstance | null) => void;
    /** Optional: oeffnet den AddTypeDialog mit dieser Karte als Anker (Insert direkt darunter). */
    onAddBelow?: (anchorId: string) => void;
    /**
     * Startzustand der Karte. Default `true`: beim Oeffnen eines Dokuments ist
     * alles zu, damit 20 Positionen auf einen Bildschirm passen. Reine
     * Ansichtssache — wird bewusst nicht persistiert.
     */
    defaultCollapsed?: boolean;
    /** Standard false. Siehe TiptapEditorProps.verlaufsModus. */
    verlaufsModus?: boolean;
    /**
     * true, wenn die Position aus einer gespeicherten Leistung stammt und
     * inzwischen davon abweicht (Name, Text, Preis oder Einheit). Dann zeigt die
     * Karte den Knopf „Als neue Leistung speichern“ – auch zugeklappt.
     */
    kannAlsNeueLeistungSpeichern?: boolean;
    /** Oeffnet den Dialog „Als neue Leistung speichern“ fuer diese Position. */
    onAlsNeueLeistungSpeichern?: (id: string) => void;
}

export function ServiceBlock({
    block,
    positionNumber,
    isLocked,
    isActive,
    editorRefs,
    onEditorReady,
    onUpdate,
    onRemove,
    onModusWechsel,
    onAlternativOeffnen,
    onFocus,
    onEditorFocus,
    onAddBelow,
    defaultCollapsed = true,
    verlaufsModus,
    kannAlsNeueLeistungSpeichern = false,
    onAlsNeueLeistungSpeichern,
}: ServiceBlockProps) {
    const total = serviceLineTotal(block);
    const hasDiscount = (block.discount ?? 0) > 0;

    /**
     * Materialposition ohne Text fuer den Kunden. Der PDF-Druck faellt dann auf
     * den Titel zurueck (RechnungPdfService:881-889) — und der Titel ist bei
     * Material die Kurzbeschreibung aus den Stammdaten, also Innensicht. Deshalb
     * bekommt genau dieser Fall einen sichtbaren Hinweis, der auch zugeklappt zu
     * sehen ist. Bei Leistungen bleibt der Notnagel unbeanstandet: Dort ist der
     * Titel ein vom Bediener geschriebener Satz.
     */
    const kundentextFehlt = block.artikelId != null && !hatKundentext(block.description);

    const [collapsed, setCollapsed] = useState(defaultCollapsed);
    const [zeitprognose, setZeitprognose] = useState<ZeitprognoseDto | null>(null);
    const [prognoseLoading, setPrognoseLoading] = useState(false);
    const [showAnalyse, setShowAnalyse] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const menge = useZahlEntwurf(block.quantity, 'Menge', (quantity) => onUpdate(block.id, { quantity }));
    const preis = useZahlEntwurf(block.price, 'Einzelpreis', (price) => onUpdate(block.id, { price }));

    // Keine Leistung oder keine Menge mehr: alte Zeitprognose beim Rendern verwerfen.
    const prognoseMoeglich = !!block.leistungId && !!block.quantity && block.quantity > 0;
    const prognoseSchluessel = `${block.leistungId ?? ''}|${block.quantity ?? ''}`;
    const [prognoseSchluesselVorher, setPrognoseSchluesselVorher] = useState(prognoseSchluessel);
    if (prognoseSchluessel !== prognoseSchluesselVorher) {
        setPrognoseSchluesselVorher(prognoseSchluessel);
        if (!prognoseMoeglich) setZeitprognose(null);
    }

    // Fetch time prediction when leistungId or quantity changes
    useEffect(() => {
        if (!block.leistungId || !block.quantity || block.quantity <= 0) return;

        if (debounceRef.current) clearTimeout(debounceRef.current);

        debounceRef.current = setTimeout(async () => {
            setPrognoseLoading(true);
            try {
                const res = await fetch(`/api/leistungen/${block.leistungId}/zeitprognose?menge=${block.quantity}`);
                if (res.ok) {
                    setZeitprognose(await res.json());
                } else {
                    setZeitprognose(null);
                }
            } catch {
                setZeitprognose(null);
            } finally {
                setPrognoseLoading(false);
            }
        }, 500);

        return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
    }, [block.leistungId, block.quantity]);

    return (
        <div className="group/card">
        {showAnalyse && zeitprognose?.kategorieId && createPortal(
            <KategorieAnalyseModal
                kategorie={{
                    id: zeitprognose.kategorieId,
                    bezeichnung: zeitprognose.kategorieName,
                    leaf: true,
                }}
                onClose={() => setShowAnalyse(false)}
            />,
            document.body
        )}
        <div
            className={cn(
                "bg-white rounded-xl border transition-all duration-200",
                isActive
                    ? "ring-2 ring-rose-500/50 border-rose-200 shadow-md shadow-rose-50"
                    : "border-slate-200 hover:border-slate-300 hover:shadow-sm",
                block.optional && "bg-slate-50/50"
            )}
            onClick={() => onFocus(block.id)}
        >
            {/* Header row: pos badge + title + actions */}
            <div className={cn("flex items-start gap-3 p-4 pb-0", collapsed && "pb-4")}>
                {/* Auf-/Zuklappen */}
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); setCollapsed(!collapsed); }}
                    aria-label={collapsed ? 'Aufklappen' : 'Zuklappen'}
                    aria-expanded={!collapsed}
                    className="flex-shrink-0 mt-1.5 w-7 h-7 flex items-center justify-center rounded-md text-slate-400 hover:text-rose-600 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40 transition-colors"
                >
                    <ChevronRight className={cn("w-3.5 h-3.5 transition-transform duration-200", !collapsed && "rotate-90")} />
                </button>

                {/* Position badge */}
                <div className="flex-shrink-0 mt-0.5">
                    <div className={cn(
                        "w-10 h-10 rounded-lg flex items-center justify-center text-sm font-bold",
                        block.optional
                            ? "bg-amber-50 text-amber-500 border border-amber-200"
                            : "bg-rose-50 text-rose-600 border border-rose-100"
                    )}>
                        {positionNumber}
                    </div>
                </div>

                {/* Title */}
                <div className="flex-1 min-w-0 pt-1">
                    <input
                        type="text"
                        placeholder="Titel / Kurztext"
                        value={block.title || ''}
                        onChange={(e) => onUpdate(block.id, { title: e.target.value })}
                        disabled={isLocked}
                        data-verlauf-feld="title"
                        className={cn(
                            "w-full font-semibold text-slate-900 bg-transparent border-none p-0 text-sm focus:ring-0 focus:outline-none placeholder:text-slate-300 disabled:text-slate-400",
                            block.optional && "italic text-slate-500"
                        )}
                    />
                    {kundentextFehlt && (
                        <p className="mt-0.5 flex items-start gap-1 text-[11px] text-amber-700">
                            <AlertTriangle className="w-3 h-3 mt-0.5 flex-shrink-0" aria-hidden="true" />
                            <span>
                                Kein Text für den Kunden — bitte unten eintragen, sonst steht dieser
                                Kurztext auf dem Angebot.
                            </span>
                        </p>
                    )}
                </div>

                {/* Zugeklappt: Summe direkt in der Kopfzeile */}
                {collapsed && (
                    <div className={cn(
                        "flex-shrink-0 pt-1 text-sm font-bold tabular-nums",
                        block.optional ? "text-amber-600" : "text-slate-900"
                    )}>
                        {formatCurrency(block.optional ? 0 : total)} €
                    </div>
                )}

                {/* Actions */}
                <div className="flex items-center gap-1 flex-shrink-0">
                    {/* In der Kopfzeile statt im aufgeklappten Teil: Karten starten
                        zugeklappt, der Hinweis "weicht ab" muss trotzdem sichtbar sein. */}
                    {kannAlsNeueLeistungSpeichern && !isLocked && onAlsNeueLeistungSpeichern && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={(e) => { e.stopPropagation(); onAlsNeueLeistungSpeichern(block.id); }}
                            aria-label="Als neue Leistung speichern"
                            title="Geändert gegenüber der gespeicherten Leistung – als neue Leistung speichern"
                            className="h-7 px-2 gap-1 text-xs font-medium text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-md"
                        >
                            <BookmarkPlus className="w-3.5 h-3.5" aria-hidden="true" />
                            <span>Neue Leistung</span>
                        </Button>
                    )}
                    <WahlpositionMenu
                        block={block}
                        isLocked={isLocked}
                        onModusWechsel={onModusWechsel}
                        onAlternativOeffnen={onAlternativOeffnen}
                    />
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); onRemove(block.id); }}
                        disabled={isLocked}
                        className="h-7 w-7 p-0 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded-md"
                    >
                        <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                </div>
            </div>

            {/* Description - nur aufgeklappt */}
            {!collapsed && (
            <div className="px-4 pt-2 pb-3">
                <div className="pl-[52px] doc-pdf-metrics doc-pdf-metrics--spalte" data-verlauf-feld="description">
                    <TiptapEditor
                        value={block.description || ''}
                        onChange={(val, art) => onUpdate(block.id, { description: val }, art)}
                        readOnly={isLocked}
                        hideToolbar={true}
                        compactMode={true}
                        onFocus={() => {
                            onFocus(block.id);
                            onEditorFocus(editorRefs.current[`${block.id}-desc`]);
                        }}
                        onEditorReady={(editor) => onEditorReady(`${block.id}-desc`, editor)}
                        verlaufsModus={verlaufsModus}
                    />
                </div>
            </div>
            )}

            {/* Calculation row - nur aufgeklappt */}
            {!collapsed && (
            <div className="mx-4 mb-4 bg-slate-50 rounded-lg border border-slate-100 p-3">
                <div className="flex items-center gap-3">
                    {/* Menge + Einheit */}
                    <div className="flex items-center gap-1.5">
                        <div>
                            <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Menge</label>
                            <DecimalInput
                                min={0}
                                aria-label="Menge"
                                value={menge.entwurf}
                                onChange={menge.aendern}
                                onBlur={menge.verlassen}
                                disabled={isLocked}
                                data-verlauf-feld="quantity"
                                className="w-16 text-center text-sm font-semibold bg-white border border-slate-200 rounded-md px-2 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 disabled:text-slate-400 transition-all"
                            />
                        </div>
                        <div>
                            <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Einheit</label>
                            <input
                                type="text"
                                value={block.unit || 'Stk'}
                                onChange={(e) => onUpdate(block.id, { unit: e.target.value })}
                                disabled={isLocked}
                                data-verlauf-feld="unit"
                                className="w-14 text-center text-xs text-slate-600 bg-white border border-slate-200 rounded-md px-1.5 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 transition-all"
                            />
                        </div>
                    </div>

                    <span className="text-slate-300 text-sm font-light pt-3.5">×</span>

                    {/* Einzelpreis */}
                    <div>
                        <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">EP</label>
                        <div className="relative">
                            <DecimalInput
                                min={0}
                                aria-label="Einzelpreis"
                                value={preis.entwurf}
                                onChange={preis.aendern}
                                onBlur={preis.verlassen}
                                disabled={isLocked}
                                data-verlauf-feld="price"
                                className="w-24 text-right text-sm font-semibold bg-white border border-slate-200 rounded-md pl-2 pr-6 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 disabled:text-slate-400 transition-all"
                            />
                            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 text-xs">€</span>
                        </div>
                    </div>

                    <span className="text-slate-300 text-sm font-light pt-3.5">=</span>

                    {/* Gesamtpreis */}
                    <div className="flex-1 text-right">
                        <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Gesamt</label>
                        <div className={cn(
                            "font-bold text-base py-0.5",
                            block.optional
                                ? "line-through text-slate-400 decoration-2"
                                : hasDiscount ? "text-rose-600" : "text-slate-900"
                        )}>
                            {formatCurrency(block.optional ? 0 : total)} €
                        </div>
                    </div>
                </div>

                {/* Time prediction badge */}
                {zeitprognose && (
                    <div className="mt-2 pt-2 border-t border-slate-100 flex items-center gap-2">
                        <div className="flex items-center gap-1.5 bg-blue-50 text-blue-700 rounded-md px-2.5 py-1 text-xs font-medium border border-blue-100">
                            <Clock className="w-3.5 h-3.5" />
                            <span>~{(zeitprognose.prognostizierteStunden ?? 0).toFixed(1)} h geschätzt</span>
                        </div>
                        <span className="text-[10px] text-slate-400" title={`Vorhersagegenauigkeit: ${((zeitprognose.rQuadrat ?? 0) * 100).toFixed(0)}% · Basierend auf ${zeitprognose.datenpunkte ?? 0} abgeschlossenen Projekten`}>
                            {(zeitprognose.rQuadrat ?? 0) >= 0.7 ? '🟢 Zuverlässig' : (zeitprognose.rQuadrat ?? 0) >= 0.4 ? '🟡 Grobe Schätzung' : '🔴 Wenig Erfahrungswerte'} · {zeitprognose.datenpunkte ?? 0} Projekte
                        </span>
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); setShowAnalyse(true); }}
                            className="ml-auto flex items-center gap-1 text-[11px] text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded px-1.5 py-0.5 transition-colors"
                            title="Zeitanalyse anzeigen"
                        >
                            <BarChart3 className="w-3.5 h-3.5" />
                            Analyse
                        </button>
                    </div>
                )}
                {prognoseLoading && (
                    <div className="mt-2 pt-2 border-t border-slate-100">
                        <span className="text-[10px] text-slate-400 animate-pulse">Zeitprognose wird berechnet...</span>
                    </div>
                )}
            </div>
            )}
        </div>
        {!isLocked && onAddBelow && (
            <AddBelowButton onClick={() => onAddBelow(block.id)} />
        )}
        </div>
    );
}
