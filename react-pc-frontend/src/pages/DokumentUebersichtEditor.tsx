import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { Input } from '../components/ui/input';
import { PageLayout } from '../components/layout/PageLayout';
import { RefreshCw, FileText, ArrowUpRight, Building2, Search, Edit3, Lock, Ban, ShieldCheck, Files, User, Truck, X, BellRing } from 'lucide-react';
import DocumentPreviewModal, { type PreviewDoc } from '../components/DocumentPreviewModal';
import { KundeSearchModal, type KundeSearchItem } from '../components/KundeSearchModal';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../components/LieferantSearchModal';

type AusgangsTyp =
    | 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | 'RECHNUNG' | 'TEILRECHNUNG'
    | 'ABSCHLAGSRECHNUNG' | 'SCHLUSSRECHNUNG' | 'GUTSCHRIFT' | 'STORNO';

interface AusgangsDokumentDto {
    id: number;
    dokumentNummer: string;
    typ: AusgangsTyp;
    datum: string | null;
    betreff: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    gebucht: boolean;
    storniert: boolean;
    digitalAngenommen: boolean;
    kundeId: number | null;
    kundenName: string | null;
    projektId: number | null;
    projektAuftragsnummer: string | null;
}

interface EingangsDokumentDto {
    id: number;
    dokumentId: number | null;
    lieferantId: number | null;
    lieferantName: string | null;
    dokumentNummer: string | null;
    typ: string | null;
    dokumentDatum: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    bezahlt: boolean;
    originalDateiname: string | null;
    pdfUrl: string | null;
}

const AUSGANGS_TYP_LABEL: Record<AusgangsTyp, string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    RECHNUNG: 'Rechnung',
    TEILRECHNUNG: 'Teilrechnung',
    ABSCHLAGSRECHNUNG: 'Abschlagsrechnung',
    SCHLUSSRECHNUNG: 'Schlussrechnung',
    GUTSCHRIFT: 'Gutschrift',
    STORNO: 'Storno',
};

const AUSGANGS_TYP_BADGE: Record<AusgangsTyp, string> = {
    ANGEBOT: 'bg-sky-100 text-sky-700',
    AUFTRAGSBESTAETIGUNG: 'bg-indigo-100 text-indigo-700',
    RECHNUNG: 'bg-emerald-100 text-emerald-700',
    TEILRECHNUNG: 'bg-emerald-100 text-emerald-700',
    ABSCHLAGSRECHNUNG: 'bg-emerald-100 text-emerald-700',
    SCHLUSSRECHNUNG: 'bg-emerald-100 text-emerald-700',
    GUTSCHRIFT: 'bg-amber-100 text-amber-700',
    STORNO: 'bg-red-100 text-red-700',
};

