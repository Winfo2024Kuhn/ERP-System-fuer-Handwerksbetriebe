import { useCallback, useEffect, useState } from 'react';
import { Download, FileQuestion, Loader2, Plus, Scale, Trash2, Truck } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { AngeboteEinholenModal } from '../components/AngeboteEinholenModal';
import { PreisanfrageVergleichModal } from '../components/PreisanfrageVergleichModal';
import { PreiseEintragenModal } from '../components/PreiseEintragenModal';
import { cn } from '../lib/utils';

type PreisanfrageStatus =
    | 'OFFEN'
    | 'TEILWEISE_BEANTWORTET'
    | 'VOLLSTAENDIG'
    | 'VERGEBEN'
    | 'ABGEBROCHEN';

type LieferantStatus =
    | 'VORBEREITET'
    | 'VERSENDET'
    | 'BEANTWORTET'
    | 'ABGELEHNT';

interface LieferantListeEintrag {
    id: number;
    lieferantId: number;
    lieferantenname: string;
    token: string;
    versendetAn?: string | null;
    versendetAm?: string | null;
    antwortErhaltenAm?: string | null;
    status: LieferantStatus;
}

interface PreisanfrageListeEintrag {
    id: number;
    nummer: string;
    bauvorhaben?: string | null;
    projektId?: number | null;
    erstelltAm?: string | null;
    antwortFrist?: string | null;
    status: PreisanfrageStatus;
    notiz?: string | null;
    vergebenAnPreisanfrageLieferantId?: number | null;
    lieferanten: LieferantListeEintrag[];
    positionen: unknown[];
}

interface StatusFilter {
    key: 'ALLE' | PreisanfrageStatus;
    label: string;
}

const FILTERS: StatusFilter[] = [
    { key: 'ALLE', label: 'Alle' },
    { key: 'OFFEN', label: 'Offen' },
    { key: 'TEILWEISE_BEANTWORTET', label: 'Teilweise' },
    { key: 'VOLLSTAENDIG', label: 'Vollständig' },
    { key: 'VERGEBEN', label: 'Vergeben' },
    { key: 'ABGEBROCHEN', label: 'Abgebrochen' },
];

