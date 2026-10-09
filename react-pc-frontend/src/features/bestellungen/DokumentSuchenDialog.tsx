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
import { MIN_QUOTE_VORAUSWAHL, TREFFER_KLASSEN, formatiereQuote, trefferStufe } from './rechnungsVorschlag';
import {
    VORSCHLAGS_TYPEN,
    anzahlMitNomen,
    dokumentBezeichnung,
    inKetteVerknuepfen,
    kettenDokumentBezeichnung,
    kettenRueckfrage,
    ladeKettenVorschlaege,
    passtZurKettenSuche,
    typWoerter,
    type KettenVorschlag,
    type SuchKette,
    type VorschlagsTyp,
} from './kettenVorschlag';

interface DokumentSuchenDialogProps {
    /** Die Kette – aus der Bestellübersicht oder ein einzelnes Lieferanten-Dokument. */
    kette: SuchKette;
    /** Vorgewählte Dokumentart, z. B. RECHNUNG vom „Suchen“ am offenen Ende. Ohne: alle Arten. */
    startTyp?: VorschlagsTyp | null;
    /**
     * Ein einzelnes Dokument sucht seine Bestellung (Lieferanten → Dokumente, „Zu Kette
     * zuordnen“). Dann heißt das Fenster „Passende Bestellung suchen“ – aus Sicht des
     * Nutzers hängt er *dieses* Dokument an, nicht etwas an eine Kette.
     */
    einzeldokument?: boolean;
    onClose: () => void;
    /** Dokument wurde zugeordnet oder hochgeladen – die Seite lädt dann neu. */
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

function anzahlText(anzahl: number, typ: VorschlagsTyp | null, lieferant: string | null, alleLieferanten: boolean): string {
    const menge = anzahlMitNomen(anzahl, typ);
    if (alleLieferanten) return `${menge} von allen Lieferanten`;
    return `${menge} von ${lieferant ?? 'diesem Lieferanten'}`;
}

function leerText(typ: VorschlagsTyp | null, lieferant: string | null, alleLieferanten: boolean): { titel: string; hilfe: string } {
    const woerter = typWoerter(typ);
    const titel = alleLieferanten
        ? `Es ist noch ${woerter.kein} da, ${woerter.relativ} passen könnte.`
        : `Von ${lieferant ?? 'diesem Lieferanten'} ist noch ${woerter.kein} da, ${woerter.relativ} passen könnte.`;
    if (typ === 'RECHNUNG') {
        return {
            titel: alleLieferanten ? titel : `Von ${lieferant ?? 'diesem Lieferanten'} ist noch keine Rechnung da.`,
            hilfe: alleLieferanten
                ? 'Liegt die Rechnung auf Papier oder als Datei vor, einfach hier hochladen.'
                : 'Vielleicht kam sie per Post oder unter einem anderen Lieferantennamen. Lade sie hoch oder such bei den anderen Lieferanten.',
        };
    }
    return {
        titel,
        hilfe: alleLieferanten
            ? 'Vielleicht ist es noch nicht im System. Importiere es beim Lieferanten unter „Dokumente“.'
            : 'Vielleicht kam es unter einem anderen Lieferantennamen. Such bei den anderen Lieferanten.',
    };
}

function ListeSkeleton() {
    return (
        <div className="p-3 space-y-2" role="status" aria-label="Vorschläge werden geladen">
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

function TypFilter({ typ, onWahl }: { typ: VorschlagsTyp | null; onWahl: (typ: VorschlagsTyp | null) => void }) {
    const chips: { wert: VorschlagsTyp | null; label: string }[] = [
        { wert: null, label: 'Alle' },
        ...VORSCHLAGS_TYPEN.map(t => ({ wert: t, label: TYP_LABELS[t] })),
    ];
    return (
        <div role="group" aria-label="Dokumentart" className="flex flex-wrap gap-1.5">
            {chips.map(chip => {
                const aktiv = chip.wert === typ;
                return (
                    <button
                        key={chip.label}
                        type="button"
                        aria-pressed={aktiv}
                        onClick={() => onWahl(chip.wert)}
                        className={`px-2.5 py-1 text-xs font-medium rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1 ${aktiv
                            ? 'bg-rose-600 border-rose-600 text-white'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-rose-300 hover:text-rose-700'}`}
                    >
                        {chip.label}
                    </button>
                );
            })}
        </div>
    );
}

/**
 * Sucht zu einer Dokumentenkette passende Lieferanten-Dokumente jeder Art und
 * hängt das gewählte mit einem Klick an. Links die Kette, in der Mitte die
 * Vorschläge (Trefferquote, Gründe), rechts das gewählte Dokument.
 */
export function DokumentSuchenDialog({ kette, startTyp = null, einzeldokument = false, onClose, onVerknuepft }: DokumentSuchenDialogProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [vorschlaege, setVorschlaege] = useState<KettenVorschlag[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [ladeVersuch, setLadeVersuch] = useState(0);
    const [suche, setSuche] = useState('');
    const [gewaehltId, setGewaehltId] = useState<number | null>(null);
    const [eigenesDokId, setEigenesDokId] = useState<number | null>(null);
    const [speichert, setSpeichert] = useState(false);
    /** Dokument im großen Vorschaufenster */
    const [vorschau, setVorschau] = useState<KettenVorschlag | null>(null);
    /** Standard: nur derselbe Lieferant. Auf Wunsch alle Lieferanten. */
    const [alleLieferanten, setAlleLieferanten] = useState(false);
    const [typ, setTyp] = useState<VorschlagsTyp | null>(startTyp);
    const zielDokument = useMemo(() => juengstesBestellDokument(kette.dokumente), [kette.dokumente]);

    const eigeneDokumente = useMemo(() => kette.dokumente.filter(d => d.pdfUrl), [kette.dokumente]);
    const eigenes = eigeneDokumente.find(d => d.id === eigenesDokId) ?? eigeneDokumente[0] ?? null;

    useEffect(() => {
        let abgebrochen = false;
        const ids = kette.dokumente.map(d => d.id);
        ladeKettenVorschlaege(ids, { alleLieferanten, typ })
            .then(liste => {
                if (abgebrochen) return;
                setVorschlaege(liste);
                setFehler(false);
                // Nur einen brauchbaren Vorschlag vorauswählen – sonst hinge ein Klick ein geratenes Dokument an
                setGewaehltId(liste[0] && liste[0].trefferquote >= MIN_QUOTE_VORAUSWAHL ? liste[0].dokument.id : null);
            })
            .catch(err => {
                if (abgebrochen) return;
                setFehler(true);
                toast.error(err instanceof Error ? err.message : 'Die Vorschläge konnten nicht geladen werden.');
            });
        return () => { abgebrochen = true; };
    }, [kette.dokumente, ladeVersuch, alleLieferanten, typ, toast]);

    /** Liste leeren, damit während des Ladens kein alter Stand stehen bleibt. */
    const zuruecksetzen = useCallback(() => {
        setVorschlaege(null);
        setFehler(false);
        setGewaehltId(null);
    }, []);

    const neuLaden = () => {
        zuruecksetzen();
        setLadeVersuch(v => v + 1);
    };

    const lieferantenUmschalten = (alle: boolean) => {
        zuruecksetzen();
        setAlleLieferanten(alle);
    };

    const typWaehlen = (neu: VorschlagsTyp | null) => {
        if (neu === typ) return;
        zuruecksetzen();
        setTyp(neu);
    };

    const sichtbar = useMemo(
        () => (vorschlaege ?? []).filter(v => passtZurKettenSuche(v, suche)),
        [vorschlaege, suche],
    );
    const gewaehlt = (vorschlaege ?? []).find(v => v.dokument.id === gewaehltId) ?? null;

    const verknuepfen = async (vorschlag: KettenVorschlag | null = gewaehlt) => {
        if (!vorschlag || speichert) return;
        const frage = kettenRueckfrage(vorschlag);
        if (frage && !(await confirm(frage))) return;
        setSpeichert(true);
        try {
            await inKetteVerknuepfen(vorschlag.kettenDokumentId, vorschlag.dokument.id);
            toast.success(einzeldokument
                ? `Zugeordnet zu ${dokumentBezeichnung(vorschlag.dokument)}.`
                : `${dokumentBezeichnung(vorschlag.dokument)} zur Kette hinzugefügt.`);
            setVorschau(null);
            onVerknuepft();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Dokument konnte nicht zugeordnet werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const laden = vorschlaege === null && !fehler;
    const nurRechnungen = typ === 'RECHNUNG';
    const leer = leerText(typ, kette.lieferantName, alleLieferanten);
    const titel = einzeldokument ? 'Passende Bestellung suchen' : nurRechnungen ? 'Rechnung suchen' : 'Dokument zur Kette hinzufügen';

    return (
        <Dialog
            open
            onOpenChange={o => { if (!o && !speichert) onClose(); }}
            className="w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] max-w-none p-0 overflow-hidden"
            aria-labelledby="dokument-suchen-titel"
        >
            <div className="px-6 py-4 pr-16 border-b border-slate-200 flex items-center gap-3 flex-shrink-0">
                <span className="w-10 h-10 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center flex-shrink-0">
                    <FileSearch className="w-5 h-5" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                    <DialogTitle id="dokument-suchen-titel" className="text-xl truncate">
                        {titel}{kette.lieferantName ? ` – ${kette.lieferantName}` : ''}
                    </DialogTitle>
                    <DialogDescription>
                        {einzeldokument
                            ? 'Links dieses Dokument, rechts der gewählte Vorschlag. Passt er, genügt ein Klick auf „Gehört dazu“.'
                            : 'Links die Kette, rechts das gewählte Dokument. Passt es, genügt ein Klick auf „Gehört dazu“.'}
                    </DialogDescription>
                </div>
            </div>

            <div className="flex-1 min-h-0 grid grid-cols-[minmax(0,1fr)_minmax(320px,26rem)_minmax(0,1fr)] divide-x divide-slate-200">
                <PdfSpalte
                    titel={einzeldokument ? 'Dieses Dokument' : 'Kette'}
                    kopf={eigeneDokumente.length > 1 ? (
                        <div className="flex gap-1" role="tablist" aria-label="Dokument der Kette">
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
                        : <PdfLeer text={einzeldokument ? 'Zu diesem Dokument gibt es keine Vorschau.' : 'Zu dieser Kette gibt es keine Vorschau.'} />}
                </PdfSpalte>

                <section aria-label="Vorschläge" className="flex flex-col min-h-0 bg-white">
                    <div className="p-3 border-b border-slate-200 space-y-2">
                        <TypFilter typ={typ} onWahl={typWaehlen} />
                        <div className="relative">
                            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                            <Input
                                type="text"
                                value={suche}
                                onChange={e => setSuche(e.target.value)}
                                placeholder="Nummer, Lieferant, Betrag oder Datum …"
                                aria-label="Vorschläge durchsuchen"
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
                                {vorschlaege ? anzahlText(sichtbar.length, typ, kette.lieferantName, alleLieferanten) : ' '}
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
                                <p className="font-medium text-slate-800">Die Vorschläge konnten nicht geladen werden.</p>
                                <Button size="sm" variant="outline" onClick={neuLaden}>
                                    <RefreshCw className="w-4 h-4" aria-hidden="true" />
                                    Nochmal versuchen
                                </Button>
                            </div>
                        )}
                        {vorschlaege && vorschlaege.length === 0 && (
                            <div className="p-6 text-center text-sm text-slate-500 space-y-3">
                                <FileText className="w-10 h-10 mx-auto text-slate-300" aria-hidden="true" />
                                <p className="font-medium text-slate-800">{leer.titel}</p>
                                <p>{leer.hilfe}</p>
                                <div className="flex flex-wrap justify-center gap-2">
                                    {/* Hochladen nur bei Rechnungen – dafür gibt es einen festen Weg ins Backend */}
                                    {nurRechnungen && zielDokument && (
                                        <RechnungHochladenKnopf
                                            bestellDokumentId={zielDokument.id}
                                            onHochgeladen={() => onVerknuepft()}
                                        />
                                    )}
                                    {!alleLieferanten && (
                                        <Button size="sm" variant="outline" onClick={() => lieferantenUmschalten(true)}>
                                            <Search className="w-4 h-4" aria-hidden="true" />
                                            Bei anderen Lieferanten suchen
                                        </Button>
                                    )}
                                    {typ !== null && (
                                        <Button size="sm" variant="outline" onClick={() => typWaehlen(null)}>
                                            Alle Dokumentarten zeigen
                                        </Button>
                                    )}
                                </div>
                            </div>
                        )}
                        {vorschlaege && vorschlaege.length > 0 && sichtbar.length === 0 && (
                            <div className="p-6 text-center text-sm text-slate-500">
                                Kein Vorschlag passt zu „{suche}“.
                            </div>
                        )}
                        {sichtbar.length > 0 && (
                            <ul className="p-3 space-y-2" aria-label="Vorschläge">
                                {sichtbar.map(v => {
                                    const aktiv = v.dokument.id === gewaehltId;
                                    const bezeichnung = dokumentBezeichnung(v.dokument);
                                    return (
                                        <li key={v.dokument.id} className={`rounded-lg border transition-colors ${aktiv
                                            ? 'border-rose-400 bg-rose-50'
                                            : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                                            <button
                                                type="button"
                                                onClick={() => setGewaehltId(v.dokument.id)}
                                                aria-pressed={aktiv}
                                                className="w-full text-left rounded-t-lg p-3 pb-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-inset"
                                            >
                                                <div className="flex items-start justify-between gap-2">
                                                    <span className="min-w-0 flex items-center gap-1.5">
                                                        <span className="flex-shrink-0 text-[11px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                                                            {TYP_LABELS[v.dokument.typ]}
                                                        </span>
                                                        <span className="text-sm font-semibold text-slate-900 truncate">
                                                            {v.dokument.dokumentNummer ?? v.dokument.dateiname}
                                                        </span>
                                                    </span>
                                                    <QuoteMarke quote={v.trefferquote} />
                                                </div>
                                                <div className="mt-0.5 text-xs text-slate-500 truncate">
                                                    {v.lieferantName ?? 'Lieferant unbekannt'}
                                                </div>
                                                <div className="mt-0.5 text-xs text-slate-600 truncate">
                                                    passt zu: <span className="font-medium text-slate-700">{kettenDokumentBezeichnung(v)}</span>
                                                </div>
                                                {(v.gehoertSchonZu || v.dokument.ausgeblendet) && (
                                                    <div className="mt-1.5 flex flex-wrap gap-1">
                                                        {v.gehoertSchonZu && (
                                                            <span className="text-[11px] leading-tight px-1.5 py-0.5 rounded border border-amber-200 bg-amber-50 text-amber-800">
                                                                Hängt schon an {v.gehoertSchonZu}
                                                            </span>
                                                        )}
                                                        {v.dokument.ausgeblendet && (
                                                            <span className="text-[11px] leading-tight px-1.5 py-0.5 rounded border border-slate-200 bg-slate-100 text-slate-500">
                                                                ausgeblendet
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                                <div className="mt-1 flex items-center justify-between text-xs text-slate-600">
                                                    <span className="tabular-nums">{formatDatum(v.dokument.dokumentDatum ?? v.dokument.eingangsDatum)}</span>
                                                    <span className="font-medium tabular-nums">{formatEuro(v.dokument.betragBrutto)}</span>
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
                                                        Ein weiteres Dokument passt gleich gut.
                                                    </p>
                                                )}
                                            </button>
                                            <div className="flex gap-2 px-3 pb-3">
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() => setVorschau(v)}
                                                    disabled={!v.dokument.pdfUrl}
                                                    title={v.dokument.pdfUrl ? undefined : 'Zu diesem Dokument gibt es keine Vorschau.'}
                                                    aria-label={`Vorschau ${bezeichnung}`}
                                                >
                                                    <Eye className="w-4 h-4" aria-hidden="true" />
                                                    Vorschau
                                                </Button>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() => void verknuepfen(v)}
                                                    disabled={speichert}
                                                    aria-label={`${bezeichnung} gehört dazu`}
                                                >
                                                    <Link2 className="w-4 h-4" aria-hidden="true" />
                                                    Gehört dazu
                                                </Button>
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                </section>

                <PdfSpalte titel={gewaehlt ? dokumentBezeichnung(gewaehlt.dokument) : 'Vorschlag'}>
                    {laden
                        ? <div className="h-full p-6 motion-safe:animate-pulse" role="status" aria-label="Vorschau wird geladen"><div className="h-full rounded-lg bg-slate-200/70" /></div>
                        : gewaehlt?.dokument.pdfUrl
                            ? <PdfCanvasViewer key={gewaehlt.dokument.pdfUrl} url={gewaehlt.dokument.pdfUrl} className="h-full" />
                            : <PdfLeer text={gewaehlt ? 'Zu diesem Dokument gibt es keine Vorschau.' : 'Ein Dokument in der Liste wählen, um es hier zu sehen.'} />}
                </PdfSpalte>
            </div>

            <div className="px-6 py-3 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3 flex-shrink-0 bg-white">
                <p className="text-sm text-slate-500 min-w-0 truncate">
                    {gewaehlt
                        ? <>Gewählt: <span className="font-medium text-slate-700">{dokumentBezeichnung(gewaehlt.dokument)}</span> · <span className="tabular-nums">{formatEuro(gewaehlt.dokument.betragBrutto)}</span></>
                        : 'Noch kein Dokument gewählt.'}
                </p>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={onClose} disabled={speichert}>Abbrechen</Button>
                    <Button
                        size="sm"
                        onClick={() => void verknuepfen()}
                        disabled={!gewaehlt || speichert}
                        title={gewaehlt ? undefined : 'Zuerst ein Dokument aus der Liste wählen.'}
                    >
                        {speichert
                            ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <Link2 className="w-4 h-4" aria-hidden="true" />}
                        Gehört dazu
                    </Button>
                </div>
            </div>

            {vorschau?.dokument.pdfUrl && (
                <Dialog
                    open
                    onOpenChange={o => { if (!o && !speichert) setVorschau(null); }}
                    className="w-[calc(100vw-3cm)] h-[calc(100vh-3cm)] max-w-none p-0 overflow-hidden"
                    aria-labelledby="dokument-vorschau-titel"
                >
                    <div className="px-6 py-4 pr-16 border-b border-slate-200 flex-shrink-0 min-w-0">
                        <DialogTitle id="dokument-vorschau-titel" className="text-xl truncate">
                            {dokumentBezeichnung(vorschau.dokument)}
                        </DialogTitle>
                        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span>{vorschau.lieferantName ?? 'Lieferant unbekannt'}</span>
                            <span aria-hidden="true">·</span>
                            <span className="tabular-nums">{formatDatum(vorschau.dokument.dokumentDatum ?? vorschau.dokument.eingangsDatum)}</span>
                            <span aria-hidden="true">·</span>
                            <span className="tabular-nums">{formatEuro(vorschau.dokument.betragBrutto)}</span>
                            <span aria-hidden="true">·</span>
                            <span>passt zu {kettenDokumentBezeichnung(vorschau)}</span>
                            <QuoteMarke quote={vorschau.trefferquote} />
                        </DialogDescription>
                    </div>
                    <div className="flex-1 min-h-0 bg-slate-50">
                        <PdfCanvasViewer key={vorschau.dokument.pdfUrl} url={vorschau.dokument.pdfUrl} className="h-full" />
                    </div>
                    <div className="px-6 py-3 border-t border-slate-200 flex justify-end gap-2 flex-shrink-0 bg-white">
                        <Button variant="outline" size="sm" onClick={() => setVorschau(null)} disabled={speichert}>Schließen</Button>
                        <Button size="sm" onClick={() => void verknuepfen(vorschau)} disabled={speichert}>
                            {speichert
                                ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                : <Link2 className="w-4 h-4" aria-hidden="true" />}
                            Gehört dazu
                        </Button>
                    </div>
                </Dialog>
            )}
        </Dialog>
    );
}
