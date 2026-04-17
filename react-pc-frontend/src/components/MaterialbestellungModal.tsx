import { useCallback, useEffect, useState } from 'react';
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
import { Select } from './ui/select-custom';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { ProjektSearchModal } from './ProjektSearchModal';
import { LieferantSearchModal, type LieferantSuchErgebnis } from './LieferantSearchModal';
import { ArtikelSearchModal, type ArtikelSuchErgebnis } from './ArtikelSearchModal';
import { KategoriePicker, type KategorieFlach } from './KategoriePicker';

// ========= Shared Types =========
interface ProjektRef {
    id: number;
    bauvorhaben?: string;
    auftragsnummer?: string;
    kunde?: string;
    excKlasse?: string | null;
}

const ZEUGNIS_OPTIONEN = [
    { value: '', label: '— Kein Zeugnis —' },
    { value: 'WZ_2_1', label: 'Werkszeugnis 2.1' },
    { value: 'WZ_2_2', label: 'Werkszeugnis 2.2' },
    { value: 'APZ_3_1', label: 'Abnahmeprüfzeugnis 3.1' },
    { value: 'APZ_3_2', label: 'Abnahmeprüfzeugnis 3.2' },
    { value: 'CE_KONFORMITAET', label: 'CE-Kennzeichnung' },
];

const EINHEITEN = [
    { value: 'Stück', label: 'Stück' },
    { value: 'm', label: 'm (Meter)' },
    { value: 'kg', label: 'kg' },
    { value: 'l', label: 'l' },
    { value: 'Paar', label: 'Paar' },
    { value: 'Paket', label: 'Paket' },
];

const EXC_LABEL: Record<string, string> = {
    EXC_1: 'EXC 1', EXC_2: 'EXC 2', EXC_3: 'EXC 3', EXC_4: 'EXC 4',
};

// ========= Position-Row-Typ =========
interface Position {
    clientId: string;                   // Lokale ID für Key
    artikelId: number | null;           // Wenn gesetzt: Stammartikel
    produktname: string;                // Freitext oder aus Artikel übernommen
    produkttext: string;
    werkstoffName?: string;
    externeArtikelnummer?: string;
    kategorieId: number | null;
    menge: string;
    einheit: string;
    fixmassMm: string;                  // in mm, als String wegen Input
    zeugnis: string;
    zeugnisVomSystem: string;
    kommentar: string;
}

