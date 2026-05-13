import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Receipt, Upload, Loader2, Search, Wallet, Banknote, CreditCard,
    Coins, FileQuestion, CheckCircle2, AlertCircle, Trash2, X, Truck,
    Save, RefreshCw, FileText, BookOpen, BarChart3, ArrowRightLeft, FileInput,
    FileDown, Calendar, ArrowRight,
} from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../components/LieferantSearchModal';
import { SteuerberaterBelegExportModal } from '../components/SteuerberaterBelegExportModal';
import { KasseShortcuts } from '../components/kasse/KasseShortcuts';
import { KostenstellenSplitsEditor, type KostenstellenSplit } from '../components/kasse/KostenstellenSplitsEditor';

// ===================== Types =====================

type BelegStatus = 'NEU' | 'VALIDIERT' | 'VERWORFEN';
type SachkontoTyp = 'AUFWAND' | 'ERTRAG' | 'PRIVAT' | 'NEUTRAL';

interface Sachkonto {
    id: number;
    nummer?: string | null;
    bezeichnung: string;
    kontoTyp: SachkontoTyp;
    beschreibung?: string | null;
    aktiv: boolean;
    sortierung: number;
}

interface Zahlungsart {
    id: number;
    bezeichnung: string;
    aktiv: boolean;
    sortierung: number;
}

interface AuswertungZeile {
    sachkontoId: number | null;
    nummer?: string | null;
    bezeichnung: string;
    kontoTyp: SachkontoTyp | null;
    summe: number;
    anzahlBelege: number;
}

interface Auswertung {
    von: string | null;
    bis: string | null;
    summeAufwand: number;
    summeErtrag: number;
    summePrivat: number;
    summeOhneKonto: number;
    zeilen: AuswertungZeile[];
}
type BelegKategorie =
    | 'UNZUGEORDNET'
    | 'KASSE_EINNAHME'
    | 'KASSE_AUSGABE'
    | 'PRIVATENTNAHME'
    | 'PRIVATEINLAGE'
    | 'BANK'
    | 'KREDITKARTE'
    | 'SONSTIGER_BELEG';
type KiStatus = 'PENDING' | 'LAEUFT' | 'DONE' | 'FAILED';

type AufteilungsModus = 'VOLLSTAENDIG' | 'TEILWEISE';

interface BelegPosition {
    id: number;
    sortierung: number;
    beschreibung?: string | null;
    menge?: number | null;
    einheit?: string | null;
    einzelpreis?: number | null;
    betragNetto?: number | null;
    betragBrutto?: number | null;
    mwstSatz?: number | null;
    istFuerFirma: boolean;
}

interface Beleg {
    id: number;
    belegKategorie: BelegKategorie;
    dokumentTyp?: string | null;
    istUmbuchung?: boolean | null;
    status: BelegStatus;
    kiAnalyseStatus: KiStatus;
    belegDatum?: string | null;
    belegNummer?: string | null;
    beschreibung?: string | null;
    betragNetto?: number | null;
    betragBrutto?: number | null;
    mwstSatz?: number | null;
    zahlungsart?: string | null;
    lieferantId?: number | null;
    lieferantName?: string | null;
    sachkontoId?: number | null;
    sachkontoBezeichnung?: string | null;
    sachkontoNummer?: string | null;
    sachkontoTyp?: SachkontoTyp | null;
    kiVorgeschlagenerLieferant?: string | null;
    kiConfidence?: number | null;
    kiFehlerText?: string | null;
    originalDateiname?: string | null;
    mimeType?: string | null;
    uploadDatum: string;
    uploadedByName?: string | null;
    validiertAm?: string | null;
    validiertVonName?: string | null;
    notiz?: string | null;
    eingangsrechnungId?: number | null;
    // Beleg-Aufteilung (Issue #58): bei TEILWEISE ist nur ein Teil des Belegs
    // betrieblich. Die Firma-Felder sind dann gefuellt; bei VOLLSTAENDIG null.
    aufteilungsModus?: AufteilungsModus | null;
    betragFirmaNetto?: number | null;
    betragFirmaBrutto?: number | null;
    betragFirmaMwst?: number | null;
    positionen?: BelegPosition[] | null;
    // Issue #60: Kostenstellen-Splits (mehrere Kostenstellen pro Beleg).
    kostenstellenSplits?: KostenstellenSplit[] | null;
}

interface KassenBewegung {
    belegId: number;
    datum: string;
    kategorie: BelegKategorie;
    beschreibung?: string | null;
    lieferantName?: string | null;
    betrag: number;
    saldoNachher: number;
}

interface Kassenbuch {
    saldoStart: number;
    saldoEnde: number;
    summeEinnahmen: number;
    summeAusgaben: number;
    summePrivatentnahmen: number;
    summePrivateinlagen: number;
    bewegungen: KassenBewegung[];
}

// ===================== Helpers =====================

const KATEGORIE_LABELS: Record<BelegKategorie, string> = {
    UNZUGEORDNET: 'Noch nicht zugeordnet',
    KASSE_EINNAHME: 'Kasse – Einnahme',
    KASSE_AUSGABE: 'Kasse – Ausgabe',
    PRIVATENTNAHME: 'Privatentnahme',
    PRIVATEINLAGE: 'Privateinlage',
    BANK: 'Bank',
    KREDITKARTE: 'Kreditkarte',
    SONSTIGER_BELEG: 'Sonstiger Beleg',
};

const KATEGORIE_FARBE: Record<BelegKategorie, string> = {
    UNZUGEORDNET: 'bg-slate-100 text-slate-600',
    KASSE_EINNAHME: 'bg-emerald-100 text-emerald-700',
    KASSE_AUSGABE: 'bg-amber-100 text-amber-700',
    PRIVATENTNAHME: 'bg-fuchsia-100 text-fuchsia-700',
    PRIVATEINLAGE: 'bg-lime-100 text-lime-700',
    BANK: 'bg-sky-100 text-sky-700',
    KREDITKARTE: 'bg-violet-100 text-violet-700',
    SONSTIGER_BELEG: 'bg-slate-100 text-slate-700',
};

const formatEuro = (v: number | null | undefined): string =>
    v == null || !Number.isFinite(v)
        ? '–'
        : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);

const formatDate = (iso?: string | null): string => {
    if (!iso) return '–';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '–' : d.toLocaleDateString('de-DE');
};

const formatDateTime = (iso?: string | null): string => {
    if (!iso) return '–';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '–' : d.toLocaleString('de-DE');
};

const SACHKONTO_TYP_LABEL: Record<SachkontoTyp, string> = {
    AUFWAND: 'Aufwand', ERTRAG: 'Ertrag', PRIVAT: 'Privat', NEUTRAL: 'Neutral',
};

// Flacht Sachkonten in Optionen ab und gruppiert sie ueber ein Praefix im Label,
// damit die Pflicht-<Select>-Komponente (ohne optgroup-Support) verwendet werden
// kann. Reihenfolge: Aufwand, Ertrag, Privat, Neutral – innerhalb sortiert nach
// `sortierung`. Der fuehrende Leer-Eintrag erlaubt das aktive Abwaehlen eines
// Kontos.
function buildSachkontoOptions(sachkonten: Sachkonto[]): { value: string; label: string }[] {
    const order: SachkontoTyp[] = ['AUFWAND', 'ERTRAG', 'PRIVAT', 'NEUTRAL'];
    const grouped = order.flatMap(typ =>
        sachkonten
            .filter(s => s.kontoTyp === typ)
            .sort((a, b) => a.sortierung - b.sortierung)
            .map(s => ({
                value: String(s.id),
                label: `${SACHKONTO_TYP_LABEL[typ]} · ${s.nummer ? `${s.nummer} ` : ''}${s.bezeichnung}`,
            }))
    );
    return [{ value: '', label: '– kein Konto –' }, ...grouped];
}

// Baut die Optionen fuer den Zahlungsart-<Select> aus den Stammdaten. Der
// fuehrende Leer-Eintrag erlaubt das aktive Loeschen der Zahlungsart bei
// bestehenden Belegen. `bestehenderWert` wird zusaetzlich aufgenommen, falls
// ein Altbeleg eine Bezeichnung enthaelt, die (noch) nicht in den Stammdaten
// steht — sonst waere der Select-Wert "verschwunden".
function buildZahlungsartOptions(
    zahlungsarten: Zahlungsart[],
    bestehenderWert?: string | null,
): { value: string; label: string }[] {
    const sorted = [...zahlungsarten]
        .sort((a, b) => a.sortierung - b.sortierung || a.bezeichnung.localeCompare(b.bezeichnung));
    const opts: { value: string; label: string }[] = [
        { value: '', label: '– keine Angabe –' },
        ...sorted.map(z => ({ value: z.bezeichnung, label: z.bezeichnung })),
    ];
    if (bestehenderWert && !opts.some(o => o.value === bestehenderWert)) {
        opts.push({ value: bestehenderWert, label: `${bestehenderWert} (nicht im Stamm)` });
    }
    return opts;
}

