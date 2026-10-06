import { useCallback, useEffect, useMemo, useState } from 'react';
import { Eye, FileSearch, FileText, Link2, RefreshCw, Search, X } from 'lucide-react';
import { Dialog, DialogDescription, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { PdfCanvasViewer } from '../../components/ui/PdfCanvasViewer';
import { useToast } from '../../components/ui/toast';
import { TYP_LABELS } from './bestellungenListe';
import { juengstesBestellDokument } from './kettenGraph';
import { RechnungHochladenKnopf } from './RechnungHochladenKnopf';
import {
    MIN_QUOTE_VORAUSWAHL,
    TREFFER_KLASSEN,
    formatiereQuote,
    ladeRechnungsVorschlaege,
    passtZurRechnungsSuche,
    rechnungVerknuepfen,
    rueckfrage,
    trefferStufe,
    type RechnungsDokument,
    type RechnungsVorschlag,
} from './rechnungsVorschlag';

interface BestellungKette {
    id: string;
    lieferantName: string | null;
    dokumente: RechnungsDokument[];
}

interface RechnungSuchenDialogProps {
    kette: BestellungKette;
    onClose: () => void;
    /** Rechnung wurde zugeordnet – die Seite lädt dann neu. */
    onVerknuepft: () => void;
}

const formatDatum = (iso: string | null | undefined): string => {
    if (!iso) return '–';
    const datum = new Date(iso);
    return Number.isNaN(datum.getTime()) ? '–' : datum.toLocaleDateString('de-DE');
};

const formatEuro = (wert: number | null): string => {
    if (wert == null || !Number.isFinite(wert)) return '–';
    return `${wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`;
};

function PdfLeer({ text }: { text: string }) {
    return (
        <div className="h-full flex flex-col items-center justify-center gap-2 text-slate-400 text-sm p-6 text-center">
            <FileText className="w-10 h-10" aria-hidden="true" />
            {text}
        </div>
    );
}

function PdfSpalte({ titel, kopf, children }: { titel: string; kopf?: React.ReactNode; children: React.ReactNode }) {
    return (
        <section aria-label={titel} className="flex flex-col min-h-0 min-w-0 bg-slate-50">
            <div className="px-4 py-2 border-b border-slate-200 bg-white flex items-center gap-2 min-h-[44px] overflow-x-auto">
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500 whitespace-nowrap">{titel}</span>
                {kopf}
            </div>
            <div className="flex-1 min-h-0">{children}</div>
        </section>
    );
}

/** 0 % ist kein Treffer – dann keine Quote zeigen, damit nichts nach Vorschlag aussieht. */
function QuoteMarke({ quote }: { quote: number }) {
    if (quote <= 0) return null;
    return (
        <span className={`flex-shrink-0 text-xs font-semibold px-2 py-0.5 rounded-full border tabular-nums ${TREFFER_KLASSEN[trefferStufe(quote)]}`}>
            {formatiereQuote(quote)}
        </span>
    );
}

function anzahlText(anzahl: number, lieferant: string | null, alleLieferanten: boolean): string {
    const rechnungen = anzahl === 1 ? 'Rechnung' : 'Rechnungen';
    if (alleLieferanten) return `${anzahl} ${rechnungen} von allen Lieferanten`;
    return `${anzahl} ${rechnungen} von ${lieferant ?? 'diesem Lieferanten'}`;
}

function ListeSkeleton() {
    return (
        <div className="p-3 space-y-2" role="status" aria-label="Rechnungen werden geladen">
            {[0, 1, 2, 3].map(i => (
                <div key={i} className="rounded-lg border border-slate-200 p-3 space-y-2 motion-safe:animate-pulse">
                    <div className="h-4 w-2/3 rounded bg-slate-200" />
                    <div className="h-3 w-1/2 rounded bg-slate-100" />
                    <div className="h-3 w-3/4 rounded bg-slate-100" />
                </div>
            ))}
        </div>
    );
}

export function RechnungSuchenDialog({ kette, onClose, onVerknuepft }: RechnungSuchenDialogProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [vorschlaege, setVorschlaege] = useState<RechnungsVorschlag[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [ladeVersuch, setLadeVersuch] = useState(0);
    const [suche, setSuche] = useState('');
    const [gewaehltId, setGewaehltId] = useState<number | null>(null);
    const [eigenesDokId, setEigenesDokId] = useState<number | null>(null);
    const [speichert, setSpeichert] = useState(false);
    /** Rechnung im großen Vorschaufenster */
    const [vorschau, setVorschau] = useState<RechnungsVorschlag | null>(null);
    /** Standard: nur derselbe Lieferant. Auf Wunsch alle Lieferanten. */
    const [alleLieferanten, setAlleLieferanten] = useState(false);
    const zielDokument = useMemo(() => juengstesBestellDokument(kette.dokumente), [kette.dokumente]);

    const eigeneDokumente = useMemo(() => kette.dokumente.filter(d => d.pdfUrl), [kette.dokumente]);
    const eigenes = eigeneDokumente.find(d => d.id === eigenesDokId) ?? eigeneDokumente[0] ?? null;

    useEffect(() => {
        let abgebrochen = false;
        const ids = kette.dokumente.map(d => d.id);
        ladeRechnungsVorschlaege(ids, { alleLieferanten })
            .then(liste => {
                if (abgebrochen) return;
                setVorschlaege(liste);
                setFehler(false);
                // Nur einen brauchbaren Vorschlag vorauswählen – sonst verknüpfte ein Klick eine geratene Rechnung
                setGewaehltId(liste[0] && liste[0].trefferquote >= MIN_QUOTE_VORAUSWAHL ? liste[0].rechnung.id : null);
            })
            .catch(err => {
                if (abgebrochen) return;
                setFehler(true);
                toast.error(err instanceof Error ? err.message : 'Rechnungen konnten nicht geladen werden.');
            });
        return () => { abgebrochen = true; };
    }, [kette.dokumente, ladeVersuch, alleLieferanten, toast]);

    const neuLaden = useCallback(() => {
        setVorschlaege(null);
        setFehler(false);
        setLadeVersuch(v => v + 1);
    }, []);

    const lieferantenUmschalten = (alle: boolean) => {
        setVorschlaege(null);
        setFehler(false);
        setGewaehltId(null);
        setAlleLieferanten(alle);
    };

    const sichtbar = useMemo(
        () => (vorschlaege ?? []).filter(v => passtZurRechnungsSuche(v, suche)),
        [vorschlaege, suche],
    );
    const gewaehlt = (vorschlaege ?? []).find(v => v.rechnung.id === gewaehltId) ?? null;

    const verknuepfen = async (vorschlag: RechnungsVorschlag | null = gewaehlt) => {
        if (!vorschlag || speichert) return;
        const frage = rueckfrage(vorschlag);
        if (frage && !(await confirm(frage))) return;
        setSpeichert(true);
        try {
            await rechnungVerknuepfen(vorschlag.bestellDokumentId, vorschlag.rechnung.id);
            toast.success(vorschlag.rechnung.dokumentNummer ? `Rechnung ${vorschlag.rechnung.dokumentNummer} zugeordnet.` : 'Rechnung zugeordnet.');
            setVorschau(null);
            onVerknuepft();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Rechnung konnte nicht zugeordnet werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const laden = vorschlaege === null && !fehler;

    return (
        <Dialog
            open
            onOpenChange={o => { if (!o && !speichert) onClose(); }}
            className="w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] max-w-none p-0 overflow-hidden"
            aria-labelledby="rechnung-suchen-titel"
        >
            <div className="px-6 py-4 pr-16 border-b border-slate-200 flex items-center gap-3 flex-shrink-0">
                <span className="w-10 h-10 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center flex-shrink-0">
                    <FileSearch className="w-5 h-5" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                    <DialogTitle id="rechnung-suchen-titel" className="text-xl truncate">
                        Rechnung suchen{kette.lieferantName ? ` – ${kette.lieferantName}` : ''}
                    </DialogTitle>
                    <DialogDescription>
                        Links die Bestellung, rechts die gewählte Rechnung. Passt sie, genügt ein Klick zum Zuordnen.
                    </DialogDescription>
                </div>
            </div>

            <div className="flex-1 min-h-0 grid grid-cols-[minmax(0,1fr)_minmax(320px,26rem)_minmax(0,1fr)] divide-x divide-slate-200">
                <PdfSpalte
                    titel="Bestellung"
                    kopf={eigeneDokumente.length > 1 ? (
                        <div className="flex gap-1" role="tablist" aria-label="Dokument der Bestellung">
                            {eigeneDokumente.map(d => (
                                <button
                                    key={d.id}
                                    type="button"
                                    role="tab"
                                    aria-selected={eigenes?.id === d.id}
                                    onClick={() => setEigenesDokId(d.id)}
                                    className={`px-2.5 py-1 text-xs font-medium rounded-md whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500 ${eigenes?.id === d.id
                                        ? 'bg-rose-50 text-rose-700'
                                        : 'text-slate-500 hover:bg-slate-100'}`}
                                >
                                    {TYP_LABELS[d.typ]}{d.dokumentNummer ? ` ${d.dokumentNummer}` : ''}
                                </button>
                            ))}
                        </div>
                    ) : undefined}
                >
                    {eigenes?.pdfUrl
                        ? <PdfCanvasViewer key={eigenes.pdfUrl} url={eigenes.pdfUrl} className="h-full" />
                        : <PdfLeer text="Zu dieser Bestellung gibt es keine Vorschau." />}
                </PdfSpalte>

                <section aria-label="Gefundene Rechnungen" className="flex flex-col min-h-0 bg-white">
                    <div className="p-3 border-b border-slate-200 space-y-1">
                        <div className="relative">
                            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                            <Input
                                type="text"
                                value={suche}
                                onChange={e => setSuche(e.target.value)}
                                placeholder="Nummer, Lieferant, Betrag oder Datum …"
                                aria-label="Rechnungen durchsuchen"
                                className="h-9 pl-9 pr-9 rounded-lg border-slate-300"
                            />
                            {suche && (
                                <button
                                    type="button"
                                    onClick={() => setSuche('')}
                                    aria-label="Suche löschen"
                                    className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded text-slate-400 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5 px-1">
                            <p className="text-xs text-slate-500" aria-live="polite">
                                {vorschlaege ? anzahlText(sichtbar.length, kette.lieferantName, alleLieferanten) : '\u00a0'}
                            </p>
                            {/* Im Leerzustand übernimmt der große Knopf darunter */}
                            {vorschlaege && (vorschlaege.length > 0 || alleLieferanten) && (
                                <button
                                    type="button"
                                    onClick={() => lieferantenUmschalten(!alleLieferanten)}
                                    className="text-xs font-medium text-rose-700 hover:text-rose-800 hover:underline underline-offset-2 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    {alleLieferanten
                                        ? `Nur ${kette.lieferantName ?? 'diesen Lieferanten'} zeigen`
                                        : 'Auch bei anderen Lieferanten suchen'}
                                </button>
                            )}
                        </div>
                    </div>

                    <div className="flex-1 min-h-0 overflow-y-auto">
                        {laden && <ListeSkeleton />}
                        {fehler && (
                            <div className="p-6 text-center text-sm text-slate-600 space-y-3">
                                <p className="font-medium text-slate-800">Die Rechnungen konnten nicht geladen werden.</p>
                                <Button size="sm" variant="outline" onClick={neuLaden}>
                                    <RefreshCw className="w-4 h-4" aria-hidden="true" />
                                    Nochmal versuchen
                                </Button>
                            </div>
                        )}
                        {vorschlaege && vorschlaege.length === 0 && (
                            <div className="p-6 text-center text-sm text-slate-500 space-y-3">
                                <FileText className="w-10 h-10 mx-auto text-slate-300" aria-hidden="true" />
                                <p className="font-medium text-slate-800">
                                    {alleLieferanten
                                        ? 'Es ist noch keine Rechnung da, die passen könnte.'
                                        : `Von ${kette.lieferantName ?? 'diesem Lieferanten'} ist noch keine Rechnung da.`}
                                </p>
                                <p>
                                    {alleLieferanten
                                        ? 'Liegt die Rechnung auf Papier oder als Datei vor, einfach hier hochladen.'
                                        : 'Vielleicht kam sie per Post oder unter einem anderen Lieferantennamen. Lade sie hoch oder such bei den anderen Lieferanten.'}
                                </p>
                                <div className="flex flex-wrap justify-center gap-2">
                                    <RechnungHochladenKnopf
                                        bestellDokumentId={zielDokument?.id ?? null}
                                        onHochgeladen={() => onVerknuepft()}
                                    />
                                    {!alleLieferanten && (
                                        <Button size="sm" variant="outline" onClick={() => lieferantenUmschalten(true)}>
                                            <Search className="w-4 h-4" aria-hidden="true" />
                                            Bei anderen Lieferanten suchen
                                        </Button>
                                    )}
                                </div>
                            </div>
                        )}
                        {vorschlaege && vorschlaege.length > 0 && sichtbar.length === 0 && (
                            <div className="p-6 text-center text-sm text-slate-500">
                                Keine Rechnung passt zu „{suche}“.
                            </div>
                        )}
                        {sichtbar.length > 0 && (
                            <ul className="p-3 space-y-2" aria-label="Rechnungen">
                                {sichtbar.map(v => {
                                    const aktiv = v.rechnung.id === gewaehltId;
                                    return (
                                        <li key={v.rechnung.id} className={`rounded-lg border transition-colors ${aktiv
                                            ? 'border-rose-400 bg-rose-50'
                                            : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                                            <button
                                                type="button"
                                                onClick={() => setGewaehltId(v.rechnung.id)}
                                                aria-pressed={aktiv}
                                                className="w-full text-left rounded-t-lg p-3 pb-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-inset"
                                            >
                                                <div className="flex items-start justify-between gap-2">
                                                    <span className="text-sm font-semibold text-slate-900 truncate">
                                                        {v.rechnung.dokumentNummer ?? v.rechnung.dateiname}
                                                    </span>
                                                    <QuoteMarke quote={v.trefferquote} />
                                                </div>
                                                <div className="mt-0.5 text-xs text-slate-500 truncate">
                                                    {v.lieferantName ?? 'Lieferant unbekannt'}
                                                </div>
                                                {(v.gehoertSchonZu || v.rechnung.ausgeblendet) && (
                                                    <div className="mt-1.5 flex flex-wrap gap-1">
                                                        {v.gehoertSchonZu && (
                                                            <span className="text-[11px] leading-tight px-1.5 py-0.5 rounded border border-amber-200 bg-amber-50 text-amber-800">
                                                                Gehört schon zu {v.gehoertSchonZu} – Teillieferung
                                                            </span>
                                                        )}
                                                        {v.rechnung.ausgeblendet && (
                                                            <span className="text-[11px] leading-tight px-1.5 py-0.5 rounded border border-slate-200 bg-slate-100 text-slate-500">
                                                                ausgeblendet
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                                <div className="mt-1 flex items-center justify-between text-xs text-slate-600">
                                                    <span className="tabular-nums">{formatDatum(v.rechnung.dokumentDatum ?? v.rechnung.eingangsDatum)}</span>
                                                    <span className="font-medium tabular-nums">{formatEuro(v.rechnung.betragBrutto)}</span>
                                                </div>
                                                {v.gruende.length > 0 && (
                                                    <ul className="mt-2 flex flex-wrap gap-1" aria-label="Gründe">
                                                        {v.gruende.map(grund => (
                                                            <li key={grund} className="text-[11px] leading-tight px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                                                                {grund}
                                                            </li>
                                                        ))}
                                                    </ul>
                                                )}
                                                {!v.eindeutig && v.trefferquote > 0 && (
                                                    <p className="mt-2 text-[11px] text-amber-700">
                                                        Eine weitere Rechnung passt gleich gut.
                                                    </p>
                                                )}
                                            </button>
                                            <div className="flex gap-2 px-3 pb-3">
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() => setVorschau(v)}
                                                    disabled={!v.rechnung.pdfUrl}
                                                    title={v.rechnung.pdfUrl ? undefined : 'Zu dieser Rechnung gibt es keine Vorschau.'}
                                                    aria-label={`Vorschau Rechnung ${v.rechnung.dokumentNummer ?? v.rechnung.dateiname}`}
                                                >
                                                    <Eye className="w-4 h-4" aria-hidden="true" />
                                                    Vorschau
                                                </Button>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() => void verknuepfen(v)}
                                                    disabled={speichert}
                                                    aria-label={`Rechnung ${v.rechnung.dokumentNummer ?? v.rechnung.dateiname} zuordnen`}
                                                >
                                                    <Link2 className="w-4 h-4" aria-hidden="true" />
                                                    Zuordnen
                                                </Button>
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                </section>

                <PdfSpalte titel={gewaehlt ? `Rechnung ${gewaehlt.rechnung.dokumentNummer ?? ''}`.trim() : 'Rechnung'}>
                    {laden
                        ? <div className="h-full p-6 motion-safe:animate-pulse" role="status" aria-label="Vorschau wird geladen"><div className="h-full rounded-lg bg-slate-200/70" /></div>
                        : gewaehlt?.rechnung.pdfUrl
                            ? <PdfCanvasViewer key={gewaehlt.rechnung.pdfUrl} url={gewaehlt.rechnung.pdfUrl} className="h-full" />
                            : <PdfLeer text={gewaehlt ? 'Zu dieser Rechnung gibt es keine Vorschau.' : 'Eine Rechnung in der Liste wählen, um sie hier zu sehen.'} />}
                </PdfSpalte>
            </div>

            <div className="px-6 py-3 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3 flex-shrink-0 bg-white">
                <p className="text-sm text-slate-500 min-w-0 truncate">
                    {gewaehlt
                        ? <>Gewählt: <span className="font-medium text-slate-700">{gewaehlt.rechnung.dokumentNummer ?? gewaehlt.rechnung.dateiname}</span> · <span className="tabular-nums">{formatEuro(gewaehlt.rechnung.betragBrutto)}</span></>
                        : 'Noch keine Rechnung gewählt.'}
                </p>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={onClose} disabled={speichert}>Abbrechen</Button>
                    <Button
                        size="sm"
                        onClick={() => void verknuepfen()}
                        disabled={!gewaehlt || speichert}
                        title={gewaehlt ? undefined : 'Zuerst eine Rechnung aus der Liste wählen.'}
                    >
                        {speichert
                            ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <Link2 className="w-4 h-4" aria-hidden="true" />}
                        Diese Rechnung gehört dazu
                    </Button>
                </div>
            </div>

            {vorschau?.rechnung.pdfUrl && (
                <Dialog
                    open
                    onOpenChange={o => { if (!o && !speichert) setVorschau(null); }}
                    className="w-[calc(100vw-3cm)] h-[calc(100vh-3cm)] max-w-none p-0 overflow-hidden"
                    aria-labelledby="rechnung-vorschau-titel"
                >
                    <div className="px-6 py-4 pr-16 border-b border-slate-200 flex-shrink-0 min-w-0">
                        <DialogTitle id="rechnung-vorschau-titel" className="text-xl truncate">
                            Rechnung {vorschau.rechnung.dokumentNummer ?? vorschau.rechnung.dateiname}
                        </DialogTitle>
                        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span>{vorschau.lieferantName ?? 'Lieferant unbekannt'}</span>
                            <span aria-hidden="true">·</span>
                            <span className="tabular-nums">{formatDatum(vorschau.rechnung.dokumentDatum ?? vorschau.rechnung.eingangsDatum)}</span>
                            <span aria-hidden="true">·</span>
                            <span className="tabular-nums">{formatEuro(vorschau.rechnung.betragBrutto)}</span>
                            <QuoteMarke quote={vorschau.trefferquote} />
                        </DialogDescription>
                    </div>
                    <div className="flex-1 min-h-0 bg-slate-50">
                        <PdfCanvasViewer key={vorschau.rechnung.pdfUrl} url={vorschau.rechnung.pdfUrl} className="h-full" />
                    </div>
                    <div className="px-6 py-3 border-t border-slate-200 flex justify-end gap-2 flex-shrink-0 bg-white">
                        <Button variant="outline" size="sm" onClick={() => setVorschau(null)} disabled={speichert}>Schließen</Button>
                        <Button size="sm" onClick={() => void verknuepfen(vorschau)} disabled={speichert}>
                            {speichert
                                ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                : <Link2 className="w-4 h-4" aria-hidden="true" />}
                            Diese Rechnung gehört dazu
                        </Button>
                    </div>
                </Dialog>
            )}
        </Dialog>
    );
}
