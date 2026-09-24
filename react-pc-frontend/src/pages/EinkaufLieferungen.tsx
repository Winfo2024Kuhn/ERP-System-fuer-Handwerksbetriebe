import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, FileText, Truck } from 'lucide-react';
import { Button } from '../components/ui/button';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { einkaufApi } from '../features/einkauf/api';
import type { BestellungUebersicht } from '../features/einkauf/types';
import type { Bestellmenge, Lieferung } from '../features/einkauf/bestellTypes';
import { LieferungDialog } from '../features/einkauf/components/LieferungDialog';
import { ZeugnisZuordnung } from '../features/einkauf/components/ZeugnisZuordnung';
import { Rechnungsabgleich } from '../features/einkauf/components/Rechnungsabgleich';

interface ZeugnisErwartung {
  id: number; version: number; art: string; status: string; grundlage: string; frist: string | null;
  dateiIds: number[]; chargeIds: number[]; materialFreigegeben: boolean;
  chargeStaende: Array<{ chargeId: number; status: string; version: number; materialFreigegeben: boolean }>;
}
interface LieferUebersicht { bestellung: BestellungUebersicht; mengen: Bestellmenge[]; lieferungen: Lieferung[]; zeugnisse: ZeugnisErwartung[] }
const datum = (value: string) => new Date(value).toLocaleDateString('de-DE');
const artText = (art: string) => ({ ZEUGNIS_2_1: 'Zeugnis 2.1', ZEUGNIS_2_2: 'Zeugnis 2.2', ZEUGNIS_3_1: 'Zeugnis 3.1', ZEUGNIS_3_2: 'Zeugnis 3.2', LEISTUNGSERKLAERUNG: 'Leistungserklärung', CE_NACHWEIS: 'CE-Nachweis' }[art] ?? art);

