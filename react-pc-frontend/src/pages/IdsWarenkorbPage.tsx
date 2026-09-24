import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink, FolderOpen, Loader2, RefreshCw, ShoppingCart } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { submitPunchoutForm } from '../lib/idsPunchout';
import type { IdsDraft } from '../types/ids';

const decimal = (value: number) => value.toLocaleString('de-DE', { maximumFractionDigits: 4 });
const projektPfad = (projektId: number) => `/bestellungen/bedarf/projekt/${encodeURIComponent(String(projektId))}`;
const projektLabel = (draft: IdsDraft) => draft.projektName || `Projekt #${draft.projektId}`;
function ProjektLink({ draft }: { draft: IdsDraft }) {
  if (draft.projektId == null) return null;
  return <Link to={projektPfad(draft.projektId)} className="inline-flex items-center gap-1.5 text-sm text-rose-700 hover:underline focus-visible:outline-rose-600">
    <FolderOpen className="h-4 w-4" />Projekt: {projektLabel(draft)}
  </Link>;
}
const money = (value: number, currency?: string, maxDigits = 2) => `${value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: maxDigits })}${currency ? ` ${currency}` : ' (Währung offen)'}`;
// Würth liefert in NetPrice den Netto-Betrag der ganzen Position (Menge × Einzelpreis), nicht den Preis je PriceBasis.
const einzelpreis = (positionsbetrag: number, menge: number) => positionsbetrag / menge;

