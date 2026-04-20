import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Loader2, Scale, Sparkles, Trophy, X } from 'lucide-react';
import { Button } from './ui/button';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { cn } from '../lib/utils';

/**
 * Matrix-Ansicht: Positionen einer Preisanfrage in Zeilen, Lieferanten in Spalten.
 * Guenstigster Preis pro Zeile wird mit bg-rose-50 font-bold markiert.
 *
 * <p>Button "Lieferant X beauftragen" pro Spalte fuehrt die Vergabe durch
 * (routet ArtikelInProjekt-Zeilen auf den Gewinner um — siehe
 * PreisanfrageService.vergebeAuftrag im Backend).</p>
 */

interface AngebotsZelle {
    preisanfrageLieferantId: number;
    einzelpreis: number | null;
    gesamtpreis: number | null;
    mwstProzent: number | null;
    lieferzeitTage: number | null;
    bemerkung: string | null;
    guenstigster: boolean;
}

interface PositionZeile {
    preisanfragePositionId: number;
    produktname: string | null;
    menge: number | null;
    einheit: string | null;
    guenstigsterPreisanfrageLieferantId: number | null;
    zellen: AngebotsZelle[];
}

interface LieferantSpalte {
    preisanfrageLieferantId: number;
    lieferantId: number | null;
    lieferantenname: string | null;
    status: string | null;
}

interface VergleichDto {
    preisanfrageId: number;
    nummer: string;
    bauvorhaben: string | null;
    lieferanten: LieferantSpalte[];
    positionen: PositionZeile[];
}

interface PreisanfrageVergleichModalProps {
    open: boolean;
    onClose: () => void;
    /** ID der Preisanfrage, fuer die die Matrix geladen wird. */
    preisanfrageId: number | null;
    /** Nummer fuer Header (optional — wenn nicht gesetzt, kommt sie aus dem Vergleich). */
    nummerHint?: string;
    /** Callback nach erfolgreicher Vergabe (z.B. Liste neu laden). */
    onVergeben?: () => void;
    /** Callback fuer "Preise eintragen" pro Lieferant — oeffnet Sub-Modal. */
    onPreiseEintragen?: (palId: number) => void;
}

