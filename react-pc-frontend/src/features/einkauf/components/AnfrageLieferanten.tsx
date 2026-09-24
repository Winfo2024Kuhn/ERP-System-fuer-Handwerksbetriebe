import { useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Select } from '../../../components/ui/select-custom';
import { useToast } from '../../../components/ui/toast';
import { VersandVorschauDialog, type VersandVorschau } from './VersandVorschauDialog';
import { einkaufApi } from '../api';
import type { Lieferantenbeteiligung, VersandDto } from '../types';
type Versand = { beteiligungId: number; versand: VersandDto };
const statusText: Record<string, string> = { AUSSTEHEND: 'Noch keine Antwort', VERSENDET: 'Gesendet', BEANTWORTET: 'Antwort erhalten', ABGESAGT: 'Abgesagt', ERLEDIGT: 'Erledigt', VORBEREITET: 'Versand vorbereitet', LAEUFT: 'Versand läuft', ANGENOMMEN: 'Vom Mailserver angenommen', FEHLGESCHLAGEN: 'Sicher fehlgeschlagen', UNKLAR: 'Versand unklar – vor erneutem Versand klären' };
export function AnfrageLieferanten({ anfrageId, revisionId, lieferanten, historisch = false }: {
  anfrageId: number; revisionId: number; lieferanten: Lieferantenbeteiligung[]; historisch?: boolean;
}) {
  const toast = useToast(); const [ausgewaehlt, setAusgewaehlt] = useState(String(lieferanten[0]?.id ?? ''));
  const [vorschau, setVorschau] = useState<VersandVorschau | null>(null); const [fehler, setFehler] = useState('');
  const [jobs, setJobs] = useState<Versand[]>([]); const [busy, setBusy] = useState(false); const [refresh, setRefresh] = useState(0);
  const [freigabeKey, setFreigabeKey] = useState('');
  useEffect(() => {
    let aktiv = true; let timer: ReturnType<typeof setTimeout> | undefined;
    const laden = async () => {
      try {
        const data = await einkaufApi.get<Versand[]>(`/api/einkauf/anfragen/${anfrageId}/revisionen/${revisionId}/versandstatus`);
        if (!aktiv) return; setJobs(data);
        if (data.some(j => ['VORBEREITET', 'LAEUFT'].includes(j.versand.status))) timer = setTimeout(() => void laden(), 2000);
      } catch (error) { if (aktiv) { const message = error instanceof Error ? error.message : 'Versandstatus konnte nicht geladen werden.'; setFehler(message); toast.error(message); } }
    };
    setVorschau(null); void laden();
    return () => { aktiv = false; clearTimeout(timer); };
  }, [anfrageId, revisionId, refresh, toast]);
  const lieferant = lieferanten.find(l => String(l.id) === ausgewaehlt);
  const job = jobs.find(j => String(j.beteiligungId) === ausgewaehlt)?.versand;
  const melden = (error: unknown) => { const message = error instanceof Error ? error.message : 'Versand konnte nicht vorbereitet werden.'; setFehler(message); toast.error(message); };
  const vorbereiten = async () => {
    if (!lieferant || historisch) return;
    setBusy(true); setFehler('');
    try {
      const templates = await einkaufApi.get<{ id: number; dokumentTyp: string; aktiv: boolean; standard: boolean }[]>('/api/email-textvorlagen');
      const template = templates.find(t => t.dokumentTyp === 'EINKAUF_ANFRAGE' && t.aktiv && t.standard) ?? templates.find(t => t.dokumentTyp === 'EINKAUF_ANFRAGE' && t.aktiv);
      if (!template) throw new Error('Bitte zuerst eine aktive Vorlage für Einkaufsanfragen anlegen.');
      setVorschau(await einkaufApi.get<VersandVorschau>(`/api/einkauf/anfragen/${anfrageId}/lieferanten/${lieferant.id}/vorschau?templateId=${template.id}`));
      setFreigabeKey(crypto.randomUUID());
    } catch (error) { melden(error); } finally { setBusy(false); }
  };
  const senden = async (hash: string) => {
    if (!vorschau || !lieferant || historisch) return;
    await einkaufApi.post(`/api/einkauf/anfragen/${anfrageId}/lieferanten/${lieferant.id}/senden`, { version: vorschau.version, vorschauHash: hash, idempotenzKey: freigabeKey });
    setVorschau(null); setRefresh(n => n + 1); toast.success('Versandauftrag erteilt. Der Versandstatus wird aktualisiert.');
  };
  const erneut = async () => {
    if (!lieferant || !job || job.status !== 'FEHLGESCHLAGEN') return;
    setBusy(true);
    try { await einkaufApi.post(`/api/einkauf/anfragen/${anfrageId}/lieferanten/${lieferant.id}/versand/${job.id}/erneut`, { version: job.version }); setRefresh(n => n + 1); toast.success('Sicher fehlgeschlagener Versand erneut beauftragt.'); }
    catch (error) { melden(error); } finally { setBusy(false); }
  };
  return <section className="rounded-lg border border-slate-200 bg-white p-4">
    <h2 className="font-semibold">Lieferanten und Versand</h2>
    {!historisch && lieferanten.length > 0 && <div className="my-3 flex flex-wrap items-center gap-3"><div className="min-w-48 flex-1"><Select aria-label="Empfänger für Versand" value={ausgewaehlt} options={lieferanten.map(l => ({ value: String(l.id), label: l.lieferantenname }))} onChange={value => { setAusgewaehlt(value); setVorschau(null); }} /></div>
      {!job && lieferant?.status === 'AUSSTEHEND' && <Button size="sm" disabled={busy} onClick={() => void vorbereiten()}>{busy ? 'Lädt …' : 'Vorschau und Freigabe'}</Button>}
      {job?.status === 'FEHLGESCHLAGEN' && <Button variant="outline" size="sm" disabled={busy} onClick={() => void erneut()}>Sicher fehlgeschlagen · erneut senden</Button>}
    </div>}
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <ul className="divide-y divide-slate-100">{lieferanten.map(l => { const versand = jobs.find(j => j.beteiligungId === l.id)?.versand; return <li key={l.id} className="py-2"><p className="font-medium">{l.lieferantenname}</p><p className="break-all text-sm text-slate-600">{l.kontakt?.email} · {statusText[versand?.status ?? l.status] ?? l.status}</p>{versand?.fehlerCode && <p className="text-sm text-rose-700">{versand.fehlerCode}</p>}</li>; })}</ul>
    {!lieferanten.length && <p className="mt-2 text-sm text-slate-600">Noch keine Empfänger. Ergänzen Sie Einkaufskontakte in einer neuen Fassung.</p>}
    {vorschau && <VersandVorschauDialog vorschau={vorschau} onFreigeben={senden} onSchliessen={() => setVorschau(null)} />}
  </section>;
}