export default function IdsWarenkorbPage() {
  const { id } = useParams();
  const toast = useToast();
  const [result, setResult] = useState<{ id?: string; drafts: IdsDraft[] }>({ drafts: [] });
  const generation = useRef(0);
  const drafts = result.id === id ? result.drafts : [];
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sending, setSending] = useState(false);
  const [opened, setOpened] = useState(false);
  // Für welche Adresse der Warenkorb schon einmal fertig geladen wurde – ab dann lädt der Fokus still nach.
  const loadedFor = useRef<string | undefined | null>(null);
  const visibleLoad = useRef(false);
  /** „silent“ = Fokus-Refresh: kein Skeleton, kein Toast, bisheriger Inhalt bleibt bei Fehlern stehen. */
  const load = useCallback(async (silent = false) => {
    if (silent && (loadedFor.current !== id || visibleLoad.current)) return;
    const requestId = ++generation.current;
    if (!silent) { visibleLoad.current = true; setLoading(true); setError(''); }
    try {
      const response = await fetch(`/api/ids/warenkoerbe${id ? `/${encodeURIComponent(id)}` : ''}`);
      if (!response.ok) throw new Error('Würth-Warenkorb konnte nicht geladen werden.');
      const data = await response.json().catch(() => { throw new Error('Würth-Warenkorb konnte nicht geladen werden.'); });
      if (requestId === generation.current) { setResult({ id, drafts: id ? [data] : data }); setError(''); }
    } catch (cause) {
      if (requestId !== generation.current || silent) return;
      setResult({ id, drafts: [] });
      const message = cause instanceof Error ? cause.message : 'Warenkorb konnte nicht geladen werden.';
      setError(message); toast.error(message);
    } finally {
      if (requestId === generation.current) { loadedFor.current = id; visibleLoad.current = false; setLoading(false); }
    }
  }, [id, toast]);
  const invalidate = useCallback(() => { generation.current++; visibleLoad.current = false; }, []);
  useEffect(() => { void load(); return invalidate; }, [load, invalidate]);
  useEffect(() => {
    // Nach der Rückgabe im Shop-Tab still den neuen Stand holen, ohne den Warenkorb kurz auszublenden.
    const onFocus = () => { void load(true); };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [load]);
  const draft = id ? drafts[0] : undefined;
  const send = async () => {
    if (!draft || error || loading) return;
    setSending(true);
    try {
      const response = await fetch(`/api/ids/warenkoerbe/${encodeURIComponent(draft.id)}/senden`, { method: 'POST' });
      if (!response.ok) {
        // Kein oder kaputtes JSON (z. B. HTML-Fehlerseite): eigene deutsche Meldung statt SyntaxError.
        const data: { message?: unknown } = await response.json().catch(() => ({}));
        throw new Error(typeof data.message === 'string' && data.message ? data.message : 'Warenkorb konnte nicht an Würth übergeben werden.');
      }
      submitPunchoutForm(await response.json());
      setOpened(true);
      toast.success('Warenkorb an Würth übergeben. Bitte Bestellung im Shop prüfen und bestätigen.');
    } catch (cause) { toast.error(cause instanceof Error ? cause.message : 'Würth-Shop konnte nicht geöffnet werden.'); }
    finally { setSending(false); }
  };
  const allPriced = draft?.items.every(item => item.netPrice != null);
  const total = draft?.items.reduce((sum, item) => sum + (item.netPrice ?? 0), 0) ?? 0;
  return <PageLayout ribbonCategory="Beschaffung" title={id ? 'Würth-Warenkorb' : 'Shop-Warenkörbe'}
    subtitle={draft ? `Interne Bestellnummer ${draft.number}` : 'Übernommene Artikel prüfen und beim Lieferanten bestellen.'}
    actions={<>
      <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}><RefreshCw className="w-4 h-4 mr-2" />Aktualisieren</Button>
      {draft && !error && !draft.ordered && <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void send()} disabled={sending || loading}>
        {sending ? <Loader2 className="w-4 h-4 mr-2 motion-safe:animate-spin" /> : <ExternalLink className="w-4 h-4 mr-2" />}Bei Würth bestellen
      </Button>}
    </>}>
    {draft?.projektId != null
      ? <Link className="inline-flex items-center gap-2 text-rose-700 hover:underline" to={projektPfad(draft.projektId)}><ArrowLeft className="w-4 h-4" />Zurück zum Projektbedarf</Link>
      : <Link className="inline-flex items-center gap-2 text-rose-700 hover:underline" to={id ? '/bestellungen/ids' : '/bestellungen/bedarf'}><ArrowLeft className="w-4 h-4" />{id ? 'Alle Shop-Warenkörbe' : 'Zur Bedarfsübersicht'}</Link>}
    {loading ? <div role="status" className="rounded-lg bg-slate-100 p-8 motion-safe:animate-pulse">Warenkorb wird geladen …</div> : error ? <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">{error}</p> : draft ? <>
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-slate-700" role="status">
        <p className="font-semibold">{draft.ordered ? 'Bei Würth bestellt' : draft.projektId != null ? 'Am Projekt geparkt – noch nicht bestellt' : 'Übernommen – noch nicht bestellt'}</p>
        {draft.projektId != null && <p className="mt-1"><ProjektLink draft={draft} /></p>}
        <p className="mt-1 text-sm">{draft.ordered ? `Würth hat die Bestellung bestätigt${draft.reference ? `: ${draft.reference}` : '.'}` : '„Bei Würth bestellen“ überträgt diese Positionen in den Würth-Shop. Dort prüfst du Preise, Lieferung und bestätigst die Bestellung verbindlich.'}</p>
        {opened && !draft.ordered && <p className="mt-2 text-sm">Der Shop wurde geöffnet. Nach der Bestellung den Warenkorb an die Anwendung zurückgeben, damit der Bestellstatus übernommen wird.</p>}
      </div>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full table-fixed text-sm">
          <thead className="bg-slate-50 text-left text-slate-600"><tr>
            <th className="w-[42%] p-4">Artikel</th><th className="w-[16%] p-4">Menge</th><th className="w-[23%] p-4">Einzelpreis netto</th><th className="w-[19%] p-4 text-right">Betrag netto</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-100">{draft.items.map((item, index) => <tr key={`${item.article}-${index}`}>
            <td className="p-4 align-top break-words"><p className="font-semibold text-slate-900">{item.name}</p><p className="mt-1 font-mono text-xs text-slate-600">Art.-Nr. {item.article}</p>
              {item.description && item.description !== item.name && <p className="mt-2 whitespace-pre-wrap text-slate-600">{item.description}</p>}
              {item.ean && <p className="mt-1 text-xs text-slate-500">EAN {item.ean}</p>}{item.hint && <p className="mt-2 text-rose-700">{item.hint}</p>}
            </td>
            <td className="p-4 align-top">{decimal(item.quantity)} {item.unit}</td>
            <td className="p-4 align-top">{item.netPrice == null ? 'Preis offen' : <>{money(einzelpreis(item.netPrice, item.quantity), draft.currency, 4)}<span className="block text-slate-500">je {item.unit}</span></>}
              {item.vat != null && <span className="mt-1 block text-xs text-slate-500">MwSt. {decimal(item.vat)} %</span>}</td>
            <td className="p-4 align-top text-right font-medium">{item.netPrice == null ? 'Offen' : money(item.netPrice, draft.currency)}</td>
          </tr>)}</tbody>
        </table>
        <div className="border-t border-slate-200 p-4 text-right font-semibold">{allPriced ? `Warenwert netto: ${money(total, draft.currency)}` : 'Gesamtbetrag offen – Preise fehlen noch'}</div>
      </div>
      <p className="text-sm text-slate-500">Übernommen werden die von Würth gelieferten Angaben. Fehlende Preise sind weiterhin offen. Versandkosten und weitere Zuschläge werden im Shop geprüft.{draft.offerNumber && ` Angebot: ${draft.offerNumber}.`}{draft.deliveryDate && ` Lieferdatum: ${draft.deliveryDate}.`}</p>
    </> : drafts.length ? <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">{drafts.map(item => <li key={item.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 p-5 hover:bg-rose-50/60">
      <span className="flex items-center gap-3"><ShoppingCart className="h-5 w-5 text-rose-600" /><span>
        <Link to={`/bestellungen/ids/${encodeURIComponent(item.id)}`} className="block font-semibold text-slate-900 hover:text-rose-700 hover:underline focus-visible:outline-rose-600">Würth · {item.number}</Link>
        <span className="text-sm text-slate-500">{item.items.length} {item.items.length === 1 ? 'Position' : 'Positionen'}</span>
        {item.projektId != null && <span className="block"><ProjektLink draft={item} /></span>}
      </span></span><span className="text-sm text-slate-600">{item.ordered ? 'Bestellt' : 'Noch nicht bestellt'}</span>
    </li>)}</ul> : <p className="rounded-lg border border-slate-200 bg-white p-8 text-slate-500">Noch keine Shop-Warenkörbe übernommen. Öffne den Lieferanten-Shop aus deinem Bedarf und gib den Warenkorb an die Anwendung zurück.</p>}
  </PageLayout>;
}
