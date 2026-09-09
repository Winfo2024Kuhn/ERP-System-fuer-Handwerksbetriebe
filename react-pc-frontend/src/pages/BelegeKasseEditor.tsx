import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Receipt, Upload, Loader2, Search, Wallet, Banknote, CreditCard,
    Coins, FileQuestion, CheckCircle2, AlertCircle, Truck,
    RefreshCw, FileText, BookOpen, BarChart3,
    FileDown, Calendar, ArrowRightLeft, X,
} from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { SteuerberaterBelegExportModal } from '../components/SteuerberaterBelegExportModal';
import { KassenbuchTab } from '../components/kasse/KassenbuchTab';
import { BelegDetailModal } from '../components/kasse/BelegDetailModal';
import { KpiTile } from '../components/kasse/KassenbuchJournal';
import {
    KATEGORIE_FARBE, KATEGORIE_LABELS, KI_LABEL,
    formatDate, formatEuro, inputCls, isoDatum,
} from '../components/kasse/belegFormat';
import type {
    Auswertung, AuswertungZeile, Beleg, Kassenbuch, Sachkonto, SachkontoTyp, Zahlungsart,
} from '../types';

// ===================== Component =====================

// Kein eigener 'privat'-Tab mehr: Privatentnahme und Privateinlage sind
// Bargeld-Bewegungen und gehoeren damit ins Kassenbuch. Dort stehen die
// Buchungs-Knoepfe (KasseShortcuts) und die Buchungen selbst im T-Konto —
// ein zweiter Ort dafuer war nur eine weitere Stelle zum Suchen.
type Tab = 'eingang' | 'alle' | 'kasse' | 'auswertung';

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
    const heuteIso = isoDatum(new Date());
    const jahresanfangIso = isoDatum(new Date(new Date().getFullYear(), 0, 1));
    const monatsanfangIso = isoDatum(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
    const [auswVon, setAuswVon] = useState<string>(jahresanfangIso);
    const [auswBis, setAuswBis] = useState<string>(heuteIso);
    // Kassenbuch-Zeitraum: Vorgabe ist der laufende Monat. Vorher lud die Seite
    // immer ALLE Bewegungen seit Beginn — bei einem gefuehrten Kassenbuch ist
    // das nach ein paar Monaten unbrauchbar. Der Anfangsbestand stimmt trotzdem:
    // der Server summiert alles vor `von` in saldoStart auf.
    const [kasseVon, setKasseVon] = useState<string>(monatsanfangIso);
    const [kasseBis, setKasseBis] = useState<string>(heuteIso);
    const [kasseSearch, setKasseSearch] = useState('');
    const fileInputRef = useRef<HTMLInputElement>(null);
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
            // von/bis kann der Server laengst — genutzt hat es bisher niemand.
            const params = new URLSearchParams();
            if (kasseVon) params.set('von', kasseVon);
            if (kasseBis) params.set('bis', kasseBis);
            const res = await fetch(`/api/buchhaltung/kassenbuch?${params}`);
            if (res.ok) {
                setKassenbuch(await res.json());
            }
        } catch (e) {
            console.error('Kassenbuch laden fehlgeschlagen', e);
        } finally {
            setKassenLoading(false);
        }
    }, [kasseVon, kasseBis]);

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
                    {activeTab === 'kasse' && (
                        <Button variant="outline" onClick={() => setMonatsExportOpen(true)}
                                title="Monatsexport für den Steuerberater als PDF">
                            <FileDown className="w-4 h-4 mr-2" />
                            Monats-Export (PDF)
                        </Button>
                    )}
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
                <KassenbuchTab
                    sachkonten={sachkonten}
                    kassenbuch={kassenbuch}
                    kassenLoading={kassenLoading}
                    kasseVon={kasseVon}
                    kasseBis={kasseBis}
                    onVonChange={setKasseVon}
                    onBisChange={setKasseBis}
                    kasseSearch={kasseSearch}
                    onSearchChange={setKasseSearch}
                    onSelectBeleg={id => {
                        const b = belege.find(x => x.id === id);
                        if (b) setEditing(b);
                    }}
                    onGeaendert={() => { loadBelege(); loadKassenbuch(); }}
                />
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
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="monats-export-titel"
                className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden"
            >
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <FileDown className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 id="monats-export-titel" className="font-bold text-slate-900">Kassenbuch-Export (Steuerberater)</h2>
                            <p className="text-xs text-slate-500">
                                Kassen-Konto im T-Konto-Format für einen Kalendermonat
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
                            Das PDF zeigt das Kassen-Konto als T-Konto (Eingang | Ausgang)
                            mit Anfangs- und Endsaldo. Übergib es zusammen mit dem Ordner
                            der Belegfotos an den Steuerberater.
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
                                ) : auswertung.zeilen.map((z: AuswertungZeile, i) => (
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