const formatDate = (isoText: string | undefined | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

const formatEuro = (value: number | undefined | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
};

const currentYear = new Date().getFullYear();
const yearOptions = Array.from({ length: 10 }, (_, i) => ({
    value: String(currentYear - i),
    label: String(currentYear - i),
}));

const monthOptions = [
    { value: '', label: 'Alle Monate' },
    { value: '1', label: 'Januar' },
    { value: '2', label: 'Februar' },
    { value: '3', label: 'März' },
    { value: '4', label: 'April' },
    { value: '5', label: 'Mai' },
    { value: '6', label: 'Juni' },
    { value: '7', label: 'Juli' },
    { value: '8', label: 'August' },
    { value: '9', label: 'September' },
    { value: '10', label: 'Oktober' },
    { value: '11', label: 'November' },
    { value: '12', label: 'Dezember' },
];

const AUSGANGS_TYP_OPTIONS = [
    { value: '', label: 'Alle Arten' },
    { value: 'ANGEBOT', label: 'Angebot' },
    { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung' },
    { value: 'RECHNUNG', label: 'Rechnung' },
    { value: 'TEILRECHNUNG', label: 'Teilrechnung' },
    { value: 'ABSCHLAGSRECHNUNG', label: 'Abschlagsrechnung' },
    { value: 'SCHLUSSRECHNUNG', label: 'Schlussrechnung' },
    { value: 'GUTSCHRIFT', label: 'Gutschrift' },
    { value: 'STORNO', label: 'Storno' },
];

const EINGANGS_TYP_OPTIONS = [
    { value: '', label: 'Alle Arten' },
    { value: 'RECHNUNG', label: 'Rechnung' },
    { value: 'GUTSCHRIFT', label: 'Gutschrift' },
    { value: 'LIEFERSCHEIN', label: 'Lieferschein' },
    { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung' },
    { value: 'ANGEBOT', label: 'Angebot' },
    { value: 'BESTELLUNG', label: 'Bestellung' },
    { value: 'SONSTIGES', label: 'Sonstiges' },
];

export default function DokumentUebersichtEditor() {
    const [activeTab, setActiveTab] = useState<'ausgang' | 'eingang'>('ausgang');
    const [selectedYear, setSelectedYear] = useState(String(currentYear));
    const [selectedMonth, setSelectedMonth] = useState('');
    const [searchQuery, setSearchQuery] = useState('');

    // Erweiterte Filter
    const [filterDokumentNummer, setFilterDokumentNummer] = useState('');
    const [filterAusgangsTyp, setFilterAusgangsTyp] = useState('');
    const [filterEingangsTyp, setFilterEingangsTyp] = useState('');
    const [filterBetragMin, setFilterBetragMin] = useState('');
    const [filterBetragMax, setFilterBetragMax] = useState('');
    const [filterKunde, setFilterKunde] = useState<KundeSearchItem | null>(null);
    const [filterLieferant, setFilterLieferant] = useState<LieferantSuchErgebnis | null>(null);

    // Modal-Status
    const [showKundeModal, setShowKundeModal] = useState(false);
    const [showLieferantModal, setShowLieferantModal] = useState(false);

    const [ausgang, setAusgang] = useState<AusgangsDokumentDto[]>([]);
    const [eingang, setEingang] = useState<EingangsDokumentDto[]>([]);
    const [loading, setLoading] = useState(true);

    const [previewDoc, setPreviewDoc] = useState<PreviewDoc | null>(null);

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const baseParams = new URLSearchParams();
            if (selectedYear) baseParams.append('year', selectedYear);
            if (selectedMonth) baseParams.append('month', selectedMonth);
            if (searchQuery) baseParams.append('search', searchQuery);
            if (filterDokumentNummer) baseParams.append('dokumentNummer', filterDokumentNummer);
            if (filterBetragMin) baseParams.append('betragMin', filterBetragMin);
            if (filterBetragMax) baseParams.append('betragMax', filterBetragMax);

            const ausgangParams = new URLSearchParams(baseParams);
            if (filterAusgangsTyp) ausgangParams.append('typ', filterAusgangsTyp);
            if (filterKunde) ausgangParams.append('kundeId', String(filterKunde.id));

            const eingangParams = new URLSearchParams(baseParams);
            if (filterEingangsTyp) eingangParams.append('typ', filterEingangsTyp);
            if (filterLieferant) eingangParams.append('lieferantId', String(filterLieferant.id));

            const [ausgangRes, eingangRes] = await Promise.all([
                fetch(`/api/dokumentuebersicht/ausgang?${ausgangParams.toString()}`),
                fetch(`/api/dokumentuebersicht/eingang?${eingangParams.toString()}`),
            ]);

            if (ausgangRes.ok) setAusgang(await ausgangRes.json());
            if (eingangRes.ok) setEingang(await eingangRes.json());
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedYear, selectedMonth, searchQuery, filterDokumentNummer,
        filterAusgangsTyp, filterEingangsTyp, filterBetragMin, filterBetragMax,
        filterKunde, filterLieferant]);

    useEffect(() => {
        const timer = setTimeout(() => {
            loadData();
        }, 400);
        return () => clearTimeout(timer);
    }, [loadData]);

    const resetFilter = () => {
        setSearchQuery('');
        setFilterDokumentNummer('');
        setFilterAusgangsTyp('');
        setFilterEingangsTyp('');
        setFilterBetragMin('');
        setFilterBetragMax('');
        setFilterKunde(null);
        setFilterLieferant(null);
    };

    const hasActiveFilter = Boolean(
        searchQuery || filterDokumentNummer || filterAusgangsTyp || filterEingangsTyp
        || filterBetragMin || filterBetragMax || filterKunde || filterLieferant,
    );

    const ausgangSumme = useMemo(
        () => ausgang.reduce((s, d) => s + (d.betragBrutto || 0), 0),
        [ausgang],
    );
    const eingangSumme = useMemo(
        () => eingang.reduce((s, d) => s + (d.betragBrutto || 0), 0),
        [eingang],
    );

    const openAusgangsDokument = (doc: AusgangsDokumentDto) => {
        const params = new URLSearchParams();
        params.set('dokumentId', String(doc.id));
        if (doc.typ) params.set('dokumentTyp', doc.typ);
        if (doc.projektId) params.set('projektId', String(doc.projektId));
        window.open(`/dokument-editor?${params.toString()}`, '_blank', 'noopener');
    };

    return (
        <PageLayout
            ribbonCategory="Projektmanagement"
            title="DOKUMENTE"
            subtitle="Alle Geschäftsdokumente – Ausgang und Eingang"
            actions={
                <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                    <RefreshCw className={`w-4 h-4 mr-1 ${loading ? 'animate-spin' : ''}`} />
                    Aktualisieren
                </Button>
            }
        >
            {/* Filter Bar */}
            <Card className="p-4 mb-5 border-0 shadow-sm rounded-xl space-y-3">
                {/* Zeile 1: Zeitraum + Volltext + Reset */}
                <div className="flex flex-wrap items-center gap-4">
                    <div className="flex items-center gap-2">
                        <label className="text-sm font-medium text-slate-600">Jahr:</label>
                        <Select value={selectedYear} onChange={setSelectedYear} options={yearOptions} className="w-28" />
                    </div>
                    <div className="flex items-center gap-2">
                        <label className="text-sm font-medium text-slate-600">Monat:</label>
                        <Select value={selectedMonth} onChange={setSelectedMonth} options={monthOptions} className="w-40" />
                    </div>
                    <div className="flex-1 min-w-[200px] flex items-center gap-2 relative">
                        <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                            <Search className="w-4 h-4" />
                        </div>
                        <Input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Volltext: Betreff, Typ, Kunde, Lieferant..."
                            className="pl-9 w-full"
                        />
                    </div>
                    {hasActiveFilter && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={resetFilter}
                            className="text-rose-700 hover:bg-rose-50"
                        >
                            <X className="w-4 h-4 mr-1" />
                            Filter zurücksetzen
                        </Button>
                    )}
                </div>

                {/* Zeile 2: Erweiterte Filter */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
                    <div>
                        <label className="block text-xs font-medium text-slate-500 mb-1">Dokumentnummer</label>
                        <Input
                            value={filterDokumentNummer}
                            onChange={(e) => setFilterDokumentNummer(e.target.value)}
                            placeholder="z.B. 2025/01/00042"
                        />
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-slate-500 mb-1">Dokumentart</label>
                        {activeTab === 'ausgang' ? (
                            <Select
                                value={filterAusgangsTyp}
                                onChange={setFilterAusgangsTyp}
                                options={AUSGANGS_TYP_OPTIONS}
                                className="w-full"
                            />
                        ) : (
                            <Select
                                value={filterEingangsTyp}
                                onChange={setFilterEingangsTyp}
                                options={EINGANGS_TYP_OPTIONS}
                                className="w-full"
                            />
                        )}
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-slate-500 mb-1">
                            {activeTab === 'ausgang' ? 'Kunde' : 'Lieferant'}
                        </label>
                        {activeTab === 'ausgang' ? (
                            <button
                                type="button"
                                onClick={() => setShowKundeModal(true)}
                                className="w-full flex items-center gap-2 h-10 px-3 rounded-md border border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50 text-left transition-colors"
                            >
                                <User className="w-4 h-4 text-slate-400 flex-shrink-0" />
                                <span className={`flex-1 truncate text-sm ${filterKunde ? 'text-slate-900' : 'text-slate-400'}`}>
                                    {filterKunde ? filterKunde.name : 'Kunde wählen...'}
                                </span>
                                {filterKunde && (
                                    <X
                                        className="w-4 h-4 text-slate-400 hover:text-rose-600 flex-shrink-0"
                                        onClick={(e) => { e.stopPropagation(); setFilterKunde(null); }}
                                    />
                                )}
                            </button>
                        ) : (
                            <button
                                type="button"
                                onClick={() => setShowLieferantModal(true)}
                                className="w-full flex items-center gap-2 h-10 px-3 rounded-md border border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50 text-left transition-colors"
                            >
                                <Truck className="w-4 h-4 text-slate-400 flex-shrink-0" />
                                <span className={`flex-1 truncate text-sm ${filterLieferant ? 'text-slate-900' : 'text-slate-400'}`}>
                                    {filterLieferant ? filterLieferant.lieferantenname : 'Lieferant wählen...'}
                                </span>
                                {filterLieferant && (
                                    <X
                                        className="w-4 h-4 text-slate-400 hover:text-rose-600 flex-shrink-0"
                                        onClick={(e) => { e.stopPropagation(); setFilterLieferant(null); }}
                                    />
                                )}
                            </button>
                        )}
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-slate-500 mb-1">Betrag (Brutto, €)</label>
                        <div className="flex items-center gap-2">
                            <Input
                                type="number"
                                step="0.01"
                                value={filterBetragMin}
                                onChange={(e) => setFilterBetragMin(e.target.value)}
                                placeholder="von"
                                className="w-full"
                            />
                            <span className="text-slate-400">–</span>
                            <Input
                                type="number"
                                step="0.01"
                                value={filterBetragMax}
                                onChange={(e) => setFilterBetragMax(e.target.value)}
                                placeholder="bis"
                                className="w-full"
                            />
                        </div>
                    </div>
                </div>
            </Card>

            {/* Tabs */}
            <div className="flex gap-1 mb-5 border-b border-slate-200">
                <button
                    onClick={() => setActiveTab('ausgang')}
                    className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                        activeTab === 'ausgang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                    }`}
                >
                    <ArrowUpRight className="w-4 h-4" />
                    Ausgang
                </button>
                <button
                    onClick={() => setActiveTab('eingang')}
                    className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                        activeTab === 'eingang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                    }`}
                >
                    <Building2 className="w-4 h-4" />
                    Eingang
                </button>
            </div>

            {/* KPI */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-5">
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-rose-50 rounded-lg">
                            <Files className="w-4 h-4 text-rose-600" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">
                                Anzahl Dokumente
                            </p>
                            <p className="text-base font-bold text-slate-900">
                                {activeTab === 'ausgang' ? ausgang.length : eingang.length}
                            </p>
                        </div>
                    </div>
                </Card>
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-emerald-50 rounded-lg">
                            <FileText className="w-4 h-4 text-emerald-600" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">
                                Gesamtsumme Brutto
                            </p>
                            <p className="text-base font-bold text-slate-900">
                                {formatEuro(activeTab === 'ausgang' ? ausgangSumme : eingangSumme)} €
                            </p>
                        </div>
                    </div>
                </Card>
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-slate-50 rounded-lg">
                            <Search className="w-4 h-4 text-slate-500" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">
                                Filter
                            </p>
                            <p className="text-base font-bold text-slate-900">
                                {selectedYear}
                                {selectedMonth ? ` / ${monthOptions.find((m) => m.value === selectedMonth)?.label}` : ''}
                            </p>
                        </div>
                    </div>
                </Card>
            </div>

            {/* Content */}
            {activeTab === 'ausgang' ? (
                <AusgangsTabelle
                    loading={loading}
                    daten={ausgang}
                    onOpen={openAusgangsDokument}
                    onMahnVorschau={(d, stufe) => {
                        const stufenLabel = MAHN_STUFEN.find(s => s.wert === stufe)?.label ?? stufe;
                        setPreviewDoc({
                            url: `/api/ausgangs-dokumente/${d.id}/mahnung-vorschau?stufe=${stufe}`,
                            title: `${stufenLabel} (Vorschau) — Rechnung ${d.dokumentNummer}`,
                        });
                    }}
                />
            ) : (
                <EingangsTabelle
                    loading={loading}
                    daten={eingang}
                    onPreview={(d) => {
                        if (d.pdfUrl) {
                            setPreviewDoc({
                                url: d.pdfUrl,
                                title: d.dokumentNummer || d.originalDateiname || 'Dokument',
                            });
                        }
                    }}
                />
            )}

            {previewDoc && <DocumentPreviewModal doc={previewDoc} onClose={() => setPreviewDoc(null)} />}

            <KundeSearchModal
                isOpen={showKundeModal}
                onClose={() => setShowKundeModal(false)}
                onSelect={(k) => setFilterKunde(k)}
                currentKundeId={filterKunde?.id}
            />
            <LieferantSearchModal
                isOpen={showLieferantModal}
                onClose={() => setShowLieferantModal(false)}
                onSelect={(l) => setFilterLieferant(l)}
                currentLieferantId={filterLieferant?.id}
                nurAktive={false}
            />
        </PageLayout>
    );
}

// --- Subkomponenten ---

interface AusgangsTabelleProps {
    loading: boolean;
    daten: AusgangsDokumentDto[];
    onOpen: (doc: AusgangsDokumentDto) => void;
    onMahnVorschau: (doc: AusgangsDokumentDto, stufe: MahnStufe) => void;
}

type MahnStufe = 'ZAHLUNGSERINNERUNG' | 'ERSTE_MAHNUNG' | 'ZWEITE_MAHNUNG';

const MAHN_STUFEN: { wert: MahnStufe; label: string }[] = [
    { wert: 'ZAHLUNGSERINNERUNG', label: 'Zahlungserinnerung' },
    { wert: 'ERSTE_MAHNUNG', label: '1. Mahnung' },
    { wert: 'ZWEITE_MAHNUNG', label: '2. Mahnung' },
];

const RECHNUNGS_TYPEN: AusgangsTyp[] = ['RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG'];

function AusgangsTabelle({ loading, daten, onOpen, onMahnVorschau }: AusgangsTabelleProps) {
    if (loading) {
        return (
            <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                <p className="text-sm">Lade Dokumente...</p>
            </Card>
        );
    }
    if (daten.length === 0) {
        return (
            <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                <FileText className="w-10 h-10 mx-auto mb-2 text-slate-300" />
                <p className="text-sm font-medium text-slate-600">Keine Ausgangs-Dokumente gefunden.</p>
                <p className="text-xs mt-1 text-slate-400">Passen Sie die Filter an oder erstellen Sie ein Dokument im Projekt.</p>
            </Card>
        );
    }

    return (
        <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
            <div className="overflow-x-auto">
                <table className="w-full">
                    <thead>
                        <tr className="bg-slate-50 border-b border-slate-200">
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Typ
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Dok.-Nr.
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Datum
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Kunde
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Projekt
                            </th>
                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Brutto
                            </th>
                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Status
                            </th>
                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Mahn-Vorschau
                            </th>
                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Öffnen
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                        {daten.map((d) => (
                            <tr
                                key={d.id}
                                className="bg-white hover:bg-rose-50/40 transition-colors cursor-pointer"
                                onDoubleClick={() => onOpen(d)}
                            >
                                <td className="px-4 py-3">
                                    <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${AUSGANGS_TYP_BADGE[d.typ]}`}>
                                        {AUSGANGS_TYP_LABEL[d.typ] ?? d.typ}
                                    </span>
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-900 font-medium whitespace-nowrap">
                                    {d.dokumentNummer || '–'}
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                                    {formatDate(d.datum)}
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-600">
                                    {d.kundenName || '–'}
                                </td>
                                <td className="px-4 py-3 text-sm text-rose-600 font-medium">
                                    {d.projektAuftragsnummer || '–'}
                                </td>
                                <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                                    {d.betragBrutto != null ? `${formatEuro(d.betragBrutto)} €` : '–'}
                                </td>
                                <td className="px-4 py-3 text-center">
                                    <div className="flex justify-center gap-1">
                                        {d.storniert && (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-red-100 text-red-700 text-[11px]" title="Storniert">
                                                <Ban className="w-3 h-3" /> Storno
                                            </span>
                                        )}
                                        {d.digitalAngenommen && !d.storniert && (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[11px]" title="Digital angenommen">
                                                <ShieldCheck className="w-3 h-3" /> Angenommen
                                            </span>
                                        )}
                                        {d.gebucht && !d.storniert && !d.digitalAngenommen && (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-[11px]" title="Gebucht (gesperrt)">
                                                <Lock className="w-3 h-3" /> Gebucht
                                            </span>
                                        )}
                                        {!d.storniert && !d.digitalAngenommen && !d.gebucht && (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[11px]" title="Bearbeitbar">
                                                <Edit3 className="w-3 h-3" /> Entwurf
                                            </span>
                                        )}
                                    </div>
                                </td>
                                <td className="px-4 py-3 text-center">
                                    {RECHNUNGS_TYPEN.includes(d.typ) ? (
                                        <div className="inline-flex items-center gap-1">
                                            {MAHN_STUFEN.map(s => (
                                                <button
                                                    key={s.wert}
                                                    onClick={(e) => { e.stopPropagation(); onMahnVorschau(d, s.wert); }}
                                                    className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                                    title={`${s.label}-PDF anzeigen (nur Vorschau, nichts wird versendet)`}
                                                >
                                                    <BellRing className="w-3 h-3" />
                                                    {s.wert === 'ZAHLUNGSERINNERUNG' ? 'ZE' : s.wert === 'ERSTE_MAHNUNG' ? '1.' : '2.'}
                                                </button>
                                            ))}
                                        </div>
                                    ) : (
                                        <span className="text-xs text-slate-300">–</span>
                                    )}
                                </td>
                                <td className="px-4 py-3 text-center">
                                    <button
                                        onClick={() => onOpen(d)}
                                        className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                        title="Im Dokument-Editor öffnen"
                                    >
                                        <Edit3 className="w-4 h-4" />
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </Card>
    );
}

interface EingangsTabelleProps {
    loading: boolean;
    daten: EingangsDokumentDto[];
    onPreview: (doc: EingangsDokumentDto) => void;
}

function EingangsTabelle({ loading, daten, onPreview }: EingangsTabelleProps) {
    if (loading) {
        return (
            <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                <p className="text-sm">Lade Dokumente...</p>
            </Card>
        );
    }
    if (daten.length === 0) {
        return (
            <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                <FileText className="w-10 h-10 mx-auto mb-2 text-slate-300" />
                <p className="text-sm font-medium text-slate-600">Keine Eingangs-Dokumente gefunden.</p>
            </Card>
        );
    }

    return (
        <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
            <div className="overflow-x-auto">
                <table className="w-full">
                    <thead>
                        <tr className="bg-slate-50 border-b border-slate-200">
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Typ
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Lieferant
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Dok.-Nr.
                            </th>
                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Datum
                            </th>
                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Netto
                            </th>
                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Brutto
                            </th>
                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Dokument
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                        {daten.map((d) => (
                            <tr
                                key={d.id}
                                className="bg-white hover:bg-rose-50/40 transition-colors cursor-pointer"
                                onDoubleClick={() => d.pdfUrl && onPreview(d)}
                            >
                                <td className="px-4 py-3">
                                    <span className="inline-flex items-center px-2 py-1 rounded-full bg-slate-100 text-slate-700 text-xs font-medium">
                                        {d.typ ? formatLieferantTyp(d.typ) : '–'}
                                    </span>
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-900 font-medium">
                                    {d.lieferantName || '–'}
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-600">
                                    {d.dokumentNummer || '–'}
                                </td>
                                <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                                    {formatDate(d.dokumentDatum)}
                                </td>
                                <td className="px-4 py-3 text-right text-sm text-slate-600 whitespace-nowrap">
                                    {d.betragNetto != null ? `${formatEuro(d.betragNetto)} €` : '–'}
                                </td>
                                <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                                    {d.betragBrutto != null ? `${formatEuro(d.betragBrutto)} €` : '–'}
                                </td>
                                <td className="px-4 py-3 text-center">
                                    {d.pdfUrl ? (
                                        <button
                                            onClick={() => onPreview(d)}
                                            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                            title="Dokument öffnen"
                                        >
                                            <FileText className="w-5 h-5" />
                                        </button>
                                    ) : (
                                        <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                                            <FileText className="w-5 h-5" />
                                        </span>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </Card>
    );
}

function formatLieferantTyp(typ: string): string {
    const map: Record<string, string> = {
        RECHNUNG: 'Rechnung',
        GUTSCHRIFT: 'Gutschrift',
        LIEFERSCHEIN: 'Lieferschein',
        AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
        ANGEBOT: 'Angebot',
        BESTELLUNG: 'Bestellung',
        SONSTIGES: 'Sonstiges',
    };
    return map[typ] ?? typ;
}