export default function PreisanfragenPage() {
    const toast = useToast();
    const confirmDialog = useConfirm();

    const [items, setItems] = useState<PreisanfrageListeEintrag[]>([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState<StatusFilter['key']>('ALLE');
    const [modalOpen, setModalOpen] = useState(false);
    const [vergleichFuerId, setVergleichFuerId] = useState<number | null>(null);
    const [vergleichHintNummer, setVergleichHintNummer] = useState<string>('');
    const [preiseModal, setPreiseModal] = useState<{ preisanfrageId: number; palId: number } | null>(null);

    const load = useCallback(async (statusKey: StatusFilter['key']) => {
        setLoading(true);
        try {
            const url = statusKey === 'ALLE'
                ? '/api/preisanfragen'
                : `/api/preisanfragen?status=${encodeURIComponent(statusKey)}`;
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: unknown = await res.json();
            const list = Array.isArray(data) ? (data as PreisanfrageListeEintrag[]) : [];
            list.sort((a, b) => (b.erstelltAm ?? '').localeCompare(a.erstelltAm ?? ''));
            setItems(list);
        } catch {
            toast.error('Preisanfragen konnten nicht geladen werden.');
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        load(filter);
    }, [filter, load]);

    const handleAbbrechen = useCallback(async (id: number, nummer: string) => {
        const ok = await confirmDialog({
            title: 'Preisanfrage abbrechen?',
            message: `Soll die Preisanfrage ${nummer} abgebrochen werden? Bereits versendete E-Mails bleiben bestehen.`,
            confirmLabel: 'Abbrechen',
            cancelLabel: 'Zurück',
            variant: 'danger',
        });
        if (!ok) return;
        try {
            const res = await fetch(`/api/preisanfragen/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                const reason = res.headers.get('X-Error-Reason') ?? `HTTP ${res.status}`;
                throw new Error(reason);
            }
            toast.success(`Preisanfrage ${nummer} abgebrochen.`);
            load(filter);
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Unbekannter Fehler';
            toast.error(`Abbrechen fehlgeschlagen: ${msg}`);
        }
    }, [confirmDialog, filter, load, toast]);

    return (
        <PageLayout
            ribbonCategory="Einkauf"
            title="Preisanfragen"
            subtitle="Angebote von mehreren Lieferanten einholen und vergleichen"
            actions={
                <Button
                    onClick={() => setModalOpen(true)}
                    className="bg-rose-600 text-white hover:bg-rose-700"
                >
                    <Plus className="w-4 h-4" />
                    Neue Preisanfrage
                </Button>
            }
        >
            {/* Status-Filter */}
            <div className="flex flex-wrap gap-1 border-b border-slate-200 pb-0 mb-6">
                {FILTERS.map(f => (
                    <button
                        key={f.key}
                        type="button"
                        onClick={() => setFilter(f.key)}
                        className={cn(
                            'px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px',
                            filter === f.key
                                ? 'text-rose-700 border-rose-600'
                                : 'text-slate-500 border-transparent hover:text-slate-800 hover:border-slate-300'
                        )}
                    >
                        {f.label}
                    </button>
                ))}
            </div>

            {/* Liste */}
            {loading ? (
                <div className="flex items-center justify-center py-16 text-slate-500 text-sm">
                    <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                    Preisanfragen werden geladen…
                </div>
            ) : items.length === 0 ? (
                <EmptyState onCreate={() => setModalOpen(true)} />
            ) : (
                <div className="space-y-4">
                    {items.map(pa => (
                        <PreisanfrageCard
                            key={pa.id}
                            item={pa}
                            onAbbrechen={() => handleAbbrechen(pa.id, pa.nummer)}
                            onVergleichOeffnen={() => {
                                setVergleichHintNummer(pa.nummer);
                                setVergleichFuerId(pa.id);
                            }}
                        />
                    ))}
                </div>
            )}

            <AngeboteEinholenModal
                open={modalOpen}
                onClose={() => setModalOpen(false)}
                onVersendet={() => load(filter)}
            />

            <PreisanfrageVergleichModal
                open={vergleichFuerId != null}
                onClose={() => setVergleichFuerId(null)}
                preisanfrageId={vergleichFuerId}
                nummerHint={vergleichHintNummer}
                onVergeben={() => load(filter)}
                onPreiseEintragen={(palId) => {
                    if (vergleichFuerId != null) {
                        setPreiseModal({ preisanfrageId: vergleichFuerId, palId });
                    }
                }}
            />

            <PreiseEintragenModal
                open={preiseModal != null}
                onClose={() => setPreiseModal(null)}
                preisanfrageId={preiseModal?.preisanfrageId ?? null}
                preisanfrageLieferantId={preiseModal?.palId ?? null}
                onSaved={() => load(filter)}
            />
        </PageLayout>
    );
}

// ---------- Sub-Komponenten ----------

function EmptyState({ onCreate }: { onCreate: () => void }) {
    return (
        <div className="py-16 px-6 text-center border border-dashed border-slate-200 rounded-2xl bg-slate-50">
            <FileQuestion className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <h3 className="text-lg font-semibold text-slate-800">Noch keine Preisanfragen</h3>
            <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                Sende Angebotsanfragen an mehrere Lieferanten gleichzeitig und vergleiche die Preise in einer
                übersichtlichen Matrix.
            </p>
            <Button
                onClick={onCreate}
                className="mt-5 bg-rose-600 text-white hover:bg-rose-700"
            >
                <Plus className="w-4 h-4" />
                Neue Preisanfrage
            </Button>
        </div>
    );
}

function PreisanfrageCard({
    item,
    onAbbrechen,
    onVergleichOeffnen,
}: {
    item: PreisanfrageListeEintrag;
    onAbbrechen: () => void;
    onVergleichOeffnen: () => void;
}) {
    const beantwortet = item.lieferanten.filter(l => l.status === 'BEANTWORTET').length;
    const istAbgeschlossen = item.status === 'VERGEBEN' || item.status === 'ABGEBROCHEN';

    return (
        <Card className="overflow-hidden">
            {/* Header */}
            <div className="px-5 py-4 bg-slate-50 border-b border-slate-200 flex flex-col md:flex-row md:items-center md:justify-between gap-3">
                <div className="min-w-0">
                    <div className="flex flex-wrap items-baseline gap-3">
                        <h3 className="text-base font-semibold text-slate-900 font-mono">
                            {item.nummer}
                        </h3>
                        {item.bauvorhaben && (
                            <span className="text-sm text-slate-600 truncate">
                                {item.bauvorhaben}
                            </span>
                        )}
                        <StatusPill status={item.status} />
                    </div>
                    <div className="flex items-center gap-4 text-xs text-slate-500 mt-1">
                        <span>
                            Rückmeldefrist: <strong className="text-slate-700">{formatDate(item.antwortFrist)}</strong>
                        </span>
                        <span>
                            Lieferanten: <strong className="text-slate-700">{beantwortet}/{item.lieferanten.length} beantwortet</strong>
                        </span>
                    </div>
                </div>

                <div className="flex items-center gap-2 flex-wrap">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={onVergleichOeffnen}
                        title="Preise nebeneinander vergleichen — günstigster markiert"
                    >
                        <Scale className="w-4 h-4" />
                        Vergleich öffnen
                    </Button>
                    {!istAbgeschlossen && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={onAbbrechen}
                            title="Preisanfrage abbrechen (Soft-Delete)"
                        >
                            <Trash2 className="w-4 h-4" />
                            Abbrechen
                        </Button>
                    )}
                </div>
            </div>

            {/* Lieferanten-Liste */}
            {item.lieferanten.length === 0 ? (
                <p className="px-5 py-4 text-sm text-slate-500">Keine Lieferanten zugeordnet.</p>
            ) : (
                <ul className="divide-y divide-slate-100">
                    {item.lieferanten.map(l => (
                        <li key={l.id} className="flex flex-col md:flex-row md:items-center md:justify-between gap-2 px-5 py-3">
                            <div className="flex items-center gap-3 min-w-0">
                                <Truck className="w-4 h-4 text-slate-400 shrink-0" />
                                <div className="min-w-0">
                                    <p className="text-sm font-medium text-slate-900 truncate">
                                        {l.lieferantenname}
                                    </p>
                                    <p className="text-xs text-slate-500 truncate">
                                        {l.versendetAn ?? '—'}
                                        {l.versendetAm ? ` · versendet ${formatDateTime(l.versendetAm)}` : ''}
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                <LieferantStatusPill status={l.status} />
                                <a
                                    href={`/api/preisanfragen/lieferant/${l.id}/pdf`}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="inline-flex items-center gap-1 text-xs font-medium text-rose-700 hover:text-rose-800"
                                    title="Versendetes PDF ansehen"
                                >
                                    <Download className="w-3.5 h-3.5" />
                                    PDF
                                </a>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </Card>
    );
}

function StatusPill({ status }: { status: PreisanfrageStatus }) {
    const map: Record<PreisanfrageStatus, { label: string; className: string }> = {
        OFFEN: { label: 'Offen', className: 'bg-slate-100 text-slate-700' },
        TEILWEISE_BEANTWORTET: { label: 'Teilweise beantwortet', className: 'bg-amber-100 text-amber-800' },
        VOLLSTAENDIG: { label: 'Vollständig', className: 'bg-emerald-100 text-emerald-800' },
        VERGEBEN: { label: 'Vergeben', className: 'bg-rose-100 text-rose-700' },
        ABGEBROCHEN: { label: 'Abgebrochen', className: 'bg-slate-200 text-slate-500' },
    };
    const s = map[status] ?? { label: status, className: 'bg-slate-100 text-slate-700' };
    return (
        <span className={cn('text-xs font-medium px-2 py-0.5 rounded-full', s.className)}>
            {s.label}
        </span>
    );
}

function LieferantStatusPill({ status }: { status: LieferantStatus }) {
    const map: Record<LieferantStatus, { label: string; className: string }> = {
        VORBEREITET: { label: 'Vorbereitet', className: 'bg-slate-100 text-slate-600' },
        VERSENDET: { label: 'Versendet', className: 'bg-sky-100 text-sky-800' },
        BEANTWORTET: { label: 'Beantwortet', className: 'bg-emerald-100 text-emerald-800' },
        ABGELEHNT: { label: 'Abgelehnt', className: 'bg-rose-100 text-rose-700' },
    };
    const s = map[status] ?? { label: status, className: 'bg-slate-100 text-slate-700' };
    return (
        <span className={cn('text-xs font-medium px-2 py-0.5 rounded-full', s.className)}>
            {s.label}
        </span>
    );
}

function formatDate(iso?: string | null): string {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
        return iso;
    }
}

function formatDateTime(iso?: string | null): string {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
        return iso;
    }
}
