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

interface Fälligkeit { typ: string; vorgangId: number; nummer: string | null; beteiligungId: number | null; frist: string | null; zustaendigId: number | null; hinweis: string | null }
interface Nachfrage { typ: string; vorgangId: number; beteiligungId: number | null; vorlageId: number; vorlageVersion: number; empfaenger: string; subject: string; htmlBody: string; fehlendeNachweise: string[] }
interface Nutzer { id: number; displayName?: string }
const heute = () => new Date().toLocaleDateString('sv-SE');
const datum = (wert: string | null) => wert ? new Date(`${wert}T00:00:00`).toLocaleDateString('de-DE') : 'Termin klären';
const titel = (typ: string) => ({ ANFRAGE_ANTWORTFRIST: 'Antwort auf Anfrage', BESTELLBESTAETIGUNG: 'Auftragsbestätigung', LIEFERTERMIN: 'Liefertermin', ZEUGNIS: 'Zeugnis je Charge' }[typ] ?? 'Einkaufsvorgang');
const linkZumVorgang = (zeile: Pick<Fälligkeit, 'typ' | 'vorgangId'>) => zeile.typ === 'ANFRAGE_ANTWORTFRIST' ? `/einkaufsanfragen/${zeile.vorgangId}` : `/bestellungen/${zeile.vorgangId}`;

