import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Select } from '../components/ui/select-custom';
import {
    CheckCircle2, AlertTriangle, XCircle, ClipboardCheck, Users, FileText,
    Cpu, RefreshCw, Plus, Trash2, ChevronRight, X, Wrench, Flame,
} from 'lucide-react';
import { useFeatures } from '../hooks/useFeatures';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';

// ─── Typen ────────────────────────────────────────────────────────────────────

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer?: string;
    abgeschlossen?: boolean;
}

interface WpkStatus {
    schweisser: string; schweisserHinweis: string;
    wps: string;        wpsHinweis: string;
    werkstoffzeugnisse: string; werkstoffzeugnisseHinweis: string;
    echeck: string;     echeckHinweis: string;
}

interface WpsResponse {
    id: number;
    wpsNummer: string;
    bezeichnung?: string;
    norm: string;
    schweissProzes: string;
    grundwerkstoff?: string;
    gueltigBis?: string;
    gespeicherterDateiname?: string;
    autoZugewiesenDurchLeistungId?: number | null;
}

interface ZuweisungResponse {
    id: number;
    wpsId: number;
    wpsNummer: string;
    wpsBezeichnung?: string;
    schweisserId?: number;
    schweisserName?: string;
    schweisspruefer?: string;
    einsatzDatum?: string;
    bemerkung?: string;
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
}

interface Werkstoffzeugnis {
    id: number;
    schmelzNummer?: string;
    materialGuete?: string;
    normTyp: string;
    pruefDatum?: string;
    lieferantName?: string;
}

// ─── Hilfsfunktionen ──────────────────────────────────────────────────────────

function statusBorder(s: string) {
    return s === 'OK' ? 'border-green-200' : s === 'WARNUNG' ? 'border-yellow-300' : 'border-red-300';
}
function statusBg(s: string) {
    return s === 'OK' ? 'bg-green-50' : s === 'WARNUNG' ? 'bg-yellow-50' : 'bg-red-50';
}

function StatusBadge({ status }: { status: string }) {
    if (status === 'OK') return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-green-100 text-green-800 text-xs font-semibold">
            <CheckCircle2 className="w-3 h-3" /> OK
        </span>
    );
    if (status === 'WARNUNG') return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-yellow-100 text-yellow-800 text-xs font-semibold">
            <AlertTriangle className="w-3 h-3" /> Warnung
        </span>
    );
    return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-red-100 text-red-800 text-xs font-semibold">
            <XCircle className="w-3 h-3" /> Fehler
        </span>
    );
}

function StatusCard({ title, icon: Icon, status, hinweis }: {
    title: string; icon: React.ElementType; status: string; hinweis: string;
}) {
    return (
        <div className={`rounded-xl border-2 ${statusBorder(status)} ${statusBg(status)} p-5`}>
            <div className="flex items-start justify-between gap-3 mb-2">
                <div className="flex items-center gap-2">
                    <Icon className="w-5 h-5 text-slate-600 flex-shrink-0" />
                    <h3 className="font-semibold text-slate-800">{title}</h3>
                </div>
                <StatusBadge status={status} />
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">
                {hinweis || (status === 'OK' ? 'Alle Anforderungen erfüllt.' : '')}
            </p>
        </div>
    );
}

