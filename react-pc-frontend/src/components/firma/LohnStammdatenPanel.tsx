import { useCallback, useEffect, useState } from 'react';
import { Plus, Trash2, Save, X, Edit2, HeartPulse, Percent, Hammer } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import { DecimalInput } from '../ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { DatePicker } from '../ui/datepicker';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { cn } from '../../lib/utils';

type SubTab = 'krankenkassen' | 'sv-saetze' | 'gewerke';

interface KrankenkasseDto {
    id?: number;
    name: string;
    kuerzel?: string;
    zusatzbeitragProzent: number | string;
    aktiv?: boolean;
    gueltigAb?: string;
    bemerkung?: string;
}

interface SvSatzDto {
    id?: number;
    satzTyp: string;
    prozent: number | string;
    gueltigAb: string;
    beschreibung?: string;
}

interface GewerkDto {
    id?: number;
    name: string;
    bgName: string;
    bgSatzProzent: number | string;
    aktiv?: boolean;
    bemerkung?: string;
}

const SV_SATZ_TYPEN = [
    { value: 'KV_GESAMT', label: 'Krankenversicherung (gesamt)' },
    { value: 'PV_GESAMT', label: 'Pflegeversicherung (gesamt)' },
    { value: 'PV_KINDERLOS_AN_ZUSCHLAG', label: 'PV-Zuschlag für Kinderlose (nur AN)' },
    { value: 'RV_GESAMT', label: 'Rentenversicherung (gesamt)' },
    { value: 'AV_GESAMT', label: 'Arbeitslosenversicherung (gesamt)' },
    { value: 'MINIJOB_AG_KV', label: 'Minijob: KV-Pauschale (AG)' },
    { value: 'MINIJOB_AG_RV', label: 'Minijob: RV-Pauschale (AG)' },
    { value: 'MINIJOB_AG_PAUSCHALSTEUER', label: 'Minijob: Pauschalsteuer (AG)' },
    { value: 'U1_UMLAGE', label: 'Umlage U1 (Krankheit)' },
    { value: 'U2_UMLAGE', label: 'Umlage U2 (Mutterschaft)' },
    { value: 'INSOLVENZGELDUMLAGE', label: 'Insolvenzgeldumlage' },
];

const labelForSatzTyp = (typ: string) => SV_SATZ_TYPEN.find(t => t.value === typ)?.label ?? typ;