const KI_LABEL: Record<KiStatus, { label: string; cls: string }> = {
    PENDING: { label: 'KI wartet…', cls: 'bg-slate-100 text-slate-500' },
    LAEUFT: { label: 'KI analysiert…', cls: 'bg-sky-100 text-sky-700' },
    DONE: { label: 'KI fertig', cls: 'bg-emerald-100 text-emerald-700' },
    FAILED: { label: 'KI-Fehler', cls: 'bg-red-100 text-red-700' },
};

// ===================== Component =====================

type Tab = 'eingang' | 'alle' | 'privat' | 'kasse' | 'auswertung';

export default function BelegeKasseEditor() {
    const [activeTab, setActiveTab] = useState<Tab>('eingang');
    const [belege, setBelege] = useState<Beleg[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<'forbidden' | 'network' | null>(null);
    const [uploading, setUploading] = useState(false);
    const [search, setSearch] = useState('');
    const [editing, setEditing] = useState<Beleg | null>(null);
    const [kassenbuch, setKassenbuch] = useState<Kassenbuch | null>(null);
    const [kassenLoading, setKassenLoading] = useState(false);
    const [sachkonten, setSachkonten] = useState<Sachkonto[]>([]);
    const [zahlungsarten, setZahlungsarten] = useState<Zahlungsart[]>([]);
    const [auswertung, setAuswertung] = useState<Auswertung | null>(null);
    const [auswertungLoading, setAuswertungLoading] = useState(false);
    const heuteIso = new Date().toISOString().slice(0, 10);
    const jahresanfangIso = new Date(new Date().getFullYear(), 0, 1).toISOString().slice(0, 10);
    const [auswVon, setAuswVon] = useState<string>(jahresanfangIso);
    const [auswBis, setAuswBis] = useState<string>(heuteIso);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [umbuchungOpen, setUmbuchungOpen] = useState(false);
    const [monatsExportOpen, setMonatsExportOpen] = useState(false);
    const [steuerberaterEmailOpen, setSteuerberaterEmailOpen] = useState(false);

    const loadBelege = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/buchhaltung/belege');
            if (res.ok) {
                const data: Beleg[] = await res.json();
                setBelege(data);
                setLoadError(null);
            } else if (res.status === 403) {
                setBelege([]);
                setLoadError('forbidden');
            } else {
                setBelege([]);
                setLoadError('network');
            }
        } catch (e) {
            console.error('Belege laden fehlgeschlagen', e);
            setLoadError('network');
        } finally {
            setLoading(false);
        }
    }, []);

    const loadSachkonten = useCallback(async () => {
        try {
            const res = await fetch('/api/buchhaltung/sachkonten?nurAktive=true');
            if (res.ok) setSachkonten(await res.json());
        } catch (e) {
            console.error('Sachkonten laden fehlgeschlagen', e);
        }
    }, []);

    const loadZahlungsarten = useCallback(async () => {
        try {
            const res = await fetch('/api/buchhaltung/zahlungsarten?nurAktive=true');
            if (res.ok) setZahlungsarten(await res.json());
        } catch (e) {
            console.error('Zahlungsarten laden fehlgeschlagen', e);
        }
    }, []);

    const loadAuswertung = useCallback(async () => {
        setAuswertungLoading(true);
        try {
            const params = new URLSearchParams();
            if (auswVon) params.set('von', auswVon);
            if (auswBis) params.set('bis', auswBis);
            const res = await fetch(`/api/buchhaltung/auswertung?${params}`);
            if (res.ok) setAuswertung(await res.json());
        } catch (e) {
            console.error('Auswertung laden fehlgeschlagen', e);
        } finally {
            setAuswertungLoading(false);
        }
    }, [auswVon, auswBis]);

    const loadKassenbuch = useCallback(async () => {
        setKassenLoading(true);
        try {
            const res = await fetch('/api/buchhaltung/kassenbuch');
            if (res.ok) {
                setKassenbuch(await res.json());
            }
        } catch (e) {
            console.error('Kassenbuch laden fehlgeschlagen', e);
        } finally {
            setKassenLoading(false);
        }
    }, []);

    useEffect(() => {
        loadBelege();
        loadSachkonten();
        loadZahlungsarten();
    }, [loadBelege, loadSachkonten, loadZahlungsarten]);

    useEffect(() => {
        if (activeTab === 'kasse') loadKassenbuch();
        if (activeTab === 'auswertung') loadAuswertung();
    }, [activeTab, loadKassenbuch, loadAuswertung]);

    // Auto-Refresh, solange noch KI-Analysen offen sind
    useEffect(() => {
        const hatOffene = belege.some(b => b.kiAnalyseStatus === 'PENDING' || b.kiAnalyseStatus === 'LAEUFT');
        if (!hatOffene || editing) return;
        const id = setTimeout(() => loadBelege(), 4000);
        return () => clearTimeout(id);
    }, [belege, editing, loadBelege]);

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setUploading(true);
        try {
            const fd = new FormData();
            fd.append('datei', file);
            const res = await fetch('/api/buchhaltung/belege', { method: 'POST', body: fd });
            if (!res.ok) {
                const msg = await res.text().catch(() => '');
                alert('Upload fehlgeschlagen: ' + msg);
            } else {
                await loadBelege();
            }
        } catch (err) {
            console.error(err);
            alert('Netzwerkfehler beim Upload');
        } finally {
            setUploading(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    // ===================== Tab-Filter =====================

    const gefiltert = useMemo(() => {
        const term = search.trim().toLowerCase();
        const match = (b: Beleg) => {
            if (!term) return true;
            return (
                b.belegNummer?.toLowerCase().includes(term) ||
                b.beschreibung?.toLowerCase().includes(term) ||
                b.lieferantName?.toLowerCase().includes(term) ||
                b.kiVorgeschlagenerLieferant?.toLowerCase().includes(term) ||
                b.originalDateiname?.toLowerCase().includes(term)
            );
        };
        switch (activeTab) {
            case 'eingang':
                return belege.filter(b => b.status === 'NEU' && match(b));
            case 'alle':
                return belege.filter(b => b.status !== 'VERWORFEN' && match(b));
            case 'privat':
                return belege.filter(b =>
                    (b.belegKategorie === 'PRIVATENTNAHME' || b.belegKategorie === 'PRIVATEINLAGE')
                    && b.status !== 'VERWORFEN' && match(b));
            case 'kasse':
                return belege.filter(b =>
                    (b.belegKategorie === 'KASSE_EINNAHME' || b.belegKategorie === 'KASSE_AUSGABE')
                    && b.status !== 'VERWORFEN' && match(b)
                );
            default:
                return [];
        }
    }, [belege, search, activeTab]);

    const eingangsCount = belege.filter(b => b.status === 'NEU').length;

    // ===================== Render =====================

    return (
        <PageLayout
            ribbonCategory="Buchhaltung"
            title="Belege & Kasse"
            subtitle="Mobile-Scans validieren, Kassenbuch und Privatentnahmen führen"
            actions={
                <div className="flex items-center gap-2">
                    <Button variant="outline" onClick={loadBelege} disabled={loading}>
                        <RefreshCw className={loading ? 'w-4 h-4 mr-2 animate-spin' : 'w-4 h-4 mr-2'} />
                        Aktualisieren
                    </Button>
                    <Button variant="outline" onClick={() => setUmbuchungOpen(true)}>
                        <ArrowRightLeft className="w-4 h-4 mr-2" />
                        Umbuchung anlegen
                    </Button>
                    <Button variant="outline" onClick={() => setMonatsExportOpen(true)}
                            title="Monatsexport für den Steuerberater als PDF">
                        <FileDown className="w-4 h-4 mr-2" />
                        Monats-Export (PDF)
                    </Button>
                    <Button variant="outline" onClick={() => setSteuerberaterEmailOpen(true)}
                            title="Belegaufstellung als HTML-Tabelle per E-Mail an den Steuerberater">
                        <FileText className="w-4 h-4 mr-2" />
                        Belegliste per E-Mail
                    </Button>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*,application/pdf"
                        onChange={handleUpload}
                        className="hidden"
                    />
                    <Button onClick={() => fileInputRef.current?.click()} disabled={uploading}>
                        {uploading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Upload className="w-4 h-4 mr-2" />}
                        Beleg hochladen
                    </Button>
                </div>
            }
        >
            {/* KPI / Tabs */}
            <div className="flex items-center gap-2 border-b border-slate-200">
                <TabButton active={activeTab === 'eingang'} onClick={() => setActiveTab('eingang')}
                    icon={<FileQuestion className="w-4 h-4" />}
                    label="Eingang (Validierung)" badge={eingangsCount > 0 ? eingangsCount : undefined} />
                <TabButton active={activeTab === 'alle'} onClick={() => setActiveTab('alle')}
                    icon={<Receipt className="w-4 h-4" />} label="Alle Belege" />
                <TabButton active={activeTab === 'privat'} onClick={() => setActiveTab('privat')}
                    icon={<Wallet className="w-4 h-4" />} label="Privat (Entnahme / Einlage)" />
                <TabButton active={activeTab === 'kasse'} onClick={() => setActiveTab('kasse')}
                    icon={<Coins className="w-4 h-4" />} label="Kassenbuch" />
                <TabButton active={activeTab === 'auswertung'} onClick={() => setActiveTab('auswertung')}
                    icon={<BarChart3 className="w-4 h-4" />} label="Auswertung" />
            </div>

            {/* Search bar */}
            {activeTab !== 'kasse' && activeTab !== 'auswertung' && (
                <div className="relative max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Suche: Nummer, Beschreibung, Lieferant…"
                        className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>
            )}

            {/* Content */}
            {activeTab === 'kasse' ? (
                <div className="space-y-3">
                    <KasseShortcuts
                        sachkonten={sachkonten}
                        onChanged={() => { loadBelege(); loadKassenbuch(); }}
                    />
                    <KassenbuchView kassenbuch={kassenbuch} loading={kassenLoading} onSelectBeleg={id => {
                        const b = belege.find(x => x.id === id);
                        if (b) setEditing(b);
                    }} />
                </div>
            ) : activeTab === 'auswertung' ? (
                <AuswertungView
                    auswertung={auswertung}
                    loading={auswertungLoading}
                    von={auswVon}
                    bis={auswBis}
                    onVonChange={setAuswVon}
                    onBisChange={setAuswBis}
                    onReload={loadAuswertung}
                />
            ) : loading ? (
                <div className="flex justify-center py-16">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-500" />
                </div>
            ) : loadError === 'forbidden' ? (
                <Card className="p-12 text-center border-amber-200 bg-amber-50">
                    <AlertCircle className="w-12 h-12 mx-auto mb-3 text-amber-500" />
                    <p className="font-medium text-amber-900">Keine Berechtigung für Belege</p>
                    <p className="text-sm mt-2 text-amber-800 max-w-md mx-auto">
                        Dein Account hat keine Sicht-Berechtigung für Belege (Typ <code className="font-mono">BELEG</code>).
                        Lass dich unter <strong>Administration → Lieferanten-Dokumentenrechte</strong> für die Abteilung
                        Buchhaltung freischalten — oder prüfe, ob dein Frontend-Login mit einem Mitarbeiter-Datensatz
                        (gleiche E-Mail) verknüpft ist.
                    </p>
                </Card>
            ) : loadError === 'network' ? (
                <Card className="p-12 text-center border-red-200 bg-red-50">
                    <AlertCircle className="w-12 h-12 mx-auto mb-3 text-red-500" />
                    <p className="font-medium text-red-900">Belege konnten nicht geladen werden</p>
                    <p className="text-sm mt-2 text-red-800">
                        Netzwerk- oder Serverfehler. Prüfe die Browser-Konsole (F12) für Details.
                    </p>
                </Card>
            ) : gefiltert.length === 0 ? (
                <Card className="p-12 text-center text-slate-500">
                    <Receipt className="w-12 h-12 mx-auto mb-3 opacity-30" />
                    <p className="font-medium">
                        {activeTab === 'eingang' ? 'Keine offenen Belege zur Validierung'
                            : activeTab === 'privat' ? 'Keine Privatentnahmen oder -einlagen erfasst'
                            : 'Keine Belege gefunden'}
                    </p>
                    <p className="text-sm mt-1">Belege werden über die Handy-App gescannt oder hier hochgeladen.</p>
                </Card>
            ) : (
                <div className="space-y-2">
                    {gefiltert.map(b => (
                        <BelegRow key={b.id} beleg={b} onClick={() => setEditing(b)} />
                    ))}
                </div>
            )}

            {editing && (
                <BelegDetailModal
                    beleg={editing}
                    sachkonten={sachkonten}
                    zahlungsarten={zahlungsarten}
                    onClose={() => setEditing(null)}
                    onSaved={updated => {
                        setBelege(list => list.map(b => b.id === updated.id ? updated : b));
                        setEditing(null);
                    }}
                    onDeleted={id => {
                        setBelege(list => list.filter(b => b.id !== id));
                        setEditing(null);
                    }}
                />
            )}

            {umbuchungOpen && (
                <UmbuchungModal
                    sachkonten={sachkonten}
                    zahlungsarten={zahlungsarten}
                    onClose={() => setUmbuchungOpen(false)}
                    onCreated={(b) => {
                        setBelege(list => [b, ...list]);
                        setUmbuchungOpen(false);
                    }}
                />
            )}

            {monatsExportOpen && (
                <MonatsExportModal onClose={() => setMonatsExportOpen(false)} />
            )}

            <SteuerberaterBelegExportModal
                isOpen={steuerberaterEmailOpen}
                onClose={() => setSteuerberaterEmailOpen(false)}
            />
        </PageLayout>
    );
}

// ===================== Monats-Export Modal =====================

/**
 * Auswahl-Dialog für den PDF-Monatsexport. Ein PDF pro Kalendermonat —
 * gedacht für die Übergabe an den Steuerberater zusammen mit dem Ordner
 * der hochgeladenen Belegfotos.
 */
function MonatsExportModal({ onClose }: { onClose: () => void }) {
    const heute = new Date();
    // Default: Vormonat — der Steuerberater bekommt typischerweise den abgeschlossenen Monat.
    const defaultMonat = heute.getMonth() === 0 ? 12 : heute.getMonth();
    const defaultJahr  = heute.getMonth() === 0 ? heute.getFullYear() - 1 : heute.getFullYear();

    const [jahr,  setJahr]  = useState<number>(defaultJahr);
    const [monat, setMonat] = useState<number>(defaultMonat);

    const monatsLabels = [
        'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
        'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
    ];
    const jahre: number[] = [];
    for (let j = heute.getFullYear() + 1; j >= heute.getFullYear() - 5; j--) jahre.push(j);

    const handleExport = () => {
        const url = `/api/buchhaltung/auswertung/monat/pdf?jahr=${jahr}&monat=${monat}`;
        window.open(url, '_blank');
        onClose();
    };

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <FileDown className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 className="font-bold text-slate-900">Monats-Export (Steuerberater)</h2>
                            <p className="text-xs text-slate-500">
                                Ein PDF mit allen validierten Belegen des Monats
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full">
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                <div className="p-6 space-y-4">
                    <div className="bg-rose-50/60 border border-rose-100 rounded-lg p-3 text-xs text-slate-600 flex items-start gap-2">
                        <Calendar className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
                        <span>
                            Das PDF enthält die Sachkonto-Auswertung und alle validierten Belege.
                            Übergib es zusammen mit dem Ordner der Belegfotos an den Steuerberater.
                        </span>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Monat">
                            <Select
                                value={String(monat)}
                                onChange={v => setMonat(Number(v))}
                                options={monatsLabels.map((label, i) => ({
                                    value: String(i + 1),
                                    label,
                                }))}
                            />
                        </Field>
                        <Field label="Jahr">
                            <Select
                                value={String(jahr)}
                                onChange={v => setJahr(Number(v))}
                                options={jahre.map(j => ({ value: String(j), label: String(j) }))}
                            />
                        </Field>
                    </div>
                </div>

                <div className="border-t border-slate-200 p-4 flex items-center justify-end gap-2 bg-slate-50">
                    <Button variant="outline" onClick={onClose}>Abbrechen</Button>
                    <Button onClick={handleExport} className="bg-rose-600 hover:bg-rose-700 text-white">
                        <FileText className="w-4 h-4 mr-2" />
                        PDF erstellen
                    </Button>
                </div>
            </div>
        </div>
    );
}

