import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../../components/ui/button';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { Input } from '../../../components/ui/input';
import { CreateReklamationModal } from '../../../components/CreateReklamationModal';
import { useToast } from '../../../components/ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { einkaufApi } from '../api';
import type { BestellungMitNachweisen, BestellungRevision } from '../bestellTypes';
import type { PositionAbgleich, Rechnungsabgleich as AbgleichDto } from '../types';
import { BelegAuswahl } from './BelegAuswahl';
import type { BestellBelegDatei } from '../belegApi';

type BelegArt = 'RECHNUNG' | 'NACHBERECHNUNG' | 'GUTSCHRIFT' | 'STORNO';
interface Draft { menge: string; preis: string }
interface ReklamationsBezug { bestellungId: number; bestellPositionId: number; rechnungId: number; lieferscheinId: number | null; beschreibung: string }
const typFuerBeleg = (art: BelegArt) => ['GUTSCHRIFT', 'STORNO'].includes(art) ? 'GUTSCHRIFT' : 'RECHNUNG';
const format = (value: number | null, unit?: string) => value == null ? 'fehlt' : `${value.toLocaleString('de-DE')} ${unit ?? ''}`.trim();

export function Rechnungsabgleich({ bestellungId }: { bestellungId: number }) {
  const toast = useToast();
  const [abgleich, setAbgleich] = useState<AbgleichDto | null>(null);
  const [bestellung, setBestellung] = useState<BestellungMitNachweisen | null>(null);
  const [art, setArt] = useState<BelegArt>('RECHNUNG');
  const [beleg, setBeleg] = useState<BestellBelegDatei | null>(null);
  const [bezugsRechnungId, setBezugsRechnungId] = useState('');
  const [entwuerfe, setEntwuerfe] = useState<Record<number, Draft>>({});
  const [reklamation, setReklamation] = useState<ReklamationsBezug | null>(null);
  const [fehler, setFehler] = useState('');
  const [laden, setLaden] = useState(true);
  const [speichert, setSpeichert] = useState(false);

  const ladeDaten = useCallback(async () => {
    setLaden(true); setFehler('');
    try {
      const [comparison, order] = await Promise.all([
        einkaufApi.get<AbgleichDto>(`/api/einkauf/bestellungen/${bestellungId}/rechnungsabgleich`),
        einkaufApi.get<BestellungMitNachweisen>(`/api/einkauf/bestellungen/${bestellungId}`),
      ]);
      setAbgleich(comparison); setBestellung(order);
      const current = [...order.revisionen].filter(r => r.angenommenAm && !r.verworfen).sort((a, b) => b.nummer - a.nummer)[0];
      setEntwuerfe(Object.fromEntries((current?.positionen ?? []).map(p => [p.id, { menge: '', preis: '' }])));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Rechnungsabgleich konnte nicht geladen werden.';
      setFehler(message); toast.error(message);
    } finally { setLaden(false); }
  }, [bestellungId, toast]);
  useEffect(() => { void ladeDaten(); }, [ladeDaten]);

  const revision: BestellungRevision | null = useMemo(() => bestellung?.revisionen.filter(r => r.angenommenAm && !r.verworfen)
    .sort((a, b) => b.nummer - a.nummer)[0] ?? null, [bestellung]);
  const positions = revision?.positionen ?? [];
  const sourceBills = useMemo(() => [...new Set(abgleich?.positionen.flatMap(row => row.quellen
    .filter(source => source.typ === 'BELEG' && source.id != null)
    .map(source => source.id as number)) ?? [])], [abgleich]);

  const updateDraft = (id: number, field: keyof Draft, value: string) => setEntwuerfe(current => {
    const previous = current[id] ?? { menge: '', preis: '' };
    return { ...current, [id]: { ...previous, [field]: value } };
  });

  const zuordnen = async () => {
    if (!abgleich || !bestellung || !revision) return;
    if (!beleg) { setFehler('Bitte wählen Sie eine Rechnung oder Gutschrift aus oder laden Sie sie hoch.'); return; }
    const correction = art !== 'RECHNUNG';
    const bezug = bezugsRechnungId ? Number(bezugsRechnungId) : null;
    if (correction && (!bezug || !sourceBills.includes(bezug))) { setFehler('Für eine Nachberechnung oder Gutschrift wählen Sie die zugehörige Rechnung aus.'); return; }
    const entries = [];
    for (const position of positions) {
      const draft = entwuerfe[position.id] ?? { menge: '', preis: '' };
      if (!draft.menge.trim()) continue;
      const quantity = validateDecimalInput(draft.menge, { label: 'Rechnungsmenge', min: 0.000001, required: true });
      if (!quantity.valid || quantity.value === null) { setFehler(`Bitte geben Sie eine gültige Rechnungsmenge für ${position.snapshot.bezeichnung ?? `Position ${position.id}`} ein.`); return; }
      const price = validateDecimalInput(draft.preis, { label: 'Netto-Einzelpreis', min: 0, required: true });
      if (!price.valid || price.value === null) { setFehler(`Bitte geben Sie einen gültigen Netto-Einzelpreis für ${position.snapshot.bezeichnung ?? `Position ${position.id}`} ein.`); return; }
      entries.push({ originalPositionsnummer: String(position.id), bestellPositionId: position.id, menge: quantity.value,
        einheit: position.snapshot.basis?.einheit ?? 'STUECK', nettoEinzelpreis: price.value, preisBasisMenge: 1,
        nurPreisKorrektur: false, kosten: [], quellen: [] });
    }
    if (!entries.length) { setFehler('Geben Sie mindestens eine Rechnungsposition ein.'); return; }
    setSpeichert(true); setFehler('');
    try {
      await einkaufApi.post(`/api/einkauf/belege/${beleg.lieferantDokumentId}/zuordnung`, {
        bestellungId, version: bestellung.version, art, bezugsDokumentId: bezug, positionen: entries, idempotenzKey: crypto.randomUUID(),
      });
      toast.success('Rechnungsbeleg wurde zugeordnet.'); setBeleg(null); setBezugsRechnungId('');
      await ladeDaten();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Rechnungsbeleg konnte nicht zugeordnet werden.';
      setFehler(message); toast.error(message);
    } finally { setSpeichert(false); }
  };

  const starteReklamation = (row: PositionAbgleich, deviation: PositionAbgleich['abweichungen'][number]) => {
    if (!bestellung) return;
    const invoiceId = deviation.quellen.find(source => source.id != null)?.id;
    if (!invoiceId) { const message = 'Zur Abweichung fehlt ein verknüpfter Rechnungsbeleg.'; setFehler(message); toast.error(message); return; }
    setReklamation({ bestellungId, bestellPositionId: row.positionId, rechnungId: invoiceId, lieferscheinId: null,
      beschreibung: `Abweichung bei ${row.bezeichnung}: ${deviation.rechenweg ?? deviation.feld}.` });
  };

  if (laden) return <section aria-label="Rechnungsabgleich"><p role="status">Rechnungsabgleich wird geladen …</p></section>;
  if (!abgleich || !bestellung) return <section aria-label="Rechnungsabgleich"><p role="alert" className="text-sm text-rose-700">{fehler || 'Rechnungsabgleich konnte nicht geladen werden.'}</p></section>;

  return <section aria-label="Rechnungsabgleich" className="space-y-4">
    <header><h2 className="text-lg font-semibold text-slate-900">Rechnungsabgleich</h2><p className="text-sm text-slate-600">Teilrechnungen und Korrekturen werden gegen ihren belegten Anteil gerechnet.</p></header>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <div className="space-y-3">{abgleich.positionen.map(row => <article key={row.positionId} className="rounded-lg border border-slate-200 bg-white p-4">
      <h3 className="font-semibold text-slate-900">{row.bezeichnung}</h3>
      <div className="mt-2 grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <p className="rounded bg-slate-50 p-2">Vereinbart: {format(row.vereinbart, row.einheit)}</p>
        <p className="rounded bg-slate-50 p-2">Bestätigt: {format(row.bestaetigt, row.einheit)}</p>
        <p className="rounded bg-slate-50 p-2">Geliefert: {format(row.geliefert, row.einheit)}</p>
        <p className="rounded bg-slate-50 p-2">Abgerechnet: {format(row.kumuliertAbgerechnet, row.einheit)}</p>
      </div>
      {row.pruefen && <p className="mt-2 rounded bg-amber-50 p-2 text-sm text-amber-900">Belegzuordnung oder Rechenangaben bitte prüfen.</p>}
      {row.abweichungen.map((deviation, index) => <div key={`${deviation.feld}-${index}`} className="mt-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm">
        <p className="font-semibold text-amber-950">Abweichung: {deviation.feld} · Differenz {format(deviation.differenz, row.einheit)}</p>
        <p className="mt-1 text-amber-900">Rechenweg: {deviation.rechenweg ?? 'Keine Rechengrundlage hinterlegt.'}</p>
        <div className="mt-2 flex flex-wrap gap-3">{deviation.quellen.map((source, sourceIndex) => source.typ === 'BELEG' && source.id != null
          ? <Link key={`${source.typ}-${source.id}-${sourceIndex}`} to={`/offeneposten?tab=eingang&dokumentId=${source.id}`} className="text-xs font-medium text-rose-700 hover:underline">Rechnung {source.bezeichnung ?? source.id} in den offenen Posten ansehen</Link>
          : <span key={`${source.typ}-${source.id}-${sourceIndex}`} className="text-xs text-slate-700">{source.typ} {source.bezeichnung ?? source.id ?? 'ohne Referenz'}</span>)}
          <Button type="button" size="sm" variant="outline" onClick={() => starteReklamation(row, deviation)}>Abweichung reklamieren · {row.bezeichnung}</Button>
        </div>
      </div>)}
    </article>)}</div>
    {abgleich.unbelegteDokumentIds.length > 0 && <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">Nicht zugeordnete Belege: {abgleich.unbelegteDokumentIds.join(', ')}. Ordnen Sie sie einer Bestellposition zu.</p>}
    <Link className="inline-flex text-sm font-medium text-rose-700 hover:underline" to="/offeneposten">Kostenübernahme und Zahlung im bestehenden Rechnungsbereich öffnen</Link>
    <section className="space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <header><h3 className="font-semibold text-slate-900">Rechnung oder Korrektur erfassen</h3><p className="text-sm text-slate-600">Erfassen Sie nur den belegten Teilbetrag. Gutschriften und Nachberechnungen bleiben mit ihrer Ursprungsrechnung verknüpft.</p></header>
      <div className="flex flex-wrap gap-2" aria-label="Belegart">{(['RECHNUNG', 'NACHBERECHNUNG', 'GUTSCHRIFT', 'STORNO'] as const).map(item => <Button key={item} type="button" size="sm" variant={art === item ? 'default' : 'outline'} aria-pressed={art === item} onClick={() => { setArt(item); setBeleg(null); }}>{item === 'RECHNUNG' ? 'Rechnung' : item === 'NACHBERECHNUNG' ? 'Nachberechnung' : item === 'GUTSCHRIFT' ? 'Gutschrift' : 'Storno'}</Button>)}</div>
      {art !== 'RECHNUNG' && <label className="block text-sm font-medium text-slate-700">Ursprüngliche Rechnungs-ID
        <Input aria-label="Ursprüngliche Rechnungs-ID" inputMode="numeric" value={bezugsRechnungId} onChange={event => setBezugsRechnungId(event.target.value)} placeholder={sourceBills.join(', ') || 'Zuerst eine Rechnung zuordnen'} />
      </label>}
      <BelegAuswahl bestellungId={bestellungId} typ={typFuerBeleg(art)} value={beleg} onChange={setBeleg} />
      <div className="space-y-3">{positions.map(position => <fieldset key={position.id} className="grid gap-3 rounded-md border border-slate-200 bg-white p-3 sm:grid-cols-2">
        <legend className="px-1 text-sm font-semibold">{position.snapshot.bezeichnung ?? `Position ${position.id}`} · {position.snapshot.basis?.einheit ?? 'STUECK'}</legend>
        <DecimalInput label={`Rechnungsmenge · ${position.snapshot.bezeichnung ?? position.id}`} value={entwuerfe[position.id]?.menge ?? ''} onChange={value => updateDraft(position.id, 'menge', value)} min={0} />
        <DecimalInput label={`Netto-Einzelpreis · ${position.snapshot.bezeichnung ?? position.id}`} value={entwuerfe[position.id]?.preis ?? ''} onChange={value => updateDraft(position.id, 'preis', value)} min={0} />
      </fieldset>)}</div>
      <Button type="button" onClick={() => void zuordnen()} disabled={speichert || !revision}>{speichert ? 'Beleg wird zugeordnet …' : art === 'RECHNUNG' ? 'Rechnung zuordnen' : `${art === 'GUTSCHRIFT' ? 'Gutschrift' : art === 'STORNO' ? 'Storno' : 'Nachberechnung'} zuordnen`}</Button>
    </section>
    <CreateReklamationModal isOpen={!!reklamation} onClose={() => setReklamation(null)} lieferantId={bestellung.lieferantId}
      initialBezug={reklamation ?? undefined} onSuccess={() => { setReklamation(null); toast.success('Reklamation mit Belegbezug erstellt.'); }} />
  </section>;
}
