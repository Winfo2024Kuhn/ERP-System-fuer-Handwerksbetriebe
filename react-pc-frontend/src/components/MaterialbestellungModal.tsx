import { useEffect, useState } from 'react';
import {
    Briefcase,
    Loader2,
    Package,
    Plus,
    Ruler,
    Search,
    ShieldCheck,
    Trash2,
    Truck,
    X,
} from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { DecimalInput } from './ui/decimal-input';
import { formatDecimalInput } from '../lib/numberInput';
import type { BedarfResponse, Einheit } from '../features/einkauf/types';
import { nutztEchtesBackend } from '../features/einkauf/originalBedarfApi';
import { neueOriginalPosition as neuePosition, originalEinheit, originalZeugnis, originalBeschaffungsdetails, originalPositionAusBedarf, originalMaterialPayload, speichereOriginalMaterial, type OriginalMaterialPosition as Position } from '../features/einkauf/originalMaterialApi';
import { materialPositionAusArtikel, MATERIAL_EINHEITEN as EINHEITEN, MATERIAL_ZEUGNISSE as ZEUGNIS_OPTIONEN } from '../features/einkauf/materialbedarfAdapter';
import { Select } from './ui/select-custom';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { toSafeResourceUrl } from '../lib/htmlSanitizer';
import { ProjektSearchModal } from './ProjektSearchModal';
import { LieferantSearchModal, type LieferantSuchErgebnis } from './LieferantSearchModal';
import { ArtikelSearchModal, type ArtikelSuchErgebnis } from './ArtikelSearchModal';
import { SonderzuschnittPicker, type SonderzuschnittAuswahl } from './SonderzuschnittPicker';
import { Scissors } from 'lucide-react';

// ========= Shared Types =========
interface ProjektRef {
    id: number;
    bauvorhaben?: string;
    auftragsnummer?: string;
    kunde?: string;
    excKlasse?: string | null;
}

const EXC_LABEL: Record<string, string> = {
    EXC_1: 'EXC 1', EXC_2: 'EXC 2', EXC_3: 'EXC 3', EXC_4: 'EXC 4',
};

// ========= Props =========
/**
 * Minimales Interface für eine zu bearbeitende Bestellposition.
 * Deckt die Felder ab, die in der Oberfläche editiert werden können.
 */
export interface EditPosition {
    id: number;
    bedarf?: BedarfResponse;
    version?: number;
    schnittForm?: string | null;
    schnittAchseId?: number | null;
    artikelId?: number | null;
    externeArtikelnummer?: string | null;
    produktname?: string | null;
    produkttext?: string | null;
    werkstoffName?: string | null;
    kategorieId?: number | null;
    menge?: number | string | null;
    einheit?: string | null;
    fixmassMm?: number | null;
    schnittbildId?: number | null;
    schnittbildBildUrl?: string | null;
    schnittAchseBildUrl?: string | null;
    anschnittWinkelLinks?: number | string | null;
    anschnittWinkelRechts?: number | string | null;
    zeugnisAnforderung?: string | null;
    kommentar?: string | null;
    projektId?: number | null;
    projektName?: string | null;
    projektNummer?: string | null;
    kundenName?: string | null;
    excKlasse?: string | null;
    lieferantId?: number | null;
    lieferantName?: string | null;
    exportiertAm?: string | null;
}

export interface MaterialbestellungModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess?: () => void;
    /** Projekt kann vorab fix gewählt werden (z. B. aus Projekt-Editor heraus) */
    initialProjekt?: ProjektRef | null;
    /** Projekt-Auswahl deaktivieren (wenn aus Projekt-Kontext aufgerufen) */
    projektSperren?: boolean;
    /** Wenn gesetzt: Edit-Modus für genau diese eine Position (PUT statt POST) */
    editPosition?: EditPosition | null;
    /** Wenn gesetzt: Batch-Edit-Modus — mehrere Positionen gleichzeitig bearbeiten (PUT pro Position) */
    editPositions?: EditPosition[] | null;
    /** Optionaler Titel für den Batch-Edit-Modus (z. B. Lieferantenname) */
    batchTitle?: string;
}

