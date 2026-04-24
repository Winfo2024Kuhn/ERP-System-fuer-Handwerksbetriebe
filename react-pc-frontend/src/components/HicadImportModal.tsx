import { useCallback, useEffect, useRef, useState } from 'react';
import {
    Briefcase,
    CheckCircle2,
    FileSpreadsheet,
    Info,
    Loader2,
    Package,
    Ruler,
    Scissors,
    Truck,
    Upload,
    X,
} from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { LieferantSearchModal, type LieferantSuchErgebnis } from './LieferantSearchModal';
import { ArtikelSearchModal, type ArtikelSuchErgebnis } from './ArtikelSearchModal';

// ==================== TYPES (aus HicadImportDtos.java) ====================
interface SaegelisteZeile {
    posNr?: number;
    anzahl: number;
    bezeichnung: string;
    laengeMm?: number;
    werkstoff?: string;
    anschnittSteg?: string;
    anschnittFlansch?: string;
    gewichtProStueckKg?: number;
    gesamtGewichtKg?: number;
    /** URLs der Schnittbilder aus der HiCAD-Excel (null, wenn nicht vorhanden). */
    anschnittbildStegUrl?: string | null;
    anschnittbildFlanschUrl?: string | null;
}

interface ProfilGruppe {
    groupKey: string;
    bezeichnung: string;
    werkstoff?: string;
    artikelId?: number | null;
    artikelProduktname?: string | null;
    verpackungseinheitM?: number | null;
    defaultAggregieren: boolean;
    summeMeter?: number;
    summeStueck?: number;
    berechneteStaebe?: number;
    zeilen: SaegelisteZeile[];
}

interface PreviewResponse {
    zeichnungsnr?: string;
    auftragsnummer?: string;
    auftragstext?: string;
    kunde?: string;
    ersteller?: string;
    erstelltAm?: string;
    erkannteProjektId?: number | null;
    erkannteProjektName?: string | null;
    gruppen: ProfilGruppe[];
}

interface GruppenEntscheidung {
    aggregieren: boolean;
    artikelId: number | null;
    artikelProduktname: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
    stangenlaengeM: number | null;
}

interface ProjektRef {
    id: number;
    bauvorhaben: string;
    auftragsnummer?: string;
}

// ==================== PROPS ====================
interface HicadImportModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    projekt: ProjektRef;
}

const formatNumber = (val: number | null | undefined, digits = 2) =>
    val != null ? val.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits }) : '-';