const neuePosition = (): Position => ({
    clientId: `p-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    artikelId: null,
    produktname: '',
    produkttext: '',
    werkstoffName: undefined,
    externeArtikelnummer: undefined,
    kategorieId: null,
    menge: '1',
    einheit: 'Stück',
    fixmassMm: '',
    zeugnis: '',
    zeugnisVomSystem: '',
    kommentar: '',
});

// ========= Props =========
/**
 * Minimales Interface für eine zu bearbeitende Bestellposition.
 * Deckt die Felder ab, die in der Oberfläche editiert werden können.
 */
export interface EditPosition {
    id: number;
    artikelId?: number | null;
    externeArtikelnummer?: string | null;
    produktname?: string | null;
    produkttext?: string | null;
    werkstoffName?: string | null;
    kategorieId?: number | null;
    menge?: number | string | null;
    einheit?: string | null;
    fixmassMm?: number | null;
    zeugnisAnforderung?: string | null;
    kommentar?: string | null;
    projektId?: number | null;
    projektName?: string | null;
    projektNummer?: string | null;
    kundenName?: string | null;
    excKlasse?: string | null;
    lieferantId?: number | null;
    lieferantName?: string | null;
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
}

// ========= Hauptkomponente =========
export const MaterialbestellungModal: React.FC<MaterialbestellungModalProps> = ({
    isOpen,
    onClose,
    onSuccess,
    initialProjekt,
    projektSperren = false,
    editPosition = null,
}) => {
    const toast = useToast();
    const istEditModus = editPosition != null;

    // Stammdaten
    const [kategorien, setKategorien] = useState<KategorieFlach[]>([]);

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

    // Reset beim Öffnen
    useEffect(() => {
        if (!isOpen) return;

        fetch('/api/kategorien').then(r => r.json()).then(setKategorien).catch(console.error);

        if (editPosition) {
            setProjekt(editPosition.projektId ? {
                id: editPosition.projektId,
                bauvorhaben: editPosition.projektName ?? undefined,
                auftragsnummer: editPosition.projektNummer ?? undefined,
                kunde: editPosition.kundenName ?? undefined,
                excKlasse: editPosition.excKlasse ?? null,
            } : null);
            setLieferant(editPosition.lieferantId ? {
                id: editPosition.lieferantId,
                lieferantenname: editPosition.lieferantName ?? '',
            } as LieferantSuchErgebnis : null);
            setPositionen([{
                ...neuePosition(),
                artikelId: editPosition.artikelId ?? null,
                externeArtikelnummer: editPosition.externeArtikelnummer ?? undefined,
                produktname: editPosition.produktname ?? '',
                produkttext: editPosition.produkttext ?? '',
                werkstoffName: editPosition.werkstoffName ?? undefined,
                kategorieId: editPosition.kategorieId ?? null,
                menge: editPosition.menge != null ? String(editPosition.menge) : '1',
                einheit: editPosition.einheit || 'Stück',
                fixmassMm: editPosition.fixmassMm != null ? String(editPosition.fixmassMm) : '',
                zeugnis: editPosition.zeugnisAnforderung ?? '',
                kommentar: editPosition.kommentar ?? '',
            }]);
        } else {
            setProjekt(initialProjekt ?? null);
            setLieferant(null);
            setPositionen([neuePosition()]);
        }
    }, [isOpen, initialProjekt, editPosition]);

    // Norm-Vorschlag pro Position nachziehen, wenn Projekt oder Kategorie sich ändert
    const ladeZeugnisVorschlag = useCallback(async (kategorieId: number, excKlasse: string) => {
        try {
            const res = await fetch(`/api/bestellungen/zeugnis-default?kategorieId=${kategorieId}&excKlasse=${excKlasse}`);
            if (!res.ok) return '';
            const data = await res.json();
            return (data?.zeugnisTyp ?? '') as string;
        } catch {
            return '';
        }
    }, []);

    useEffect(() => {
        if (!projekt?.excKlasse) return;
        const excKlasse = projekt.excKlasse;
        positionen.forEach(pos => {
            if (!pos.kategorieId) return;
            ladeZeugnisVorschlag(pos.kategorieId, excKlasse).then(vorschlag => {
                setPositionen(prev => prev.map(p => {
                    if (p.clientId !== pos.clientId) return p;
                    // Nur setzen, wenn User noch nicht manuell etwas anderes gewählt hat
                    const userHatNichtsGewaehlt = p.zeugnis === '' || p.zeugnis === p.zeugnisVomSystem;
                    return {
                        ...p,
                        zeugnisVomSystem: vorschlag,
                        zeugnis: userHatNichtsGewaehlt ? vorschlag : p.zeugnis,
                    };
                }));
            });
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projekt?.id, projekt?.excKlasse, positionen.map(p => `${p.clientId}:${p.kategorieId}`).join(',')]);

    // Handlers
    const addLeerePosition = () => setPositionen(prev => [...prev, neuePosition()]);

    const entfernePosition = (clientId: string) => {
        setPositionen(prev => prev.length <= 1 ? [neuePosition()] : prev.filter(p => p.clientId !== clientId));
    };

    const updatePosition = (clientId: string, patch: Partial<Position>) => {
        setPositionen(prev => prev.map(p => p.clientId === clientId ? { ...p, ...patch } : p));
    };

    const artikelUebernehmen = (clientId: string, a: ArtikelSuchErgebnis) => {
        updatePosition(clientId, {
            artikelId: a.id,
            produktname: a.produktname || '',
            produkttext: a.produkttext || '',
            werkstoffName: a.werkstoffName || undefined,
            externeArtikelnummer: a.externeArtikelnummer || undefined,
            kategorieId: a.kategorieId ?? null,
            fixmassMm: a.fixmassMm ? String(a.fixmassMm) : '',
        });
    };

    const artikelMultiUebernehmen = (ausgewaehlt: ArtikelSuchErgebnis[]) => {
        if (ausgewaehlt.length === 0) return;
        setPositionen(prev => {
            // Leere erste Zeile überschreiben, wenn noch nichts drin
            const first = prev[0];
            const istErsteLeer = first && !first.artikelId && !first.produktname.trim();
            const rest = istErsteLeer ? prev.slice(1) : prev;
            const neu: Position[] = ausgewaehlt.map(a => ({
                ...neuePosition(),
                artikelId: a.id,
                produktname: a.produktname || '',
                produkttext: a.produkttext || '',
                werkstoffName: a.werkstoffName || undefined,
                externeArtikelnummer: a.externeArtikelnummer || undefined,
                kategorieId: a.kategorieId ?? null,
                fixmassMm: a.fixmassMm ? String(a.fixmassMm) : '',
            }));
            return [...rest, ...neu];
        });
    };

    const speichern = async () => {
        if (!lieferant) { toast.warning('Bitte Lieferanten auswählen'); return; }
        const zuSpeichern = positionen.filter(p => p.produktname.trim() && p.menge && !isNaN(Number(p.menge)));
        if (zuSpeichern.length === 0) { toast.warning('Bitte mindestens eine Position ausfüllen'); return; }

        setSaving(true);

        // Edit-Modus: PUT für genau eine Position
        if (istEditModus && editPosition) {
            const pos = zuSpeichern[0];
            const payload = {
                projektId: projekt?.id ?? null,
                lieferantId: lieferant.id,
                kategorieId: pos.kategorieId,
                artikelId: pos.artikelId,
                produktname: pos.produktname.trim(),
                produkttext: pos.produkttext.trim() || null,
                menge: Number(pos.menge),
                einheit: pos.einheit.trim() || 'Stück',
                fixmassMm: pos.fixmassMm ? Number(pos.fixmassMm) : null,
                zeugnisAnforderung: pos.zeugnis || null,
                kommentar: pos.kommentar.trim() || null,
            };
            try {
                const res = await fetch(`/api/bestellungen/${editPosition.id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                });
                setSaving(false);
                if (res.ok) {
                    toast.success('Änderungen gespeichert');
                    onSuccess?.();
                    onClose();
                } else if (res.status === 409) {
                    toast.error(res.headers.get('X-Error-Reason') || 'Position wurde bereits exportiert');
                } else {
                    toast.error('Speichern fehlgeschlagen');
                }
            } catch {
                setSaving(false);
                toast.error('Speichern fehlgeschlagen');
            }
            return;
        }

        // Standard-Modus: POST je Position
        let erfolg = 0;
        let fehler = 0;

        for (const pos of zuSpeichern) {
            try {
                const payload = {
                    projektId: projekt?.id ?? null,
                    lieferantId: lieferant.id,
                    kategorieId: pos.kategorieId,
                    artikelId: pos.artikelId,
                    produktname: pos.produktname.trim(),
                    produkttext: pos.produkttext.trim() || null,
                    menge: Number(pos.menge),
                    einheit: pos.einheit.trim() || 'Stück',
                    fixmassMm: pos.fixmassMm ? Number(pos.fixmassMm) : null,
                    zeugnisAnforderung: pos.zeugnis || null,
                    kommentar: pos.kommentar.trim() || null,
                };
                const res = await fetch('/api/bestellungen/manuell', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                });
                if (res.ok) erfolg++; else fehler++;
            } catch {
                fehler++;
            }
        }

        setSaving(false);
        if (erfolg > 0 && fehler === 0) {
            toast.success(`${erfolg} Position${erfolg > 1 ? 'en' : ''} gespeichert`);
            onSuccess?.();
            onClose();
        } else if (erfolg > 0) {
            toast.warning(`${erfolg} gespeichert, ${fehler} fehlgeschlagen`);
            onSuccess?.();
        } else {
            toast.error('Speichern fehlgeschlagen');
        }
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
                                {istEditModus ? 'Bestellposition bearbeiten' : 'Materialbestellung'}
                            </h2>
                            <p className="text-sm text-slate-500">
                                {istEditModus
                                    ? 'Änderungen werden gespeichert, solange die Position nicht exportiert wurde.'
                                    : 'Mehrere Positionen für einen Lieferanten erfassen'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={saving}>Abbrechen</Button>
                        <Button
                            onClick={speichern}
                            disabled={saving || !lieferant}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plus className="w-4 h-4 mr-2" />}
                            {saving ? 'Speichern...' : istEditModus ? 'Änderungen speichern' : 'Alle speichern'}
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}>
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Shared Header-Controls: Projekt + Lieferant */}
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
                                kategorien={kategorien}
                                onUpdate={patch => updatePosition(pos.clientId, patch)}
                                onRemove={() => entfernePosition(pos.clientId)}
                                onArtikelSuchen={() => setArtikelModalFuerZeile(pos.clientId)}
                                showRemove={!istEditModus}
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
    kategorien: KategorieFlach[];
    onUpdate: (patch: Partial<Position>) => void;
    onRemove: () => void;
    onArtikelSuchen: () => void;
    showRemove?: boolean;
}

