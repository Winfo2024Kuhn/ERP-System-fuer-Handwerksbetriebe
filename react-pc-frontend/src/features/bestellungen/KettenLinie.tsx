import { useId, useMemo, useState, type ReactNode } from 'react';
import { Clock, File, FileBadge, FileCheck, FileSearch, FileText, Receipt, RefreshCw, Truck, Unlink, type LucideIcon } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { parseIsoDatum, type KettenDokumentTyp } from './bestellungenListe';
import {
    KETTEN_LABELS,
    istRechnungsTyp,
    juengstesBestellDokument,
    ordneKettenLinie,
    verbundeneIds,
    type KettenLinienDokument,
    type KettenVerbindung,
} from './kettenLinieLogik';
import { DokumentPositionenListe, PositionenKnopf } from './DokumentPositionenAufklappen';
import { kannPositionenHaben } from './dokumentPositionen';
import { RechnungHochladenKnopf } from './RechnungHochladenKnopf';
import { dokumentAbhaengen } from './rechnungsVorschlag';

export interface KettenLinieProps {
    dokumente: KettenLinienDokument[];
    /** Verknüpfungen innerhalb der Kette – setzen Werkstoffzeugnisse unter ihren Lieferschein. */
    verbindungen?: KettenVerbindung[] | null;
    /** Klick auf eine Zeile öffnet das PDF (Standard). */
    onOpenPdf?: (url: string, title: string) => void;
    /** Ersetzt das Öffnen des PDFs, z. B. durch ein Detailfenster. */
    onZeileKlick?: (dok: KettenLinienDokument) => void;
    /** Welcher Betrag rechts steht. Standard: brutto. */
    betrag?: 'brutto' | 'netto';
    /** Laufende Bestellung ohne Rechnung: gestrichelter Platzhalter am Ende. */
    offenesEnde?: boolean;
    /** Zeigt am offenen Ende „Rechnung hochladen“ und „Suchen“. */
    onRechnungSuchen?: () => void;
    /** Zeigt je verknüpftem Beleg einen Abhängen-Knopf (mit Rückfrage). */
    abhaengbar?: boolean;
    /** Nach Abhängen oder Hochladen – die Seite lädt neu. */
    onGeaendert?: () => void;
    /** Zusätzliche Textzeile je Beleg (steht im Klickbereich, daher nur Text, keine Knöpfe). */
    zeilenZusatz?: (dok: KettenLinienDokument) => ReactNode;
    /** Aktive Suche: passende Artikelpositionen werden im aufgeklappten Bereich hervorgehoben. */
    suchbegriff?: string;
    /** Name der Liste für Screenreader */
    listenName?: string;
}

const ICONS: Record<KettenDokumentTyp, LucideIcon> = {
    ANGEBOT: FileText,
    AUFTRAGSBESTAETIGUNG: FileCheck,
    LIEFERSCHEIN: Truck,
    WERKSTOFFZEUGNIS: FileBadge,
    RECHNUNG: Receipt,
    GUTSCHRIFT: Receipt,
    SONSTIG: File,
};

/** Abstand der Punktmitte vom oberen Rand einer Zeile (px) – auf Höhe der ersten Textzeile. */
const PUNKT_Y = 18;
/** Mitte der Linie von links (px). */
const LINIE_X = 10;

// parseIsoDatum liest „2026-09-22“ als Ortszeit – new Date() nähme UTC und zeigte westlich davon den Vortag
const formatDatum = (iso: string | null | undefined): string => parseIsoDatum(iso)?.toLocaleDateString('de-DE') ?? '–';

const formatEuro = (wert: number | null | undefined): string | null => {
    if (wert == null || !Number.isFinite(wert)) return null;
    return `${wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`;
};

const titelVon = (dok: KettenLinienDokument) =>
    `${KETTEN_LABELS[dok.typ]}${dok.dokumentNummer ? ` ${dok.dokumentNummer}` : ''}`;

/** Senkrechte Linie oberhalb bzw. unterhalb des Punktes einer Zeile. */
function LinienStueck({ lage, gestrichelt }: { lage: 'oben' | 'unten'; gestrichelt?: boolean }) {
    const position = lage === 'oben' ? { top: 0, height: PUNKT_Y } : { top: PUNKT_Y, bottom: 0 };
    return (
        <span
            aria-hidden="true"
            data-linie={gestrichelt ? 'gestrichelt' : 'durchgehend'}
            className={`absolute ${gestrichelt ? 'w-0 border-l-2 border-dashed border-amber-400' : 'w-0.5 bg-slate-300'}`}
            style={{ ...position, left: LINIE_X - 1 }}
        />
    );
}

