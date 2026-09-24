import { useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select-custom';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi } from '../api';
import type { Lieferantenbeteiligung, Nachricht, Zuordnungsergebnis } from '../types';
export type KommunikationsTyp = 'ANFRAGE' | 'BESTELLUNG';
type Mail = { id: number; subject: string; fromAddress: string; body?: string; attachments?: { id: number; originalFilename: string }[] };
export function KommunikationsVerlauf({ typ, vorgangId, revisionId, beteiligungen = [], readOnly = false }: {
  typ: KommunikationsTyp; vorgangId: number; revisionId?: number; beteiligungen?: Lieferantenbeteiligung[]; readOnly?: boolean;
}) {
  const toast = useToast(); const [nachrichten, setNachrichten] = useState<Nachricht[]>([]);
  const [page, setPage] = useState(0); const [pages, setPages] = useState(1); const [refresh, setRefresh] = useState(0);
  const [fehler, setFehler] = useState(''); const [offen, setOffen] = useState(false); const [suche, setSuche] = useState('');
  const [suchOffset, setSuchOffset] = useState(0); const [suchQuery, setSuchQuery] = useState('');
  const [treffer, setTreffer] = useState<Mail[]>([]); const [mail, setMail] = useState<Mail | null>(null);
  const [vorschlag, setVorschlag] = useState<Zuordnungsergebnis | null>(null); const [lieferant, setLieferant] = useState('');
  const [grund, setGrund] = useState(''); const [bestaetigt, setBestaetigt] = useState(false); const [busy, setBusy] = useState(false);
  const melden = (error: unknown) => { const message = error instanceof Error ? error.message : 'Nachrichten konnten nicht geladen werden.'; setFehler(message); toast.error(message); };
  useEffect(() => {
    let aktiv = true;
    einkaufApi.get<{ content: Nachricht[]; totalPages: number }>(`/api/einkauf/${typ}/${vorgangId}/verlauf?page=${page}&size=20`)
      .then(data => { if (aktiv) { setNachrichten(data.content ?? []); setPages(data.totalPages ?? 1); } })
      .catch(error => { if (aktiv) { setFehler(error.message); toast.error(error.message); } });
    return () => { aktiv = false; };
  }, [typ, vorgangId, page, refresh, toast]);
  const suchen = async (offset = 0, query = suche.trim()) => {
    if (query.length < 2) { melden(new Error('Bitte mindestens zwei Zeichen aus Betreff oder Absender eingeben.')); return; }
    setBusy(true);
    try { const daten = await einkaufApi.get<Mail[]>(`/api/emails/search?q=${encodeURIComponent(query)}&offset=${offset}&limit=20`); setTreffer(daten); setSuchOffset(offset); setSuchQuery(query); }
    catch (error) { melden(error); } finally { setBusy(false); }
  };
  const oeffnen = async (id: number) => {
    setBusy(true); setVorschlag(null); setBestaetigt(false); setGrund('');
    try { setMail(await einkaufApi.get<Mail>(`/api/emails/${id}`)); }
    catch (error) { melden(error); } finally { setBusy(false); }
  };
  const ermitteln = async () => {
    if (!mail) return; setBusy(true);
    try { setVorschlag(await einkaufApi.post<Zuordnungsergebnis>(`/api/einkauf/mail/${mail.id}/zuordnung/ermitteln`, {})); }
    catch (error) { melden(error); } finally { setBusy(false); }
  };
  const zuordnen = async () => {
    if (!mail || !bestaetigt || !grund.trim() || !lieferant || !revisionId || readOnly) return;
    setBusy(true);
    try {
      await einkaufApi.post(`/api/einkauf/mail/${mail.id}/zuordnung`, { typ, vorgangId, beteiligungId: Number(lieferant), revisionId, begruendung: grund.trim() });
      setOffen(false); setMail(null); setRefresh(n => n + 1); toast.success('Nachricht bewusst zugeordnet.');
    } catch (error) { melden(error); } finally { setBusy(false); }
  };
  return <section className="rounded-lg border border-slate-200 bg-white p-4" aria-label="Kommunikationsverlauf">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-semibold">Kommunikationsverlauf</h2>
      {!readOnly && revisionId && beteiligungen.length > 0 && <Button size="sm" variant="outline" onClick={() => { setOffen(true); setMail(null); setBestaetigt(false); }}>Nachricht zuordnen</Button>}
    </div>
    {fehler && <p role="alert" className="mt-2 text-sm text-rose-700">{fehler}</p>}
    {!nachrichten.length ? <p className="mt-2 text-sm text-slate-600">Noch keine Nachrichten zu diesem Vorgang.</p> : <ol className="divide-y divide-slate-100">{nachrichten.map(n => <li key={n.emailId} className="py-3"><p className="font-medium">{n.subject}</p><p className="break-all text-sm text-slate-600">{n.fromAddress} · {n.quelle}{n.revisionId ? ` · Revision ${n.revisionId}` : ''}</p></li>)}</ol>}
    {pages > 1 && <div className="flex items-center gap-3"><Button variant="outline" size="sm" disabled={!page} onClick={() => setPage(p => p - 1)}>Vorige Nachrichten</Button><span>Seite {page + 1} von {pages}</span><Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)}>Weitere Nachrichten</Button></div>}
    <Dialog open={offen} onOpenChange={setOffen} className="max-w-3xl"><DialogContent className="max-h-[85vh] overflow-y-auto"><DialogHeader><DialogTitle>Nachricht und Unterlagen prüfen</DialogTitle></DialogHeader>
      <div className="flex gap-2"><Input aria-label="Nachricht suchen" placeholder="Betreff oder Absender" value={suche} onChange={e => setSuche(e.target.value)} /><Button variant="outline" disabled={busy} onClick={() => void suchen()}>Suchen</Button></div>
      <ul>{treffer.map(n => <li key={n.id}><button className="w-full rounded p-2 text-left text-sm hover:bg-slate-50" onClick={() => void oeffnen(n.id)}>{n.subject} · {n.fromAddress}</button></li>)}</ul>
      {suchQuery && <nav aria-label="Suchergebnisse" className="flex items-center gap-3"><Button variant="outline" size="sm" disabled={busy || suchOffset === 0} onClick={() => void suchen(Math.max(0, suchOffset - 20), suchQuery)}>Vorige Treffer</Button><span className="text-sm">Seite {suchOffset / 20 + 1}</span><Button variant="outline" size="sm" disabled={busy || treffer.length < 20} onClick={() => void suchen(suchOffset + 20, suchQuery)}>Weitere Treffer</Button></nav>}
      {mail && <div className="space-y-3"><section className="rounded border border-slate-200 bg-slate-50 p-3"><h3 className="font-semibold">Fundstelle: {mail.subject}</h3><p className="break-all text-sm">Absender: {mail.fromAddress}</p><p className="mt-2 max-h-40 overflow-y-auto whitespace-pre-wrap text-sm">{mail.body || 'Kein Klartext vorhanden. Bitte die Originalunterlagen prüfen.'}</p><ul>{mail.attachments?.map(a => <li key={a.id}><a className="text-sm text-rose-700 underline" target="_blank" rel="noreferrer" href={`/api/emails/${mail.id}/attachments/${a.id}`}>{a.originalFilename} öffnen</a></li>)}</ul></section>
        <Button variant="outline" size="sm" disabled={busy} onClick={() => void ermitteln()}>Zuordnungsvorschlag prüfen</Button>
        {vorschlag && <p className="text-sm text-slate-600">Ergebnis: {vorschlag.status} · Fundstelle {vorschlag.quelle}. {vorschlag.vorgangId && vorschlag.vorgangId !== vorgangId ? 'Der Vorschlag betrifft einen anderen Vorgang. Bitte ausdrücklich prüfen.' : 'Bitte Absender, Lieferant und Unterlagen selbst prüfen.'}</p>}
        <Select aria-label="Geprüfter Lieferant" value={lieferant} placeholder="Lieferant dieser Anfrage" options={beteiligungen.map(l => ({ value: String(l.id), label: `${l.lieferantenname} · ${l.kontakt?.email ?? ''}` }))} onChange={value => { setLieferant(value); setBestaetigt(false); }} />
        <Input aria-label="Begründung der Zuordnung" value={grund} onChange={e => setGrund(e.target.value)} placeholder="Warum gehört die Nachricht zu diesem Lieferanten und dieser Fassung?" maxLength={1000} />
        <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={bestaetigt} onChange={e => setBestaetigt(e.target.checked)} />Ich habe Absender, Lieferant, Fundstelle und Unterlagen für diese Anfragefassung geprüft.</label>
      </div>}
      <DialogFooter><Button variant="outline" onClick={() => setOffen(false)}>Zurück</Button><Button disabled={busy || !mail || !lieferant || !grund.trim() || !bestaetigt} title={!bestaetigt ? 'Bitte die Zuordnung nach Prüfung ausdrücklich bestätigen.' : undefined} onClick={() => void zuordnen()}>Zuordnung bestätigen</Button></DialogFooter>
    </DialogContent></Dialog>
  </section>;
}
