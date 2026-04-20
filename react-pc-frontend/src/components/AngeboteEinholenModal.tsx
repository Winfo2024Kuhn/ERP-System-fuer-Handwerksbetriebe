import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, Scale, Search, Send, Truck, X } from 'lucide-react';
import { Button } from './ui/button';
import { DatePicker } from './ui/datepicker';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';

/**
 * Schlankes Interface fuer eine Bedarfsposition, die als Position einer
 * Preisanfrage vorgeschlagen werden kann. Kommt entweder aus dem BestellungEditor
 * (vorausgewaehlt) oder wird im Modal via /api/bestellungen/offen nachgeladen.
 */
export interface BedarfPosition {
    id: number;
    artikelId?: number | null;
    externeArtikelnummer?: string | null;
    produktname?: string | null;
    produkttext?: string | null;
    werkstoffName?: string | null;
    menge?: number | null;
    einheit?: string | null;
    kommentar?: string | null;
    projektId?: number | null;
    projektName?: string | null;
    lieferantName?: string | null;
}

interface LieferantListenEintrag {
    id: number;
    lieferantenname: string;
    ort?: string | null;
    plz?: string | null;
    istAktiv?: boolean | null;
}

interface AngeboteEinholenModalProps {
    open: boolean;
    onClose: () => void;
    /** Vorausgewaehlte Positionen (z.B. aus einer Bedarf-Gruppe). */
    vorausgewaehltePositionen?: BedarfPosition[];
    /** Optional: Bauvorhaben-Text, der ins Formular uebernommen wird. */
    vorschlagBauvorhaben?: string;
    /** Callback nach erfolgreichem Versand (z.B. Liste neu laden). */
    onVersendet?: (preisanfrageId: number) => void;
}

/**
 * Modal zum Einholen von Angeboten bei mehreren Lieferanten gleichzeitig.
 * Erzeugt eine Preisanfrage und versendet sie sofort an alle gewaehlten
 * Lieferanten. Validierung: mindestens 1 Position + 1 Lieferant + Rueckmeldefrist.
 */
