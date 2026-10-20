import { useState, useEffect, useCallback, useLayoutEffect, useMemo, useRef } from 'react';
import DocumentPreviewModal from '../components/DocumentPreviewModal';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { RefreshCw, FileText, FilePlus2, Package, Clock, CheckCircle, AlertCircle, X, FolderOpen, EyeOff, Eye, Archive, Search, ChevronDown, ChevronRight, Check, Sparkles } from 'lucide-react';
import { useToast } from '../components/ui/toast';
import { ZuordnungModal as BelegZuordnungModal } from '../components/ZuordnungModal';
import { useConfirm } from '../components/ui/confirm-dialog';
import { DokumentSuchenDialog } from '../features/bestellungen/DokumentSuchenDialog';
import type { VorschlagsTyp } from '../features/bestellungen/kettenVorschlag';
import { formatiereAlter, fortschrittsStufe, kettenBetrag, letzteBewegung, teileNachAlter, type KettenDokumentTyp } from '../features/bestellungen/bestellungenListe';
import {
    SEITEN_GROESSE, SUCHE_MAX_LAENGE, SUCHE_MIN_LAENGE, SUCHE_VERZOEGERUNG_MS,
    ausblendenOptimistisch, einblendenOptimistisch, gesamtJeGruppe, inLadeBloecke, ladeStand, normalisiereUebersicht, uebersichtUrl, wirksameSuche,
    type GruppenSchluessel, type LadeParameter, type UebersichtListen,
} from '../features/bestellungen/bestellungenNachladen';
import { TREFFER_KLASSEN, formatiereQuote, rechnungVerknuepfen, rueckfrage, trefferStufe, type RechnungsVorschlag } from '../features/bestellungen/rechnungsVorschlag';
import { KettenLinie } from '../features/bestellungen/KettenLinie';
import { KettenZuordnung } from '../features/bestellungen/KettenZuordnung';
import { anzahlOffeneRechnungen } from '../features/bestellungen/rechnungsLeisteLogik';
import { istRechnungsTyp, type KettenVerbindung } from '../features/bestellungen/kettenLinieLogik';

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
    /** Hat schon Projekt- oder Kostenstellen-Anteile (fehlt bei älteren Antworten = nein). */
    zugeordnet?: boolean;
    /** Als Lagerbestellung markiert (fehlt bei älteren Antworten = nein). */
    lagerbestellung?: boolean;
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

type BestellungsUebersicht = UebersichtListen<DokumentenKette>;

/** Reiter → Gruppe in der Server-Antwort. */
const GRUPPE_JE_TAB: Record<TabKey, GruppenSchluessel> = {
    offen: 'offeneAnfragen',
    laufend: 'laufendeBestellungen',
    abgeschlossen: 'abgeschlossen',
    zugeordnet: 'zugeordnet',
    ausgeblendet: 'ausgeblendet',
};

/** Wie die Liste geladen wird: entscheidet, welcher Ladezustand sichtbar ist. */
type LadeModus = 'voll' | 'still' | 'suche' | 'mehr';

