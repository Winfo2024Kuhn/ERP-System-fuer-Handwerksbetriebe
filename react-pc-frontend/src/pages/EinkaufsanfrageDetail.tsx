import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '../components/ui/button';
import { DetailLayout } from '../components/DetailLayout';
import { PageLayout } from '../components/layout/PageLayout';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { AnfrageLieferanten } from '../features/einkauf/components/AnfrageLieferanten';
import { AnfrageEntwurfEditor } from '../features/einkauf/components/AnfrageEntwurfEditor';
import { KommunikationsVerlauf } from '../features/einkauf/components/KommunikationsVerlauf';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { einkaufApi } from '../features/einkauf/api';
import type { AnfrageDetail } from '../features/einkauf/types';

type Revision = { id: number; nummer: number; status: string };
const datum = (value?: string | null) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('de-DE') : 'Nicht angegeben';
export default function EinkaufsanfrageDetail() {
  const { id } = useParams(); const navigate = useNavigate(); const toast = useToast();
  const [detail, setDetail] = useState<AnfrageDetail | null>(null);
  const [revisionen, setRevisionen] = useState<Revision[]>([]);
  const [fehler, setFehler] = useState(''); const [bearbeiten, setBearbeiten] = useState(false);
  const [wechselt, setWechselt] = useState(false); const neu = id === 'neu';
  useEffect(() => {
    if (neu) return;
    let aktiv = true;
    setDetail(null); setBearbeiten(false); setFehler('');
    Promise.all([einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${encodeURIComponent(id ?? '')}`),
      einkaufApi.get<Revision[]>(`/api/einkauf/anfragen/${encodeURIComponent(id ?? '')}/revisionen`)])
      .then(([d, r]) => { if (aktiv) { setDetail(d); setRevisionen(r); } })
      .catch(error => { if (aktiv) { setFehler(error.message); toast.error(error.message); } });
    return () => { aktiv = false; };
  }, [id, neu, toast]);
  const revisionWaehlen = async (value: string) => {
    setWechselt(true); setFehler(''); setBearbeiten(false);
    try { setDetail(await einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${id}/revisionen/${encodeURIComponent(value)}`)); }
    catch (error) { const message = error instanceof Error ? error.message : 'Fassung konnte nicht geladen werden.'; setFehler(message); toast.error(message); }
    finally { setWechselt(false); }
  };
  const gespeichert = (saved: AnfrageDetail) => {
    if (neu) { navigate(`/einkaufsanfragen/${saved.kopf.id}`, { replace: true }); return; }
    setDetail(saved); setBearbeiten(false);
    void einkaufApi.get<Revision[]>(`/api/einkauf/anfragen/${saved.kopf.id}/revisionen`).then(setRevisionen).catch(error => toast.error(error.message));
  };
  return <PageLayout ribbonCategory="Einkauf" title={neu ? 'NEUE EINKAUFSANFRAGE' : detail?.kopf.paNummer ?? 'EINKAUFSANFRAGE'}
    subtitle={neu ? 'Bedarfe und Empfänger für eine Anfrage zusammenstellen.' : 'Anfragefassungen, Versand und Rückmeldungen prüfen.'}
    actions={<Link to="/einkaufsanfragen"><Button variant="outline" size="sm"><ArrowLeft className="mr-2 h-4 w-4" />Zur Übersicht</Button></Link>}>
    <div className="space-y-4"><EinkaufNavigation active="anfragen" />
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {neu ? <AnfrageEntwurfEditor onSaved={gespeichert} /> : !detail ? <p role="status">Anfrage wird geladen …</p> :
        <DetailLayout header={<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
          <div><p className="font-medium">Revision {detail.kopf.revisionsNummer}{detail.historisch ? ' · frühere Fassung (nur Lesen)' : ' · aktuelle Fassung'}</p>
            <p className="text-sm text-slate-600">Antwort bis {datum(detail.kopf.antwortfrist)} · Lieferung {datum(detail.kopf.liefertermin)}</p>
            {detail.historisch && <p className="text-sm text-slate-600">Schreibgeschützt. Frühere Antworten bleiben dieser Fassung zugeordnet.</p>}</div>
          <div className="flex flex-wrap items-center gap-2"><Select aria-label="Anfragefassung" value={String(detail.angezeigteRevisionId)}
            options={revisionen.map(r => ({ value: String(r.id), label: `Fassung ${r.nummer}` }))} disabled={wechselt || bearbeiten} onChange={v => void revisionWaehlen(v)} />
            {!detail.historisch && <Button size="sm" variant="outline" disabled={wechselt || bearbeiten} onClick={() => setBearbeiten(true)}>Neue Revision</Button>}</div>
        </div>}
        mainContent={<div className="space-y-4">
          {bearbeiten && !detail.historisch ? <AnfrageEntwurfEditor initial={detail} onCancel={() => setBearbeiten(false)} onSaved={gespeichert} /> :
            <AnfrageLieferanten key={detail.angezeigteRevisionId} anfrageId={detail.kopf.id} revisionId={detail.angezeigteRevisionId} lieferanten={detail.lieferanten} historisch={detail.historisch} />}
          <section className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="font-semibold">Positionen · Revision {detail.kopf.revisionsNummer}</h2>
            {detail.positionen.map(p => <div key={p.id} className="border-t border-slate-100 py-2"><p>{p.snapshot.bezeichnung}</p><p className="text-sm text-slate-600">{p.herkuenfte.map(h => h.menge?.toLocaleString('de-DE')).join(', ')} {p.snapshot.basis?.einheit?.toLowerCase()}</p></div>)}
          </section>
          <KommunikationsVerlauf typ="ANFRAGE" vorgangId={detail.kopf.id} revisionId={detail.angezeigteRevisionId} beteiligungen={detail.lieferanten} readOnly={detail.historisch} />
        </div>}
        sideContent={<section className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="font-semibold">Anfrage im Blick</h2><p className="mt-2 text-sm">{detail.lieferanten.length} Lieferanten · Fassung {detail.kopf.revisionsNummer}</p><p className="mt-2 text-sm text-slate-600">Eine Anfrage bestellt kein Material. Angebote werden anschließend geprüft und bewusst freigegeben.</p></section>} />}
    </div>
  </PageLayout>;
}
