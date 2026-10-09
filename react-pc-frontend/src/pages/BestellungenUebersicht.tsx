import { useState, useEffect, useCallback, useMemo } from 'react';
import DocumentPreviewModal from '../components/DocumentPreviewModal';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { RefreshCw, FileText, Package, Clock, CheckCircle, AlertCircle, X, FolderOpen, EyeOff, Eye, Archive, Search, ChevronDown, ChevronRight, Check, Sparkles } from 'lucide-react';
import { useToast } from '../components/ui/toast';
import { ZuordnungModal as BelegZuordnungModal } from '../components/ZuordnungModal';
import { useConfirm } from '../components/ui/confirm-dialog';
import { RechnungSuchenDialog } from '../features/bestellungen/RechnungSuchenDialog';
import { formatiereAlter, fortschrittsStufe, kettenBetrag, letzteBewegung, passtZurSuche, teileNachAlter, type KettenDokumentTyp } from '../features/bestellungen/bestellungenListe';
import { TREFFER_KLASSEN, formatiereQuote, rechnungVerknuepfen, rueckfrage, trefferStufe, type RechnungsVorschlag } from '../features/bestellungen/rechnungsVorschlag';
import { KettenGabel } from '../features/bestellungen/KettenGabel';
import { istRechnungsTyp, type KettenVerbindung } from '../features/bestellungen/kettenGraph';

// ========== Types ==========
interface DokumentRef {
    id: number;
    typ: KettenDokumentTyp;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragBrutto: number | null;
    betragNetto: number | null;
    liefertermin: string | null;
    /** Eingang im System – Ersatz, wenn kein Belegdatum erkannt wurde. */
    eingangsDatum?: string | null;
    dateiname: string;
    pdfUrl: string | null;
    /** Ausgeblendete Belege bleiben in der Kette, werden aber gedämpft gezeigt. */
    ausgeblendet?: boolean;
}

interface DokumentenKette {
    id: string;
    lieferantId: number | null;
    lieferantName: string | null;
    dokumente: DokumentRef[];
    /** Kanten innerhalb der Kette (Nachfolger → Vorgänger), z. B. Rechnung → Lieferschein. */
    verbindungen?: KettenVerbindung[];
    /** Nur bei „Bestellt“ gefüllt: die wahrscheinlichste Rechnung (ab 40 %). */
    rechnungsVorschlag?: RechnungsVorschlag | null;
}

type TabKey = 'offen' | 'laufend' | 'abgeschlossen' | 'zugeordnet' | 'ausgeblendet';

interface BestellungsUebersicht {
    offeneAnfragen: DokumentenKette[];
    laufendeBestellungen: DokumentenKette[];
    abgeschlossen: DokumentenKette[];
    zugeordnet: DokumentenKette[];
    ausgeblendet: DokumentenKette[];
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
    WERKSTOFFZEUGNIS: 'Werkstoffzeugnis',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
};

// ========== Ketten-Komponente ==========
const FORTSCHRITT_SCHRITTE = ['Angebot', 'Bestellt', 'Geliefert', 'Rechnung'];

function Fortschritt({ stufe }: { stufe: number }) {
    return (
        <ol className="grid grid-cols-4 gap-1.5" aria-label={`Fortschritt: ${stufe} von 4 Schritten`}>
            {FORTSCHRITT_SCHRITTE.map((name, idx) => {
                const erreicht = idx < stufe;
                return (
                    <li key={name} className="min-w-0">
                        <div className={`h-1.5 rounded-full ${erreicht ? 'bg-rose-500' : 'bg-slate-200'}`} />
                        <span className={`mt-1 block text-[11px] truncate ${erreicht ? 'text-slate-700 font-medium' : 'text-slate-400'}`}>
                            {name}
                            <span className="sr-only">{erreicht ? ' (erreicht)' : ' (offen)'}</span>
                        </span>
                    </li>
                );
            })}
        </ol>
    );
}

