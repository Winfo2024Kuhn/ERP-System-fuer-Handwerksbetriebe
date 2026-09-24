import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FilePlus2 } from 'lucide-react';
import { Button } from '../components/ui/button';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';

type Kopf = { id: number; paNummer: string; revisionsNummer: number; status: string; antwortfrist?: string | null; liefertermin?: string | null; projektIds?: number[]; antworten?: number; lieferantenAnzahl?: number };
type Page<T> = { content: T[]; totalPages?: number; totalElements?: number };
const datum = (value?: string | null) => value ? new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(`${value}T00:00:00`)) : 'Keine Frist';
export default function Einkaufsanfragen() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [seite, setSeite] = useState<Page<Kopf>>({ content: [] });
  const [laedt, setLaedt] = useState(true);
  const [fehler, setFehler] = useState('');
  const laden = useCallback(async () => { setFehler(''); setLaedt(true); try { const r = await fetch(`/api/einkauf/anfragen?page=${page}&size=20`); if (!r.ok) throw new Error('Anfragen konnten nicht geladen werden.'); setSeite(await r.json() as Page<Kopf>); } catch (e) { const message = e instanceof Error ? e.message : 'Anfragen konnten nicht geladen werden.'; setFehler(message); toast.error(message); } finally { setLaedt(false); } }, [page, toast]);
  useEffect(() => { void laden(); }, [laden]);
  return <PageLayout ribbonCategory="Einkauf" title="EINKAUFSANFRAGEN" subtitle="Anfragen und Rückmeldungen Ihrer Lieferanten im Blick behalten." actions={<Link to="/einkaufsanfragen/neu"><Button size="sm"><FilePlus2 className="mr-2 h-4 w-4" />Neue Anfrage</Button></Link>}>
    <div className="space-y-5"><EinkaufNavigation active="anfragen" />
      {laedt ? <p role="status" className="rounded-lg border border-slate-200 bg-white p-5">Anfragen werden geladen …</p> : fehler ? <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-5 text-rose-800">{fehler}</p> : seite.content.length === 0 ? <p className="rounded-lg border border-slate-200 bg-white p-5 text-slate-600">Noch keine Einkaufsanfragen vorhanden.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-slate-600"><tr><th className="p-3">Anfrage</th><th className="p-3">Projekt</th><th className="p-3">Antwortfrist</th><th className="p-3">Liefertermin</th><th className="p-3">Status</th><th className="p-3">Antworten</th></tr></thead><tbody className="divide-y divide-slate-100">{seite.content.map(a => <tr key={a.id}><td className="p-3"><Link className="font-medium text-rose-700 hover:underline" to={`/einkaufsanfragen/${a.id}`}>{a.paNummer}</Link><span className="ml-2 text-slate-500">Revision {a.revisionsNummer}</span></td><td className="p-3">{a.projektIds?.length?a.projektIds.map(p=>`#${p}`).join(', '):'Kein Projekt'}</td><td className="p-3">{datum(a.antwortfrist)}</td><td className="p-3">{datum(a.liefertermin)}</td><td className="p-3">{a.status}</td><td className="p-3">{a.antworten??0} / {a.lieferantenAnzahl??0}</td></tr>)}</tbody></table></div>}
      <nav aria-label="Anfrageseiten" className="flex items-center justify-center gap-3"><Button variant="outline" size="sm" disabled={laedt || page === 0} onClick={() => setPage(p => p - 1)}>Vorige Seite</Button><span>Seite {page + 1} von {Math.max(1, seite.totalPages ?? 1)}</span><Button variant="outline" size="sm" disabled={laedt || page + 1 >= (seite.totalPages ?? 1)} onClick={() => setPage(p => p + 1)}>Nächste Seite</Button></nav>
    </div>
  </PageLayout>;
}
