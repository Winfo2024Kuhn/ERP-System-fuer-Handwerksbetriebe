import { useEffect, useState, type FormEvent } from 'react';
import { type Raum, type Verbraucher, type Zaehlerstand } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Input } from '../ui/input';
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
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // --- Room Handlers ---
    const handleNewRoom = () => {
        setEditingRoom({ id: 0, mietobjektId, name: '', beschreibung: '', flaecheQuadratmeter: 0 });
        setRoomModalOpen(true);
    };
    const handleEditRoom = (r: Raum) => {
        setEditingRoom(r);
        setRoomModalOpen(true);
    };
    const saveRoom = async (e: FormEvent) => {
        e.preventDefault();
        if (!editingRoom) return;
        try {
            if (editingRoom.id === 0) await MietabrechnungService.createRaum(mietobjektId, editingRoom);
            else await MietabrechnungService.updateRaum(editingRoom.id, editingRoom);
            setRoomModalOpen(false);
            loadData();
        } catch (err) { toast.error('Fehler beim Speichern'); }
    };
    const deleteRoom = async (id: number) => {
        if (!await confirmDialog({ title: 'Raum löschen', message: 'Raum und alle Verbraucher löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try { await MietabrechnungService.deleteRaum(id); loadData(); } catch (err) { toast.error('Fehler'); }
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
        if (!editingConsumer) return;
        try {
            if (editingConsumer.id === 0) await MietabrechnungService.createVerbraucher(editingConsumer.raumId, editingConsumer);
            else await MietabrechnungService.updateVerbraucher(editingConsumer.id, editingConsumer);
            setConsumerModalOpen(false);
            loadData();
        } catch (err) { toast.error('Fehler'); }
    };
    const deleteConsumer = async (id: number) => {
        if (!await confirmDialog({ title: 'Verbraucher löschen', message: 'Verbraucher löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try { await MietabrechnungService.deleteVerbraucher(id); loadData(); } catch (err) { toast.error('Fehler'); }
    };

    // --- Meter Handlers ---
    const handleNewMeter = (consumerId: number) => {
        setActiveConsumerId(consumerId);
        setEditingMeter({ id: 0, verbrauchsgegenstandId: consumerId, abrechnungsJahr: new Date().getFullYear(), stichtag: `${new Date().getFullYear()}-12-31`, stand: 0, kommentar: '' });
        setMeterModalOpen(true);
    };
    const saveMeter = async (e: FormEvent) => {
        e.preventDefault();
        const cid = activeConsumerId || editingMeter?.verbrauchsgegenstandId;
        if (!editingMeter || !cid) return;

        try {
            // Ensure verbrauchsgegenstandId is set correctly
            const meterToSave = { ...editingMeter, verbrauchsgegenstandId: cid };

            if (editingMeter.id === 0) await MietabrechnungService.createZaehlerstand(cid, meterToSave);
            else await MietabrechnungService.updateZaehlerstand(editingMeter.id, meterToSave);

            setMeterModalOpen(false);
            loadData();
        } catch (err: any) {
            console.error(err);
            toast.error('Fehler beim Speichern des Zählerstands: ' + (err.message || 'Unbekannt'));
        }
    };

    const handleEditMeter = (m: Zaehlerstand) => {
        setEditingMeter(m);
        setActiveConsumerId(m.verbrauchsgegenstandId || null);
        setMeterModalOpen(true);
    };

    const deleteMeter = async (id: number) => {
        if (!await confirmDialog({ title: 'Zählerstand löschen', message: 'Zählerstand löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await MietabrechnungService.deleteZaehlerstand(id);
            loadData();
        } catch (err) { toast.error('Fehler beim Löschen'); }
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
                                    <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5" /></svg>
                                    {room.name}
                                </h4>
                                <div className="flex items-center gap-3 text-sm text-slate-500 mt-0.5">
                                    <span className="bg-white px-2 py-0.5 rounded border border-slate-200 shadow-sm font-mono text-xs">{room.flaecheQuadratmeter} m²</span>
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
                                    <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                                    Zähler hinzufügen
                                </button>
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {room.verbraucher && room.verbraucher.map(consumer => (
                                    <div key={consumer.id} className="group/consumer border border-slate-100 rounded-lg p-4 hover:border-rose-200 hover:shadow-sm transition-all bg-white relative">
                                        <div className="flex justify-between items-start mb-3">
                                            <div className="flex items-center gap-3">
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 border ${consumer.verbrauchsart === 'WASSER' ? 'bg-blue-50 text-blue-600 border-blue-100' :
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
                                                <button onClick={() => handleEditConsumer(consumer)} className="p-1.5 text-slate-400 hover:text-rose-600 rounded hover:bg-rose-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg></button>
                                                <button onClick={() => deleteConsumer(consumer.id)} className="p-1.5 text-slate-400 hover:text-red-600 rounded hover:bg-red-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg></button>
                                            </div>
                                        </div>

                                        <div className="bg-slate-50 rounded-lg p-3 border border-slate-100">
                                            <div className="flex justify-between items-center mb-2">
                                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Erfasste Stände</span>
                                                <button onClick={() => handleNewMeter(consumer.id)} className="text-xs text-rose-600 font-bold hover:text-rose-700 hover:underline flex items-center gap-0.5">
                                                    <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                                                    Stand
                                                </button>
                                            </div>
                                            {consumer.zaehlerstaende && consumer.zaehlerstaende.length > 0 ? (
                                                <div className="space-y-1.5">
                                                    {consumer.zaehlerstaende.sort((a, b) => b.abrechnungsJahr - a.abrechnungsJahr).slice(0, 3).map(m => (
                                                        <div key={m.id} className="flex items-center justify-between text-xs group/meter hover:bg-white hover:shadow-sm p-1 rounded transition-colors cursor-default">
                                                            <div className="flex items-center gap-2">
                                                                <span className="font-bold text-slate-400 w-8">{m.abrechnungsJahr}</span>
                                                                <span className="bg-white border border-slate-200 px-1.5 py-0.5 rounded font-mono text-slate-900 font-medium">{m.stand}</span>
                                                            </div>
                                                            <div className="flex gap-2">
                                                                {m.verbrauch != null && <span className="text-slate-500 text-[10px] bg-slate-100 px-1 rounded flex items-center">Ø {m.verbrauch}</span>}
                                                                <div className="flex gap-1 opacity-0 group-hover/meter:opacity-100 transition-opacity">
                                                                    <button onClick={() => handleEditMeter(m)} className="text-slate-400 hover:text-rose-600"><svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg></button>
                                                                    <button onClick={() => deleteMeter(m.id)} className="text-slate-400 hover:text-red-600"><svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg></button>
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
                            <svg className="w-16 h-16 mx-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5" /></svg>
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
            <Dialog open={roomModalOpen} onOpenChange={setRoomModalOpen}>
                <DialogContent>
                    <DialogHeader><DialogTitle>{editingRoom?.id === 0 ? 'Neuer Raum' : 'Raum bearbeiten'}</DialogTitle></DialogHeader>
                    {editingRoom && (
                        <form onSubmit={saveRoom} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input value={editingRoom.name} onChange={e => setEditingRoom({ ...editingRoom, name: e.target.value })} placeholder="z.B. Wohnzimmer, Küche" required /></div>
                            <div className="space-y-2"><Label>Beschreibung</Label><Input value={editingRoom.beschreibung} onChange={e => setEditingRoom({ ...editingRoom, beschreibung: e.target.value })} placeholder="Optional, z.B. EG Links" /></div>
                            <div className="space-y-2"><Label>Fläche (m²)</Label><Input type="number" step="0.01" value={editingRoom.flaecheQuadratmeter} onChange={e => setEditingRoom({ ...editingRoom, flaecheQuadratmeter: parseFloat(e.target.value) || 0 })} /></div>
                            <DialogFooter><button type="button" onClick={() => setRoomModalOpen(false)} className="btn-secondary">Abbrechen</button><button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>

            {/* Consumer Modal */}
            <Dialog open={consumerModalOpen} onOpenChange={setConsumerModalOpen}>
                <DialogContent>
                    <DialogHeader><DialogTitle>Verbraucher / Zähler</DialogTitle></DialogHeader>
                    {editingConsumer && (
                        <form onSubmit={saveConsumer} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input value={editingConsumer.name} onChange={e => setEditingConsumer({ ...editingConsumer, name: e.target.value })} placeholder="z.B. Hauptwasserzähler" required /></div>
                            <div className="space-y-2"><Label>Art</Label>
                                <Select value={editingConsumer.verbrauchsart} onChange={val => setEditingConsumer({ ...editingConsumer, verbrauchsart: val as any })} options={[{ value: 'WASSER', label: 'Wasser' }, { value: 'STROM', label: 'Strom' }, { value: 'HEIZUNG', label: 'Heizung' }, { value: 'GAS', label: 'Gas' }, { value: 'SONSTIGES', label: 'Sonstiges' }]} />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2"><Label>Einheit</Label><Input value={editingConsumer.einheit} onChange={e => setEditingConsumer({ ...editingConsumer, einheit: e.target.value })} placeholder="m³, kWh..." /></div>
                                <div className="space-y-2"><Label>Seriennummer</Label><Input value={editingConsumer.seriennummer} onChange={e => setEditingConsumer({ ...editingConsumer, seriennummer: e.target.value })} /></div>
                            </div>
                            <DialogFooter><button type="button" onClick={() => setConsumerModalOpen(false)} className="btn-secondary">Abbrechen</button><button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>

            {/* Meter Modal */}
            <Dialog open={meterModalOpen} onOpenChange={setMeterModalOpen}>
                <DialogContent>
                    <DialogHeader><DialogTitle>Zählerstand erfassen</DialogTitle></DialogHeader>
                    {editingMeter && (
                        <form onSubmit={saveMeter} className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2"><Label>Abrechnungsjahr</Label><Input type="number" value={editingMeter.abrechnungsJahr} onChange={e => setEditingMeter({ ...editingMeter, abrechnungsJahr: parseInt(e.target.value) || 0 })} /></div>
                                <div className="space-y-2"><Label>Stichtag</Label><DatePicker value={editingMeter.stichtag} onChange={value => setEditingMeter({ ...editingMeter, stichtag: value })} placeholder="Stichtag wählen" /></div>
                            </div>
                            <div className="space-y-2">
                                <Label>Zählerstand ({editingMeter.stand})</Label>
                                <Input type="number" step="0.001" value={editingMeter.stand} onChange={e => setEditingMeter({ ...editingMeter, stand: parseFloat(e.target.value) || 0 })} required className="text-lg font-mono font-bold" />
                            </div>
                            <div className="space-y-2 p-3 bg-slate-50 rounded-lg border border-slate-100">
                                <Label className="text-slate-700">Verbrauch (Optional)</Label>
                                <div className="text-xs text-slate-500 mb-2">Wird normalerweise automatisch aus der Differenz zum Vorjahr berechnet. Nur ausfüllen, wenn manueller Override nötig ist.</div>
                                <Input type="number" step="0.001" value={editingMeter.verbrauch || ''} onChange={e => setEditingMeter({ ...editingMeter, verbrauch: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="Automatisch berechnet" />
                            </div>
                            <div className="space-y-2"><Label>Kommentar</Label><Input value={editingMeter.kommentar} onChange={e => setEditingMeter({ ...editingMeter, kommentar: e.target.value })} placeholder="z.B. Zählerwechsel" /></div>
                            <DialogFooter><button type="button" onClick={() => setMeterModalOpen(false)} className="btn-secondary">Abbrechen</button><button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
