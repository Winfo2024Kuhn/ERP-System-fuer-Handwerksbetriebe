import { useEffect, useState, type FormEvent } from 'react';
import { type Kostenposition, type Kostenstelle } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Input } from '../ui/input';
import { DecimalInput } from '../ui/decimal-input';
import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import { Zap } from 'lucide-react';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { DatePicker } from '../ui/datepicker';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';

interface KostenpositionenViewProps {
    mietobjektId: number;
}

const currentYear = new Date().getFullYear();

export function KostenpositionenView({ mietobjektId }: KostenpositionenViewProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [centers, setCenters] = useState<Kostenstelle[]>([]);
    const [positions, setPositions] = useState<(Kostenposition & { centerName?: string })[]>([]);
    const [loading, setLoading] = useState(false);
    const [filterCenterId, setFilterCenterId] = useState<string>('all');
    const [filterYear, setFilterYear] = useState<string>(String(currentYear));

    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState<Kostenposition | null>(null);
    const [drafts, setDrafts] = useState({ jahr: String(currentYear), betrag: '0', faktor: '' });
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mietobjektId]);

    const loadData = async () => {
        setLoading(true);
        try {
            const cs = await MietabrechnungService.getKostenstellen(mietobjektId);
            setCenters(cs);
            const allPos: (Kostenposition & { centerName?: string })[] = [];

            const centerPromises = cs.map(async c => {
                let positions = c.kostenpositionen || [];
                if (!c.kostenpositionen) {
                    try {
                        positions = await MietabrechnungService.getKostenpositionen(c.id);
                    } catch (e) {
                        console.error('Kostenpositionen konnten nicht geladen werden.', e);
                        toast.error('Kostenpositionen einer Kostenstelle konnten nicht geladen werden.');
                    }
                }
                positions.forEach((p) => allPos.push({ ...p, centerName: c.name }));
            });
            await Promise.all(centerPromises);
            setPositions(allPos);

        } catch { toast.error('Kostenstellen konnten nicht geladen werden.'); } finally { setLoading(false); }
    };

    const handleNew = () => {
        if (centers.length === 0) { toast.warning('Erst Kostenstellen anlegen!'); return; }
        setDrafts({ jahr: filterYear || String(currentYear), betrag: '0', faktor: '' });
        setEditing({
            id: 0,
            kostenstelleId: centers[0].id,
            abrechnungsJahr: Number(filterYear) || currentYear,
            buchungsdatum: new Date().toISOString().split('T')[0],
            betrag: 0,
            berechnung: 'BETRAG',
            verbrauchsfaktor: null,
            beschreibung: '',
        });
        setModalOpen(true);
    };

    const handleCopyFromVorjahr = async () => {
        const targetYear = Number(filterYear);
        if (!targetYear) {
            toast.warning('Bitte wählen Sie zuerst das Zieljahr im Filter aus, um Daten vom Vorjahr zu kopieren.');
            return;
        }
        if (!await confirmDialog({ title: "Kostenpositionen kopieren", message: `Sollen alle Kostenpositionen aus dem Jahr ${targetYear - 1} in das Jahr ${targetYear} kopiert werden? Bereits vorhandene Einträge im Zieljahr bleiben erhalten.`, variant: "info", confirmLabel: "Kopieren" })) {
            return;
        }
        setLoading(true);
        try {
            const res = await MietabrechnungService.copyKostenpositionenVonVorjahr(mietobjektId, targetYear);
            toast.success(`${res.kopiert} Kostenpositionen wurden erfolgreich kopiert.`);
            loadData();
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Kopieren der Daten.');
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = (p: Kostenposition) => { setEditing({ ...p }); setDrafts({ jahr: String(p.abrechnungsJahr ?? currentYear), betrag: p.betrag == null ? '' : formatDecimalInput(p.betrag), faktor: p.verbrauchsfaktor == null ? '' : formatDecimalInput(p.verbrauchsfaktor) }); setModalOpen(true); };

    const save = async (e: FormEvent) => {
        e.preventDefault();
        if (!editing || saving) return;
        const faktor = editing.berechnung === 'VERBRAUCHSFAKTOR';
        const parsed = validateNumberDrafts({ jahr: drafts.jahr, wert: faktor ? drafts.faktor : drafts.betrag }, { jahr: { label: 'Abrechnungsjahr', required: true, integer: true, min: 1, max: 9999 }, wert: { label: faktor ? 'Verbrauchsfaktor' : 'Betrag', required: true, maxDecimalPlaces: faktor ? 6 : 2 } });
        if (!parsed.valid) { toast.error(parsed.message); return; }
        if (!editing.buchungsdatum || !centers.some(c => c.id === editing.kostenstelleId)) { toast.error('Bitte Kostenstelle und Buchungsdatum wählen.'); return; }
        const payload = { ...editing, abrechnungsJahr: parsed.values.jahr!, betrag: faktor ? 0 : parsed.values.wert!, verbrauchsfaktor: faktor ? parsed.values.wert : null };
        setSaving(true);
        try {
            if (editing.id === 0) await MietabrechnungService.createKostenposition(editing.kostenstelleId, payload);
            else await MietabrechnungService.updateKostenposition(editing.id, payload);
            setModalOpen(false); toast.success('Kostenposition gespeichert.'); await loadData();
        } catch { toast.error('Kostenposition konnte nicht gespeichert werden.'); } finally { setSaving(false); }
    };

    const del = async (id: number) => { if (await confirmDialog({ title: 'Löschen', message: 'Möchten Sie diesen Eintrag wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) try { await MietabrechnungService.deleteKostenposition(id); loadData(); } catch { toast.error('Kostenposition konnte nicht gelöscht werden.'); } };

    // Filtering
    const yearNum = Number(filterYear) || null;
    let filtered = filterCenterId === 'all' ? positions : positions.filter(p => String(p.kostenstelleId) === filterCenterId);
    if (yearNum) {
        filtered = filtered.filter(p => p.abrechnungsJahr === yearNum);
    }

    // Sort by date desc
    filtered.sort((a, b) => new Date(b.buchungsdatum).getTime() - new Date(a.buchungsdatum).getTime());

    // Available years from the positions
    const years = [...new Set(positions.map(p => p.abrechnungsJahr).filter(Boolean))].sort((a, b) => (b ?? 0) - (a ?? 0));
    const yearOptions = [
        { value: '', label: 'Alle Jahre' },
        ...years.map(y => ({ value: String(y), label: String(y) })),
    ];
    // Ensure current filter year is in options
    if (yearNum && !years.includes(yearNum)) {
        yearOptions.push({ value: String(yearNum), label: String(yearNum) });
        yearOptions.sort((a, b) => {
            if (a.value === '') return -1;
            if (b.value === '') return 1;
            return Number(b.value) - Number(a.value);
        });
    }

    const isVerbrauchsfaktor = editing?.berechnung === 'VERBRAUCHSFAKTOR';

    const formatCurrency = (value: number | undefined | null) => {
        if (value == null) return '–';
        return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(value);
    };

    return (
        <div className="space-y-4">
            {loading && <div className="text-sm text-center text-slate-500 py-2">Daten werden geladen...</div>}
            <div className="flex justify-between items-center bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                <div className="flex items-center gap-4">
                    <h3 className="text-lg font-semibold text-slate-900">Kostenpositionen</h3>
                    <Select
                        aria-label="Abrechnungsjahr filtern" value={filterYear}
                        onChange={setFilterYear}
                        options={yearOptions}
                        className="w-32"
                    />
                    <Select
                        aria-label="Kostenstelle filtern" value={filterCenterId}
                        onChange={setFilterCenterId}
                        options={[{ value: 'all', label: 'Alle Kostenstellen' }, ...centers.map(c => ({ value: String(c.id), label: c.name }))]}
                        className="w-48"
                    />
                </div>
                <div className="flex gap-2">
                    <button
                        onClick={handleCopyFromVorjahr}
                        className="text-rose-600 border border-rose-200 hover:bg-rose-50 px-3 py-2 text-sm rounded-md font-medium"
                        title="Kopiert Einträge vom Vorjahr in das aktuell gewählte Jahr"
                    >
                        Vom Vorjahr kopieren ({Number(filterYear) ? `${Number(filterYear) - 1} → ${filterYear}` : 'Bitte Jahr wählen'})
                    </button>
                    <button onClick={handleNew} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 px-3 py-2 text-sm rounded-md font-medium">+ Kostenposition</button>
                </div>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
                <table className="w-full text-sm text-left">
                    <thead className="bg-slate-50 border-b border-slate-200 text-slate-500">
                        <tr>
                            <th className="px-4 py-3">Datum</th>
                            <th className="px-4 py-3">Beschreibung</th>
                            <th className="px-4 py-3">Kostenstelle</th>
                            <th className="px-4 py-3">Berechnung</th>
                            <th className="px-4 py-3 text-right">Betrag</th>
                            <th className="px-4 py-3 text-right">Aktionen</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                        {filtered.map(p => {
                            const istVerbrauch = p.berechnung === 'VERBRAUCHSFAKTOR';
                            const displayBetrag = istVerbrauch && p.berechneterBetrag != null ? p.berechneterBetrag : p.betrag;
                            return (
                                <tr key={p.id} className="hover:bg-slate-50">
                                    <td className="px-4 py-3 text-slate-600">{p.buchungsdatum ? new Date(p.buchungsdatum).toLocaleDateString('de-DE') : '–'}</td>
                                    <td className="px-4 py-3 font-medium text-slate-900">{p.beschreibung || '–'}</td>
                                    <td className="px-4 py-3 text-slate-500"><span className="bg-slate-100 px-2 py-0.5 rounded text-xs">{p.centerName}</span></td>
                                    <td className="px-4 py-3">
                                        {istVerbrauch ? (
                                            <span className="inline-flex items-center gap-1 bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full text-xs font-medium">
                                                <Zap className="h-3 w-3" /> Verbrauch
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center gap-1 bg-slate-50 text-slate-600 border border-slate-200 px-2 py-0.5 rounded-full text-xs font-medium">
                                                Fester Betrag
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-right font-medium">
                                        {formatCurrency(displayBetrag)}
                                        {istVerbrauch && p.verbrauchsfaktor != null && (
                                            <div className="text-xs text-slate-400 mt-0.5">
                                                Faktor: {formatDecimalInput(p.verbrauchsfaktor)} · Menge: {p.verbrauchsmenge == null ? '–' : formatDecimalInput(p.verbrauchsmenge)}
                                            </div>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-right space-x-2">
                                        <button onClick={() => handleEdit(p)} className="text-slate-600 hover:text-rose-600">Bearbeiten</button>
                                        <button onClick={() => del(p.id)} className="text-slate-400 hover:text-red-600">Löschen</button>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
                {filtered.length === 0 && <div className="p-8 text-center text-slate-400">Keine Einträge gefunden.</div>}
            </div>

            <Dialog open={modalOpen} onOpenChange={open => { if (!saving) setModalOpen(open); }} aria-label="Kostenposition" className="w-full max-w-xl">
                <DialogContent className="overflow-y-auto">
                    <DialogHeader><DialogTitle>Kostenposition</DialogTitle></DialogHeader>
                    {editing && (
                        <form noValidate onSubmit={save} className="space-y-4">
                            <div className="space-y-2"><Label>Kostenstelle</Label>
                                <Select aria-label="Kostenstelle" value={String(editing.kostenstelleId)} onChange={val => setEditing({ ...editing, kostenstelleId: Number(val) })} options={centers.map(c => ({ value: String(c.id), label: c.name }))} />
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2"><Label>Abrechnungsjahr</Label>
                                    <DecimalInput aria-label="Abrechnungsjahr" integer min={1} max={9999} value={drafts.jahr} onChange={jahr => setDrafts({ ...drafts, jahr })} required />
                                </div>
                                <div className="space-y-2"><Label>Buchungsdatum</Label><DatePicker aria-label="Buchungsdatum" required value={editing.buchungsdatum} onChange={value => setEditing({ ...editing, buchungsdatum: value })} placeholder="Datum wählen" /></div>
                            </div>

                            {/* Berechnungsart Toggle */}
                            <div className="space-y-2">
                                <Label>Berechnungsart</Label>
                                <Select
                                    aria-label="Berechnungsart" value={editing.berechnung || 'BETRAG'}
                                    onChange={(val) => {
                                        const isFaktor = val === 'VERBRAUCHSFAKTOR';
                                        if (isFaktor && drafts.faktor === '') setDrafts({ ...drafts, faktor: '1' });
                                        setEditing({
                                            ...editing,
                                            berechnung: val as 'BETRAG' | 'VERBRAUCHSFAKTOR',
                                            verbrauchsfaktor: isFaktor ? (editing.verbrauchsfaktor ?? 1) : null,
                                            betrag: isFaktor ? 0 : editing.betrag,
                                        });
                                    }}
                                    options={[
                                        { value: 'BETRAG', label: 'Fester Betrag' },
                                        { value: 'VERBRAUCHSFAKTOR', label: 'Verbrauch × Faktor' },
                                    ]}
                                />
                            </div>

                            {isVerbrauchsfaktor ? (
                                <div className="space-y-3">
                                    <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                                        <p className="text-xs text-amber-700 font-medium mb-1">Verbrauchsbasierte Berechnung</p>
                                        <p className="text-xs text-amber-600">Der Betrag wird automatisch berechnet: Gesamtverbrauch aller Zähler × Faktor</p>
                                    </div>
                                    <div className="space-y-2">
                                        <Label>Verbrauchsfaktor (€ pro Einheit)</Label>
                                        <DecimalInput aria-label="Verbrauchsfaktor (€ pro Einheit)" value={drafts.faktor} onChange={faktor => setDrafts({ ...drafts, faktor })} placeholder="z. B. 2,50" required />
                                    </div>
                                    {editing.id !== 0 && editing.berechneterBetrag != null && (
                                        <div className="grid grid-cols-2 gap-3 text-sm">
                                            <div className="bg-slate-50 rounded-lg p-2.5">
                                                <div className="text-slate-500 text-xs">Verbrauchsmenge</div>
                                                <div className="font-semibold text-slate-900">{editing.verbrauchsmenge == null ? '–' : formatDecimalInput(editing.verbrauchsmenge)}</div>
                                            </div>
                                            <div className="bg-slate-50 rounded-lg p-2.5">
                                                <div className="text-slate-500 text-xs">Berechneter Betrag</div>
                                                <div className="font-semibold text-slate-900">{formatCurrency(editing.berechneterBetrag)}</div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div className="space-y-2"><Label>Betrag (€)</Label>
                                    <DecimalInput aria-label="Betrag (€)" value={drafts.betrag} onChange={betrag => setDrafts({ ...drafts, betrag })} required />
                                </div>
                            )}

                            <div className="space-y-2"><Label>Beschreibung</Label><Input aria-label="Beschreibung" value={editing.beschreibung} onChange={e => setEditing({ ...editing, beschreibung: e.target.value })} /></div>
                            <div className="space-y-2"><Label>Belegnummer</Label><Input aria-label="Belegnummer" value={editing.belegNummer ?? ''} onChange={e => setEditing({ ...editing, belegNummer: e.target.value || undefined })} /></div>

                            <DialogFooter>
                                <button type="button" disabled={saving} onClick={() => setModalOpen(false)} className="border-rose-300 text-rose-700 hover:bg-rose-50 border px-3 py-2 text-sm rounded-md font-medium">Abbrechen</button>
                                <button type="submit" disabled={saving} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 px-3 py-2 text-sm rounded-md font-medium">Speichern</button>
                            </DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
