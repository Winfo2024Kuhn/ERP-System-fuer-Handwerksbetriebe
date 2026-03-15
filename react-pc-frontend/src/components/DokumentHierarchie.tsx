import { useState, useMemo, useCallback } from 'react';
import {
    Calendar,
    Check,
    ChevronDown,
    ChevronRight,
    Edit2,
    FileText,
    GitBranch,
    Lock,
    Plus,
    Trash2,
    User,
} from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Input } from './ui/input';
import { Label } from './ui/label';
import type {
    AusgangsGeschaeftsDokument,
    AusgangsGeschaeftsDokumentTyp,
    AbrechnungsverlaufDto,
} from '../types';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../types';
import type { DocBlock } from './document-editor/types';
import { TeilrechnungPositionRow, getAllServiceBlocks, filterBlocksBySelectedIds } from './TeilrechnungPositionRow';

// ============ Farbkonfiguration ============

const TYP_COLORS: Record<string, string> = {
    'ANGEBOT': 'bg-blue-50 text-blue-700 border-blue-200',
    'AUFTRAGSBESTAETIGUNG': 'bg-purple-50 text-purple-700 border-purple-200',
    'RECHNUNG': 'bg-rose-50 text-rose-700 border-rose-200',
    'TEILRECHNUNG': 'bg-rose-50 text-rose-600 border-rose-200',
    'ABSCHLAGSRECHNUNG': 'bg-orange-50 text-orange-700 border-orange-200',
    'SCHLUSSRECHNUNG': 'bg-rose-100 text-rose-800 border-rose-300',
    'GUTSCHRIFT': 'bg-green-50 text-green-700 border-green-200',
    'STORNO': 'bg-red-50 text-red-700 border-red-200',
};

const formatCurrency = (val: number | null | undefined) =>
    val !== null && val !== undefined
        ? new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val)
        : '-';

// ============ Baum-Typen ============

type DokumentTreeNode = {
    dok: AusgangsGeschaeftsDokument;
    children: DokumentTreeNode[];
    depth: number;
};

// ============ Props ============

// ============ DokumentHierarchie Props ============

interface DokumentHierarchieProps {
    ausgangsDokumente: AusgangsGeschaeftsDokument[];
    projektId?: number;
    anfrageId?: number;
    angebotId?: number;
    /** Wenn gesetzt, werden nur diese Dokumenttypen im Erstellen-Dialog anfragenn */
    allowedTypes?: AusgangsGeschaeftsDokumentTyp[];
    /** Wenn true, werden Rechnungserstellungs-Aktionen ausgeblendet */
    hideRechnungActions?: boolean;
    onRefresh: () => void;
    confirmDialog: (opts: { title: string; message: string; variant: string; confirmLabel: string }) => Promise<boolean>;
    toast: { error: (msg: string) => void; success: (msg: string) => void };
}

