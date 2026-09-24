import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi } from '../api';
import type { Dokumentart, DokumentSoll, ZeugnisVorlage } from '../types';

const dokumentarten: Array<{ art: Dokumentart; titel: string }> = [
  { art: 'ZEUGNIS_2_1', titel: 'Zeugnis 2.1' },
  { art: 'ZEUGNIS_2_2', titel: 'Zeugnis 2.2' },
  { art: 'ZEUGNIS_3_1', titel: 'Zeugnis 3.1' },
  { art: 'ZEUGNIS_3_2', titel: 'Zeugnis 3.2' },
  { art: 'LEISTUNGSERKLAERUNG', titel: 'Leistungserklärung' },
  { art: 'CE_NACHWEIS', titel: 'CE-Nachweis' },
];
const positiveId = (value: string) => /^\d+$/.test(value.trim()) && Number(value) > 0 && Number.isSafeInteger(Number(value));

export function AnforderungsVorlagen() {
  const toast = useToast();
  const [artikelId, setArtikelId] = useState('');
  const [projektId, setProjektId] = useState('');
  const [art, setArt] = useState<Dokumentart>('ZEUGNIS_3_1');
  const [grundlage, setGrundlage] = useState('');
  const [vorgaben, setVorgaben] = useState<DokumentSoll[]>([]);
  const [gespeichert, setGespeichert] = useState<ZeugnisVorlage | null>(null);
  const [fehler, setFehler] = useState('');
  const [laedt, setLaedt] = useState(false);
  const [speichert, setSpeichert] = useState(false);

  const ladeVorgaben = async () => {
    const article = artikelId.trim(); const project = projektId.trim();
    if ((!positiveId(article) && !positiveId(project)) || (article && !positiveId(article)) || (project && !positiveId(project))) {
      const message = 'Geben Sie eine gültige Artikel- oder Projekt-ID ein.'; setFehler(message); toast.error(message); return;
    }
    const params = new URLSearchParams();
    if (positiveId(article)) params.set('artikelId', article);
    if (positiveId(project)) params.set('projektId', project);
    setLaedt(true); setFehler('');
    try { setVorgaben(await einkaufApi.get<DokumentSoll[]>(`/api/einkauf/anforderungsvorlagen?${params.toString()}`)); }
    catch (error) { const message = error instanceof Error ? error.message : 'Vorgaben konnten nicht geladen werden.'; setFehler(message); toast.error(message); }
    finally { setLaedt(false); }
  };

  const speichern = async () => {
    const article = artikelId.trim(); const project = projektId.trim();
    if ((!positiveId(article) && !positiveId(project)) || (article && !positiveId(article)) || (project && !positiveId(project))) {
      const message = 'Geben Sie eine gültige Artikel- oder Projekt-ID ein.'; setFehler(message); toast.error(message); return;
    }
    if (!grundlage.trim()) { const message = 'Bitte dokumentieren Sie die fachliche Grundlage.'; setFehler(message); toast.error(message); return; }
    if (grundlage.trim().length > 1000) { const message = 'Die fachliche Grundlage darf höchstens 1.000 Zeichen lang sein.'; setFehler(message); toast.error(message); return; }
    setSpeichert(true); setFehler(''); setGespeichert(null);
    try {
      const created = await einkaufApi.post<ZeugnisVorlage>('/api/einkauf/anforderungsvorlagen', {
        artikelId: positiveId(article) ? Number(article) : null,
        projektId: positiveId(project) ? Number(project) : null,
        art,
        grundlage: grundlage.trim(),
      });
      setGespeichert(created); setVorgaben(current => [{ art: created.art, grundlage: created.grundlage, grundlageVersion: created.grundlageVersion, fachlichBestaetigt: created.fachlichBestaetigt }, ...current.filter(x => x.art !== created.art)]);
      toast.success(`Neue Vorgabe ${created.grundlageVersion} gespeichert.`);
    } catch (error) { const message = error instanceof Error ? error.message : 'Vorgabe konnte nicht gespeichert werden.'; setFehler(message); toast.error(message); }
    finally { setSpeichert(false); }
  };

  return <section aria-label="Zeugnisvorgaben pflegen" className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
    <header><h2 className="text-lg font-semibold text-slate-900">Zeugnisvorgaben pflegen</h2><p className="text-sm text-slate-600">Grundlage und Version manuell dokumentieren. Es wird keine EN-1090-Erfüllung automatisch berechnet.</p></header>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="text-sm font-medium text-slate-700">Artikelnummer<Input aria-label="Artikelnummer" inputMode="numeric" value={artikelId} onChange={event => setArtikelId(event.target.value)} placeholder="Zum Beispiel 15" /></label>
      <label className="text-sm font-medium text-slate-700">Projekt-ID<Input aria-label="Projekt-ID" inputMode="numeric" value={projektId} onChange={event => setProjektId(event.target.value)} placeholder="Optional" /></label>
    </div>
    <Button type="button" variant="outline" onClick={() => void ladeVorgaben()} disabled={laedt}>{laedt ? 'Vorgaben werden geladen …' : 'Vorgaben laden'}</Button>
    {vorgaben.length > 0 && <section aria-label="Aktuelle Vorgaben" className="rounded-lg bg-slate-50 p-3">
      <h3 className="mb-2 text-sm font-semibold">Aktuelle bestätigte Vorgaben</h3>
      <ul className="space-y-1">{vorgaben.map((row, index) => <li key={`${row.art}-${row.grundlageVersion}-${index}`} className="text-sm text-slate-700">{dokumentarten.find(item => item.art === row.art)?.titel ?? row.art} · {row.grundlage ?? 'Grundlage fehlt'} · {row.grundlageVersion ?? 'Version fehlt'}{row.fachlichBestaetigt ? ' · bestätigt' : ' · Prüfung erforderlich'}</li>)}</ul>
    </section>}
    <fieldset className="space-y-2"><legend className="text-sm font-semibold text-slate-800">Dokumentart</legend>
      <div className="flex flex-wrap gap-2">{dokumentarten.map(item => <Button key={item.art} type="button" size="sm" variant={art === item.art ? 'default' : 'outline'} aria-pressed={art === item.art} onClick={() => setArt(item.art)}>{item.titel}</Button>)}</div>
    </fieldset>
    <label className="block text-sm font-medium text-slate-700">Fachliche Grundlage
      <textarea aria-label="Fachliche Grundlage" value={grundlage} onChange={event => setGrundlage(event.target.value)} maxLength={1000} rows={3} className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-200" placeholder="Zum Beispiel EN 10204, Ausgabe 2026" />
    </label>
    <Button type="button" onClick={() => void speichern()} disabled={speichert}>{speichert ? 'Vorgabe wird gespeichert …' : 'Vorgabe speichern'}</Button>
    {gespeichert && <p role="status" className="text-sm text-emerald-800">Neue Vorgabe {gespeichert.grundlageVersion} gespeichert: {gespeichert.grundlage}</p>}
  </section>;
}
