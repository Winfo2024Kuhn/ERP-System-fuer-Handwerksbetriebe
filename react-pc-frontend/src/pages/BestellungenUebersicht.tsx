import { useState, useEffect, useCallback } from 'react';
import { PdfCanvasViewer } from '../components/ui/PdfCanvasViewer';
import DocumentPreviewModal from '../components/DocumentPreviewModal';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { RefreshCw, FileText, ChevronRight, Package, Clock, CheckCircle, AlertCircle, X, Download, FolderOpen, Plus, Trash2, Percent, Euro, Save, Briefcase, EyeOff, Eye, Archive } from 'lucide-react';
import { ProjectSelectModal } from '../components/ProjectSelectModal';
import { KostenstelleSelectModal } from '../components/KostenstelleSelectModal';
import { useToast } from '../components/ui/toast';
import { ZuordnungModal as BelegZuordnungModal } from '../components/ZuordnungModal';

// ========== Types ==========
interface DokumentRef {
    id: number;
    typ: 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | 'LIEFERSCHEIN' | 'RECHNUNG' | 'GUTSCHRIFT' | 'SONSTIG';
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragBrutto: number | null;
    betragNetto: number | null;
    liefertermin: string | null;
    dateiname: string;
    pdfUrl: string | null;
}

interface DokumentenKette {
    id: string;
    lieferantId: number | null;
    lieferantName: string | null;
    dokumente: DokumentRef[];
}

interface BestellungsUebersicht {
    offeneAnfragen: DokumentenKette[];
    laufendeBestellungen: DokumentenKette[];
    abgeschlossen: DokumentenKette[];
    zugeordnet: DokumentenKette[];
    ausgeblendet: DokumentenKette[];
}

interface BackendDokumentenKette {
    bestellung?: DokumentRef | null;
    wareneingaenge?: DokumentRef[] | null;
    rechnungen?: DokumentRef[] | null;
    dokumente?: DokumentRef[] | null;
    id?: string;
    lieferantId?: number | null;
    lieferantName?: string | null;
}

interface GeschaeftsdatenDto {
    id: number;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    mwstSatz: number | null;
    liefertermin: string | null;
    bestellnummer: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
}

interface BelegZuordnungRef {
    id: number;
    belegNummer: string | null;
    belegDatum: string | null;
    beschreibung: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    lieferantName: string | null;
    originalDateiname: string | null;
    mimeType: string | null;
    pdfUrl: string | null;
}



interface ProjektAnteil {
    projektId?: number;
    projektName?: string;
    kostenstelleId?: number;
    kostenstelleName?: string;
    betrag: number;
    prozentanteil: number | null;
    beschreibung: string;
    // Über wie viele Jahre die Kosten verteilt werden (nur Kostenstellen). 1 = keine Aufteilung.
    streckungJahre?: number;
}

