import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Briefcase, Loader2, Plus, Scale, Send, Truck, X } from 'lucide-react';
import { Button } from './ui/button';
import { DatePicker } from './ui/datepicker';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { LieferantSearchModal, type LieferantSuchErgebnis } from './LieferantSearchModal';
import { ProjektSearchModal } from './ProjektSearchModal';

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
 *
 * Lieferanten werden ueber das zentrale `LieferantSearchModal` hinzugefuegt,
 * Projekte ueber das `ProjektSearchModal` — konsistent zum Rest der App.
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

    const [selectedPositionIds, setSelectedPositionIds] = useState<Set<number>>(new Set());
    const [gewaehlteLieferanten, setGewaehlteLieferanten] = useState<LieferantSuchErgebnis[]>([]);
    /**
     * Gewaehlte Empfaenger-Adresse je Lieferant. Wird beim Hinzufuegen mit
     * der ersten hinterlegten `kundenEmails` vorbelegt; der Nutzer kann via
     * Dropdown eine andere hinterlegte Adresse waehlen. Nie Free-Text — die
     * Auswahl wird backend-seitig gegen `kundenEmails` validiert.
     */
    const [empfaengerMap, setEmpfaengerMap] = useState<Map<number, string>>(new Map());
    const [antwortFrist, setAntwortFrist] = useState('');
    const [notiz, setNotiz] = useState('');
    const [bauvorhaben, setBauvorhaben] = useState('');
    const [projektId, setProjektId] = useState<number | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const [lieferantSuchOpen, setLieferantSuchOpen] = useState(false);
    const [projektSuchOpen, setProjektSuchOpen] = useState(false);

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
        setGewaehlteLieferanten([]);
        setEmpfaengerMap(new Map());
        setLieferantSuchOpen(false);
        setProjektSuchOpen(false);

        const vorschlag = vorschlagBauvorhaben
            ?? firstProjektName(vorausgewaehltePositionen)
            ?? '';
        setBauvorhaben(vorschlag);
        setProjektId(firstProjektIdVon(vorausgewaehltePositionen));

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

    const togglePosition = useCallback((id: number) => {
        setSelectedPositionIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }, []);

    const lieferantHinzufuegen = useCallback((lieferant: LieferantSuchErgebnis) => {
        setGewaehlteLieferanten(prev => {
            if (prev.some(l => l.id === lieferant.id)) return prev;
            return [...prev, lieferant];
        });
        setEmpfaengerMap(prev => {
            if (prev.has(lieferant.id)) return prev;
            const ersteMail = (lieferant.kundenEmails ?? []).find(m => m && m.trim().length > 0);
            if (!ersteMail) return prev;
            const next = new Map(prev);
            next.set(lieferant.id, ersteMail);
            return next;
        });
    }, []);

    const lieferantEntfernen = useCallback((lieferantId: number) => {
        setGewaehlteLieferanten(prev => prev.filter(l => l.id !== lieferantId));
        setEmpfaengerMap(prev => {
            if (!prev.has(lieferantId)) return prev;
            const next = new Map(prev);
            next.delete(lieferantId);
            return next;
        });
    }, []);

    const setEmpfaengerFuer = useCallback((lieferantId: number, email: string) => {
        setEmpfaengerMap(prev => {
            const next = new Map(prev);
            next.set(lieferantId, email);
            return next;
        });
    }, []);

    const projektUebernehmen = useCallback((p: { id: number; bauvorhaben: string }) => {
        setProjektId(p.id);
        if (p.bauvorhaben && p.bauvorhaben.trim().length > 0) {
            setBauvorhaben(p.bauvorhaben);
        }
    }, []);

    const canSubmit = (
        !submitting
        && selectedPositionIds.size > 0
        && gewaehlteLieferanten.length > 0
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

        const lieferantIds = gewaehlteLieferanten.map(l => l.id);
        const empfaengerProLieferant: Record<number, string> = {};
        gewaehlteLieferanten.forEach(l => {
            const mail = empfaengerMap.get(l.id);
            if (mail && mail.trim().length > 0) {
                empfaengerProLieferant[l.id] = mail;
            }
        });

        const effektiveProjektId = projektId ?? firstProjektIdAusAuswahl(arbeitsPositionen, selectedPositionIds);

        const payload = {
            projektId: effektiveProjektId,
            bauvorhaben: bauvorhaben.trim() || null,
            antwortFrist,
            notiz: notiz.trim() || null,
            lieferantIds,
            empfaengerProLieferant,
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

            toast.success(`Preisanfrage an ${lieferantIds.length} Lieferant${lieferantIds.length === 1 ? '' : 'en'} versendet.`);
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
                    {/* Bauvorhaben / Projekt */}
                    <section>
                        <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="pa-bauvorhaben">
                            Bauvorhaben <span className="text-slate-400 font-normal">(optional)</span>
                        </label>
                        <div className="flex gap-2">
                            <input
                                id="pa-bauvorhaben"
                                type="text"
                                value={bauvorhaben}
                                onChange={e => setBauvorhaben(e.target.value)}
                                placeholder="z. B. Stahlbau Musterstraße"
                                className="flex-1 h-10 px-3 rounded-md border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            />
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setProjektSuchOpen(true)}
                                className="shrink-0"
                            >
                                <Briefcase className="w-4 h-4 mr-1.5" />
                                Projekt wählen
                            </Button>
                        </div>
                        {projektId != null && (
                            <p className="text-xs text-slate-500 mt-1">
                                Verknüpftes Projekt: #{projektId}
                            </p>
                        )}
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
                                Lieferanten <span className="text-slate-500 font-normal">({gewaehlteLieferanten.length} ausgewählt)</span>
                            </h3>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setLieferantSuchOpen(true)}
                            >
                                <Plus className="w-4 h-4 mr-1.5" />
                                Lieferant hinzufügen
                            </Button>
                        </div>
                        <GewaehlteLieferantenListe
                            lieferanten={gewaehlteLieferanten}
                            empfaengerMap={empfaengerMap}
                            onEmpfaengerChange={setEmpfaengerFuer}
                            onEntfernen={lieferantEntfernen}
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

            {/* Sub-Modale */}
            <LieferantSearchModal
                isOpen={lieferantSuchOpen}
                onClose={() => setLieferantSuchOpen(false)}
                onSelect={lieferantHinzufuegen}
            />
            <ProjektSearchModal
                isOpen={projektSuchOpen}
                onClose={() => setProjektSuchOpen(false)}
                onSelect={projektUebernehmen}
                currentProjektId={projektId ?? undefined}
            />
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

