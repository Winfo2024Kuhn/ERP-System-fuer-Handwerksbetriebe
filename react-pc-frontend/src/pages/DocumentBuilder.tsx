import { useState, useEffect, useCallback, useRef } from 'react';
import {
    DndContext,
    DragOverlay,
    useDraggable,
    useDroppable,
    rectIntersection,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
    type DragEndEvent,
    type DragStartEvent
} from '@dnd-kit/core';
import {
    arrayMove,
    SortableContext,
    sortableKeyboardCoordinates,
    useSortable,
    verticalListSortingStrategy
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
    FileText,
    GripVertical,
    Layers,
    Trash2,
    Calculator,
    Undo2,
    Redo2,
    Bold,
    Italic,
    Underline,
    AlignLeft,
    AlignCenter,
    AlignRight,
    AlignJustify,
    List,
    ListOrdered,
    Minus,
    Plus,
    Sigma,
    Download
} from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { cn } from '../lib/utils';
import { TiptapEditor } from '../components/TiptapEditor';
import { useToast } from '../components/ui/toast';
import {
    type FormBlock,
    type FormBlockType,
    type FormBlockStyles,
    GESCHAEFTSDOKUMENT_TYPEN
} from '../types';

// --- Types ---

type BlockType = 'TEXT' | 'SERVICE';

interface DocBlock {
    id: string;
    type: BlockType;
    content?: string;
    pos?: string;
    title?: string;
    quantity?: number;
    unit?: string;
    price?: number;
    description?: string;
    fontSize?: number;
    fett?: boolean;
    optional?: boolean;
}

interface TemplateSource {
    id: string;
    type: BlockType;
    label: string;
    payload: Partial<DocBlock>;
}

// --- API Types ---

interface TextbausteinApiDto {
    id: number;
    name: string;
    typ: string;
    beschreibung?: string;
    html?: string;
}

interface LeistungApiDto {
    id: number;
    name: string;
    description: string;
    price: number;
    unit: {
        name: string;
        anzeigename: string;
    };
    folderId?: number;
}

// Map Verrechnungseinheit to short unit strings
const unitMap: Record<string, string> = {
    'LAUFENDE_METER': 'lfm',
    'QUADRATMETER': 'm²',
    'KILOGRAMM': 'kg',
    'STUECK': 'Stk'
};
// ... (Lines in between skipped for brevity if possible, or just replacing the block)
// Be careful with large replacements. I will break it down if needed.
// Actually I need to replace the interface area AND the fetch area.
// I'll do two replaces.

// REPLACE 1: Interface


const STYLE_DEFAULTS: Partial<Record<FormBlockType, FormBlockStyles>> = {
    heading: { fontSize: 20, fontWeight: '700', color: '#111827', textAlign: 'left' },
    text: { fontSize: 14, fontWeight: '400', color: '#111827', textAlign: 'left' },
    adresse: { fontSize: 13, fontWeight: '400', color: '#111827', textAlign: 'left' },
    dokumenttyp: { fontSize: 16, fontWeight: '700', color: '#111827', textAlign: 'left' },
};

// --- Components ---

function DraggableSource({ source }: { source: TemplateSource }) {
    const { attributes, listeners, setNodeRef } = useDraggable({
        id: source.id,
        data: { type: 'SOURCE', source }
    });

    return (
        <div
            ref={setNodeRef}
            {...listeners}
            {...attributes}
            className="p-3 bg-white border border-slate-200 rounded-lg shadow-sm cursor-grab hover:border-rose-300 hover:shadow transition-all flex items-center gap-3"
        >
            <div className="p-2 bg-slate-50 rounded text-slate-500">
                {source.type === 'TEXT' ? <FileText className="w-4 h-4" /> : <Layers className="w-4 h-4" />}
            </div>
            <span className="text-sm font-medium text-slate-700">{source.label}</span>
        </div>
    );
}