// ========== Helpers ==========
const formatDate = (isoText: string | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

const formatEuro = (value: number | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

const TYP_LABELS: Record<DokumentRef['typ'], string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'AB',
    LIEFERSCHEIN: 'Lieferschein',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
};

const asArray = <T,>(value: unknown): T[] => Array.isArray(value) ? value : [];

const normalizeKette = (raw: BackendDokumentenKette): DokumentenKette => {
    const dokumente = raw.dokumente && raw.dokumente.length > 0
        ? raw.dokumente
        : [
            raw.bestellung,
            ...asArray<DokumentRef>(raw.wareneingaenge),
            ...asArray<DokumentRef>(raw.rechnungen),
        ].filter((dok): dok is DokumentRef => Boolean(dok));

    return {
        id: raw.id || dokumente.map(dok => dok.id).join('-') || `kette-${Math.random().toString(36).slice(2)}`,
        lieferantId: raw.lieferantId ?? null,
        lieferantName: raw.lieferantName ?? null,
        dokumente,
    };
};

const normalizeUebersicht = (raw: any): BestellungsUebersicht => {
    const offeneAnfragen = asArray<BackendDokumentenKette>(raw?.offeneAnfragen).map(normalizeKette);
    const laufendeBestellungen = [
        ...asArray<BackendDokumentenKette>(raw?.laufendeBestellungen),
        ...asArray<BackendDokumentenKette>(raw?.offeneBestellungen),
        ...asArray<BackendDokumentenKette>(raw?.offeneWareneingaenge),
    ].map(normalizeKette);

    return {
        offeneAnfragen,
        laufendeBestellungen,
        abgeschlossen: [
            ...asArray<BackendDokumentenKette>(raw?.abgeschlossen),
            ...asArray<BackendDokumentenKette>(raw?.offeneRechnungen),
        ].map(normalizeKette),
        zugeordnet: asArray<BackendDokumentenKette>(raw?.zugeordnet).map(normalizeKette),
        ausgeblendet: asArray<BackendDokumentenKette>(raw?.ausgeblendet).map(normalizeKette),
    };
};

// ========== Ketten-Komponente ==========
interface KetteCardProps {
    kette: DokumentenKette;
    onOpenPdf: (url: string, title: string) => void;
    showZuordnenButton?: boolean;
    onZuordnen?: (kette: DokumentenKette) => void;
    onAusblenden?: (kette: DokumentenKette) => void;
    onEinblenden?: (kette: DokumentenKette) => void;
    busy?: boolean;
}

function KetteCard({ kette, onOpenPdf, showZuordnenButton, onZuordnen, onAusblenden, onEinblenden, busy }: KetteCardProps) {
    const rechnung = kette.dokumente.find(d => d.typ === 'RECHNUNG');

    return (
        <Card className="p-4 hover:shadow-md transition-shadow">
            {/* Header mit Lieferant */}
            <div className="flex items-center justify-between mb-3">
                <div>
                    <span className="text-sm font-semibold text-slate-900">
                        {kette.lieferantName || 'Unbekannter Lieferant'}
                    </span>
                </div>
                {rechnung?.betragBrutto && (
                    <span className="text-sm font-medium text-slate-600">
                        {formatEuro(rechnung.betragBrutto)} €
                    </span>
                )}
            </div>

            {/* Dokumenten-Kette horizontal */}
            <div className="flex items-center gap-2 flex-wrap mb-3">
                {kette.dokumente.map((dok, idx) => (
                    <div key={dok.id} className="flex items-center">
                        {idx > 0 && <ChevronRight className="w-4 h-4 text-slate-300 mx-1" />}
                        <button
                            onClick={() => dok.pdfUrl && onOpenPdf(dok.pdfUrl, dok.dokumentNummer || dok.dateiname)}
                            disabled={!dok.pdfUrl}
                            className="flex flex-col items-start p-2 rounded-lg border border-slate-200 bg-slate-50 hover:bg-slate-100 transition-colors min-w-[100px] text-left disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <span className="text-xs font-medium text-slate-500 uppercase">
                                {TYP_LABELS[dok.typ]}
                            </span>
                            <span className="text-sm font-semibold text-slate-800 truncate max-w-[120px]">
                                {dok.dokumentNummer || '–'}
                            </span>
                            <span className="text-xs text-slate-500">
                                {formatDate(dok.dokumentDatum)}
                            </span>
                            {dok.typ === 'AUFTRAGSBESTAETIGUNG' && dok.liefertermin && (
                                <span className="text-xs text-rose-600 mt-1 flex items-center gap-1">
                                    <Clock className="w-3 h-3" />
                                    {formatDate(dok.liefertermin)}
                                </span>
                            )}
                        </button>
                    </div>
                ))}
            </div>

            {/* Zuordnen-Button */}
            {showZuordnenButton && onZuordnen && (
                <Button
                    onClick={() => onZuordnen(kette)}
                    size="sm"
                    variant="outline"
                    className="w-full text-slate-600 border-slate-300 hover:bg-slate-50 hover:text-slate-800"
                >
                    <FolderOpen className="w-4 h-4 mr-2" />
                    Projekten zuordnen
                </Button>
            )}

            {/* Ausblenden / Einblenden */}
            {onAusblenden && (
                <Button
                    onClick={() => onAusblenden(kette)}
                    size="sm"
                    variant="ghost"
                    disabled={busy}
                    className="w-full text-slate-500 hover:text-rose-700 hover:bg-rose-50 mt-2"
                >
                    <EyeOff className="w-4 h-4 mr-2" />
                    Ausblenden
                </Button>
            )}
            {onEinblenden && (
                <Button
                    onClick={() => onEinblenden(kette)}
                    size="sm"
                    variant="outline"
                    disabled={busy}
                    className="w-full text-rose-700 border-rose-300 hover:bg-rose-50 mt-2"
                >
                    <Eye className="w-4 h-4 mr-2" />
                    Wieder einblenden
                </Button>
            )}
        </Card>
    );
}

// ========== Tab-Komponente (Projekt-Stil) ==========
interface TabButtonProps {
    active: boolean;
    onClick: () => void;
    icon: React.ReactNode;
    label: string;
    count: number;
}

function TabButton({ active, onClick, icon, label, count }: TabButtonProps) {
    return (
        <button
            onClick={onClick}
            className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap flex items-center gap-2 ${active
                ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                }`}
        >
            {icon}
            {label} ({count})
        </button>
    );
}

// PDF-Vorschau läuft über die globale Komponente ../components/DocumentPreviewModal

interface BelegZuordnungAuswahlModalProps {
    belege: BelegZuordnungRef[];
    loading: boolean;
    onSelect: (beleg: BelegZuordnungRef) => void;
    onClose: () => void;
}

function BelegZuordnungAuswahlModal({ belege, loading, onSelect, onClose }: BelegZuordnungAuswahlModalProps) {
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <Card className="w-full max-w-3xl max-h-[82vh] overflow-hidden bg-white shadow-2xl" onClick={e => e.stopPropagation()}>
                <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
                    <div>
                        <h3 className="font-semibold text-slate-900">Belegkosten zuordnen</h3>
                        <p className="text-sm text-slate-500">Nicht per E-Mail importierte Belege ohne Kostenstelle</p>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-lg hover:bg-slate-100">
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>
                <div className="max-h-[65vh] overflow-auto divide-y divide-slate-100">
                    {loading ? (
                        <div className="p-10 text-center text-slate-500">
                            <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin" />
                            Belege werden geladen...
                        </div>
                    ) : belege.length === 0 ? (
                        <div className="p-10 text-center text-slate-500">Keine offenen Belegkosten vorhanden.</div>
                    ) : (
                        belege.map(beleg => (
                            <button
                                key={beleg.id}
                                onClick={() => onSelect(beleg)}
                                className="w-full text-left p-4 hover:bg-rose-50 transition flex items-center justify-between gap-4"
                            >
                                <div className="min-w-0">
                                    <div className="font-medium text-slate-900 truncate">
                                        {beleg.belegNummer || beleg.originalDateiname || `Beleg #${beleg.id}`}
                                    </div>
                                    <div className="text-sm text-slate-500 truncate">
                                        {beleg.lieferantName || 'Kein Lieferant'} · {formatDate(beleg.belegDatum)} · {beleg.beschreibung || 'Keine Beschreibung'}
                                    </div>
                                </div>
                                <div className="text-right shrink-0">
                                    <div className="font-semibold text-slate-900">{formatEuro(beleg.betragNetto)} €</div>
                                    <div className="text-xs text-slate-400">Netto</div>
                                </div>
                            </button>
                        ))
                    )}
                </div>
            </Card>
        </div>
    );
}

