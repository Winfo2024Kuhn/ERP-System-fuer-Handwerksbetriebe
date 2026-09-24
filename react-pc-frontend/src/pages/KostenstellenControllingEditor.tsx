import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Wallet, Plus, Edit2, Trash2, Save, X, RefreshCw, Search, Filter,
    ArrowUpRight, ArrowDownRight, Minus,
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { PageLayout } from '../components/layout/PageLayout';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../components/ui/dialog';
import { KostenstelleDetailView } from '../components/firma/KostenstelleDetailView';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { cn } from '../lib/utils';

interface KostenstelleAuswertung {
    id: number;
    bezeichnung: string;
    typ: 'LAGER' | 'GEMEINKOSTEN' | 'PROJEKT' | 'SONSTIG';
    beschreibung: string;
    istFixkosten: boolean;
    istInvestition: boolean;
    summeDiesesJahr: number;
    summeVorjahr: number;
    anzahlDiesesJahr: number;
}

interface KostenstelleForm {
    id?: number;
    bezeichnung?: string;
    typ?: 'LAGER' | 'GEMEINKOSTEN' | 'PROJEKT' | 'SONSTIG';
    beschreibung?: string;
    aktiv?: boolean;
    sortierung?: number;
}

const KOSTENSTELLEN_TYP_OPTIONS = [
    { value: 'LAGER', label: 'Lager (Investitionen)' },
    { value: 'GEMEINKOSTEN', label: 'Gemeinkosten (Fixkosten)' },
    { value: 'PROJEKT', label: 'Projekt' },
    { value: 'SONSTIG', label: 'Sonstige' },
];

const MONATE = [
    { value: '', label: 'Ganzes Jahr' },
    { value: '1', label: 'Januar' },
    { value: '2', label: 'Februar' },
    { value: '3', label: 'März' },
    { value: '4', label: 'April' },
    { value: '5', label: 'Mai' },
    { value: '6', label: 'Juni' },
    { value: '7', label: 'Juli' },
    { value: '8', label: 'August' },
    { value: '9', label: 'September' },
    { value: '10', label: 'Oktober' },
    { value: '11', label: 'November' },
    { value: '12', label: 'Dezember' },
];

const formatCurrency = (val: number) =>
    new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

const getTypLabel = (typ: string) =>
    KOSTENSTELLEN_TYP_OPTIONS.find(o => o.value === typ)?.label || typ;

const getTypColor = (typ: string) => {
    switch (typ) {
        case 'GEMEINKOSTEN': return 'bg-rose-100 text-rose-700 border-rose-200';
        case 'LAGER': return 'bg-rose-50 text-rose-600 border-rose-100';
        case 'PROJEKT': return 'bg-slate-100 text-slate-700 border-slate-300';
        default: return 'bg-slate-100 text-slate-600 border-slate-200';
    }
};

