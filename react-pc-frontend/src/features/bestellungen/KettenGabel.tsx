import { useMemo, useState } from 'react';
import { Clock, File, FileBadge, FileCheck, FileSearch, FileText, Receipt, RefreshCw, Truck, Unlink, type LucideIcon } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import type { KettenDokumentTyp } from './bestellungenListe';
import {
    GABEL_LABELS,
    baueKettenGraph,
    gruppiereNachRechnung,
    istRechnungsTyp,
    juengstesBestellDokument,
    linearerGraph,
    type GraphDokument,
    type GraphKante,
    type KettenGraph,
    type KettenVerbindung,
} from './kettenGraph';
import { RechnungHochladenKnopf } from './RechnungHochladenKnopf';
import { dokumentAbhaengen } from './rechnungsVorschlag';

export interface GabelDokument extends GraphDokument {
    dokumentNummer: string | null;
    betragBrutto: number | null;
    liefertermin: string | null;
    dateiname: string;
    pdfUrl: string | null;
}

interface KettenGabelProps {
    dokumente: GabelDokument[];
    verbindungen?: KettenVerbindung[] | null;
    onOpenPdf: (url: string, title: string) => void;
    /** Laufende Bestellung ohne Rechnung: gestrichelter Platzhalter am Ende. */
    offenesEnde?: boolean;
    /** Zeigt am offenen Ende „Rechnung hochladen“ und „Suchen“. */
    onRechnungSuchen?: () => void;
    /** Nach Abhängen oder Hochladen – die Seite lädt neu. */
    onGeaendert: () => void;
}

const GABEL_ICONS: Record<KettenDokumentTyp, LucideIcon> = {
    ANGEBOT: FileText,
    AUFTRAGSBESTAETIGUNG: FileCheck,
    LIEFERSCHEIN: Truck,
    WERKSTOFFZEUGNIS: FileBadge,
    RECHNUNG: Receipt,
    GUTSCHRIFT: Receipt,
    SONSTIG: File,
};

// Maße der Graph-Spalte (px)
const ZEILE = 48;
const OFFEN_MIT_AKTIONEN = 72;
const RAND = 10;
const SPUR_BREITE = 14;
/** Höchstens so breit wird der Graph – sonst bleibt für Nummer und Betrag zu wenig Platz. */
export const MAX_GRAPH_BREITE = 56;
/** Mehr Spuren werden in die letzte zusammengelegt. */
const MAX_SPUREN = 5;

const formatDatum = (iso: string | null | undefined): string => {
    if (!iso) return '–';
    const datum = new Date(iso);
    return Number.isNaN(datum.getTime()) ? '–' : datum.toLocaleDateString('de-DE');
};

const formatEuro = (wert: number | null): string | null => {
    if (wert == null || !Number.isFinite(wert)) return null;
    return `${wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`;
};

/** Spurabstand: normal 14 px, bei vielen Spuren enger, damit der Graph nie breiter als MAX_GRAPH_BREITE wird. */
function spurAbstand(spuren: number): number {
    if (spuren <= 1) return SPUR_BREITE;
    return Math.min(SPUR_BREITE, (MAX_GRAPH_BREITE - 2 * RAND) / (spuren - 1));
}

/** Linie wie bei git: Abzweig oben als Kurve, gerade in der eigenen Spur, Einmündung unten als Kurve. */
function kantenPfad(kante: GraphKante, punktY: number[], spurX: (spur: number) => number): string {
    const x1 = spurX(kante.vonSpur);
    const y1 = punktY[kante.vonZeile];
    const xL = spurX(kante.spur);
    const x2 = spurX(kante.zuSpur);
    const y2 = punktY[kante.zuZeile];
    const halbe = Math.min(ZEILE / 2, (y2 - y1) / 2);
    let pfad = `M ${x1} ${y1}`;
    let y = y1;
    if (xL !== x1) {
        const ziel = y1 + halbe;
        pfad += ` C ${x1} ${(y1 + ziel) / 2}, ${xL} ${(y1 + ziel) / 2}, ${xL} ${ziel}`;
        y = ziel;
    }
    if (xL !== x2) {
        const start = Math.max(y, y2 - halbe);
        if (start > y) pfad += ` L ${xL} ${start}`;
        pfad += ` C ${xL} ${(start + y2) / 2}, ${x2} ${(start + y2) / 2}, ${x2} ${y2}`;
    } else {
        pfad += ` L ${x2} ${y2}`;
    }
    return pfad;
}