// ===================== Umbuchungs-Modal (ohne Beleg-Datei) =====================

/**
 * Belegfreie Buchung: Privatentnahme, Privat->Firma, Kasse->Bank.
 * Sendet an POST /api/buchhaltung/umbuchungen. Erzeugter Beleg ist sofort
 * validiert und mit istUmbuchung=true markiert.
 */
function UmbuchungModal({ sachkonten, zahlungsarten, onClose, onCreated }: {
    sachkonten: Sachkonto[];
    zahlungsarten: Zahlungsart[];
    onClose: () => void;
    onCreated: (b: Beleg) => void;
}) {
    const heute = new Date().toISOString().slice(0, 10);
    const [form, setForm] = useState({
        belegKategorie: 'PRIVATENTNAHME' as BelegKategorie,
        belegDatum: heute,
        betragBrutto: '' as string,
        beschreibung: '',
        zahlungsart: '',
        sachkontoId: null as number | null,
        notiz: '',
    });
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const update = <K extends keyof typeof form>(k: K, v: typeof form[K]) =>
        setForm(f => ({ ...f, [k]: v }));

    const submit = async () => {
        setError(null);
        const betrag = Number(form.betragBrutto);
        if (!Number.isFinite(betrag) || betrag <= 0) {
            setError('Bitte einen positiven Betrag eingeben.');
            return;
        }
        if (!form.belegDatum) {
            setError('Bitte ein Datum wählen.');
            return;
        }
        setSaving(true);
        try {
            const res = await fetch('/api/buchhaltung/umbuchungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    belegKategorie: form.belegKategorie,
                    belegDatum: form.belegDatum,
                    betragBrutto: betrag,
                    beschreibung: form.beschreibung || null,
                    zahlungsart: form.zahlungsart || null,
                    sachkontoId: form.sachkontoId,
                    notiz: form.notiz || null,
                }),
            });
            if (!res.ok) {
                const txt = await res.text().catch(() => '');
                setError('Anlegen fehlgeschlagen: ' + (txt || res.statusText));
                return;
            }
            const b: Beleg = await res.json();
            onCreated(b);
        } catch (e) {
            setError('Netzwerkfehler');
            console.error(e);
        } finally {
            setSaving(false);
        }
    };

    // Nur Bewegungskategorien erlauben — Umbuchungen ohne Wirkung im Kassenbuch
    // (UNZUGEORDNET, SONSTIGER_BELEG) sind serverseitig blockiert.
    const KATEGORIEN_UMBUCHUNG: BelegKategorie[] = [
        'PRIVATENTNAHME', 'PRIVATEINLAGE', 'KASSE_EINNAHME', 'KASSE_AUSGABE', 'BANK', 'KREDITKARTE',
    ];

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-xl flex flex-col">
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <FileInput className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 className="font-bold text-slate-900">Umbuchung ohne Beleg</h2>
                            <p className="text-xs text-slate-500">Privatentnahme, Kasse → Bank, Privat → Firma …</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full"><X className="w-5 h-5 text-slate-500" /></button>
                </div>

                <div className="p-6 space-y-4">
                    {error && (
                        <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                            {error}
                        </div>
                    )}

                    <Field label="Art der Buchung">
                        <Select
                            value={form.belegKategorie}
                            onChange={v => update('belegKategorie', v as BelegKategorie)}
                            options={KATEGORIEN_UMBUCHUNG.map(k => ({ value: k, label: KATEGORIE_LABELS[k] }))}
                        />
                    </Field>

                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Datum">
                            <input type="date" value={form.belegDatum}
                                onChange={e => update('belegDatum', e.target.value)}
                                className={inputCls} />
                        </Field>
                        <Field label="Betrag (€)">
                            <input type="number" step="0.01" min="0" value={form.betragBrutto}
                                onChange={e => update('betragBrutto', e.target.value)}
                                placeholder="z.B. 100,00"
                                className={inputCls} />
                        </Field>
                    </div>

                    <Field label="Beschreibung">
                        <input type="text" value={form.beschreibung}
                            onChange={e => update('beschreibung', e.target.value)}
                            maxLength={500}
                            placeholder="z.B. Privatentnahme Bar, Bareinzahlung auf Bankkonto"
                            className={inputCls} />
                    </Field>

                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Zahlungsart">
                            <Select
                                value={form.zahlungsart}
                                onChange={v => update('zahlungsart', v)}
                                placeholder="– bitte wählen –"
                                options={buildZahlungsartOptions(zahlungsarten, form.zahlungsart)}
                            />
                        </Field>
                        <Field label="Konto (optional)">
                            <Select
                                value={form.sachkontoId != null ? String(form.sachkontoId) : ''}
                                onChange={v => update('sachkontoId', v ? Number(v) : null)}
                                placeholder="– kein Konto –"
                                options={buildSachkontoOptions(sachkonten)}
                            />
                        </Field>
                    </div>

                    <Field label="Notiz">
                        <textarea rows={2} value={form.notiz}
                            onChange={e => update('notiz', e.target.value)}
                            maxLength={1000}
                            className={inputCls} />
                    </Field>
                </div>

                <div className="border-t border-slate-200 p-4 flex items-center justify-end gap-2 bg-slate-50">
                    <Button variant="outline" onClick={onClose} disabled={saving}>
                        Abbrechen
                    </Button>
                    <Button onClick={submit} disabled={saving}>
                        {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                        Umbuchung anlegen
                    </Button>
                </div>
            </div>
        </div>
    );
}

