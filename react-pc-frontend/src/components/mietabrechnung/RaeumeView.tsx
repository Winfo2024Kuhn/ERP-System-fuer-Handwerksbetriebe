import { useEffect, useState, type FormEvent } from 'react';
import { type Raum, type Verbraucher, type Zaehlerstand } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Input } from '../ui/input';
import { DecimalInput } from '../ui/decimal-input';
import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import { Building2, Plus, Pencil, Trash2 } from 'lucide-react';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { DatePicker } from '../ui/datepicker';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';

interface RaeumeViewProps {
    mietobjektId: number;
}

export function RaeumeView({ mietobjektId }: RaeumeViewProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [raeume, setRaeume] = useState<Raum[]>([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [flaeche, setFlaeche] = useState('0');
    const [meterDraft, setMeterDraft] = useState({ jahr: '', stand: '0', verbrauch: '' });

    // Modal States
    const [roomModalOpen, setRoomModalOpen] = useState(false);
    const [consumerModalOpen, setConsumerModalOpen] = useState(false);
    const [meterModalOpen, setMeterModalOpen] = useState(false);

    const [editingRoom, setEditingRoom] = useState<Raum | null>(null);
    const [editingConsumer, setEditingConsumer] = useState<Verbraucher | null>(null);
    const [editingMeter, setEditingMeter] = useState<Zaehlerstand | null>(null);
    const [activeConsumerId, setActiveConsumerId] = useState<number | null>(null);

    useEffect(() => {
        loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mietobjektId]);

    const loadData = async () => {
        setLoading(true);
        try {
            const rooms = await MietabrechnungService.getRaeume(mietobjektId);
            const enriched = await Promise.all(rooms.map(async (r) => {
                const consumers = await MietabrechnungService.getVerbraucherForRaum(r.id);
                const consumersWithMeters = await Promise.all(consumers.map(async (c) => {
                    const meters = await MietabrechnungService.getZaehlerstaende(c.id);
                    return { ...c, zaehlerstaende: meters };
                }));
                return { ...r, verbraucher: consumersWithMeters };
            }));
            setRaeume(enriched);
        } catch {
            toast.error('Räume und Zähler konnten nicht geladen werden.');
        } finally {
            setLoading(false);
        }
    };

    // --- Room Handlers ---
    const handleNewRoom = () => {
        setFlaeche('0');
        setEditingRoom({ id: 0, mietobjektId, name: '', beschreibung: '', flaecheQuadratmeter: 0 });
        setRoomModalOpen(true);
    };
    const handleEditRoom = (r: Raum) => {
        setEditingRoom(r);
        setFlaeche(r.flaecheQuadratmeter == null ? '' : formatDecimalInput(r.flaecheQuadratmeter));
        setRoomModalOpen(true);
    };
    const saveRoom = async (e: FormEvent) => {
        e.preventDefault();
        if (!editingRoom || saving) return;
        const parsed = validateNumberDrafts({ flaeche }, { flaeche: { label: 'Fläche', required: true, maxDecimalPlaces: 2 } });
        if (!parsed.valid) { toast.error(parsed.message); return; }
        if (!editingRoom.name.trim()) { toast.error('Bitte eine Raumbezeichnung eingeben.'); return; }
        const payload = { ...editingRoom, flaecheQuadratmeter: parsed.values.flaeche! };
        setSaving(true);
        try {
            if (editingRoom.id === 0) await MietabrechnungService.createRaum(mietobjektId, payload);
            else await MietabrechnungService.updateRaum(editingRoom.id, payload);
            setRoomModalOpen(false);
            toast.success('Raum gespeichert.'); await loadData();
        } catch { toast.error('Raum konnte nicht gespeichert werden.'); } finally { setSaving(false); }
    };
    const deleteRoom = async (id: number) => {
        if (!await confirmDialog({ title: 'Raum löschen', message: 'Raum und alle Verbraucher löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try { await MietabrechnungService.deleteRaum(id); loadData(); } catch { toast.error('Raum konnte nicht gelöscht werden.'); }
    };

    // --- Consumer Handlers ---
    const handleNewConsumer = (raumId: number) => {
        setEditingConsumer({ id: 0, raumId, name: '', verbrauchsart: 'WASSER', einheit: 'm³', seriennummer: '', aktiv: true });
        setConsumerModalOpen(true);
    };
    const handleEditConsumer = (c: Verbraucher) => {
        setEditingConsumer(c);
        setConsumerModalOpen(true);
    };
    const saveConsumer = async (e: FormEvent) => {
        e.preventDefault();
        if (!editingConsumer || saving) return;
        if (!editingConsumer.name.trim()) { toast.error('Bitte eine Zählerbezeichnung eingeben.'); return; }
        setSaving(true);
        try {
            if (editingConsumer.id === 0) await MietabrechnungService.createVerbraucher(editingConsumer.raumId, editingConsumer);
            else await MietabrechnungService.updateVerbraucher(editingConsumer.id, editingConsumer);
            setConsumerModalOpen(false);
            toast.success('Zähler gespeichert.'); await loadData();
        } catch { toast.error('Zähler konnte nicht gespeichert werden.'); } finally { setSaving(false); }
    };
    const deleteConsumer = async (id: number) => {
        if (!await confirmDialog({ title: 'Verbraucher löschen', message: 'Verbraucher löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try { await MietabrechnungService.deleteVerbraucher(id); loadData(); } catch { toast.error('Zähler konnte nicht gelöscht werden.'); }
    };

    // --- Meter Handlers ---
    const handleNewMeter = (consumerId: number) => {
        setMeterDraft({ jahr: String(new Date().getFullYear()), stand: '0', verbrauch: '' });
        setActiveConsumerId(consumerId);
        setEditingMeter({ id: 0, verbrauchsgegenstandId: consumerId, abrechnungsJahr: new Date().getFullYear(), stichtag: `${new Date().getFullYear()}-12-31`, stand: 0, kommentar: '' });
        setMeterModalOpen(true);
    };
    const saveMeter = async (e: FormEvent) => {
        e.preventDefault();
        const cid = activeConsumerId || editingMeter?.verbrauchsgegenstandId;
        if (!editingMeter || !cid || saving) return;
        const parsed = validateNumberDrafts(meterDraft, { jahr: { label: 'Abrechnungsjahr', required: true, integer: true, min: 1, max: 9999 }, stand: { label: 'Zählerstand', required: true, maxDecimalPlaces: 4 }, verbrauch: { label: 'Verbrauch', maxDecimalPlaces: 4 } });
        if (!parsed.valid) { toast.error(parsed.message); return; }
        if (!editingMeter.stichtag) { toast.error('Bitte einen Stichtag wählen.'); return; }
        setSaving(true);
        try {
            // Ensure verbrauchsgegenstandId is set correctly
            const meterToSave = { ...editingMeter, verbrauchsgegenstandId: cid, abrechnungsJahr: parsed.values.jahr!, stand: parsed.values.stand!, verbrauch: parsed.values.verbrauch ?? undefined };

            if (editingMeter.id === 0) await MietabrechnungService.createZaehlerstand(cid, meterToSave);
            else await MietabrechnungService.updateZaehlerstand(editingMeter.id, meterToSave);

            setMeterModalOpen(false);
            toast.success('Zählerstand gespeichert.'); await loadData();
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Unbekannt';
            console.error(err);
            toast.error('Zählerstand konnte nicht gespeichert werden: ' + message);
        } finally { setSaving(false); }
    };

    const handleEditMeter = (m: Zaehlerstand) => {
        setEditingMeter(m);
        setMeterDraft({ jahr: String(m.abrechnungsJahr), stand: formatDecimalInput(m.stand), verbrauch: m.verbrauch == null ? '' : formatDecimalInput(m.verbrauch) });
        setActiveConsumerId(m.verbrauchsgegenstandId || null);
        setMeterModalOpen(true);
    };

    const deleteMeter = async (id: number) => {
        if (!await confirmDialog({ title: 'Zählerstand löschen', message: 'Zählerstand löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await MietabrechnungService.deleteZaehlerstand(id);
            loadData();
        } catch { toast.error('Zählerstand konnte nicht gelöscht werden.'); }
    };

    return (
        <div className="space-y-6">
            {loading && <div className="text-sm text-center text-slate-500 py-2">Daten werden geladen...</div>}

            <div className="flex justify-between items-center bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                <div>
                    <h3 className="text-lg font-semibold text-slate-900">Räume & Verbraucher</h3>
                    <p className="text-sm text-slate-500">Erfassungsstruktur für Zähler und Flächen</p>
                </div>
                <button onClick={handleNewRoom} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm shadow-sm transition-colors">
                    + Raum hinzufügen
                </button>
            </div>

            <div className="grid grid-cols-1 gap-6">
                {raeume.map(room => (
                    <div key={room.id} className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden group/room transition-all hover:shadow-md">
                        <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex justify-between items-center">
                            <div>
                                <h4 className="font-bold text-slate-900 text-lg flex items-center gap-2">
                                    <Building2 className="w-5 h-5 text-slate-400" />
                                    {room.name}
                                </h4>
                                <div className="flex items-center gap-3 text-sm text-slate-500 mt-0.5">
                                    <span className="bg-white px-2 py-0.5 rounded border border-slate-200 shadow-sm font-mono text-xs">{room.flaecheQuadratmeter == null ? '–' : formatDecimalInput(room.flaecheQuadratmeter)} m²</span>
                                    {room.beschreibung && <span>&bull; {room.beschreibung}</span>}
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button onClick={() => handleEditRoom(room)} className="text-slate-500 hover:text-rose-600 text-sm font-medium px-3 py-1.5 rounded-full hover:bg-white transition-colors border border-transparent hover:border-slate-200">Bearbeiten</button>
                                <button onClick={() => deleteRoom(room.id)} className="text-slate-500 hover:text-red-600 text-sm font-medium px-3 py-1.5 rounded-full hover:bg-white transition-colors border border-transparent hover:border-slate-200">Löschen</button>
                            </div>
                        </div>
                        <div className="p-6">
                            <div className="flex justify-between items-center mb-4">
                                <h5 className="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-2">
                                    <span className="w-2 h-2 rounded-full bg-slate-300"></span>
                                    Verbraucher & Zähler
                                </h5>
                                <button onClick={() => handleNewConsumer(room.id)} className="text-xs font-bold text-rose-600 hover:text-rose-700 uppercase tracking-wide border border-rose-100 bg-rose-50 hover:bg-rose-100 px-3 py-1.5 rounded-full transition-colors flex items-center gap-1">
                                    <Plus className="w-3 h-3" />
                                    Zähler hinzufügen
                                </button>
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {room.verbraucher && room.verbraucher.map(consumer => (
                                    <div key={consumer.id} className="group/consumer border border-slate-100 rounded-lg p-4 hover:border-rose-200 hover:shadow-sm transition-all bg-white relative">
                                        <div className="flex justify-between items-start mb-3">
                                            <div className="flex items-center gap-3">
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 border ${consumer.verbrauchsart === 'WASSER' ? 'bg-slate-50 text-slate-600 border-slate-200' :
                                                        consumer.verbrauchsart === 'STROM' ? 'bg-yellow-50 text-yellow-600 border-yellow-100' :
                                                            consumer.verbrauchsart === 'HEIZUNG' ? 'bg-orange-50 text-orange-600 border-orange-100' :
                                                                'bg-slate-50 text-slate-600 border-slate-100'
                                                    }`}>
                                                    <span className="text-xs font-bold">{consumer.verbrauchsart ? consumer.verbrauchsart[0] : '?'}</span>
                                                </div>
                                                <div>
                                                    <div className="font-semibold text-slate-900 text-sm">{consumer.name}</div>
                                                    <div className="text-xs text-slate-500 font-mono mt-0.5 flex items-center gap-1">
                                                        <span className="bg-slate-100 px-1 rounded">{consumer.seriennummer || 'Keine S/N'}</span>
                                                        <span>&bull;</span>
                                                        <span className="font-bold text-slate-600">{consumer.einheit}</span>
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="flex gap-1 opacity-0 group-hover/consumer:opacity-100 transition-opacity absolute top-2 right-2 bg-white shadow-sm border border-slate-100 rounded p-1">
                                                <button aria-label="Zähler bearbeiten" onClick={() => handleEditConsumer(consumer)} className="p-1.5 text-slate-400 hover:text-rose-600 rounded hover:bg-rose-50"><Pencil className="w-4 h-4" /></button>
                                                <button aria-label="Zähler löschen" onClick={() => deleteConsumer(consumer.id)} className="p-1.5 text-slate-400 hover:text-red-600 rounded hover:bg-red-50"><Trash2 className="w-4 h-4" /></button>
                                            </div>
                                        </div>

                                        <div className="bg-slate-50 rounded-lg p-3 border border-slate-100">
                                            <div className="flex justify-between items-center mb-2">
                                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Erfasste Stände</span>
                                                <button onClick={() => handleNewMeter(consumer.id)} className="text-xs text-rose-600 font-bold hover:text-rose-700 hover:underline flex items-center gap-0.5">
                                                    <Plus className="w-3 h-3" />
                                                    Stand
                                                </button>
                                            </div>
                                            {consumer.zaehlerstaende && consumer.zaehlerstaende.length > 0 ? (
                                                <div className="space-y-1.5">
                                                    {consumer.zaehlerstaende.sort((a, b) => b.abrechnungsJahr - a.abrechnungsJahr).slice(0, 3).map(m => (
                                                        <div key={m.id} className="flex items-center justify-between text-xs group/meter hover:bg-white hover:shadow-sm p-1 rounded transition-colors cursor-default">
                                                            <div className="flex items-center gap-2">
                                                                <span className="font-bold text-slate-400 w-8">{m.abrechnungsJahr}</span>
                                                                <span className="bg-white border border-slate-200 px-1.5 py-0.5 rounded font-mono text-slate-900 font-medium">{formatDecimalInput(m.stand)}</span>
                                                            </div>
                                                            <div className="flex gap-2">
                                                                {m.verbrauch != null && <span className="text-slate-500 text-[10px] bg-slate-100 px-1 rounded flex items-center">Ø {formatDecimalInput(m.verbrauch)}</span>}
                                                                <div className="flex gap-1 opacity-0 group-hover/meter:opacity-100 transition-opacity">
                                                                    <button aria-label="Zählerstand bearbeiten" onClick={() => handleEditMeter(m)} className="text-slate-400 hover:text-rose-600"><Pencil className="w-3 h-3" /></button>
                                                                    <button aria-label="Zählerstand löschen" onClick={() => deleteMeter(m.id)} className="text-slate-400 hover:text-red-600"><Trash2 className="w-3 h-3" /></button>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    ))}
                                                    {consumer.zaehlerstaende.length > 3 && <div className="text-[10px] text-center text-slate-400 mt-1 hover:text-slate-600 cursor-pointer">...und {consumer.zaehlerstaende.length - 3} weitere anzeigen</div>}
                                                </div>
                                            ) : (
                                                <div className="text-[10px] text-slate-400 italic text-center py-2 bg-slate-100/50 rounded border border-dashed border-slate-200">Keine Stände erfasst</div>
                                            )}
                                        </div>
                                    </div>
                                ))}
                                {(!room.verbraucher || room.verbraucher.length === 0) && (
                                    <div className="col-span-1 md:col-span-2 text-center py-8 border-2 border-dashed border-slate-100 rounded-lg bg-slate-50/30">
                                        <p className="text-sm text-slate-400">Keine Verbraucher oder Zähler in diesem Raum.</p>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
                {raeume.length === 0 && (
                    <div className="text-center py-12 text-slate-400 bg-white rounded-xl border border-dashed border-slate-300">
                        <div className="mb-4 text-slate-200">
                            <Building2 className="w-16 h-16 mx-auto" />
                        </div>
                        <h3 className="text-lg font-medium text-slate-900">Noch keine Räume angelegt</h3>
                        <p className="text-sm text-slate-500 mt-1 mb-4">Erstellen Sie den ersten Raum, um Zähler und Flächen zu verwalten.</p>
                        <button onClick={handleNewRoom} className="text-rose-600 font-medium hover:text-rose-700 hover:underline">
                            + Jetzt Raum erstellen
                        </button>
                    </div>
                )}
            </div>

            {/* Room Modal */}
            <Dialog open={roomModalOpen} onOpenChange={open => { if (!saving) setRoomModalOpen(open); }} aria-label="Raum bearbeiten" className="w-full max-w-xl">
                <DialogContent className="overflow-y-auto">
                    <DialogHeader><DialogTitle>{editingRoom?.id === 0 ? 'Neuer Raum' : 'Raum bearbeiten'}</DialogTitle></DialogHeader>
                    {editingRoom && (
                        <form noValidate onSubmit={saveRoom} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input aria-label="Bezeichnung" value={editingRoom.name} onChange={e => setEditingRoom({ ...editingRoom, name: e.target.value })} placeholder="z.B. Wohnzimmer, Küche" required /></div>
                            <div className="space-y-2"><Label>Beschreibung</Label><Input aria-label="Beschreibung" value={editingRoom.beschreibung} onChange={e => setEditingRoom({ ...editingRoom, beschreibung: e.target.value })} placeholder="Optional, z.B. EG Links" /></div>
                            <div className="space-y-2"><Label>Fläche (m²)</Label><DecimalInput aria-label="Fläche (m²)" required value={flaeche} onChange={setFlaeche} /></div>
                            <DialogFooter><button type="button" onClick={() => setRoomModalOpen(false)} disabled={saving} className="border border-rose-300 text-rose-700 hover:bg-rose-50 px-4 py-2 rounded-lg text-sm">Abbrechen</button><button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>

            {/* Consumer Modal */}
            <Dialog open={consumerModalOpen} onOpenChange={open => { if (!saving) setConsumerModalOpen(open); }} aria-label="Zähler bearbeiten" className="w-full max-w-xl">
                <DialogContent className="overflow-y-auto">
                    <DialogHeader><DialogTitle>Verbraucher / Zähler</DialogTitle></DialogHeader>
                    {editingConsumer && (
                        <form noValidate onSubmit={saveConsumer} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input aria-label="Bezeichnung" value={editingConsumer.name} onChange={e => setEditingConsumer({ ...editingConsumer, name: e.target.value })} placeholder="z.B. Hauptwasserzähler" required /></div>
                            <div className="space-y-2"><Label>Art</Label>
                                <Select aria-label="Art" value={editingConsumer.verbrauchsart} onChange={val => setEditingConsumer({ ...editingConsumer, verbrauchsart: val as Verbraucher['verbrauchsart'] })} options={[{ value: 'WASSER', label: 'Wasser' }, { value: 'STROM', label: 'Strom' }, { value: 'HEIZUNG', label: 'Heizung' }, { value: 'GAS', label: 'Gas' }, { value: 'SONSTIGES', label: 'Sonstiges' }]} />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2"><Label>Einheit</Label><Input aria-label="Einheit" value={editingConsumer.einheit} onChange={e => setEditingConsumer({ ...editingConsumer, einheit: e.target.value })} placeholder="m³, kWh..." /></div>
                                <div className="space-y-2"><Label>Seriennummer</Label><Input aria-label="Seriennummer" value={editingConsumer.seriennummer} onChange={e => setEditingConsumer({ ...editingConsumer, seriennummer: e.target.value })} /></div>
                            </div>
                            <DialogFooter><button type="button" onClick={() => setConsumerModalOpen(false)} disabled={saving} className="border border-rose-300 text-rose-700 hover:bg-rose-50 px-4 py-2 rounded-lg text-sm">Abbrechen</button><button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>

            {/* Meter Modal */}
            <Dialog open={meterModalOpen} onOpenChange={open => { if (!saving) setMeterModalOpen(open); }} aria-label="Zählerstand erfassen" className="w-full max-w-xl">
                <DialogContent className="overflow-y-auto">
                    <DialogHeader><DialogTitle>Zählerstand erfassen</DialogTitle></DialogHeader>
                    {editingMeter && (
                        <form noValidate onSubmit={saveMeter} className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2"><Label>Abrechnungsjahr</Label><DecimalInput aria-label="Abrechnungsjahr" integer required min={1} max={9999} value={meterDraft.jahr} onChange={jahr => setMeterDraft({ ...meterDraft, jahr })} /></div>
                                <div className="space-y-2"><Label>Stichtag</Label><DatePicker aria-label="Stichtag" required value={editingMeter.stichtag} onChange={value => setEditingMeter({ ...editingMeter, stichtag: value })} placeholder="Stichtag wählen" /></div>
                            </div>
                            <div className="space-y-2">
                                <Label>Zählerstand</Label>
                                <DecimalInput aria-label="Zählerstand" value={meterDraft.stand} onChange={stand => setMeterDraft({ ...meterDraft, stand })} required className="text-lg font-semibold" />
                            </div>
                            <div className="space-y-2 p-3 bg-slate-50 rounded-lg border border-slate-100">
                                <Label className="text-slate-700">Verbrauch (optional)</Label>
                                <div className="text-xs text-slate-500 mb-2">Wird normalerweise automatisch aus der Differenz zum Vorjahr berechnet. Nur ausfüllen, wenn ein abweichender Verbrauch eingetragen werden soll.</div>
                                <DecimalInput aria-label="Verbrauch (optional)" value={meterDraft.verbrauch} onChange={verbrauch => setMeterDraft({ ...meterDraft, verbrauch })} placeholder="Automatisch berechnet" />
                            </div>
                            <div className="space-y-2"><Label>Kommentar</Label><Input aria-label="Kommentar" value={editingMeter.kommentar} onChange={e => setEditingMeter({ ...editingMeter, kommentar: e.target.value })} placeholder="z.B. Zählerwechsel" /></div>
                            <DialogFooter><button type="button" onClick={() => setMeterModalOpen(false)} disabled={saving} className="border border-rose-300 text-rose-700 hover:bg-rose-50 px-4 py-2 rounded-lg text-sm">Abbrechen</button><button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