// ========= Hauptkomponente =========
export const MaterialbestellungModal: React.FC<MaterialbestellungModalProps> = ({
    isOpen,
    onClose,
    onSuccess,
    initialProjekt,
    projektSperren = false,
    editPosition = null,
    editPositions = null,
    batchTitle,
}) => {
    const toast = useToast();
    const istBatchEditModus = editPositions != null && editPositions.length > 0;
    const istEditModus = editPosition != null || istBatchEditModus;
    // Im Bedarfs-Workflow (Projekt-Detail-Page, Single-Edit) sind Projekt + Lieferant
    // entweder durch den Aufruf-Kontext fix oder gehören erst zur späteren Bestellung.
    // Dann blenden wir die beiden Header-Felder aus und Lieferant ist optional.
    const headerKontextVerstecken = projektSperren || (editPosition != null && !istBatchEditModus);

    // Stammdaten

    // Gemeinsame Auswahl
    const [projekt, setProjekt] = useState<ProjektRef | null>(initialProjekt ?? null);
    const [lieferant, setLieferant] = useState<LieferantSuchErgebnis | null>(null);

    // Positionen
    const [positionen, setPositionen] = useState<Position[]>([neuePosition()]);

    // Modal-Zustände
    const [projektModalOffen, setProjektModalOffen] = useState(false);
    const [lieferantModalOffen, setLieferantModalOffen] = useState(false);
    const [artikelModalFuerZeile, setArtikelModalFuerZeile] = useState<string | null>(null);
    const [artikelMultiModalOffen, setArtikelMultiModalOffen] = useState(false);

    const [saving, setSaving] = useState(false);
    const [fehler, setFehler] = useState('');

    // Reset beim Öffnen
    useEffect(() => {
        if (!isOpen) return;
        setFehler('');


        if (istBatchEditModus && editPositions) {
            // Projekt/Lieferant-Header im Batch-Modus ungenutzt — Kontext ist per-Position
            setProjekt(null);
            setLieferant(null);
            setPositionen(editPositions.map(ep => ({
                ...neuePosition(),
                originalId: ep.id,
                artikelId: ep.artikelId ?? null,
                externeArtikelnummer: ep.externeArtikelnummer ?? undefined,
                produktname: ep.produktname ?? '',
                produkttext: ep.produkttext ?? '',
                werkstoffName: ep.werkstoffName ?? undefined,
                kategorieId: ep.kategorieId ?? null,
                menge: ep.menge != null ? String(ep.menge).replace('.', ',') : '',
                einheit: originalEinheit(ep.einheit),
                fixzuschnitt: ep.fixmassMm != null,
                sonderzuschnitt: ep.schnittbildId != null,
                fixmassMm: ep.fixmassMm != null ? formatDecimalInput(ep.fixmassMm) : '',
                schnittbildId: ep.schnittbildId ?? null,
                schnittAchseId: ep.schnittAchseId ?? null,
                schnittForm: ep.schnittForm ?? '',
                schnittbildBildUrl: ep.schnittbildBildUrl ?? null,
                schnittAchseBildUrl: ep.schnittAchseBildUrl ?? null,
                winkelLinks: ep.anschnittWinkelLinks != null ? String(ep.anschnittWinkelLinks).replace('°', '').replace('.', ',') : '',
                winkelRechts: ep.anschnittWinkelRechts != null ? String(ep.anschnittWinkelRechts).replace('°', '').replace('.', ',') : '',
                zeugnis: originalZeugnis(ep.zeugnisAnforderung),
                kommentar: ep.kommentar ?? '',
                ...(ep.bedarf ? originalPositionAusBedarf(ep.bedarf) : {}),
                bedarf: ep.bedarf,
                perProjektId: ep.projektId ?? ep.bedarf?.liefergruppe.projektId ?? null,
                perProjektName: ep.projektName ?? null,
                perProjektNummer: ep.projektNummer ?? null,
                perKundenName: ep.kundenName ?? null,
                perExcKlasse: ep.excKlasse ?? null,
                perLieferantId: ep.lieferantId ?? originalBeschaffungsdetails(ep.bedarf)?.lieferantId ?? null,
                perLieferantName: ep.lieferantName ?? null,
                exportiertAm: ep.exportiertAm ?? null,
            })));
        } else if (editPosition) {
            const projektId = editPosition.projektId ?? editPosition.bedarf?.liefergruppe.projektId;
            const lieferantId = editPosition.lieferantId ?? originalBeschaffungsdetails(editPosition.bedarf)?.lieferantId;
            setProjekt(projektId ? {
                id: projektId,
                bauvorhaben: editPosition.projektName ?? undefined,
                auftragsnummer: editPosition.projektNummer ?? undefined,
                kunde: editPosition.kundenName ?? undefined,
                excKlasse: editPosition.excKlasse ?? null,
            } : null);
            setLieferant(lieferantId ? {
                id: lieferantId,
                lieferantenname: editPosition.lieferantName ?? '',
            } as LieferantSuchErgebnis : null);
            setPositionen([{
                ...neuePosition(),
                originalId: editPosition.id,
                artikelId: editPosition.artikelId ?? null,
                externeArtikelnummer: editPosition.externeArtikelnummer ?? undefined,
                produktname: editPosition.produktname ?? '',
                produkttext: editPosition.produkttext ?? '',
                werkstoffName: editPosition.werkstoffName ?? undefined,
                kategorieId: editPosition.kategorieId ?? null,
                menge: editPosition.menge != null ? String(editPosition.menge).replace('.', ',') : '',
                einheit: originalEinheit(editPosition.einheit),
                fixzuschnitt: editPosition.fixmassMm != null,
                sonderzuschnitt: editPosition.schnittbildId != null,
                fixmassMm: editPosition.fixmassMm != null ? formatDecimalInput(editPosition.fixmassMm) : '',
                schnittbildId: editPosition.schnittbildId ?? null,
                schnittAchseId: editPosition.schnittAchseId ?? null,
                schnittForm: editPosition.schnittForm ?? '',
                schnittbildBildUrl: editPosition.schnittbildBildUrl ?? null,
                schnittAchseBildUrl: editPosition.schnittAchseBildUrl ?? null,
                winkelLinks: editPosition.anschnittWinkelLinks != null ? String(editPosition.anschnittWinkelLinks).replace('°', '').replace('.', ',') : '',
                winkelRechts: editPosition.anschnittWinkelRechts != null ? String(editPosition.anschnittWinkelRechts).replace('°', '').replace('.', ',') : '',
                zeugnis: originalZeugnis(editPosition.zeugnisAnforderung),
                kommentar: editPosition.kommentar ?? '',
                ...(editPosition.bedarf ? originalPositionAusBedarf(editPosition.bedarf) : {}),
                bedarf: editPosition.bedarf,
            }]);
        } else {
            setProjekt(initialProjekt ?? null);
            setLieferant(null);
            setPositionen([neuePosition()]);
        }
    }, [isOpen, initialProjekt, editPosition, editPositions, istBatchEditModus]);

    // Handlers
    const addLeerePosition = () => setPositionen(prev => [...prev, neuePosition()]);

    const entfernePosition = (clientId: string) => {
        setPositionen(prev => prev.length <= 1 ? [neuePosition()] : prev.filter(p => p.clientId !== clientId));
    };

    const updatePosition = (clientId: string, patch: Partial<Position>) => {
        setPositionen(prev => prev.map(p => p.clientId === clientId ? { ...p, ...patch } : p));
    };

    const artikelUebernehmen = (clientId: string, a: ArtikelSuchErgebnis) => {
        updatePosition(clientId, materialPositionAusArtikel(a));
    };

    const artikelMultiUebernehmen = (ausgewaehlt: ArtikelSuchErgebnis[]) => {
        if (ausgewaehlt.length === 0) return;
        setPositionen(prev => {
            // Leere erste Zeile überschreiben, wenn noch nichts drin
            const first = prev[0];
            const istErsteLeer = first && !first.artikelId && !first.produktname.trim();
            const rest = istErsteLeer ? prev.slice(1) : prev;
            const neu: Position[] = ausgewaehlt.map(a => ({ ...neuePosition(), ...materialPositionAusArtikel(a) }));
            return [...rest, ...neu];
        });
    };

    const speichern = async () => {
        const offen = positionen.filter(pos => !pos.exportiertAm);
        let payloads: ReturnType<typeof originalMaterialPayload>[];
        try {
            if (!offen.length) throw new Error('Keine bearbeitbaren Positionen vorhanden.');
            payloads = offen.map(pos => originalMaterialPayload(pos,
                istBatchEditModus ? pos.perProjektId ?? null : projekt?.id ?? null,
                istBatchEditModus ? pos.perLieferantId ?? null : lieferant?.id ?? null));
        } catch (error) { const message = error instanceof Error ? error.message : 'Bitte alle Positionen prüfen.'; setFehler(message); toast.error(message); return; }
        setSaving(true); setFehler('');
        const gespeichert = new Set<string>();
        try {
            for (let index = 0; index < offen.length; index++) {
                await speichereOriginalMaterial(offen[index], payloads[index]);
                gespeichert.add(offen[index].clientId);
            }
            toast.success('Materialbedarf gespeichert.'); onSuccess?.(); onClose();
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Materialbedarf konnte nicht gespeichert werden.';
            setFehler(message); toast.error(message);
            if (gespeichert.size) setPositionen(vorher => vorher.filter(pos => !gespeichert.has(pos.clientId)));
        } finally { setSaving(false); }
    };

    if (!isOpen) return null;

    const excBadge = projekt?.excKlasse
        ? <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-medium">
            <ShieldCheck className="w-3 h-3" />{EXC_LABEL[projekt.excKlasse] ?? projekt.excKlasse}
        </span>
        : null;

    return (
        <>
            <div className="fixed inset-4 z-50 bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-slate-200"
                role="dialog" aria-modal="true" aria-labelledby="materialbest-title">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-gradient-to-r from-rose-50 to-white shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
                            <Package className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 id="materialbest-title" className="text-xl font-bold text-slate-900">
                                {istBatchEditModus
                                    ? (batchTitle ? `${batchTitle} – Positionen bearbeiten` : 'Positionen bearbeiten')
                                    : istEditModus ? 'Bestellposition bearbeiten' : 'Materialbestellung'}
                            </h2>
                            <p className="text-sm text-slate-500">
                                {istBatchEditModus
                                    ? `${positionen.length} Position${positionen.length === 1 ? '' : 'en'} — Änderungen pro Position speichern`
                                    : istEditModus
                                        ? 'Änderungen werden gespeichert, solange die Position nicht exportiert wurde.'
                                        : 'Mehrere Positionen für einen Lieferanten erfassen'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={saving}>Abbrechen</Button>
                        <Button
                            onClick={speichern}
                            disabled={saving || (!nutztEchtesBackend && !istBatchEditModus && !headerKontextVerstecken && !lieferant)}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plus className="w-4 h-4 mr-2" />}
                            {saving
                                ? 'Speichern...'
                                : istBatchEditModus
                                    ? 'Änderungen speichern'
                                    : istEditModus ? 'Änderungen speichern' : 'Alle speichern'}
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}>
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Shared Header-Controls: Projekt + Lieferant (nicht im Batch-Edit-Modus,
                    nicht im Projekt-Detail-Kontext und nicht im Single-Edit-Modus) */}
                {!istBatchEditModus && !headerKontextVerstecken && (
                <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 shrink-0">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Projekt */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                Projekt / Baustelle
                            </label>
                            <button
                                type="button"
                                onClick={() => !projektSperren && setProjektModalOffen(true)}
                                disabled={projektSperren}
                                className={cn(
                                    'w-full flex items-center gap-3 px-3 py-2.5 border rounded-lg text-left transition-colors group',
                                    projekt
                                        ? 'border-rose-300 bg-white hover:border-rose-400'
                                        : 'border-dashed border-slate-300 bg-white hover:border-rose-300 hover:bg-rose-50',
                                    projektSperren && 'opacity-70 cursor-not-allowed'
                                )}
                            >
                                <Briefcase className={cn('w-5 h-5 flex-shrink-0', projekt ? 'text-rose-600' : 'text-slate-400')} />
                                <div className="flex-1 min-w-0">
                                    {projekt ? (
                                        <>
                                            <p className="font-medium text-slate-900 truncate">
                                                {projekt.bauvorhaben || 'Projekt'}
                                            </p>
                                            <div className="flex items-center gap-2 text-xs text-slate-500 mt-0.5">
                                                {projekt.auftragsnummer && (
                                                    <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded">
                                                        {projekt.auftragsnummer}
                                                    </span>
                                                )}
                                                {projekt.kunde && <span className="truncate">{projekt.kunde}</span>}
                                                {excBadge}
                                            </div>
                                        </>
                                    ) : (
                                        <span className="text-slate-400">— Kein Projekt (optional) —</span>
                                    )}
                                </div>
                                {!projektSperren && <Search className="w-4 h-4 text-slate-400 group-hover:text-rose-500 flex-shrink-0" />}
                                {projekt && !projektSperren && (
                                    <span
                                        role="button"
                                        tabIndex={0}
                                        onClick={e => { e.stopPropagation(); setProjekt(null); }}
                                        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.stopPropagation(); setProjekt(null); } }}
                                        className="p-1 hover:bg-slate-100 rounded text-slate-400 hover:text-slate-700 cursor-pointer"
                                        aria-label="Projekt entfernen"
                                    >
                                        <X className="w-4 h-4" />
                                    </span>
                                )}
                            </button>
                        </div>

                        {/* Lieferant */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                Lieferant *
                            </label>
                            <button
                                type="button"
                                onClick={() => setLieferantModalOffen(true)}
                                className={cn(
                                    'w-full flex items-center gap-3 px-3 py-2.5 border rounded-lg text-left transition-colors group',
                                    lieferant
                                        ? 'border-rose-300 bg-white hover:border-rose-400'
                                        : 'border-dashed border-amber-300 bg-amber-50/50 hover:border-rose-300 hover:bg-rose-50'
                                )}
                            >
                                <Truck className={cn('w-5 h-5 flex-shrink-0', lieferant ? 'text-rose-600' : 'text-amber-500')} />
                                <div className="flex-1 min-w-0">
                                    {lieferant ? (
                                        <>
                                            <p className="font-medium text-slate-900 truncate">{lieferant.lieferantenname}</p>
                                            <div className="flex items-center gap-2 text-xs text-slate-500 mt-0.5">
                                                {lieferant.lieferantenTyp && <span>{lieferant.lieferantenTyp}</span>}
                                                {(lieferant.plz || lieferant.ort) && (
                                                    <span className="truncate">
                                                        {[lieferant.plz, lieferant.ort].filter(Boolean).join(' ')}
                                                    </span>
                                                )}
                                            </div>
                                        </>
                                    ) : (
                                        <span className="text-amber-700 font-medium">Lieferant auswählen →</span>
                                    )}
                                </div>
                                <Search className="w-4 h-4 text-slate-400 group-hover:text-rose-500 flex-shrink-0" />
                            </button>
                        </div>
                    </div>
                </div>
                )}

                {fehler && <p role="alert" className="px-6 pt-3 text-sm text-rose-700">{fehler}</p>}
                {/* Positionen-Bereich */}
                <div className="flex-1 overflow-auto px-6 py-4">
                    <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">
                            {istBatchEditModus
                                ? <>Positionen <span className="text-slate-400">({positionen.length})</span></>
                                : istEditModus ? 'Position' : <>Positionen <span className="text-slate-400">({positionen.length})</span></>}
                        </h3>
                        {!istEditModus && (
                            <div className="flex items-center gap-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setArtikelMultiModalOffen(true)}
                                    className="border-rose-200 text-rose-700 hover:bg-rose-50"
                                >
                                    <Package className="w-4 h-4 mr-1" />
                                    Artikel aus Stammdaten (Mehrfachauswahl)
                                </Button>
                                <Button variant="outline" size="sm" onClick={addLeerePosition}>
                                    <Plus className="w-4 h-4 mr-1" />
                                    Leere Zeile
                                </Button>
                            </div>
                        )}
                    </div>

                    <div className="space-y-3">
                        {positionen.map((pos, idx) => (
                            <PositionRow
                                key={pos.clientId}
                                index={idx}
                                position={pos}
                                onUpdate={patch => updatePosition(pos.clientId, patch)}
                                onRemove={() => entfernePosition(pos.clientId)}
                                onArtikelSuchen={() => setArtikelModalFuerZeile(pos.clientId)}
                                showRemove={!istEditModus}
                                showKontext={istBatchEditModus}
                                disabled={saving || (istBatchEditModus && pos.exportiertAm != null)}
                            />
                        ))}
                    </div>

                    {!istEditModus && (
                        <div className="mt-4 flex justify-center">
                            <Button variant="outline" onClick={addLeerePosition} className="border-dashed">
                                <Plus className="w-4 h-4 mr-2" />
                                Weitere Position hinzufügen
                            </Button>
                        </div>
                    )}
                </div>
            </div>

            {/* Sub-Modals */}
            <ProjektSearchModal
                isOpen={projektModalOffen}
                onClose={() => setProjektModalOffen(false)}
                onSelect={(p) => setProjekt({
                    id: p.id,
                    bauvorhaben: p.bauvorhaben,
                    auftragsnummer: p.auftragsnummer,
                    kunde: p.kunde,
                    excKlasse: (p as ProjektRef).excKlasse ?? null,
                })}
                nurOffene
            />

            <LieferantSearchModal
                isOpen={lieferantModalOffen}
                onClose={() => setLieferantModalOffen(false)}
                onSelect={setLieferant}
                currentLieferantId={lieferant?.id}
            />

            <ArtikelSearchModal
                isOpen={artikelModalFuerZeile !== null}
                onClose={() => setArtikelModalFuerZeile(null)}
                onSelect={(a) => {
                    if (artikelModalFuerZeile) artikelUebernehmen(artikelModalFuerZeile, a);
                }}
                lieferantName={lieferant?.lieferantenname}
            />

            <ArtikelSearchModal
                isOpen={artikelMultiModalOffen}
                onClose={() => setArtikelMultiModalOffen(false)}
                onSelect={() => { /* nicht genutzt im Multi-Modus */ }}
                onSelectMany={artikelMultiUebernehmen}
                multiSelect
                lieferantName={lieferant?.lieferantenname}
            />
        </>
    );
};

