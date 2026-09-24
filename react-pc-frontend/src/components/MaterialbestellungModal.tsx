// Restored from feature/en1090-echeck; persistence uses the current purchasing needs API.
import { useEffect, useState } from 'react';
import { Briefcase, Loader2, Package, Plus, Ruler, Search, ShieldCheck, Trash2, X, Scissors } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { DecimalInput } from './ui/decimal-input';
import { Dialog } from './ui/dialog';
import { Select } from './ui/select-custom';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { toSafeResourceUrl } from '../lib/htmlSanitizer';
import { formatDecimalInput } from '../lib/numberInput';
import { ProjektSearchModal } from './ProjektSearchModal';
import { ArtikelSearchModal, type ArtikelSuchErgebnis } from './ArtikelSearchModal';
import { SonderzuschnittPicker, type SonderzuschnittAuswahl } from './SonderzuschnittPicker';
import { einkaufApi } from '../features/einkauf/api';
import type { BedarfResponse, Einheit } from '../features/einkauf/types';
import { neueMaterialPosition as neuePosition, materialPositionAusBedarf, materialPositionAusArtikel, materialbedarfPayload, MATERIAL_EINHEITEN as EINHEITEN, MATERIAL_ZEUGNISSE as ZEUGNIS_OPTIONEN, type MaterialPosition as Position } from '../features/einkauf/materialbedarfAdapter';