interface GabelBlockProps {
    graph: KettenGraph<GabelDokument>;
    /** Name der Liste für Screenreader */
    listenName: string;
    onOpenPdf: (url: string, title: string) => void;
    /** Zeigt am offenen Ende „Rechnung hochladen“ und „Suchen“. */
    onRechnungSuchen?: () => void;
    /** Bestelldokument, an das eine hochgeladene Rechnung gehängt wird. */
    uploadZielId: number | null;
    onGeaendert: () => void;
    abhaengenId: number | null;
    onAbhaengen: (dok: GabelDokument) => Promise<void>;
}

/** Ein Graph (links) mit einer Zeile je Beleg (rechts). */
function GabelBlock({ graph, listenName, onOpenPdf, onRechnungSuchen, uploadZielId, onGeaendert, abhaengenId, onAbhaengen }: GabelBlockProps) {
    const mitAktionen = Boolean(onRechnungSuchen);

    const hoehen = graph.zeilen.map(z => (z.art === 'offen' && mitAktionen ? OFFEN_MIT_AKTIONEN : ZEILE));
    const punktY: number[] = [];
    let summe = 0;
    graph.zeilen.forEach((z, i) => {
        // Am offenen Ende mit Knöpfen sitzt der Punkt auf Höhe des Textes
        punktY.push(summe + (z.art === 'offen' && mitAktionen ? 16 : hoehen[i] / 2));
        summe += hoehen[i];
    });
    const abstand = spurAbstand(graph.spuren);
    const spurX = (spur: number) => RAND + spur * abstand;
    // Enge Spuren: Punkte etwas kleiner, damit sie die Nachbarlinien nicht verdecken
    const eng = abstand < SPUR_BREITE;
    const breite = spurX(graph.spuren - 1) + RAND;
    // Stabile Schlüssel je Zeile (Dokument-ID bzw. „offen“) – auch für die Kanten dazwischen
    const schluessel = graph.zeilen.map(z => (z.dokument ? `dok-${z.dokument.id}` : 'offen'));

    if (graph.zeilen.length === 0) return null;

    return (
        <div className="relative">
            <svg
                aria-hidden="true"
                className="absolute left-0 top-0 overflow-visible"
                width={breite}
                height={summe}
                data-testid="ketten-graph"
            >
                {graph.kanten.map(kante => (
                    <path
                        key={`${schluessel[kante.vonZeile]}-${schluessel[kante.zuZeile]}`}
                        d={kantenPfad(kante, punktY, spurX)}
                        fill="none"
                        strokeWidth={2}
                        strokeLinecap="round"
                        strokeDasharray={kante.gestrichelt ? '3 4' : undefined}
                        className={kante.gestrichelt ? 'stroke-amber-400' : 'stroke-slate-300'}
                    />
                ))}
                {graph.zeilen.map((zeile, i) => {
                    const cx = spurX(zeile.spur);
                    const cy = punktY[i];
                    if (zeile.art === 'offen') {
                        return <circle key={schluessel[i]} cx={cx} cy={cy} r={6} strokeWidth={2} strokeDasharray="2.5 2.5" className="fill-white stroke-amber-500" />;
                    }
                    const versteckt = zeile.dokument?.ausgeblendet === true;
                    if (zeile.istRechnung) {
                        return (
                            <g key={schluessel[i]}>
                                {!versteckt && <circle cx={cx} cy={cy} r={eng ? 8 : 9.5} className="fill-emerald-100" />}
                                <circle cx={cx} cy={cy} r={eng ? 5.5 : 6.5} strokeWidth={2} className={versteckt ? 'fill-slate-300 stroke-white' : 'fill-emerald-500 stroke-white'} />
                            </g>
                        );
                    }
                    return <circle key={schluessel[i]} cx={cx} cy={cy} r={eng ? 3.5 : 4.5} strokeWidth={2} className={versteckt ? 'fill-white stroke-slate-300' : 'fill-white stroke-rose-500'} />;
                })}
            </svg>

            <ul className="relative" style={{ paddingLeft: breite + 4 }} aria-label={listenName}>
                {graph.zeilen.map((zeile, i) => {
                    if (zeile.art === 'offen') {
                        return (
                            <li key="offen" style={{ height: hoehen[i] }} className={`flex flex-col ${mitAktionen ? 'pt-1.5 gap-1.5' : 'justify-center'}`}>
                                <span className="text-sm font-medium text-amber-700 px-1.5">Rechnung fehlt noch</span>
                                {mitAktionen && (
                                    <div className="flex gap-2 px-1.5 flex-wrap">
                                        <RechnungHochladenKnopf
                                            bestellDokumentId={uploadZielId}
                                            onHochgeladen={() => onGeaendert()}
                                            className="py-1"
                                        />
                                        <Button size="sm" variant="outline" className="py-1" onClick={onRechnungSuchen}>
                                            <FileSearch className="w-4 h-4" aria-hidden="true" />
                                            Suchen
                                        </Button>
                                    </div>
                                )}
                            </li>
                        );
                    }
                    const dok = zeile.dokument as GabelDokument;
                    const Icon = GABEL_ICONS[dok.typ];
                    const label = GABEL_LABELS[dok.typ];
                    const versteckt = dok.ausgeblendet === true;
                    const betrag = formatEuro(dok.betragBrutto);
                    const titel = `${label}${dok.dokumentNummer ? ` ${dok.dokumentNummer}` : ''}`;
                    const rechnung = istRechnungsTyp(dok.typ);
                    return (
                        <li key={dok.id} style={{ height: hoehen[i] }} className="flex items-center gap-0.5">
                            <button
                                type="button"
                                onClick={() => dok.pdfUrl && onOpenPdf(dok.pdfUrl, dok.dokumentNummer || dok.dateiname)}
                                disabled={!dok.pdfUrl}
                                title={dok.pdfUrl ? 'Dokument öffnen' : 'Keine Vorschau vorhanden'}
                                className="flex-1 min-w-0 h-11 flex items-center gap-2 rounded-md px-1.5 text-left transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:hover:bg-transparent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                            >
                                <Icon
                                    className={`w-4 h-4 flex-shrink-0 ${versteckt ? 'text-slate-300' : rechnung ? 'text-emerald-600' : 'text-slate-400'}`}
                                    aria-hidden="true"
                                />
                                <span className="min-w-0 flex-1">
                                    <span className={`flex items-baseline gap-1 text-sm min-w-0 ${versteckt ? 'text-slate-400' : 'text-slate-800'}`}>
                                        <span className="font-medium flex-shrink-0">{label}</span>
                                        {' '}
                                        {dok.dokumentNummer && (
                                            <span
                                                className={`truncate ${versteckt ? '' : 'text-slate-600'}`}
                                                title={dok.dokumentNummer}
                                                data-kuerzung-erlaubt=""
                                            >
                                                {dok.dokumentNummer}
                                            </span>
                                        )}
                                    </span>
                                    {' '}
                                    {/* Zweite Zeile: links Datum & Hinweise (dürfen kürzen), rechts der Betrag (nie gekürzt) */}
                                    <span className={`flex items-center gap-2 text-[11px] tabular-nums min-w-0 ${versteckt ? 'text-slate-400' : 'text-slate-500'}`}>
                                        <span className="flex items-center gap-1.5 min-w-0 flex-1">
                                            <span className="flex-shrink-0">{formatDatum(dok.dokumentDatum ?? dok.eingangsDatum)}</span>
                                            {dok.typ === 'AUFTRAGSBESTAETIGUNG' && dok.liefertermin && (
                                                <span
                                                    className={`flex items-center gap-0.5 min-w-0 ${versteckt ? '' : 'text-rose-600'}`}
                                                    title={`Liefertermin ${formatDatum(dok.liefertermin)}`}
                                                >
                                                    <Clock className="w-3 h-3 flex-shrink-0" aria-hidden="true" />
                                                    <span className="truncate" data-kuerzung-erlaubt="">Liefertermin {formatDatum(dok.liefertermin)}</span>
                                                </span>
                                            )}
                                            {versteckt && (
                                                <span className="px-1.5 rounded bg-slate-100 text-slate-500 flex-shrink-0">ausgeblendet</span>
                                            )}
                                            {zeile.auchOben && (
                                                <span className="px-1.5 rounded bg-slate-100 text-slate-500 flex-shrink-0">auch oben</span>
                                            )}
                                        </span>
                                        {betrag && (
                                            <span className={`text-xs whitespace-nowrap flex-shrink-0 ${versteckt ? '' : 'font-medium text-slate-700'}`}>
                                                {betrag}
                                            </span>
                                        )}
                                    </span>
                                </span>
                            </button>
                            {zeile.hatVerbindung && (
                                <button
                                    type="button"
                                    onClick={() => void onAbhaengen(dok)}
                                    disabled={abhaengenId !== null}
                                    aria-label={`${titel} von der Bestellung abhängen`}
                                    title="Von der Bestellung abhängen"
                                    className="flex-shrink-0 ml-1 p-1.5 rounded text-slate-400 hover:text-rose-700 hover:bg-rose-50 disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    {abhaengenId === dok.id
                                        ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                        : <Unlink className="w-4 h-4" aria-hidden="true" />}
                                </button>
                            )}
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}

/**
 * Die Belege einer Bestellung als Gabel wie bei git: links der Graph, rechts je
 * Beleg eine Zeile, die das Dokument öffnet. Mit höchstens einer Rechnung eine
 * Gabel über alles; bei mehreren Rechnungen oben die Bestellung und darunter je
 * Rechnung ein Kasten mit einer kleinen, geraden Gabel – so sieht man, was zusammengehört.
 */
export function KettenGabel({ dokumente, verbindungen, onOpenPdf, offenesEnde, onRechnungSuchen, onGeaendert }: KettenGabelProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [abhaengenId, setAbhaengenId] = useState<number | null>(null);

    const gruppierung = useMemo(() => gruppiereNachRechnung(dokumente, verbindungen), [dokumente, verbindungen]);
    const einfacherGraph = useMemo(
        () => (gruppierung.art === 'einfach'
            ? baueKettenGraph(dokumente, verbindungen, { offenesEnde, maxSpuren: MAX_SPUREN })
            : null),
        [gruppierung, dokumente, verbindungen, offenesEnde],
    );

    const abhaengen = async (dok: GabelDokument) => {
        const ok = await confirm({
            title: 'Beleg abhängen?',
            message: 'Dieser Beleg wird von der Bestellung gelöst und nicht mehr automatisch zugeordnet.',
            confirmLabel: 'Abhängen',
            variant: 'warning',
        });
        if (!ok) return;
        setAbhaengenId(dok.id);
        try {
            await dokumentAbhaengen(dok.id);
            toast.success(`${GABEL_LABELS[dok.typ]}${dok.dokumentNummer ? ` ${dok.dokumentNummer}` : ''} abgehängt.`);
            onGeaendert();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Beleg konnte nicht abgehängt werden.');
        } finally {
            setAbhaengenId(null);
        }
    };

    const gemeinsam = { onOpenPdf, onGeaendert, abhaengenId, onAbhaengen: abhaengen };

    if (einfacherGraph) {
        return (
            <GabelBlock
                {...gemeinsam}
                graph={einfacherGraph}
                listenName="Belege der Bestellung"
                onRechnungSuchen={onRechnungSuchen}
                uploadZielId={juengstesBestellDokument(dokumente)?.id ?? null}
            />
        );
    }
    if (gruppierung.art !== 'je-rechnung') return null;

    const ohneRechnungDoks = gruppierung.ohneRechnung.map(b => b.dokument);
    return (
        <div className="space-y-2">
            {gruppierung.bestellung.length > 0 && (
                <section aria-label="Bestellung">
                    <h4 className="px-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">Bestellung</h4>
                    <GabelBlock {...gemeinsam} graph={linearerGraph(gruppierung.bestellung)} listenName="Bestellung" uploadZielId={null} />
                </section>
            )}
            {gruppierung.gruppen.map(gruppe => {
                const re = gruppe.rechnung.dokument;
                const name = `${GABEL_LABELS[re.typ]}${re.dokumentNummer ? ` ${re.dokumentNummer}` : ''}`;
                return (
                    <section key={re.id} aria-label={name} className="border border-slate-200 rounded-lg px-1 py-0.5">
                        <GabelBlock
                            {...gemeinsam}
                            graph={linearerGraph([...gruppe.zuleitungen, gruppe.rechnung, ...gruppe.gutschriften])}
                            listenName={`Belege zu ${name}`}
                            uploadZielId={null}
                        />
                    </section>
                );
            })}
            {gruppierung.ohneRechnung.length > 0 && (
                <section aria-label="Noch ohne Rechnung" className="border border-dashed border-slate-300 rounded-lg px-1 py-0.5">
                    <GabelBlock
                        {...gemeinsam}
                        graph={linearerGraph(gruppierung.ohneRechnung, { offenesEnde: true })}
                        listenName="Lieferscheine ohne Rechnung"
                        onRechnungSuchen={onRechnungSuchen}
                        uploadZielId={juengstesBestellDokument(ohneRechnungDoks)?.id ?? null}
                    />
                </section>
            )}
        </div>
    );
}