// ========== Zuordnungs-Modal ==========
interface ZuordnungModalProps {
    kette: DokumentenKette;
    onClose: () => void;
    onSuccess: () => void;
}

function ZuordnungModal({ kette, onClose, onSuccess }: ZuordnungModalProps) {
    const toast = useToast();
    const rechnung = kette.dokumente.find(d => d.typ === 'RECHNUNG');
    const [geschaeftsdaten, setGeschaeftsdaten] = useState<GeschaeftsdatenDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [modus, setModus] = useState<'prozent' | 'absolut'>('prozent');
    const [anteile, setAnteile] = useState<ProjektAnteil[]>([]);
    const [showProjectModal, setShowProjectModal] = useState(false);
    const [showKostenstelleModal, setShowKostenstelleModal] = useState(false);

    // Geschäftsdaten laden
    useEffect(() => {
        if (!rechnung) return;
        setLoading(true);
        fetch(`/api/bestellungen-uebersicht/geschaeftsdaten/${rechnung.id}`)
            .then(res => res.json())
            .then(data => setGeschaeftsdaten(data))
            .catch(() => { })
            .finally(() => setLoading(false));
    }, [rechnung]);

    // Projekt hinzufügen (vom Modal aufgerufen)
    const addProjekt = (p: { id: number; bauvorhaben: string }) => {
        if (anteile.find(a => a.projektId === p.id)) {
            setShowProjectModal(false);
            return;
        }
        const defaultAnteil = anteile.length === 0 ? 100 : 0;
        const defaultBetrag = geschaeftsdaten?.betragNetto || 0;
        setAnteile([...anteile, {
            projektId: p.id,
            projektName: p.bauvorhaben,
            prozentanteil: defaultAnteil,
            betrag: modus === 'prozent' ? (defaultBetrag * defaultAnteil / 100) : 0,
            beschreibung: ''
        }]);
        setShowProjectModal(false);
    };

    // Kostenstelle hinzufügen (vom Modal aufgerufen)
    const addKostenstelle = (k: { id: number; bezeichnung: string }) => {
        if (anteile.find(a => a.kostenstelleId === k.id)) {
            setShowKostenstelleModal(false);
            return;
        }

        const defaultAnteil = anteile.length === 0 ? 100 : 0;
        const defaultBetrag = geschaeftsdaten?.betragNetto || 0;
        setAnteile([...anteile, {
            kostenstelleId: k.id,
            kostenstelleName: k.bezeichnung,
            prozentanteil: defaultAnteil,
            betrag: modus === 'prozent' ? (defaultBetrag * defaultAnteil / 100) : 0,
            beschreibung: '',
            streckungJahre: 1
        }]);
        setShowKostenstelleModal(false);
    };

    // Anteil aktualisieren (nur für nicht-letzte Einträge)
    const updateAnteil = (idx: number, field: keyof ProjektAnteil, value: number | string) => {
        const gesamt = geschaeftsdaten?.betragNetto || 0;
        
        setAnteile(prev => prev.map((a, i) => {
            if (i !== idx) return a;
            const newA = { ...a, [field]: value };
            if (modus === 'prozent' && field === 'prozentanteil') {
                newA.betrag = Number(((gesamt * Number(value)) / 100).toFixed(2));
            } else if (modus === 'absolut' && field === 'betrag') {
                newA.prozentanteil = gesamt > 0 ? Number(((Number(value) / gesamt) * 100).toFixed(2)) : 0;
            }
            return newA;
        }));
    };

    // Berechne die Werte für den letzten Eintrag (Differenz zu 100% bzw. Gesamtbetrag)
    const berechneLetzenAnteil = () => {
        if (anteile.length < 2) return null;
        const gesamt = geschaeftsdaten?.betragNetto || 0;
        const lastIdx = anteile.length - 1;
        
        // Summe aller Einträge AUSSER dem letzten
        const sumProzentOhneLetzen = anteile.slice(0, lastIdx).reduce((s, a) => s + (a.prozentanteil || 0), 0);
        const sumBetragOhneLetzen = anteile.slice(0, lastIdx).reduce((s, a) => s + a.betrag, 0);
        
        const restProzent = Number((100 - sumProzentOhneLetzen).toFixed(2));
        const restBetrag = Number((gesamt - sumBetragOhneLetzen).toFixed(2));
        
        return { restProzent, restBetrag };
    };
    
    const letzterAnteilBerechnet = berechneLetzenAnteil();

    // Entfernen
    const removeAnteil = (idx: number) => {
        setAnteile(prev => prev.filter((_, i) => i !== idx));
    };

    // Berechne Summen
    const sumBetrag = anteile.reduce((s, a, idx) => {
        const isLast = idx === anteile.length - 1 && anteile.length >= 2;
        return s + (isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restBetrag : a.betrag);
    }, 0);
    const sumProzent = anteile.reduce((s, a, idx) => {
        const isLast = idx === anteile.length - 1 && anteile.length >= 2;
        return s + (isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restProzent : (a.prozentanteil || 0));
    }, 0);
    const rest = (geschaeftsdaten?.betragNetto || 0) - sumBetrag;
    const rechnungsJahr = geschaeftsdaten?.dokumentDatum
        ? new Date(geschaeftsdaten.dokumentDatum).getFullYear()
        : new Date().getFullYear();

    // Speichern - mit berechneten Werten für den letzten Eintrag
    const speichern = async () => {
        if (!rechnung || anteile.length === 0) return;
        setSaving(true);
        try {
            // Für den letzten Eintrag die berechneten Werte verwenden
            const anteileZumSpeichern = anteile.map((a, idx) => {
                const isLast = idx === anteile.length - 1 && anteile.length >= 2;
                const betrag = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restBetrag : a.betrag;
                const prozentanteil = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restProzent : a.prozentanteil;
                return {
                    projektId: a.projektId,
                    kostenstelleId: a.kostenstelleId,
                    betrag: modus === 'absolut' ? betrag : null,
                    prozentanteil: modus === 'absolut' ? null : prozentanteil,
                    beschreibung: a.beschreibung,
                    // Streckung nur bei Kostenstellen mitschicken (Projekte bleiben einmalig)
                    streckungJahre: a.kostenstelleId != null ? (a.streckungJahre && a.streckungJahre > 1 ? a.streckungJahre : 1) : undefined
                };
            });

            const res = await fetch('/api/bestellungen-uebersicht/zuordnen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    geschaeftsdokumentId: rechnung.id,
                    frontendUserProfileId: (() => {
                        try {
                            const stored = localStorage.getItem('frontendUserSelection');
                            if (stored) {
                                const parsed = JSON.parse(stored);
                                return parsed.id || null;
                            }
                        } catch { /* ignore */ }
                        return null;
                    })(),
                    projektAnteile: anteileZumSpeichern
                })
            });
            if (res.ok) {
                onSuccess();
                onClose();
            } else {
                toast.error('Fehler beim Speichern');
            }
        } catch {
            toast.error('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // ESC Handler
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    if (!rechnung) return null;

    // Find PDF URL from rechnung
    const pdfUrl = rechnung.pdfUrl;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
            <div className="relative bg-white rounded-xl shadow-2xl w-full h-full mx-10 max-h-[90vh] overflow-hidden flex flex-col" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900">
                            Rechnung {geschaeftsdaten?.dokumentNummer || rechnung.dokumentNummer || '–'}
                        </h3>
                        <p className="text-sm text-slate-500">{kette.lieferantName}</p>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-lg hover:bg-rose-100 transition">
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                {/* Content - Split Layout */}
                <div className="flex-1 overflow-hidden flex">
                    {/* Left: PDF Preview */}
                    <div className="w-1/2 border-r border-slate-200 bg-slate-100 flex flex-col">
                        <div className="px-4 py-2 bg-slate-200 border-b border-slate-300 flex items-center justify-between">
                            <span className="text-sm font-medium text-slate-700">PDF-Vorschau</span>
                            {pdfUrl && (
                                <a
                                    href={pdfUrl}
                                    download
                                    className="text-xs px-2 py-1 bg-white rounded border border-slate-300 text-slate-600 hover:bg-slate-50 flex items-center gap-1"
                                >
                                    <Download className="w-3 h-3" />
                                    Download
                                </a>
                            )}
                        </div>
                        <div className="flex-1 overflow-auto p-4">
                            {pdfUrl ? (
                                <PdfCanvasViewer url={pdfUrl} className="w-full h-full min-h-[500px] rounded-lg overflow-y-auto overflow-x-hidden" />
                            ) : (
                                <div className="flex flex-col items-center justify-center h-full text-slate-400">
                                    <FileText className="w-12 h-12 mb-3" />
                                    <p>Keine PDF-Vorschau verfügbar</p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Right: Assignment Form */}
                    <div className="w-1/2 overflow-auto p-6 space-y-6">
                        {loading ? (
                            <div className="flex items-center justify-center py-12">
                                <RefreshCw className="w-8 h-8 animate-spin text-slate-400" />
                            </div>
                        ) : (
                            <>
                                {/* Metadaten */}
                                <div className="bg-slate-50 rounded-xl p-4">
                                    <h4 className="text-sm font-semibold text-slate-700 mb-3">Rechnungsdaten</h4>
                                    <div className="grid grid-cols-2 gap-4 text-sm">
                                        <div>
                                            <span className="text-slate-500">Rechnungsnr.:</span>
                                            <p className="font-medium">{geschaeftsdaten?.dokumentNummer || '–'}</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Datum:</span>
                                            <p className="font-medium">{formatDate(geschaeftsdaten?.dokumentDatum || null)}</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Netto:</span>
                                            <p className="font-medium text-rose-600">{formatEuro(geschaeftsdaten?.betragNetto || null)} €</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Brutto:</span>
                                            <p className="font-medium">{formatEuro(geschaeftsdaten?.betragBrutto || null)} €</p>
                                        </div>
                                    </div>
                                </div>

                                {/* Modus-Toggle */}
                                <div className="flex items-center gap-4">
                                    <span className="text-sm font-medium text-slate-700">Verteilungsmodus:</span>
                                    <div className="flex bg-slate-100 rounded-lg p-1">
                                        <button
                                            onClick={() => setModus('prozent')}
                                            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium transition ${modus === 'prozent' ? 'bg-white shadow text-rose-600' : 'text-slate-500'}`}
                                        >
                                            <Percent className="w-4 h-4" />
                                            Prozentual
                                        </button>
                                        <button
                                            onClick={() => setModus('absolut')}
                                            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium transition ${modus === 'absolut' ? 'bg-white shadow text-rose-600' : 'text-slate-500'}`}
                                        >
                                            <Euro className="w-4 h-4" />
                                            Absolut
                                        </button>
                                    </div>
                                </div>

                                {/* Projekt-Suche & Kostenstelle Buttons */}
                                <div className="flex gap-2">
                                    <Button
                                        variant="outline"
                                        onClick={() => setShowProjectModal(true)}
                                        className="flex-1 justify-start gap-2 text-slate-600 hover:text-rose-700 hover:border-rose-300"
                                    >
                                        <Briefcase className="w-4 h-4" />
                                        Projekt hinzufügen...
                                    </Button>
                                    
                                    <Button
                                        variant="outline"
                                        onClick={() => setShowKostenstelleModal(true)}
                                        className="flex-1 justify-start gap-2 text-slate-600 hover:text-rose-700 hover:border-rose-300"
                                    >
                                        <Package className="w-4 h-4" />
                                        Kostenstelle...
                                    </Button>
                                </div>

                                {/* ProjectSelectModal */}
                                {showProjectModal && (
                                    <ProjectSelectModal
                                        onSelect={addProjekt}
                                        onClose={() => setShowProjectModal(false)}
                                    />
                                )}

                                {/* KostenstelleSelectModal */}
                                {showKostenstelleModal && (
                                    <KostenstelleSelectModal
                                        onSelect={addKostenstelle}
                                        onClose={() => setShowKostenstelleModal(false)}
                                    />
                                )}

                                {/* Zuordnungsliste */}
                                {anteile.length === 0 ? (
                                    <div className="text-center py-8 text-slate-400 border-2 border-dashed rounded-xl">
                                        <Plus className="w-8 h-8 mx-auto mb-2" />
                                        <p>Klicken Sie oben, um Projekte zuzuordnen</p>
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        {anteile.map((a, idx) => {
                                            const isLast = idx === anteile.length - 1 && anteile.length >= 2;
                                            // Für den letzten Eintrag: berechnete Werte verwenden
                                            const displayProzent = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restProzent : (a.prozentanteil || 0);
                                            const displayBetrag = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restBetrag : a.betrag;
                                            
                                            return (
                                                <div key={idx} className={`rounded-xl p-4 border ${isLast ? 'bg-rose-50 border-rose-200' : 'bg-slate-50 border-slate-200'}`}>
                                                    <div className="flex items-start justify-between mb-3">
                                                        <div>
                                                            <p className="font-medium text-slate-900">
                                                                {a.projektName || a.kostenstelleName}
                                                            </p>
                                                            <div className="flex gap-1 mt-0.5">
                                                                {a.kostenstelleId && (
                                                                    <span className="text-xs bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded">
                                                                        Kostenstelle
                                                                    </span>
                                                                )}
                                                                {isLast && (
                                                                    <span className="text-xs bg-rose-200 text-rose-700 px-1.5 py-0.5 rounded">
                                                                        Automatisch (Rest)
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                        <button onClick={() => removeAnteil(idx)} className="text-slate-400 hover:text-red-500">
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                    <div className="grid grid-cols-3 gap-3">
                                                        <div>
                                                            <label className="text-xs text-slate-500">{modus === 'prozent' ? 'Anteil %' : 'Betrag €'}</label>
                                                            {isLast ? (
                                                                <p className={`px-3 py-1.5 rounded-lg text-sm font-medium ${displayProzent < 0 || displayBetrag < 0 ? 'bg-red-100 text-red-700' : 'bg-rose-100 text-rose-700'}`}>
                                                                    {modus === 'prozent' ? displayProzent.toFixed(2) : formatEuro(displayBetrag)}
                                                                </p>
                                                            ) : (
                                                                <input
                                                                    type="number"
                                                                    value={modus === 'prozent' ? (a.prozentanteil || 0) : a.betrag}
                                                                    onChange={e => updateAnteil(idx, modus === 'prozent' ? 'prozentanteil' : 'betrag', Number(e.target.value))}
                                                                    className="w-full px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-rose-500"
                                                                />
                                                            )}
                                                        </div>
                                                        <div>
                                                            <label className="text-xs text-slate-500">= {modus === 'prozent' ? 'Betrag' : 'Prozent'}</label>
                                                            <p className={`px-3 py-1.5 rounded-lg text-sm font-medium ${isLast ? 'bg-rose-100' : 'bg-slate-100'}`}>
                                                                {modus === 'prozent' ? `${formatEuro(displayBetrag)} €` : `${displayProzent.toFixed(1)} %`}
                                                            </p>
                                                        </div>
                                                        <div>
                                                            <label className="text-xs text-slate-500">Beschreibung</label>
                                                            <input
                                                                type="text"
                                                                value={a.beschreibung}
                                                                onChange={e => updateAnteil(idx, 'beschreibung', e.target.value)}
                                                                placeholder="z.B. Dachlatten"
                                                                className="w-full px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-rose-500"
                                                            />
                                                        </div>
                                                    </div>

                                                    {/* Kosten über mehrere Jahre verteilen – nur für Kostenstellen */}
                                                    {a.kostenstelleId != null && (() => {
                                                        const jahre = a.streckungJahre && a.streckungJahre > 0 ? a.streckungJahre : 1;
                                                        const proJahr = jahre > 1 ? displayBetrag / jahre : displayBetrag;
                                                        return (
                                                            <div className="mt-3 pt-3 border-t border-slate-200 flex flex-wrap items-center gap-2">
                                                                <span className="text-xs text-slate-600">Kosten verteilen über</span>
                                                                <input
                                                                    type="number"
                                                                    min={1}
                                                                    max={20}
                                                                    value={jahre}
                                                                    onChange={e => updateAnteil(idx, 'streckungJahre', Math.max(1, Math.min(20, Number(e.target.value) || 1)))}
                                                                    className="w-16 px-2 py-1 border border-slate-200 rounded-lg text-sm text-center focus:ring-2 focus:ring-rose-500"
                                                                />
                                                                <span className="text-xs text-slate-600">{jahre === 1 ? 'Jahr' : 'Jahre'}</span>
                                                                {jahre > 1 && (
                                                                    <span className="text-xs text-rose-600 font-medium ml-1">
                                                                        ≈ {formatEuro(proJahr)} € / Jahr ab {rechnungsJahr}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        );
                                                    })()}
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}

                                {/* Zusammenfassung */}
                                {anteile.length > 0 && (
                                    <div className={`rounded-xl p-4 ${Math.abs(rest) < 0.01 ? 'bg-green-50 border border-green-200' : 'bg-amber-50 border border-amber-200'}`}>
                                        <div className="flex justify-between text-sm">
                                            <span>Zugeordnet:</span>
                                            <span className="font-medium">{formatEuro(sumBetrag)} € ({sumProzent.toFixed(1)} %)</span>
                                        </div>
                                        <div className="flex justify-between text-sm mt-1">
                                            <span>Gesamt:</span>
                                            <span className="font-medium">{formatEuro(geschaeftsdaten?.betragNetto || null)} €</span>
                                        </div>
                                        <div className="flex justify-between text-sm mt-1 font-semibold">
                                            <span>Rest:</span>
                                            <span className={Math.abs(rest) < 0.01 ? 'text-green-600' : 'text-amber-600'}>
                                                {formatEuro(rest)} €
                                            </span>
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div className="flex justify-end gap-3 px-6 py-4 border-t border-slate-200 bg-slate-50">
                    <Button variant="outline" onClick={onClose}>Abbrechen</Button>
                    <Button
                        onClick={speichern}
                        disabled={saving || anteile.length === 0}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {saving ? <RefreshCw className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
                        Zuordnen & Abschließen
                    </Button>
                </div>
            </div>
        </div>
    );
}

// ========== Hauptkomponente ==========
export default function BestellungenUebersicht() {
    const toast = useToast();
    const [tab, setTab] = useState<'offen' | 'laufend' | 'abgeschlossen' | 'zugeordnet' | 'ausgeblendet'>('laufend');
    const [data, setData] = useState<BestellungsUebersicht | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [busyKetteId, setBusyKetteId] = useState<string | null>(null);
    const [offeneBelege, setOffeneBelege] = useState<BelegZuordnungRef[]>([]);
    const [offeneBelegeLoading, setOffeneBelegeLoading] = useState(false);
    const [showBelegAuswahl, setShowBelegAuswahl] = useState(false);
    const [selectedBeleg, setSelectedBeleg] = useState<BelegZuordnungRef | null>(null);

    // PDF Preview State
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewTitle, setPreviewTitle] = useState('');

    // Zuordnung Modal State
    const [zuordnungKette, setZuordnungKette] = useState<DokumentenKette | null>(null);

    const loadData = useCallback(async (silent = false) => {
        if (!silent) setLoading(true);
        setError(null);
        try {
            const res = await fetch('/api/bestellungen-uebersicht');
            if (!res.ok) throw new Error('Fehler beim Laden');
            const json = await res.json();
            setData(normalizeUebersicht(json));
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unbekannter Fehler');
        } finally {
            if (!silent) setLoading(false);
        }
    }, []);

    const loadOffeneBelege = useCallback(async () => {
        setOffeneBelegeLoading(true);
        try {
            const res = await fetch('/api/bestellungen-uebersicht/belege-offen');
            if (res.status === 401 || res.status === 403) {
                setOffeneBelege([]);
                return;
            }
            if (!res.ok) throw new Error('Fehler beim Laden');
            const json = await res.json();
            setOffeneBelege(Array.isArray(json) ? json : []);
        } catch {
            toast.error('Belege konnten nicht geladen werden');
            setOffeneBelege([]);
        } finally {
            setOffeneBelegeLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        loadData();
        void loadOffeneBelege();
    }, [loadData, loadOffeneBelege]);

    const handleOpenPdf = (url: string, title: string) => {
        setPreviewUrl(url);
        setPreviewTitle(title);
    };

    const [bulkBusy, setBulkBusy] = useState(false);

    const alleZugeordnetAusblenden = useCallback(async () => {
        if (!data || data.zugeordnet.length === 0) return;
        const ketten = data.zugeordnet;
        if (!window.confirm(`Möchten Sie wirklich alle ${ketten.length} zugeordneten Bestellungen ausblenden?`)) {
            return;
        }

        setBulkBusy(true);

        // Optimistic Update: Zugeordnete sofort in Ausgeblendet verschieben
        setData(prev => prev ? {
            ...prev,
            zugeordnet: [],
            ausgeblendet: [...ketten, ...prev.ausgeblendet],
        } : prev);

        const allDokumentIds = ketten.flatMap(k => k.dokumente.map(d => d.id));
        const CHUNK_SIZE = 500; // Backend-Limit aus AusblendenRequest

        try {
            for (let i = 0; i < allDokumentIds.length; i += CHUNK_SIZE) {
                const chunk = allDokumentIds.slice(i, i + CHUNK_SIZE);
                const res = await fetch('/api/bestellungen-uebersicht/ausblenden', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ dokumentIds: chunk }),
                });
                if (!res.ok) throw new Error('Fehler');
            }
            await loadData(true);
            toast.success(`${ketten.length} Bestellungen ausgeblendet`);
        } catch {
            toast.error('Aktion fehlgeschlagen');
            await loadData(true);
        } finally {
            setBulkBusy(false);
        }
    }, [data, loadData, toast]);

    const setKetteAusgeblendet = useCallback(async (kette: DokumentenKette, ausblenden: boolean) => {
        setBusyKetteId(kette.id);

        // Optimistic Update: Kette sofort verschieben, damit kein Ganzseiten-Spinner nötig ist
        setData(prev => {
            if (!prev) return prev;
            if (ausblenden) {
                return {
                    offeneAnfragen: prev.offeneAnfragen.filter(k => k.id !== kette.id),
                    laufendeBestellungen: prev.laufendeBestellungen.filter(k => k.id !== kette.id),
                    abgeschlossen: prev.abgeschlossen.filter(k => k.id !== kette.id),
                    zugeordnet: prev.zugeordnet.filter(k => k.id !== kette.id),
                    ausgeblendet: [kette, ...prev.ausgeblendet.filter(k => k.id !== kette.id)],
                };
            }
            return {
                ...prev,
                ausgeblendet: prev.ausgeblendet.filter(k => k.id !== kette.id),
            };
        });

        try {
            const res = await fetch(`/api/bestellungen-uebersicht/${ausblenden ? 'ausblenden' : 'einblenden'}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ dokumentIds: kette.dokumente.map(d => d.id) }),
            });
            if (!res.ok) throw new Error('Fehler');
            // Silent reload, damit beim Einblenden die Kette in die richtige Liste rutscht
            await loadData(true);
            toast.success(ausblenden ? 'Ausgeblendet' : 'Wieder eingeblendet');
        } catch {
            toast.error('Aktion fehlgeschlagen');
            // Server-Stand wiederherstellen, falls Optimistic Update falsch lag
            await loadData(true);
        } finally {
            setBusyKetteId(null);
        }
    }, [loadData, toast]);

    const currentList = tab === 'offen'
        ? data?.offeneAnfragen
        : tab === 'laufend'
            ? data?.laufendeBestellungen
            : tab === 'abgeschlossen'
                ? data?.abgeschlossen
                : tab === 'zugeordnet'
                    ? data?.zugeordnet
                    : data?.ausgeblendet;

    return (
        <div className="p-6 space-y-6 bg-slate-50 min-h-screen">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Einkauf
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        BESTELLUNGEN
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Übersicht aller Lieferanten-Dokumente nach Bestellstatus
                    </p>
                </div>
                <div className="flex flex-col sm:flex-row gap-2">
                    <Button
                        onClick={() => {
                            setShowBelegAuswahl(true);
                            void loadOffeneBelege();
                        }}
                        variant="outline"
                        size="sm"
                        className="gap-2 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                    >
                        <FileText className="w-4 h-4" />
                        Belegkosten zuordnen ({offeneBelege.length})
                    </Button>
                    <Button
                        onClick={() => loadData()}
                        disabled={loading}
                        variant="outline"
                        size="sm"
                        className="gap-2"
                    >
                        <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                        Aktualisieren
                    </Button>
                </div>
            </div>

            {/* Tabs - Projekt-Stil */}
            <div className="flex gap-2 border-b border-slate-200 pb-2 overflow-x-auto">
                <TabButton
                    active={tab === 'offen'}
                    onClick={() => setTab('offen')}
                    icon={<AlertCircle className="w-4 h-4" />}
                    label="Offene Anfragen"
                    count={data?.offeneAnfragen.length || 0}
                />
                <TabButton
                    active={tab === 'laufend'}
                    onClick={() => setTab('laufend')}
                    icon={<Package className="w-4 h-4" />}
                    label="Laufende Bestellungen"
                    count={data?.laufendeBestellungen.length || 0}
                />
                <TabButton
                    active={tab === 'abgeschlossen'}
                    onClick={() => setTab('abgeschlossen')}
                    icon={<CheckCircle className="w-4 h-4" />}
                    label="Abgeschlossen"
                    count={data?.abgeschlossen.length || 0}
                />
                <TabButton
                    active={tab === 'zugeordnet'}
                    onClick={() => setTab('zugeordnet')}
                    icon={<FolderOpen className="w-4 h-4" />}
                    label="Zugeordnet"
                    count={data?.zugeordnet.length || 0}
                />
                <TabButton
                    active={tab === 'ausgeblendet'}
                    onClick={() => setTab('ausgeblendet')}
                    icon={<Archive className="w-4 h-4" />}
                    label="Ausgeblendet"
                    count={data?.ausgeblendet.length || 0}
                />
            </div>

            {/* Content */}
            {loading ? (
                <div className="flex items-center justify-center py-12">
                    <RefreshCw className="w-8 h-8 animate-spin text-slate-400" />
                </div>
            ) : error ? (
                <Card className="p-6 text-center text-red-600">
                    <AlertCircle className="w-8 h-8 mx-auto mb-2" />
                    {error}
                </Card>
            ) : !currentList || currentList.length === 0 ? (
                <Card className="p-12 text-center text-slate-500 border-dashed">
                    <FileText className="w-12 h-12 mx-auto mb-4 text-slate-300" />
                    <p className="text-lg font-medium">Keine Einträge</p>
                    <p className="text-sm mt-1">
                        {tab === 'offen' && 'Keine offenen Anfragen vorhanden.'}
                        {tab === 'laufend' && 'Keine laufenden Bestellungen vorhanden.'}
                        {tab === 'abgeschlossen' && 'Keine abgeschlossenen Bestellungen zum Zuordnen.'}
                        {tab === 'zugeordnet' && 'Noch keine Bestellungen Projekten zugeordnet.'}
                        {tab === 'ausgeblendet' && 'Keine ausgeblendeten Einträge.'}
                    </p>
                </Card>
            ) : (
                <div className="space-y-4">
                    {tab === 'zugeordnet' && currentList.length > 0 && (
                        <div className="flex justify-end">
                            <Button
                                onClick={alleZugeordnetAusblenden}
                                disabled={bulkBusy}
                                variant="outline"
                                size="sm"
                                className="text-slate-600 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                            >
                                {bulkBusy ? (
                                    <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                                ) : (
                                    <EyeOff className="w-4 h-4 mr-2" />
                                )}
                                Alle ausblenden ({currentList.length})
                            </Button>
                        </div>
                    )}
                    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                        {(() => {
                            const istAusgeblendetTab = tab === 'ausgeblendet';
                            return currentList.map(kette => (
                                <KetteCard
                                    key={kette.id}
                                    kette={kette}
                                    onOpenPdf={handleOpenPdf}
                                    showZuordnenButton={tab === 'abgeschlossen'}
                                    onZuordnen={setZuordnungKette}
                                    onAusblenden={istAusgeblendetTab ? undefined : (k) => setKetteAusgeblendet(k, true)}
                                    onEinblenden={istAusgeblendetTab ? (k) => setKetteAusgeblendet(k, false) : undefined}
                                    busy={busyKetteId === kette.id}
                                />
                            ));
                        })()}
                    </div>
                </div>
            )}

            {/* PDF Preview Modal */}
            {previewUrl && (
                <DocumentPreviewModal
                    doc={{ url: previewUrl, title: previewTitle }}
                    onClose={() => setPreviewUrl(null)}
                />
            )}

            {showBelegAuswahl && (
                <BelegZuordnungAuswahlModal
                    belege={offeneBelege}
                    loading={offeneBelegeLoading}
                    onClose={() => setShowBelegAuswahl(false)}
                    onSelect={(beleg) => {
                        setSelectedBeleg(beleg);
                        setShowBelegAuswahl(false);
                    }}
                />
            )}

            {selectedBeleg && (
                <BelegZuordnungModal
                    belegId={selectedBeleg.id}
                    dokumentNummer={selectedBeleg.belegNummer || selectedBeleg.originalDateiname}
                    lieferantName={selectedBeleg.lieferantName}
                    pdfUrl={selectedBeleg.pdfUrl}
                    previewMimeType={selectedBeleg.mimeType}
                    onClose={() => setSelectedBeleg(null)}
                    onSuccess={() => {
                        setSelectedBeleg(null);
                        void loadOffeneBelege();
                        void loadData(true);
                    }}
                />
            )}

            {/* Zuordnung Modal */}
            {zuordnungKette && (
                <ZuordnungModal
                    kette={zuordnungKette}
                    onClose={() => setZuordnungKette(null)}
                    onSuccess={() => loadData()}
                />
            )}
        </div>
    );
}
