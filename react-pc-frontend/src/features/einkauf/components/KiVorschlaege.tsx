import { useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { useToast } from '../../../components/ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { einkaufApi } from '../api';

interface Job { id: number; angebotId: number; status: string; hinweis?: string | null }
interface Vorschlag { feldpfad: string; wert: unknown; quelle: { emailId: number | null; dateiId: number | null; seite: number | null; zitat: string | null } | null; confidence: number | null; hinweis: string | null }
interface Props { jobId: number; onUebernommen: () => void }
const text = (value: unknown) => typeof value === 'string' ? value : value == null ? '' : JSON.stringify(value);

export function KiVorschlaege({ jobId, onUebernommen }: Props) {
  const toast = useToast(); const [job, setJob] = useState<Job | null>(null); const [vorschlaege, setVorschlaege] = useState<Vorschlag[]>([]); const [selected, setSelected] = useState<string[]>([]); const [korrekturen, setKorrekturen] = useState<Record<string, string>>({}); const [fehler, setFehler] = useState(''); const [busy, setBusy] = useState(false);
  useEffect(() => {
    let aktiv = true;
    Promise.all([einkaufApi.get<Job>(`/api/einkauf/analysen/${jobId}`), einkaufApi.get<Vorschlag[]>(`/api/einkauf/analysen/${jobId}/vorschlaege`)]).then(([j, v]) => { if (aktiv) { setJob(j); setVorschlaege(v); } }).catch(error => { if (aktiv) setFehler(error instanceof Error ? error.message : 'Analysevorschläge konnten nicht geladen werden.'); });
    return () => { aktiv = false; };
  }, [jobId]);
  const uebernehmen = async () => {
    if (!job || selected.length === 0) { toast.error('Wählen Sie mindestens einen Vorschlag ausdrücklich aus.'); return; }
    setBusy(true);
    try {
      const angebot = await einkaufApi.get<{ versionen: Array<{ version: number; id: number }> }>(`/api/einkauf/angebote/${job.angebotId}`);
      const latest = angebot.versionen.at(-1); if (!latest) throw new Error('Zum Angebot liegt keine gespeicherte Fassung vor.');
      const acceptedCorrections: Record<string, unknown> = {};
      for (const field of selected) {
        const draft = korrekturen[field]; if (draft == null || draft === '') continue;
        const proposal = vorschlaege.find(item => item.feldpfad === field);
        if (typeof proposal?.wert === 'number') {
          const parsed = validateDecimalInput(draft, { label: `Korrektur ${field}`, required: true });
          if (!parsed.valid) { toast.error(parsed.message); return; }
          acceptedCorrections[field] = parsed.value;
        } else acceptedCorrections[field] = draft;
      }
      await einkaufApi.post(`/api/einkauf/analysen/${jobId}/uebernehmen`, { erwarteteAngebotVersion: latest.version, akzeptierteFeldpfade: selected, korrekturen: acceptedCorrections });
      toast.success('Ausgewählte Werte wurden als neue Angebotsfassung übernommen.'); onUebernommen();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Vorschläge konnten nicht übernommen werden.'); }
    finally { setBusy(false); }
  };
  return <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4"><div><h2 className="font-semibold">KI-Vorschläge prüfen</h2><p className="text-sm text-slate-600">Keine Angabe wird automatisch übernommen. Markierte Werte und Korrekturen werden als neue Angebotsfassung gespeichert.</p></div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}{!job ? <p role="status">Analyse wird geladen …</p> : <><p className="text-sm">Analyse: {job.status}{job.hinweis ? ` · ${job.hinweis}` : ''}</p>{vorschlaege.length === 0 ? <p className="text-sm text-slate-600">Für diese Analyse liegen keine Feldvorschläge vor.</p> : <ul className="space-y-3">{vorschlaege.map(item => <li key={item.feldpfad} className="rounded-md border border-slate-200 p-3"><label className="flex items-start gap-2 text-sm font-medium"><input aria-label={`Vorschlag übernehmen ${item.feldpfad}`} type="checkbox" checked={selected.includes(item.feldpfad)} onChange={event => setSelected(current => event.target.checked ? [...current, item.feldpfad] : current.filter(field => field !== item.feldpfad))} />{item.feldpfad} · Vorschlag: {text(item.wert) || 'offen'}{item.confidence != null && <span className="text-slate-500"> ({Math.round(item.confidence * 100)}% Sicherheit)</span>}</label><label className="mt-2 block text-sm">Korrektur für {item.feldpfad}<input className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={korrekturen[item.feldpfad] ?? ''} onChange={event => setKorrekturen(current => ({ ...current, [item.feldpfad]: event.target.value }))} /></label>{item.quelle && <p className="mt-2 text-xs text-slate-600">Quelle: {item.quelle.dateiId ? `Datei ${item.quelle.dateiId}` : `E-Mail ${item.quelle.emailId ?? 'unbekannt'}`}{item.quelle.seite ? ` · Seite ${item.quelle.seite}` : ''}{item.quelle.zitat ? ` · „${item.quelle.zitat}“` : ''}</p>}{item.hinweis && <p className="mt-1 text-xs text-amber-800">{item.hinweis}</p>}</li>)}</ul>}<Button disabled={busy || selected.length === 0} onClick={() => void uebernehmen()}>{busy ? 'Übernimmt …' : 'Auswahl übernehmen'}</Button></>}
  </section>;
}