function SortableBlock({
    block,
    onRemove,
    onChange,
    onHeightChange
}: {
    block: DocBlock;
    onRemove: (id: string) => void;
    onChange: (id: string, updates: Partial<DocBlock>) => void;
    onHeightChange: (id: string, height: number) => void;
}) {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging,
        isOver
    } = useSortable({ id: block.id });

    const elementRef = useRef<HTMLDivElement>(null);

    // Combine refs: dnd-kit needs setNodeRef, we need elementRef for measuring
    const setRefs = useCallback((node: HTMLDivElement | null) => {
        setNodeRef(node);
        (elementRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
    }, [setNodeRef]);

    // ResizeObserver: Report height changes to parent for page break calculation
    useEffect(() => {
        if (!elementRef.current) return;

        const observer = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const h = entry.borderBoxSize?.[0]?.blockSize ?? entry.target.getBoundingClientRect().height;
                onHeightChange(block.id, h);
            }
        });

        observer.observe(elementRef.current);
        return () => observer.disconnect();
    }, [block.id, onHeightChange]);

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <div ref={setRefs} style={style} className="group relative">
            {/* Drop indicator - red line above block when hovered */}
            {isOver && !isDragging && (
                <div className="absolute -top-1 left-0 right-0 h-0.5 bg-rose-500 rounded-full z-20 shadow-sm" />
            )}

            <div className={cn(
                "absolute left-[-1.5rem] top-2 p-1 cursor-grab text-slate-300 hover:text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity",
                isDragging && "opacity-100"
            )}
                {...attributes}
                {...listeners}
            >
                <GripVertical className="w-4 h-4" />
            </div>

            <div className="relative border border-transparent group-hover:border-rose-200 rounded-none p-0 transition-colors">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onRemove(block.id)}
                    className="absolute -right-2 -top-2 h-6 w-6 p-0 rounded-full bg-white border shadow-sm opacity-0 group-hover:opacity-100 transition-opacity z-10 text-red-500 hover:text-red-600 hover:bg-red-50"
                >
                    <Trash2 className="w-3 h-3" />
                </Button>

                {block.type === 'TEXT' ? (
                    <div className="bg-white rounded border border-slate-200 shadow-sm">
                        <TiptapEditor
                            value={block.content || ''}
                            onChange={(val) => onChange(block.id, { content: val })}
                            hideToolbar={true}
                            compactMode={true}
                        />
                    </div>
                ) : (
                    <div className="bg-white rounded border border-slate-200 shadow-sm text-xs">
                        <div className="grid grid-cols-12 gap-0 items-center">
                            {/* Pos. */}
                            <div className="col-span-1 p-2 font-mono text-slate-700">{block.pos || '1.1'}</div>
                            {/* Menge */}
                            <div className="col-span-1 p-2 text-center">
                                <input
                                    type="number"
                                    value={block.quantity}
                                    onChange={e => onChange(block.id, { quantity: parseFloat(e.target.value) || 0 })}
                                    className="w-full text-center bg-transparent border-b border-transparent hover:border-slate-300 focus:border-rose-500 focus:outline-none"
                                />
                            </div>
                            {/* ME (Einheit) */}
                            <div className="col-span-1 p-2 text-center text-slate-500">{block.unit}</div>
                            {/* Bezeichnung */}
                            <div className="col-span-5 p-2">
                                <input
                                    value={block.title}
                                    onChange={e => onChange(block.id, { title: e.target.value })}
                                    className="w-full bg-transparent border-b border-transparent hover:border-slate-300 focus:border-rose-500 focus:outline-none font-semibold"
                                />
                            </div>
                            {/* EP (Einzelpreis) */}
                            <div className="col-span-2 p-2 text-right">
                                <input
                                    type="number"
                                    step="0.01"
                                    value={block.price}
                                    onChange={e => onChange(block.id, { price: parseFloat(e.target.value) || 0 })}
                                    className="w-full text-right bg-transparent border-b border-transparent hover:border-slate-300 focus:border-rose-500 focus:outline-none"
                                />
                                <span className="text-slate-400 ml-1">€</span>
                            </div>
                            {/* GP (Gesamtpreis) */}
                            <div className="col-span-2 p-2 text-right font-medium text-slate-700">
                                {((block.quantity || 0) * (block.price || 0)).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €
                            </div>
                        </div>

                        {/* Description Editor for Service */}
                        <div className="border-t border-slate-100 p-2">
                            <TiptapEditor
                                value={block.description || ''}
                                onChange={(val) => onChange(block.id, { description: val })}
                                hideToolbar={true}
                                compactMode={true}
                            />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

// Types for Abschluss API response
interface AbschlussInfo {
    nettosumme?: number;
    mwstProzent?: number;
    mwstBetrag?: number;
    gesamtsumme?: number;
    angebotReferenz?: { dokumentNummer: string };
    auftragsbestaetigungReferenz?: { dokumentNummer: string };
    vorherigeZahlungen?: { dokumentNummer: string; dokumentTypAnzeigename: string; betrag: number }[];
    summeVorherigerZahlungen?: number;
    nochZuZahlen?: number;
    aktuelleAbschlagsNummer?: number;
}

interface AbschlussBlockProps {
    blocks: DocBlock[];
    abschlussInfo?: AbschlussInfo | null; // Optional API data
}

function AbschlussBlock({ blocks, abschlussInfo }: AbschlussBlockProps) {
    // Calculate from blocks as fallback
    const totalNet = blocks
        .filter(b => b.type === 'SERVICE')
        .reduce((sum, b) => sum + ((b.quantity || 0) * (b.price || 0)), 0);

    const tax = totalNet * 0.19;
    const gross = totalNet + tax;

    // Use API data if available, otherwise use calculated values
    const displayNet = abschlussInfo?.nettosumme ?? totalNet;
    const displayMwstProzent = abschlussInfo?.mwstProzent ?? 19;
    const displayTax = abschlussInfo?.mwstBetrag ?? tax;
    const displayGross = abschlussInfo?.gesamtsumme ?? gross;

    const formatCurrency = (value: number) =>
        value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';

    const hasReferences = abschlussInfo?.angebotReferenz || abschlussInfo?.auftragsbestaetigungReferenz;
    const hasPreviousPayments = (abschlussInfo?.vorherigeZahlungen?.length ?? 0) > 0;

    return (
        <div className="mt-8 p-6 bg-slate-50 border-t border-slate-200">
            {/* Main amounts */}
            <div className="space-y-1 max-w-sm ml-auto">
                <div className="flex justify-between text-sm text-slate-600">
                    <span className="font-medium">Nettosumme</span>
                    <span>{formatCurrency(displayNet)}</span>
                </div>
                <div className="flex justify-between text-sm text-slate-600">
                    <span>Umsatzsteuer</span>
                    <span className="flex gap-8">
                        <span className="text-slate-400">{displayMwstProzent} %</span>
                        <span>{formatCurrency(displayTax)}</span>
                    </span>
                </div>
                <div className="flex justify-between font-bold text-base text-slate-900 border-t border-slate-400 pt-2 mt-2">
                    <span>Gesamtsumme</span>
                    <span>{formatCurrency(displayGross)}</span>
                </div>
            </div>

            {/* Document references (Angebot, AB) */}
            {hasReferences && (
                <div className="mt-6 pt-4 border-t border-slate-200">
                    <p className="text-sm text-slate-500">Bezug auf:</p>
                    {abschlussInfo?.angebotReferenz && (
                        <p className="text-sm text-slate-600 ml-4">
                            Angebot Nr. {abschlussInfo.angebotReferenz.dokumentNummer}
                        </p>
                    )}
                    {abschlussInfo?.auftragsbestaetigungReferenz && (
                        <p className="text-sm text-slate-600 ml-4">
                            Auftragsbestätigung Nr. {abschlussInfo.auftragsbestaetigungReferenz.dokumentNummer}
                        </p>
                    )}
                </div>
            )}

            {/* Previous payments (for partial invoices) */}
            {hasPreviousPayments && (
                <div className="mt-4 max-w-sm ml-auto">
                    <p className="text-sm text-slate-500 mb-2">Bereits bezahlt:</p>
                    {abschlussInfo?.vorherigeZahlungen?.map((zahlung, idx) => (
                        <div key={idx} className="flex justify-between text-sm text-slate-600">
                            <span>{zahlung.dokumentTypAnzeigename} {zahlung.dokumentNummer}</span>
                            <span className="text-rose-600">-{formatCurrency(zahlung.betrag)}</span>
                        </div>
                    ))}

                    {/* Final amount to pay */}
                    <div className="flex justify-between font-bold text-base text-slate-900 border-t-2 border-slate-800 border-double pt-2 mt-2">
                        <span>Noch zu zahlen:</span>
                        <span>{formatCurrency(abschlussInfo?.nochZuZahlen ?? displayGross)}</span>
                    </div>
                </div>
            )}

            {/* Abschlagsrechnung indicator */}
            {abschlussInfo?.aktuelleAbschlagsNummer && (
                <div className="mt-4 text-xs text-slate-400 text-right">
                    {abschlussInfo.aktuelleAbschlagsNummer}. Abschlagsrechnung
                </div>
            )}
        </div>
    );
}


// --- Main Page ---

export default function DocumentBuilder() {
    const toast = useToast();
    const [activeTab, setActiveTab] = useState<'TEXT' | 'SERVICE'>('TEXT');
    const [selectedDocType, setSelectedDocType] = useState<string>('ANGEBOT');
    const [blocks, setBlocks] = useState<DocBlock[]>([]);
    const [activeDragItem, setActiveDragItem] = useState<TemplateSource | null>(null);
    const [bgBlocks, setBgBlocks] = useState<FormBlock[]>([]);
    const [tableBlock, setTableBlock] = useState<FormBlock | null>(null);
    const [backgroundImage, setBackgroundImage] = useState<string | null>(null);

    // Undo/Redo State
    const [history, setHistory] = useState<DocBlock[][]>([[]]);
    const [historyIndex, setHistoryIndex] = useState(0);

    // Abschluss visibility
    const [showAbschluss, setShowAbschluss] = useState(false);

    // Data from API
    const [textTemplates, setTextTemplates] = useState<TemplateSource[]>([]);
    const [serviceTemplates, setServiceTemplates] = useState<TemplateSource[]>([]);
    const [loadingData, setLoadingData] = useState(true);

    // --- A4 Page Pagination ---
    // A4 Konstanten (in Pixeln bei ca. 96 DPI)
    const A4_HEIGHT = 842; // Gesamthöhe
    const PAGE_MARGIN_TOP = 40; // Platz für Briefkopf
    const PAGE_MARGIN_BOTTOM = 50; // Platz für Fußzeile
    const CONTENT_HEIGHT = A4_HEIGHT - PAGE_MARGIN_TOP - PAGE_MARGIN_BOTTOM; // ~752px nutzbarer Bereich

    // State für Block-Höhen
    const [blockHeights, setBlockHeights] = useState<Record<string, number>>({});

    // Callback zum Speichern der gemessenen Höhen (mit Toleranz)
    const handleHeightChange = useCallback((id: string, height: number) => {
        setBlockHeights(prev => {
            if (Math.abs((prev[id] || 0) - height) < 2) return prev; // Toleranz 2px
            return { ...prev, [id]: height };
        });
    }, []);

    // Layout Engine: Verteilt Blöcke intelligent auf Seiten
    interface PageLayout {
        blocks: DocBlock[];
        hasTableStart: boolean;  // Ob wir mitten in einer Tabelle sind (für Header-Wiederholung)
        subTotal: number;        // Zwischensumme für Übertrag-Anzeige
    }

    const calculatePages = useCallback((): PageLayout[] => {
        // Dynamische Werte basierend auf Template (tableBlock)
        // Verwende die Höhe aus dem Template als verfügbare Höhe pro Seite (nur für Seite 1)
        const firstPageHeight = tableBlock ? tableBlock.height : CONTENT_HEIGHT;
        const followingPageHeight = CONTENT_HEIGHT;

        const layoutPages: PageLayout[] = [];
        let currentBlocks: DocBlock[] = [];
        let currentHeight = 0;
        let isTableActive = false;
        let runningTotal = 0;

        blocks.forEach((block) => {
            // Höhe abrufen oder schätzen (SERVICE ~40px, TEXT ~60px)
            const h = blockHeights[block.id] || (block.type === 'SERVICE' ? 40 : 60);

            // Limit bestimmen: Seite 1 hat Template-Limit, weitere Seiten haben Standard-Limit
            const currentLimit = (layoutPages.length === 0) ? firstPageHeight : followingPageHeight;

            // Passt der Block noch auf die Seite?
            if (currentHeight + h > currentLimit - 5 && currentBlocks.length > 0) {

                // SEITENUMBRUCH
                layoutPages.push({
                    blocks: [...currentBlocks],
                    hasTableStart: isTableActive,
                    subTotal: runningTotal
                });

                currentBlocks = [];
                currentHeight = 0;

                // Wenn wir mitten in Leistungen sind, Platz für Header auf neuer Seite
                if (isTableActive) {
                    currentHeight += 30; // Platz für wiederholten Header
                }
            }

            // Block hinzufügen
            currentBlocks.push(block);
            currentHeight += h;

            // Status für Tabelle tracken
            if (block.type === 'SERVICE') {
                isTableActive = true;
                runningTotal += (block.quantity || 0) * (block.price || 0);
            }
        });

        // Letzte Seite pushen
        if (currentBlocks.length > 0) {
            layoutPages.push({ blocks: currentBlocks, hasTableStart: isTableActive, subTotal: runningTotal });
        }

        // Fallback für leeres Dokument
        if (layoutPages.length === 0) layoutPages.push({ blocks: [], hasTableStart: false, subTotal: 0 });

        return layoutPages;
    }, [blocks, blockHeights, tableBlock, CONTENT_HEIGHT]);

    const pages = calculatePages();


    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    // Parse Template Logic
    const deserialize = useCallback((html: string) => {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html || '';
        const found = wrapper.querySelectorAll('[data-block-type]');
        return Array.from(found).map((el, idx) => {
            const dataset = (el as HTMLElement).dataset;
            return {
                id: `bg-${idx}`,
                type: dataset.blockType as FormBlockType,
                page: Number(dataset.page || 1),
                x: Number(dataset.x || 32),
                y: Number(dataset.y || 32),
                z: Number(dataset.z || idx + 1),
                width: Number(dataset.width) || 200,
                height: Number(dataset.height) || 80,
                content: dataset.content ? decodeURIComponent(dataset.content) : undefined,
                styles: dataset.style ? JSON.parse(decodeURIComponent(dataset.style)) : undefined
            } as FormBlock;
        });
    }, []);

    // Load real template when doc type changes
    useEffect(() => {
        const fetchTemplate = async () => {
            try {
                // First, get the template name for this document type
                // Convert enum value to German label for backend lookup
                const docTypeLabel = GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === selectedDocType)?.label || selectedDocType;
                const selectionRes = await fetch(`/api/formulare/templates/selection?dokumenttyp=${encodeURIComponent(docTypeLabel)}`);

                if (selectionRes.ok && selectionRes.status !== 204) {
                    const templateName = await selectionRes.text();

                    if (templateName) {
                        // Fetch the actual template
                        const templateRes = await fetch(`/api/formulare/templates/${encodeURIComponent(templateName)}`);

                        if (templateRes.ok) {
                            const templateData = await templateRes.json();
                            const html = templateData.html || '';

                            // Parse background image from meta tag
                            const bgMatch = html.match(/<meta\s+name="background-image"\s+content="([^"]+)"/);
                            if (bgMatch && bgMatch[1]) {
                                try {
                                    setBackgroundImage(decodeURIComponent(bgMatch[1]));
                                } catch {
                                    setBackgroundImage(bgMatch[1]);
                                }
                            } else {
                                setBackgroundImage(null);
                            }

                            const parsedBlocks = deserialize(html);

                            // Page 1 blocks (excluding table) - we only use page 1 for now
                            const page1Blocks = parsedBlocks.filter(b => b.page === 1 || !b.page);
                            setBgBlocks(page1Blocks.filter(b => b.type !== 'table'));
                            const tbl1 = page1Blocks.find(b => b.type === 'table');
                            setTableBlock(tbl1 || null);

                            return;
                        }
                    }
                }

                // Fallback to a minimal default template if no template found
                const fallbackHtml = `
                    <div data-block-type="heading" data-x="40" data-y="40" data-width="300" data-height="60" data-content="${encodeURIComponent('Dokument')}"></div>
                    <div data-block-type="dokumenttyp" data-x="40" data-y="100" data-width="200" data-height="40" data-content="${encodeURIComponent(selectedDocType)}"></div>
                    <div data-block-type="table" data-x="40" data-y="160" data-width="515" data-height="500"></div>
                `;
                const parsedBlocks = deserialize(fallbackHtml);
                setBgBlocks(parsedBlocks.filter(b => b.type !== 'table'));
                const tbl = parsedBlocks.find(b => b.type === 'table');
                setTableBlock(tbl || null);

            } catch (err) {
                console.error('Failed to fetch template', err);
                // Use minimal fallback on error
                const fallbackHtml = `
                    <div data-block-type="table" data-x="40" data-y="100" data-width="515" data-height="600"></div>
                `;
                const parsedBlocks = deserialize(fallbackHtml);
                setBgBlocks([]);
                const tbl = parsedBlocks.find(b => b.type === 'table');
                setTableBlock(tbl || null);
            }
        };

        fetchTemplate();
    }, [selectedDocType, deserialize]);

    // Fetch Textbausteine from API
    useEffect(() => {
        const fetchData = async () => {
            setLoadingData(true);
            try {
                // Fetch Textbausteine
                const tbRes = await fetch('/api/textbausteine');
                if (tbRes.ok) {
                    const data: TextbausteinApiDto[] = await tbRes.json();
                    const mapped: TemplateSource[] = data.map(tb => ({
                        id: `tb-${tb.id}`,
                        type: 'TEXT' as BlockType,
                        label: tb.name,
                        payload: { content: tb.html || tb.beschreibung || '' }
                    }));
                    setTextTemplates(mapped);
                }

                // Fetch Leistungen from API
                const pkRes = await fetch('/api/leistungen');
                if (pkRes.ok) {
                    const pkData: LeistungApiDto[] = await pkRes.json();
                    const mappedServices: TemplateSource[] = pkData.map(pk => ({
                        id: `srv-${pk.id}`,
                        type: 'SERVICE' as BlockType,
                        label: pk.name,
                        payload: {
                            title: pk.name,
                            description: pk.description || '', // This was already technically here in my plan, but verifying mapping exists
                            quantity: 1,
                            unit: unitMap[pk.unit?.name || 'STUECK'] || 'Stk',
                            price: pk.price || 0
                        }
                    }));
                    setServiceTemplates(mappedServices);
                }
            } catch (err) {
                console.error('Failed to fetch data', err);
            } finally {
                setLoadingData(false);
            }
        };
        fetchData();
    }, []);

    // History management
    const saveToHistory = useCallback((newBlocks: DocBlock[]) => {
        const newHistory = history.slice(0, historyIndex + 1);
        newHistory.push(newBlocks);
        setHistory(newHistory);
        setHistoryIndex(newHistory.length - 1);
    }, [history, historyIndex]);

    const undo = useCallback(() => {
        if (historyIndex > 0) {
            setHistoryIndex(historyIndex - 1);
            setBlocks(history[historyIndex - 1]);
        }
    }, [history, historyIndex]);

    const redo = useCallback(() => {
        if (historyIndex < history.length - 1) {
            setHistoryIndex(historyIndex + 1);
            setBlocks(history[historyIndex + 1]);
        }
    }, [history, historyIndex]);

    const handleDragStart = (event: DragStartEvent) => {
        const { active } = event;
        if (active.data.current?.type === 'SOURCE') {
            setActiveDragItem(active.data.current.source as TemplateSource);
        }
    };

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        setActiveDragItem(null);

        // For SOURCE items (from sidebar), always add - don't require exact drop target detection
        // This is more user-friendly than strict collision detection
        const isSourceDrag = active.data.current?.type === 'SOURCE';

        if (isSourceDrag) {
            // Dropping a new item from sidebar
            const source = active.data.current!.source as TemplateSource;
            const newBlock: DocBlock = {
                id: `block-${Date.now()}`,
                type: source.type,
                ...source.payload,
                pos: source.type === 'SERVICE' ? String(blocks.filter(b => b.type === 'SERVICE').length + 1) : undefined
            };

            // Determine insertion index based on drop target
            let insertIndex = blocks.length;
            if (over && over.id !== 'canvas') {
                const targetIndex = blocks.findIndex(b => b.id === over.id);
                if (targetIndex !== -1) {
                    insertIndex = targetIndex;
                }
            }

            const newBlocks = [...blocks];
            newBlocks.splice(insertIndex, 0, newBlock);
            setBlocks(newBlocks);
            saveToHistory(newBlocks);
            return;
        }

        // Reordering existing items - this still needs 'over'
        if (!over) return;

        if (active.id !== over.id) {
            const oldIndex = blocks.findIndex((item) => item.id === active.id);
            const newIndex = blocks.findIndex((item) => item.id === over.id);
            if (oldIndex !== -1 && newIndex !== -1) {
                const newBlocks = arrayMove(blocks, oldIndex, newIndex);
                setBlocks(newBlocks);
                saveToHistory(newBlocks);
            }
        }
    };

    const updateBlock = (id: string, updates: Partial<DocBlock>) => {
        const newBlocks = blocks.map(b => b.id === id ? { ...b, ...updates } : b);
        setBlocks(newBlocks);
        // Don't save every keystroke to history - only major changes
    };

    const removeBlock = (id: string) => {
        const newBlocks = blocks.filter(b => b.id !== id);
        setBlocks(newBlocks);
        saveToHistory(newBlocks);
    };

    // PDF Export Handler
    const handleExportPdf = async () => {
        try {
            // Prepare layout blocks from bgBlocks + tableBlock
            const layoutBlocks = [
                ...bgBlocks.map(b => ({
                    id: b.id,
                    type: b.type,
                    page: b.page,
                    x: b.x,
                    y: b.y,
                    width: b.width,
                    height: b.height,
                    content: b.content || ''
                })),
                ...(tableBlock ? [{
                    id: tableBlock.id,
                    type: tableBlock.type,
                    page: tableBlock.page,
                    x: tableBlock.x,
                    y: tableBlock.y,
                    width: tableBlock.width,
                    height: tableBlock.height,
                    content: ''
                }] : [])
            ];

            // Prepare content blocks
            const contentBlocks = blocks.map(b => ({
                id: b.id,
                type: b.type,
                content: b.content || '',
                pos: b.pos || '',
                title: b.title || '',
                quantity: b.quantity || 0,
                unit: b.unit || 'Stk',
                price: b.price || 0,
                description: b.description || '',
                fontSize: b.fontSize || 10,
                fett: b.fett || false,
                optional: b.optional || false
            }));

            const docTypeLabel = GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === selectedDocType)?.label || selectedDocType;
            const dokumentnummer = `${selectedDocType.substring(0, 3)}-${Date.now()}`;

            const request = {
                dokumentTyp: selectedDocType,
                templateName: 'default',
                kopfdaten: {
                    dokumentnummer,
                    rechnungsDatum: new Date().toISOString().split('T')[0],
                    leistungsDatum: null,
                    kundenName: 'Musterkunde GmbH',
                    kundenAdresse: 'Musterstraße 1\n12345 Musterstadt',
                    betreff: docTypeLabel,
                    kundennummer: '',
                    bezugsdokument: '',
                    projektnummer: '',
                    bauvorhaben: ''
                },
                layoutBlocks,
                contentBlocks,
                schlusstext: '',
                backgroundImagePage1: backgroundImage || null,
                backgroundImagePage2: null // Not used yet in DocumentBuilder
            };

            const response = await fetch('/api/dokument-generator/pdf', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            if (!response.ok) {
                throw new Error('PDF-Generierung fehlgeschlagen');
            }

            // Download the PDF
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${selectedDocType.toLowerCase()}_${dokumentnummer}.pdf`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error('PDF Export failed:', error);
            toast.error('PDF-Export fehlgeschlagen: ' + (error instanceof Error ? error.message : 'Unbekannter Fehler'));
        }
    };

    const { setNodeRef: setDroppableRef } = useDroppable({ id: 'canvas' });

    const getBlockStyles = (item: FormBlock): React.CSSProperties => {
        const defaults = STYLE_DEFAULTS[item.type] || {};
        const merged = { ...defaults, ...item.styles };
        return {
            left: item.x,
            top: item.y,
            width: item.width,
            height: item.height,
            position: 'absolute',
            zIndex: item.z,
            fontSize: merged.fontSize,
            fontWeight: merged.fontWeight as React.CSSProperties['fontWeight'],
            textAlign: merged.textAlign,
            color: merged.color
        };
    };

    const renderBgBlock = (block: FormBlock) => {
        let content = block.content;
        if (block.type === 'dokumenttyp') {
            const label = GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === selectedDocType)?.label || selectedDocType;
            content = label;
        }

        switch (block.type) {
            case 'logo': return <img src={content || '/image001.png'} alt="Logo" className="w-full h-full object-contain opacity-50" />;
            case 'table': return null;
            default: return <div className="p-1 whitespace-pre-wrap">{content || block.type}</div>;
        }
    };

    return (
        <PageLayout
            ribbonCategory="Kommunikation"
            title="DOKUMENTEN-GENERATOR"
            subtitle="Erstellen Sie Dokumente basierend auf Ihren Vorlagen."
            actions={
                <div className="flex gap-2 items-center">
                    {/* Undo/Redo Buttons */}
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={undo}
                        disabled={historyIndex <= 0}
                        className="rounded-full w-9 h-9 p-0"
                        title="Rückgängig"
                    >
                        <Undo2 className="w-4 h-4" />
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={redo}
                        disabled={historyIndex >= history.length - 1}
                        className="rounded-full w-9 h-9 p-0"
                        title="Wiederherstellen"
                    >
                        <Redo2 className="w-4 h-4" />
                    </Button>

                    <div className="w-px h-6 bg-slate-200 mx-1" />

                    <Select
                        options={GESCHAEFTSDOKUMENT_TYPEN.map(t => ({ value: t.value, label: t.label }))}
                        value={selectedDocType}
                        onChange={setSelectedDocType}
                        className="w-48 bg-white"
                    />
                    <Button onClick={() => { setBlocks([]); setHistory([[]]); setHistoryIndex(0); }} variant="outline" size="sm">
                        <Trash2 className="w-4 h-4 mr-2" />
                        Alles löschen
                    </Button>
                    <Button size="sm">
                        <Calculator className="w-4 h-4 mr-2" />
                        Fertigstellen
                    </Button>
                    <Button onClick={handleExportPdf} variant="outline" size="sm" className="text-rose-600 border-rose-300 hover:bg-rose-50">
                        <Download className="w-4 h-4 mr-2" />
                        PDF Export
                    </Button>
                    <div className="w-px h-6 bg-slate-200 mx-1" />
                    <Button
                        variant={showAbschluss ? "default" : "outline"}
                        size="sm"
                        onClick={() => setShowAbschluss(!showAbschluss)}
                        className={showAbschluss ? "bg-rose-600 hover:bg-rose-700" : "text-rose-600 border-rose-300 hover:bg-rose-50"}
                        title="Endsumme / Abschluss ein- oder ausblenden"
                    >
                        <Sigma className="w-4 h-4 mr-2" />
                        Abschluss
                    </Button>
                </div>
            }
        >
            {/* Formatting Ribbon - Word-Style */}
            <div className="bg-slate-50 border-b border-slate-200 px-4 py-2 mb-4 rounded-lg shadow-sm">
                <div className="flex items-center gap-1 flex-wrap">
                    {/* Font Controls */}
                    <div className="flex items-center gap-1 pr-3 border-r border-slate-200">
                        <select className="h-8 px-2 text-sm border border-slate-200 rounded focus:ring-2 focus:ring-rose-500 focus:outline-none bg-white">
                            <option>Inter</option>
                            <option>Arial</option>
                            <option>Times New Roman</option>
                            <option>Courier New</option>
                        </select>
                        <div className="flex items-center border border-slate-200 rounded bg-white">
                            <button className="p-1.5 hover:bg-slate-100" title="Schriftgröße verkleinern">
                                <Minus className="w-3 h-3" />
                            </button>
                            <input type="number" defaultValue={12} className="w-10 text-center text-sm h-8 border-x border-slate-200 focus:outline-none" />
                            <button className="p-1.5 hover:bg-slate-100" title="Schriftgröße vergrößern">
                                <Plus className="w-3 h-3" />
                            </button>
                        </div>
                    </div>

                    {/* Text Formatting */}
                    <div className="flex items-center gap-0.5 px-3 border-r border-slate-200">
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Fett (Strg+B)">
                            <Bold className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Kursiv (Strg+I)">
                            <Italic className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Unterstrichen (Strg+U)">
                            <Underline className="w-4 h-4" />
                        </button>
                    </div>

                    {/* Alignment */}
                    <div className="flex items-center gap-0.5 px-3 border-r border-slate-200">
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors bg-slate-100" title="Linksbündig">
                            <AlignLeft className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Zentriert">
                            <AlignCenter className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Rechtsbündig">
                            <AlignRight className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Blocksatz">
                            <AlignJustify className="w-4 h-4" />
                        </button>
                    </div>

                    {/* Lists */}
                    <div className="flex items-center gap-0.5 px-3">
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Aufzählung">
                            <List className="w-4 h-4" />
                        </button>
                        <button className="p-2 hover:bg-slate-100 rounded transition-colors" title="Nummerierung">
                            <ListOrdered className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            </div>

            <DndContext
                sensors={sensors}
                collisionDetection={rectIntersection}
                onDragStart={handleDragStart}
                onDragEnd={handleDragEnd}
            >
                <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 h-[calc(100vh-12rem)]">
                    {/* Sidebar */}
                    <div className="lg:col-span-1 flex flex-col gap-4 h-full">
                        <div className="flex bg-slate-100 p-1 rounded-lg">
                            <button
                                className={cn("flex-1 py-2 text-sm font-medium rounded-md transition-all", activeTab === 'TEXT' ? "bg-white shadow text-slate-900" : "text-slate-500 hover:text-slate-700")}
                                onClick={() => setActiveTab('TEXT')}
                            >
                                Textbausteine
                            </button>
                            <button
                                className={cn("flex-1 py-2 text-sm font-medium rounded-md transition-all", activeTab === 'SERVICE' ? "bg-white shadow text-slate-900" : "text-slate-500 hover:text-slate-700")}
                                onClick={() => setActiveTab('SERVICE')}
                            >
                                Leistungen
                            </button>
                        </div>

                        <div className="flex-1 overflow-y-auto space-y-3 pr-2">
                            {loadingData ? (
                                <div className="text-center text-slate-400 py-8 text-sm">Lade Daten...</div>
                            ) : activeTab === 'TEXT' ? (
                                textTemplates.length > 0 ? (
                                    textTemplates.map(tpl => <DraggableSource key={tpl.id} source={tpl} />)
                                ) : (
                                    <div className="text-center text-slate-400 py-8 text-sm">Keine Textbausteine vorhanden</div>
                                )
                            ) : (
                                serviceTemplates.map(srv => <DraggableSource key={srv.id} source={srv} />)
                            )}
                        </div>
                    </div>

                    {/* Canvas - A4 Page Containers */}
                    <div className="lg:col-span-3 h-full overflow-y-auto bg-slate-200/50 p-8 flex flex-col items-center gap-8 shadow-inner">

                        {/* SortableContext über ALLE Seiten für Drag & Drop */}
                        <SortableContext items={blocks} strategy={verticalListSortingStrategy}>

                            {pages.map((page, pageIndex) => (
                                <div
                                    key={pageIndex}
                                    className="bg-white shadow-lg relative transition-all ring-1 ring-slate-900/5 shrink-0"
                                    style={{
                                        width: '595px',
                                        height: `${A4_HEIGHT}px`,
                                        // Dynamic positioning based on Formularwesen Table definition (for Page 1)
                                        // Subsequent pages get standard padding
                                        paddingTop: (pageIndex === 0 && tableBlock) ? `${tableBlock.y}px` : `${PAGE_MARGIN_TOP}px`,
                                        paddingBottom: `${PAGE_MARGIN_BOTTOM}px`,
                                        paddingLeft: (pageIndex === 0 && tableBlock) ? `${tableBlock.x}px` : '40px',
                                        // Calculate right padding to ensure content has exact table width
                                        paddingRight: (pageIndex === 0 && tableBlock) ? `${595 - tableBlock.x - tableBlock.width}px` : '40px',
                                        overflow: 'hidden'
                                    }}
                                >
                                    {/* Briefpapier Hintergrund (nur auf Seite 1) */}
                                    {pageIndex === 0 && backgroundImage && (
                                        <img
                                            src={backgroundImage}
                                            alt="Briefpapier"
                                            className="absolute inset-0 w-full h-full object-cover pointer-events-none opacity-30 z-0"
                                        />
                                    )}

                                    {/* Background Blocks - nur auf Seite 1 */}
                                    {pageIndex === 0 && bgBlocks.map(block => (
                                        <div key={block.id} style={getBlockStyles(block)} className="pointer-events-none text-slate-400 select-none border border-dashed border-slate-100">
                                            {renderBgBlock(block)}
                                        </div>
                                    ))}

                                    {/* Seitenzahl */}
                                    <div className="absolute top-4 right-8 text-xs text-slate-400 font-mono">
                                        Seite {pageIndex + 1} / {pages.length}
                                    </div>

                                    {/* Übertrag-Anzeige (wenn Tabelle von Vorseite fortgesetzt wird) */}
                                    {pageIndex > 0 && page.hasTableStart && (
                                        <div className="flex justify-between text-xs text-slate-500 mb-2 italic border-b border-slate-100 pb-1">
                                            <span>Übertrag von Vorseite</span>
                                            <span>...</span>
                                        </div>
                                    )}

                                    {/* Table Header - automatisch auf jeder Seite mit SERVICE Blöcken */}
                                    {((pageIndex === 0 && page.blocks.some(b => b.type === 'SERVICE')) ||
                                        (pageIndex > 0 && page.hasTableStart && page.blocks.some(b => b.type === 'SERVICE'))) && (
                                            <div className="grid grid-cols-12 gap-0 text-xs font-bold text-slate-700 border-b-2 border-slate-800 pb-1 mb-2 bg-slate-50/50">
                                                <div className="col-span-1 pl-2">Pos.</div>
                                                <div className="col-span-1 text-center">Menge</div>
                                                <div className="col-span-1 text-center">ME</div>
                                                <div className="col-span-5">Bezeichnung</div>
                                                <div className="col-span-2 text-right">EP</div>
                                                <div className="col-span-2 text-right pr-2">GP</div>
                                            </div>
                                        )}

                                    {/* Content Area - Blöcke dieser Seite */}
                                    <div
                                        ref={pageIndex === 0 ? setDroppableRef : undefined}
                                        className="flex flex-col w-full relative z-10"
                                    >
                                        {page.blocks.map(block => (
                                            <SortableBlock
                                                key={block.id}
                                                block={block}
                                                onRemove={removeBlock}
                                                onChange={updateBlock}
                                                onHeightChange={handleHeightChange}
                                            />
                                        ))}

                                        {/* Dropzone-Hinweis auf leerer erster Seite */}
                                        {pages.length === 1 && page.blocks.length === 0 && (
                                            <div className="mt-10 border-2 border-dashed border-slate-200 rounded-lg h-32 flex items-center justify-center text-slate-400 text-sm">
                                                Ziehen Sie Elemente hierher
                                            </div>
                                        )}
                                    </div>

                                    {/* Visueller Footer-Bereich (Lochmarke) */}
                                    <div className="absolute left-2 top-[421px] w-2 h-px bg-slate-300"></div>
                                </div>
                            ))}

                            {/* Abschlussblock (Summen) unter der letzten Seite */}
                            {showAbschluss && (
                                <div className="w-[595px] bg-white p-6 shadow-lg">
                                    <AbschlussBlock blocks={blocks} />
                                </div>
                            )}

                        </SortableContext>
                    </div>
                </div>

                <DragOverlay>
                    {activeDragItem ? (
                        <div className="p-3 bg-white border border-rose-200 rounded-lg shadow-lg opacity-90 w-64">
                            <span className="font-medium text-rose-700">{activeDragItem.label}</span>
                        </div>
                    ) : null}
                </DragOverlay>
            </DndContext>
        </PageLayout >
    );
}