export function HicadImportModal({ isOpen, onClose, onSuccess, projekt }: HicadImportModalProps) {
    const toast = useToast();

    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [preview, setPreview] = useState<PreviewResponse | null>(null);
    const [entscheidungen, setEntscheidungen] = useState<Record<string, GruppenEntscheidung>>({});

    // Picker-States
    const [lieferantPickerFuer, setLieferantPickerFuer] = useState<string | null>(null);
    const [artikelPickerFuer, setArtikelPickerFuer] = useState<string | null>(null);

    // Live-Neuberechnung der Zuschnitt-Optimierung (FFD) bei Stangenlaengen-Aenderung
    const [optimiereLaufend, setOptimiereLaufend] = useState<Record<string, boolean>>({});
    const optimiereTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
    const optimiereReqId = useRef<Record<string, number>>({});

    const reset = useCallback(() => {
        setFile(null);
        setPreview(null);
        setEntscheidungen({});
        setUploading(false);
        setSubmitting(false);
    }, []);

    const handleClose = useCallback(() => {
        reset();
        onClose();
    }, [onClose, reset]);

    // ==================== UPLOAD → PREVIEW ====================
    const handleUpload = async () => {
        if (!file) {
            toast.warning('Bitte eine Datei auswählen');
            return;
        }
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append('file', file);
            const res = await fetch('/api/bestellungen/import/hicad/preview', {
                method: 'POST',
                body: formData,
            });
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') || 'Upload fehlgeschlagen';
                throw new Error(reason);
            }
            const data: PreviewResponse = await res.json();
            setPreview(data);

            // Default-Entscheidungen pro Gruppe initialisieren
            const init: Record<string, GruppenEntscheidung> = {};
            data.gruppen.forEach(g => {
                init[g.groupKey] = {
                    aggregieren: g.defaultAggregieren,
                    artikelId: g.artikelId ?? null,
                    artikelProduktname: g.artikelProduktname ?? null,
                    lieferantId: null,
                    lieferantName: null,
                    stangenlaengeM: g.verpackungseinheitM ?? null,
                };
            });
            setEntscheidungen(init);

            // Warnung, falls HiCAD-Datei ein anderes Projekt erkennt
            if (data.erkannteProjektId && data.erkannteProjektId !== projekt.id) {
                toast.warning(
                    `HiCAD-Datei verweist auf Projekt „${data.erkannteProjektName ?? '?'}" – Import erfolgt trotzdem auf „${projekt.bauvorhaben}".`,
                );
            }
        } catch (err) {
            console.error(err);
            toast.error(err instanceof Error ? err.message : 'Upload fehlgeschlagen');
        } finally {
            setUploading(false);
        }
    };

    // ==================== CONFIRM ====================
    const handleConfirm = async () => {
        if (!preview) return;

        // Gruppen, bei denen kein Artikel gematcht ist UND kein Lieferant zugeordnet → warnen
        const ohneLieferant = preview.gruppen.filter(
            g => !entscheidungen[g.groupKey]?.artikelId && !entscheidungen[g.groupKey]?.lieferantId,
        );
        if (ohneLieferant.length > 0) {
            toast.warning(
                `${ohneLieferant.length} Gruppe(n) ohne Artikel-Match und ohne Lieferant – bitte Lieferant wählen.`,
            );
            return;
        }

        setSubmitting(true);
        try {
            const body = {
                projektId: projekt.id,
                kommentarPrefix: preview.auftragsnummer
                    ? `HiCAD ${preview.auftragsnummer}`
                    : 'HiCAD-Import',
                gruppen: preview.gruppen.map(g => {
                    const e = entscheidungen[g.groupKey];
                    return {
                        groupKey: g.groupKey,
                        projektId: projekt.id,
                        lieferantId: e?.lieferantId ?? null,
                        artikelId: e?.artikelId ?? null,
                        kategorieId: null,
                        aggregieren: e?.aggregieren ?? g.defaultAggregieren,
                        stangenlaengeM: e?.stangenlaengeM ?? g.verpackungseinheitM ?? null,
                    };
                }),
                preview: preview.gruppen,
            };

            const res = await fetch('/api/bestellungen/import/hicad/confirm', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') || 'Import fehlgeschlagen';
                throw new Error(reason);
            }
            const result = await res.json();
            toast.success(`${result.angelegtePositionen} Positionen angelegt`);
            onSuccess();
            handleClose();
        } catch (err) {
            console.error(err);
            toast.error(err instanceof Error ? err.message : 'Import fehlgeschlagen');
        } finally {
            setSubmitting(false);
        }
    };

    // ==================== UPDATE-HELPER ====================
    const updateEntscheidung = (key: string, patch: Partial<GruppenEntscheidung>) => {
        setEntscheidungen(prev => ({ ...prev, [key]: { ...prev[key], ...patch } }));
    };

    // Debounced FFD-Neuberechnung beim Backend (400 ms) — überschreibt berechneteStaebe.
    const scheduleOptimize = useCallback((groupKey: string, stangenlaengeM: number) => {
        const existing = optimiereTimers.current[groupKey];
        if (existing) clearTimeout(existing);
        optimiereTimers.current[groupKey] = setTimeout(async () => {
            const gruppe = preview?.gruppen.find(g => g.groupKey === groupKey);
            if (!gruppe || !stangenlaengeM || stangenlaengeM <= 0) return;
            const myReqId = (optimiereReqId.current[groupKey] ?? 0) + 1;
            optimiereReqId.current[groupKey] = myReqId;
            setOptimiereLaufend(prev => ({ ...prev, [groupKey]: true }));
            try {
                const res = await fetch('/api/bestellungen/import/hicad/optimiere', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ stangenlaengeM, zeilen: gruppe.zeilen }),
                });
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const data: { anzahlStangen: number; verschnittMm: number; ueberlange: number } = await res.json();
                // Stale-Response ignorieren (falls inzwischen ein neuerer Request lief)
                if (optimiereReqId.current[groupKey] !== myReqId) return;
                setPreview(prev => {
                    if (!prev) return prev;
                    return {
                        ...prev,
                        gruppen: prev.gruppen.map(g =>
                            g.groupKey === groupKey ? { ...g, berechneteStaebe: data.anzahlStangen } : g,
                        ),
                    };
                });
            } catch (err) {
                console.warn('Zuschnitt-Optimierung fehlgeschlagen', err);
            } finally {
                if (optimiereReqId.current[groupKey] === myReqId) {
                    setOptimiereLaufend(prev => ({ ...prev, [groupKey]: false }));
                }
            }
        }, 400);
    }, [preview]);

    // Timer aufräumen beim Unmount
    useEffect(() => {
        const timers = optimiereTimers.current;
        return () => {
            Object.values(timers).forEach(clearTimeout);
        };
    }, []);

    if (!isOpen) return null;

    const hatPreview = preview != null;

    // Zusammenfassung für Header/Footer
    const gruppenGesamt = preview?.gruppen.length ?? 0;
    const gruppenMitArtikel = preview
        ? preview.gruppen.filter(g => entscheidungen[g.groupKey]?.artikelId).length
        : 0;
    const gruppenOhneZuordnung = preview
        ? preview.gruppen.filter(
              g =>
                  !entscheidungen[g.groupKey]?.artikelId &&
                  !entscheidungen[g.groupKey]?.lieferantId,
          ).length
        : 0;

    return (
        <>
            <div
                className="fixed inset-4 z-50 bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-slate-200"
                role="dialog"
                aria-modal="true"
                aria-labelledby="hicad-import-title"
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-gradient-to-r from-rose-50 to-white shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
                            <FileSpreadsheet className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 id="hicad-import-title" className="text-xl font-bold text-slate-900">
                                HiCAD-Sägeliste importieren
                            </h2>
                            <p className="text-sm text-slate-500">
                                {hatPreview
                                    ? `${gruppenGesamt} Gruppe${gruppenGesamt === 1 ? '' : 'n'} erkannt · ${gruppenMitArtikel} mit Stammartikel`
                                    : 'Excel-Datei einlesen und automatisch Bestellpositionen anlegen'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        {hatPreview && (
                            <>
                                <Button
                                    variant="ghost"
                                    onClick={reset}
                                    disabled={submitting}
                                    className="text-slate-600"
                                >
                                    Andere Datei
                                </Button>
                                <Button
                                    onClick={handleConfirm}
                                    disabled={submitting}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    {submitting ? (
                                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                    ) : (
                                        <CheckCircle2 className="w-4 h-4 mr-2" />
                                    )}
                                    {submitting
                                        ? 'Wird angelegt...'
                                        : gruppenGesamt > 0
                                            ? `${gruppenGesamt} Position${gruppenGesamt === 1 ? '' : 'en'} anlegen`
                                            : 'Positionen anlegen'}
                                </Button>
                            </>
                        )}
                        <Button variant="ghost" size="sm" onClick={handleClose} disabled={submitting} aria-label="Schließen">
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-auto">
                    {/* Upload-Bereich */}
                    {!hatPreview && (
                        <div className="flex flex-col items-center justify-center min-h-full px-6 py-10">
                            <label
                                htmlFor="hicad-file-input"
                                className={cn(
                                    'group w-full max-w-xl rounded-2xl p-10 text-center cursor-pointer transition-colors',
                                    'border-2 border-dashed',
                                    file
                                        ? 'border-rose-400 bg-rose-50/60'
                                        : 'border-slate-300 bg-white hover:border-rose-400 hover:bg-rose-50/40',
                                )}
                            >
                                <div
                                    className={cn(
                                        'w-14 h-14 mx-auto mb-4 rounded-xl flex items-center justify-center transition-colors',
                                        file
                                            ? 'bg-rose-600 text-white'
                                            : 'bg-slate-100 text-slate-400 group-hover:bg-rose-100 group-hover:text-rose-600',
                                    )}
                                >
                                    {file ? (
                                        <FileSpreadsheet className="w-7 h-7" />
                                    ) : (
                                        <Upload className="w-7 h-7" />
                                    )}
                                </div>
                                {file ? (
                                    <>
                                        <p className="font-semibold text-slate-900 break-all">{file.name}</p>
                                        <p className="text-xs text-slate-500 mt-1">
                                            {(file.size / 1024).toLocaleString('de-DE', { maximumFractionDigits: 0 })} KB
                                            · Klicken zum Ersetzen
                                        </p>
                                    </>
                                ) : (
                                    <>
                                        <p className="font-semibold text-slate-900">
                                            Excel-Datei aus HiCAD auswählen
                                        </p>
                                        <p className="text-sm text-slate-500 mt-1">
                                            Sheet „Sägeliste" · Format .xlsx
                                        </p>
                                    </>
                                )}
                                <Input
                                    id="hicad-file-input"
                                    type="file"
                                    accept=".xlsx"
                                    onChange={e => setFile(e.target.files?.[0] ?? null)}
                                    className="hidden"
                                />
                            </label>

                            <Button
                                onClick={handleUpload}
                                disabled={!file || uploading}
                                className="mt-5 bg-rose-600 text-white hover:bg-rose-700 min-w-[180px]"
                            >
                                {uploading ? (
                                    <>
                                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                        Wird analysiert...
                                    </>
                                ) : (
                                    <>
                                        <Upload className="w-4 h-4 mr-2" />
                                        Analysieren
                                    </>
                                )}
                            </Button>

                            <p className="mt-6 flex items-center gap-2 text-xs text-slate-500 max-w-md text-center">
                                <Info className="w-3.5 h-3.5 shrink-0" />
                                Die Datei wird nur zur Analyse gelesen – es wird noch nichts gespeichert.
                            </p>
                        </div>
                    )}

                    {/* Preview-Bereich */}
                    {hatPreview && preview && (
                        <>
                            {/* Shared Header-Controls: Meta + Projekt-Info */}
                            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200">
                                {/* Meta-Infos aus HiCAD-Datei */}
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                                    <MetaField label="Auftragsnr." value={preview.auftragsnummer} mono />
                                    <MetaField label="Auftragstext" value={preview.auftragstext} />
                                    <MetaField label="Kunde" value={preview.kunde} />
                                </div>

                                {/* Projekt (fest aus Seiten-Kontext) */}
                                <div className="flex items-center gap-3 px-3 py-2.5 border border-rose-200 bg-white rounded-lg">
                                    <Briefcase className="w-5 h-5 flex-shrink-0 text-rose-600" />
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
                                            Import in Projekt
                                        </p>
                                        <p className="font-medium text-slate-900 truncate">
                                            {projekt.bauvorhaben}
                                        </p>
                                    </div>
                                    {projekt.auftragsnummer && (
                                        <span className="font-mono text-xs bg-slate-100 text-slate-700 px-2 py-1 rounded">
                                            {projekt.auftragsnummer}
                                        </span>
                                    )}
                                </div>
                            </div>

                            {/* Gruppen-Bereich */}
                            <div className="px-6 py-4">
                                <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                                    <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">
                                        Gruppen <span className="text-slate-400">({gruppenGesamt})</span>
                                    </h3>
                                    <div className="flex items-center gap-2 text-xs">
                                        <StatusChip
                                            tone="emerald"
                                            label={`${gruppenMitArtikel} mit Stammartikel`}
                                        />
                                        {gruppenOhneZuordnung > 0 && (
                                            <StatusChip
                                                tone="amber"
                                                label={`${gruppenOhneZuordnung} ohne Zuordnung`}
                                            />
                                        )}
                                    </div>
                                </div>

                                <div className="space-y-3">
                                    {preview.gruppen.map(g => {
                                        const e = entscheidungen[g.groupKey];
                                        if (!e) return null;
                                        const aggregieren = e.aggregieren;
                                        const stangeM = e.stangenlaengeM ?? g.verpackungseinheitM ?? 6;
                                        // Server berechnet die optimierte Stabzahl (FFD). Fallback: naive Schätzung.
                                        const staebe =
                                            g.berechneteStaebe ??
                                            (g.summeMeter ? Math.ceil(g.summeMeter / Math.max(stangeM, 1)) : 0);
                                        const hatArtikel = !!e.artikelId;

                                        return (
                                            <div
                                                key={g.groupKey}
                                                className={cn(
                                                    'bg-white border rounded-xl p-4 transition-colors',
                                                    hatArtikel
                                                        ? 'border-slate-200 hover:border-slate-300'
                                                        : 'border-amber-200 hover:border-amber-300',
                                                )}
                                            >
                                                {/* Kopfzeile der Gruppe */}
                                                <div className="flex items-start justify-between gap-4 flex-wrap">
                                                    <div className="flex-1 min-w-0">
                                                        <div className="flex items-center gap-2 flex-wrap">
                                                            <Package className="w-4 h-4 text-slate-500 shrink-0" />
                                                            <h4 className="font-semibold text-slate-900">
                                                                {g.bezeichnung}
                                                            </h4>
                                                            {g.werkstoff && (
                                                                <span className="px-2 py-0.5 text-xs rounded-full bg-slate-100 text-slate-700 font-medium">
                                                                    {g.werkstoff}
                                                                </span>
                                                            )}
                                                            {hatArtikel ? (
                                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
                                                                    <CheckCircle2 className="w-3 h-3" />
                                                                    {e.artikelProduktname || 'Stammartikel'}
                                                                </span>
                                                            ) : (
                                                                <span className="px-2 py-0.5 text-xs rounded-full bg-amber-50 text-amber-800 border border-amber-200 font-medium">
                                                                    Freitext – kein Stammartikel
                                                                </span>
                                                            )}
                                                        </div>
                                                        <div className="flex items-center gap-3 text-sm text-slate-600 mt-2 flex-wrap">
                                                            <span>
                                                                <strong className="text-slate-900">
                                                                    {g.summeStueck}
                                                                </strong>{' '}
                                                                Stk
                                                            </span>
                                                            <span className="text-slate-300">·</span>
                                                            <span>
                                                                <strong className="text-slate-900">
                                                                    {formatNumber(g.summeMeter)}
                                                                </strong>{' '}
                                                                m gesamt
                                                            </span>
                                                            {g.zeilen.length > 1 && (
                                                                <>
                                                                    <span className="text-slate-300">·</span>
                                                                    <span>
                                                                        {g.zeilen.length} verschiedene Längen
                                                                    </span>
                                                                </>
                                                            )}
                                                        </div>
                                                    </div>

                                                    <div className="flex items-center gap-2 flex-wrap">
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            onClick={() => setArtikelPickerFuer(g.groupKey)}
                                                        >
                                                            <Package className="w-4 h-4 mr-1.5" />
                                                            {e.artikelId ? 'Artikel ändern' : 'Artikel zuordnen'}
                                                        </Button>
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            onClick={() => setLieferantPickerFuer(g.groupKey)}
                                                            className={cn(
                                                                e.lieferantName &&
                                                                    'border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100',
                                                            )}
                                                        >
                                                            <Truck className="w-4 h-4 mr-1.5" />
                                                            {e.lieferantName || 'Lieferant'}
                                                        </Button>
                                                    </div>
                                                </div>

                                                {/* Aggregations-Toggle */}
                                                <div className="mt-3 flex items-center gap-1 p-1 bg-slate-100 rounded-full w-fit">
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            updateEntscheidung(g.groupKey, { aggregieren: false })
                                                        }
                                                        className={cn(
                                                            'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition-colors',
                                                            !aggregieren
                                                                ? 'bg-white text-rose-700 shadow-sm'
                                                                : 'text-slate-600 hover:text-slate-900',
                                                        )}
                                                    >
                                                        <Ruler className="w-3.5 h-3.5" />
                                                        Fixzuschnitt ({g.zeilen.length})
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            updateEntscheidung(g.groupKey, { aggregieren: true })
                                                        }
                                                        className={cn(
                                                            'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition-colors',
                                                            aggregieren
                                                                ? 'bg-white text-rose-700 shadow-sm'
                                                                : 'text-slate-600 hover:text-slate-900',
                                                        )}
                                                    >
                                                        <Scissors className="w-3.5 h-3.5" />
                                                        Stangenware (selbst schneiden)
                                                    </button>
                                                </div>

                                                {/* Aggregations-Detail */}
                                                {aggregieren && (
                                                    <div className="mt-3 p-3 bg-rose-50 border border-rose-100 rounded-lg">
                                                        <div className="flex items-center gap-3 flex-wrap text-sm">
                                                            <span className="text-slate-700">
                                                                <strong className="text-slate-900">
                                                                    {formatNumber(g.summeMeter)} m
                                                                </strong>{' '}
                                                                benötigt
                                                            </span>
                                                            <span className="text-slate-300">·</span>
                                                            <label className="flex items-center gap-2 text-slate-700">
                                                                <span>Stange à</span>
                                                                <Input
                                                                    type="number"
                                                                    min={1}
                                                                    step={1}
                                                                    value={stangeM}
                                                                    onChange={ev => {
                                                                        const neu = parseInt(ev.target.value, 10) || null;
                                                                        updateEntscheidung(g.groupKey, {
                                                                            stangenlaengeM: neu,
                                                                        });
                                                                        if (neu && neu > 0) {
                                                                            scheduleOptimize(g.groupKey, neu);
                                                                        }
                                                                    }}
                                                                    className="w-20 h-8 text-sm"
                                                                />
                                                                <span>m</span>
                                                                {g.verpackungseinheitM != null &&
                                                                    stangeM !== g.verpackungseinheitM && (
                                                                        <span className="text-xs text-slate-400">
                                                                            (Standard: {g.verpackungseinheitM} m)
                                                                        </span>
                                                                    )}
                                                            </label>
                                                            <span className="text-slate-300">=</span>
                                                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-rose-600 text-white text-sm font-semibold">
                                                                {optimiereLaufend[g.groupKey] ? (
                                                                    <>
                                                                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                                                                        Optimiere…
                                                                    </>
                                                                ) : (
                                                                    <>
                                                                        {staebe} {staebe === 1 ? 'Stange' : 'Stangen'} bestellen
                                                                    </>
                                                                )}
                                                            </span>
                                                        </div>
                                                    </div>
                                                )}

                                                {/* Zuschnitt-Liste bei Fixzuschnitt */}
                                                {!aggregieren && (
                                                    <div className="mt-3 flex flex-wrap gap-1.5">
                                                        {g.zeilen.map((z, idx) => {
                                                            const hatAnschnitt = !!(
                                                                z.anschnittbildStegUrl ||
                                                                z.anschnittbildFlanschUrl ||
                                                                z.anschnittSteg ||
                                                                z.anschnittFlansch
                                                            );
                                                            return (
                                                                <span
                                                                    key={idx}
                                                                    className="inline-flex items-center gap-1.5 px-2 py-1 text-xs rounded-md bg-slate-50 border border-slate-200 text-slate-700 font-medium"
                                                                >
                                                                    <span className="text-rose-600 font-semibold">
                                                                        {z.anzahl}×
                                                                    </span>
                                                                    <span>{z.laengeMm} mm</span>
                                                                    {hatAnschnitt && (
                                                                        <span className="inline-flex items-center gap-1 pl-1 ml-0.5 border-l border-slate-200">
                                                                            <AnschnittChip label="Steg" text={z.anschnittSteg} bildUrl={z.anschnittbildStegUrl} />
                                                                            <AnschnittChip label="Flansch" text={z.anschnittFlansch} bildUrl={z.anschnittbildFlanschUrl} />
                                                                        </span>
                                                                    )}
                                                                </span>
                                                            );
                                                        })}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>

            {/* Sub-Modals */}
            <LieferantSearchModal
                isOpen={lieferantPickerFuer != null}
                onClose={() => setLieferantPickerFuer(null)}
                onSelect={(l: LieferantSuchErgebnis) => {
                    if (lieferantPickerFuer) {
                        updateEntscheidung(lieferantPickerFuer, {
                            lieferantId: l.id,
                            lieferantName: l.lieferantenname,
                        });
                    }
                    setLieferantPickerFuer(null);
                }}
            />

            <ArtikelSearchModal
                isOpen={artikelPickerFuer != null}
                onClose={() => setArtikelPickerFuer(null)}
                onSelect={(a: ArtikelSuchErgebnis) => {
                    if (artikelPickerFuer) {
                        updateEntscheidung(artikelPickerFuer, {
                            artikelId: a.id,
                            artikelProduktname: a.produktname,
                        });
                    }
                    setArtikelPickerFuer(null);
                }}
            />
        </>
    );
}

// ==================== SUB-COMPONENTS ====================
function AnschnittChip({
    label,
    text,
    bildUrl,
}: {
    label: 'Steg' | 'Flansch';
    text?: string | null;
    bildUrl?: string | null;
}) {
    if (!text && !bildUrl) return null;
    // HiCAD-Zelle: "27.6° 27.6°" → links und rechts vom Bild je ein Winkel
    const teile = (text ?? '').split(/\s+/).filter(Boolean);
    const links = teile[0] ?? null;
    const rechts = teile.length > 1 ? teile[teile.length - 1] : null;
    return (
        <span className="inline-flex items-center gap-0.5" title={`Anschnitt ${label}`}>
            {links && <span className="text-[10px] text-rose-600 tabular-nums">{links}</span>}
            {bildUrl && (
                <img
                    src={bildUrl}
                    alt={`Anschnitt ${label}`}
                    className="h-5 w-auto rounded bg-white border border-slate-200 object-contain"
                />
            )}
            {rechts && <span className="text-[10px] text-rose-600 tabular-nums">{rechts}</span>}
        </span>
    );
}

function MetaField({
    label,
    value,
    mono,
}: {
    label: string;
    value?: string | null;
    mono?: boolean;
}) {
    return (
        <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                {label}
            </p>
            <p
                className={cn(
                    'font-medium text-slate-900 truncate',
                    mono && value && 'font-mono text-sm',
                )}
            >
                {value || <span className="text-slate-400 font-normal">–</span>}
            </p>
        </div>
    );
}

function StatusChip({
    tone,
    label,
}: {
    tone: 'emerald' | 'amber';
    label: string;
}) {
    const tones: Record<typeof tone, string> = {
        emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
        amber: 'bg-amber-50 text-amber-800 border-amber-200',
    };
    return (
        <span
            className={cn(
                'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border font-medium',
                tones[tone],
            )}
        >
            <span
                className={cn(
                    'w-1.5 h-1.5 rounded-full',
                    tone === 'emerald' && 'bg-emerald-500',
                    tone === 'amber' && 'bg-amber-500',
                )}
            />
            {label}
        </span>
    );
}