export default function KostenstellenControllingEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();

    const currentYear = new Date().getFullYear();
    const [jahr, setJahr] = useState<number>(currentYear);
    const [monat, setMonat] = useState<string>('');
    const [suche, setSuche] = useState<string>('');

    const [kostenstellen, setKostenstellen] = useState<KostenstelleAuswertung[]>([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    const [selected, setSelected] = useState<KostenstelleAuswertung | null>(null);
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState<KostenstelleForm | null>(null);

    const jahrOptionen = useMemo(() => {
        const jahre: number[] = [];
        for (let y = currentYear + 1; y >= currentYear - 6; y--) jahre.push(y);
        return jahre.map(y => ({ value: String(y), label: String(y) }));
    }, [currentYear]);

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams({ jahr: String(jahr) });
            if (monat) params.append('monat', monat);
            const res = await fetch(`/api/bestellungen-uebersicht/kostenstellen/auswertung?${params.toString()}`);
            if (res.ok) {
                setKostenstellen(await res.json());
            } else {
                setKostenstellen([]);
            }
        } catch (e) {
            console.error('Fehler beim Laden der Kostenstellen', e);
        } finally {
            setLoading(false);
        }
    }, [jahr, monat]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const gefiltert = useMemo(() => {
        const begriff = suche.trim().toLowerCase();
        if (!begriff) return kostenstellen;
        return kostenstellen.filter(k =>
            k.bezeichnung.toLowerCase().includes(begriff)
            || (k.beschreibung || '').toLowerCase().includes(begriff)
            || getTypLabel(k.typ).toLowerCase().includes(begriff));
    }, [kostenstellen, suche]);

    const gesamtSumme = useMemo(
        () => gefiltert.reduce((sum, k) => sum + (k.summeDiesesJahr || 0), 0),
        [gefiltert]
    );
    const gesamtVorjahr = useMemo(
        () => gefiltert.reduce((sum, k) => sum + (k.summeVorjahr || 0), 0),
        [gefiltert]
    );

    const saveKostenstelle = async () => {
        if (!editing?.bezeichnung) return;
        setSaving(true);
        try {
            const typ = editing.typ || 'GEMEINKOSTEN';
            const payload = {
                ...editing,
                typ,
                istFixkosten: typ === 'GEMEINKOSTEN',
                istInvestition: typ === 'LAGER',
            };
            const method = payload.id ? 'PUT' : 'POST';
            const url = payload.id
                ? `/api/firma/kostenstellen/${payload.id}`
                : '/api/firma/kostenstellen';
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                setShowModal(false);
                setEditing(null);
                await loadData();
            } else {
                toast.error('Fehler beim Speichern');
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
            toast.error('Netzwerkfehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    const deleteKostenstelle = async (id: number) => {
        if (!await confirmDialog({
            title: 'Kostenstelle löschen',
            message: 'Kostenstelle wirklich löschen? Bestehende Zuordnungen bleiben erhalten, aber die Kostenstelle verschwindet aus der Auswahl.',
            variant: 'danger',
            confirmLabel: 'Löschen',
        })) return;
        try {
            const res = await fetch(`/api/firma/kostenstellen/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadData();
            } else {
                toast.error('Löschen fehlgeschlagen');
            }
        } catch (e) {
            console.error('Fehler beim Löschen', e);
            toast.error('Netzwerkfehler beim Löschen');
        }
    };

    const initKostenstellen = async () => {
        try {
            const res = await fetch('/api/firma/kostenstellen/init', { method: 'POST' });
            if (res.ok) {
                await loadData();
            }
        } catch (e) {
            console.error('Fehler beim Initialisieren', e);
        }
    };

    const monatsLabel = MONATE.find(m => m.value === monat)?.label ?? 'Ganzes Jahr';

    return (
        <PageLayout
            ribbonCategory="Finanzen & Controlling"
            title="KOSTENSTELLEN"
            subtitle="Kosten aus Lieferantenrechnungen nach Kostenstelle – mit Vorjahresvergleich"
            actions={
                <div className="flex gap-2">
                    {kostenstellen.length === 0 && !loading && (
                        <Button variant="outline" onClick={initKostenstellen} className="border-rose-300 text-rose-700 hover:bg-rose-50">
                            Standard anlegen
                        </Button>
                    )}
                    <Button
                        onClick={() => {
                            setEditing({ typ: 'GEMEINKOSTEN', aktiv: true, sortierung: kostenstellen.length + 1 });
                            setShowModal(true);
                        }}
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                    >
                        <Plus className="w-4 h-4 mr-2" />
                        Neue Kostenstelle
                    </Button>
                </div>
            }
        >
            {selected ? (
                <KostenstelleDetailView
                    kostenstelle={{
                        id: selected.id,
                        bezeichnung: selected.bezeichnung,
                        typ: selected.typ,
                        beschreibung: selected.beschreibung,
                    }}
                    onBack={() => setSelected(null)}
                />
            ) : (
                <div className="flex flex-col gap-6">
                    {/* Filterleiste */}
                    <Card className="p-4 border-0 shadow-sm rounded-xl">
                        <div className="flex flex-wrap items-end gap-4">
                            <div className="w-36">
                                <Label className="text-sm font-medium text-slate-600">Jahr</Label>
                                <Select
                                    value={String(jahr)}
                                    onChange={(v) => setJahr(parseInt(v))}
                                    options={jahrOptionen}
                                />
                            </div>
                            <div className="w-44">
                                <Label className="text-sm font-medium text-slate-600">Monat</Label>
                                <Select
                                    value={monat}
                                    onChange={setMonat}
                                    options={MONATE}
                                    placeholder="Ganzes Jahr"
                                />
                            </div>
                            <div className="flex-1 min-w-[200px]">
                                <Label className="text-sm font-medium text-slate-600">Suche</Label>
                                <div className="relative">
                                    <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                                    <Input
                                        value={suche}
                                        onChange={e => setSuche(e.target.value)}
                                        placeholder="Kostenstelle suchen..."
                                        className="pl-9"
                                    />
                                </div>
                            </div>
                            <Button onClick={loadData} variant="outline" size="sm" disabled={loading} className="border-rose-300 text-rose-700 hover:bg-rose-50">
                                {loading ? <RefreshCw className="w-4 h-4 mr-1 animate-spin" /> : <Filter className="w-4 h-4 mr-1" />}
                                Aktualisieren
                            </Button>
                        </div>
                    </Card>

                    {/* Summen-Karten */}
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Card className="p-4 border-0 shadow-sm rounded-xl">
                            <p className="text-xs text-slate-500 font-semibold uppercase mb-1">Kosten {jahr}{monat ? ` · ${monatsLabel}` : ''}</p>
                            <p className="text-lg font-bold text-slate-900">{formatCurrency(gesamtSumme)}</p>
                        </Card>
                        <Card className="p-4 border-0 shadow-sm rounded-xl">
                            <p className="text-xs text-slate-500 font-semibold uppercase mb-1">Vorjahr {jahr - 1}</p>
                            <p className="text-lg font-bold text-slate-500">{formatCurrency(gesamtVorjahr)}</p>
                        </Card>
                        <Card className="p-4 border-0 shadow-sm rounded-xl">
                            <p className="text-xs text-slate-500 font-semibold uppercase mb-1">Kostenstellen</p>
                            <p className="text-lg font-bold text-slate-900">{gefiltert.length}</p>
                        </Card>
                    </div>

                    {/* Karten-Liste */}
                    {loading ? (
                        <div className="flex items-center justify-center py-16">
                            <RefreshCw className="w-8 h-8 animate-spin text-rose-600" />
                        </div>
                    ) : gefiltert.length === 0 ? (
                        <div className="py-16 flex flex-col items-center justify-center text-slate-400 gap-2 border-2 border-dashed border-slate-100 rounded-xl">
                            <Wallet className="w-8 h-8 text-slate-300" />
                            <p className="font-medium">Keine Kostenstellen gefunden</p>
                            {suche && <p className="text-xs">Suchbegriff „{suche}" ergab keine Treffer.</p>}
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {gefiltert.map(ks => {
                                const diff = (ks.summeDiesesJahr || 0) - (ks.summeVorjahr || 0);
                                const TrendIcon = diff > 0 ? ArrowUpRight : diff < 0 ? ArrowDownRight : Minus;
                                // Steigende Kosten = Aufmerksamkeit (rose), Rückgang/gleich = neutral (slate)
                                const trendColor = diff > 0 ? 'text-rose-600' : 'text-slate-500';
                                return (
                                    <Card
                                        key={ks.id}
                                        className="p-4 cursor-pointer hover:shadow-md transition-shadow group"
                                        onClick={() => setSelected(ks)}
                                    >
                                        <div className="flex justify-between items-start gap-2">
                                            <div className="min-w-0">
                                                <h4 className="font-semibold text-slate-900 group-hover:text-rose-600 transition-colors truncate">{ks.bezeichnung}</h4>
                                                <span className={cn('inline-block px-2 py-0.5 text-xs rounded border mt-1', getTypColor(ks.typ))}>
                                                    {getTypLabel(ks.typ)}
                                                </span>
                                            </div>
                                            <div className="flex gap-1 shrink-0">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={(e) => { e.stopPropagation(); setEditing(ks); setShowModal(true); }}
                                                    aria-label="Kostenstelle bearbeiten"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={(e) => { e.stopPropagation(); deleteKostenstelle(ks.id); }}
                                                    className="text-rose-600 hover:text-rose-700"
                                                    aria-label="Kostenstelle löschen"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>

                                        {ks.beschreibung && (
                                            <p className="text-sm text-slate-500 mt-2 line-clamp-2">{ks.beschreibung}</p>
                                        )}

                                        <div className="mt-3 pt-3 border-t border-slate-100 flex items-end justify-between">
                                            <div>
                                                <p className="text-xs text-slate-400">Kosten {jahr}</p>
                                                <p className="text-lg font-bold text-slate-900">{formatCurrency(ks.summeDiesesJahr)}</p>
                                                <p className="text-xs text-slate-400 mt-0.5">
                                                    {ks.anzahlDiesesJahr} {ks.anzahlDiesesJahr === 1 ? 'Buchung' : 'Buchungen'}
                                                </p>
                                            </div>
                                            <div className={cn('flex items-center gap-1 text-sm font-medium', trendColor)} title={`Vorjahr ${jahr - 1}: ${formatCurrency(ks.summeVorjahr)}`}>
                                                <TrendIcon className="w-4 h-4" />
                                                {formatCurrency(Math.abs(diff))}
                                            </div>
                                        </div>
                                    </Card>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}

            {/* Kostenstelle Modal */}
            <Dialog open={showModal} onOpenChange={setShowModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{editing?.id ? 'Kostenstelle bearbeiten' : 'Neue Kostenstelle'}</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <Label>Bezeichnung *</Label>
                            <Input
                                value={editing?.bezeichnung || ''}
                                onChange={e => setEditing({ ...editing, bezeichnung: e.target.value })}
                            />
                        </div>
                        <div>
                            <Label>Typ *</Label>
                            <Select
                                value={editing?.typ || 'GEMEINKOSTEN'}
                                onChange={value => setEditing({ ...editing, typ: value as KostenstelleForm['typ'] })}
                                options={KOSTENSTELLEN_TYP_OPTIONS}
                            />
                        </div>
                        <div>
                            <Label>Beschreibung</Label>
                            <Input
                                value={editing?.beschreibung || ''}
                                onChange={e => setEditing({ ...editing, beschreibung: e.target.value })}
                            />
                        </div>
                        <p className="text-xs text-slate-500">
                            {(editing?.typ || 'GEMEINKOSTEN') === 'GEMEINKOSTEN'
                                ? 'Wird als Fixkosten für die Gemeinkostenberechnung verwendet.'
                                : (editing?.typ || '') === 'LAGER'
                                    ? 'Wird als Investition gewertet (keine echten Kosten).'
                                    : (editing?.typ || '') === 'PROJEKT'
                                        ? 'Kosten werden dem jeweiligen Projekt zugeordnet.'
                                        : 'Sonstige Kostenzuordnung.'}
                        </p>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowModal(false)}>
                            <X className="w-4 h-4 mr-2" />
                            Abbrechen
                        </Button>
                        <Button
                            onClick={saveKostenstelle}
                            disabled={saving || !editing?.bezeichnung}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {saving ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                            Speichern
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
}
