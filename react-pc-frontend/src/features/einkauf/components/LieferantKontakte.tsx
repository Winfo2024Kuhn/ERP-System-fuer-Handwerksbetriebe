import { useCallback, useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { useToast } from '../../../components/ui/toast';

type Kontakt = { id: number | null; version: number; name: string | null; anrede: string | null; email: string; standardAnfrage: boolean; standardBestellung: boolean; aktiv: boolean };
const leer: Kontakt = { id: null, version: 0, name: '', anrede: '', email: '', standardAnfrage: false, standardBestellung: false, aktiv: true };

export function LieferantKontakte({ lieferantId, readOnly }: { lieferantId: number; readOnly: boolean }) {
  const toast = useToast();
  const [kontakte, setKontakte] = useState<Kontakt[]>([]);
  const [entwurf, setEntwurf] = useState<Kontakt>(leer);
  const [laedt, setLaedt] = useState(true);
  const [speichert, setSpeichert] = useState(false);
  const ladeKontakte = useCallback(async () => {
    setLaedt(true);
    try { const response = await fetch(`/api/lieferanten/${lieferantId}/einkauf-kontakte`); if (!response.ok) throw new Error('Einkaufskontakte konnten nicht geladen werden.'); setKontakte(await response.json() as Kontakt[]); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Einkaufskontakte konnten nicht geladen werden.'); }
    finally { setLaedt(false); }
  }, [lieferantId, toast]);
  useEffect(() => { void ladeKontakte(); }, [ladeKontakte]);
  const speichern = async () => {
    if (!entwurf.email.trim()) { toast.error('Bitte eine E-Mail-Adresse für den Einkauf eintragen.'); return; }
    setSpeichert(true);
    try {
      const path = `/api/lieferanten/${lieferantId}/einkauf-kontakte${entwurf.id ? `/${entwurf.id}` : ''}`;
      const response = await fetch(path, { method: entwurf.id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(entwurf) });
      if (!response.ok) throw new Error('Einkaufskontakt konnte nicht gespeichert werden.');
      setEntwurf(leer); toast.success('Einkaufskontakt gespeichert.'); await ladeKontakte();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Einkaufskontakt konnte nicht gespeichert werden.'); }
    finally { setSpeichert(false); }
  };
  return <section aria-labelledby="einkauf-kontakte-title" className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
    <div><h2 id="einkauf-kontakte-title" className="text-lg font-semibold text-slate-900">Einkaufskontakte</h2><p className="text-sm text-slate-600">Anfrage- und Bestelladressen getrennt von Rechnungs-E-Mails verwalten.</p></div>
    {laedt ? <p role="status">Einkaufskontakte werden geladen …</p> : kontakte.length ? <ul className="divide-y divide-slate-200">{kontakte.map(k => <li key={k.id} className="flex flex-wrap items-center justify-between gap-3 py-3"><div><p className="font-medium text-slate-900">{k.name || 'Allgemeiner Einkauf'}</p><p className="text-sm text-slate-600">{k.email}{k.anrede ? ` · ${k.anrede}` : ''}</p><p className="text-xs text-slate-500">{[k.standardAnfrage && 'Standard für Anfragen', k.standardBestellung && 'Standard für Bestellungen'].filter(Boolean).join(' · ') || 'Kein Standardkontakt'}</p></div>{!readOnly && <Button size="sm" variant="outline" onClick={() => setEntwurf(k)}>Bearbeiten</Button>}</li>)}</ul> : <p className="text-sm text-slate-600">Noch keine Einkaufskontakte eingetragen.</p>}
    {!readOnly && <div className="grid gap-3 border-t border-slate-200 pt-4 md:grid-cols-2">
      <div><Label htmlFor="ek-kontakt-name">Name (optional)</Label><Input id="ek-kontakt-name" value={entwurf.name ?? ''} onChange={e => setEntwurf({ ...entwurf, name: e.target.value })} /></div>
      <div><Label htmlFor="ek-kontakt-anrede">Anrede (optional)</Label><Input id="ek-kontakt-anrede" value={entwurf.anrede ?? ''} onChange={e => setEntwurf({ ...entwurf, anrede: e.target.value })} placeholder="Guten Tag" /></div>
      <div><Label htmlFor="ek-kontakt-email">E-Mail für den Einkauf *</Label><Input id="ek-kontakt-email" type="email" required value={entwurf.email} onChange={e => setEntwurf({ ...entwurf, email: e.target.value })} /></div>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={entwurf.standardAnfrage} onChange={e => setEntwurf({ ...entwurf, standardAnfrage: e.target.checked })} /> Standard für Anfragen</label>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={entwurf.standardBestellung} onChange={e => setEntwurf({ ...entwurf, standardBestellung: e.target.checked })} /> Standard für Bestellungen</label>
      <div className="flex gap-2 md:col-span-2"><Button size="sm" disabled={speichert} onClick={() => void speichern()}>{speichert ? 'Speichert …' : 'Kontakt speichern'}</Button>{entwurf.id && <Button size="sm" variant="outline" onClick={() => setEntwurf(leer)}>Abbrechen</Button>}</div>
    </div>}
  </section>;
}