export default function EinkaufFaelligkeiten() {
  const meldungen = useToast();
  const [fälligkeiten, setzeFälligkeiten] = useState<Fälligkeit[]>([]);
  const [nutzer, setzeNutzer] = useState<Nutzer | null>(null);
  const [nurEigene, setzeNurEigene] = useState(false);
  const [seite, setzeSeite] = useState(0);
  const [seitenGesamt, setzeSeitenGesamt] = useState(0);
  const [laden, setzeLaden] = useState(true);
  const [fehler, setzeFehler] = useState('');
  const [aktualisierung, setzeAktualisierung] = useState(0);
  const [vorschau, setzeVorschau] = useState<Nachfrage | null>(null);
  const [vorschauLaden, setzeVorschauLaden] = useState<number | null>(null);

  useEffect(() => { let aktiv = true; einkaufApi.get<Nutzer>('/api/auth/me').then(wert => { if (aktiv && Number.isFinite(wert.id)) setzeNutzer(wert); }).catch(() => { if (aktiv) setzeNutzer(null); }); return () => { aktiv = false; }; }, []);
  useEffect(() => {
    let aktiv = true; setzeLaden(true); setzeFehler('');
    const zuständig = nurEigene && nutzer ? `&zustaendigId=${encodeURIComponent(nutzer.id)}` : '';
    einkaufApi.get<Page<Fälligkeit>>(`/api/einkauf/faelligkeiten?heute=${encodeURIComponent(heute())}${zuständig}&page=${seite}&size=20&sort=frist,asc`)
      .then(ergebnis => { if (aktiv) { setzeFälligkeiten(ergebnis.content); setzeSeitenGesamt(ergebnis.totalPages); } })
      .catch(fehlerursache => { if (aktiv) { const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'Fälligkeiten konnten nicht geladen werden.'; setzeFehler(meldung); meldungen.error(meldung); } })
      .finally(() => { if (aktiv) setzeLaden(false); });
    return () => { aktiv = false; };
  }, [aktualisierung, nurEigene, nutzer, seite, meldungen]);

  const nachfrageVorbereiten = async (zeile: Fälligkeit) => {
    setzeVorschauLaden(zeile.vorgangId);
    try {
      setzeVorschau(await einkaufApi.post<Nachfrage>('/api/einkauf/faelligkeiten/nachfrage', { typ: zeile.typ, vorgangId: zeile.vorgangId, beteiligungId: zeile.beteiligungId }));
    } catch (fehlerursache) { meldungen.error(fehlerursache instanceof Error ? fehlerursache.message : 'Nachfragevorschau konnte nicht vorbereitet werden.'); }
    finally { setzeVorschauLaden(null); }
  };

  return <PageLayout ribbonCategory="Einkauf" title="DAS IST FÄLLIG" subtitle="Offene Antworten, Auftragsbestätigungen, Liefertermine und Zeugnisse zuerst erledigen."
    actions={<Button size="sm" variant="outline" disabled={laden} onClick={() => setzeAktualisierung(wert => wert + 1)}><RefreshCw className={`mr-2 h-4 w-4 ${laden ? 'animate-spin' : ''}`} />Aktualisieren</Button>}>
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
        <label className="min-w-[14rem] space-y-1 text-sm font-medium">Zuständigkeit<Select aria-label="Zuständigkeitsfilter" value={nurEigene ? 'eigen' : 'alle'} options={[{ value: 'alle', label: 'Alle offenen Vorgänge' }, { value: 'eigen', label: 'Meine Vorgänge' }]} onChange={wert => { setzeSeite(0); setzeNurEigene(wert === 'eigen'); }} disabled={!nutzer} /></label>
        <p className="text-sm text-slate-600">Terminlose Vorgänge stehen als „Termin klären“ in der Liste. Erledigte Vorgänge kommen vom Server nicht mehr zurück.</p>
      </div>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laden ? <p role="status" className="rounded-lg border border-slate-200 bg-white p-5">Fällige Vorgänge werden geladen …</p>
        : fälligkeiten.length === 0 ? <section className="rounded-lg border border-slate-200 bg-white p-8 text-center"><CalendarClock className="mx-auto mb-3 h-8 w-8 text-slate-400" /><h2 className="font-semibold">Alles erledigt</h2><p className="mt-1 text-sm text-slate-600">Es sind keine offenen Einkaufsvorgänge fällig.</p></section>
        : <ul className="space-y-3">{fälligkeiten.map(zeile => <li key={`${zeile.typ}-${zeile.vorgangId}-${zeile.beteiligungId ?? 'gesamt'}`}><article className="grid min-w-0 gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[minmax(0,1fr)_auto]">
          <div className="min-w-0"><p className="text-sm font-semibold text-rose-600">{titel(zeile.typ)} · {zeile.nummer ?? `Vorgang ${zeile.vorgangId}`}</p><h2 className="mt-1 break-words font-semibold text-slate-900">{zeile.hinweis ?? titel(zeile.typ)}</h2><p className="mt-1 text-sm text-slate-600">Fällig: {datum(zeile.frist)} · Zuständig: {zeile.zustaendigId ? (zeile.zustaendigId === nutzer?.id && nutzer.displayName ? nutzer.displayName : 'Zugewiesene Person') : 'Noch nicht zugeordnet'}</p>
            <Link className="mt-2 inline-flex text-sm font-medium text-rose-700 underline-offset-2 hover:underline" to={linkZumVorgang(zeile)}>Vorgang {zeile.nummer ?? zeile.vorgangId} öffnen</Link></div>
          <div className="flex flex-wrap items-center gap-2 md:justify-end"><Button size="sm" variant="outline" disabled={vorschauLaden === zeile.vorgangId} onClick={() => void nachfrageVorbereiten(zeile)}>{vorschauLaden === zeile.vorgangId ? 'Vorschau wird erstellt …' : 'Nachfrage vorbereiten'}</Button></div>
        </article></li>)}</ul>}
      {!laden && seitenGesamt > 1 && <div className="flex items-center justify-center gap-3"><Button variant="outline" size="sm" disabled={seite === 0} onClick={() => setzeSeite(wert => Math.max(0, wert - 1))}>Vorige</Button><span className="text-sm text-slate-600">Seite {seite + 1} von {seitenGesamt}</span><Button variant="outline" size="sm" disabled={seite + 1 >= seitenGesamt} onClick={() => setzeSeite(wert => wert + 1)}>Weitere</Button></div>}
    </div>
    {vorschau && <Dialog open onOpenChange={offen => { if (!offen) setzeVorschau(null); }}><DialogContent className="max-w-3xl"><DialogHeader><DialogTitle>Nachfrage prüfen · {titel(vorschau.typ)}</DialogTitle></DialogHeader><dl className="grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2"><div><dt className="font-semibold">Empfänger</dt><dd className="break-all">{vorschau.empfaenger}</dd></div><div><dt className="font-semibold">Vorlage</dt><dd>Fassung {vorschau.vorlageVersion}</dd></div><div className="sm:col-span-2"><dt className="font-semibold">Betreff</dt><dd>{vorschau.subject}</dd></div><div className="sm:col-span-2"><dt className="font-semibold">Nachricht</dt><dd className="whitespace-pre-wrap">{unescapeHtmlEntities(stripHtmlTags(vorschau.htmlBody))}</dd></div></dl>
      {vorschau.fehlendeNachweise.length > 0 && <p className="text-sm text-amber-800">Fehlende Angaben: {vorschau.fehlendeNachweise.join(', ')}</p>}
      <p className="text-sm text-slate-600">Diese Vorschau sendet nichts. Öffnen Sie den Vorgang, um den vorhandenen Versandweg zu prüfen und ausdrücklich freizugeben.</p>
      <DialogFooter><Button variant="outline" onClick={() => setzeVorschau(null)}>Zurück zur Liste</Button><Link className="inline-flex min-h-10 items-center rounded-lg border border-rose-600 bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700" to={linkZumVorgang(vorschau)} onClick={() => setzeVorschau(null)}>Vorgang und Verlauf öffnen</Link></DialogFooter></DialogContent></Dialog>}
  </PageLayout>;
}