/** Punkt auf der Linie: Rechnung grün hervorgehoben, sonst weiß mit rose Rand, ausgeblendet grau. */
function Punkt({ art, ausgeblendet }: { art: 'rechnung' | 'beleg' | 'offen'; ausgeblendet?: boolean }) {
    const groesse = art === 'rechnung' ? 14 : art === 'offen' ? 12 : 10;
    const klassen = art === 'offen'
        ? 'bg-white border-2 border-dashed border-amber-500'
        : art === 'rechnung'
            ? (ausgeblendet ? 'bg-slate-300 border-2 border-white' : 'bg-emerald-500 border-2 border-white ring-4 ring-emerald-100')
            : (ausgeblendet ? 'bg-white border-2 border-slate-300' : 'bg-white border-2 border-rose-500');
    return (
        <span
            aria-hidden="true"
            data-punkt={art}
            className={`absolute rounded-full ${klassen}`}
            style={{ width: groesse, height: groesse, top: PUNKT_Y - groesse / 2, left: LINIE_X - groesse / 2 }}
        />
    );
}

interface AbhaengenKnopfProps {
    dok: KettenLinienDokument;
    /** Gerade wird irgendein Beleg abgehängt – dann sind alle Knöpfe gesperrt. */
    laufendeId: number | null;
    setLaufendeId: (id: number | null) => void;
    onGeaendert?: () => void;
}

/** Eigener Baustein, damit Rückfrage und Meldungen nur gebraucht werden, wo abgehängt werden darf. */
function AbhaengenKnopf({ dok, laufendeId, setLaufendeId, onGeaendert }: AbhaengenKnopfProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const titel = titelVon(dok);

    const abhaengen = async () => {
        const ok = await confirm({
            title: 'Beleg abhängen?',
            message: 'Dieser Beleg wird von der Bestellung gelöst und nicht mehr automatisch zugeordnet.',
            confirmLabel: 'Abhängen',
            variant: 'warning',
        });
        if (!ok) return;
        setLaufendeId(dok.id);
        try {
            await dokumentAbhaengen(dok.id);
            toast.success(`${titel} abgehängt.`);
            onGeaendert?.();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Beleg konnte nicht abgehängt werden.');
        } finally {
            setLaufendeId(null);
        }
    };

    return (
        <button
            type="button"
            onClick={() => void abhaengen()}
            disabled={laufendeId !== null}
            aria-label={`${titel} von der Bestellung abhängen`}
            title="Von der Bestellung abhängen"
            className="flex-shrink-0 ml-1 mt-1 p-1.5 rounded text-slate-400 hover:text-rose-700 hover:bg-rose-50 disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            {laufendeId === dok.id
                ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                : <Unlink className="w-4 h-4" aria-hidden="true" />}
        </button>
    );
}

/**
 * Die Belege einer Lieferanten-Kette als eine gerade senkrechte Linie mit Punkten,
 * wie git-Commits auf einem Branch – ohne Abzweigungen. Rechts neben jedem Punkt
 * steht eine Zeile mit Symbol, Art, Nummer, Datum und Betrag; ein Klick öffnet das Dokument.
 */