interface ProjektRef { id: number; bauvorhaben?: string; auftragsnummer?: string; kunde?: string; excKlasse?: string | null }
const EXC_LABEL: Record<string, string> = { EXC_1: 'EXC 1', EXC_2: 'EXC 2', EXC_3: 'EXC 3', EXC_4: 'EXC 4' };
export interface MaterialbestellungModalProps {
    isOpen: boolean; onClose: () => void; onSuccess?: () => void;
    initialProjekt?: ProjektRef | null; projektSperren?: boolean;
    ausgangsbedarf?: BedarfResponse; ohneProjekt?: boolean;
}
export const MaterialbestellungModal: React.FC<MaterialbestellungModalProps> = ({ isOpen, onClose, onSuccess, initialProjekt, projektSperren = false, ausgangsbedarf, ohneProjekt = false }) => {
    const toast = useToast();
    const istEditModus = !!ausgangsbedarf;
    const [projekt, setProjekt] = useState<ProjektRef | null>(initialProjekt ?? null);
    const [positionen, setPositionen] = useState<Position[]>([neuePosition()]);
    const [projektModalOffen, setProjektModalOffen] = useState(false);
    const [artikelModalFuerZeile, setArtikelModalFuerZeile] = useState<string | null>(null);
    const [artikelMultiModalOffen, setArtikelMultiModalOffen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [fehler, setFehler] = useState('');
    useEffect(() => {
        if (!isOpen) return;
        setProjekt(ohneProjekt ? null : initialProjekt ?? (ausgangsbedarf?.liefergruppe.projektId ? { id: ausgangsbedarf.liefergruppe.projektId, bauvorhaben: `Projekt ${ausgangsbedarf.liefergruppe.projektId}` } : null));
        setPositionen(ausgangsbedarf ? [materialPositionAusBedarf(ausgangsbedarf)] : [neuePosition()]);
        setFehler('');
    }, [isOpen, initialProjekt, ausgangsbedarf, ohneProjekt]);
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
        let payloads: ReturnType<typeof materialbedarfPayload>[];
        try { payloads = positionen.map(pos => materialbedarfPayload(pos, ohneProjekt ? null : projekt?.id ?? null, ausgangsbedarf)); }
        catch (error) { const message = error instanceof Error ? error.message : 'Bitte die Materialangaben prüfen.'; setFehler(message); toast.error(message); return; }
        setSaving(true); setFehler('');
        const gespeichert = new Set<string>();
        try {
            // On partial success, remove saved rows before retrying to prevent duplicate needs.
            for (let index = 0; index < payloads.length; index++) {
                if (ausgangsbedarf) await einkaufApi.put(`/api/einkauf/bedarf/${ausgangsbedarf.id}`, payloads[index]);
                else await einkaufApi.post('/api/einkauf/bedarf', payloads[index]);
                gespeichert.add(positionen[index].clientId);
            }
            toast.success(istEditModus ? 'Bedarf wurde aktualisiert.' : 'Materialbedarf wurde gespeichert.');
            onSuccess?.(); onClose();
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
        <Dialog open={isOpen} onOpenChange={open => { if (!open && !saving) onClose(); }} className="w-[calc(100vw-2rem)] h-[calc(100vh-2rem)] p-0 overflow-hidden" aria-labelledby="materialbest-title">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-gradient-to-r from-rose-50 to-white shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
                            <Package className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 id="materialbest-title" className="text-xl font-bold text-slate-900">
                                {istEditModus ? 'Bedarf bearbeiten' : 'Materialbedarf erfassen'}
                            </h2>
                            <p className="text-sm text-slate-500">
                                Material aus dem Katalog oder frei eintragen. Technische Angaben sind optional.
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 mr-10">
                        <Button variant="ghost" onClick={onClose} disabled={saving}>Abbrechen</Button>
                        <Button
                            onClick={speichern}
                            disabled={saving}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plus className="w-4 h-4 mr-2" />}
                            {saving ? 'Speichern...' : istEditModus ? 'Änderungen speichern' : 'Bedarf speichern'}
                        </Button>

                    </div>
                </div>

                {/* Shared Header-Controls: Projekt + Lieferant (nicht im Batch-Edit-Modus,
                    nicht im Projekt-Detail-Kontext und nicht im Single-Edit-Modus) */}
                {(
                <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 shrink-0">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Projekt */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                Projekt / Baustelle
                            </label>
                            <button
                                type="button"
                                onClick={() => !projektSperren && !ohneProjekt && setProjektModalOffen(true)}
                                disabled={projektSperren || ohneProjekt} title={projektSperren || ohneProjekt ? "Bereich ist durch die Bedarfsliste vorgegeben" : undefined}
                                className={cn(
                                    'w-full flex items-center gap-3 px-3 py-2.5 border rounded-lg text-left transition-colors group',
                                    projekt
                                        ? 'border-rose-300 bg-white hover:border-rose-400'
                                        : 'border-dashed border-slate-300 bg-white hover:border-rose-300 hover:bg-rose-50',
                                    (projektSperren || ohneProjekt) && 'opacity-70 cursor-not-allowed'
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
                                        <span className="text-slate-400">Für Werkstatt / auf Vorrat</span>
                                    )}
                                </div>
                                {!projektSperren && !ohneProjekt && <Search className="w-4 h-4 text-slate-400 group-hover:text-rose-500 flex-shrink-0" />}
                                {projekt && !projektSperren && !ohneProjekt && (
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


                    </div>
                </div>
                )}

                {/* Positionen-Bereich */}
                <div className="flex-1 overflow-auto px-6 py-4">
                    <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">
                            {istEditModus ? 'Position' : <>Positionen <span className="text-slate-400">({positionen.length})</span></>}
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
                                disabled={saving}
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
                {fehler && <p role="alert" className="px-6 pb-4 text-sm text-rose-700">{fehler}</p>}

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

            <ArtikelSearchModal
                isOpen={artikelModalFuerZeile !== null}
                onClose={() => setArtikelModalFuerZeile(null)}
                onSelect={(a) => {
                    if (artikelModalFuerZeile) artikelUebernehmen(artikelModalFuerZeile, a);
                }}
            />

            <ArtikelSearchModal
                isOpen={artikelMultiModalOffen}
                onClose={() => setArtikelMultiModalOffen(false)}
                onSelect={() => { /* nicht genutzt im Multi-Modus */ }}
                onSelectMany={artikelMultiUebernehmen}
                multiSelect
            />
        </Dialog>
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
    /** Zeile ist lesend (z. B. weil bereits exportiert) */
    disabled?: boolean;
}

const PositionRow: React.FC<PositionRowProps> = ({
    index, position, onUpdate, onRemove, onArtikelSuchen, showRemove = true,
    disabled = false,
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
                    {/* Zeile 1: Artikel-Auswahl + Produktname */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-12 md:col-span-5">
                            <label className="block text-xs font-medium text-slate-500 mb-1">
                                Artikel aus Stammdaten {position.artikelId && <span className="text-rose-600">·  verknüpft</span>}
                            </label>
                            <div
                                className={cn(
                                    'w-full flex items-center border rounded-md text-sm transition-colors',
                                    position.artikelId
                                        ? 'border-rose-300 bg-rose-50 text-rose-800 hover:bg-rose-100'
                                        : 'border-slate-300 bg-white text-slate-500 hover:border-rose-300 hover:bg-rose-50'
                                )}
                            >
                              <button type="button" onClick={onArtikelSuchen} className="flex min-w-0 flex-1 items-center gap-2 px-3 py-1.5 text-left rounded-md focus-visible:outline-rose-600">
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
                                        className="mr-1 p-1.5 hover:bg-rose-200 rounded focus-visible:outline-rose-600"
                                        aria-label="Artikel-Verknüpfung entfernen"
                                    >
                                        <X className="w-3 h-3" />
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

                    {/* Zeile 2: Menge | Einheit */}
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

                            </label>
                            <Select
                                aria-label={`Zeugnis Position ${index + 1}`}
                                value={position.zeugnis}
                                onChange={v => onUpdate({ zeugnis: v, zeugnisBestaetigt: false })}
                                options={ZEUGNIS_OPTIONEN}
                            />
                            {position.zeugnis && <label className="mt-2 flex items-start gap-2 text-xs text-slate-600">
                                <input type="checkbox" checked={position.zeugnisBestaetigt}
                                    onChange={event => onUpdate({ zeugnisBestaetigt: event.target.checked })}
                                    className="mt-0.5 rounded border-slate-300 accent-rose-600" />
                                Zeugnisanforderung fachlich geprüft
                            </label>}

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
                schnittForm: '',
                schnittbildBildUrl: null,
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
                schnittForm: '',
                schnittbildBildUrl: null,
                winkelLinks: '',
                winkelRechts: '',
            });
        }
    };

    const handlePickerSubmit = (auswahl: SonderzuschnittAuswahl) => {
        onUpdate({
            sonderzuschnitt: true,
            fixzuschnitt: true,
            schnittForm: auswahl.schnittForm,
            schnittbildBildUrl: auswahl.schnittbildBildUrl,
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
                    {position.schnittForm ? (
                        <>
                            <div className="flex items-center gap-3 flex-1 min-w-0">
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
                                    aria-label="Sonderzuschnitt entfernen"
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
                    position.schnittForm
                        ? {
                              schnittForm: position.schnittForm,
                              schnittbildBildUrl: position.schnittbildBildUrl ?? undefined,
                              anschnittWinkelLinks: position.winkelLinks ? Number(position.winkelLinks.replace(',', '.')) : undefined,
                              anschnittWinkelRechts: position.winkelRechts ? Number(position.winkelRechts.replace(',', '.')) : undefined,
                          }
                        : null
                }
            />
        </div>
    );
};