export function AngeboteEinholenModal({
    open,
    onClose,
    vorausgewaehltePositionen,
    vorschlagBauvorhaben,
    onVersendet,
}: AngeboteEinholenModalProps) {
    const toast = useToast();
    const navigate = useNavigate();

    const [offenePositionen, setOffenePositionen] = useState<BedarfPosition[]>([]);
    const [positionenLoading, setPositionenLoading] = useState(false);
    const [positionenError, setPositionenError] = useState<string | null>(null);

    const [lieferanten, setLieferanten] = useState<LieferantListenEintrag[]>([]);
    const [lieferantenLoading, setLieferantenLoading] = useState(false);
    const [lieferantenSuche, setLieferantenSuche] = useState('');

    const [selectedPositionIds, setSelectedPositionIds] = useState<Set<number>>(new Set());
    const [selectedLieferantIds, setSelectedLieferantIds] = useState<Set<number>>(new Set());
    const [antwortFrist, setAntwortFrist] = useState('');
    const [notiz, setNotiz] = useState('');
    const [bauvorhaben, setBauvorhaben] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const arbeitsPositionen: BedarfPosition[] = useMemo(() => {
        if (vorausgewaehltePositionen && vorausgewaehltePositionen.length > 0) {
            return vorausgewaehltePositionen;
        }
        return offenePositionen;
    }, [vorausgewaehltePositionen, offenePositionen]);

    // Reset + Initialisierung beim Oeffnen
    useEffect(() => {
        if (!open) return;

        setNotiz('');
        setAntwortFrist(defaultFristIso(7));
        setLieferantenSuche('');
        setSelectedLieferantIds(new Set());

        const vorschlag = vorschlagBauvorhaben
            ?? firstProjektName(vorausgewaehltePositionen)
            ?? '';
        setBauvorhaben(vorschlag);

        if (vorausgewaehltePositionen && vorausgewaehltePositionen.length > 0) {
            setSelectedPositionIds(new Set(vorausgewaehltePositionen.map(p => p.id)));
            setOffenePositionen([]);
            setPositionenError(null);
            return;
        }

        // Ohne Vorauswahl: offene Bedarfspositionen laden
        setSelectedPositionIds(new Set());
        setPositionenLoading(true);
        setPositionenError(null);
        fetch('/api/bestellungen/offen')
            .then(async res => {
                if (!res.ok) throw new Error('Bedarfspositionen konnten nicht geladen werden.');
                return res.json();
            })
            .then((data: unknown) => {
                const list = Array.isArray(data) ? (data as BedarfPosition[]) : [];
                setOffenePositionen(list);
            })
            .catch((err: Error) => {
                setOffenePositionen([]);
                setPositionenError(err.message || 'Bedarfspositionen konnten nicht geladen werden.');
            })
            .finally(() => setPositionenLoading(false));
    }, [open, vorausgewaehltePositionen, vorschlagBauvorhaben]);

    // Lieferanten laden (einmalig beim Oeffnen)
    useEffect(() => {
        if (!open) return;

        setLieferantenLoading(true);
        fetch('/api/lieferanten?size=500')
            .then(async res => {
                if (!res.ok) throw new Error('Lieferanten konnten nicht geladen werden.');
                return res.json();
            })
            .then((data: unknown) => {
                const raw = (data as { lieferanten?: LieferantListenEintrag[] })?.lieferanten;
                const list = Array.isArray(raw) ? raw : [];
                setLieferanten(list.filter(l => l.istAktiv !== false));
            })
            .catch(() => {
                setLieferanten([]);
                toast.error('Lieferantenliste konnte nicht geladen werden.');
            })
            .finally(() => setLieferantenLoading(false));
    }, [open, toast]);

    const togglePosition = useCallback((id: number) => {
        setSelectedPositionIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }, []);

    const toggleLieferant = useCallback((id: number) => {
        setSelectedLieferantIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }, []);

    const gefilterteLieferanten = useMemo(() => {
        const q = lieferantenSuche.trim().toLowerCase();
        if (!q) return lieferanten;
        return lieferanten.filter(l => {
            const hay = `${l.lieferantenname ?? ''} ${l.ort ?? ''} ${l.plz ?? ''}`.toLowerCase();
            return hay.includes(q);
        });
    }, [lieferanten, lieferantenSuche]);

    const canSubmit = (
        !submitting
        && selectedPositionIds.size > 0
        && selectedLieferantIds.size > 0
        && antwortFrist.trim().length > 0
    );

    const handleSubmit = async () => {
        if (!canSubmit) return;

        const positionen = arbeitsPositionen
            .filter(p => selectedPositionIds.has(p.id))
            .map((p, idx) => ({
                artikelInProjektId: p.id,
                artikelId: p.artikelId ?? null,
                externeArtikelnummer: p.externeArtikelnummer ?? null,
                produktname: p.produktname ?? null,
                produkttext: p.produkttext ?? null,
                werkstoffName: p.werkstoffName ?? null,
                menge: p.menge ?? null,
                einheit: p.einheit ?? null,
                kommentar: p.kommentar ?? null,
                reihenfolge: idx + 1,
            }));

        const payload = {
            projektId: firstProjektId(arbeitsPositionen, selectedPositionIds),
            bauvorhaben: bauvorhaben.trim() || null,
            antwortFrist,
            notiz: notiz.trim() || null,
            lieferantIds: Array.from(selectedLieferantIds),
            positionen,
        };

        setSubmitting(true);
        try {
            const createRes = await fetch('/api/preisanfragen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!createRes.ok) {
                const reason = createRes.headers.get('X-Error-Reason') ?? `HTTP ${createRes.status}`;
                throw new Error(reason);
            }
            const created: { id: number } = await createRes.json();

            const sendRes = await fetch(`/api/preisanfragen/${created.id}/versenden`, {
                method: 'POST',
            });
            if (!sendRes.ok) {
                const reason = sendRes.headers.get('X-Error-Reason') ?? `HTTP ${sendRes.status}`;
                throw new Error(reason);
            }

            toast.success(`Preisanfrage an ${selectedLieferantIds.size} Lieferant${selectedLieferantIds.size === 1 ? '' : 'en'} versendet.`);
            onVersendet?.(created.id);
            onClose();
            navigate('/einkauf/preisanfragen');
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            toast.error(`Preisanfrage konnte nicht versendet werden: ${msg}`);
        } finally {
            setSubmitting(false);
        }
    };

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="angebote-einholen-title"
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl max-h-[92vh] flex flex-col overflow-hidden"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 shrink-0">
                    <div className="flex items-center gap-3">
                        <Scale className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 id="angebote-einholen-title" className="text-lg font-semibold text-slate-900">
                                Angebote einholen
                            </h2>
                            <p className="text-xs text-slate-500 mt-0.5">
                                Preisanfrage an mehrere Lieferanten gleichzeitig
                            </p>
                        </div>
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
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Bauvorhaben */}
                    <section>
                        <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="pa-bauvorhaben">
                            Bauvorhaben <span className="text-slate-400 font-normal">(optional)</span>
                        </label>
                        <input
                            id="pa-bauvorhaben"
                            type="text"
                            value={bauvorhaben}
                            onChange={e => setBauvorhaben(e.target.value)}
                            placeholder="z. B. Stahlbau Musterstraße"
                            className="w-full h-10 px-3 rounded-md border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                        />
                    </section>

                    {/* Positionen */}
                    <section>
                        <div className="flex items-center justify-between mb-2">
                            <h3 className="text-sm font-semibold text-slate-800">
                                Positionen <span className="text-slate-500 font-normal">({selectedPositionIds.size} ausgewählt)</span>
                            </h3>
                            {arbeitsPositionen.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (selectedPositionIds.size === arbeitsPositionen.length) {
                                            setSelectedPositionIds(new Set());
                                        } else {
                                            setSelectedPositionIds(new Set(arbeitsPositionen.map(p => p.id)));
                                        }
                                    }}
                                    className="text-xs text-rose-700 hover:text-rose-800 font-medium"
                                >
                                    {selectedPositionIds.size === arbeitsPositionen.length ? 'Alle abwählen' : 'Alle auswählen'}
                                </button>
                            )}
                        </div>
                        <PositionenListe
                            positionen={arbeitsPositionen}
                            selectedIds={selectedPositionIds}
                            onToggle={togglePosition}
                            loading={positionenLoading}
                            error={positionenError}
                        />
                    </section>

                    {/* Lieferanten */}
                    <section>
                        <div className="flex items-center justify-between mb-2">
                            <h3 className="text-sm font-semibold text-slate-800">
                                Lieferanten <span className="text-slate-500 font-normal">({selectedLieferantIds.size} ausgewählt)</span>
                            </h3>
                        </div>
                        <div className="relative mb-2">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                type="text"
                                value={lieferantenSuche}
                                onChange={e => setLieferantenSuche(e.target.value)}
                                placeholder="Nach Name, Ort oder PLZ suchen…"
                                className="w-full h-10 pl-9 pr-3 rounded-md border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            />
                        </div>
                        <LieferantenListe
                            lieferanten={gefilterteLieferanten}
                            selectedIds={selectedLieferantIds}
                            onToggle={toggleLieferant}
                            loading={lieferantenLoading}
                        />
                    </section>

                    {/* Rueckmeldefrist + Notiz */}
                    <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                Rückmeldefrist <span className="text-rose-600">*</span>
                            </label>
                            <DatePicker
                                value={antwortFrist}
                                onChange={setAntwortFrist}
                                placeholder="Bis wann antworten?"
                            />
                            <p className="text-xs text-slate-500 mt-1">
                                Erscheint im PDF und in der E-Mail.
                            </p>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="pa-notiz">
                                Notiz <span className="text-slate-400 font-normal">(optional)</span>
                            </label>
                            <textarea
                                id="pa-notiz"
                                value={notiz}
                                onChange={e => setNotiz(e.target.value)}
                                rows={3}
                                placeholder="Hinweis für die Lieferanten (erscheint im PDF und der E-Mail)"
                                className="w-full px-3 py-2 rounded-md border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 resize-y"
                            />
                        </div>
                    </section>
                </div>

                {/* Footer */}
                <div className="px-6 py-4 border-t border-slate-200 bg-white shrink-0 flex items-center justify-between gap-3">
                    <p className="text-xs text-slate-500">
                        Pro Lieferant wird automatisch eine E-Mail mit eigenem PDF und Rückmelde-Code versendet.
                    </p>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={submitting}>
                            Abbrechen
                        </Button>
                        <Button
                            onClick={handleSubmit}
                            disabled={!canSubmit}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {submitting ? (
                                <Loader2 className="w-4 h-4 mr-1 animate-spin" />
                            ) : (
                                <Send className="w-4 h-4 mr-1" />
                            )}
                            {submitting ? 'Wird versendet…' : 'Anfrage versenden'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ---------- Sub-Komponenten ----------

function PositionenListe({
    positionen,
    selectedIds,
    onToggle,
    loading,
    error,
}: {
    positionen: BedarfPosition[];
    selectedIds: Set<number>;
    onToggle: (id: number) => void;
    loading: boolean;
    error: string | null;
}) {
    if (loading) {
        return (
            <div className="flex items-center justify-center py-8 text-slate-500 text-sm border border-slate-200 rounded-lg bg-slate-50">
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Positionen werden geladen…
            </div>
        );
    }
    if (error) {
        return (
            <div className="py-6 px-4 text-sm text-rose-700 border border-rose-200 rounded-lg bg-rose-50">
                {error}
            </div>
        );
    }
    if (positionen.length === 0) {
        return (
            <div className="py-8 text-center text-sm text-slate-500 border border-dashed border-slate-200 rounded-lg bg-slate-50">
                Keine offenen Bedarfspositionen gefunden.
            </div>
        );
    }

    return (
        <div className="border border-slate-200 rounded-lg overflow-hidden">
            <div className="max-h-64 overflow-y-auto divide-y divide-slate-100">
                {positionen.map(p => {
                    const checked = selectedIds.has(p.id);
                    return (
                        <label
                            key={p.id}
                            className={cn(
                                'flex items-start gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50 transition-colors',
                                checked && 'bg-rose-50'
                            )}
                        >
                            <input
                                type="checkbox"
                                checked={checked}
                                onChange={() => onToggle(p.id)}
                                className="mt-1 w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                aria-label={`Position ${p.produktname ?? p.id} auswählen`}
                            />
                            <div className="flex-1 min-w-0">
                                <div className="flex items-baseline gap-2">
                                    <span className="text-sm font-medium text-slate-900 truncate">
                                        {p.produktname || p.externeArtikelnummer || `Position #${p.id}`}
                                    </span>
                                    {p.werkstoffName && (
                                        <span className="text-xs text-slate-500 truncate">· {p.werkstoffName}</span>
                                    )}
                                </div>
                                <div className="flex items-center gap-3 text-xs text-slate-500 mt-0.5">
                                    {p.menge != null && (
                                        <span>{formatMenge(p.menge)} {p.einheit ?? ''}</span>
                                    )}
                                    {p.projektName && <span>· {p.projektName}</span>}
                                    {p.lieferantName && <span>· bisher: {p.lieferantName}</span>}
                                </div>
                            </div>
                        </label>
                    );
                })}
            </div>
        </div>
    );
}

function LieferantenListe({
    lieferanten,
    selectedIds,
    onToggle,
    loading,
}: {
    lieferanten: LieferantListenEintrag[];
    selectedIds: Set<number>;
    onToggle: (id: number) => void;
    loading: boolean;
}) {
    if (loading && lieferanten.length === 0) {
        return (
            <div className="flex items-center justify-center py-8 text-slate-500 text-sm border border-slate-200 rounded-lg bg-slate-50">
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Lieferanten werden geladen…
            </div>
        );
    }
    if (lieferanten.length === 0) {
        return (
            <div className="py-8 text-center text-sm text-slate-500 border border-dashed border-slate-200 rounded-lg bg-slate-50">
                Keine Lieferanten gefunden.
            </div>
        );
    }

    return (
        <div className="border border-slate-200 rounded-lg overflow-hidden">
            <div className="max-h-64 overflow-y-auto divide-y divide-slate-100">
                {lieferanten.map(l => {
                    const checked = selectedIds.has(l.id);
                    return (
                        <label
                            key={l.id}
                            className={cn(
                                'flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50 transition-colors',
                                checked && 'bg-rose-50'
                            )}
                        >
                            <input
                                type="checkbox"
                                checked={checked}
                                onChange={() => onToggle(l.id)}
                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                aria-label={`Lieferant ${l.lieferantenname} auswählen`}
                            />
                            <Truck className={cn('w-4 h-4', checked ? 'text-rose-600' : 'text-slate-400')} />
                            <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium text-slate-900 truncate">
                                    {l.lieferantenname}
                                </p>
                                {(l.plz || l.ort) && (
                                    <p className="text-xs text-slate-500 truncate">
                                        {[l.plz, l.ort].filter(Boolean).join(' ')}
                                    </p>
                                )}
                            </div>
                        </label>
                    );
                })}
            </div>
        </div>
    );
}

// ---------- Helpers ----------

function defaultFristIso(inDays: number): string {
    const d = new Date();
    d.setDate(d.getDate() + inDays);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function firstProjektName(positionen?: BedarfPosition[]): string | null {
    if (!positionen) return null;
    for (const p of positionen) {
        if (p.projektName && p.projektName.trim().length > 0) return p.projektName;
    }
    return null;
}

function firstProjektId(positionen: BedarfPosition[], selected: Set<number>): number | null {
    for (const p of positionen) {
        if (selected.has(p.id) && p.projektId != null) return p.projektId;
    }
    return null;
}

function formatMenge(menge: number): string {
    return menge.toLocaleString('de-DE', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 3,
    });
}