// ===================== Sub-Components =====================

function TabButton({ active, onClick, icon, label, badge }: {
    active: boolean; onClick: () => void; icon: React.ReactNode; label: string; badge?: number;
}) {
    return (
        <button
            onClick={onClick}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors -mb-px
                ${active
                    ? 'border-rose-600 text-rose-700'
                    : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-200'}`}
        >
            {icon}
            {label}
            {badge != null && (
                <span className="ml-1 inline-flex items-center justify-center min-w-[1.25rem] h-5 px-1.5 text-xs font-semibold rounded-full bg-rose-600 text-white">
                    {badge}
                </span>
            )}
        </button>
    );
}

function BelegRow({ beleg, onClick }: { beleg: Beleg; onClick: () => void }) {
    const ki = KI_LABEL[beleg.kiAnalyseStatus];
    // Issue #58: Bei TEILWEISE-Belegen ist die relevante Buchhaltungs-Summe
    // der Firma-Anteil — nicht der Gesamt-Brutto. Sonst sieht der Buchhalter
    // bei einem Mischbeleg ueber 178,50 € am Listenrand 178,50 €, obwohl
    // davon nur 30 € fuer die Firma gebucht werden.
    const teilweise = beleg.aufteilungsModus === 'TEILWEISE' && beleg.betragFirmaBrutto != null;
    const anzeigeBrutto = teilweise ? beleg.betragFirmaBrutto : beleg.betragBrutto;
    return (
        <button
            onClick={onClick}
            className="w-full text-left bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-sm transition-all flex items-center gap-4"
        >
            <div className="w-12 h-12 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
                <Receipt className="w-6 h-6 text-rose-600" />
            </div>
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <span className="font-semibold text-slate-900 truncate">
                        {beleg.belegNummer || beleg.kiVorgeschlagenerLieferant || beleg.originalDateiname || `Beleg #${beleg.id}`}
                    </span>
                    <span className={`text-xs px-2 py-0.5 rounded-full ${KATEGORIE_FARBE[beleg.belegKategorie]}`}>
                        {KATEGORIE_LABELS[beleg.belegKategorie]}
                    </span>
                    {teilweise && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 inline-flex items-center gap-1"
                              title="Mischbeleg – nur ein Teil ist betrieblich">
                            Mischbeleg
                        </span>
                    )}
                    {beleg.status === 'NEU' && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 inline-flex items-center gap-1">
                            <AlertCircle className="w-3 h-3" /> Zu prüfen
                        </span>
                    )}
                    {beleg.status === 'VALIDIERT' && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 inline-flex items-center gap-1">
                            <CheckCircle2 className="w-3 h-3" /> Validiert
                        </span>
                    )}
                    {beleg.istUmbuchung && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-slate-200 text-slate-700 inline-flex items-center gap-1">
                            <ArrowRightLeft className="w-3 h-3" /> Umbuchung
                        </span>
                    )}
                    {beleg.eingangsrechnungId != null && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 inline-flex items-center gap-1"
                              title="Auch unter Eingangsrechnungen sichtbar">
                            <FileText className="w-3 h-3" /> Eingangsrechnung
                        </span>
                    )}
                    {!beleg.istUmbuchung && (
                        <span className={`text-xs px-2 py-0.5 rounded-full ${ki.cls}`}>{ki.label}</span>
                    )}
                </div>
                <div className="text-sm text-slate-500 flex items-center gap-4 flex-wrap">
                    <span>{formatDate(beleg.belegDatum)}</span>
                    {beleg.lieferantName && <span className="inline-flex items-center gap-1"><Truck className="w-3 h-3" />{beleg.lieferantName}</span>}
                    {beleg.sachkontoBezeichnung && (
                        <span className="inline-flex items-center gap-1 text-slate-600">
                            <BookOpen className="w-3 h-3" />
                            {beleg.sachkontoNummer ? `${beleg.sachkontoNummer} ` : ''}{beleg.sachkontoBezeichnung}
                        </span>
                    )}
                    {beleg.uploadedByName && <span>Hochgeladen von {beleg.uploadedByName}</span>}
                </div>
            </div>
            <div className="text-right flex-shrink-0">
                <div className="font-semibold text-slate-900">{formatEuro(anzeigeBrutto)} €</div>
                {teilweise ? (
                    <div className="text-xs text-rose-700">
                        davon Firma · Gesamt {formatEuro(beleg.betragBrutto)} €
                    </div>
                ) : beleg.mwstSatz != null && (
                    <div className="text-xs text-slate-400">MwSt {beleg.mwstSatz}%</div>
                )}
            </div>
        </button>
    );
}