export function LohnStammdatenPanel() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const showError = toast.error;
    const [subTab, setSubTab] = useState<SubTab>('krankenkassen');

    const [krankenkassen, setKrankenkassen] = useState<KrankenkasseDto[]>([]);
    const [svSaetze, setSvSaetze] = useState<SvSatzDto[]>([]);
    const [gewerke, setGewerke] = useState<GewerkDto[]>([]);

    const [editingKK, setEditingKK] = useState<(Omit<KrankenkasseDto, 'zusatzbeitragProzent'> & { zusatzbeitragProzent: string }) | null>(null);
    const [editingSv, setEditingSv] = useState<(Omit<SvSatzDto, 'prozent'> & { prozent: string }) | null>(null);
    const [editingGewerk, setEditingGewerk] = useState<(Omit<GewerkDto, 'bgSatzProzent'> & { bgSatzProzent: string }) | null>(null);

    const loadKrankenkassen = useCallback(async () => {
        try {
            const res = await fetch('/api/lohn-stammdaten/krankenkassen');
            if (!res.ok) { showError('Krankenkassen konnten nicht geladen werden.'); return; }
            if (res.ok) setKrankenkassen(await res.json());
        } catch {
            showError('Krankenkassen konnten nicht geladen werden.');
        }
    }, [showError]);
    const loadSvSaetze = useCallback(async () => {
        try {
            const res = await fetch('/api/lohn-stammdaten/sv-saetze');
            if (!res.ok) { showError('SV-Sätze konnten nicht geladen werden.'); return; }
            if (res.ok) setSvSaetze(await res.json());
        } catch {
            showError('SV-Sätze konnten nicht geladen werden.');
        }
    }, [showError]);
    const loadGewerke = useCallback(async () => {
        try {
            const res = await fetch('/api/lohn-stammdaten/gewerke');
            if (!res.ok) { showError('Gewerke konnten nicht geladen werden.'); return; }
            if (res.ok) setGewerke(await res.json());
        } catch {
            showError('Gewerke konnten nicht geladen werden.');
        }
    }, [showError]);

    useEffect(() => {
        // Initial alle drei Listen laden - die Datenmengen sind klein (< 50 Zeilen je
        // Tabelle) und der Sub-Tab-Wechsel soll instant passieren.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        void loadKrankenkassen();
        void loadSvSaetze();
        void loadGewerke();
    }, [loadKrankenkassen, loadSvSaetze, loadGewerke]);

    // ---------- Krankenkasse Save/Delete ----------
    const saveKK = async () => {
        if (!editingKK) return;
        const parsed = validateDecimalInput(editingKK.zusatzbeitragProzent, { label: 'Zusatzbeitrag in %', required: true, min: 0, max: 100 });
        if (!parsed.valid || parsed.value === null) { toast.error(!parsed.valid ? parsed.message : 'Bitte Prozentwert eingeben.'); return; }
        if (!editingKK.name?.trim()) {
            toast.error('Bitte den Namen der Krankenkasse eintragen.');
            return;
        }
        const url = editingKK.id ? `/api/lohn-stammdaten/krankenkassen/${encodeURIComponent(String(editingKK.id))}` : '/api/lohn-stammdaten/krankenkassen';
        const method = editingKK.id ? 'PUT' : 'POST';
        try {
            const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...editingKK, zusatzbeitragProzent: parsed.value }) });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                toast.error(err.message ?? 'Speichern fehlgeschlagen.');
                return;
            }
            setEditingKK(null);
            await loadKrankenkassen();
            toast.success('Krankenkasse gespeichert.');
        } catch {
            toast.error('Krankenkasse konnte nicht gespeichert werden.');
        }
    };
    const deleteKK = async (id: number) => {
        const ok = await confirmDialog({ title: 'Krankenkasse löschen?', message: 'Mitarbeiter-Zuordnungen werden auf "keine" gesetzt.', confirmLabel: 'Löschen', variant: 'danger' });
        if (!ok) return;
        try {
            const res = await fetch(`/api/lohn-stammdaten/krankenkassen/${encodeURIComponent(String(id))}`, { method: 'DELETE' });
            if (!res.ok) { toast.error('Krankenkasse konnte nicht gelöscht werden.'); return; }
            await loadKrankenkassen();
        } catch {
            toast.error('Krankenkasse konnte nicht gelöscht werden.');
        }
    };

    // ---------- SvSatz Save/Delete ----------
    const saveSv = async () => {
        if (!editingSv) return;
        const parsed = validateDecimalInput(editingSv.prozent, { label: 'Prozent', required: true, min: 0, max: 100 });
        if (!parsed.valid || parsed.value === null) { toast.error(!parsed.valid ? parsed.message : 'Bitte Prozentwert eingeben.'); return; }
        if (!editingSv.satzTyp || !editingSv.gueltigAb) {
            toast.error('Bitte Typ und Gültig-ab-Datum auswählen.');
            return;
        }
        const url = editingSv.id ? `/api/lohn-stammdaten/sv-saetze/${encodeURIComponent(String(editingSv.id))}` : '/api/lohn-stammdaten/sv-saetze';
        const method = editingSv.id ? 'PUT' : 'POST';
        try {
            const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...editingSv, prozent: parsed.value }) });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                toast.error(err.message ?? 'Speichern fehlgeschlagen.');
                return;
            }
            setEditingSv(null);
            await loadSvSaetze();
            toast.success('SV-Satz gespeichert.');
        } catch {
            toast.error('SV-Satz konnte nicht gespeichert werden.');
        }
    };
    const deleteSv = async (id: number) => {
        const ok = await confirmDialog({ title: 'SV-Satz löschen?', message: 'Diesen Satz wirklich entfernen?', confirmLabel: 'Löschen', variant: 'danger' });
        if (!ok) return;
        try {
            const res = await fetch(`/api/lohn-stammdaten/sv-saetze/${encodeURIComponent(String(id))}`, { method: 'DELETE' });
            if (!res.ok) { toast.error('SV-Satz konnte nicht gelöscht werden.'); return; }
            await loadSvSaetze();
        } catch {
            toast.error('SV-Satz konnte nicht gelöscht werden.');
        }
    };

    // ---------- Gewerk Save/Delete ----------
    const saveGewerk = async () => {
        if (!editingGewerk) return;
        const parsed = validateDecimalInput(editingGewerk.bgSatzProzent, { label: 'BG-Beitragssatz in %', required: true, min: 0, max: 100 });
        if (!parsed.valid || parsed.value === null) { toast.error(!parsed.valid ? parsed.message : 'Bitte Prozentwert eingeben.'); return; }
        if (!editingGewerk.name?.trim() || !editingGewerk.bgName?.trim()) {
            toast.error('Name und Berufsgenossenschaft sind Pflicht.');
            return;
        }
        const url = editingGewerk.id ? `/api/lohn-stammdaten/gewerke/${encodeURIComponent(String(editingGewerk.id))}` : '/api/lohn-stammdaten/gewerke';
        const method = editingGewerk.id ? 'PUT' : 'POST';
        try {
            const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...editingGewerk, bgSatzProzent: parsed.value }) });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                toast.error(err.message ?? 'Speichern fehlgeschlagen.');
                return;
            }
            setEditingGewerk(null);
            await loadGewerke();
            toast.success('Gewerk gespeichert.');
        } catch {
            toast.error('Gewerk konnte nicht gespeichert werden.');
        }
    };
    const deleteGewerk = async (id: number) => {
        const ok = await confirmDialog({ title: 'Gewerk löschen?', message: 'Dieses Gewerk wirklich entfernen?', confirmLabel: 'Löschen', variant: 'danger' });
        if (!ok) return;
        try {
            const res = await fetch(`/api/lohn-stammdaten/gewerke/${encodeURIComponent(String(id))}`, { method: 'DELETE' });
            if (!res.ok) { toast.error('Gewerk konnte nicht gelöscht werden.'); return; }
            await loadGewerke();
        } catch {
            toast.error('Gewerk konnte nicht gelöscht werden.');
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex gap-2 border-b border-slate-200 pb-1">
                <SubTabButton active={subTab === 'krankenkassen'} icon={<HeartPulse className="w-4 h-4" />} onClick={() => setSubTab('krankenkassen')}>
                    Krankenkassen
                </SubTabButton>
                <SubTabButton active={subTab === 'sv-saetze'} icon={<Percent className="w-4 h-4" />} onClick={() => setSubTab('sv-saetze')}>
                    SV-Sätze
                </SubTabButton>
                <SubTabButton active={subTab === 'gewerke'} icon={<Hammer className="w-4 h-4" />} onClick={() => setSubTab('gewerke')}>
                    Gewerke / BG
                </SubTabButton>
            </div>

            {subTab === 'krankenkassen' && (
                <div className="space-y-3">
                    <div className="flex justify-between items-center">
                        <p className="text-slate-500 text-sm">Krankenkassen mit ihrem aktuellen Zusatzbeitrag. Werte können jederzeit angepasst werden.</p>
                        <Button onClick={() => setEditingKK({ name: '', zusatzbeitragProzent: '', aktiv: true })} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" />Neue Krankenkasse
                        </Button>
                    </div>
                    <Card className="p-0 overflow-hidden">
                        <table className="w-full text-sm">
                            <thead className="bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="text-left px-4 py-2 font-semibold">Name</th>
                                    <th className="text-left px-4 py-2 font-semibold">Kürzel</th>
                                    <th className="text-right px-4 py-2 font-semibold">Zusatzbeitrag</th>
                                    <th className="text-left px-4 py-2 font-semibold">Gültig ab</th>
                                    <th className="text-center px-4 py-2 font-semibold">Aktiv</th>
                                    <th className="text-right px-4 py-2 font-semibold">Aktionen</th>
                                </tr>
                            </thead>
                            <tbody>
                                {krankenkassen.map(k => (
                                    <tr key={k.id} className="border-t border-slate-100 hover:bg-slate-50">
                                        <td className="px-4 py-2 font-medium text-slate-900">{k.name}</td>
                                        <td className="px-4 py-2 text-slate-600">{k.kuerzel ?? '—'}</td>
                                        <td className="px-4 py-2 text-right tabular-nums">{Number(k.zusatzbeitragProzent).toLocaleString('de-DE', { minimumFractionDigits: 2 })} %</td>
                                        <td className="px-4 py-2 text-slate-600">{k.gueltigAb ?? '—'}</td>
                                        <td className="px-4 py-2 text-center">
                                            <span className={cn('inline-block px-2 py-0.5 text-xs rounded', k.aktiv ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-500')}>
                                                {k.aktiv ? 'aktiv' : 'inaktiv'}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2 text-right">
                                            <Button variant="ghost" size="sm" onClick={() => setEditingKK({ ...k, zusatzbeitragProzent: formatDecimalInput(Number(k.zusatzbeitragProzent)) })}><Edit2 className="w-4 h-4" /></Button>
                                            <Button variant="ghost" size="sm" onClick={() => k.id && deleteKK(k.id)} className="text-red-600 hover:text-red-700"><Trash2 className="w-4 h-4" /></Button>
                                        </td>
                                    </tr>
                                ))}
                                {krankenkassen.length === 0 && (
                                    <tr><td colSpan={6} className="px-4 py-6 text-center text-slate-400">Keine Krankenkassen angelegt.</td></tr>
                                )}
                            </tbody>
                        </table>
                    </Card>
                </div>
            )}

            {subTab === 'sv-saetze' && (
                <div className="space-y-3">
                    <div className="flex justify-between items-center">
                        <p className="text-slate-500 text-sm">Bundeseinheitliche Sozialversicherungs-Beitragssätze. Bei Gesetzesänderungen einfach hier anpassen.</p>
                        <Button onClick={() => setEditingSv({ satzTyp: 'KV_GESAMT', prozent: '', gueltigAb: new Date().toISOString().slice(0, 10) })} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" />Neuer Satz
                        </Button>
                    </div>
                    <Card className="p-0 overflow-hidden">
                        <table className="w-full text-sm">
                            <thead className="bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="text-left px-4 py-2 font-semibold">Typ</th>
                                    <th className="text-right px-4 py-2 font-semibold">Prozent</th>
                                    <th className="text-left px-4 py-2 font-semibold">Gültig ab</th>
                                    <th className="text-left px-4 py-2 font-semibold">Beschreibung</th>
                                    <th className="text-right px-4 py-2 font-semibold">Aktionen</th>
                                </tr>
                            </thead>
                            <tbody>
                                {svSaetze.map(s => (
                                    <tr key={s.id} className="border-t border-slate-100 hover:bg-slate-50">
                                        <td className="px-4 py-2 font-medium text-slate-900">{labelForSatzTyp(s.satzTyp)}</td>
                                        <td className="px-4 py-2 text-right tabular-nums">{Number(s.prozent).toLocaleString('de-DE', { minimumFractionDigits: 2 })} %</td>
                                        <td className="px-4 py-2 text-slate-600">{s.gueltigAb}</td>
                                        <td className="px-4 py-2 text-slate-500 max-w-md truncate">{s.beschreibung}</td>
                                        <td className="px-4 py-2 text-right">
                                            <Button variant="ghost" size="sm" onClick={() => setEditingSv({ ...s, prozent: formatDecimalInput(Number(s.prozent)) })}><Edit2 className="w-4 h-4" /></Button>
                                            <Button variant="ghost" size="sm" onClick={() => s.id && deleteSv(s.id)} className="text-red-600 hover:text-red-700"><Trash2 className="w-4 h-4" /></Button>
                                        </td>
                                    </tr>
                                ))}
                                {svSaetze.length === 0 && (
                                    <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-400">Keine SV-Sätze angelegt.</td></tr>
                                )}
                            </tbody>
                        </table>
                    </Card>
                </div>
            )}

            {subTab === 'gewerke' && (
                <div className="space-y-3">
                    <div className="flex justify-between items-center">
                        <p className="text-slate-500 text-sm">Gewerk und zugeordnete Berufsgenossenschaft. Der BG-Satz ist die gesetzliche Unfallversicherung – Standardwert pro Gewerk.</p>
                        <Button onClick={() => setEditingGewerk({ name: '', bgName: '', bgSatzProzent: '', aktiv: true })} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" />Neues Gewerk
                        </Button>
                    </div>
                    <Card className="p-0 overflow-hidden">
                        <table className="w-full text-sm">
                            <thead className="bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="text-left px-4 py-2 font-semibold">Gewerk</th>
                                    <th className="text-left px-4 py-2 font-semibold">Berufsgenossenschaft</th>
                                    <th className="text-right px-4 py-2 font-semibold">BG-Satz</th>
                                    <th className="text-center px-4 py-2 font-semibold">Aktiv</th>
                                    <th className="text-right px-4 py-2 font-semibold">Aktionen</th>
                                </tr>
                            </thead>
                            <tbody>
                                {gewerke.map(g => (
                                    <tr key={g.id} className="border-t border-slate-100 hover:bg-slate-50">
                                        <td className="px-4 py-2 font-medium text-slate-900">{g.name}</td>
                                        <td className="px-4 py-2 text-slate-600">{g.bgName}</td>
                                        <td className="px-4 py-2 text-right tabular-nums">{Number(g.bgSatzProzent).toLocaleString('de-DE', { minimumFractionDigits: 2 })} %</td>
                                        <td className="px-4 py-2 text-center">
                                            <span className={cn('inline-block px-2 py-0.5 text-xs rounded', g.aktiv ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-500')}>
                                                {g.aktiv ? 'aktiv' : 'inaktiv'}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2 text-right">
                                            <Button variant="ghost" size="sm" onClick={() => setEditingGewerk({ ...g, bgSatzProzent: formatDecimalInput(Number(g.bgSatzProzent)) })}><Edit2 className="w-4 h-4" /></Button>
                                            <Button variant="ghost" size="sm" onClick={() => g.id && deleteGewerk(g.id)} className="text-red-600 hover:text-red-700"><Trash2 className="w-4 h-4" /></Button>
                                        </td>
                                    </tr>
                                ))}
                                {gewerke.length === 0 && (
                                    <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-400">Keine Gewerke angelegt.</td></tr>
                                )}
                            </tbody>
                        </table>
                    </Card>
                </div>
            )}

            {/* ---------- Edit-Modals ---------- */}
            {editingKK && (
                <SimpleEditModal title={editingKK.id ? 'Krankenkasse bearbeiten' : 'Neue Krankenkasse'} onClose={() => setEditingKK(null)} onSave={saveKK}>
                    <Field label="Name *">
                        <Input value={editingKK.name} onChange={e => setEditingKK({ ...editingKK, name: e.target.value })} />
                    </Field>
                    <Field label="Kürzel">
                        <Input value={editingKK.kuerzel ?? ''} onChange={e => setEditingKK({ ...editingKK, kuerzel: e.target.value })} />
                    </Field>
                    <Field label="Zusatzbeitrag in %">
                        <DecimalInput aria-label="Zusatzbeitrag in %" required min={0} max={100} value={editingKK.zusatzbeitragProzent} onChange={draft => setEditingKK({ ...editingKK, zusatzbeitragProzent: draft })} />
                    </Field>
                    <Field label="Gültig ab (Datum)">
                        <DatePicker value={editingKK.gueltigAb ?? ''} onChange={v => setEditingKK({ ...editingKK, gueltigAb: v })} />
                    </Field>
                    <Field label="Bemerkung">
                        <Input value={editingKK.bemerkung ?? ''} onChange={e => setEditingKK({ ...editingKK, bemerkung: e.target.value })} />
                    </Field>
                    <Field label="Status">
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={editingKK.aktiv ?? true} onChange={e => setEditingKK({ ...editingKK, aktiv: e.target.checked })} />
                            Diese Krankenkasse ist aktiv (kann Mitarbeitern zugewiesen werden)
                        </label>
                    </Field>
                </SimpleEditModal>
            )}

            {editingSv && (
                <SimpleEditModal title={editingSv.id ? 'SV-Satz bearbeiten' : 'Neuer SV-Satz'} onClose={() => setEditingSv(null)} onSave={saveSv}>
                    <Field label="Satz-Typ *">
                        <Select value={editingSv.satzTyp} onChange={v => setEditingSv({ ...editingSv, satzTyp: v })} options={SV_SATZ_TYPEN} />
                    </Field>
                    <Field label="Prozent *">
                        <DecimalInput aria-label="Prozent" required min={0} max={100} value={editingSv.prozent} onChange={draft => setEditingSv({ ...editingSv, prozent: draft })} />
                    </Field>
                    <Field label="Gültig ab *">
                        <DatePicker value={editingSv.gueltigAb} onChange={v => setEditingSv({ ...editingSv, gueltigAb: v })} />
                    </Field>
                    <Field label="Beschreibung">
                        <Input value={editingSv.beschreibung ?? ''} onChange={e => setEditingSv({ ...editingSv, beschreibung: e.target.value })} />
                    </Field>
                </SimpleEditModal>
            )}

            {editingGewerk && (
                <SimpleEditModal title={editingGewerk.id ? 'Gewerk bearbeiten' : 'Neues Gewerk'} onClose={() => setEditingGewerk(null)} onSave={saveGewerk}>
                    <Field label="Gewerk *">
                        <Input value={editingGewerk.name} onChange={e => setEditingGewerk({ ...editingGewerk, name: e.target.value })} />
                    </Field>
                    <Field label="Berufsgenossenschaft *">
                        <Input value={editingGewerk.bgName} onChange={e => setEditingGewerk({ ...editingGewerk, bgName: e.target.value })} />
                    </Field>
                    <Field label="BG-Beitragssatz in %">
                        <DecimalInput aria-label="BG-Beitragssatz in %" required min={0} max={100} value={editingGewerk.bgSatzProzent} onChange={draft => setEditingGewerk({ ...editingGewerk, bgSatzProzent: draft })} />
                    </Field>
                    <Field label="Bemerkung">
                        <Input value={editingGewerk.bemerkung ?? ''} onChange={e => setEditingGewerk({ ...editingGewerk, bemerkung: e.target.value })} />
                    </Field>
                    <Field label="Status">
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={editingGewerk.aktiv ?? true} onChange={e => setEditingGewerk({ ...editingGewerk, aktiv: e.target.checked })} />
                            Aktiv
                        </label>
                    </Field>
                </SimpleEditModal>
            )}
        </div>
    );
}

function SubTabButton({ active, icon, onClick, children }: { active: boolean; icon: React.ReactNode; onClick: () => void; children: React.ReactNode }) {
    return (
        <button
            onClick={onClick}
            className={cn(
                'inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap',
                active ? 'bg-rose-600 text-white' : 'text-slate-600 hover:bg-rose-50'
            )}
        >
            {icon}
            {children}
        </button>
    );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div className="space-y-1.5">
            <Label>{label}</Label>
            {children}
        </div>
    );
}

function SimpleEditModal({ title, children, onClose, onSave }: { title: string; children: React.ReactNode; onClose: () => void; onSave: () => void }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
            <Card className="w-full max-w-lg p-6 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex justify-between items-center">
                    <h3 className="text-lg font-semibold text-slate-900">{title}</h3>
                    <Button variant="ghost" size="sm" onClick={onClose}><X className="w-4 h-4" /></Button>
                </div>
                <div className="space-y-3">{children}</div>
                <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                    <Button variant="outline" onClick={onClose} className="border-rose-300 text-rose-700 hover:bg-rose-50">Abbrechen</Button>
                    <Button onClick={onSave} className="bg-rose-600 text-white hover:bg-rose-700"><Save className="w-4 h-4 mr-2" />Speichern</Button>
                </div>
            </Card>
        </div>
    );
}