const PositionRow: React.FC<PositionRowProps> = ({
    index, position, kategorien, onUpdate, onRemove, onArtikelSuchen, showRemove = true,
}) => {
    return (
        <div className="bg-white border border-slate-200 rounded-xl p-4 hover:border-slate-300 transition-colors">
            <div className="flex gap-4">
                {/* Nummerierung */}
                <div className="flex-shrink-0">
                    <div className="w-9 h-9 rounded-full bg-rose-100 text-rose-700 flex items-center justify-center text-sm font-bold">
                        {index + 1}
                    </div>
                </div>

                {/* Eingabefelder */}
                <div className="flex-1 space-y-3 min-w-0">
                    {/* Zeile 1: Artikel-Auswahl + Produktname */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-12 md:col-span-5">
                            <label className="block text-xs font-medium text-slate-500 mb-1">
                                Artikel aus Stammdaten {position.artikelId && <span className="text-rose-600">·  verknüpft</span>}
                            </label>
                            <button
                                type="button"
                                onClick={onArtikelSuchen}
                                className={cn(
                                    'w-full flex items-center gap-2 px-3 py-1.5 border rounded-md text-sm text-left transition-colors',
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
                                {position.artikelId && (
                                    <span
                                        role="button"
                                        tabIndex={0}
                                        onClick={e => {
                                            e.stopPropagation();
                                            onUpdate({ artikelId: null, externeArtikelnummer: undefined });
                                        }}
                                        onKeyDown={e => {
                                            if (e.key === 'Enter' || e.key === ' ') {
                                                e.stopPropagation();
                                                onUpdate({ artikelId: null, externeArtikelnummer: undefined });
                                            }
                                        }}
                                        className="p-0.5 hover:bg-rose-200 rounded cursor-pointer"
                                        aria-label="Artikel-Verknüpfung entfernen"
                                    >
                                        <X className="w-3 h-3" />
                                    </span>
                                )}
                            </button>
                        </div>
                        <div className="col-span-12 md:col-span-7">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Produktname *</label>
                            <Input
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

                    {/* Zeile 2: Menge | Einheit | Fixmaß | Kategorie */}
                    <div className="grid grid-cols-12 gap-3">
                        <div className="col-span-6 md:col-span-2">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Menge *</label>
                            <Input
                                type="number"
                                value={position.menge}
                                onChange={e => onUpdate({ menge: e.target.value })}
                                placeholder="1"
                                min="0"
                                step="any"
                            />
                        </div>
                        <div className="col-span-6 md:col-span-2">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Einheit</label>
                            <Select
                                value={position.einheit}
                                onChange={v => onUpdate({ einheit: v })}
                                options={EINHEITEN}
                            />
                        </div>
                        <div className="col-span-12 md:col-span-3">
                            <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                <Ruler className="w-3 h-3" />
                                Fixmaß (mm)
                            </label>
                            <Input
                                type="number"
                                value={position.fixmassMm}
                                onChange={e => onUpdate({ fixmassMm: e.target.value })}
                                placeholder="z. B. 6000"
                                min="0"
                                step="1"
                            />
                        </div>
                        <div className="col-span-12 md:col-span-5">
                            <label className="block text-xs font-medium text-slate-500 mb-1">Warengruppe</label>
                            <KategoriePicker
                                kategorien={kategorien}
                                value={position.kategorieId}
                                onChange={(id) => onUpdate({ kategorieId: id })}
                                compact
                            />
                        </div>
                    </div>

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
                                value={position.zeugnis}
                                onChange={v => onUpdate({ zeugnis: v })}
                                options={ZEUGNIS_OPTIONEN}
                            />
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
                </div>

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