/** Nächster scrollbarer Vorfahre (im Programm scrollt der Hauptbereich, nicht das Fenster). */
function scrollBereich(element: HTMLElement): HTMLElement | null {
    for (let el = element.parentElement; el; el = el.parentElement) {
        const { overflowY } = window.getComputedStyle(el);
        if ((overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight) return el;
    }
    return (document.scrollingElement as HTMLElement | null) ?? null;
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
    /** Rechnung suchen am offenen Ende der Kette (nicht bei Angeboten und Ausgeblendetem). */
    onRechnungSuchen?: (kette: DokumentenKette) => void;
    /** „Dokument hinzufügen“ in der Fußzeile: beliebige Dokumentart nachträglich zuordnen. */
    onDokumentHinzufuegen?: (kette: DokumentenKette) => void;
    /** Nach Abhängen oder Hochladen: Seite neu laden. */
    onGeaendert: () => void;
    onVorschlagUebernehmen?: (kette: DokumentenKette) => void;
    uebernehmenBusy?: boolean;
    /** Lange ohne Rechnung: bernsteinfarbener Hinweis. */
    ohneRechnungHinweis?: boolean;
    busy?: boolean;
}

function KetteCard({ kette, heute, onOpenPdf, showZuordnenButton, onZuordnen, onAusblenden, onEinblenden, onRechnungSuchen, onDokumentHinzufuegen, onGeaendert, onVorschlagUebernehmen, uebernehmenBusy, ohneRechnungHinweis, busy }: KetteCardProps) {
    const betrag = kettenBetrag(kette);
    const alter = formatiereAlter(letzteBewegung(kette), heute);
    const vorschlag = onVorschlagUebernehmen ? kette.rechnungsVorschlag : null;
    // Bestellt oder geliefert, aber noch keine Rechnung: offenes Ende in der Kette
    const offenesEnde = !kette.dokumente.some(d => istRechnungsTyp(d.typ))
        && kette.dokumente.some(d => d.typ === 'AUFTRAGSBESTAETIGUNG' || d.typ === 'LIEFERSCHEIN');
    const offeneRechnungen = anzahlOffeneRechnungen(kette.dokumente);

    return (
        <Card data-kette-id={kette.id} className={`p-4 mb-4 break-inside-avoid flex flex-col gap-3 hover:shadow-md transition-shadow ${ohneRechnungHinweis ? 'border-amber-300' : ''}`}>
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

            {/* Belege als gerade Linie wie git-Commits auf einem Branch */}
            <KettenLinie
                dokumente={kette.dokumente}
                verbindungen={kette.verbindungen}
                onOpenPdf={onOpenPdf}
                offenesEnde={offenesEnde}
                onRechnungSuchen={onRechnungSuchen ? () => onRechnungSuchen(kette) : undefined}
                abhaengbar
                onGeaendert={onGeaendert}
                listenName="Belege der Bestellung"
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
            {(showZuordnenButton || onDokumentHinzufuegen || onAusblenden || onEinblenden) && (
                <div className="pt-3 border-t border-slate-100 flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex gap-2 flex-wrap">
                        {showZuordnenButton && onZuordnen && (
                            <Button onClick={() => onZuordnen(kette)} size="sm" variant="outline">
                                <FolderOpen className="w-4 h-4" aria-hidden="true" />
                                {offeneRechnungen > 1 ? `Projekten zuordnen (${offeneRechnungen} offen)` : 'Projekten zuordnen'}
                            </Button>
                        )}
                        {onDokumentHinzufuegen && (
                            <Button
                                onClick={() => onDokumentHinzufuegen(kette)}
                                size="sm"
                                variant="outline"
                                title="Fehlendes Dokument dieser Bestellung suchen und zuordnen, z. B. ein Werkstoffzeugnis"
                            >
                                <FilePlus2 className="w-4 h-4" aria-hidden="true" />
                                Dokument hinzufügen
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
    /** Offener Suchdialog: Kette und vorgewählte Dokumentart (null = alle). */
    const [dokumentSuche, setDokumentSuche] = useState<{ kette: DokumentenKette; startTyp: VorschlagsTyp | null } | null>(null);
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

    /** Suchbegriff und Anzahl der letzten Anfrage – Neuladen nach Aktionen behält beides bei. */
    const parameterRef = useRef<LadeParameter>({ anzahl: SEITEN_GROESSE, suche: '' });
    /** Laufende Anfrage: Eine neue bricht die alte ab, damit keine veraltete Antwort die Liste überschreibt. */
    const anfrageRef = useRef<AbortController | null>(null);
    /** Suchbegriff, zu dem die angezeigte Liste gehört (kann beim Tippen kurz vom Eingabefeld abweichen). */
    const [geladeneSuche, setGeladeneSuche] = useState('');
    const [sucheLaedt, setSucheLaedt] = useState(false);
    const [mehrLaedt, setMehrLaedt] = useState(false);

    const loadData = useCallback(async (modus: LadeModus = 'voll') => {
        anfrageRef.current?.abort();
        const anfrage = new AbortController();
        anfrageRef.current = anfrage;
        const parameter = { ...parameterRef.current };
        if (modus === 'voll') setLoading(true);
        if (modus === 'suche') setSucheLaedt(true);
        if (modus === 'mehr') setMehrLaedt(true);
        try {
            const res = await fetch(uebersichtUrl(parameter), { signal: anfrage.signal });
            if (!res.ok) {
                const body = await res.json().catch(() => null) as { message?: unknown } | null;
                throw new Error(typeof body?.message === 'string' && body.message ? body.message : 'Bestellungen konnten nicht geladen werden.');
            }
            const json = await res.json();
            if (anfrageRef.current !== anfrage) return; // inzwischen neuere Anfrage unterwegs
            setData(normalisiereUebersicht<DokumentenKette>(json));
            setGeladeneSuche(parameter.suche);
            setError(null);
        } catch (err) {
            // Abgebrochen (neuere Anfrage oder Seite verlassen): kein Fehler für den Nutzer
            if (anfrage.signal.aborted || anfrageRef.current !== anfrage) return;
            const meldung = err instanceof Error ? err.message : 'Bestellungen konnten nicht geladen werden.';
            // Ohne angezeigte Liste ersetzt der Fehler die Liste, sonst bleibt sie stehen und ein Toast meldet es
            if (modus === 'voll') setError(meldung);
            else toast.error(meldung);
        } finally {
            if (anfrageRef.current === anfrage) {
                anfrageRef.current = null;
                setLoading(false);
                setSucheLaedt(false);
                setMehrLaedt(false);
            }
        }
    }, [toast]);

    useEffect(() => () => anfrageRef.current?.abort(), []);

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
        void loadData();
        void loadOffeneBelege();
    }, [loadData, loadOffeneBelege]);

    // Suche fragt den Server (alle Bestellungen, nicht nur die geladenen) – entprellt, Löschen sofort
    useEffect(() => {
        // Unter 2 Zeichen gilt wie keine Suche (der Server sieht es genauso)
        const begriff = wirksameSuche(suche);
        if (begriff === parameterRef.current.suche) return;
        const timer = window.setTimeout(() => {
            parameterRef.current = { anzahl: SEITEN_GROESSE, suche: begriff };
            void loadData('suche');
        }, begriff === '' ? 0 : SUCHE_VERZOEGERUNG_MS);
        return () => window.clearTimeout(timer);
    }, [suche, loadData]);

    const neuLadenNachAenderung = useCallback(() => { void loadData('still'); }, [loadData]);

    /** Letzte Karte vor „Mehr laden“ – bleibt nach dem Nachladen an derselben Stelle im Bild. */
    const listeRef = useRef<HTMLDivElement>(null);
    const scrollAnkerRef = useRef<{ id: string; top: number } | null>(null);

    const mehrLaden = () => {
        const karten = listeRef.current?.querySelectorAll<HTMLElement>('[data-kette-id]');
        const letzte = karten && karten.length > 0 ? karten[karten.length - 1] : null;
        scrollAnkerRef.current = letzte?.dataset.ketteId
            ? { id: letzte.dataset.ketteId, top: letzte.getBoundingClientRect().top }
            : null;
        parameterRef.current = { ...parameterRef.current, anzahl: parameterRef.current.anzahl + SEITEN_GROESSE };
        void loadData('mehr');
    };

    // Kommen ältere Karten in einem Abschnitt weiter oben dazu, Ansicht entsprechend nachziehen
    useLayoutEffect(() => {
        const anker = scrollAnkerRef.current;
        if (!anker || mehrLaedt) return;
        scrollAnkerRef.current = null;
        const karte = Array.from(listeRef.current?.querySelectorAll<HTMLElement>('[data-kette-id]') ?? [])
            .find(el => el.dataset.ketteId === anker.id);
        if (!karte) return;
        const verschoben = karte.getBoundingClientRect().top - anker.top;
        const bereich = verschoben !== 0 ? scrollBereich(karte) : null;
        if (bereich && typeof bereich.scrollBy === 'function') bereich.scrollBy(0, verschoben);
    }, [data, mehrLaedt]);

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
        // Optimistic Update: Ketten sofort in „Ausgeblendet“ verschieben, Zähler ziehen mit
        setData(prev => prev ? ausblendenOptimistisch(prev, ketten, heute) : prev);

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
            await loadData('still');
            toast.success(erfolg);
        } catch {
            toast.error('Ausblenden fehlgeschlagen. Der aktuelle Stand wird neu geladen.');
            await loadData('still');
        } finally {
            setBulkBusy(false);
        }
    }, [confirm, heute, loadData, toast]);

    const vorschlagUebernehmen = useCallback(async (kette: DokumentenKette) => {
        const vorschlag = kette.rechnungsVorschlag;
        if (!vorschlag) return;
        const frage = rueckfrage(vorschlag);
        if (frage && !(await confirm(frage))) return;
        setUebernehmenKetteId(kette.id);
        try {
            await rechnungVerknuepfen(vorschlag.bestellDokumentId, vorschlag.rechnung.id);
            toast.success(vorschlag.rechnung.dokumentNummer ? `Rechnung ${vorschlag.rechnung.dokumentNummer} zugeordnet.` : 'Rechnung zugeordnet.');
            await loadData('still');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Rechnung konnte nicht zugeordnet werden.');
        } finally {
            setUebernehmenKetteId(null);
        }
    }, [confirm, loadData, toast]);

    const setKetteAusgeblendet = useCallback(async (kette: DokumentenKette, ausblenden: boolean) => {
        setBusyKetteId(kette.id);

        // Optimistic Update: Kette sofort verschieben (samt Zählern), damit kein Ganzseiten-Spinner nötig ist
        setData(prev => {
            if (!prev) return prev;
            return ausblenden ? ausblendenOptimistisch(prev, [kette], heute) : einblendenOptimistisch(prev, kette, heute);
        });

        try {
            const res = await fetch(`/api/bestellungen-uebersicht/${ausblenden ? 'ausblenden' : 'einblenden'}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ dokumentIds: kette.dokumente.map(d => d.id) }),
            });
            if (!res.ok) throw new Error('Fehler');
            // Silent reload, damit beim Einblenden die Kette in die richtige Liste rutscht
            await loadData('still');
            toast.success(ausblenden ? 'Ausgeblendet' : 'Wieder eingeblendet');
        } catch {
            toast.error('Aktion fehlgeschlagen');
            // Server-Stand wiederherstellen, falls Optimistic Update falsch lag
            await loadData('still');
        } finally {
            setBusyKetteId(null);
        }
    }, [heute, loadData, toast]);

    // Die Suche filtert der Server über alle Bestellungen; sie wirkt auf alle Reiter gleichzeitig
    const sucheAktiv = geladeneSuche !== '';
    const gefiltert = useMemo(() => ({
        offen: data?.offeneAnfragen ?? [],
        laufend: data?.laufendeBestellungen ?? [],
        abgeschlossen: data?.abgeschlossen ?? [],
        zugeordnet: data?.zugeordnet ?? [],
        ausgeblendet: data?.ausgeblendet ?? [],
    } satisfies Record<TabKey, DokumentenKette[]>), [data]);
    const gesamt = useMemo(() => gesamtJeGruppe(data ?? normalisiereUebersicht<DokumentenKette>(null), heute), [data, heute]);
    const gesamtImTab = (key: TabKey) => gesamt[GRUPPE_JE_TAB[key]];

    const offeneTeile = useMemo(() => teileNachAlter(gefiltert.offen, heute), [gefiltert.offen, heute]);
    const laufendeTeile = useMemo(() => teileNachAlter(gefiltert.laufend, heute), [gefiltert.laufend, heute]);

    const tabs: { key: TabKey; label: string; icon: React.ReactNode; erklaerung: string; leer: string; zaehler: number; attention?: boolean }[] = [
        { key: 'offen', label: 'Angebote', icon: <AlertCircle className="w-4 h-4" />, zaehler: gesamt.offeneAnfragenAktuell,
            erklaerung: 'Angebote von Lieferanten, zu denen noch keine Bestellung kam.', leer: 'Keine offenen Angebote vorhanden.' },
        { key: 'laufend', label: 'Bestellt', icon: <Package className="w-4 h-4" />, zaehler: gesamt.laufendeBestellungen,
            erklaerung: 'Bestellt oder geliefert – die Rechnung fehlt noch.', leer: 'Keine laufenden Bestellungen vorhanden.' },
        { key: 'abgeschlossen', label: 'Rechnung zuordnen', icon: <CheckCircle className="w-4 h-4" />, zaehler: gesamt.abgeschlossen, attention: true,
            erklaerung: 'Die Rechnung ist da – ordne die Kosten jetzt einem Projekt oder einer Kostenstelle zu.', leer: 'Keine Bestellungen zum Zuordnen.' },
        { key: 'zugeordnet', label: 'Erledigt', icon: <FolderOpen className="w-4 h-4" />, zaehler: gesamt.zugeordnet,
            erklaerung: 'Bestellungen, deren Kosten schon zugeordnet sind.', leer: 'Noch keine Bestellungen zugeordnet.' },
        { key: 'ausgeblendet', label: 'Ausgeblendet', icon: <Archive className="w-4 h-4" />, zaehler: gesamt.ausgeblendet,
            erklaerung: 'Ausgeblendete Einträge. Sie lassen sich jederzeit wieder einblenden.', leer: 'Keine ausgeblendeten Einträge.' },
    ];
    const aktiverTab = tabs.find(t => t.key === tab) ?? tabs[0];
    const currentList = gefiltert[tab];
    const trefferWoanders = tabs.filter(t => t.key !== tab && gesamtImTab(t.key) > 0);
    const stand = data ? ladeStand(data, GRUPPE_JE_TAB[tab], heute) : { geladen: 0, gesamt: 0, vollstaendig: true, alt: null };
    const geladenImTab = stand.geladen;
    const gesamtAktiverTab = stand.gesamt;
    const alleGeladen = stand.vollstaendig;
    const zuKurzeSuche = suche.trim().length > 0 && suche.trim().length < SUCHE_MIN_LAENGE;
    const aelterAufgeklappt = aelterManuell ?? (sucheAktiv && offeneTeile.aelter.length > 0);

    const renderKarte = (kette: DokumentenKette, optionen: { ohneRechnungHinweis?: boolean } = {}) => {
        const istAusgeblendetTab = tab === 'ausgeblendet';
        const istBestellt = tab === 'laufend';
        // Bestellt, Rechnung zuordnen, Erledigt: dort kann noch ein Dokument fehlen
        const dokumenteErgaenzbar = tab === 'laufend' || tab === 'abgeschlossen' || tab === 'zugeordnet';
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
                onRechnungSuchen={tab !== 'offen' && !istAusgeblendetTab ? k => setDokumentSuche({ kette: k, startTyp: 'RECHNUNG' }) : undefined}
                onDokumentHinzufuegen={dokumenteErgaenzbar ? k => setDokumentSuche({ kette: k, startTyp: null }) : undefined}
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
    /** Ein Mauerwerk je Lade-Runde, damit „Mehr laden“ die sichtbaren Karten nicht umsortiert. */
    const renderBloecke = (teil: DokumentenKette[], optionen: { ohneRechnungHinweis?: boolean } = {}) =>
        inLadeBloecke(teil).map(block => (
            <div key={block[0].id} className={kartenGitter}>{block.map(k => renderKarte(k, optionen))}</div>
        ));
    const aelterGesamt = Math.max(gesamt.offeneAnfragen - gesamt.offeneAnfragenAktuell, offeneTeile.aelter.length);
    const aelterAlleGeladen = offeneTeile.aelter.length >= aelterGesamt;
    const ueberfaelligGesamt = Math.max(gesamt.laufendeBestellungen - gesamt.laufendeBestellungenAktuell, laufendeTeile.aelter.length);
    /** „(n)“, wenn alles geladen ist, sonst „(n von m)“. */
    const anzahlText = (geladen: number, insgesamt: number) => (geladen >= insgesamt ? `${geladen}` : `${geladen} von ${insgesamt}`);

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
            <div className="max-w-xl space-y-1.5">
                <div className="relative">
                    {sucheLaedt ? (
                        <RefreshCw className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 motion-safe:animate-spin" aria-hidden="true" />
                    ) : (
                        <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                    )}
                    <Input
                        type="text"
                        value={suche}
                        maxLength={SUCHE_MAX_LAENGE}
                        onChange={e => setSuche(e.target.value)}
                        placeholder="Lieferant, Nummer, Betrag oder Datum suchen …"
                        aria-label="Bestellungen durchsuchen"
                        aria-busy={sucheLaedt}
                        aria-describedby={zuKurzeSuche ? 'bestellungen-suche-hinweis' : undefined}
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
                {zuKurzeSuche && (
                    <p id="bestellungen-suche-hinweis" className="text-xs text-slate-500 pl-1">
                        Bitte mindestens {SUCHE_MIN_LAENGE} Zeichen eingeben.
                    </p>
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
            ) : currentList.length === 0 ? (
                <Card className="p-12 text-center text-slate-500 border-dashed">
                    <FileText className="w-12 h-12 mx-auto mb-4 text-slate-300" />
                    {sucheAktiv ? (
                        trefferWoanders.length > 0 ? (
                            <>
                                <p className="text-lg font-medium">Keine Treffer in „{aktiverTab.label}“</p>
                                <div className="mt-3 space-y-2">
                                    <p className="text-sm">Zu „{geladeneSuche}“ gibt es Treffer in:</p>
                                    <div className="flex flex-wrap justify-center gap-2">
                                        {trefferWoanders.map(t => (
                                            <Button key={t.key} size="sm" variant="outline" onClick={() => setTab(t.key)}>
                                                {t.label} ({gesamtImTab(t.key)})
                                            </Button>
                                        ))}
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                <p className="text-lg font-medium">Keine Bestellung passt zu „{geladeneSuche}“.</p>
                                <p className="text-sm mt-1">Durchsucht wurden alle Bestellungen in allen Reitern – auch die noch nicht geladenen.</p>
                            </>
                        )
                    ) : (
                        <>
                            <p className="text-lg font-medium">Keine Einträge</p>
                            <p className="text-sm mt-1">{aktiverTab.leer}</p>
                        </>
                    )}
                </Card>
            ) : (
                <div ref={listeRef} className="space-y-4 [overflow-anchor:none]">
                    {tab === 'zugeordnet' && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => void kettenAusblenden(
                                    currentList,
                                    alleGeladen
                                        ? `Wirklich alle ${currentList.length} erledigten Bestellungen ausblenden? Sie stehen danach unter „Ausgeblendet“.`
                                        : `Wirklich die ${currentList.length} angezeigten erledigten Bestellungen ausblenden? Sie stehen danach unter „Ausgeblendet“. Die ${gesamtAktiverTab - currentList.length} noch nicht geladenen bleiben hier.`,
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
                                {alleGeladen ? 'Alle ausblenden' : 'Angezeigte ausblenden'} ({currentList.length})
                            </Button>
                        </div>
                    )}

                    {tab === 'offen' && (
                        <>
                            {offeneTeile.aktuell.length > 0 ? (
                                renderBloecke(offeneTeile.aktuell)
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
                                            Ältere Angebote ohne Bestellung ({anzahlText(offeneTeile.aelter.length, aelterGesamt)})
                                        </button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            disabled={bulkBusy}
                                            onClick={() => void kettenAusblenden(
                                                offeneTeile.aelter,
                                                aelterAlleGeladen
                                                    ? `Wirklich ${offeneTeile.aelter.length} ältere Angebote ausblenden? Sie stehen danach unter „Ausgeblendet“.`
                                                    : `Wirklich die ${offeneTeile.aelter.length} angezeigten älteren Angebote ausblenden? Sie stehen danach unter „Ausgeblendet“. Die ${aelterGesamt - offeneTeile.aelter.length} noch nicht geladenen bleiben hier.`,
                                                `${offeneTeile.aelter.length} ältere Angebote ausgeblendet`,
                                            )}
                                        >
                                            {bulkBusy ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" /> : <EyeOff className="w-4 h-4" />}
                                            {aelterAlleGeladen ? 'Alle ausblenden' : 'Angezeigte ausblenden'}
                                        </Button>
                                    </div>
                                    {aelterAufgeklappt && (
                                        <div id="aeltere-angebote" className="p-4 pt-2 border-t border-slate-100">
                                            {renderBloecke(offeneTeile.aelter)}
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
                                        Seit über 2 Monaten keine Rechnung ({anzahlText(laufendeTeile.aelter.length, ueberfaelligGesamt)})
                                    </h2>
                                    <div>{renderBloecke(laufendeTeile.aelter, { ohneRechnungHinweis: true })}</div>
                                </section>
                            )}
                            {laufendeTeile.aktuell.length > 0 && (
                                <section aria-label="Aktuelle Bestellungen" className="space-y-3">
                                    {laufendeTeile.aelter.length > 0 && (
                                        <h2 className="text-sm font-semibold text-slate-700">Aktuell ({laufendeTeile.aktuell.length})</h2>
                                    )}
                                    <div>{renderBloecke(laufendeTeile.aktuell)}</div>
                                </section>
                            )}
                        </>
                    )}

                    {tab !== 'offen' && tab !== 'laufend' && (
                        <div>{renderBloecke(currentList)}</div>
                    )}

                    {!alleGeladen && (
                        <div className="flex flex-col items-center gap-1.5 pt-2">
                            <div className="flex items-center gap-1">
                                <Button variant="outline" size="sm" onClick={mehrLaden} disabled={mehrLaedt}>
                                    {mehrLaedt
                                        ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                        : <ChevronDown className="w-4 h-4" aria-hidden="true" />}
                                    {mehrLaedt ? 'Lädt …' : 'Mehr laden'}
                                </Button>
                                <InfoButton titel="Mehr laden">
                                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                        <p>Damit die Seite schnell bleibt, lädt sie pro Reiter zuerst nur die 25 neuesten Bestellungen – bei „Angebote“ und „Bestellt“ zusätzlich bis zu 25 ältere, damit Überfälliges sofort sichtbar ist. Die Suche durchsucht dagegen immer alle Bestellungen.</p>
                                    </InfoAbschnitt>
                                    <InfoAbschnitt titel="Was bewirkt das?">
                                        <p>„Mehr laden“ holt die nächsten 25. Es geht nichts verloren – ältere Bestellungen sind nur noch nicht geladen.</p>
                                    </InfoAbschnitt>
                                </InfoButton>
                            </div>
                            <p className="text-xs text-slate-500 tabular-nums" aria-live="polite">
                                {geladenImTab} von {gesamtAktiverTab} angezeigt
                            </p>
                        </div>
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
                        void loadData('still');
                    }}
                />
            )}

            {dokumentSuche && (
                <DokumentSuchenDialog
                    kette={dokumentSuche.kette}
                    startTyp={dokumentSuche.startTyp}
                    onClose={() => setDokumentSuche(null)}
                    onVerknuepft={() => {
                        setDokumentSuche(null);
                        void loadData('still');
                    }}
                />
            )}

            {/* Zuordnung: bei mehreren Rechnungen mit Rechnungs-Leiste, springt zur nächsten offenen */}
            {zuordnungKette && (
                <KettenZuordnung
                    kette={zuordnungKette}
                    onSchliessen={gespeichert => {
                        setZuordnungKette(null);
                        if (gespeichert) void loadData();
                    }}
                />
            )}
        </div>
    );
}