function GewaehlteLieferantenListe({
    lieferanten,
    empfaengerMap,
    onEmpfaengerChange,
    onEntfernen,
}: {
    lieferanten: LieferantSuchErgebnis[];
    empfaengerMap: Map<number, string>;
    onEmpfaengerChange: (lieferantId: number, email: string) => void;
    onEntfernen: (lieferantId: number) => void;
}) {
    if (lieferanten.length === 0) {
        return (
            <div className="py-8 text-center text-sm text-slate-500 border border-dashed border-slate-200 rounded-lg bg-slate-50">
                Noch keine Lieferanten ausgewählt — oben auf „Lieferant hinzufügen" klicken.
            </div>
        );
    }

    return (
        <ul className="space-y-2">
            {lieferanten.map(l => {
                const mails = (l.kundenEmails ?? []).filter(m => m && m.trim().length > 0);
                const gewaehlteMail = empfaengerMap.get(l.id) ?? mails[0] ?? '';
                return (
                    <li
                        key={l.id}
                        className="flex items-center gap-3 px-3 py-2 border border-slate-200 rounded-lg bg-white"
                    >
                        <div className="w-9 h-9 rounded-lg bg-rose-100 flex items-center justify-center shrink-0">
                            <Truck className="w-4 h-4 text-rose-600" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-slate-900 truncate">
                                {l.lieferantenname}
                            </p>
                            <div className="mt-1 flex items-center gap-2">
                                <label
                                    htmlFor={`pa-empfaenger-${l.id}`}
                                    className="text-xs text-slate-600 shrink-0"
                                >
                                    E-Mail an:
                                </label>
                                {mails.length === 0 ? (
                                    <span className="text-xs text-rose-700 italic">
                                        Keine E-Mail-Adresse hinterlegt
                                    </span>
                                ) : mails.length === 1 ? (
                                    <span className="text-xs text-slate-700 truncate">{mails[0]}</span>
                                ) : (
                                    <select
                                        id={`pa-empfaenger-${l.id}`}
                                        value={gewaehlteMail}
                                        onChange={e => onEmpfaengerChange(l.id, e.target.value)}
                                        className="h-8 px-2 rounded-md border border-slate-200 bg-white text-xs focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 max-w-xs truncate"
                                        aria-label={`Empfänger-Adresse für ${l.lieferantenname}`}
                                    >
                                        {mails.map(m => (
                                            <option key={m} value={m}>{m}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={() => onEntfernen(l.id)}
                            className="p-1.5 rounded-full text-slate-400 hover:text-rose-700 hover:bg-rose-50 transition-colors shrink-0"
                            aria-label={`${l.lieferantenname} entfernen`}
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </li>
                );
            })}
        </ul>
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

function firstProjektIdVon(positionen?: BedarfPosition[]): number | null {
    if (!positionen) return null;
    for (const p of positionen) {
        if (p.projektId != null) return p.projektId;
    }
    return null;
}

function firstProjektIdAusAuswahl(positionen: BedarfPosition[], selected: Set<number>): number | null {
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