interface KetteCardProps {
    kette: DokumentenKette;
    heute: Date;
    onOpenPdf: (url: string, title: string) => void;
    showZuordnenButton?: boolean;
    onZuordnen?: (kette: DokumentenKette) => void;
    onAusblenden?: (kette: DokumentenKette) => void;
    onEinblenden?: (kette: DokumentenKette) => void;
    /** Rechnung suchen am offenen Ende der Gabel (nicht bei Angeboten und Ausgeblendetem). */
    onRechnungSuchen?: (kette: DokumentenKette) => void;
    /** Nach Abhängen oder Hochladen: Seite neu laden. */
    onGeaendert: () => void;
    onVorschlagUebernehmen?: (kette: DokumentenKette) => void;
    uebernehmenBusy?: boolean;
    /** Lange ohne Rechnung: bernsteinfarbener Hinweis. */
    ohneRechnungHinweis?: boolean;
    busy?: boolean;
}

function KetteCard({ kette, heute, onOpenPdf, showZuordnenButton, onZuordnen, onAusblenden, onEinblenden, onRechnungSuchen, onGeaendert, onVorschlagUebernehmen, uebernehmenBusy, ohneRechnungHinweis, busy }: KetteCardProps) {
    const betrag = kettenBetrag(kette);
    const alter = formatiereAlter(letzteBewegung(kette), heute);
    const vorschlag = onVorschlagUebernehmen ? kette.rechnungsVorschlag : null;
    // Bestellt oder geliefert, aber noch keine Rechnung: offenes Ende in der Gabel
    const offenesEnde = !kette.dokumente.some(d => istRechnungsTyp(d.typ))
        && kette.dokumente.some(d => d.typ === 'AUFTRAGSBESTAETIGUNG' || d.typ === 'LIEFERSCHEIN');

    return (
        <Card className={`p-4 mb-4 break-inside-avoid flex flex-col gap-3 hover:shadow-md transition-shadow ${ohneRechnungHinweis ? 'border-amber-300' : ''}`}>
            <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                    <h3 className="text-sm font-semibold text-slate-900 truncate">
                        {kette.lieferantName || 'Unbekannter Lieferant'}
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5 flex items-center gap-1">
                        <Clock className="w-3 h-3 flex-shrink-0" aria-hidden="true" />
                        Letzte Bewegung {alter}
                    </p>
                </div>
                {betrag && (
                    <div className="text-right flex-shrink-0">
                        <div className="text-sm font-semibold text-slate-900 tabular-nums">{formatEuro(betrag.betrag)} €</div>
                        <div className="text-[11px] text-slate-400">
                            {betrag.anzahl > 1 ? `laut ${betrag.anzahl} Rechnungen` : `laut ${TYP_LABELS[betrag.typ]}`}
                        </div>
                    </div>
                )}
            </div>

            {ohneRechnungHinweis && (
                <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-2 py-1.5 flex items-center gap-1.5">
                    <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />
                    Seit {alter.replace(/^vor /, '')} keine Rechnung
                </p>
            )}

            <Fortschritt stufe={fortschrittsStufe(kette)} />

            {/* Belege als Gabel wie bei git */}
            <KettenGabel
                dokumente={kette.dokumente}
                verbindungen={kette.verbindungen}
                onOpenPdf={onOpenPdf}
                offenesEnde={offenesEnde}
                onRechnungSuchen={onRechnungSuchen ? () => onRechnungSuchen(kette) : undefined}
                onGeaendert={onGeaendert}
            />

            {/* Wahrscheinliche Rechnung */}
            {vorschlag && (
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5 space-y-1.5" aria-label="Vorschlag für die Rechnung">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div className="text-sm text-slate-700 min-w-0 flex items-center gap-1.5 flex-wrap">
                            <Sparkles className="w-3.5 h-3.5 text-rose-600 flex-shrink-0" aria-hidden="true" />
                            <span>Wahrscheinlich: Rechnung</span>
                            {vorschlag.rechnung.pdfUrl ? (
                                <button
                                    type="button"
                                    onClick={() => onOpenPdf(vorschlag.rechnung.pdfUrl as string, vorschlag.rechnung.dokumentNummer || vorschlag.rechnung.dateiname)}
                                    className="font-semibold text-rose-700 underline underline-offset-2 hover:text-rose-800 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    {vorschlag.rechnung.dokumentNummer || vorschlag.rechnung.dateiname}
                                </button>
                            ) : (
                                <span className="font-semibold">{vorschlag.rechnung.dokumentNummer || vorschlag.rechnung.dateiname}</span>
                            )}
                            <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border tabular-nums ${TREFFER_KLASSEN[trefferStufe(vorschlag.trefferquote)]}`}>
                                {formatiereQuote(vorschlag.trefferquote)}
                            </span>
                        </div>
                        <div className="flex gap-2">
                            {vorschlag.rechnung.pdfUrl && (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => onOpenPdf(vorschlag.rechnung.pdfUrl as string, vorschlag.rechnung.dokumentNummer || vorschlag.rechnung.dateiname)}
                                    aria-label={`Vorschau Rechnung ${vorschlag.rechnung.dokumentNummer ?? vorschlag.rechnung.dateiname}`}
                                >
                                    <Eye className="w-4 h-4" aria-hidden="true" />
                                    Vorschau
                                </Button>
                            )}
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => onVorschlagUebernehmen?.(kette)}
                                disabled={uebernehmenBusy}
                                aria-label={`Rechnung ${vorschlag.rechnung.dokumentNummer ?? vorschlag.rechnung.dateiname} übernehmen`}
                            >
                                {uebernehmenBusy
                                    ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                    : <Check className="w-4 h-4" aria-hidden="true" />}
                                Übernehmen
                            </Button>
                        </div>
                    </div>
                    {vorschlag.gruende.length > 0 && (
                        <p className="text-[11px] text-slate-500">{vorschlag.gruende.join(' · ')}</p>
                    )}
                    {!vorschlag.eindeutig && (
                        <p className="text-[11px] text-amber-700 flex items-start gap-1">
                            <AlertCircle className="w-3 h-3 mt-0.5 flex-shrink-0" aria-hidden="true" />
                            Weitere Rechnung gleich wahrscheinlich – bitte über „Suchen“ prüfen.
                        </p>
                    )}
                </div>
            )}

            {/* Fußzeile: Aktionen */}
            {(showZuordnenButton || onAusblenden || onEinblenden) && (
                <div className="pt-3 border-t border-slate-100 flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex gap-2 flex-wrap">
                        {showZuordnenButton && onZuordnen && (
                            <Button onClick={() => onZuordnen(kette)} size="sm" variant="outline">
                                <FolderOpen className="w-4 h-4" aria-hidden="true" />
                                Projekten zuordnen
                            </Button>
                        )}
                        {onEinblenden && (
                            <Button onClick={() => onEinblenden(kette)} size="sm" variant="outline" disabled={busy}>
                                <Eye className="w-4 h-4" aria-hidden="true" />
                                Wieder einblenden
                            </Button>
                        )}
                    </div>
                    {onAusblenden && (
                        <Button
                            onClick={() => onAusblenden(kette)}
                            size="sm"
                            variant="ghost"
                            disabled={busy}
                            className="text-slate-500 hover:text-rose-700 hover:bg-rose-50 ml-auto"
                        >
                            <EyeOff className="w-4 h-4" aria-hidden="true" />
                            Ausblenden
                        </Button>
                    )}
                </div>
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
    /** Hervorgehoben (rose), wenn dort Arbeit wartet. */
    attention?: boolean;
}

function TabButton({ active, onClick, icon, label, count, attention }: TabButtonProps) {
    return (
        <button
            type="button"
            role="tab"
            aria-selected={active}
            onClick={onClick}
            className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-inset ${active
                ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                }`}
        >
            {icon}
            {label}
            <span
                className={`min-w-[1.5rem] px-1.5 py-0.5 rounded-full text-xs font-semibold tabular-nums text-center ${attention && count > 0
                    ? 'bg-rose-600 text-white'
                    : 'bg-slate-100 text-slate-600'}`}
            >
                {count}
            </span>
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

// ========== Hauptkomponente ==========
export default function BestellungenUebersicht() {
    const toast = useToast();
    const confirm = useConfirm();
    const [tab, setTab] = useState<TabKey>('laufend');
    const [heute] = useState(() => new Date());
    const [suche, setSucheText] = useState('');
    // null = automatisch (bei Suche mit Treffern aufgeklappt), sonst Wahl des Nutzers
    const [aelterManuell, setAelterManuell] = useState<boolean | null>(null);
    const [rechnungSuchenKette, setRechnungSuchenKette] = useState<DokumentenKette | null>(null);
    const [uebernehmenKetteId, setUebernehmenKetteId] = useState<string | null>(null);
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
    const zuordnungRechnung = zuordnungKette?.dokumente.find(d => d.typ === 'RECHNUNG') ?? null;

    const loadData = useCallback(async (silent = false) => {
        if (!silent) setLoading(true);
        setError(null);
        try {
            const res = await fetch('/api/bestellungen-uebersicht');
            if (!res.ok) throw new Error('Fehler beim Laden');
            const json = await res.json();
            setData(json);
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

    const neuLadenNachAenderung = useCallback(() => { void loadData(true); }, [loadData]);

    const handleOpenPdf = (url: string, title: string) => {
        setPreviewUrl(url);
        setPreviewTitle(title);
    };

    const [bulkBusy, setBulkBusy] = useState(false);

    const setSuche = (text: string) => {
        setSucheText(text);
        setAelterManuell(null);
    };

    /** Blendet viele Ketten auf einmal aus (Bestätigung, sofortige Anzeige, Chunks à 500 IDs). */
    const kettenAusblenden = useCallback(async (ketten: DokumentenKette[], frage: string, erfolg: string) => {
        if (ketten.length === 0) return;
        const ok = await confirm({
            title: 'Wirklich ausblenden?',
            message: frage,
            confirmLabel: 'Ausblenden',
            variant: 'warning',
        });
        if (!ok) return;

        setBulkBusy(true);
        const ids = new Set(ketten.map(k => k.id));
        // Optimistic Update: Ketten sofort in „Ausgeblendet“ verschieben
        setData(prev => prev ? {
            offeneAnfragen: prev.offeneAnfragen.filter(k => !ids.has(k.id)),
            laufendeBestellungen: prev.laufendeBestellungen.filter(k => !ids.has(k.id)),
            abgeschlossen: prev.abgeschlossen.filter(k => !ids.has(k.id)),
            zugeordnet: prev.zugeordnet.filter(k => !ids.has(k.id)),
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
            toast.success(erfolg);
        } catch {
            toast.error('Ausblenden fehlgeschlagen. Der aktuelle Stand wird neu geladen.');
            await loadData(true);
        } finally {
            setBulkBusy(false);
        }
    }, [confirm, loadData, toast]);

    const vorschlagUebernehmen = useCallback(async (kette: DokumentenKette) => {
        const vorschlag = kette.rechnungsVorschlag;
        if (!vorschlag) return;
        const frage = rueckfrage(vorschlag);
        if (frage && !(await confirm(frage))) return;
        setUebernehmenKetteId(kette.id);
        try {
            await rechnungVerknuepfen(vorschlag.bestellDokumentId, vorschlag.rechnung.id);
            toast.success(vorschlag.rechnung.dokumentNummer ? `Rechnung ${vorschlag.rechnung.dokumentNummer} zugeordnet.` : 'Rechnung zugeordnet.');
            await loadData(true);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Rechnung konnte nicht zugeordnet werden.');
        } finally {
            setUebernehmenKetteId(null);
        }
    }, [confirm, loadData, toast]);

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

    // Suche wirkt auf alle Tabs gleichzeitig
    const sucheAktiv = suche.trim() !== '';
    const gefiltert = useMemo(() => {
        const filter = (liste: DokumentenKette[] | undefined) => (liste ?? []).filter(k => passtZurSuche(k, suche));
        return {
            offen: filter(data?.offeneAnfragen),
            laufend: filter(data?.laufendeBestellungen),
            abgeschlossen: filter(data?.abgeschlossen),
            zugeordnet: filter(data?.zugeordnet),
            ausgeblendet: filter(data?.ausgeblendet),
        } satisfies Record<TabKey, DokumentenKette[]>;
    }, [data, suche]);

    const offeneTeile = useMemo(() => teileNachAlter(gefiltert.offen, heute), [gefiltert.offen, heute]);
    const laufendeTeile = useMemo(() => teileNachAlter(gefiltert.laufend, heute), [gefiltert.laufend, heute]);

    const tabs: { key: TabKey; label: string; icon: React.ReactNode; erklaerung: string; leer: string; zaehler: number; attention?: boolean }[] = [
        { key: 'offen', label: 'Angebote', icon: <AlertCircle className="w-4 h-4" />, zaehler: offeneTeile.aktuell.length,
            erklaerung: 'Angebote von Lieferanten, zu denen noch keine Bestellung kam.', leer: 'Keine offenen Angebote vorhanden.' },
        { key: 'laufend', label: 'Bestellt', icon: <Package className="w-4 h-4" />, zaehler: gefiltert.laufend.length,
            erklaerung: 'Bestellt oder geliefert – die Rechnung fehlt noch.', leer: 'Keine laufenden Bestellungen vorhanden.' },
        { key: 'abgeschlossen', label: 'Rechnung zuordnen', icon: <CheckCircle className="w-4 h-4" />, zaehler: gefiltert.abgeschlossen.length, attention: true,
            erklaerung: 'Die Rechnung ist da – ordne die Kosten jetzt einem Projekt oder einer Kostenstelle zu.', leer: 'Keine Bestellungen zum Zuordnen.' },
        { key: 'zugeordnet', label: 'Erledigt', icon: <FolderOpen className="w-4 h-4" />, zaehler: gefiltert.zugeordnet.length,
            erklaerung: 'Bestellungen, deren Kosten schon zugeordnet sind.', leer: 'Noch keine Bestellungen zugeordnet.' },
        { key: 'ausgeblendet', label: 'Ausgeblendet', icon: <Archive className="w-4 h-4" />, zaehler: gefiltert.ausgeblendet.length,
            erklaerung: 'Ausgeblendete Einträge. Sie lassen sich jederzeit wieder einblenden.', leer: 'Keine ausgeblendeten Einträge.' },
    ];
    const aktiverTab = tabs.find(t => t.key === tab) ?? tabs[0];
    const currentList = gefiltert[tab];
    const trefferWoanders = tabs.filter(t => t.key !== tab && gefiltert[t.key].length > 0);
    const aelterAufgeklappt = aelterManuell ?? (sucheAktiv && offeneTeile.aelter.length > 0);

    const renderKarte = (kette: DokumentenKette, optionen: { ohneRechnungHinweis?: boolean } = {}) => {
        const istAusgeblendetTab = tab === 'ausgeblendet';
        const istBestellt = tab === 'laufend';
        return (
            <KetteCard
                key={kette.id}
                kette={kette}
                heute={heute}
                onOpenPdf={handleOpenPdf}
                showZuordnenButton={tab === 'abgeschlossen'}
                onZuordnen={setZuordnungKette}
                onAusblenden={istAusgeblendetTab ? undefined : (k) => setKetteAusgeblendet(k, true)}
                onEinblenden={istAusgeblendetTab ? (k) => setKetteAusgeblendet(k, false) : undefined}
                // Rechnung suchen/hochladen: bei laufenden Bestellungen und bei Teillieferungen ohne Rechnung
                onRechnungSuchen={tab !== 'offen' && !istAusgeblendetTab ? setRechnungSuchenKette : undefined}
                onGeaendert={neuLadenNachAenderung}
                onVorschlagUebernehmen={istBestellt ? vorschlagUebernehmen : undefined}
                uebernehmenBusy={uebernehmenKetteId === kette.id}
                ohneRechnungHinweis={optionen.ohneRechnungHinweis}
                busy={busyKetteId === kette.id}
            />
        );
    };
    // Mauerwerk: Karten unterschiedlicher Höhe ohne Lücken (Tastatur-Reihenfolge spaltenweise)
    const kartenGitter = 'columns-1 md:columns-2 xl:columns-3 gap-4';

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
                        Angefragt, bestellt, geliefert – und was noch abgerechnet werden muss. Alles an einem Ort.
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
                        <RefreshCw className={`w-4 h-4 ${loading ? 'motion-safe:animate-spin' : ''}`} />
                        Aktualisieren
                    </Button>
                </div>
            </div>

            {/* Suche */}
            <div className="relative max-w-xl">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                <Input
                    type="text"
                    value={suche}
                    onChange={e => setSuche(e.target.value)}
                    placeholder="Lieferant, Nummer, Betrag oder Datum suchen …"
                    aria-label="Bestellungen durchsuchen"
                    className="h-10 pl-9 pr-10 bg-white rounded-lg border-slate-300"
                />
                {suche && (
                    <button
                        type="button"
                        onClick={() => setSuche('')}
                        aria-label="Suche löschen"
                        className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded text-slate-400 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                        <X className="w-4 h-4" />
                    </button>
                )}
            </div>

            {/* Tabs */}
            <div>
                <div role="tablist" aria-label="Bestellstatus" className="flex gap-2 border-b border-slate-200 pb-2 overflow-x-auto">
                    {tabs.map(t => (
                        <TabButton
                            key={t.key}
                            active={tab === t.key}
                            onClick={() => setTab(t.key)}
                            icon={t.icon}
                            label={t.label}
                            count={t.zaehler}
                            attention={t.attention}
                        />
                    ))}
                </div>
                <p className="text-sm text-slate-500 mt-3">{aktiverTab.erklaerung}</p>
            </div>

            {/* Content */}
            {loading ? (
                <div className={kartenGitter} role="status" aria-label="Bestellungen werden geladen">
                    {[0, 1, 2].map(i => (
                        <Card key={i} className="p-4 mb-4 break-inside-avoid space-y-3 motion-safe:animate-pulse">
                            <div className="h-4 w-1/2 rounded bg-slate-200" />
                            <div className="h-1.5 w-full rounded bg-slate-100" />
                            <div className="h-12 w-2/3 rounded bg-slate-100" />
                        </Card>
                    ))}
                </div>
            ) : error ? (
                <Card className="p-6 text-center text-red-600">
                    <AlertCircle className="w-8 h-8 mx-auto mb-2" />
                    {error}
                </Card>
            ) : (tab === 'offen' ? gefiltert.offen.length === 0 : currentList.length === 0) ? (
                <Card className="p-12 text-center text-slate-500 border-dashed">
                    <FileText className="w-12 h-12 mx-auto mb-4 text-slate-300" />
                    {sucheAktiv ? (
                        <>
                            <p className="text-lg font-medium">Keine Treffer in „{aktiverTab.label}“</p>
                            {trefferWoanders.length > 0 ? (
                                <div className="mt-3 space-y-2">
                                    <p className="text-sm">Zu „{suche.trim()}“ gibt es Treffer in:</p>
                                    <div className="flex flex-wrap justify-center gap-2">
                                        {trefferWoanders.map(t => (
                                            <Button key={t.key} size="sm" variant="outline" onClick={() => setTab(t.key)}>
                                                {t.label} ({gefiltert[t.key].length})
                                            </Button>
                                        ))}
                                    </div>
                                </div>
                            ) : (
                                <p className="text-sm mt-1">Auch in den anderen Reitern gibt es nichts dazu.</p>
                            )}
                        </>
                    ) : (
                        <>
                            <p className="text-lg font-medium">Keine Einträge</p>
                            <p className="text-sm mt-1">{aktiverTab.leer}</p>
                        </>
                    )}
                </Card>
            ) : (
                <div className="space-y-4">
                    {tab === 'zugeordnet' && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => void kettenAusblenden(
                                    currentList,
                                    `Wirklich alle ${currentList.length} erledigten Bestellungen ausblenden? Sie stehen danach unter „Ausgeblendet“.`,
                                    `${currentList.length} Bestellungen ausgeblendet`,
                                )}
                                disabled={bulkBusy}
                                variant="outline"
                                size="sm"
                                className="text-slate-600 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                            >
                                {bulkBusy ? (
                                    <RefreshCw className="w-4 h-4 motion-safe:animate-spin" />
                                ) : (
                                    <EyeOff className="w-4 h-4" />
                                )}
                                Alle ausblenden ({currentList.length})
                            </Button>
                        </div>
                    )}

                    {tab === 'offen' && (
                        <>
                            {offeneTeile.aktuell.length > 0 ? (
                                <div className={kartenGitter}>{offeneTeile.aktuell.map(k => renderKarte(k))}</div>
                            ) : (
                                <p className="text-sm text-slate-500">Keine neueren Angebote{sucheAktiv ? ' zur Suche' : ''}.</p>
                            )}
                            {offeneTeile.aelter.length > 0 && (
                                <section aria-label="Ältere Angebote" className="rounded-lg border border-slate-200 bg-white">
                                    <div className="flex items-center justify-between gap-3 p-2 pr-3">
                                        <button
                                            type="button"
                                            aria-expanded={aelterAufgeklappt}
                                            aria-controls="aeltere-angebote"
                                            onClick={() => setAelterManuell(!aelterAufgeklappt)}
                                            className="flex items-center gap-2 px-2 py-1.5 text-sm font-medium text-slate-700 rounded hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                        >
                                            {aelterAufgeklappt ? <ChevronDown className="w-4 h-4" aria-hidden="true" /> : <ChevronRight className="w-4 h-4" aria-hidden="true" />}
                                            Ältere Angebote ohne Bestellung ({offeneTeile.aelter.length})
                                        </button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            disabled={bulkBusy}
                                            onClick={() => void kettenAusblenden(
                                                offeneTeile.aelter,
                                                `Wirklich ${offeneTeile.aelter.length} ältere Angebote ausblenden? Sie stehen danach unter „Ausgeblendet“.`,
                                                `${offeneTeile.aelter.length} ältere Angebote ausgeblendet`,
                                            )}
                                        >
                                            {bulkBusy ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" /> : <EyeOff className="w-4 h-4" />}
                                            Alle ausblenden
                                        </Button>
                                    </div>
                                    {aelterAufgeklappt && (
                                        <div id="aeltere-angebote" className={`${kartenGitter} p-4 pt-2 border-t border-slate-100`}>
                                            {offeneTeile.aelter.map(k => renderKarte(k))}
                                        </div>
                                    )}
                                </section>
                            )}
                        </>
                    )}

                    {tab === 'laufend' && (
                        <>
                            {laufendeTeile.aelter.length > 0 && (
                                <section aria-label="Seit über 2 Monaten keine Rechnung" className="space-y-3">
                                    <h2 className="text-sm font-semibold text-amber-800 flex items-center gap-2">
                                        <AlertCircle className="w-4 h-4" aria-hidden="true" />
                                        Seit über 2 Monaten keine Rechnung ({laufendeTeile.aelter.length})
                                    </h2>
                                    <div className={kartenGitter}>
                                        {laufendeTeile.aelter.map(k => renderKarte(k, { ohneRechnungHinweis: true }))}
                                    </div>
                                </section>
                            )}
                            {laufendeTeile.aktuell.length > 0 && (
                                <section aria-label="Aktuelle Bestellungen" className="space-y-3">
                                    {laufendeTeile.aelter.length > 0 && (
                                        <h2 className="text-sm font-semibold text-slate-700">Aktuell ({laufendeTeile.aktuell.length})</h2>
                                    )}
                                    <div className={kartenGitter}>{laufendeTeile.aktuell.map(k => renderKarte(k))}</div>
                                </section>
                            )}
                        </>
                    )}

                    {tab !== 'offen' && tab !== 'laufend' && (
                        <div className={kartenGitter}>{currentList.map(k => renderKarte(k))}</div>
                    )}
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

            {rechnungSuchenKette && (
                <RechnungSuchenDialog
                    kette={rechnungSuchenKette}
                    onClose={() => setRechnungSuchenKette(null)}
                    onVerknuepft={() => {
                        setRechnungSuchenKette(null);
                        void loadData(true);
                    }}
                />
            )}

            {/* Zuordnung Modal */}
            {zuordnungRechnung && zuordnungKette && (
                <BelegZuordnungModal
                    geschaeftsdokumentId={zuordnungRechnung.id}
                    dokumentNummer={zuordnungRechnung.dokumentNummer}
                    lieferantName={zuordnungKette.lieferantName}
                    pdfUrl={zuordnungRechnung.pdfUrl}
                    onClose={() => setZuordnungKette(null)}
                    onSuccess={() => loadData()}
                />
            )}
        </div>
    );
}