// ========= Position-Zeile =========
interface PositionRowProps {
    index: number;
    position: Position;
    onUpdate: (patch: Partial<Position>) => void;
    onRemove: () => void;
    onArtikelSuchen: () => void;
    showRemove?: boolean;
    /** Zeigt Projekt/Lieferant-Kontext-Badges oben in der Zeile (Batch-Edit) */
    showKontext?: boolean;
    /** Zeile ist lesend (z. B. weil bereits exportiert) */
    disabled?: boolean;
}

const PositionRow: React.FC<PositionRowProps> = ({
    index, position, onUpdate, onRemove, onArtikelSuchen, showRemove = true,
    showKontext = false, disabled = false,
}) => {
    return (
        <div className={cn(
            "bg-white border border-slate-200 rounded-xl p-4 transition-colors",
            disabled ? "opacity-60 bg-slate-50" : "hover:border-slate-300"
        )}>
            <div className="flex gap-4">
                {/* Nummerierung */}
                <div className="flex-shrink-0">
                    <div className={cn(
                        "w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold",
                        disabled ? "bg-slate-200 text-slate-500" : "bg-rose-100 text-rose-700"
                    )}>
                        {index + 1}
                    </div>
                </div>

                {/* Eingabefelder */}
                <fieldset className="flex-1 space-y-3 min-w-0" disabled={disabled}>
                    {showKontext && (
                        <div className="flex flex-wrap items-center gap-2 pb-2 border-b border-slate-100">
                            {position.perProjektName || position.perProjektNummer ? (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-xs">
                                    <Briefcase className="w-3 h-3" />
                                    {position.perProjektNummer && (
                                        <span className="font-mono">{position.perProjektNummer}</span>
                                    )}
                                    {position.perProjektName && <span>· {position.perProjektName}</span>}
                                </span>
                            ) : (
                                <span className="text-xs text-slate-400 italic">Ohne Projekt</span>
                            )}
                            {position.perLieferantName && (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-xs">
                                    <Truck className="w-3 h-3" />
                                    {position.perLieferantName}
                                </span>
                            )}
                            {position.perExcKlasse && (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-medium">
                                    <ShieldCheck className="w-3 h-3" />
                                    {EXC_LABEL[position.perExcKlasse] ?? position.perExcKlasse}
                                </span>
                            )}
                            {disabled && (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-medium">
                                    Bereits exportiert — Bearbeitung gesperrt
                                </span>
                            )}
                        </div>
                    )}
                    {/* Zeile 1: Artikel-Auswahl + Produktname */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-12 md:col-span-5">
                            <label className="block text-xs font-medium text-slate-500 mb-1">
                                Artikel aus Stammdaten {position.artikelId && <span className="text-rose-600">·  verknüpft</span>}
                            </label>
                            {/* Entfernen-X als eigener Knopf neben dem Suchfeld: ein Knopf im Knopf ist per Tastatur und Screenreader nicht erreichbar. */}
                            <div className="flex items-center gap-1">
                                <button
                                    type="button"
                                    onClick={onArtikelSuchen}
                                    className={cn(
                                        'flex-1 min-w-0 flex items-center gap-2 px-3 py-1.5 border rounded-md text-sm text-left transition-colors',
                                        position.artikelId
                                            ? 'border-rose-300 bg-rose-50 text-rose-800 hover:bg-rose-100'
                                            : 'border-slate-300 bg-white text-slate-500 hover:border-rose-300 hover:bg-rose-50'
                                    )}
                                >
                                    <Search className="w-4 h-4 flex-shrink-0" />
                                    <span className="flex-1 truncate">
                                        {position.artikelId
                                            ? `#${position.externeArtikelnummer || position.artikelId}`
                                            : 'Artikel suchen...'}
                                    </span>
                                </button>
                                {position.artikelId && (
                                    <button
                                        type="button"
                                        onClick={() => onUpdate({ artikelId: null, externeArtikelnummer: undefined })}
                                        className="p-1.5 rounded-md text-rose-700 hover:bg-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        aria-label="Artikel-Verknüpfung entfernen"
                                        title="Artikel-Verknüpfung entfernen"
                                    >
                                        <X className="w-4 h-4" />
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="col-span-12 md:col-span-7">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Produktname *</label>
                            <Input
                                aria-label={`Produktname Position ${index + 1}`}
                                value={position.produktname}
                                onChange={e => onUpdate({ produktname: e.target.value })}
                                placeholder="z. B. IPE 200, S235"
                            />
                            {position.werkstoffName && (
                                <p className="text-xs text-slate-500 mt-1">
                                    Werkstoff: <span className="font-medium">{position.werkstoffName}</span>
                                </p>
                            )}
                        </div>
                    </div>

                    {/* Zeile 2: Menge | Einheit | Kategorie */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-6 md:col-span-3">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Menge *</label>
                            <DecimalInput
                                aria-label={`Menge Position ${index + 1}`}
                                value={position.menge}
                                onChange={value => onUpdate({ menge: value })}
                                placeholder="1"

                            />
                        </div>
                        <div className="col-span-6 md:col-span-3">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Einheit</label>
                            <Select
                                aria-label={`Einheit Position ${index + 1}`}
                                value={position.einheit}
                                onChange={v => onUpdate({ einheit: v as Einheit })}
                                options={EINHEITEN}
                            />
                        </div>
                    </div>

                    {/* Zeile 2b: Zuschnitt — Checkboxen + abhängige Felder */}
                    <ZuschnittBlock position={position} onUpdate={onUpdate} disabled={disabled} />

                    {/* Zeile 3: Zeugnis + Produkttext + Kommentar */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-12 md:col-span-4">
                            <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                Zeugnis (EN 1090)
                                {position.zeugnisVomSystem && position.zeugnis === position.zeugnisVomSystem && (
                                    <span className="text-rose-600 font-normal ml-1">
                                        <ShieldCheck className="w-3 h-3 inline" /> Norm
                                    </span>
                                )}
                            </label>
                            <Select
                                aria-label={`Zeugnis Position ${index + 1}`}
                                value={position.zeugnis}
                                onChange={v => onUpdate({ zeugnis: v, zeugnisBestaetigt: false })}
                                options={ZEUGNIS_OPTIONEN}
                            />
                            {position.zeugnis && <label className="mt-2 flex items-start gap-2 text-xs text-slate-600"><input type="checkbox" checked={position.zeugnisBestaetigt} onChange={event => onUpdate({ zeugnisBestaetigt: event.target.checked })} className="mt-0.5 rounded accent-rose-600" />Zeugnisanforderung fachlich geprüft</label>}

                        </div>
                        <div className="col-span-12 md:col-span-4">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Produktbeschreibung</label>
                            <Input
                                value={position.produkttext}
                                onChange={e => onUpdate({ produkttext: e.target.value })}
                                placeholder="Optional: Norm, Maße..."
                            />
                        </div>
                        <div className="col-span-12 md:col-span-4">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Kommentar</label>
                            <Input
                                value={position.kommentar}
                                onChange={e => onUpdate({ kommentar: e.target.value })}
                                placeholder="z. B. Lieferung KW 22"
                            />
                        </div>
                    </div>
                </fieldset>

                {/* Löschen */}
                {showRemove && (
                    <div className="flex-shrink-0">
                        <button
                            type="button"
                            onClick={onRemove}
                            className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                            title="Position entfernen"
                            aria-label={`Position ${index + 1} entfernen`}
                        >
                            <Trash2 className="w-5 h-5" />
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

// ========= Zuschnitt-Block (Fixzuschnitt + Sonderzuschnitt) =========
interface ZuschnittBlockProps {
    position: Position;
    onUpdate: (patch: Partial<Position>) => void;
    disabled?: boolean;
}

const ZuschnittBlock: React.FC<ZuschnittBlockProps> = ({ position, onUpdate, disabled = false }) => {
    const [pickerOffen, setPickerOffen] = useState(false);

    const toggleFixzuschnitt = (checked: boolean) => {
        if (checked) {
            onUpdate({ fixzuschnitt: true });
        } else {
            // Fixzuschnitt aus → Sonderzuschnitt zwangsweise auch aus + Felder zurücksetzen
            onUpdate({
                fixzuschnitt: false,
                fixmassMm: '',
                sonderzuschnitt: false,
                schnittbildId: null,
                schnittForm: '',
                schnittbildBildUrl: null,
                schnittAchseId: null,
                schnittAchseBildUrl: null,
                winkelLinks: '',
                winkelRechts: '',
            });
        }
    };

    const toggleSonderzuschnitt = (checked: boolean) => {
        if (checked) {
            // Sonderzuschnitt aktivieren → Fixzuschnitt zwangsläufig mit an, Picker öffnen
            onUpdate({ fixzuschnitt: true, sonderzuschnitt: true });
            setPickerOffen(true);
        } else {
            onUpdate({
                sonderzuschnitt: false,
                schnittbildId: null,
                schnittForm: '',
                schnittbildBildUrl: null,
                schnittAchseId: null,
                schnittAchseBildUrl: null,
                winkelLinks: '',
                winkelRechts: '',
            });
        }
    };

    const handlePickerSubmit = (auswahl: SonderzuschnittAuswahl) => {
        onUpdate({
            sonderzuschnitt: true,
            fixzuschnitt: true,
            schnittbildId: auswahl.schnittbildId,
            schnittForm: auswahl.schnittForm ?? '',
            schnittbildBildUrl: auswahl.schnittbildBildUrl,
            schnittAchseId: auswahl.schnittAchseId,
            schnittAchseBildUrl: auswahl.schnittAchseBildUrl,
            winkelLinks: formatDecimalInput(auswahl.anschnittWinkelLinks),
            winkelRechts: formatDecimalInput(auswahl.anschnittWinkelRechts),
        });
    };

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-4">
                <label className="inline-flex items-center gap-2 cursor-pointer select-none">
                    <input
                        type="checkbox"
                        checked={position.fixzuschnitt}
                        onChange={(e) => toggleFixzuschnitt(e.target.checked)}
                        disabled={disabled}
                        className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 cursor-pointer"
                    />
                    <span className="text-sm font-medium text-slate-700 inline-flex items-center gap-1">
                        <Ruler className="w-3.5 h-3.5 text-slate-500" />
                        Fixzuschnitt
                    </span>
                </label>

                <label
                    className={cn(
                        'inline-flex items-center gap-2 select-none',
                        disabled ? 'cursor-not-allowed' : 'cursor-pointer',
                    )}
                >
                    <input
                        type="checkbox"
                        checked={position.sonderzuschnitt}
                        onChange={(e) => toggleSonderzuschnitt(e.target.checked)}
                        disabled={disabled}
                        className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 cursor-pointer"
                    />
                    <span className="text-sm font-medium text-slate-700 inline-flex items-center gap-1">
                        <Scissors className="w-3.5 h-3.5 text-slate-500" />
                        Sonderzuschnitt
                        <span className="text-xs text-slate-400 font-normal ml-1">
                            (nicht 90° – Gehrung/Winkel)
                        </span>
                    </span>
                </label>
            </div>

            {/* Fixmaß-Input (nur wenn Fixzuschnitt aktiv) */}
            {position.fixzuschnitt && (
                <div className="grid grid-cols-12 gap-3">
                    <div className="col-span-12 md:col-span-4">
                        <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                            <Ruler className="w-3 h-3" />
                            Fixmaß pro Stück (mm) *
                        </label>
                        <DecimalInput
                            aria-label="Fixmaß pro Stück (mm)"
                            value={position.fixmassMm}
                            onChange={value => onUpdate({ fixmassMm: value })}
                            placeholder="z. B. 6000"

                            disabled={disabled}
                        />
                    </div>
                </div>
            )}

            {/* Sonderzuschnitt-Vorschau */}
            {position.sonderzuschnitt && (
                <div className="border border-rose-200 rounded-xl p-3 bg-rose-50/40 flex items-center gap-4">
                    {position.schnittbildId != null ? (
                        <>
                            <div className="flex items-center gap-3 flex-1 min-w-0">
                                {position.schnittAchseBildUrl && (
                                    <img
                                        src={toSafeResourceUrl(position.schnittAchseBildUrl) ?? undefined}
                                        alt="Achse"
                                        className="w-6 h-6 object-contain bg-white border border-rose-200 rounded-lg"
                                    />
                                )}
                                {position.schnittbildBildUrl && (
                                    <img
                                        src={toSafeResourceUrl(position.schnittbildBildUrl) ?? undefined}
                                        alt="Schnittbild"
                                        className="w-6 h-6 object-contain bg-white border border-rose-200 rounded-lg"
                                    />
                                )}
                                <div className="text-sm">
                                    <p className="font-medium text-slate-900">
                                        {position.winkelLinks || '90'}° ·· {position.winkelRechts || '90'}°
                                    </p>
                                    <p className="text-xs text-slate-500">
                                        Winkel links / rechts
                                    </p>
                                </div>
                            </div>
                            <div className="flex gap-2 flex-shrink-0">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPickerOffen(true)}
                                    disabled={disabled}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    Anpassen
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => toggleSonderzuschnitt(false)}
                                    disabled={disabled}
                                    className="text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                                >
                                    <X className="w-4 h-4" />
                                </Button>
                            </div>
                        </>
                    ) : (
                        <div className="flex items-center justify-between gap-3 w-full">
                            <p className="text-sm text-slate-600">
                                Schnittbild und Winkel noch nicht gewählt.
                            </p>
                            <Button
                                size="sm"
                                onClick={() => setPickerOffen(true)}
                                disabled={disabled}
                                className="bg-rose-600 text-white hover:bg-rose-700"
                            >
                                Schnittbild wählen
                            </Button>
                        </div>
                    )}
                </div>
            )}

            <SonderzuschnittPicker
                isOpen={pickerOffen}
                onClose={() => setPickerOffen(false)}
                onSubmit={handlePickerSubmit}
                kategorieId={position.kategorieId}
                artikelId={position.artikelId}
                initial={
                    position.schnittbildId != null
                        ? {
                              schnittbildId: position.schnittbildId,
                              schnittbildBildUrl: position.schnittbildBildUrl ?? undefined,
                              schnittAchseId: position.schnittAchseId ?? undefined,
                              schnittAchseBildUrl: position.schnittAchseBildUrl ?? undefined,
                              anschnittWinkelLinks: position.winkelLinks ? Number(position.winkelLinks.replace(',', '.')) : undefined,
                              anschnittWinkelRechts: position.winkelRechts ? Number(position.winkelRechts.replace(',', '.')) : undefined,
                          }
                        : null
                }
            />
        </div>
    );
};
