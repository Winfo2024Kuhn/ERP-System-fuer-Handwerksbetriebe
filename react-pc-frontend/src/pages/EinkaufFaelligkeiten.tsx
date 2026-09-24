import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock, RefreshCw } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { PageLayout } from '../components/layout/PageLayout';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { stripHtmlTags, unescapeHtmlEntities } from '../lib/htmlSanitizer';
import { einkaufApi } from '../features/einkauf/api';
import type { Page } from '../features/einkauf/types';

interface Faelligkeit { typ: string; vorgangId: number; nummer: string | null; beteiligungId: number | null; frist: string | null; zustaendigId: number | null; hinweis: string | null }
interface Nachfrage { typ: string; vorgangId: number; beteiligungId: number | null; vorlageId: number; vorlageVersion: number; empfaenger: string; subject: string; htmlBody: string; fehlendeNachweise: string[] }
interface Nutzer { id: number }
const heute = () => new Date().toLocaleDateString('sv-SE');
const datum = (value: string | null) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('de-DE') : 'Termin klären';
const titel = (typ: string) => ({ ANFRAGE_ANTWORTFRIST: 'Antwort auf Anfrage', BESTELLBESTAETIGUNG: 'Auftragsbestätigung', LIEFERTERMIN: 'Liefertermin', ZEUGNIS: 'Zeugnis je Charge' }[typ] ?? 'Einkaufsvorgang');
const linkZumVorgang = (row: Pick<Faelligkeit, 'typ' | 'vorgangId'>) => row.typ === 'ANFRAGE_ANTWORTFRIST' ? `/einkaufsanfragen/${row.vorgangId}` : `/bestellungen/${row.vorgangId}`;

