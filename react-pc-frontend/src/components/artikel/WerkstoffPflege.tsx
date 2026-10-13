import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pencil, Plus, Save, Search, Trash2, X } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import { useConfirm } from '../ui/confirm-dialog';
import { DecimalInput } from '../ui/decimal-input';
import { InfoButton } from '../ui/info-button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { useToast } from '../ui/toast';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { parseErrorMessage } from '../settings/settingsApi';

interface Werkstoff {
    id: number;
    name: string;
    anzeigename: string | null;
    werkstattname: string | null;
    dichte: number | null;
    werkstoffnorm: string | null;
    verzinkungsgeeignet: boolean;
    pulverbeschichtungsgeeignet: boolean;
    beschichtungshinweis: string | null;
    artikelAnzahl: number;
}

interface Formular {
    id: number | null;
    name: string;
    anzeigename: string;
    werkstattname: string;
    dichte: string;
    werkstoffnorm: string;
    verzinkungsgeeignet: boolean;
    pulverbeschichtungsgeeignet: boolean;
    beschichtungshinweis: string;
}

const LEER: Formular = {
    id: null, name: '', anzeigename: '', werkstattname: '', dichte: '', werkstoffnorm: '', verzinkungsgeeignet: false, pulverbeschichtungsgeeignet: false, beschichtungshinweis: '',
};

const BASIS = '/api/werkstoffe';
const json = { 'Content-Type': 'application/json' };

const ausWerkstoff = (w: Werkstoff): Formular => ({
    id: w.id, name: w.name, anzeigename: w.anzeigename ?? '', werkstattname: w.werkstattname ?? '', dichte: w.dichte == null ? '' : formatDecimalInput(w.dichte),
    werkstoffnorm: w.werkstoffnorm ?? '', verzinkungsgeeignet: w.verzinkungsgeeignet, pulverbeschichtungsgeeignet: w.pulverbeschichtungsgeeignet,
    beschichtungshinweis: w.beschichtungshinweis ?? '',
});

interface Props {
    /** Wird gemeldet, sobald sich an den Werkstoffen etwas geändert hat (angelegt, gespeichert, gelöscht), damit die Umgebung ihre Listen neu laden kann. */
    onGeaendert?: () => void;
}

/**
 * Werkstoffe pflegen: die Auswahlliste „Werkstoff“ am Artikel (Stahl, Edelstahl, Aluminium und was sonst verarbeitet wird).
 * Gedacht für ein Fenster in der Artikelübersicht. Die Eignung für Verzinken und Pulvern gilt für alle Artikel aus diesem Werkstoff.
 */
