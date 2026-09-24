import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, FileWarning, RefreshCw, Search } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { einkaufApi, EinkaufApiError } from '../features/einkauf/api';
import type { BedarfResponse, Page } from '../features/einkauf/types';
import { BedarfDialog } from '../features/einkauf/components/BedarfDialog';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { HiCadImportDialog } from '../features/einkauf/components/HiCadImportDialog';
import { LagerentnahmeDialog } from '../features/einkauf/components/LagerentnahmeDialog';
import { DirektbestellungDialog } from '../features/einkauf/components/DirektbestellungDialog';

const format = (value: number | null | undefined) => (value ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 3 });
const parseMenge = (value: string) => {
  const normalized = value.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normalized)) return null;
  const result = Number(normalized);
  return Number.isFinite(result) && result > 0 ? result : null;
};

export default function BestellungEditor() {
  const toast = useToast(); const navigate = useNavigate();
  const [daten, setDaten] = useState<Page<BedarfResponse> | null>(null);
  const [suche, setSuche] = useState(''); const [seite, setSeite] = useState(0);
  const [aktualisierung, setAktualisierung] = useState(0); const [laden, setLaden] = useState(true);
  const [fehler, setFehler] = useState(''); const [auswahl, setAuswahl] = useState<Record<number, string>>({});
  const [dialogOffen, setDialogOffen] = useState(false); const [bearbeitung, setBearbeitung] = useState<BedarfResponse | undefined>();
  const [hicadOffen, setHicadOffen] = useState(false); const [direktOffen, setDirektOffen] = useState(false);
  const [entnahme, setEntnahme] = useState<BedarfResponse | null>(null);
  const [projektNamen, setProjektNamen] = useState<Record<number, string>>({});

  useEffect(() => {
    void einkaufApi.get<Array<{ id: number; auftragsnummer?: string | null; bauvorhaben?: string | null }>>('/api/projekte/simple?size=500')
      .then(items => setProjektNamen(Object.fromEntries(items.map(item => [item.id, [item.auftragsnummer, item.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt']))))
      .catch(() => undefined);
  }, []);

  const ladenBedarfe = useCallback(async () => {
    setLaden(true); setFehler('');
    try {
      const params = new URLSearchParams({ page: String(seite), size: '20', sort: 'id,desc' });
      if (suche.trim()) params.set('q', suche.trim());
      const result = await einkaufApi.get<Page<BedarfResponse>>(`/api/einkauf/bedarf?${params}`);
      setDaten(result);
      setAuswahl(current => Object.fromEntries(Object.entries(current).filter(([id]) => result.content.some(item => item.id === Number(id)))));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Bedarfe konnten nicht geladen werden.';
      setFehler(message); toast.error(message);
    } finally { setLaden(false); }
  }, [seite, suche, toast]);
  useEffect(() => { void ladenBedarfe(); }, [ladenBedarfe, aktualisierung]);

  const ausgewählteAnteile = useMemo(() => Object.entries(auswahl).flatMap(([idText, raw]) => {
    const row = daten?.content.find(item => item.id === Number(idText)); const menge = parseMenge(raw);
    if (!row || !menge) return [];
    return [{ bedarfId: row.id, version: row.version, menge }];
  }), [auswahl, daten]);

  const anfrageVorbereiten = async () => {
    if (!ausgewählteAnteile.length) { toast.error('Bitte mindestens einen verfügbaren Bedarf auswählen.'); return; }
    const ungueltigeMenge = ausgewählteAnteile.some(item => {
      const row = daten?.content.find(candidate => candidate.id === item.bedarfId);
      return !row || item.menge > (row.mengen.disponierbar ?? 0);
    });
    if (ungueltigeMenge) { toast.error('Die Anfragemenge darf die aktuell verfügbare Menge nicht überschreiten.'); return; }
    setFehler('');
    try {
      const created = await einkaufApi.post<{ kopf: { id: number } }>('/api/einkauf/anfragen', {
        positionen: ausgewählteAnteile, empfaenger: [], antwortfrist: null, liefertermin: null, zustaendigId: null, idempotenzKey: crypto.randomUUID(),
      });
      navigate(`/einkaufsanfragen/${created.kopf.id}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Anfrage konnte nicht vorbereitet werden.';
      toast.error(message);
      if (error instanceof EinkaufApiError && error.status === 409) void ladenBedarfe();
    }
  };
  const gespeichert = () => { setDialogOffen(false); setBearbeitung(undefined); setAktualisierung(value => value + 1); };
  const auswahlMengeAendern = (bedarf: BedarfResponse, value: string) => {
    setAuswahl(current => ({ ...current, [bedarf.id]: value }));
  };

  return <PageLayout ribbonCategory="Einkauf" title="BEDARF" subtitle="Materialbedarf prüfen, Teilmengen anfragen und bestätigte Lagerentnahmen erfassen."
    actions={<div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => { setBearbeitung(undefined); setDialogOffen(true); }}>Bedarf erfassen</Button><Button variant="outline" size="sm" onClick={() => setHicadOffen(true)}>HiCAD importieren</Button><Button variant="outline" size="sm" onClick={() => setDirektOffen(true)}>Direktbestellung</Button><Button variant="outline" size="sm" disabled={laden} onClick={() => setAktualisierung(value => value + 1)}><RefreshCw className={`mr-2 h-4 w-4 ${laden ? 'animate-spin' : ''}`} />Aktualisieren</Button></div>}>
    <div className="space-y-4">
      <EinkaufNavigation active="bedarf" />
      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
        <label htmlFor="bedarf-suche" className="min-w-[15rem] flex-1 space-y-1 text-sm font-medium">Bedarf suchen<Input id="bedarf-suche" value={suche} onChange={event => { setSuche(event.target.value); setSeite(0); }} placeholder="Bezeichnung, interne Nummer oder Projekt" /></label>
        <Button variant="outline" size="sm" onClick={() => setAktualisierung(value => value + 1)}><Search className="mr-2 h-4 w-4" />Suchen</Button>
      </div>
      {ausgewählteAnteile.length > 0 && <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3"><p className="text-sm text-rose-900">{ausgewählteAnteile.length} Bedarfe für eine Anfrage ausgewählt.</p><Button size="sm" onClick={() => void anfrageVorbereiten()}>Angebote einholen ({ausgewählteAnteile.length})</Button></div>}
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laden ? <p role="status" className="rounded-lg border border-slate-200 bg-white p-5">Bedarfe werden geladen …</p>
        : !daten?.content.length ? <section className="rounded-lg border border-slate-200 bg-white p-8 text-center"><ClipboardList className="mx-auto mb-3 h-8 w-8 text-slate-400" /><h2 className="font-semibold">Noch kein Bedarf vorhanden</h2><p className="mt-1 text-sm text-slate-600">Erfassen Sie Material oder importieren Sie eine HiCAD-Datei.</p></section>
        : <div className="space-y-3">{daten.content.map(row => {
          const position = row.position; const unit = position.basis?.einheit?.toLowerCase() ?? '';
          const available = row.mengen.disponierbar ?? 0; const selected = Object.hasOwn(auswahl, row.id);
          const key = position.interneReferenz || `BED-${row.id}`;
          return <article key={row.id} className="grid min-w-0 gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm lg:grid-cols-[minmax(0,1fr)_auto]">
            <div className="min-w-0"><div className="flex min-w-0 items-start gap-3"><input type="checkbox" aria-label={`Bedarf ${key} auswählen`} checked={selected} disabled={available <= 0} onChange={event => setAuswahl(current => event.target.checked ? { ...current, [row.id]: String(available).replace('.', ',') } : Object.fromEntries(Object.entries(current).filter(([id]) => Number(id) !== row.id)))} /><div className="min-w-0"><p className="text-sm font-semibold text-rose-600">{key} · {position.art === 'ZEICHNUNGSTEIL' ? 'Zeichnungsteil' : 'Artikel'}</p><h2 className="break-words font-semibold text-slate-900">{position.bezeichnung ?? 'Unbenannter Bedarf'}</h2><p className="mt-1 text-sm text-slate-600">{row.liefergruppe.projektId ? projektNamen[row.liefergruppe.projektId] || 'Projekt zugeordnet' : row.liefergruppe.lagerzweck || 'Kein Projekt zugeordnet'}{position.werkstoff ? ` · ${position.werkstoff}` : ''}{position.abmessung ? ` · ${position.abmessung}` : ''}</p></div></div>
              {row.nachpflegeErforderlich && <p className="mt-2 flex items-center gap-2 rounded-md bg-amber-50 p-2 text-sm text-amber-900"><FileWarning className="h-4 w-4 shrink-0" />Altdaten bitte nachpflegen. <button className="font-medium underline" onClick={() => navigate('/bestellungen')}>Bisherige Bestellungen öffnen</button></p>}
              {position.art === 'ZEICHNUNGSTEIL' && position.anlageVersionIds.length === 0 && <p role="alert" className="mt-2 text-sm text-rose-700">Für dieses Zeichnungsteil fehlt eine freigegebene Zeichnungsanlage. <button className="font-medium underline" onClick={() => { setBearbeitung(row); setDialogOffen(true); }}>Anlage ergänzen</button></p>}
              <dl className="mt-3 grid gap-2 sm:grid-cols-5"><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Soll</dt><dd className="font-medium tabular-nums">{format(row.mengen.bedarf)} {unit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Offen</dt><dd className="font-medium tabular-nums">{format(row.mengen.ungedeckt)} {unit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Reserviert</dt><dd className="font-medium tabular-nums">{format(row.mengen.reserviert)} {unit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Bestellt</dt><dd className="font-medium tabular-nums">{format(row.mengen.bestellt)} {unit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Angefragt</dt><dd className="font-medium tabular-nums">{format(row.mengen.angefragt)} {unit}</dd></div></dl>
              {selected && <label className="mt-3 block max-w-xs space-y-1 text-sm font-medium">Anfragemenge {key}<Input aria-label={`Anfragemenge ${key}`} inputMode="decimal" value={auswahl[row.id] ?? ''} onFocus={event => { if (event.currentTarget.value === '0' || event.currentTarget.value === '0,00') auswahlMengeAendern(row, ''); }} onChange={event => auswahlMengeAendern(row, event.target.value)} placeholder={`Menge in ${unit}`} /><span className="font-normal text-slate-600">Verfügbar: {format(available)} {unit}</span></label>}
            </div>
            <div className="flex flex-wrap items-start gap-2 lg:justify-end"><Button size="sm" variant="outline" onClick={() => { setBearbeitung(row); setDialogOffen(true); }}>Bearbeiten</Button><Button size="sm" variant="outline" disabled={available <= 0} onClick={() => setEntnahme(row)}>Lagerentnahme erfassen {key}</Button></div>
          </article>;
        })}</div>}
      {!laden && daten && daten.totalPages > 1 && <div className="flex items-center justify-center gap-3"><Button size="sm" variant="outline" disabled={seite === 0} onClick={() => setSeite(value => Math.max(0, value - 1))}>Vorige</Button><span className="text-sm text-slate-600">Seite {seite + 1} von {daten.totalPages}</span><Button size="sm" variant="outline" disabled={seite + 1 >= daten.totalPages} onClick={() => setSeite(value => value + 1)}>Weitere</Button></div>}
    </div>
    {dialogOffen && <BedarfDialog open onClose={() => { setDialogOffen(false); setBearbeitung(undefined); }} onSaved={gespeichert} initial={bearbeitung} />}
    {hicadOffen && <HiCadImportDialog onClose={() => setHicadOffen(false)} onImported={() => { setHicadOffen(false); setAktualisierung(value => value + 1); toast.success('Ausgewählte HiCAD-Zeilen wurden übernommen.'); }} />}
    {direktOffen && <DirektbestellungDialog onClose={() => setDirektOffen(false)} onCreated={id => { setDirektOffen(false); navigate(`/bestellungen/${id}`); }} />}
    {entnahme && <LagerentnahmeDialog bedarf={{ id: entnahme.id, version: entnahme.version, bezeichnung: entnahme.position.bezeichnung ?? 'Materialbedarf', einheit: entnahme.position.basis?.einheit ?? 'STUECK', offen: entnahme.mengen.disponierbar ?? 0 }} onClose={() => setEntnahme(null)} onBestaetigt={() => { setEntnahme(null); setAktualisierung(value => value + 1); }} />}
  </PageLayout>;
}