export function KettenLinie({
    dokumente,
    verbindungen,
    onOpenPdf,
    onZeileKlick,
    betrag = 'brutto',
    offenesEnde = false,
    onRechnungSuchen,
    abhaengbar = false,
    onGeaendert,
    zeilenZusatz,
    suchbegriff,
    listenName = 'Belege der Kette',
}: KettenLinieProps) {
    const [abhaengenId, setAbhaengenId] = useState<number | null>(null);
    // Belege mit aufgeklappten Artikelpositionen – mehrere dürfen gleichzeitig offen sein
    const [aufgeklappt, setAufgeklappt] = useState<ReadonlySet<number>>(() => new Set());
    const idPraefix = useId();
    const umschalten = (id: number) => setAufgeklappt(vorher => {
        const neu = new Set(vorher);
        if (!neu.delete(id)) neu.add(id);
        return neu;
    });
    const zeilen = useMemo(() => ordneKettenLinie(dokumente, verbindungen), [dokumente, verbindungen]);
    const verbunden = useMemo(() => verbundeneIds(dokumente, verbindungen), [dokumente, verbindungen]);
    const uploadZielId = useMemo(() => juengstesBestellDokument(dokumente)?.id ?? null, [dokumente]);

    if (zeilen.length === 0) return null;

    return (
        <ul className="relative" aria-label={listenName} data-testid="ketten-linie">
            {zeilen.map((dok, i) => {
                const Icon = ICONS[dok.typ];
                const label = KETTEN_LABELS[dok.typ];
                const versteckt = dok.ausgeblendet === true;
                const rechnung = istRechnungsTyp(dok.typ);
                const betragText = formatEuro(betrag === 'netto' ? dok.betragNetto : dok.betragBrutto);
                const nurEingang = !dok.dokumentDatum && Boolean(dok.eingangsDatum);
                const letzte = i === zeilen.length - 1;
                const klickbar = Boolean(onZeileKlick) || Boolean(dok.pdfUrl && onOpenPdf);
                const zusatz = zeilenZusatz?.(dok);
                const oeffnen = () => {
                    if (onZeileKlick) onZeileKlick(dok);
                    else if (dok.pdfUrl) onOpenPdf?.(dok.pdfUrl, dok.dokumentNummer || dok.dateiname || label);
                };
                const offen = aufgeklappt.has(dok.id);
                const bereichId = `${idPraefix}-positionen-${dok.id}`;
                return (
                    <li key={dok.id} className="relative py-0.5 pl-5">
                        {i > 0 && <LinienStueck lage="oben" />}
                        {(!letzte || offenesEnde) && <LinienStueck lage="unten" gestrichelt={letzte} />}
                        <Punkt art={rechnung ? 'rechnung' : 'beleg'} ausgeblendet={versteckt} />
                        <div className="flex items-start gap-0.5">
                            <button
                                type="button"
                                onClick={oeffnen}
                                disabled={!klickbar}
                                title={klickbar ? 'Dokument öffnen' : 'Keine Vorschau vorhanden'}
                                className="flex-1 min-w-0 min-h-11 flex items-start gap-2 rounded-md px-1.5 py-1.5 text-left transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:hover:bg-transparent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                            >
                                <Icon
                                    className={`w-4 h-4 mt-0.5 flex-shrink-0 ${versteckt ? 'text-slate-300' : rechnung ? 'text-emerald-600' : 'text-slate-400'}`}
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
                                            <span
                                                className="flex-shrink-0"
                                                title={nurEingang ? 'Kein Dokumentdatum erkannt – Eingangsdatum' : undefined}
                                            >
                                                {formatDatum(dok.dokumentDatum ?? dok.eingangsDatum)}
                                                {nurEingang && <span className="ml-1 text-slate-400">Eingang</span>}
                                            </span>
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
                                        </span>
                                        {betragText && (
                                            <span
                                                className={`text-xs whitespace-nowrap flex-shrink-0 ${versteckt ? '' : 'font-medium text-slate-700'}`}
                                                title={betrag === 'netto' ? 'Betrag netto' : 'Betrag brutto'}
                                            >
                                                {betragText}
                                            </span>
                                        )}
                                    </span>
                                    {zusatz != null && zusatz !== false && (
                                        <span className="mt-0.5 block text-[11px] text-slate-400 min-w-0">{zusatz}</span>
                                    )}
                                </span>
                            </button>
                            {kannPositionenHaben(dok.typ) && (
                                <PositionenKnopf
                                    offen={offen}
                                    onUmschalten={() => umschalten(dok.id)}
                                    dokumentName={titelVon(dok)}
                                    bereichId={bereichId}
                                    className="mt-1"
                                />
                            )}
                            {abhaengbar && verbunden.has(dok.id) && (
                                <AbhaengenKnopf
                                    dok={dok}
                                    laufendeId={abhaengenId}
                                    setLaufendeId={setAbhaengenId}
                                    onGeaendert={onGeaendert}
                                />
                            )}
                        </div>
                        {/* Eingerückt unter der Zeile; die senkrechte Linie läuft links daneben weiter */}
                        {offen && (
                            <DokumentPositionenListe
                                id={bereichId}
                                dokumentId={dok.id}
                                suchbegriff={suchbegriff}
                                kompakt
                                className="ml-7 mr-1 mb-1.5 mt-0.5 rounded-md border border-slate-200 bg-white px-2 py-1"
                            />
                        )}
                    </li>
                );
            })}
            {offenesEnde && (
                <li key="offen" className="relative flex flex-col gap-1.5 py-0.5 pl-5">
                    <LinienStueck lage="oben" gestrichelt />
                    <Punkt art="offen" />
                    <span className="text-sm font-medium text-amber-700 px-1.5 pt-1.5 leading-5">Rechnung fehlt noch</span>
                    {onRechnungSuchen && (
                        <div className="flex gap-2 px-1.5 pb-1 flex-wrap">
                            <RechnungHochladenKnopf
                                bestellDokumentId={uploadZielId}
                                onHochgeladen={() => onGeaendert?.()}
                                className="py-1"
                            />
                            <Button size="sm" variant="outline" className="py-1" onClick={onRechnungSuchen}>
                                <FileSearch className="w-4 h-4" aria-hidden="true" />
                                Suchen
                            </Button>
                        </div>
                    )}
                </li>
            )}
        </ul>
    );
}