function KassenbuchView({ kassenbuch, loading, onSelectBeleg }: {
    kassenbuch: Kassenbuch | null; loading: boolean; onSelectBeleg: (id: number) => void;
}) {
    if (loading) {
        return <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-rose-500" /></div>;
    }
    if (!kassenbuch) {
        return <Card className="p-12 text-center text-slate-500"><Coins className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>Kassenbuch konnte nicht geladen werden.</p></Card>;
    }

    // T-Konto: Soll-Seite = Geld rein (Einnahmen + Privateinlagen),
    // Haben-Seite = Geld raus (Ausgaben + Privatentnahmen). Sortierung folgt
    // der Server-Reihenfolge (chronologisch). 0,00-€-Bewegungen (pathologisch
    // aber moeglich) landen auf der Eingang-Seite, damit nichts stillschweigend
    // verschwindet — Summenfuss-Konsistenz bleibt.
    const eingaenge = kassenbuch.bewegungen.filter(b => b.betrag >= 0);
    const ausgaenge = kassenbuch.bewegungen.filter(b => b.betrag < 0);
    const summeEingang = kassenbuch.summeEinnahmen + kassenbuch.summePrivateinlagen;
    const summeAusgang = kassenbuch.summeAusgaben + kassenbuch.summePrivatentnahmen;

    return (
        <div className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                <KpiTile label="Saldo aktuell" value={`${formatEuro(kassenbuch.saldoEnde)} €`} icon={<Wallet className="w-5 h-5" />} highlight />
                <KpiTile label="Einnahmen" value={`${formatEuro(kassenbuch.summeEinnahmen)} €`} icon={<Banknote className="w-5 h-5 text-emerald-600" />} />
                <KpiTile label="Ausgaben" value={`${formatEuro(kassenbuch.summeAusgaben)} €`} icon={<CreditCard className="w-5 h-5 text-amber-600" />} />
                <KpiTile label="Privatentnahmen" value={`${formatEuro(kassenbuch.summePrivatentnahmen)} €`} icon={<Wallet className="w-5 h-5 text-fuchsia-600" />} />
                <KpiTile label="Privateinlagen" value={`${formatEuro(kassenbuch.summePrivateinlagen)} €`} icon={<Wallet className="w-5 h-5 text-lime-600" />} />
            </div>

            <Card className="overflow-hidden">
                {/* Konto-Kopf */}
                <div className="border-b-2 border-slate-800 bg-slate-50/60 px-6 py-3 text-center">
                    <div className="text-[10px] uppercase tracking-[0.25em] text-slate-500 font-semibold">Kasse · Bargeldkonto</div>
                    <div className="text-lg font-bold text-slate-900 mt-0.5">Bar-Bewegungen</div>
                </div>

                {/* Spaltenköpfe */}
                <div className="grid grid-cols-2 border-b border-slate-300">
                    <div className="px-6 py-3 border-r-2 border-slate-800 bg-emerald-50/40">
                        <div className="flex items-baseline gap-2">
                            <span className="text-sm font-bold text-emerald-800 uppercase tracking-wider">Eingang</span>
                            <span className="text-[10px] text-emerald-600 font-medium uppercase tracking-wider">Soll</span>
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5">Einnahmen + Privateinlagen</div>
                    </div>
                    <div className="px-6 py-3 bg-amber-50/40">
                        <div className="flex items-baseline gap-2 justify-end">
                            <span className="text-[10px] text-amber-700 font-medium uppercase tracking-wider">Haben</span>
                            <span className="text-sm font-bold text-amber-800 uppercase tracking-wider">Ausgang</span>
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5 text-right">Ausgaben + Privatentnahmen</div>
                    </div>
                </div>

                {/* Buchungsspalten */}
                <div className="grid grid-cols-2 min-h-[420px]">
                    {/* Eingang / Soll */}
                    <div className="border-r-2 border-slate-800 divide-y divide-slate-100">
                        {eingaenge.length === 0 ? (
                            <div className="px-6 py-10 text-center text-slate-400 text-sm">Keine Eingänge erfasst.</div>
                        ) : eingaenge.map(bew => (
                            <TKontoZeile key={`in-${bew.belegId}`} bew={bew} side="eingang" onClick={() => onSelectBeleg(bew.belegId)} />
                        ))}
                    </div>
                    {/* Ausgang / Haben */}
                    <div className="divide-y divide-slate-100">
                        {ausgaenge.length === 0 ? (
                            <div className="px-6 py-10 text-center text-slate-400 text-sm">Keine Ausgänge erfasst.</div>
                        ) : ausgaenge.map(bew => (
                            <TKontoZeile key={`out-${bew.belegId}`} bew={bew} side="ausgang" onClick={() => onSelectBeleg(bew.belegId)} />
                        ))}
                    </div>
                </div>

                {/* Doppelstrich nach Buchhalter-Tradition */}
                <div className="border-t-2 border-slate-800" />
                <div className="border-t border-slate-800 mt-[3px]" />

                {/* Summenfuß */}
                <div className="grid grid-cols-2 bg-slate-50">
                    <div className="px-6 py-3 border-r-2 border-slate-800 flex items-baseline justify-between">
                        <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Summe Eingang</span>
                        <span className="text-xl font-bold text-emerald-700 tabular-nums">{formatEuro(summeEingang)} €</span>
                    </div>
                    <div className="px-6 py-3 flex items-baseline justify-between">
                        <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Summe Ausgang</span>
                        <span className="text-xl font-bold text-amber-700 tabular-nums">{formatEuro(summeAusgang)} €</span>
                    </div>
                </div>

                {/* Saldo-Zeile */}
                <div className="bg-rose-50/60 border-t border-rose-200 px-6 py-3">
                    <div className="flex items-baseline justify-between max-w-2xl mx-auto gap-4">
                        <div>
                            <div className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">Anfangsbestand</div>
                            <div className="text-sm text-slate-600 tabular-nums">{formatEuro(kassenbuch.saldoStart)} €</div>
                        </div>
                        <ArrowRight className="w-5 h-5 text-slate-300 shrink-0" aria-hidden />
                        <div className="text-right">
                            <div className="text-[10px] uppercase tracking-wider text-rose-600 font-semibold">Neuer Saldo</div>
                            <div className="text-2xl font-bold text-rose-700 tabular-nums">{formatEuro(kassenbuch.saldoEnde)} €</div>
                        </div>
                    </div>
                </div>
            </Card>
        </div>
    );
}

function TKontoZeile({ bew, side, onClick }: {
    bew: KassenBewegung;
    side: 'eingang' | 'ausgang';
    onClick: () => void;
}) {
    const istPrivat = bew.kategorie === 'PRIVATENTNAHME' || bew.kategorie === 'PRIVATEINLAGE';
    const betragColor = side === 'eingang'
        ? (istPrivat ? 'text-lime-700' : 'text-emerald-700')
        : (istPrivat ? 'text-fuchsia-700' : 'text-amber-700');
    const hoverBg = side === 'eingang' ? 'hover:bg-emerald-50/40' : 'hover:bg-amber-50/40';
    return (
        <button type="button" onClick={onClick}
            className={`w-full text-left px-6 py-2.5 cursor-pointer ${hoverBg} focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 focus-visible:ring-inset`}>
            <div className="flex items-baseline justify-between gap-3">
                <div className="flex items-baseline gap-3 min-w-0 flex-1">
                    <span className="text-xs text-slate-500 tabular-nums w-20 shrink-0">{formatDate(bew.datum)}</span>
                    <div className="min-w-0 flex-1">
                        <div className="text-sm text-slate-800 font-medium truncate">
                            {bew.beschreibung || '–'}
                            {bew.lieferantName && <span className="ml-2 text-slate-400 font-normal">({bew.lieferantName})</span>}
                        </div>
                        <span className={`text-[10px] uppercase tracking-wider font-semibold px-1.5 py-0.5 rounded ${KATEGORIE_FARBE[bew.kategorie]} mt-1 inline-block`}>
                            {KATEGORIE_LABELS[bew.kategorie]}
                        </span>
                    </div>
                </div>
                <span className={`text-base font-semibold tabular-nums shrink-0 ${betragColor}`}>
                    {formatEuro(Math.abs(bew.betrag))} €
                </span>
            </div>
        </button>
    );
}

function KpiTile({ label, value, icon, highlight }: { label: string; value: string; icon: React.ReactNode; highlight?: boolean }) {
    return (
        <Card className={`p-4 ${highlight ? 'border-rose-200 bg-rose-50/50' : ''}`}>
            <div className="flex items-center gap-2 text-slate-500 text-xs uppercase font-semibold tracking-wide">
                {icon}
                {label}
            </div>
            <div className="mt-2 text-2xl font-bold text-slate-900 tabular-nums">{value}</div>
        </Card>
    );
}

// ===================== Detail / Validierungs-Modal =====================