export function WerkstoffPflege({ onGeaendert }: Props = {}) {
    const toast = useToast();
    const confirm = useConfirm();
    const [liste, setListe] = useState<Werkstoff[] | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [suche, setSuche] = useState('');
    const [form, setForm] = useState<Formular | null>(null);
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        try {
            const res = await fetch(BASIS);
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Werkstoffe konnten nicht geladen werden.'));
            setListe(await res.json() as Werkstoff[]);
            setLadefehler('');
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Die Werkstoffe konnten nicht geladen werden.';
            setLadefehler(text);
            toast.error(text);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const sichtbar = useMemo(() => {
        const q = suche.trim().toLowerCase();
        return (liste ?? []).filter(w => !q || [w.name, w.anzeigename, w.werkstattname, w.werkstoffnorm].filter(Boolean).join(' ').toLowerCase().includes(q));
    }, [liste, suche]);

    const setze = (teil: Partial<Formular>) => setForm(f => (f ? { ...f, ...teil } : f));
    const neu = () => { setForm({ ...LEER }); setFehler(''); };

    const speichern = async () => {
        if (!form) return;
        const dichte = validateDecimalInput(form.dichte, { label: 'Dichte', min: 0.1, max: 25 });
        if (!dichte.valid) { setFehler(dichte.message); toast.error(dichte.message); return; }
        setSpeichert(true);
        setFehler('');
        try {
            const res = await fetch(form.id == null ? BASIS : `${BASIS}/${encodeURIComponent(String(form.id))}`, {
                method: form.id == null ? 'POST' : 'PUT', headers: json,
                body: JSON.stringify({
                    name: form.name, anzeigename: form.anzeigename, werkstattname: form.werkstattname, dichte: dichte.value, werkstoffnorm: form.werkstoffnorm,
                    verzinkungsgeeignet: form.verzinkungsgeeignet, pulverbeschichtungsgeeignet: form.pulverbeschichtungsgeeignet, beschichtungshinweis: form.beschichtungshinweis,
                }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Werkstoff konnte nicht gespeichert werden.'));
            toast.success(form.id == null ? 'Der Werkstoff wurde angelegt.' : 'Der Werkstoff wurde gespeichert.');
            setForm(null);
            onGeaendert?.();
            await laden();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Der Werkstoff konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    const loeschen = async (w: Werkstoff) => {
        const ok = await confirm({ title: 'Werkstoff löschen?', message: `„${w.anzeigename || w.name}“ wird gelöscht. Das lässt sich nicht rückgängig machen.`, confirmLabel: 'Löschen', variant: 'danger' });
        if (!ok) return;
        try {
            const res = await fetch(`${BASIS}/${encodeURIComponent(String(w.id))}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Werkstoff konnte nicht gelöscht werden.'));
            toast.success('Der Werkstoff wurde gelöscht.');
            if (form?.id === w.id) setForm(null);
            onGeaendert?.();
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Werkstoff konnte nicht gelöscht werden.');
        }
    };

    return (
        <div className="space-y-4">
            {!form && (
                <div className="flex justify-end">
                    <Button size="sm" onClick={neu}><Plus className="mr-2 h-4 w-4" aria-hidden="true" />Neuer Werkstoff</Button>
                </div>
            )}
            {ladefehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{ladefehler}</p>}

            {form && (
                <Card className="border-rose-100 p-6 shadow-lg" data-testid="werkstoff-formular">
                    <div className="mb-4 flex items-center justify-between">
                        <h3 className="text-lg font-semibold text-slate-900">{form.id == null ? 'Neuer Werkstoff' : 'Werkstoff ändern'}</h3>
                        <Button variant="ghost" size="sm" onClick={() => setForm(null)} aria-label="Abbrechen"><X className="h-4 w-4" aria-hidden="true" /></Button>
                    </div>
                    <div className="grid gap-4 md:grid-cols-2">
                        <div>
                            <Label htmlFor="ws-name">Name (technisch) *</Label>
                            <Input id="ws-name" value={form.name} onChange={e => setze({ name: e.target.value })} placeholder="S235JR oder 1.4301" maxLength={255} />
                            <p className="mt-1 text-xs text-slate-500">Beim Artikel-Import erkennt das Programm den Werkstoff an diesem Namen wieder.</p>
                        </div>
                        <div>
                            <Label htmlFor="ws-anzeige">Anzeigename</Label>
                            <Input id="ws-anzeige" value={form.anzeigename} onChange={e => setze({ anzeigename: e.target.value })} placeholder="Baustahl S235JR" maxLength={255} />
                        </div>
                        <div>
                            <Label htmlFor="ws-werkstatt">Werkstattname</Label>
                            <Input id="ws-werkstatt" value={form.werkstattname} onChange={e => setze({ werkstattname: e.target.value })} placeholder="V2A, Schwarzstahl" maxLength={255} />
                            <p className="mt-1 text-xs text-slate-500">So sagt man in der Werkstatt dazu; die Artikelsuche findet den Werkstoff auch darüber.</p>
                        </div>
                        <div>
                            <Label htmlFor="ws-norm">Norm</Label>
                            <Input id="ws-norm" value={form.werkstoffnorm} onChange={e => setze({ werkstoffnorm: e.target.value })} placeholder="EN 10025-2" maxLength={255} />
                        </div>
                        <div>
                            <div className="flex items-center gap-1">
                                <span className="text-sm font-medium text-slate-700">Dichte (kg/dm³)</span>
                                <InfoButton titel="Dichte">
                                    <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                    <p>Die Dichte wird beim Werkstoff gespeichert. Das Programm rechnet damit im Moment noch nichts aus.</p>
                                    <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                    <p>Sie steht als Grundlage bereit, um später Gewicht aus den Maßen zu berechnen. An bestehenden Artikeln, Preisen und Angeboten ändert sich nichts.</p>
                                </InfoButton>
                            </div>
                            <DecimalInput id="ws-dichte" aria-label="Dichte (kg/dm³)" value={form.dichte} onChange={dichte => setze({ dichte })} min={0.1} max={25} placeholder="7,85" />
                            <p className="mt-1 text-xs text-slate-500">Stahl 7,85, Edelstahl 7,9, Aluminium 2,7.</p>
                        </div>
                        <fieldset>
                            <legend className="mb-1 flex items-center gap-1 text-sm font-medium text-slate-700">
                                Eignung für die Oberfläche
                                <InfoButton titel="Eignung für die Oberfläche">
                                    <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                    <p>Alle Artikel aus diesem Werkstoff gelten sofort als verzinkbar bzw. pulverbeschichtbar.</p>
                                    <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                    <p>Die Eignung erscheint auf der Artikelseite und in den Filtern „verzinkbar“ und „pulverbeschichtbar“ der Artikelsuche. Preise und bestehende Angebote ändern sich nicht.</p>
                                </InfoButton>
                            </legend>
                            <label className="flex items-center gap-2 text-sm text-slate-700">
                                <input type="checkbox" checked={form.verzinkungsgeeignet} onChange={e => setze({ verzinkungsgeeignet: e.target.checked })}
                                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                                Feuerverzinken geht
                            </label>
                            <label className="mt-1 flex items-center gap-2 text-sm text-slate-700">
                                <input type="checkbox" checked={form.pulverbeschichtungsgeeignet} onChange={e => setze({ pulverbeschichtungsgeeignet: e.target.checked })}
                                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                                Pulverbeschichten geht
                            </label>
                            <p className="mt-1 text-xs text-slate-500">Gilt für alle Artikel aus diesem Werkstoff.</p>
                        </fieldset>
                        <div className="md:col-span-2">
                            <Label htmlFor="ws-hinweis">Hinweis zur Beschichtung</Label>
                            <Input id="ws-hinweis" value={form.beschichtungshinweis} onChange={e => setze({ beschichtungshinweis: e.target.value })} maxLength={255}
                                placeholder="Zum Beispiel: Edelstahl wird nicht verzinkt" />
                        </div>
                    </div>
                    {fehler && <p role="alert" className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                    <div className="mt-4 flex gap-2">
                        <Button onClick={() => void speichern()} disabled={speichert || !form.name.trim()} title={form.name.trim() ? undefined : 'Bitte zuerst einen Namen eingeben'}><Save className="mr-2 h-4 w-4" aria-hidden="true" />Speichern</Button>
                        <Button variant="outline" onClick={() => setForm(null)}>Abbrechen</Button>
                    </div>
                </Card>
            )}

            <Card className="p-6">
                <div className="relative mb-4 w-full sm:w-80">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <Input value={suche} onChange={e => setSuche(e.target.value)} placeholder="Werkstoff suchen" aria-label="Werkstoff suchen" className="pl-9" />
                </div>
                {!liste && !ladefehler && <p className="text-sm text-slate-500">Die Werkstoffe werden geladen …</p>}
                {liste && sichtbar.length === 0 && <p className="text-sm text-slate-500">{liste.length === 0 ? 'Noch kein Werkstoff angelegt.' : 'Kein Werkstoff gefunden.'}</p>}
                {sichtbar.length > 0 && (
                    <div className="overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-sm" data-testid="werkstoff-liste">
                            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                                <tr>
                                    <th scope="col" className="px-3 py-2 font-medium">Werkstoff</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Werkstattname</th>
                                    <th scope="col" className="px-3 py-2 text-right font-medium">Dichte</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Oberfläche</th>
                                    <th scope="col" className="px-3 py-2 text-right font-medium">Artikel</th>
                                    <th scope="col" className="px-3 py-2"><span className="sr-only">Aktionen</span></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {sichtbar.map(w => (
                                    <tr key={w.id}>
                                        <th scope="row" className="px-3 py-2 text-left font-medium text-slate-800">
                                            {w.anzeigename || w.name}
                                            {w.anzeigename && <span className="block font-mono text-xs font-normal text-slate-500">{w.name}</span>}
                                        </th>
                                        <td className="px-3 py-2 text-slate-700">{w.werkstattname || '–'}</td>
                                        <td className="px-3 py-2 text-right text-slate-700">{w.dichte == null ? '–' : `${formatDecimalInput(w.dichte)} kg/dm³`}</td>
                                        <td className="px-3 py-2 text-slate-700">{[w.verzinkungsgeeignet && 'verzinken', w.pulverbeschichtungsgeeignet && 'pulvern'].filter(Boolean).join(', ') || '–'}</td>
                                        <td className="px-3 py-2 text-right text-slate-700">{w.artikelAnzahl}</td>
                                        <td className="px-3 py-2 text-right whitespace-nowrap">
                                            <Button variant="ghost" size="sm" onClick={() => { setForm(ausWerkstoff(w)); setFehler(''); }} aria-label={`${w.anzeigename || w.name} ändern`}><Pencil className="h-4 w-4" aria-hidden="true" /></Button>
                                            <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-50" onClick={() => void loeschen(w)} aria-label={`${w.anzeigename || w.name} löschen`}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </Card>
        </div>
    );
}