export default function EinkaufLieferungen() {
  const toast = useToast(); const [eintraege, setEintraege] = useState<LieferUebersicht[]>([]); const [laden, setLaden] = useState(true); const [fehler, setFehler] = useState('');
  const [aktualisierung, setAktualisierung] = useState(0);
  const [dialogBestellungId, setDialogBestellungId] = useState<number | null>(null);
  const [offeneBereiche, setOffeneBereiche] = useState<Record<number, 'zeugnisse' | 'rechnungen' | null>>({});
  useEffect(() => {
    let aktiv = true;
    einkaufApi.get<{ content: BestellungUebersicht[] }>('/api/einkauf/bestellungen?page=0&size=20')
      .then(async page => Promise.all(page.content.map(async bestellung => {
        const [mengen, lieferungen, zeugnisse] = await Promise.all([
          einkaufApi.get<Bestellmenge[]>(`/api/einkauf/bestellungen/${bestellung.id}/mengen`),
          einkaufApi.get<Lieferung[]>(`/api/einkauf/bestellungen/${bestellung.id}/lieferungen`),
          einkaufApi.get<ZeugnisErwartung[]>(`/api/einkauf/zeugnisse?bestellungId=${bestellung.id}`),
        ]);
        return { bestellung, mengen, lieferungen, zeugnisse };
      })))
      .then(rows => { if (aktiv) setEintraege(rows); })
      .catch(error => { if (aktiv) { const message = error instanceof Error ? error.message : 'Lieferungen konnten nicht geladen werden.'; setFehler(message); toast.error(message); } })
      .finally(() => { if (aktiv) setLaden(false); });
    return () => { aktiv = false; };
  }, [aktualisierung, toast]);
  return <PageLayout ribbonCategory="Einkauf" title="LIEFERUNGEN UND UNTERLAGEN" subtitle="Gelieferte Mengen, Chargen und erforderliche Nachweise gemeinsam prüfen."
    actions={<Button size="sm" variant="outline" onClick={() => { setLaden(true); setFehler(''); setAktualisierung(x => x + 1); }}>Aktualisieren</Button>}>
    <div className="space-y-4"><EinkaufNavigation active="lieferungen" />
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laden ? <p role="status">Lieferungen werden geladen …</p> : eintraege.length === 0 ? <section className="rounded-lg border border-slate-200 bg-white p-6 text-center text-slate-600">Noch keine Bestellungen vorhanden.</section> : <div className="space-y-4">{eintraege.map(({ bestellung, mengen, lieferungen, zeugnisse }) => {
        const offeneZeugnisse = zeugnisse.filter(z => z.dateiIds.length === 0 || z.chargeStaende.some(c => !c.materialFreigegeben));
        const materialFreigegeben = zeugnisse.length > 0 && zeugnisse.every(z => z.materialFreigegeben);
        const liefermenge = mengen.reduce((sum, stand) => sum + (stand.geliefert ?? 0), 0);
        const bestellmenge = mengen.reduce((sum, stand) => sum + (stand.bestellt ?? 0), 0);
        const offen = mengen.reduce((sum, stand) => sum + (stand.offen ?? 0), 0);
        return <article key={bestellung.id} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <header className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-sm font-semibold text-rose-600">{bestellung.nummer}</p><h2 className="font-semibold">Lieferung prüfen</h2></div><div className="flex flex-wrap gap-2"><Button size="sm" onClick={() => setDialogBestellungId(bestellung.id)}>Lieferung erfassen</Button><Link className="self-center text-sm font-medium text-rose-700 hover:underline" to={`/bestellungen/${bestellung.id}`}>Bestellung öffnen</Link></div></header>
          <div className="mt-3 grid gap-3 sm:grid-cols-3"><p className="rounded-lg bg-slate-50 p-3 text-sm"><Truck className="mr-2 inline h-4 w-4 text-slate-600" />{liefermenge.toLocaleString('de-DE')} von {bestellmenge.toLocaleString('de-DE')} geliefert · {offen.toLocaleString('de-DE')} offen</p><p className="rounded-lg bg-slate-50 p-3 text-sm"><FileText className="mr-2 inline h-4 w-4 text-slate-600" />{offeneZeugnisse.length ? `${offeneZeugnisse.length} Nachweise offen` : 'Nachweise vollständig'}</p><p className={`rounded-lg p-3 text-sm ${materialFreigegeben ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-900'}`}><AlertTriangle className="mr-2 inline h-4 w-4" />{materialFreigegeben ? 'Material freigegeben' : 'Material nicht freigegeben'}</p></div>
          <section className="mt-4"><h3 className="text-sm font-semibold text-slate-800">Eingänge und Chargen</h3>{lieferungen.length === 0 ? <p className="mt-1 text-sm text-slate-600">Noch keine Lieferung erfasst.</p> : <ul className="mt-2 divide-y divide-slate-100 rounded-md border border-slate-200">{lieferungen.flatMap(batch => batch.positionen.map(pos => <li key={`${batch.id}-${pos.id}`} className="flex flex-wrap justify-between gap-2 px-3 py-2 text-sm"><span>{datum(batch.eingang)} · {pos.menge.toLocaleString('de-DE')} · Charge {pos.chargen.map(c => c.kennung ?? `#${c.id}`).join(', ') || pos.charge || 'nicht angegeben'}{(pos.chargen.map(c => c.schmelznummer).filter(Boolean).join(', ') || pos.schmelznummer) ? ` · Schmelze ${pos.chargen.map(c => c.schmelznummer).filter(Boolean).join(', ') || pos.schmelznummer}` : ''}</span><span className="text-slate-600">Position {pos.bestellPositionId} · Bedarf {pos.projektAnteile.map(a => a.bedarfId).join(', ') || 'nicht zugeordnet'}</span></li>))}</ul>}</section>
          {offeneZeugnisse.length > 0 && <ul className="mt-2 space-y-1 text-sm text-amber-900">{offeneZeugnisse.map(z => <li key={z.id}>{artText(z.art)} fehlt · {z.grundlage}{z.frist ? ` · fällig ${datum(z.frist)}` : ''}</li>)}</ul>}
          <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
            <Button type="button" size="sm" variant="outline" onClick={() => setOffeneBereiche(current => ({ ...current, [bestellung.id]: current[bestellung.id] === 'zeugnisse' ? null : 'zeugnisse' }))} aria-expanded={offeneBereiche[bestellung.id] === 'zeugnisse'}>Zeugnisse und Prüfung {offeneBereiche[bestellung.id] === 'zeugnisse' ? 'ausblenden' : 'anzeigen'}</Button>
            <Button type="button" size="sm" variant="outline" onClick={() => setOffeneBereiche(current => ({ ...current, [bestellung.id]: current[bestellung.id] === 'rechnungen' ? null : 'rechnungen' }))} aria-expanded={offeneBereiche[bestellung.id] === 'rechnungen'}>Rechnungen abgleichen {offeneBereiche[bestellung.id] === 'rechnungen' ? 'ausblenden' : 'anzeigen'}</Button>
          </div>
          {offeneBereiche[bestellung.id] === 'zeugnisse' && <div className="mt-4 border-t border-slate-100 pt-4"><ZeugnisZuordnung bestellungId={bestellung.id} /></div>}
          {offeneBereiche[bestellung.id] === 'rechnungen' && <div className="mt-4 border-t border-slate-100 pt-4"><Rechnungsabgleich bestellungId={bestellung.id} /></div>}
        </article>;
      })}</div>}
    </div>
    {dialogBestellungId !== null && <LieferungDialog bestellungId={dialogBestellungId} onClose={() => setDialogBestellungId(null)} onSaved={() => { setDialogBestellungId(null); setLaden(true); setAktualisierung(value => value + 1); }} />}
  </PageLayout>;
}