export function DokumentHierarchie({
    ausgangsDokumente,
    projektId,
    anfrageId,
    angebotId,
    allowedTypes,
    hideRechnungActions,
    onRefresh,
    confirmDialog,
    toast,
}: DokumentHierarchieProps) {
    const contextAnfrageId = anfrageId ?? angebotId;
    // URL-Param für den Dokument-Editor: projektId oder anfrageId
    const editorParam = projektId ? `projektId=${projektId}` : `anfrageId=${contextAnfrageId}`;
    // Payload-Felder für die Dokumenterstellung
    const createPayloadIds = projektId ? { projektId } : { anfrageId: contextAnfrageId };
    const [actionMenuId, setActionMenuId] = useState<number | null>(null);
    const [collapsedIds, setCollapsedIds] = useState<Set<number>>(new Set());

    // Rechnungserstellungsdialog
    const [showRechnungDialog, setShowRechnungDialog] = useState(false);
    const [rechnungBasisDok, setRechnungBasisDok] = useState<AusgangsGeschaeftsDokument | null>(null);
    const [abrechnungsverlauf, setAbrechnungsverlauf] = useState<AbrechnungsverlaufDto | null>(null);
    const [rechnungTyp, setRechnungTyp] = useState<AusgangsGeschaeftsDokumentTyp>('ABSCHLAGSRECHNUNG');
    const [abschlagsBetrag, setAbschlagsBetrag] = useState('');
    const [rechnungLoading, setRechnungLoading] = useState(false);

    // Teilrechnung Positions-Auswahl
    const [basisDokBlocks, setBasisDokBlocks] = useState<DocBlock[]>([]);
    const [selectedBlockIds, setSelectedBlockIds] = useState<Set<string>>(new Set());
    const [expandedBlockIds, setExpandedBlockIds] = useState<Set<string>>(new Set());

    // Abschlagsrechnung Eingabemodus
    const [abschlagsEingabeModus, setAbschlagsEingabeModus] = useState<'netto' | 'brutto' | 'prozent'>('netto');

    // Dokumenttyp-Dialog
    const [showDokumentTypDialog, setShowDokumentTypDialog] = useState(false);

    // Prüfen ob bereits ein Basisdokument (Root ohne Vorgänger) existiert
    const hasBasisdokument = useMemo(
        () => ausgangsDokumente.some(d => !d.vorgaengerId),
        [ausgangsDokumente]
    );

    // ============ Baum aufbauen ============

    const tree = useMemo<DokumentTreeNode[]>(() => {
        if (ausgangsDokumente.length === 0) return [];

        const byId = new Map(ausgangsDokumente.map(d => [d.id, d]));
        const childrenMap = new Map<number, AusgangsGeschaeftsDokument[]>();
        const rootDocs: AusgangsGeschaeftsDokument[] = [];

        for (const dok of ausgangsDokumente) {
            if (dok.vorgaengerId && byId.has(dok.vorgaengerId)) {
                const existing = childrenMap.get(dok.vorgaengerId) || [];
                existing.push(dok);
                childrenMap.set(dok.vorgaengerId, existing);
            } else {
                rootDocs.push(dok);
            }
        }

        const build = (dok: AusgangsGeschaeftsDokument, depth: number): DokumentTreeNode => ({
            dok,
            children: (childrenMap.get(dok.id) || []).map(c => build(c, depth + 1)),
            depth,
        });

        return rootDocs.map(d => build(d, 0));
    }, [ausgangsDokumente]);

    // ============ Rechnung-Dialog öffnen ============

    const handleOpenRechnungDialog = useCallback(async (basisDok: AusgangsGeschaeftsDokument) => {
        setRechnungBasisDok(basisDok);
        setRechnungTyp('ABSCHLAGSRECHNUNG');
        setAbschlagsBetrag('');
        setAbschlagsEingabeModus('netto');
        setShowRechnungDialog(true);
        setAbrechnungsverlauf(null);
        setBasisDokBlocks([]);
        setSelectedBlockIds(new Set());
        setExpandedBlockIds(new Set());
        try {
            const [verlaufRes, dokRes] = await Promise.all([
                fetch(`/api/ausgangs-dokumente/${basisDok.id}/abrechnungsverlauf`),
                fetch(`/api/ausgangs-dokumente/${basisDok.id}`)
            ]);
            if (verlaufRes.ok) setAbrechnungsverlauf(await verlaufRes.json());
            if (dokRes.ok) {
                const dokData = await dokRes.json();
                if (dokData.positionenJson) {
                    try {
                        const parsed = JSON.parse(dokData.positionenJson);
                        const blocks: DocBlock[] = Array.isArray(parsed) ? parsed : (parsed.blocks || []);
                        setBasisDokBlocks(blocks);
                        // Alle SERVICE-Blocks standardmäßig auswählen
                        const allIds = new Set<string>();
                        for (const b of blocks) {
                            if (b.type === 'SERVICE') allIds.add(b.id);
                            if (b.type === 'SECTION_HEADER' && b.children) {
                                for (const c of b.children) {
                                    if (c.type === 'SERVICE') allIds.add(c.id);
                                }
                            }
                        }
                        setSelectedBlockIds(allIds);
                    } catch { /* ignore parse error */ }
                }
            }
        } catch { /* ignore */ }
    }, []);



    // ============ Berechneter Abschlagsbetrag (netto) ============

    const berechneterAbschlagNetto = useMemo(() => {
        const val = parseFloat(abschlagsBetrag);
        if (isNaN(val) || val <= 0) return 0;
        if (abschlagsEingabeModus === 'netto') return val;
        if (abschlagsEingabeModus === 'brutto') return val / 1.19;
        // prozent
        const basis = abrechnungsverlauf?.basisdokumentBetragNetto ?? rechnungBasisDok?.betragNetto ?? 0;
        return basis * (val / 100);
    }, [abschlagsBetrag, abschlagsEingabeModus, abrechnungsverlauf, rechnungBasisDok]);

    // ============ Rechnung erstellen ============

    const handleCreateRechnung = useCallback(async () => {
        if (!rechnungBasisDok) return;
        setRechnungLoading(true);
        try {
            let betrag: number | undefined;
            let positionenJson: string | undefined;

            if (rechnungTyp === 'ABSCHLAGSRECHNUNG') {
                betrag = berechneterAbschlagNetto;
                if (!betrag || betrag <= 0) {
                    toast.error('Bitte geben Sie einen gültigen Betrag ein.');
                    return;
                }
            } else if (rechnungTyp === 'SCHLUSSRECHNUNG') {
                betrag = abrechnungsverlauf?.restbetrag ?? 0;
            } else if (rechnungTyp === 'TEILRECHNUNG') {
                // Nur ausgewählte Positionen übernehmen
                if (selectedBlockIds.size === 0) {
                    toast.error('Bitte wählen Sie mindestens eine Leistung aus.');
                    return;
                }
                const filteredBlocks = filterBlocksBySelectedIds(basisDokBlocks, selectedBlockIds);
                positionenJson = JSON.stringify({ blocks: filteredBlocks, globalRabatt: 0 });
                // Betrag aus gewählten Positionen berechnen für Abrechnungsverlauf
                betrag = getAllServiceBlocks(basisDokBlocks)
                    .filter(b => selectedBlockIds.has(b.id))
                    .reduce((sum, b) => sum + (b.quantity || 0) * (b.price || 0), 0);
            }

            const response = await fetch('/api/ausgangs-dokumente', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    typ: rechnungTyp,
                    ...createPayloadIds,
                    vorgaengerId: rechnungBasisDok.id,
                    betreff: rechnungBasisDok.betreff,
                    betragNetto: betrag,
                    ...(positionenJson ? { positionenJson } : {}),
                }),
            });

            if (response.ok) {
                const newDoc = await response.json();
                onRefresh();
                setShowRechnungDialog(false);
                window.open(`/dokument-editor?${editorParam}&dokumentId=${newDoc.id}`, '_blank');
            } else {
                const error = await response.text();
                toast.error(error || 'Fehler beim Erstellen der Rechnung');
            }
        } catch {
            toast.error('Fehler beim Erstellen der Rechnung');
        } finally {
            setRechnungLoading(false);
        }
    }, [rechnungBasisDok, rechnungTyp, abschlagsBetrag, berechneterAbschlagNetto, abrechnungsverlauf, editorParam, createPayloadIds, onRefresh, toast, selectedBlockIds, basisDokBlocks]);

    // ============ Knoten ein-/ausklappen ============

    const toggleCollapse = (id: number) => {
        setCollapsedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    // ============ Einzelne Dokument-Zeile rendern ============

    const renderNode = (node: DokumentTreeNode): React.ReactNode => {
        const { dok, children, depth } = node;
        const typConfig = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dok.typ);
        const hasChildren = children.length > 0;
        const isCollapsed = collapsedIds.has(dok.id);

        return (
            <div key={dok.id}>
                <div
                    className={cn(
                        'bg-white rounded-xl border border-slate-200 p-4 hover:border-rose-300 hover:shadow-md transition-all cursor-pointer group relative',
                        dok.storniert && 'opacity-60'
                    )}
                    style={{ marginLeft: depth * 28 }}
                    onClick={() => setActionMenuId(actionMenuId === dok.id ? null : dok.id)}
                    onDoubleClick={() => window.open(`/dokument-editor?${editorParam}&dokumentId=${dok.id}`, '_blank')}
                    title="Klick für Aktionen, Doppelklick zum Öffnen"
                >
                    {/* Verbindungslinie links */}
                    {depth > 0 && (
                        <div
                            className="absolute -left-3 top-1/2 w-3 border-t border-slate-300"
                            style={{ marginLeft: 0 }}
                        />
                    )}

                    <div className="flex items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 mb-1">
                                {/* Collapse button */}
                                {hasChildren && (
                                    <button
                                        onClick={(e) => { e.stopPropagation(); toggleCollapse(dok.id); }}
                                        className="w-5 h-5 flex items-center justify-center text-slate-400 hover:text-rose-600 transition-colors"
                                    >
                                        {isCollapsed
                                            ? <ChevronRight className="w-4 h-4" />
                                            : <ChevronDown className="w-4 h-4" />
                                        }
                                    </button>
                                )}
                                {!hasChildren && depth > 0 && (
                                    <GitBranch className="w-4 h-4 text-slate-300 rotate-180" />
                                )}

                                <span className={cn(
                                    "text-xs font-medium px-2 py-0.5 rounded-full border",
                                    TYP_COLORS[dok.typ] || 'bg-slate-50 text-slate-700 border-slate-200'
                                )}>
                                    {typConfig?.label || dok.typ}
                                    {dok.typ === 'ABSCHLAGSRECHNUNG' && (dok as any).abschlagsNummer
                                        ? ` #${(dok as any).abschlagsNummer}` : ''}
                                </span>

                                {dok.storniert && (
                                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-red-100 text-red-700 border border-red-200">
                                        Storniert
                                    </span>
                                )}
                                {!dok.storniert && dok.gebucht && (
                                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-200">
                                        <Lock className="w-3 h-3 inline-block mr-1" />
                                        Gebucht
                                    </span>
                                )}
                                {!dok.storniert && !dok.gebucht && (
                                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 border border-amber-200">
                                        Entwurf
                                    </span>
                                )}
                            </div>
                            <p className="font-semibold text-slate-900 group-hover:text-rose-700 transition-colors">
                                {dok.dokumentNummer}
                            </p>
                            {dok.betreff && (
                                <p className="text-sm text-slate-600 truncate mt-0.5">{dok.betreff}</p>
                            )}
                            <div className="flex items-center gap-4 mt-2 text-xs text-slate-400">
                                <span>
                                    <Calendar className="w-3 h-3 inline-block mr-1" />
                                    {new Date(dok.datum).toLocaleDateString('de-DE')}
                                </span>
                                {dok.kundenName && (
                                    <span>
                                        <User className="w-3 h-3 inline-block mr-1" />
                                        {dok.kundenName}
                                    </span>
                                )}
                                {dok.erstelltVonName && (
                                    <span title="Erstellt von">
                                        <Edit2 className="w-3 h-3 inline-block mr-1" />
                                        {dok.erstelltVonName}
                                    </span>
                                )}
                                {dok.vorgaengerNummer && (
                                    <span className="text-slate-400" title="Basisdokument">
                                        ← {dok.vorgaengerNummer}
                                    </span>
                                )}
                            </div>
                        </div>
                        <div className="text-right shrink-0">
                            {dok.betragNetto !== undefined && dok.betragNetto !== null ? (
                                <>
                                    <p className="text-lg font-bold text-slate-900">
                                        {formatCurrency(dok.betragNetto)}
                                    </p>
                                    <p className="text-xs text-slate-400">netto</p>
                                    {dok.betragBrutto !== undefined && dok.betragBrutto !== null && (
                                        <>
                                            <p className="text-sm font-semibold text-slate-600 mt-0.5">
                                                {formatCurrency(dok.betragBrutto)}
                                            </p>
                                            <p className="text-xs text-slate-400">brutto</p>
                                        </>
                                    )}
                                </>
                            ) : null}
                        </div>
                    </div>

                    {/* Aktionsmenü */}
                    {actionMenuId === dok.id && (
                        <div
                            className="absolute right-4 top-4 bg-white rounded-lg shadow-xl border border-slate-200 py-2 z-20 min-w-[220px]"
                            onClick={e => e.stopPropagation()}
                        >
                            <button
                                className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-rose-50 hover:text-rose-700 flex items-center gap-2"
                                onClick={() => {
                                    window.open(`/dokument-editor?${editorParam}&dokumentId=${dok.id}`, '_blank');
                                    setActionMenuId(null);
                                }}
                            >
                                <FileText className="w-4 h-4" />
                                Öffnen (neuer Tab)
                            </button>

                            {/* Umwandeln / Rechnung erstellen */}
                            {(dok.typ === 'ANGEBOT' || dok.typ === 'AUFTRAGSBESTAETIGUNG') && (
                                <>
                                    <hr className="my-1 border-slate-100" />
                                    <p className="px-4 py-1 text-xs text-slate-400 font-medium">Umwandeln in:</p>

                                    {dok.typ === 'ANGEBOT' && (
                                        <button
                                            className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-700 flex items-center gap-2"
                                            onClick={async () => {
                                                if (await confirmDialog({ title: "In Auftragsbestätigung umwandeln", message: `Anfrage ${dok.dokumentNummer} in Auftragsbestätigung umwandeln?`, variant: "info", confirmLabel: "Umwandeln" })) {
                                                    try {
                                                        const response = await fetch('/api/ausgangs-dokumente', {
                                                            method: 'POST',
                                                            headers: { 'Content-Type': 'application/json' },
                                                            body: JSON.stringify({
                                                                typ: 'AUFTRAGSBESTAETIGUNG',
                                                                ...createPayloadIds,
                                                                vorgaengerId: dok.id,
                                                                betreff: dok.betreff,
                                                                betragNetto: dok.betragNetto,
                                                            }),
                                                        });
                                                        if (response.ok) {
                                                            const newDoc = await response.json();
                                                            onRefresh();
                                                            window.open(`/dokument-editor?${editorParam}&dokumentId=${newDoc.id}`, '_blank');
                                                        }
                                                    } catch (e) { console.error(e); }
                                                }
                                                setActionMenuId(null);
                                            }}
                                        >
                                            → Auftragsbestätigung
                                        </button>
                                    )}

                                    {/* Rechnung erstellen → Dialog */}
                                    {!hideRechnungActions && (
                                    <button
                                        className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-rose-50 hover:text-rose-700 flex items-center gap-2"
                                        onClick={() => {
                                            handleOpenRechnungDialog(dok);
                                            setActionMenuId(null);
                                        }}
                                    >
                                        → Rechnung erstellen
                                    </button>
                                    )}
                                </>
                            )}

                            {/* Löschen */}
                            {dok.typ !== 'RECHNUNG' && dok.typ !== 'GUTSCHRIFT' && dok.typ !== 'STORNO'
                                && dok.typ !== 'TEILRECHNUNG' && dok.typ !== 'ABSCHLAGSRECHNUNG' && dok.typ !== 'SCHLUSSRECHNUNG'
                                && !dok.gebucht && (
                                <>
                                    <hr className="my-1 border-slate-100" />
                                    <button
                                        className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
                                        onClick={async () => {
                                            if (await confirmDialog({ title: "Dokument löschen", message: `Dokument ${dok.dokumentNummer} wirklich löschen?`, variant: "danger", confirmLabel: "Löschen" })) {
                                                try {
                                                    const response = await fetch(`/api/ausgangs-dokumente/${dok.id}`, { method: 'DELETE' });
                                                    if (response.ok) {
                                                        onRefresh();
                                                    } else {
                                                        const error = await response.text();
                                                        toast.error(error || 'Löschen fehlgeschlagen');
                                                    }
                                                } catch (e) { console.error(e); }
                                            }
                                            setActionMenuId(null);
                                        }}
                                    >
                                        <Trash2 className="w-4 h-4" />
                                        Löschen
                                    </button>
                                </>
                            )}
                        </div>
                    )}
                </div>

                {/* Kinder rekursiv rendern */}
                {hasChildren && !isCollapsed && (
                    <div className="relative ml-3 pl-3 border-l-2 border-slate-200">
                        {children.map(child => renderNode(child))}
                    </div>
                )}
            </div>
        );
    };

    // ============ Render ============

    return (
        <div className="space-y-6">
            <div>
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-medium text-slate-900">Geschäftsdokumente</h3>
                    <div className="flex items-center gap-3">
                        <span className="text-sm text-slate-500">
                            {ausgangsDokumente.length} Dokument{ausgangsDokumente.length !== 1 ? 'e' : ''}
                        </span>
                        <Button
                            size="sm"
                            className="bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={() => setShowDokumentTypDialog(true)}
                            disabled={hasBasisdokument}
                            title={hasBasisdokument ? 'Es existiert bereits ein Basisdokument' : undefined}
                        >
                            <Plus className="w-4 h-4 mr-1" /> Dokument erstellen
                        </Button>
                    </div>
                </div>

                {tree.length > 0 ? (
                    <div className="grid gap-3">
                        {tree.map(node => renderNode(node))}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-8 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                        <FileText className="w-10 h-10 text-slate-300 mb-2" />
                        <p className="text-sm">Keine Geschäftsdokumente vorhanden.</p>
                        <p className="text-xs text-slate-400 mt-1">Klicken Sie oben auf "Dokument erstellen"</p>
                    </div>
                )}
            </div>

            {/* Dokumenttyp Auswahl Dialog */}
            <Dialog open={showDokumentTypDialog} onOpenChange={setShowDokumentTypDialog}>
                <DialogContent className="sm:max-w-lg">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">Dokument erstellen</DialogTitle>
                        <p className="text-sm text-slate-500">Welche Art von Dokument möchten Sie erstellen?</p>
                    </DialogHeader>
                    <div className="grid grid-cols-2 gap-3 py-4">
                        {AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN
                            .filter(typ => {
                                const defaultTypes = ['ANGEBOT', 'RECHNUNG', 'AUFTRAGSBESTAETIGUNG'];
                                const allowed = allowedTypes || defaultTypes;
                                return allowed.includes(typ.value as AusgangsGeschaeftsDokumentTyp);
                            })
                            .map(typ => {
                                const isBaseType = typ.value === 'ANGEBOT' || typ.value === 'AUFTRAGSBESTAETIGUNG';
                                const disabled = isBaseType && hasBasisdokument;
                                return (
                                    <button
                                        key={typ.value}
                                        onClick={() => {
                                            if (disabled) return;
                                            setShowDokumentTypDialog(false);
                                            window.open(`/dokument-editor?${editorParam}&dokumentTyp=${typ.value}`, '_blank');
                                        }}
                                        disabled={disabled}
                                        className={`flex items-center gap-3 p-4 rounded-xl border transition-all text-left group ${
                                            disabled
                                                ? 'border-slate-100 bg-slate-50 opacity-50 cursor-not-allowed'
                                                : 'border-slate-200 hover:border-rose-300 hover:bg-rose-50'
                                        }`}
                                        title={disabled ? 'Es existiert bereits ein Basisdokument' : undefined}
                                    >
                                        <div className={`flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center transition-colors ${
                                            disabled ? 'bg-slate-100 text-slate-400' : 'bg-rose-100 text-rose-600 group-hover:bg-rose-200'
                                        }`}>
                                            <FileText className="w-5 h-5" />
                                        </div>
                                        <span className={`font-medium transition-colors ${
                                            disabled ? 'text-slate-400' : 'text-slate-700 group-hover:text-rose-700'
                                        }`}>
                                            {typ.label}
                                        </span>
                                    </button>
                                );
                            })}
                    </div>
                </DialogContent>
            </Dialog>

            {/* Rechnungserstellungsdialog */}
            <Dialog open={showRechnungDialog} onOpenChange={setShowRechnungDialog}>
                <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            Rechnung erstellen
                        </DialogTitle>
                        <p className="text-sm text-slate-500">
                            Basierend auf: {rechnungBasisDok?.dokumentNummer}
                        </p>
                    </DialogHeader>

                    <div className="space-y-5 py-4">
                        {/* Basisdokument-Info */}
                        <div className="p-4 bg-slate-50 rounded-xl border border-slate-200">
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="text-xs text-slate-500 mb-1">Basisdokument</p>
                                    <p className="font-semibold text-slate-900">{rechnungBasisDok?.dokumentNummer}</p>
                                    <p className="text-sm text-slate-500">{rechnungBasisDok?.betreff}</p>
                                </div>
                                <div className="text-right">
                                    <p className="text-xs text-slate-500 mb-1">Betrag Netto</p>
                                    <p className="text-xl font-bold text-slate-900">
                                        {formatCurrency(rechnungBasisDok?.betragNetto)}
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Abrechnungsverlauf */}
                        {abrechnungsverlauf && abrechnungsverlauf.positionen.length > 0 && (
                            <div className="space-y-2">
                                <h4 className="text-sm font-semibold text-slate-700">Bisherige Abrechnungen</h4>
                                <div className="space-y-1.5">
                                    {abrechnungsverlauf.positionen.map((pos) => {
                                        const posTypConfig = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === pos.typ);
                                        return (
                                            <div key={pos.id} className={cn(
                                                "flex items-center justify-between p-3 rounded-lg border",
                                                pos.storniert
                                                    ? 'bg-red-50 border-red-200 opacity-60'
                                                    : 'bg-white border-slate-100'
                                            )}>
                                                <div className="flex items-center gap-3">
                                                    <span className={cn(
                                                        "text-xs font-medium px-2 py-0.5 rounded-full border",
                                                        TYP_COLORS[pos.typ] || ''
                                                    )}>
                                                        {posTypConfig?.label || pos.typ}
                                                        {pos.abschlagsNummer ? ` #${pos.abschlagsNummer}` : ''}
                                                    </span>
                                                    <span className="text-sm text-slate-600">{pos.dokumentNummer}</span>
                                                    <span className="text-xs text-slate-400">
                                                        {new Date(pos.datum).toLocaleDateString('de-DE')}
                                                    </span>
                                                    {pos.storniert && (
                                                        <span className="text-xs text-red-600 font-medium">Storniert</span>
                                                    )}
                                                </div>
                                                <span className={cn(
                                                    "font-semibold",
                                                    pos.storniert ? 'text-red-400 line-through' : 'text-slate-900'
                                                )}>
                                                    {formatCurrency(pos.betragNetto)}
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>

                                {/* Zusammenfassung */}
                                <div className="flex justify-between items-center p-3 bg-slate-100 rounded-lg border border-slate-200">
                                    <span className="text-sm font-medium text-slate-600">Bereits abgerechnet</span>
                                    <span className="font-bold text-slate-900">
                                        {formatCurrency(abrechnungsverlauf.bereitsAbgerechnet)}
                                    </span>
                                </div>
                                <div className="flex justify-between items-center p-3 bg-rose-50 rounded-lg border border-rose-200">
                                    <span className="text-sm font-bold text-rose-700">Verbleibender Restbetrag</span>
                                    <span className="text-lg font-bold text-rose-700">
                                        {formatCurrency(abrechnungsverlauf.restbetrag)}
                                    </span>
                                </div>

                                {/* Fortschrittsbalken */}
                                <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
                                    <div
                                        className="h-full bg-rose-500 rounded-full transition-all"
                                        style={{
                                            width: `${Math.min(100,
                                                abrechnungsverlauf.basisdokumentBetragNetto > 0
                                                    ? (abrechnungsverlauf.bereitsAbgerechnet / abrechnungsverlauf.basisdokumentBetragNetto) * 100
                                                    : 0
                                            )}%`
                                        }}
                                    />
                                </div>
                            </div>
                        )}

                        {/* Rechnungstyp-Auswahl */}
                        <div className="space-y-2">
                            <Label className="text-sm font-semibold text-slate-700">Rechnungstyp</Label>
                            <div className="grid grid-cols-3 gap-2">
                                {([
                                    { value: 'ABSCHLAGSRECHNUNG' as const, label: 'Abschlagsrechnung', desc: 'Pauschaler Abschlag' },
                                    { value: 'TEILRECHNUNG' as const, label: 'Teilrechnung', desc: 'Einzelne Positionen' },
                                    { value: 'SCHLUSSRECHNUNG' as const, label: 'Schlussrechnung', desc: 'Restbetrag' },
                                ]).map(opt => {
                                    const hatVorherigeAbrechnung = abrechnungsverlauf?.positionen.some(
                                        p => !p.storniert && (p.typ === 'TEILRECHNUNG' || p.typ === 'ABSCHLAGSRECHNUNG')
                                    );
                                    const isDisabled = opt.value === 'SCHLUSSRECHNUNG' && !hatVorherigeAbrechnung;
                                    return (
                                    <button
                                        key={opt.value}
                                        onClick={() => !isDisabled && setRechnungTyp(opt.value)}
                                        disabled={isDisabled}
                                        className={cn(
                                            "p-3 rounded-xl border text-left transition-all",
                                            isDisabled
                                                ? "border-slate-200 bg-slate-50 opacity-50 cursor-not-allowed"
                                                : rechnungTyp === opt.value
                                                    ? "border-rose-400 bg-rose-50 ring-2 ring-rose-200"
                                                    : "border-slate-200 hover:border-rose-300 hover:bg-rose-50/50"
                                        )}
                                        title={isDisabled ? 'Schlussrechnung ist erst nach einer Teilrechnung oder Abschlagsrechnung möglich' : undefined}
                                    >
                                        <p className={cn("text-sm font-medium", isDisabled ? "text-slate-400" : "text-slate-900")}>{opt.label}</p>
                                        <p className="text-xs text-slate-500 mt-0.5">{opt.desc}</p>
                                    </button>
                                    );
                                })}
                            </div>
                        </div>

                        {/* Betrag-Eingabe für Abschlagsrechnung */}
                        {rechnungTyp === 'ABSCHLAGSRECHNUNG' && (
                            <div className="space-y-3">
                                <Label className="text-sm font-semibold text-slate-700">
                                    Eingabemodus
                                </Label>
                                <div className="grid grid-cols-3 gap-2">
                                    {([
                                        { value: 'netto' as const, label: 'Netto absolut' },
                                        { value: 'brutto' as const, label: 'Brutto absolut' },
                                        { value: 'prozent' as const, label: 'Prozentual' },
                                    ]).map(m => (
                                        <button
                                            key={m.value}
                                            onClick={() => { setAbschlagsEingabeModus(m.value); setAbschlagsBetrag(''); }}
                                            className={cn(
                                                "px-3 py-2 rounded-lg border text-sm font-medium transition-all",
                                                abschlagsEingabeModus === m.value
                                                    ? "border-rose-400 bg-rose-50 text-rose-700"
                                                    : "border-slate-200 text-slate-600 hover:border-slate-300"
                                            )}
                                        >
                                            {m.label}
                                        </button>
                                    ))}
                                </div>
                                <div className="relative">
                                    <Input
                                        type="number"
                                        step={abschlagsEingabeModus === 'prozent' ? '1' : '0.01'}
                                        min="0"
                                        max={abschlagsEingabeModus === 'prozent' ? '100' : undefined}
                                        value={abschlagsBetrag}
                                        onChange={e => setAbschlagsBetrag(e.target.value)}
                                        placeholder={abschlagsEingabeModus === 'prozent' ? '0' : '0,00'}
                                        className="pr-8"
                                    />
                                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">
                                        {abschlagsEingabeModus === 'prozent' ? '%' : '€'}
                                    </span>
                                </div>
                                {berechneterAbschlagNetto > 0 && abschlagsEingabeModus !== 'netto' && (
                                    <p className="text-xs text-slate-500">
                                        = {formatCurrency(berechneterAbschlagNetto)} netto
                                    </p>
                                )}
                                {abrechnungsverlauf && berechneterAbschlagNetto > abrechnungsverlauf.restbetrag && (
                                    <p className="text-xs text-red-600">
                                        Betrag übersteigt den verfügbaren Restbetrag ({formatCurrency(abrechnungsverlauf.restbetrag)})
                                    </p>
                                )}
                            </div>
                        )}

                        {/* Schlussrechnung Info */}
                        {rechnungTyp === 'SCHLUSSRECHNUNG' && abrechnungsverlauf && (
                            <div className="p-4 bg-green-50 rounded-xl border border-green-200">
                                <p className="text-sm text-green-700">
                                    Die Schlussrechnung wird automatisch über den verbleibenden Restbetrag von{' '}
                                    <strong>{formatCurrency(abrechnungsverlauf.restbetrag)}</strong> erstellt.
                                </p>
                            </div>
                        )}

                        {/* Teilrechnung Positions-Auswahl */}
                        {rechnungTyp === 'TEILRECHNUNG' && (
                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <Label className="text-sm font-semibold text-slate-700">
                                        Leistungen auswählen
                                    </Label>
                                    {getAllServiceBlocks(basisDokBlocks).length > 0 && (
                                        <button
                                            className="text-xs text-rose-600 hover:text-rose-700 font-medium"
                                            onClick={() => {
                                                const allIds = new Set<string>();
                                                for (const b of basisDokBlocks) {
                                                    if (b.type === 'SERVICE') allIds.add(b.id);
                                                    if (b.type === 'SECTION_HEADER' && b.children) {
                                                        for (const c of b.children) {
                                                            if (c.type === 'SERVICE') allIds.add(c.id);
                                                        }
                                                    }
                                                }
                                                setSelectedBlockIds(prev =>
                                                    prev.size === allIds.size ? new Set() : allIds
                                                );
                                            }}
                                        >
                                            {selectedBlockIds.size === getAllServiceBlocks(basisDokBlocks).length ? 'Alle abwählen' : 'Alle auswählen'}
                                        </button>
                                    )}
                                </div>
                                {basisDokBlocks.length === 0 ? (
                                    <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 text-sm text-slate-500 text-center">
                                        Keine Positionen im Basisdokument vorhanden.
                                    </div>
                                ) : (
                                    <div className="max-h-[400px] overflow-y-auto space-y-1 border border-slate-200 rounded-xl p-2">
                                        {basisDokBlocks.map((block) => {
                                            if (block.type === 'SECTION_HEADER') {
                                                const sectionServices = (block.children || []).filter(c => c.type === 'SERVICE');
                                                const allSelected = sectionServices.every(c => selectedBlockIds.has(c.id));
                                                const someSelected = sectionServices.some(c => selectedBlockIds.has(c.id));
                                                return (
                                                    <div key={block.id} className="mb-2">
                                                        <div
                                                            className="flex items-center gap-2 px-3 py-2 bg-slate-100 rounded-lg cursor-pointer"
                                                            onClick={() => {
                                                                setSelectedBlockIds(prev => {
                                                                    const next = new Set(prev);
                                                                    if (allSelected) {
                                                                        sectionServices.forEach(c => next.delete(c.id));
                                                                    } else {
                                                                        sectionServices.forEach(c => next.add(c.id));
                                                                    }
                                                                    return next;
                                                                });
                                                            }}
                                                        >
                                                            <div className={cn(
                                                                "w-4 h-4 rounded border flex items-center justify-center flex-shrink-0",
                                                                allSelected ? "bg-rose-600 border-rose-600" : someSelected ? "bg-rose-200 border-rose-400" : "border-slate-300"
                                                            )}>
                                                                {(allSelected || someSelected) && <Check className="w-3 h-3 text-white" />}
                                                            </div>
                                                            <span className="text-sm font-semibold text-slate-700">
                                                                {block.sectionLabel || 'Bauabschnitt'}
                                                            </span>
                                                        </div>
                                                        <div className="ml-6 space-y-1 mt-1">
                                                            {sectionServices.map(service => (
                                                                <TeilrechnungPositionRow
                                                                    key={service.id}
                                                                    block={service}
                                                                    selected={selectedBlockIds.has(service.id)}
                                                                    expanded={expandedBlockIds.has(service.id)}
                                                                    onToggleSelect={() => {
                                                                        setSelectedBlockIds(prev => {
                                                                            const next = new Set(prev);
                                                                            if (next.has(service.id)) next.delete(service.id);
                                                                            else next.add(service.id);
                                                                            return next;
                                                                        });
                                                                    }}
                                                                    onToggleExpand={() => {
                                                                        setExpandedBlockIds(prev => {
                                                                            const next = new Set(prev);
                                                                            if (next.has(service.id)) next.delete(service.id);
                                                                            else next.add(service.id);
                                                                            return next;
                                                                        });
                                                                    }}
                                                                />
                                                            ))}
                                                        </div>
                                                    </div>
                                                );
                                            }
                                            if (block.type === 'SERVICE') {
                                                return (
                                                    <TeilrechnungPositionRow
                                                        key={block.id}
                                                        block={block}
                                                        selected={selectedBlockIds.has(block.id)}
                                                        expanded={expandedBlockIds.has(block.id)}
                                                        onToggleSelect={() => {
                                                            setSelectedBlockIds(prev => {
                                                                const next = new Set(prev);
                                                                if (next.has(block.id)) next.delete(block.id);
                                                                else next.add(block.id);
                                                                return next;
                                                            });
                                                        }}
                                                        onToggleExpand={() => {
                                                            setExpandedBlockIds(prev => {
                                                                const next = new Set(prev);
                                                                if (next.has(block.id)) next.delete(block.id);
                                                                else next.add(block.id);
                                                                return next;
                                                            });
                                                        }}
                                                    />
                                                );
                                            }
                                            return null;
                                        })}
                                    </div>
                                )}
                                {/* Summe der gewählten Positionen */}
                                {getAllServiceBlocks(basisDokBlocks).length > 0 && (
                                    <div className="flex justify-between items-center p-3 bg-rose-50 rounded-lg border border-rose-200">
                                        <span className="text-sm font-medium text-rose-700">
                                            Summe ({selectedBlockIds.size} von {getAllServiceBlocks(basisDokBlocks).length} Positionen)
                                        </span>
                                        <span className="text-base font-bold text-rose-700">
                                            {formatCurrency(
                                                getAllServiceBlocks(basisDokBlocks)
                                                    .filter(b => selectedBlockIds.has(b.id))
                                                    .reduce((sum, b) => sum + (b.quantity || 0) * (b.price || 0), 0)
                                            )}
                                        </span>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowRechnungDialog(false)}>
                            Abbrechen
                        </Button>
                        <Button
                            className="bg-rose-600 text-white hover:bg-rose-700"
                            onClick={handleCreateRechnung}
                            disabled={
                                rechnungLoading ||
                                (rechnungTyp === 'ABSCHLAGSRECHNUNG' && berechneterAbschlagNetto <= 0) ||
                                (rechnungTyp === 'ABSCHLAGSRECHNUNG' && !!abrechnungsverlauf && berechneterAbschlagNetto > abrechnungsverlauf.restbetrag) ||
                                (rechnungTyp === 'SCHLUSSRECHNUNG' && abrechnungsverlauf != null && abrechnungsverlauf.restbetrag <= 0) ||
                                (rechnungTyp === 'TEILRECHNUNG' && selectedBlockIds.size === 0)
                            }
                        >
                            {rechnungLoading ? 'Erstelle...' : 'Rechnung erstellen'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
