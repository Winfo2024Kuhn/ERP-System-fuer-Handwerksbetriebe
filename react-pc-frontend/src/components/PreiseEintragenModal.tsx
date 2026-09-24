import { useCallback, useEffect, useMemo, useState } from 'react';
import { Loader2, Save, Sparkles, X } from 'lucide-react';
import { Button } from './ui/button';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';

/**
 * Manuelles Erfassen / Korrigieren der Angebotspreise pro
 * Preisanfrage-Lieferant. Erlaubt auch das Uebernehmen von KI-Vorschlaegen
 * (markiert mit Sparkle-Icon + "KI-Vorschlag, bitte pruefen").
 */

interface AngebotsZelle {
    preisanfrageLieferantId: number;
    einzelpreis: number | null;
    mwstProzent: number | null;
    lieferzeitTage: number | null;
    bemerkung: string | null;
}

interface PositionZeile {
    preisanfragePositionId: number;
    produktname: string | null;
    menge: number | null;
    einheit: string | null;
    zellen: AngebotsZelle[];
}

interface LieferantSpalte {
    preisanfrageLieferantId: number;
    lieferantenname: string | null;
}

interface VergleichDto {
    preisanfrageId: number;
    nummer: string;
    lieferanten: LieferantSpalte[];
    positionen: PositionZeile[];
}

interface AngebotDetailRow {
    positionId: number;
    produktname: string | null;
    menge: number | null;
    einheit: string | null;
    einzelpreis: string; // User-Input: String, damit Komma moeglich
    mwstProzent: string;
    lieferzeitTage: string;
    bemerkung: string;
    istKiVorschlag: boolean;
}

interface PreiseEintragenModalProps {
    open: boolean;
    onClose: () => void;
    preisanfrageId: number | null;
    preisanfrageLieferantId: number | null;
    /** Callback nach erfolgreichem Speichern (z.B. Vergleich neu laden). */
    onSaved?: () => void;
}