// Tab-Button-Komponente
function TabBtn({ label, active, onClick, count, alert }: {
    label: string; active: boolean; onClick: () => void; count?: number | string; alert?: boolean;
}) {
    return (
        <button
            onClick={onClick}
            className={cn(
                'px-4 py-2.5 text-sm font-medium rounded-t-lg transition-colors whitespace-nowrap cursor-pointer',
                active
                    ? 'bg-white border-t-2 border-x border-t-rose-500 border-x-slate-200 -mb-px text-rose-600'
                    : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50'
            )}
        >
            {label}
            {count !== undefined && (
                <span className={cn(
                    'ml-1.5 px-1.5 py-0.5 rounded-full text-xs font-semibold',
                    alert ? 'bg-red-100 text-red-700' : (active ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-500')
                )}>{count}</span>
            )}
        </button>
    );
}

// ─── Haupt-Komponente ─────────────────────────────────────────────────────────

type Tab = 'status' | 'wps' | 'werkstoff' | 'schweisser';

export default function WpkDashboardPage() {
    const features = useFeatures();
    const toast = useToast();

    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [loadingProjekte, setLoadingProjekte] = useState(true);
    const [activeTab, setActiveTab] = useState<Tab>('status');

    // WPK-Status
    const [wpkStatus, setWpkStatus] = useState<WpkStatus | null>(null);
    const [loadingStatus, setLoadingStatus] = useState(false);

    // WPS
    const [projektWps, setProjektWps] = useState<WpsResponse[]>([]);
    const [alleWps, setAlleWps] = useState<WpsResponse[]>([]);
    const [zuweisungen, setZuweisungen] = useState<ZuweisungResponse[]>([]);
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const [showWpsPickerModal, setShowWpsPickerModal] = useState(false);
    const [showZuweisungForm, setShowZuweisungForm] = useState(false);
    const [zuweisungForm, setZuweisungForm] = useState({
        wpsId: '', schweisserId: '', schweisspruefer: '', einsatzDatum: '', bemerkung: '',
    });
    const [savingZuweisung, setSavingZuweisung] = useState(false);

    // Werkstoffzeugnisse
    const [projektWz, setProjektWz] = useState<Werkstoffzeugnis[]>([]);
    const [alleWz, setAlleWz] = useState<Werkstoffzeugnis[]>([]);
    const [showWzPickerModal, setShowWzPickerModal] = useState(false);

    // ── Projekte laden ────────────────────────────────────────────────────────
    useEffect(() => {
        fetch('/api/projekte/simple')
            .then(r => r.json())
            .then((data: Projekt[]) => setProjekte(data.filter(p => !p.abgeschlossen)))
            .catch(console.error)
            .finally(() => setLoadingProjekte(false));
    }, []);

    // ── Mitarbeiter einmalig laden ────────────────────────────────────────────
    useEffect(() => {
        fetch('/api/mitarbeiter')
            .then(r => r.json())
            .then((data: Mitarbeiter[]) => setMitarbeiter(data))
            .catch(console.error);
    }, []);

    // ── Projektdaten laden wenn Projekt gewechselt ────────────────────────────
    const loadProjektData = useCallback(async (id: number) => {
        setLoadingStatus(true);
        setWpkStatus(null);
        setProjektWps([]);
        setZuweisungen([]);
        setProjektWz([]);

        try {
            const [statusRes, wpsRes, wzRes, zuweisRes, alleWpsRes, alleWzRes] = await Promise.all([
                fetch(`/api/en1090/wpk/${id}`),
                fetch(`/api/wps/projekt/${id}`),
                fetch(`/api/werkstoffzeugnisse/projekt/${id}`),
                fetch(`/api/wps/projekt/${id}/zuweisungen`),
                fetch('/api/wps'),
                fetch('/api/werkstoffzeugnisse'),
            ]);
            if (statusRes.ok) setWpkStatus(await statusRes.json());
            if (wpsRes.ok) setProjektWps(await wpsRes.json());
            if (wzRes.ok) setProjektWz(await wzRes.json());
            if (zuweisRes.ok) setZuweisungen(await zuweisRes.json());
            if (alleWpsRes.ok) setAlleWps(await alleWpsRes.json());
            if (alleWzRes.ok) setAlleWz(await alleWzRes.json());
        } catch (e) {
            console.error(e);
        } finally {
            setLoadingStatus(false);
        }
    }, []);

    const handleSelectProjekt = (id: number) => {
        setSelectedId(id);
        setActiveTab('status');
        loadProjektData(id);
    };

    // ── WPS-Zuweisung ─────────────────────────────────────────────────────────
    const assignWps = async (wpsId: number) => {
        if (!selectedId) return;
        const res = await fetch(`/api/wps/projekt/${selectedId}/${wpsId}`, { method: 'POST' });
        if (res.ok) {
            setProjektWps(prev => {
                const wps = alleWps.find(w => w.id === wpsId);
                return wps && !prev.find(p => p.id === wpsId) ? [...prev, wps] : prev;
            });
            toast.success('WPS zugeordnet');
        }
        setShowWpsPickerModal(false);
    };

    const unassignWps = async (wpsId: number) => {
        if (!selectedId) return;
        const res = await fetch(`/api/wps/projekt/${selectedId}/${wpsId}`, { method: 'DELETE' });
        if (res.ok) {
            setProjektWps(prev => prev.filter(w => w.id !== wpsId));
            setZuweisungen(prev => prev.filter(z => z.wpsId !== wpsId));
            toast.success('WPS entfernt');
        }
    };

    // ── Schweißer-Zuweisung speichern ─────────────────────────────────────────
    const saveZuweisung = async () => {
        if (!selectedId || !zuweisungForm.wpsId) return;
        setSavingZuweisung(true);
        try {
            const res = await fetch(`/api/wps/projekt/${selectedId}/zuweisungen`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    wpsId: Number(zuweisungForm.wpsId),
                    schweisserId: zuweisungForm.schweisserId ? Number(zuweisungForm.schweisserId) : null,
                    schweisspruefer: zuweisungForm.schweisspruefer || null,
                    einsatzDatum: zuweisungForm.einsatzDatum || null,
                    bemerkung: zuweisungForm.bemerkung || null,
                }),
            });
            if (res.ok) {
                const neu: ZuweisungResponse = await res.json();
                setZuweisungen(prev => [...prev, neu]);
                setZuweisungForm({ wpsId: '', schweisserId: '', schweisspruefer: '', einsatzDatum: '', bemerkung: '' });
                setShowZuweisungForm(false);
                toast.success('Zuweisung gespeichert');
            }
        } finally {
            setSavingZuweisung(false);
        }
    };

    const deleteZuweisung = async (id: number) => {
        const res = await fetch(`/api/wps/zuweisungen/${id}`, { method: 'DELETE' });
        if (res.ok) setZuweisungen(prev => prev.filter(z => z.id !== id));
    };

    // ── Werkstoffzeugnis-Zuweisung ────────────────────────────────────────────
    const assignWz = async (wzId: number) => {
        if (!selectedId) return;
        const res = await fetch(`/api/werkstoffzeugnisse/${wzId}/projekt/${selectedId}`, { method: 'POST' });
        if (res.ok) {
            const wz = alleWz.find(w => w.id === wzId);
            if (wz && !projektWz.find(p => p.id === wzId)) setProjektWz(prev => [...prev, wz]);
            toast.success('Zeugnis zugeordnet');
        }
        setShowWzPickerModal(false);
    };

    const unassignWz = async (wzId: number) => {
        if (!selectedId) return;
        const res = await fetch(`/api/werkstoffzeugnisse/${wzId}/projekt/${selectedId}`, { method: 'DELETE' });
        if (res.ok) {
            setProjektWz(prev => prev.filter(w => w.id !== wzId));
            toast.success('Zeugnis entfernt');
        }
    };

    // ── Gesamt-Status ─────────────────────────────────────────────────────────
    const gesamtStatus = wpkStatus
        ? [wpkStatus.schweisser, wpkStatus.wps, wpkStatus.werkstoffzeugnisse, wpkStatus.echeck].includes('FEHLER')
            ? 'FEHLER'
            : [wpkStatus.schweisser, wpkStatus.wps, wpkStatus.werkstoffzeugnisse, wpkStatus.echeck].includes('WARNUNG')
            ? 'WARNUNG' : 'OK'
        : null;

    const projektOptions = projekte.map(p => ({
        value: String(p.id),
        label: p.auftragsnummer ? `${p.auftragsnummer} – ${p.bauvorhaben}` : p.bauvorhaben,
    }));

    // ── Feature-Guard ─────────────────────────────────────────────────────────
    if (!features.en1090) {
        return (
            <PageLayout ribbonCategory="EN 1090" title="WPK-PRÜFZENTRALE" subtitle="Werk­seigene Produktions­kontrolle – EN 1090 EXC 2">
                <div className="text-center py-16 text-slate-400">
                    <ClipboardCheck className="w-12 h-12 mx-auto mb-3 opacity-30" />
                    <p className="text-base font-medium">EN 1090 ist in dieser Installation nicht aktiviert.</p>
                </div>
            </PageLayout>
        );
    }

    const nichtZugewieseneWps = alleWps.filter(w => !projektWps.find(p => p.id === w.id));
    const nichtZugewieseneWz  = alleWz.filter(w => !projektWz.find(p => p.id === w.id));

    return (
        <PageLayout
            ribbonCategory="EN 1090"
            title="WPK-PRÜFZENTRALE"
            subtitle="Werkseigene Produktionskontrolle – alles zur EN 1090-Prüfung an einem Ort"
        >
            {/* ── Projekt-Auswahl ────────────────────────────────────────────── */}
            <Card className="p-5">
                <div className="flex flex-col sm:flex-row items-start sm:items-end gap-4">
                    <div className="flex-1 min-w-0">
                        <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                            Projekt auswählen
                        </label>
                        {loadingProjekte ? (
                            <div className="h-10 bg-slate-100 rounded-lg animate-pulse w-full max-w-md" />
                        ) : (
                            <div className="max-w-md">
                                <Select
                                    options={projektOptions}
                                    value={selectedId ? String(selectedId) : ''}
                                    onChange={v => v && handleSelectProjekt(Number(v))}
                                    placeholder="– Projekt wählen –"
                                />
                            </div>
                        )}
                    </div>
                    {selectedId && (
                        <button
                            onClick={() => loadProjektData(selectedId)}
                            disabled={loadingStatus}
                            className="flex items-center gap-2 px-4 py-2 rounded-lg border border-rose-300 text-rose-700 hover:bg-rose-50 text-sm font-medium disabled:opacity-50 transition-colors cursor-pointer"
                        >
                            <RefreshCw className={`w-4 h-4 ${loadingStatus ? 'animate-spin' : ''}`} />
                            Aktualisieren
                        </button>
                    )}
                </div>
            </Card>

            {/* ── Kein Projekt gewählt ───────────────────────────────────────── */}
            {!selectedId && (
                <div className="text-center py-20 text-slate-400">
                    <ClipboardCheck className="w-14 h-14 mx-auto mb-4 opacity-20" />
                    <p className="text-base font-semibold text-slate-500">Projekt auswählen, um die EN 1090 Prüfzentrale zu öffnen</p>
                    <p className="text-sm mt-1">Hier weisen Sie WPS, Schweißer und Werkstoffzeugnisse zu.</p>
                </div>
            )}

            {/* ── Tabs ──────────────────────────────────────────────────────── */}
            {selectedId && (
                <div>
                    {/* Gesamt-Status-Banner */}
                    {gesamtStatus && !loadingStatus && (
                        <div className={cn(
                            'rounded-xl p-4 flex items-center gap-3 mb-0',
                            gesamtStatus === 'OK' ? 'bg-green-50 border border-green-200' :
                            gesamtStatus === 'WARNUNG' ? 'bg-yellow-50 border border-yellow-200' :
                            'bg-red-50 border border-red-200'
                        )}>
                            {gesamtStatus === 'OK'      && <CheckCircle2 className="w-5 h-5 text-green-600 flex-shrink-0" />}
                            {gesamtStatus === 'WARNUNG' && <AlertTriangle className="w-5 h-5 text-yellow-600 flex-shrink-0" />}
                            {gesamtStatus === 'FEHLER'  && <XCircle className="w-5 h-5 text-red-600 flex-shrink-0" />}
                            <p className={cn('font-semibold text-sm',
                                gesamtStatus === 'OK' ? 'text-green-800' :
                                gesamtStatus === 'WARNUNG' ? 'text-yellow-800' : 'text-red-800'
                            )}>
                                Gesamtstatus: {gesamtStatus === 'OK' ? 'Konform – alle EN 1090 Anforderungen erfüllt' :
                                              gesamtStatus === 'WARNUNG' ? 'Handlungsbedarf – offene Punkte vorhanden' :
                                              'Nicht konform – kritische Mängel beheben'}
                            </p>
                        </div>
                    )}

                    {/* Tab-Leiste */}
                    <div className="flex gap-1 border-b border-slate-200 mt-4 overflow-x-auto">
                        <TabBtn label="WPK-Status"          active={activeTab === 'status'}    onClick={() => setActiveTab('status')} />
                        <TabBtn label="Schweißanweisungen"  active={activeTab === 'wps'}       onClick={() => setActiveTab('wps')}    count={projektWps.length} />
                        <TabBtn label="Werkstoffzeugnisse"  active={activeTab === 'werkstoff'} onClick={() => setActiveTab('werkstoff')} count={projektWz.length} />
                        <TabBtn label="Schweißer-Zertifikate" active={activeTab === 'schweisser'} onClick={() => setActiveTab('schweisser')} 
                            count={wpkStatus && wpkStatus.schweisser !== 'OK' ? '!' : undefined} 
                            alert={wpkStatus?.schweisser !== 'OK'} 
                        />
                    </div>

                    {/* ── Tab: WPK-Status ─────────────────────────────────── */}
                    {activeTab === 'status' && (
                        <div className="pt-5">
                            {loadingStatus ? (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    {[0,1,2,3].map(i => <div key={i} className="h-28 bg-slate-100 rounded-xl animate-pulse" />)}
                                </div>
                            ) : wpkStatus ? (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <StatusCard title="Schweißer-Zertifikate" icon={Users}         status={wpkStatus.schweisser}        hinweis={wpkStatus.schweisserHinweis} />
                                    <StatusCard title="Schweißanweisungen (WPS)" icon={FileText}   status={wpkStatus.wps}               hinweis={wpkStatus.wpsHinweis} />
                                    <StatusCard title="Werkstoffzeugnisse"     icon={ClipboardCheck} status={wpkStatus.werkstoffzeugnisse} hinweis={wpkStatus.werkstoffzeugnisseHinweis} />
                                    <StatusCard title="E-Check (BGV A3)"       icon={Cpu}          status={wpkStatus.echeck}            hinweis={wpkStatus.echeckHinweis} />
                                </div>
                            ) : null}
                        </div>
                    )}

                    {/* ── Tab: Schweißanweisungen ──────────────────────────── */}
                    {activeTab === 'wps' && (
                        <div className="pt-5 space-y-6">
                            {/* Zugewiesene WPS */}
                            <section>
                                <div className="flex items-center justify-between mb-3">
                                    <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                        <Wrench className="w-4 h-4 text-rose-500" />
                                        Zugewiesene Schweißanweisungen
                                    </h3>
                                    <button
                                        onClick={() => setShowWpsPickerModal(true)}
                                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 text-white text-sm font-medium hover:bg-rose-700 transition-colors cursor-pointer"
                                    >
                                        <Plus className="w-4 h-4" /> WPS zuordnen
                                    </button>
                                </div>

                                {projektWps.length === 0 ? (
                                    <div className="border-2 border-dashed border-slate-200 rounded-xl py-10 text-center text-slate-400">
                                        <FileText className="w-8 h-8 mx-auto mb-2 opacity-30" />
                                        <p className="text-sm">Noch keine WPS zugeordnet. Klicke auf „WPS zuordnen".</p>
                                    </div>
                                ) : (
                                    <div className="border border-slate-200 rounded-xl overflow-hidden">
                                        <table className="w-full text-sm">
                                            <thead className="bg-slate-50 border-b border-slate-200">
                                                <tr>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">WPS-Nr.</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Bezeichnung</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Prozess</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Quelle</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Gültig bis</th>
                                                    <th className="px-4 py-2.5" aria-label="Aktionen" />
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {projektWps.map((wps, i) => (
                                                    <tr key={wps.id} className={cn('border-b border-slate-100 last:border-0', i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50')}>
                                                        <td className="px-4 py-2.5 font-mono font-medium text-rose-700">{wps.wpsNummer}</td>
                                                        <td className="px-4 py-2.5 text-slate-700">{wps.bezeichnung || '–'}</td>
                                                        <td className="px-4 py-2.5 text-slate-500">{wps.schweissProzes}</td>
                                                        <td className="px-4 py-2.5">
                                                            {wps.autoZugewiesenDurchLeistungId ? (
                                                                <span
                                                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 text-xs font-medium border border-rose-200"
                                                                    title="Automatisch über eine Leistung im Angebot/Auftrag zugeordnet"
                                                                >
                                                                    <Flame className="w-3 h-3" /> Auto (Leistung)
                                                                </span>
                                                            ) : (
                                                                <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium">
                                                                    Manuell
                                                                </span>
                                                            )}
                                                        </td>
                                                        <td className="px-4 py-2.5 text-slate-500">
                                                            {wps.gueltigBis ? new Date(wps.gueltigBis).toLocaleDateString('de-DE') : '–'}
                                                        </td>
                                                        <td className="px-4 py-2.5 text-right">
                                                            <button
                                                                onClick={() => unassignWps(wps.id)}
                                                                className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                                                                title="WPS entfernen"
                                                            >
                                                                <X className="w-4 h-4" />
                                                            </button>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </section>

                            {/* Schweißer-Zuweisungen */}
                            <section>
                                <div className="flex items-center justify-between mb-3">
                                    <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                        <Users className="w-4 h-4 text-rose-500" />
                                        Schweißer-Zuweisungen
                                        <span className="text-xs font-normal text-slate-400">(mit Datum &amp; Prüfer)</span>
                                    </h3>
                                    <button
                                        onClick={() => setShowZuweisungForm(true)}
                                        disabled={projektWps.length === 0}
                                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-rose-300 text-rose-700 text-sm font-medium hover:bg-rose-50 transition-colors disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed"
                                    >
                                        <Plus className="w-4 h-4" /> Zuweisung hinzufügen
                                    </button>
                                </div>

                                {/* Formular */}
                                {showZuweisungForm && (
                                    <Card className="p-4 mb-4 border-rose-200 bg-rose-50">
                                        <h4 className="text-sm font-semibold text-rose-800 mb-3">Neue Schweißer-Zuweisung</h4>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                            <div>
                                                <label className="block text-xs font-medium text-slate-600 mb-1">Schweißanweisung *</label>
                                                <Select
                                                    options={projektWps.map(w => ({ value: String(w.id), label: `${w.wpsNummer}${w.bezeichnung ? ' – ' + w.bezeichnung : ''}` }))}
                                                    value={zuweisungForm.wpsId}
                                                    onChange={v => setZuweisungForm(f => ({ ...f, wpsId: v }))}
                                                    placeholder="WPS wählen..."
                                                />
                                            </div>
                                            <div>
                                                <label className="block text-xs font-medium text-slate-600 mb-1">Schweißer</label>
                                                <Select
                                                    options={mitarbeiter.map(m => ({ value: String(m.id), label: `${m.vorname} ${m.nachname}` }))}
                                                    value={zuweisungForm.schweisserId}
                                                    onChange={v => setZuweisungForm(f => ({ ...f, schweisserId: v }))}
                                                    placeholder="Mitarbeiter wählen..."
                                                />
                                            </div>
                                            <div>
                                                <label className="block text-xs font-medium text-slate-600 mb-1">Schweißprüfer</label>
                                                <input
                                                    type="text"
                                                    value={zuweisungForm.schweisspruefer}
                                                    onChange={e => setZuweisungForm(f => ({ ...f, schweisspruefer: e.target.value }))}
                                                    placeholder="Name des Prüfers"
                                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white"
                                                />
                                            </div>
                                            <div>
                                                <label className="block text-xs font-medium text-slate-600 mb-1">Einsatzdatum</label>
                                                <input
                                                    type="date"
                                                    value={zuweisungForm.einsatzDatum}
                                                    onChange={e => setZuweisungForm(f => ({ ...f, einsatzDatum: e.target.value }))}
                                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white"
                                                />
                                            </div>
                                            <div className="sm:col-span-2 lg:col-span-2">
                                                <label className="block text-xs font-medium text-slate-600 mb-1">Bemerkung</label>
                                                <input
                                                    type="text"
                                                    value={zuweisungForm.bemerkung}
                                                    onChange={e => setZuweisungForm(f => ({ ...f, bemerkung: e.target.value }))}
                                                    placeholder="Optional"
                                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white"
                                                />
                                            </div>
                                        </div>
                                        <div className="flex gap-2 mt-3 justify-end">
                                            <button
                                                onClick={() => setShowZuweisungForm(false)}
                                                className="px-3 py-1.5 text-sm border border-slate-300 rounded-lg text-slate-600 hover:bg-slate-50 cursor-pointer"
                                            >
                                                Abbrechen
                                            </button>
                                            <button
                                                onClick={saveZuweisung}
                                                disabled={!zuweisungForm.wpsId || savingZuweisung}
                                                className="px-4 py-1.5 text-sm bg-rose-600 text-white rounded-lg hover:bg-rose-700 disabled:opacity-50 cursor-pointer"
                                            >
                                                Speichern
                                            </button>
                                        </div>
                                    </Card>
                                )}

                                {zuweisungen.length === 0 && !showZuweisungForm ? (
                                    <div className="border-2 border-dashed border-slate-200 rounded-xl py-8 text-center text-slate-400">
                                        <p className="text-sm">Noch keine Schweißer zugewiesen.</p>
                                    </div>
                                ) : zuweisungen.length > 0 ? (
                                    <div className="border border-slate-200 rounded-xl overflow-hidden">
                                        <table className="w-full text-sm">
                                            <thead className="bg-slate-50 border-b border-slate-200">
                                                <tr>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">WPS</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Schweißer</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Schweißprüfer</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Datum</th>
                                                    <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Bemerkung</th>
                                                    <th className="px-4 py-2.5" aria-label="Aktionen" />
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {zuweisungen.map((z, i) => (
                                                    <tr key={z.id} className={cn('border-b border-slate-100 last:border-0', i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50')}>
                                                        <td className="px-4 py-2.5 font-mono text-rose-700 font-medium">{z.wpsNummer}</td>
                                                        <td className="px-4 py-2.5 text-slate-700">{z.schweisserName || '–'}</td>
                                                        <td className="px-4 py-2.5 text-slate-700">{z.schweisspruefer || '–'}</td>
                                                        <td className="px-4 py-2.5 text-slate-500">{z.einsatzDatum ? new Date(z.einsatzDatum).toLocaleDateString('de-DE') : '–'}</td>
                                                        <td className="px-4 py-2.5 text-slate-400 text-xs">{z.bemerkung || '–'}</td>
                                                        <td className="px-4 py-2.5 text-right">
                                                            <button
                                                                onClick={() => deleteZuweisung(z.id)}
                                                                className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : null}
                            </section>
                        </div>
                    )}

                    {/* ── Tab: Werkstoffzeugnisse ──────────────────────────── */}
                    {activeTab === 'werkstoff' && (
                        <div className="pt-5">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                    <ClipboardCheck className="w-4 h-4 text-rose-500" />
                                    Werkstoffzeugnisse für dieses Projekt
                                </h3>
                                <button
                                    onClick={() => setShowWzPickerModal(true)}
                                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 text-white text-sm font-medium hover:bg-rose-700 transition-colors cursor-pointer"
                                >
                                    <Plus className="w-4 h-4" /> Zeugnis zuordnen
                                </button>
                            </div>

                            {projektWz.length === 0 ? (
                                <div className="border-2 border-dashed border-slate-200 rounded-xl py-10 text-center text-slate-400">
                                    <ClipboardCheck className="w-8 h-8 mx-auto mb-2 opacity-30" />
                                    <p className="text-sm">Noch keine Werkstoffzeugnisse zugeordnet.</p>
                                </div>
                            ) : (
                                <div className="border border-slate-200 rounded-xl overflow-hidden">
                                    <table className="w-full text-sm">
                                        <thead className="bg-slate-50 border-b border-slate-200">
                                            <tr>
                                                <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Schmelz-Nr.</th>
                                                <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Werkstoff</th>
                                                <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Typ</th>
                                                <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Prüfdatum</th>
                                                <th className="text-left px-4 py-2.5 font-semibold text-slate-600">Lieferant</th>
                                                <th className="px-4 py-2.5" />
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {projektWz.map((wz, i) => (
                                                <tr key={wz.id} className={cn('border-b border-slate-100 last:border-0', i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50')}>
                                                    <td className="px-4 py-2.5 font-mono text-slate-700">{wz.schmelzNummer || '–'}</td>
                                                    <td className="px-4 py-2.5 text-slate-700">{wz.materialGuete || '–'}</td>
                                                    <td className="px-4 py-2.5">
                                                        <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-mono">EN 10204 · {wz.normTyp}</span>
                                                    </td>
                                                    <td className="px-4 py-2.5 text-slate-500">{wz.pruefDatum ? new Date(wz.pruefDatum).toLocaleDateString('de-DE') : '–'}</td>
                                                    <td className="px-4 py-2.5 text-slate-500">{wz.lieferantName || '–'}</td>
                                                    <td className="px-4 py-2.5 text-right">
                                                        <button
                                                            onClick={() => unassignWz(wz.id)}
                                                            className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                                                        >
                                                            <X className="w-4 h-4" />
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}

                    {/* ── Tab: Schweißer-Zertifikate ───────────────────────── */}
                    {activeTab === 'schweisser' && (
                        <div className="pt-5">
                            {wpkStatus && wpkStatus.schweisser !== 'OK' && (
                                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2">
                                    <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-semibold text-red-800">Achtung: Zertifikats-Verlängerung prüfen</p>
                                        <p className="text-sm text-red-700 mt-0.5">{wpkStatus.schweisserHinweis}</p>
                                    </div>
                                </div>
                            )}

                            <div className="mb-3">
                                <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                    <Users className="w-4 h-4 text-rose-500" />
                                    Schweißer-Qualifikationen
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    Übersicht der den zugewiesenen Schweißern gehörenden Zertifikate.
                                    Zertifikate verwalten unter <ChevronRight className="inline w-3 h-3" /> EN 1090 → Schweißer-Zertifikate.
                                </p>
                            </div>

                            {zuweisungen.filter(z => z.schweisserId).length === 0 ? (
                                <div className="border-2 border-dashed border-slate-200 rounded-xl py-10 text-center text-slate-400">
                                    <Users className="w-8 h-8 mx-auto mb-2 opacity-30" />
                                    <p className="text-sm">Keine Schweißer zugewiesen. Im Tab „Schweißanweisungen" Zuweisungen anlegen.</p>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {Array.from(new Map(
                                        zuweisungen
                                            .filter(z => z.schweisserId)
                                            .map(z => [z.schweisserId, z])
                                    ).values()).map(z => (
                                        <Card key={z.schweisserId} className="p-4 flex items-center gap-3">
                                            <div className="w-9 h-9 rounded-full bg-rose-100 flex items-center justify-center flex-shrink-0">
                                                <Users className="w-4 h-4 text-rose-600" />
                                            </div>
                                            <div>
                                                <p className="font-semibold text-slate-800">{z.schweisserName}</p>
                                                <p className="text-xs text-slate-500">
                                                    Schweißprüfer: {z.schweisspruefer || '–'} · Einsatz: {z.einsatzDatum ? new Date(z.einsatzDatum).toLocaleDateString('de-DE') : '–'}
                                                </p>
                                            </div>
                                        </Card>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* ── Modal: WPS aus Pool auswählen ─────────────────────────────── */}
            {showWpsPickerModal && (
                <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col">
                        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
                            <h2 className="font-semibold text-slate-800">Schweißanweisung zuordnen</h2>
                            <button onClick={() => setShowWpsPickerModal(false)} className="p-1 rounded hover:bg-slate-100 cursor-pointer">
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <div className="overflow-y-auto flex-1 p-4">
                            {nichtZugewieseneWps.length === 0 ? (
                                <p className="text-slate-500 text-sm text-center py-8">Alle verfügbaren WPS sind bereits zugeordnet.</p>
                            ) : (
                                <div className="space-y-2">
                                    {nichtZugewieseneWps.map(wps => (
                                        <button
                                            key={wps.id}
                                            onClick={() => assignWps(wps.id)}
                                            className="w-full text-left flex items-center justify-between p-3 rounded-xl border border-slate-200 hover:border-rose-300 hover:bg-rose-50 transition-colors cursor-pointer group"
                                        >
                                            <div>
                                                <p className="font-mono font-semibold text-rose-700">{wps.wpsNummer}</p>
                                                <p className="text-sm text-slate-600">{wps.bezeichnung || wps.norm} · {wps.schweissProzes}</p>
                                            </div>
                                            <span className="text-xs text-rose-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">Zuordnen</span>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* ── Modal: Werkstoffzeugnis aus Pool auswählen ───────────────── */}
            {showWzPickerModal && (
                <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col">
                        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
                            <h2 className="font-semibold text-slate-800">Werkstoffzeugnis zuordnen</h2>
                            <button onClick={() => setShowWzPickerModal(false)} className="p-1 rounded hover:bg-slate-100 cursor-pointer">
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <div className="overflow-y-auto flex-1 p-4">
                            {nichtZugewieseneWz.length === 0 ? (
                                <p className="text-slate-500 text-sm text-center py-8">Alle verfügbaren Werkstoffzeugnisse sind bereits zugeordnet.</p>
                            ) : (
                                <div className="space-y-2">
                                    {nichtZugewieseneWz.map(wz => (
                                        <button
                                            key={wz.id}
                                            onClick={() => assignWz(wz.id)}
                                            className="w-full text-left flex items-center justify-between p-3 rounded-xl border border-slate-200 hover:border-rose-300 hover:bg-rose-50 transition-colors cursor-pointer group"
                                        >
                                            <div>
                                                <p className="font-semibold text-slate-800">{wz.materialGuete || wz.schmelzNummer || 'Werkstoffzeugnis'}</p>
                                                <p className="text-sm text-slate-500">
                                                    {wz.schmelzNummer ? `Schmelze: ${wz.schmelzNummer} · ` : ''}
                                                    EN 10204 Typ {wz.normTyp}
                                                    {wz.pruefDatum ? ` · ${new Date(wz.pruefDatum).toLocaleDateString('de-DE')}` : ''}
                                                </p>
                                            </div>
                                            <span className="text-xs text-rose-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">Zuordnen</span>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </PageLayout>
    );
}