export default function EinkaufFaelligkeiten() {
  const toast = useToast();
  const [faelligkeiten, setFaelligkeiten] = useState<Faelligkeit[]>([]);
  const [nutzer, setNutzer] = useState<Nutzer | null>(null);
  const [nurEigene, setNurEigene] = useState(false);
  const [seite, setSeite] = useState(0);
  const [seitenGesamt, setSeitenGesamt] = useState(0);
  const [laden, setLaden] = useState(true);
  const [fehler, setFehler] = useState('');
  const [aktualisierung, setAktualisierung] = useState(0);
  const [vorschau, setVorschau] = useState<Nachfrage | null>(null);
  const [vorschauLaden, setVorschauLaden] = useState<number | null>(null);

  useEffect(() => { let aktiv = true; einkaufApi.get<Nutzer>('/api/auth/me').then(value => { if (aktiv && Number.isFinite(value.id)) setNutzer(value); }).catch(() => { if (aktiv) setNutzer(null); }); return () => { aktiv = false; }; }, []);
  useEffect(() => {
    let aktiv = true; setLaden(true); setFehler('');
    const zustaendig = nurEigene && nutzer ? `&zustaendigId=${encodeURIComponent(nutzer.id)}` : '';
    einkaufApi.get<Page<Faelligkeit>>(`/api/einkauf/faelligkeiten?heute=${encodeURIComponent(heute())}${zustaendig}&page=${seite}&size=20&sort=frist,asc`)
      .then(result => { if (aktiv) { setFaelligkeiten(result.content); setSeitenGesamt(result.totalPages); } })
      .catch(error => { if (aktiv) { const message = error instanceof Error ? error.message : 'Fälligkeiten konnten nicht geladen werden.'; setFehler(message); toast.error(message); } })
      .finally(() => { if (aktiv) setLaden(false); });
    return () => { aktiv = false; };
  }, [aktualisierung, nurEigene, nutzer, seite, toast]);

  const nachfrageVorbereiten = async (row: Faelligkeit) => {
    setVorschauLaden(row.vorgangId);
    try {
      setVorschau(await einkaufApi.post<Nachfrage>('/api/einkauf/faelligkeiten/nachfrage', { typ: row.typ, vorgangId: row.vorgangId, beteiligungId: row.beteiligungId }));
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Nachfragevorschau konnte nicht vorbereitet werden.'); }
    finally { setVorschauLaden(null); }
  };

  return <PageLayout ribbonCategory="Einkauf" title="DAS IST FÄLLIG" subtitle="Offene Antworten, Auftragsbestätigungen, Liefertermine und Zeugnisse zuerst erledigen."
    actions={<Button size="sm" variant="outline" disabled={laden} onClick={() => setAktualisierung(value => value + 1)}><RefreshCw className={`mr-2 h-4 w-4 ${laden ? 'animate-spin' : ''}`} />Aktualisieren</Button>}>
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
        <label className="min-w-[14rem] space-y-1 text-sm font-medium">Zuständigkeit<Select aria-label="Zuständigkeitsfilter" value={nurEigene ? 'eigen' : 'alle'} options={[{ value: 'alle', label: 'Alle offenen Vorgänge' }, { value: 'eigen', label: 'Meine Vorgänge' }]} onChange={value => { setSeite(0); setNurEigene(value === 'eigen'); }} disabled={!nutzer} /></label>
        <p className="text-sm text-slate-600">Terminlose Vorgänge stehen als „Termin klären“ in der Liste. Erledigte Vorgänge kommen vom Server nicht mehr zurück.</p>
      </div>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laden ? <p role="status" className="rounded-lg border border-slate-200 bg-white p-5">Fällige Vorgänge werden geladen …</p>
        : faelligkeiten.length === 0 ? <section className="rounded-lg border border-slate-200 bg-white p-8 text-center"><CalendarClock className="mx-auto mb-3 h-8 w-8 text-slate-400" /><h2 className="font-semibold">Alles erledigt</h2><p className="mt-1 text-sm text-slate-600">Es sind keine offenen Einkaufsvorgänge fällig.</p></section>
        : <ul className="space-y-3">{faelligkeiten.map(row => <li key={`${row.typ}-${row.vorgangId}-${row.beteiligungId ?? 'gesamt'}`}><article className="grid min-w-0 gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[minmax(0,1fr)_auto]">
          <div className="min-w-0"><p className="text-sm font-semibold text-rose-600">{titel(row.typ)} · {row.nummer ?? `Vorgang ${row.vorgangId}`}</p><h2 className="mt-1 break-words font-semibold text-slate-900">{row.hinweis ?? titel(row.typ)}</h2><p className="mt-1 text-sm text-slate-600">Fällig: {datum(row.frist)} · Zuständig: {row.zustaendigId ? `Mitarbeiter ${row.zustaendigId}` : 'Noch nicht zugeordnet'}</p>
            <Link className="mt-2 inline-flex text-sm font-medium text-rose-700 underline-offset-2 hover:underline" to={linkZumVorgang(row)}>Vorgang {row.nummer ?? row.vorgangId} öffnen</Link></div>
          <div className="flex flex-wrap items-center gap-2 md:justify-end"><Button size="sm" variant="outline" disabled={vorschauLaden === row.vorgangId} onClick={() => void nachfrageVorbereiten(row)}>{vorschauLaden === row.vorgangId ? 'Vorschau wird erstellt …' : 'Nachfrage vorbereiten'}</Button></div>
        </article></li>)}</ul>}
      {!laden && seitenGesamt > 1 && <div className="flex items-center justify-center gap-3"><Button variant="outline" size="sm" disabled={seite === 0} onClick={() => setSeite(value => Math.max(0, value - 1))}>Vorige</Button><span className="text-sm text-slate-600">Seite {seite + 1} von {seitenGesamt}</span><Button variant="outline" size="sm" disabled={seite + 1 >= seitenGesamt} onClick={() => setSeite(value => value + 1)}>Weitere</Button></div>}
    </div>
    {vorschau && <Dialog open onOpenChange={open => { if (!open) setVorschau(null); }}><DialogContent className="max-w-3xl"><DialogHeader><DialogTitle>Nachfrage prüfen · {titel(vorschau.typ)}</DialogTitle></DialogHeader><dl className="grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2"><div><dt className="font-semibold">Empfänger</dt><dd className="break-all">{vorschau.empfaenger}</dd></div><div><dt className="font-semibold">Vorlage</dt><dd>Fassung {vorschau.vorlageVersion}</dd></div><div className="sm:col-span-2"><dt className="font-semibold">Betreff</dt><dd>{vorschau.subject}</dd></div><div className="sm:col-span-2"><dt className="font-semibold">Nachricht</dt><dd className="whitespace-pre-wrap">{unescapeHtmlEntities(stripHtmlTags(vorschau.htmlBody))}</dd></div></dl>
      {vorschau.fehlendeNachweise.length > 0 && <p className="text-sm text-amber-800">Fehlende Angaben: {vorschau.fehlendeNachweise.join(', ')}</p>}
      <p className="text-sm text-slate-600">Diese Vorschau sendet nichts. Öffnen Sie den Vorgang, um den vorhandenen Versandweg zu prüfen und ausdrücklich freizugeben.</p>
      <DialogFooter><Button variant="outline" onClick={() => setVorschau(null)}>Zurück zur Liste</Button><Link className="inline-flex min-h-10 items-center rounded-lg border border-rose-600 bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700" to={linkZumVorgang(vorschau)} onClick={() => setVorschau(null)}>Vorgang und Verlauf öffnen</Link></DialogFooter></DialogContent></Dialog>}
  </PageLayout>;
}