export function PreisanfrageVergleichModal({
    open,
    onClose,
    preisanfrageId,
    nummerHint,
    onVergeben,
    onPreiseEintragen,
}: PreisanfrageVergleichModalProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();

    const [data, setData] = useState<VergleichDto | null>(null);
    const [loading, setLoading] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [vergebend, setVergebend] = useState<number | null>(null);
    const [extrahierend, setExtrahierend] = useState(false);

    const load = useCallback(async () => {
        if (preisanfrageId == null) return;
        setLoading(true);
        setFehler(null);
        try {
            const res = await fetch(`/api/preisanfragen/${preisanfrageId}/vergleich`);
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                throw new Error(reason);
            }
            const dto: VergleichDto = await res.json();
            setData(dto);
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            setFehler(msg);
            setData(null);
        } finally {
            setLoading(false);
        }
    }, [preisanfrageId]);

    useEffect(() => {
        if (open && preisanfrageId != null) {
            load();
        }
    }, [open, preisanfrageId, load]);

    const extrahiere = useCallback(async () => {
        if (preisanfrageId == null) return;
        setExtrahierend(true);
        try {
            toast.info('KI wertet Angebots-PDFs aus — das kann einen Moment dauern…');
            const res = await fetch(`/api/preisanfragen/${preisanfrageId}/angebote/extrahieren`, {
                method: 'POST',
            });
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                throw new Error(reason);
            }
            const ergebnis: { verarbeiteteLieferanten: number; extrahierteAngebote: number; fehler: number } =
                await res.json();
            if (ergebnis.extrahierteAngebote === 0) {
                toast.info('Keine neuen Preise extrahiert — vermutlich bereits erfasst oder keine Antwort-PDFs vorhanden.');
            } else {
                toast.success(
                    `${ergebnis.extrahierteAngebote} Preis${ergebnis.extrahierteAngebote === 1 ? '' : 'e'} aus ` +
                    `${ergebnis.verarbeiteteLieferanten} Lieferanten-PDF${ergebnis.verarbeiteteLieferanten === 1 ? '' : 's'} erkannt.`,
                );
            }
            if (ergebnis.fehler > 0) {
                toast.error(`${ergebnis.fehler} Lieferant${ergebnis.fehler === 1 ? '' : 'en'} konnten nicht ausgewertet werden.`);
            }
            await load();
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            toast.error(`KI-Auswertung fehlgeschlagen: ${msg}`);
        } finally {
            setExtrahierend(false);
        }
    }, [preisanfrageId, toast, load]);

    const vergebeAnLieferant = useCallback(async (sp: LieferantSpalte) => {
        if (preisanfrageId == null) return;
        const name = sp.lieferantenname ?? `Lieferant #${sp.preisanfrageLieferantId}`;
        const ok = await confirmDialog({
            title: 'Auftrag vergeben?',
            message:
                `Soll der Auftrag an ${name} vergeben werden? ` +
                'Die zugehörigen Bedarfspositionen werden auf diesen Lieferanten umgeroutet und bekommen die Angebotspreise.',
            confirmLabel: 'Auftrag vergeben',
            cancelLabel: 'Abbrechen',
            variant: 'info',
        });
        if (!ok) return;
        setVergebend(sp.preisanfrageLieferantId);
        try {
            const res = await fetch(
                `/api/preisanfragen/${preisanfrageId}/vergeben/${sp.preisanfrageLieferantId}`,
                { method: 'POST' },
            );
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                throw new Error(reason);
            }
            toast.success(`Auftrag an ${name} vergeben.`);
            onVergeben?.();
            onClose();
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            toast.error(`Vergabe fehlgeschlagen: ${msg}`);
        } finally {
            setVergebend(null);
        }
    }, [preisanfrageId, confirmDialog, toast, onVergeben, onClose]);

    // Summen pro Lieferant (optional, hilft beim Schnellscan)
    const summen = useMemo<Map<number, number>>(() => {
        const out = new Map<number, number>();
        if (!data) return out;
        for (const sp of data.lieferanten) {
            let s = 0;
            for (const zeile of data.positionen) {
                const z = zeile.zellen.find(x => x.preisanfrageLieferantId === sp.preisanfrageLieferantId);
                if (z?.einzelpreis != null && zeile.menge != null) {
                    s += Number(z.einzelpreis) * Number(zeile.menge);
                }
            }
            out.set(sp.preisanfrageLieferantId, s);
        }
        return out;
    }, [data]);

    if (!open) return null;

    const titel = data?.nummer ?? nummerHint ?? '';

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="vergleich-title"
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl max-h-[92vh] flex flex-col overflow-hidden"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 shrink-0">
                    <div className="flex items-center gap-3 min-w-0">
                        <Scale className="w-5 h-5 text-rose-600 shrink-0" />
                        <div className="min-w-0">
                            <h2 id="vergleich-title" className="text-lg font-semibold text-slate-900 truncate">
                                Vergleich {titel}
                            </h2>
                            <p className="text-xs text-slate-500 mt-0.5 truncate">
                                {data?.bauvorhaben ?? 'Angebote nebeneinander — günstigster Preis pro Zeile markiert'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={extrahiere}
                            disabled={extrahierend || loading}
                            title="PDF-Anhänge der Lieferanten-Antworten per KI auslesen"
                        >
                            {extrahierend ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                                <Sparkles className="w-4 h-4" />
                            )}
                            KI-Preise auslesen
                        </Button>
                        <button
                            onClick={onClose}
                            className="p-1.5 rounded-full hover:bg-white hover:text-rose-700 transition-colors"
                            aria-label="Schließen"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto overflow-x-auto p-6">
                    {loading ? (
                        <div className="flex items-center justify-center py-16 text-slate-500 text-sm">
                            <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                            Vergleichs-Matrix wird geladen…
                        </div>
                    ) : fehler ? (
                        <div className="py-10 text-center text-sm text-rose-700">
                            Fehler: {fehler}
                        </div>
                    ) : !data || data.positionen.length === 0 ? (
                        <div className="py-10 text-center text-sm text-slate-500">
                            Keine Positionen in dieser Preisanfrage.
                        </div>
                    ) : data.lieferanten.length === 0 ? (
                        <div className="py-10 text-center text-sm text-slate-500">
                            Keine Lieferanten zugeordnet.
                        </div>
                    ) : (
                        <MatrixTabelle
                            data={data}
                            summen={summen}
                            onVergeben={vergebeAnLieferant}
                            vergebend={vergebend}
                            onPreiseEintragen={onPreiseEintragen}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

// ─────────────────────────────────────────────────────────────
// Matrix-Tabelle
// ─────────────────────────────────────────────────────────────

function MatrixTabelle({
    data,
    summen,
    onVergeben,
    vergebend,
    onPreiseEintragen,
}: {
    data: VergleichDto;
    summen: Map<number, number>;
    onVergeben: (sp: LieferantSpalte) => void;
    vergebend: number | null;
    onPreiseEintragen?: (palId: number) => void;
}) {
    return (
        <table
            className="min-w-full border-collapse text-sm"
            aria-label={`Preisvergleich ${data.nummer}`}
        >
            <thead>
                <tr>
                    <th
                        className="sticky left-0 z-10 bg-slate-50 border-b border-r border-slate-200 text-left px-3 py-2 font-semibold text-slate-700"
                    >
                        Position
                    </th>
                    {data.lieferanten.map(sp => (
                        <th
                            key={sp.preisanfrageLieferantId}
                            className="border-b border-slate-200 px-3 py-2 text-left font-semibold text-slate-700 min-w-[180px]"
                        >
                            <div className="flex flex-col gap-1">
                                <span className="truncate">{sp.lieferantenname ?? '—'}</span>
                                <span className="text-xs font-normal text-slate-500">
                                    {sp.status ?? '—'}
                                </span>
                            </div>
                        </th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {data.positionen.map(zeile => (
                    <tr key={zeile.preisanfragePositionId} className="border-b border-slate-100">
                        <td className="sticky left-0 z-10 bg-white border-r border-slate-200 px-3 py-2 align-top">
                            <div className="font-medium text-slate-900">
                                {zeile.produktname ?? '—'}
                            </div>
                            <div className="text-xs text-slate-500 mt-0.5">
                                {zeile.menge != null ? formatNumber(zeile.menge) : '—'}
                                {zeile.einheit ? ` ${zeile.einheit}` : ''}
                            </div>
                        </td>
                        {data.lieferanten.map(sp => {
                            const zelle = zeile.zellen.find(
                                z => z.preisanfrageLieferantId === sp.preisanfrageLieferantId,
                            );
                            const istGuenstigster = zelle?.guenstigster === true;
                            return (
                                <td
                                    key={sp.preisanfrageLieferantId}
                                    className={cn(
                                        'px-3 py-2 align-top text-sm',
                                        istGuenstigster && 'bg-rose-50 font-bold text-rose-800',
                                    )}
                                    data-guenstigster={istGuenstigster ? 'true' : undefined}
                                >
                                    {zelle?.einzelpreis != null ? (
                                        <div className="space-y-0.5">
                                            <div className="flex items-center gap-1.5">
                                                {istGuenstigster && (
                                                    <Trophy className="w-3.5 h-3.5 text-rose-700" aria-label="günstigster Preis" />
                                                )}
                                                <span>
                                                    {formatEuro(zelle.einzelpreis)}
                                                    {zeile.einheit ? ` / ${zeile.einheit}` : ''}
                                                </span>
                                            </div>
                                            {zelle.lieferzeitTage != null && (
                                                <div className="text-xs text-slate-500 font-normal">
                                                    Lieferzeit: {zelle.lieferzeitTage} Tage
                                                </div>
                                            )}
                                            {zelle.bemerkung && (
                                                <div className="text-xs text-slate-500 font-normal italic truncate" title={zelle.bemerkung}>
                                                    {zelle.bemerkung}
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <span className="text-xs text-slate-400">—</span>
                                    )}
                                </td>
                            );
                        })}
                    </tr>
                ))}
                {/* Summenzeile */}
                <tr className="bg-slate-50">
                    <td className="sticky left-0 z-10 bg-slate-50 border-r border-slate-200 px-3 py-2 font-semibold text-slate-700">
                        Summe (netto)
                    </td>
                    {data.lieferanten.map(sp => (
                        <td
                            key={sp.preisanfrageLieferantId}
                            className="px-3 py-2 font-semibold text-slate-800"
                        >
                            {formatEuro(summen.get(sp.preisanfrageLieferantId) ?? 0)}
                        </td>
                    ))}
                </tr>
                {/* Action-Zeile */}
                <tr>
                    <td className="sticky left-0 z-10 bg-white border-r border-slate-200 px-3 py-3 text-xs text-slate-500">
                        Aktion
                    </td>
                    {data.lieferanten.map(sp => (
                        <td key={sp.preisanfrageLieferantId} className="px-3 py-3">
                            <div className="flex flex-col gap-1">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => onPreiseEintragen?.(sp.preisanfrageLieferantId)}
                                    disabled={vergebend != null || !onPreiseEintragen}
                                >
                                    Preise eintragen
                                </Button>
                                <Button
                                    size="sm"
                                    onClick={() => onVergeben(sp)}
                                    disabled={vergebend != null}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    {vergebend === sp.preisanfrageLieferantId ? (
                                        <Loader2 className="w-4 h-4 animate-spin" />
                                    ) : (
                                        <CheckCircle2 className="w-4 h-4" />
                                    )}
                                    Beauftragen
                                </Button>
                            </div>
                        </td>
                    ))}
                </tr>
            </tbody>
        </table>
    );
}

function formatEuro(n: number | null): string {
    if (n == null) return '—';
    return new Intl.NumberFormat('de-DE', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
    }).format(n);
}

function formatNumber(n: number): string {
    return new Intl.NumberFormat('de-DE', {
        maximumFractionDigits: 3,
    }).format(n);
}