function BelegDetailModal({ beleg, sachkonten, zahlungsarten, onClose, onSaved, onDeleted }: {
    beleg: Beleg;
    sachkonten: Sachkonto[];
    zahlungsarten: Zahlungsart[];
    onClose: () => void;
    onSaved: (b: Beleg) => void;
    onDeleted: (id: number) => void;
}) {
    // Issue #58: Detail-Beleg inkl. Positionen nachladen — die Listen-Query
    // liefert positionen[] aus Performance-Gruenden nicht. Erst nach dem
    // Nachladen kennen wir die Position-Details fuer den Aufteilungs-Bereich.
    const [detailBeleg, setDetailBeleg] = useState<Beleg>(beleg);
    useEffect(() => {
        if (beleg.aufteilungsModus !== 'TEILWEISE') return;
        let cancelled = false;
        fetch(`/api/buchhaltung/belege/${beleg.id}`)
            .then(r => r.ok ? r.json() : null)
            .then((data: Beleg | null) => {
                if (cancelled || !data) return;
                setDetailBeleg(data);
            })
            .catch(err => console.error('Beleg-Detail laden fehlgeschlagen', err));
        return () => { cancelled = true; };
    }, [beleg.id, beleg.aufteilungsModus]);

    const [form, setForm] = useState({
        belegKategorie: beleg.belegKategorie,
        belegDatum: beleg.belegDatum ?? '',
        belegNummer: beleg.belegNummer ?? '',
        beschreibung: beleg.beschreibung ?? '',
        betragNetto: beleg.betragNetto ?? '',
        betragBrutto: beleg.betragBrutto ?? '',
        mwstSatz: beleg.mwstSatz ?? '',
        zahlungsart: beleg.zahlungsart ?? '',
        lieferantId: beleg.lieferantId ?? null as number | null,
        lieferantName: beleg.lieferantName ?? '',
        sachkontoId: beleg.sachkontoId ?? null as number | null,
        notiz: beleg.notiz ?? '',
    });
    const [splits, setSplits] = useState<KostenstellenSplit[]>(beleg.kostenstellenSplits ?? []);
    // Wenn das Detail nachgeladen wird (TEILWEISE), beziehen wir die Splits
    // aus dem frischen DTO — die Listen-Query liefert sie ggf. nicht mit.
    useEffect(() => {
        if (detailBeleg.kostenstellenSplits) setSplits(detailBeleg.kostenstellenSplits);
    }, [detailBeleg]);
    const [saving, setSaving] = useState(false);
    const [lieferantPicker, setLieferantPicker] = useState(false);
    const [saldoInfo, setSaldoInfo] = useState<{ saldo: number; mindestbestand: number } | null>(null);
    const [konflikt, setKonflikt] = useState<{ projizierterSaldo: number; mindestbestand: number; message: string } | null>(null);
    // Inline-Validierungs-Hinweis statt blocking alert(), gemäß Toast-Pattern
    // im restlichen Modul (siehe KasseShortcuts.tsx). Nach 4s ausblenden.
    const [validationHint, setValidationHint] = useState<string | null>(null);
    useEffect(() => {
        if (!validationHint) return;
        const t = setTimeout(() => setValidationHint(null), 4000);
        return () => clearTimeout(t);
    }, [validationHint]);

    // Live-Saldo laden, sobald Modal offen — nur fuer Bar-Belege relevant.
    useEffect(() => {
        const barKategorien: BelegKategorie[] = ['KASSE_EINNAHME', 'KASSE_AUSGABE', 'PRIVATENTNAHME', 'PRIVATEINLAGE'];
        if (!barKategorien.includes(form.belegKategorie)) {
            setSaldoInfo(null);
            return;
        }
        fetch('/api/buchhaltung/kasse/saldo')
            .then(r => r.ok ? r.json() : null)
            .then((s) => s && setSaldoInfo(s))
            .catch(err => console.error('Saldo laden fehlgeschlagen', err));
    }, [form.belegKategorie]);

    // Live-Projektion: wie sieht der Saldo nach Validierung dieses Belegs aus?
    const projektion = useMemo(() => {
        if (!saldoInfo) return null;
        const brutto = Number(form.betragBrutto);
        if (!Number.isFinite(brutto) || brutto <= 0) return null;
        const alt = beleg.status === 'VALIDIERT' && beleg.betragBrutto != null
            ? (beleg.belegKategorie === 'KASSE_AUSGABE' || beleg.belegKategorie === 'PRIVATENTNAHME'
                ? -beleg.betragBrutto : beleg.betragBrutto) : 0;
        const neu = form.belegKategorie === 'KASSE_AUSGABE' || form.belegKategorie === 'PRIVATENTNAHME'
            ? -brutto : brutto;
        return saldoInfo.saldo - alt + neu;
    }, [saldoInfo, form.belegKategorie, form.betragBrutto, beleg.status, beleg.belegKategorie, beleg.betragBrutto]);

    const update = <K extends keyof typeof form>(k: K, v: typeof form[K]) =>
        setForm(f => ({ ...f, [k]: v }));

    const save = async (alsValidiert: boolean) => {
        // Splits-Vorab-Validierung: Summe Prozent <= 100 + jeder Eintrag hat
        // genau eines von Prozent/Absolut. Verhindert HTTP 400 Round-trip.
        const prozentSumme = splits.reduce((acc, s) => acc + (s.prozent ?? 0), 0);
        if (prozentSumme > 100) {
            setValidationHint(`Summe der Kostenstellen-Prozente ist ${prozentSumme}% — darf nicht über 100% liegen.`);
            return;
        }
        for (const s of splits) {
            if (!s.kostenstelleId) {
                setValidationHint('Jeder Split braucht eine Kostenstelle.');
                return;
            }
            const hatProzent = s.prozent != null;
            const hatAbsolut = s.absoluterBetrag != null;
            if (hatProzent === hatAbsolut) {
                setValidationHint('Pro Split-Eintrag genau EINES von Prozent ODER absolutem Betrag setzen.');
                return;
            }
        }
        setValidationHint(null);

        setSaving(true);
        setKonflikt(null);
        try {
            const body = {
                belegKategorie: form.belegKategorie,
                status: alsValidiert ? 'VALIDIERT' : undefined,
                belegDatum: form.belegDatum || null,
                belegNummer: form.belegNummer || null,
                beschreibung: form.beschreibung || null,
                betragNetto: form.betragNetto === '' ? null : Number(form.betragNetto),
                betragBrutto: form.betragBrutto === '' ? null : Number(form.betragBrutto),
                mwstSatz: form.mwstSatz === '' ? null : Number(form.mwstSatz),
                zahlungsart: form.zahlungsart || null,
                lieferantId: form.lieferantId,
                sachkontoId: form.sachkontoId,
                notiz: form.notiz || null,
                kostenstellenSplits: splits.map(s => ({
                    kostenstelleId: s.kostenstelleId,
                    prozent: s.prozent,
                    absoluterBetrag: s.absoluterBetrag,
                    beschreibung: s.beschreibung || null,
                    streckungJahre: s.streckungJahre,
                    streckungStartJahr: s.streckungStartJahr,
                })),
            };
            const res = await fetch(`/api/buchhaltung/belege/${beleg.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (res.ok) {
                const updated: Beleg = await res.json();
                onSaved(updated);
                return;
            }
            if (res.status === 409) {
                const body409 = await res.json();
                setKonflikt({
                    projizierterSaldo: Number(body409.projizierterSaldo),
                    mindestbestand: Number(body409.mindestbestand),
                    message: body409.message ?? 'Kasse würde unter Mindestbestand fallen',
                });
                return;
            }
            const body400 = await res.json().catch(() => null);
            alert(body400?.message ?? 'Speichern fehlgeschlagen');
        } catch (e) {
            console.error(e);
            alert('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // 1-Klick-Loesung bei 409: vorab eine Privateinlage in der benoetigten
    // Hoehe buchen und dann nochmal speichern.
    const loeseUnterdeckung = async () => {
        if (!konflikt) return;
        const benoetigt = Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo);
        if (benoetigt <= 0) {
            setKonflikt(null);
            return;
        }
        setSaving(true);
        try {
            const heute = new Date().toISOString().slice(0, 10);
            const res = await fetch('/api/buchhaltung/kasse/privateinlage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    betrag: benoetigt,
                    datum: heute,
                    beschreibung: 'Vorab-Einlage für validierten Beleg',
                }),
            });
            if (!res.ok) {
                alert('Vorab-Einlage fehlgeschlagen');
                return;
            }
            setKonflikt(null);
            await save(true);
        } finally {
            setSaving(false);
        }
    };

    const verwerfen = async () => {
        if (!confirm('Beleg wirklich verwerfen? Die Datei bleibt erhalten (Steuer-Nachweis).')) return;
        setSaving(true);
        try {
            const res = await fetch(`/api/buchhaltung/belege/${beleg.id}`, { method: 'DELETE' });
            if (res.ok) onDeleted(beleg.id);
            else alert('Verwerfen fehlgeschlagen');
        } finally {
            setSaving(false);
        }
    };

    const kiVorschlag = beleg.kiVorgeschlagenerLieferant && !form.lieferantName
        ? beleg.kiVorgeschlagenerLieferant : null;

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-[98vw] max-h-[95vh] flex flex-col overflow-hidden">
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <Receipt className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 className="font-bold text-slate-900">Beleg prüfen & validieren</h2>
                            <p className="text-xs text-slate-500">Hochgeladen {formatDateTime(beleg.uploadDatum)} {beleg.uploadedByName ? `von ${beleg.uploadedByName}` : ''}</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full"><X className="w-5 h-5 text-slate-500" /></button>
                </div>

                <div className="flex-1 overflow-hidden grid grid-cols-1 lg:grid-cols-3 gap-0 min-h-0">
                    {/* Vorschau */}
                    <div className="lg:col-span-2 bg-slate-100 flex flex-col items-stretch p-4 border-r border-slate-200 overflow-auto">
                        <BelegPreview belegId={beleg.id} mimeType={beleg.mimeType} originalDateiname={beleg.originalDateiname} />
                    </div>

                    {/* Form */}
                    <div className="lg:col-span-1 overflow-auto p-6 space-y-5">
                        {beleg.kiAnalyseStatus === 'FAILED' && (
                            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                                <strong>KI-Analyse fehlgeschlagen:</strong> {beleg.kiFehlerText}
                            </div>
                        )}
                        {beleg.kiAnalyseStatus === 'PENDING' || beleg.kiAnalyseStatus === 'LAEUFT' ? (
                            <div className="bg-sky-50 border border-sky-200 rounded-lg p-3 text-sm text-sky-700 inline-flex items-center gap-2">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                KI-Analyse läuft – Werte erscheinen automatisch, sobald sie fertig ist.
                            </div>
                        ) : null}

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            <div>
                                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Wo gezahlt</label>
                                <Select
                                    value={form.belegKategorie}
                                    onChange={v => update('belegKategorie', v as BelegKategorie)}
                                    options={(Object.entries(KATEGORIE_LABELS) as [BelegKategorie, string][])
                                        .map(([k, label]) => ({ value: k, label }))}
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1 inline-flex items-center gap-1">
                                    <BookOpen className="w-3 h-3" /> Konto / Wofür?
                                </label>
                                <Select
                                    value={form.sachkontoId != null ? String(form.sachkontoId) : ''}
                                    onChange={v => update('sachkontoId', v ? Number(v) : null)}
                                    placeholder="– kein Konto zugewiesen –"
                                    options={buildSachkontoOptions(sachkonten)}
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Beleg-Nummer">
                                <input type="text" value={form.belegNummer}
                                    onChange={e => update('belegNummer', e.target.value)}
                                    className={inputCls} />
                            </Field>
                            <Field label="Beleg-Datum">
                                <input type="date" value={form.belegDatum}
                                    onChange={e => update('belegDatum', e.target.value)}
                                    className={inputCls} />
                            </Field>
                            <Field label="Brutto (€)">
                                <input type="number" step="0.01" value={form.betragBrutto}
                                    onChange={e => update('betragBrutto', e.target.value as never)}
                                    className={inputCls} />
                            </Field>
                            <Field label="Netto (€)">
                                <input type="number" step="0.01" value={form.betragNetto}
                                    onChange={e => update('betragNetto', e.target.value as never)}
                                    className={inputCls} />
                            </Field>
                            <Field label="MwSt-Satz (%)">
                                <input type="number" step="0.1" value={form.mwstSatz}
                                    onChange={e => update('mwstSatz', e.target.value as never)}
                                    className={inputCls} />
                            </Field>
                            <Field label="Zahlungsart">
                                <Select
                                    value={form.zahlungsart}
                                    onChange={v => update('zahlungsart', v)}
                                    placeholder="– bitte wählen –"
                                    options={buildZahlungsartOptions(zahlungsarten, form.zahlungsart)}
                                />
                            </Field>
                        </div>

                        <Field label="Beschreibung">
                            <input type="text" value={form.beschreibung}
                                onChange={e => update('beschreibung', e.target.value)}
                                placeholder="z.B. Tankquittung, Büromaterial…"
                                className={inputCls} />
                        </Field>

                        <Field label="Lieferant (optional)">
                            <div className="flex items-center gap-2">
                                <input type="text" readOnly
                                    value={form.lieferantName || (kiVorschlag ? `KI-Vorschlag: ${kiVorschlag}` : '')}
                                    placeholder="Kein Lieferant – z.B. bei Kassen-Einnahme"
                                    className={`${inputCls} bg-slate-50`} />
                                <Button variant="outline" type="button" onClick={() => setLieferantPicker(true)}>
                                    <Truck className="w-4 h-4 mr-2" />
                                    Wählen
                                </Button>
                                {form.lieferantId && (
                                    <Button variant="ghost" type="button"
                                        onClick={() => { update('lieferantId', null); update('lieferantName', ''); }}>
                                        <X className="w-4 h-4" />
                                    </Button>
                                )}
                            </div>
                        </Field>

                        <Field label="Notiz">
                            <textarea rows={2} value={form.notiz}
                                onChange={e => update('notiz', e.target.value)}
                                className={inputCls} />
                        </Field>

                        {detailBeleg.aufteilungsModus === 'TEILWEISE' && (
                            <AufteilungsSektion beleg={detailBeleg} />
                        )}

                        {/* Issue #60: Kostenstellen-Splits — mehrere Kostenstellen pro Beleg */}
                        <KostenstellenSplitsEditor
                            splits={splits}
                            onChange={setSplits}
                            defaultStartJahr={form.belegDatum
                                ? new Date(form.belegDatum).getFullYear()
                                : new Date().getFullYear()}
                        />

                        {/* Live-Saldo-Vorschau + 409-Konflikt-Dialog */}
                        {saldoInfo && (
                            <div className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-2 space-y-0.5">
                                <div>Kassenstand jetzt: <strong>{formatEuro(saldoInfo.saldo)} €</strong></div>
                                {projektion != null && (
                                    <div>
                                        Nach Validierung: <strong className={projektion < saldoInfo.mindestbestand ? 'text-red-700' : 'text-slate-700'}>
                                            {formatEuro(projektion)} €
                                        </strong>
                                        {saldoInfo.mindestbestand > 0 && (
                                            <span className="text-slate-500"> (Mindestbestand: {formatEuro(saldoInfo.mindestbestand)} €)</span>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}
                        {konflikt && (
                            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-900">
                                <div className="flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                    <div className="flex-1">
                                        <p className="font-medium">Kasse würde auf {formatEuro(konflikt.projizierterSaldo)} € rutschen.</p>
                                        <p className="text-xs mt-1">Mindestbestand: {formatEuro(konflikt.mindestbestand)} €</p>
                                        <Button size="sm" className="mt-2 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                            onClick={loeseUnterdeckung} disabled={saving}>
                                            Privateinlage in Höhe {formatEuro(Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo))} € vorab buchen?
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                <div className="border-t border-slate-200 p-4 flex flex-col gap-2 bg-slate-50">
                    {validationHint && (
                        <div className="text-sm px-3 py-2 rounded-lg border bg-amber-50 border-amber-200 text-amber-900">
                            {validationHint}
                        </div>
                    )}
                    <div className="flex items-center justify-between gap-3">
                        <Button variant="ghost" onClick={verwerfen} disabled={saving} className="text-red-600 hover:bg-red-50">
                            <Trash2 className="w-4 h-4 mr-2" /> Verwerfen
                        </Button>
                        <div className="flex items-center gap-2">
                            <Button variant="outline" onClick={() => save(false)} disabled={saving}>
                                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                                Zwischenspeichern
                            </Button>
                            <Button onClick={() => save(true)} disabled={saving}>
                                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <CheckCircle2 className="w-4 h-4 mr-2" />}
                                Prüfen & Übernehmen
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            {lieferantPicker && (
                <LieferantSearchModal
                    isOpen={lieferantPicker}
                    onClose={() => setLieferantPicker(false)}
                    currentLieferantId={form.lieferantId ?? undefined}
                    onSelect={(l: LieferantSuchErgebnis) => {
                        update('lieferantId', l.id);
                        update('lieferantName', l.lieferantenname);
                    }}
                />
            )}
        </div>
    );
}

/**
 * Issue #58: Read-only Anzeige der Beleg-Positionen mit Hervorhebung der am
 * Handy markierten Firma-Positionen. Korrektur am PC ist NICHT vorgesehen —
 * die Mobile-Auswahl ist die Quelle der Wahrheit, der Buchhalter sieht hier
 * nur, was der Scanner gewaehlt hat (und kann es ggf. ueber die Mobile-PWA
 * korrigieren).
 */
function AufteilungsSektion({ beleg }: { beleg: Beleg }) {
    const positionen = beleg.positionen ?? [];
    if (positionen.length === 0) {
        return (
            <div className="bg-rose-50/60 border border-rose-100 rounded-lg p-3 text-sm text-slate-600">
                <strong className="text-rose-700">Teil-Beleg.</strong>{' '}
                Positionen werden geladen oder wurden vom Scanner noch nicht erfasst.
            </div>
        );
    }
    const firmaCount = positionen.filter(p => p.istFuerFirma).length;
    return (
        <div className="border border-rose-200 rounded-lg overflow-hidden">
            <div className="bg-rose-50 px-3 py-2 flex items-center gap-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-rose-700">
                    Aufteilung – nur ein Teil ist betrieblich
                </span>
                <span className="text-xs text-slate-600">
                    {firmaCount} von {positionen.length} Positionen für die Firma
                </span>
            </div>
            <table className="w-full text-xs">
                <thead className="bg-slate-50 text-slate-600">
                    <tr>
                        <th className="text-center px-2 py-1.5 font-medium w-8">✓</th>
                        <th className="text-left px-2 py-1.5 font-medium">Beschreibung</th>
                        <th className="text-right px-2 py-1.5 font-medium">Menge</th>
                        <th className="text-right px-2 py-1.5 font-medium">Einzel</th>
                        <th className="text-right px-2 py-1.5 font-medium">Brutto</th>
                        <th className="text-right px-2 py-1.5 font-medium">MwSt</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {positionen.map(p => (
                        <tr key={p.id} className={p.istFuerFirma ? 'bg-rose-50/60' : ''}>
                            <td className="px-2 py-1.5 text-center">
                                {p.istFuerFirma
                                    ? <CheckCircle2 className="w-4 h-4 text-rose-600 inline" />
                                    : <span className="text-slate-300">–</span>}
                            </td>
                            <td className="px-2 py-1.5 text-slate-800">{p.beschreibung || `Pos ${p.sortierung}`}</td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.menge != null
                                    ? `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 }).format(p.menge)}${p.einheit ? ' ' + p.einheit : ''}`
                                    : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.einzelpreis != null ? `${formatEuro(p.einzelpreis)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums font-medium">
                                {p.betragBrutto != null ? `${formatEuro(p.betragBrutto)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-500">
                                {p.mwstSatz != null ? `${p.mwstSatz}%` : '–'}
                            </td>
                        </tr>
                    ))}
                </tbody>
                <tfoot className="bg-rose-50/40 border-t border-rose-200">
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs font-semibold text-rose-700">
                            Summe für Firma
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums font-bold text-rose-700">
                            {formatEuro(beleg.betragFirmaBrutto)} €
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-rose-700">
                            {formatEuro(beleg.betragFirmaMwst)} €
                        </td>
                    </tr>
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs text-slate-500">
                            Netto / MwSt davon
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                            {formatEuro(beleg.betragFirmaNetto)} € netto
                        </td>
                        <td className="px-2 py-1.5"></td>
                    </tr>
                </tfoot>
            </table>
            <div className="px-3 py-2 text-xs text-slate-500 bg-white border-t border-slate-100">
                Auswahl wurde am Handy getroffen. Zum Korrigieren in der Mobile-App neu auswählen.
            </div>
        </div>
    );
}

const inputCls = 'w-full p-2.5 border border-slate-200 rounded-lg bg-white text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div>
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">{label}</label>
            {children}
        </div>
    );
}

function AuswertungView({ auswertung, loading, von, bis, onVonChange, onBisChange, onReload }: {
    auswertung: Auswertung | null;
    loading: boolean;
    von: string;
    bis: string;
    onVonChange: (v: string) => void;
    onBisChange: (v: string) => void;
    onReload: () => void;
}) {
    const typLabel: Record<SachkontoTyp, string> = {
        AUFWAND: 'Aufwand', ERTRAG: 'Ertrag', PRIVAT: 'Privat', NEUTRAL: 'Neutral',
    };
    const typFarbe: Record<SachkontoTyp, string> = {
        AUFWAND: 'bg-amber-100 text-amber-700',
        ERTRAG: 'bg-emerald-100 text-emerald-700',
        PRIVAT: 'bg-fuchsia-100 text-fuchsia-700',
        NEUTRAL: 'bg-slate-100 text-slate-600',
    };

    const ergebnis = auswertung
        ? (auswertung.summeErtrag - auswertung.summeAufwand)
        : 0;

    return (
        <div className="space-y-4">
            <Card className="p-4 flex flex-wrap items-end gap-3">
                <Field label="Von">
                    <input type="date" value={von} onChange={e => onVonChange(e.target.value)} className={inputCls} />
                </Field>
                <Field label="Bis">
                    <input type="date" value={bis} onChange={e => onBisChange(e.target.value)} className={inputCls} />
                </Field>
                <Button onClick={onReload} disabled={loading}>
                    {loading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <RefreshCw className="w-4 h-4 mr-2" />}
                    Aktualisieren
                </Button>
            </Card>

            {loading ? (
                <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-rose-500" /></div>
            ) : !auswertung ? (
                <Card className="p-12 text-center text-slate-500"><BarChart3 className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>Noch keine Auswertung geladen.</p></Card>
            ) : (
                <>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        <KpiTile label="Erträge" value={`${formatEuro(auswertung.summeErtrag)} €`} icon={<Banknote className="w-5 h-5 text-emerald-600" />} />
                        <KpiTile label="Aufwand" value={`${formatEuro(auswertung.summeAufwand)} €`} icon={<CreditCard className="w-5 h-5 text-amber-600" />} />
                        <KpiTile label="Privatentnahmen" value={`${formatEuro(auswertung.summePrivat)} €`} icon={<Wallet className="w-5 h-5 text-fuchsia-600" />} />
                        <KpiTile label="Ergebnis" value={`${formatEuro(ergebnis)} €`} icon={<BarChart3 className={`w-5 h-5 ${ergebnis < 0 ? 'text-red-500' : 'text-emerald-600'}`} />} highlight />
                    </div>

                    {auswertung.summeOhneKonto > 0 && (
                        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-800 inline-flex items-center gap-2">
                            <AlertCircle className="w-4 h-4" />
                            {formatEuro(auswertung.summeOhneKonto)} € sind noch keinem Sachkonto zugeordnet.
                        </div>
                    )}

                    <Card className="overflow-hidden">
                        <table className="w-full text-sm">
                            <thead className="bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="text-left px-4 py-2 font-medium">Nr.</th>
                                    <th className="text-left px-4 py-2 font-medium">Konto</th>
                                    <th className="text-left px-4 py-2 font-medium">Typ</th>
                                    <th className="text-right px-4 py-2 font-medium">Belege</th>
                                    <th className="text-right px-4 py-2 font-medium">Summe brutto</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {auswertung.zeilen.length === 0 ? (
                                    <tr><td colSpan={5} className="text-center py-8 text-slate-400">Keine validierten Belege im Zeitraum.</td></tr>
                                ) : auswertung.zeilen.map((z, i) => (
                                    <tr key={z.sachkontoId ?? `none-${i}`} className="hover:bg-slate-50">
                                        <td className="px-4 py-2 text-slate-500 tabular-nums">{z.nummer ?? '–'}</td>
                                        <td className="px-4 py-2 font-medium text-slate-900">{z.bezeichnung}</td>
                                        <td className="px-4 py-2">
                                            {z.kontoTyp ? (
                                                <span className={`text-xs px-2 py-0.5 rounded-full ${typFarbe[z.kontoTyp]}`}>
                                                    {typLabel[z.kontoTyp]}
                                                </span>
                                            ) : (
                                                <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">offen</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-2 text-right tabular-nums">{z.anzahlBelege}</td>
                                        <td className="px-4 py-2 text-right font-semibold tabular-nums">{formatEuro(z.summe)} €</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </Card>
                </>
            )}
        </div>
    );
}

function BelegPreview({ belegId, mimeType, originalDateiname }: {
    belegId: number; mimeType?: string | null; originalDateiname?: string | null;
}) {
    const src = `/api/buchhaltung/belege/${belegId}/datei`;
    const istPdf = mimeType?.includes('pdf') || originalDateiname?.toLowerCase().endsWith('.pdf');
    const istBild = mimeType?.startsWith('image/');

    if (istPdf) {
        return <iframe src={src} title={originalDateiname ?? 'Beleg-PDF'} className="w-full h-full min-h-[400px] bg-white rounded border border-slate-200" />;
    }
    if (istBild) {
        return <img src={src} alt={originalDateiname ?? 'Beleg'} className="max-w-full max-h-[600px] rounded shadow" />;
    }
    return (
        <div className="text-center text-slate-500 p-8">
            <FileText className="w-12 h-12 mx-auto mb-2 opacity-30" />
            <p className="text-sm">{originalDateiname}</p>
            <a href={src} target="_blank" rel="noopener noreferrer" className="text-rose-600 text-sm underline mt-2 inline-block">
                Datei öffnen
            </a>
        </div>
    );
}