export function PreiseEintragenModal({
    open,
    onClose,
    preisanfrageId,
    preisanfrageLieferantId,
    onSaved,
}: PreiseEintragenModalProps) {
    const toast = useToast();

    const [titel, setTitel] = useState('');
    const [rows, setRows] = useState<AngebotDetailRow[]>([]);
    const [loading, setLoading] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const load = useCallback(async () => {
        if (preisanfrageId == null || preisanfrageLieferantId == null) return;
        setLoading(true);
        setFehler(null);
        try {
            const res = await fetch(`/api/preisanfragen/${preisanfrageId}/vergleich`);
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                throw new Error(reason);
            }
            const dto: VergleichDto = await res.json();
            const sp = dto.lieferanten.find(l => l.preisanfrageLieferantId === preisanfrageLieferantId);
            setTitel(
                `${dto.nummer} · ${sp?.lieferantenname ?? `Lieferant #${preisanfrageLieferantId}`}`,
            );

            // KI-Vorschlag erkennen: an der Zelle gibt es keinen expliziten Flag im DTO,
            // aber "erfasstDurch=ki-extraktion" haengt am Angebot. Wir markieren jede
            // vorbefuellte Zelle mit einem Hinweis, damit der User weiss "bitte pruefen".
            // Heuristik: Wenn der Preis vom Backend vorgefuellt kommt, markieren.
            const detailRows: AngebotDetailRow[] = dto.positionen.map(p => {
                const z = p.zellen.find(c => c.preisanfrageLieferantId === preisanfrageLieferantId);
                return {
                    positionId: p.preisanfragePositionId,
                    produktname: p.produktname,
                    menge: p.menge,
                    einheit: p.einheit,
                    einzelpreis: z?.einzelpreis != null ? String(z.einzelpreis).replace('.', ',') : '',
                    mwstProzent: z?.mwstProzent != null ? String(z.mwstProzent).replace('.', ',') : '',
                    lieferzeitTage: z?.lieferzeitTage != null ? String(z.lieferzeitTage) : '',
                    bemerkung: z?.bemerkung ?? '',
                    istKiVorschlag: z?.einzelpreis != null,
                };
            });
            setRows(detailRows);
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            setFehler(msg);
        } finally {
            setLoading(false);
        }
    }, [preisanfrageId, preisanfrageLieferantId]);

    useEffect(() => {
        if (open && preisanfrageId != null && preisanfrageLieferantId != null) {
            load();
        }
    }, [open, preisanfrageId, preisanfrageLieferantId, load]);

    const mindestensEinPreis = useMemo(
        () => rows.some(r => r.einzelpreis.trim() !== ''),
        [rows],
    );

    const updateRow = (idx: number, patch: Partial<AngebotDetailRow>) => {
        setRows(rs => rs.map((r, i) => (i === idx ? { ...r, ...patch, istKiVorschlag: false } : r)));
    };

    const submit = useCallback(async () => {
        if (preisanfrageLieferantId == null) return;
        if (!mindestensEinPreis) {
            toast.warning('Mindestens ein Preis muss eingetragen sein.');
            return;
        }
        setSubmitting(true);
        try {
            let gespeichert = 0;
            for (const r of rows) {
                const preis = parseDezimal(r.einzelpreis);
                if (preis == null) continue;
                const body = {
                    preisanfrageLieferantId,
                    preisanfragePositionId: r.positionId,
                    einzelpreis: preis,
                    mwstProzent: parseDezimal(r.mwstProzent),
                    lieferzeitTage: parseInt10(r.lieferzeitTage),
                    bemerkung: r.bemerkung.trim() || null,
                };
                const res = await fetch('/api/preisanfragen/angebote', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body),
                });
                if (!res.ok) {
                    const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                    throw new Error(`Position ${r.produktname ?? r.positionId}: ${reason}`);
                }
                gespeichert++;
            }
            toast.success(`${gespeichert} Preis${gespeichert === 1 ? '' : 'e'} gespeichert.`);
            onSaved?.();
            onClose();
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            toast.error(`Speichern fehlgeschlagen: ${msg}`);
        } finally {
            setSubmitting(false);
        }
    }, [preisanfrageLieferantId, mindestensEinPreis, rows, toast, onSaved, onClose]);

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="preise-eintragen-title"
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl max-h-[92vh] flex flex-col overflow-hidden"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 shrink-0">
                    <div className="min-w-0">
                        <h2 id="preise-eintragen-title" className="text-lg font-semibold text-slate-900 truncate">
                            Preise eintragen
                        </h2>
                        <p className="text-xs text-slate-500 mt-0.5 truncate">{titel}</p>
                    </div>
                    <button
                        onClick={onClose}
                        disabled={submitting}
                        className="p-1.5 rounded-full hover:bg-white hover:text-rose-700 transition-colors disabled:opacity-50"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6">
                    {loading ? (
                        <div className="flex items-center justify-center py-16 text-slate-500 text-sm">
                            <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                            Positionen werden geladen…
                        </div>
                    ) : fehler ? (
                        <div className="py-10 text-center text-sm text-rose-700">Fehler: {fehler}</div>
                    ) : rows.length === 0 ? (
                        <div className="py-10 text-center text-sm text-slate-500">
                            Keine Positionen in dieser Preisanfrage.
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {rows.map((row, idx) => (
                                <div
                                    key={row.positionId}
                                    className={cn(
                                        'rounded-lg border border-slate-200 p-4',
                                        row.istKiVorschlag && 'border-rose-200 bg-rose-50/40',
                                    )}
                                >
                                    <div className="flex items-start justify-between gap-3 mb-3">
                                        <div className="min-w-0">
                                            <div className="font-medium text-slate-900 truncate">
                                                {row.produktname ?? '—'}
                                            </div>
                                            <div className="text-xs text-slate-500">
                                                {row.menge != null ? formatNumber(row.menge) : '—'}
                                                {row.einheit ? ` ${row.einheit}` : ''}
                                            </div>
                                        </div>
                                        {row.istKiVorschlag && (
                                            <span className="inline-flex items-center gap-1 text-xs font-medium text-rose-700 bg-white border border-rose-200 rounded-full px-2 py-0.5 shrink-0">
                                                <Sparkles className="w-3 h-3" />
                                                KI-Vorschlag, bitte prüfen
                                            </span>
                                        )}
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                                        <FeldInput
                                            label={`Einzelpreis ${row.einheit ? `(€ / ${row.einheit})` : '(€)'}`}
                                            value={row.einzelpreis}
                                            onChange={v => updateRow(idx, { einzelpreis: v })}
                                            placeholder="0,00"
                                        />
                                        <FeldInput
                                            label="MwSt. (%)"
                                            value={row.mwstProzent}
                                            onChange={v => updateRow(idx, { mwstProzent: v })}
                                            placeholder="19"
                                        />
                                        <FeldInput
                                            label="Lieferzeit (Tage)"
                                            value={row.lieferzeitTage}
                                            onChange={v => updateRow(idx, { lieferzeitTage: v })}
                                            placeholder="10"
                                        />
                                        <FeldInput
                                            label="Bemerkung"
                                            value={row.bemerkung}
                                            onChange={v => updateRow(idx, { bemerkung: v })}
                                            placeholder="z. B. Staffelpreis ab 500 kg"
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 px-6 py-4 flex items-center justify-end gap-2 bg-slate-50 shrink-0">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={submit}
                        disabled={submitting || !mindestensEinPreis || loading}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        {submitting ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                            <Save className="w-4 h-4" />
                        )}
                        Preise speichern
                    </Button>
                </div>
            </div>
        </div>
    );
}

function FeldInput({
    label,
    value,
    onChange,
    placeholder,
}: {
    label: string;
    value: string;
    onChange: (v: string) => void;
    placeholder?: string;
}) {
    return (
        <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-600">{label}</span>
            <input
                type="text"
                value={value}
                onChange={e => onChange(e.target.value)}
                placeholder={placeholder}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent"
            />
        </label>
    );
}

function parseDezimal(s: string): number | null {
    if (!s || s.trim() === '') return null;
    const n = Number(s.replace(',', '.').trim());
    return isNaN(n) ? null : n;
}

function parseInt10(s: string): number | null {
    if (!s || s.trim() === '') return null;
    const n = parseInt(s, 10);
    return isNaN(n) ? null : n;
}

function formatNumber(n: number): string {
    return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 3 }).format(n);
}
