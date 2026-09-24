import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, FileWarning, RefreshCw, Search } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { einkaufApi, EinkaufApiError } from '../features/einkauf/api';
import type { BedarfResponse, Page } from '../features/einkauf/types';
import { einheitenAnzeige } from '../features/einkauf/einheiten';
import { BedarfDialog } from '../features/einkauf/components/BedarfDialog';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { HiCadImportDialog } from '../features/einkauf/components/HiCadImportDialog';
import { LagerentnahmeDialog } from '../features/einkauf/components/LagerentnahmeDialog';
import { DirektbestellungDialog } from '../features/einkauf/components/DirektbestellungDialog';
import { KonfliktAbgleichDialog } from '../features/einkauf/components/KonfliktAbgleichDialog';
import type { EntwurfKonflikt } from '../features/einkauf/components/KonfliktAbgleichDialog';

const formatiere = (wert: number | null | undefined) => (wert ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 3 });
const leseMenge = (wert: string) => {
  const normalisiert = wert.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normalisiert)) return null;
  const ergebnis = Number(normalisiert);
  return Number.isFinite(ergebnis) && ergebnis > 0 ? ergebnis : null;
};

export default function BestellungEditor() {
  const meldungen = useToast(); const navigiere = useNavigate();
  const [daten, setzeDaten] = useState<Page<BedarfResponse> | null>(null);
  const [suche, setzeSuche] = useState(''); const [seite, setzeSeite] = useState(0);
  const [aktualisierung, setzeAktualisierung] = useState(0); const [laden, setzeLaden] = useState(true);
  const [fehler, setzeFehler] = useState(''); const [auswahl, setzeAuswahl] = useState<Record<number, string>>({});
  const [dialogOffen, setzeDialogOffen] = useState(false); const [bearbeitung, setzeBearbeitung] = useState<BedarfResponse | undefined>();
  const [hicadOffen, setzeHicadOffen] = useState(false); const [direktOffen, setzeDirektOffen] = useState(false);
  const [direkteTeilmengen, setzeDirekteTeilmengen] = useState<Array<{ bedarfId: number; menge: number }>>([]);
  const [entnahme, setzeEntnahme] = useState<BedarfResponse | null>(null);
  const [projektNamen, setzeProjektNamen] = useState<Record<number, string>>({});
  const [anfrageKonflikt, setzeAnfrageKonflikt] = useState<EntwurfKonflikt | null>(null);

  useEffect(() => {
    void einkaufApi.get<Array<{ id: number; auftragsnummer?: string | null; bauvorhaben?: string | null }>>('/api/projekte/simple?size=500')
      .then(einträge => setzeProjektNamen(Object.fromEntries(einträge.map(eintrag => [eintrag.id, [eintrag.auftragsnummer, eintrag.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt']))))
      .catch(() => undefined);
  }, []);

  const ladenBedarfe = useCallback(async () => {
    setzeLaden(true); setzeFehler('');
    try {
      const parameter = new URLSearchParams({ page: String(seite), size: '20', sort: 'id,desc' });
      if (suche.trim()) parameter.set('q', suche.trim());
      const ergebnis = await einkaufApi.get<Page<BedarfResponse>>(`/api/einkauf/bedarf?${parameter}`);
      setzeDaten(ergebnis);
      setzeAuswahl(aktuell => Object.fromEntries(Object.entries(aktuell).filter(([id]) => ergebnis.content.some(eintrag => eintrag.id === Number(id)))));
    } catch (fehlerursache) {
      const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'Bedarfe konnten nicht geladen werden.';
      setzeFehler(meldung); meldungen.error(meldung);
    } finally { setzeLaden(false); }
  }, [seite, suche, meldungen]);
  useEffect(() => { void ladenBedarfe(); }, [ladenBedarfe, aktualisierung]);

  const ausgewählteAnteile = useMemo(() => Object.entries(auswahl).flatMap(([idText, eingabe]) => {
    const zeile = daten?.content.find(eintrag => eintrag.id === Number(idText)); const menge = leseMenge(eingabe);
    if (!zeile) return [];
    if (!menge) return [{ bedarfId: zeile.id, version: zeile.version, menge: Number.NaN }];
    return [{ bedarfId: zeile.id, version: zeile.version, menge }];
  }), [auswahl, daten]);

  const anfrageVorbereiten = async () => {
    if (!ausgewählteAnteile.length) { meldungen.error('Bitte mindestens einen verfügbaren Bedarf auswählen.'); return; }
    if (ausgewählteAnteile.some(anteil => !Number.isFinite(anteil.menge) || anteil.menge <= 0)) { meldungen.error('Bitte geben Sie für jeden ausgewählten Bedarf eine gültige Anfragemenge ein.'); return; }
    const ungültigeMenge = ausgewählteAnteile.some(anteil => {
      const zeile = daten?.content.find(kandidat => kandidat.id === anteil.bedarfId);
      return !zeile || anteil.menge > (zeile.mengen.disponierbar ?? 0);
    });
    if (ungültigeMenge) { meldungen.error('Die Anfragemenge darf die aktuell verfügbare Menge nicht überschreiten.'); return; }
    setzeFehler('');
    try {
      const angelegt = await einkaufApi.post<{ kopf: { id: number } }>('/api/einkauf/anfragen', {
        positionen: ausgewählteAnteile, empfaenger: [], antwortfrist: null, liefertermin: null, zustaendigId: null, idempotenzKey: crypto.randomUUID(),
      });
      navigiere(`/einkaufsanfragen/${angelegt.kopf.id}`);
    } catch (fehlerursache) {
      const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'Anfrage konnte nicht vorbereitet werden.';
      meldungen.error(meldung);
      if (fehlerursache instanceof EinkaufApiError && fehlerursache.status === 409) {
        try {
          const aktuelleBedarfe = await Promise.all(ausgewählteAnteile.map(anteil => einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${anteil.bedarfId}`)));
          const felder: EntwurfKonflikt['felder'] = [];
          aktuelleBedarfe.forEach(aktuell => {
            const vorher = daten?.content.find(eintrag => eintrag.id === aktuell.id);
            const alteVerfügbarkeit = vorher?.mengen.disponierbar ?? 0;
            const neueVerfügbarkeit = aktuell.mengen.disponierbar ?? 0;
            const entwurfsmenge = auswahl[aktuell.id] ?? '';
            if (aktuell.version !== vorher?.version || neueVerfügbarkeit !== alteVerfügbarkeit) felder.push({ id: `menge-${aktuell.id}`, label: `${aktuell.position.interneReferenz || aktuell.position.bezeichnung} · Anfragemenge`, lokal: `${entwurfsmenge} (Verfügbarkeit zuvor ${formatiere(alteVerfügbarkeit)})`, server: `Verfügbar ${formatiere(neueVerfügbarkeit)} ${einheitenAnzeige(aktuell.position.basis?.einheit)}` });
          });
          setzeAnfrageKonflikt({ felder, hinweise: ['Mindestens ein Bedarf wurde während der Anfragevorbereitung geändert. Entscheiden Sie je Bedarf, ob Ihre Teilmenge bestehen bleibt oder Sie die aktuelle verfügbare Menge übernehmen.'], anwenden: wahl => { setzeAuswahl(aktuell => { const neu = { ...aktuell }; aktuelleBedarfe.forEach(bedarf => { if (wahl[`menge-${bedarf.id}`] === 'server') neu[bedarf.id] = String(bedarf.mengen.disponierbar ?? 0).replace('.', ','); }); return neu; }); setzeAktualisierung(stand => stand + 1); } });
        } catch (ladeFehler) { meldungen.error(ladeFehler instanceof Error ? ladeFehler.message : 'Aktuelle Bedarfe konnten nicht abgeglichen werden.'); }
      }
    }
  };
  const gespeichert = () => { setzeDialogOffen(false); setzeBearbeitung(undefined); setzeAktualisierung(wert => wert + 1); };
  const auswahlMengeÄndern = (bedarf: BedarfResponse, wert: string) => {
    setzeAuswahl(aktuell => ({ ...aktuell, [bedarf.id]: wert }));
  };
  const direktbestellungÖffnen = () => {
    const ausgewählt = Object.entries(auswahl);
    if (!ausgewählt.length) { setzeDirekteTeilmengen([]); setzeDirektOffen(true); return; }
    const teilmengen: Array<{ bedarfId: number; menge: number }> = [];
    for (const [idText, entwurf] of ausgewählt) {
      const bedarf = daten?.content.find(eintrag => eintrag.id === Number(idText));
      const menge = leseMenge(entwurf);
      if (!bedarf || !menge || menge > (bedarf.mengen.disponierbar ?? 0)) { meldungen.error('Bitte prüfen Sie für jeden ausgewählten Bedarf eine gültige Menge innerhalb der verfügbaren Restmenge.'); return; }
      teilmengen.push({ bedarfId: bedarf.id, menge });
    }
    setzeDirekteTeilmengen(teilmengen); setzeDirektOffen(true);
  };

  return <PageLayout ribbonCategory="Einkauf" title="BEDARF" subtitle="Materialbedarf prüfen, Teilmengen anfragen und bestätigte Lagerentnahmen erfassen."
    actions={<div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => { setzeBearbeitung(undefined); setzeDialogOffen(true); }}>Bedarf erfassen</Button><Button variant="outline" size="sm" onClick={() => setzeHicadOffen(true)}>HiCAD importieren</Button><Button variant="outline" size="sm" onClick={direktbestellungÖffnen}>Direktbestellung</Button><Button variant="outline" size="sm" disabled={laden} onClick={() => setzeAktualisierung(wert => wert + 1)}><RefreshCw className={`mr-2 h-4 w-4 ${laden ? 'animate-spin' : ''}`} />Aktualisieren</Button></div>}>
    <div className="space-y-4">
      <EinkaufNavigation active="bedarf" />
      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
        <label htmlFor="bedarf-suche" className="min-w-[15rem] flex-1 space-y-1 text-sm font-medium">Bedarf suchen<Input id="bedarf-suche" value={suche} onChange={ereignis => { setzeSuche(ereignis.target.value); setzeSeite(0); }} placeholder="Bezeichnung, interne Nummer oder Projekt" /></label>
        <Button variant="outline" size="sm" onClick={() => setzeAktualisierung(wert => wert + 1)}><Search className="mr-2 h-4 w-4" />Suchen</Button>
      </div>
      {Object.keys(auswahl).length > 0 && <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3"><p className="text-sm text-rose-900">{Object.keys(auswahl).length} Bedarfe für eine Anfrage ausgewählt.</p><Button size="sm" onClick={() => void anfrageVorbereiten()}>Angebote einholen ({Object.keys(auswahl).length})</Button></div>}
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laden ? <p role="status" className="rounded-lg border border-slate-200 bg-white p-5">Bedarfe werden geladen …</p>
        : !daten?.content.length ? <section className="rounded-lg border border-slate-200 bg-white p-8 text-center"><ClipboardList className="mx-auto mb-3 h-8 w-8 text-slate-400" /><h2 className="font-semibold">Noch kein Bedarf vorhanden</h2><p className="mt-1 text-sm text-slate-600">Erfassen Sie Material oder importieren Sie eine HiCAD-Datei.</p></section>
        : <div className="space-y-3">{daten.content.map(zeile => {
          const position = zeile.position; const einheit = einheitenAnzeige(position.basis?.einheit);
          const verfügbar = zeile.mengen.disponierbar ?? 0; const ausgewählt = Object.hasOwn(auswahl, zeile.id);
          const kennung = position.interneReferenz || `BED-${zeile.id}`;
          return <article key={zeile.id} className="grid min-w-0 gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm lg:grid-cols-[minmax(0,1fr)_auto]">
            <div className="min-w-0"><div className="flex min-w-0 items-start gap-3"><input type="checkbox" aria-label={`Bedarf ${kennung} auswählen`} checked={ausgewählt} disabled={verfügbar <= 0} onChange={ereignis => setzeAuswahl(aktuell => ereignis.target.checked ? { ...aktuell, [zeile.id]: String(verfügbar).replace('.', ',') } : Object.fromEntries(Object.entries(aktuell).filter(([id]) => Number(id) !== zeile.id)))} /><div className="min-w-0"><p className="text-sm font-semibold text-rose-600">{kennung} · {position.art === 'ZEICHNUNGSTEIL' ? 'Zeichnungsteil' : 'Artikel'}</p><h2 className="break-words font-semibold text-slate-900">{position.bezeichnung ?? 'Unbenannter Bedarf'}</h2><p className="mt-1 text-sm text-slate-600">{zeile.liefergruppe.projektId ? projektNamen[zeile.liefergruppe.projektId] || 'Projekt zugeordnet' : zeile.liefergruppe.lagerzweck || 'Kein Projekt zugeordnet'}{position.werkstoff ? ` · ${position.werkstoff}` : ''}{position.abmessung ? ` · ${position.abmessung}` : ''}</p></div></div>
              {zeile.nachpflegeErforderlich && <p className="mt-2 flex items-center gap-2 rounded-md bg-amber-50 p-2 text-sm text-amber-900"><FileWarning className="h-4 w-4 shrink-0" />Altdaten bitte nachpflegen. <button className="font-medium underline" onClick={() => navigiere('/bestellungen')}>Bisherige Bestellungen öffnen</button></p>}
              {position.art === 'ZEICHNUNGSTEIL' && position.anlageVersionIds.length === 0 && <p role="alert" className="mt-2 text-sm text-rose-700">Für dieses Zeichnungsteil fehlt eine freigegebene Zeichnungsanlage. <button className="font-medium underline" onClick={() => { setzeBearbeitung(zeile); setzeDialogOffen(true); }}>Anlage ergänzen</button></p>}
              <dl className="mt-3 grid gap-2 sm:grid-cols-5"><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Soll</dt><dd className="font-medium tabular-nums">{formatiere(zeile.mengen.bedarf)} {einheit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Offen</dt><dd className="font-medium tabular-nums">{formatiere(zeile.mengen.ungedeckt)} {einheit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Reserviert</dt><dd className="font-medium tabular-nums">{formatiere(zeile.mengen.reserviert)} {einheit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Bestellt</dt><dd className="font-medium tabular-nums">{formatiere(zeile.mengen.bestellt)} {einheit}</dd></div><div className="rounded-md bg-slate-50 p-2"><dt className="text-xs text-slate-600">Angefragt</dt><dd className="font-medium tabular-nums">{formatiere(zeile.mengen.angefragt)} {einheit}</dd></div></dl>
              {ausgewählt && <label className="mt-3 block max-w-xs space-y-1 text-sm font-medium">Anfragemenge {kennung}<Input aria-label={`Anfragemenge ${kennung}`} inputMode="decimal" value={auswahl[zeile.id] ?? ''} onFocus={ereignis => { if (ereignis.currentTarget.value === '0' || ereignis.currentTarget.value === '0,00') auswahlMengeÄndern(zeile, ''); }} onChange={ereignis => auswahlMengeÄndern(zeile, ereignis.target.value)} placeholder={`Menge in ${einheit}`} /><span className="font-normal text-slate-600">Verfügbar: {formatiere(verfügbar)} {einheit}</span></label>}
            </div>
            <div className="flex flex-wrap items-start gap-2 lg:justify-end"><Button size="sm" variant="outline" onClick={() => { setzeBearbeitung(zeile); setzeDialogOffen(true); }}>Bearbeiten</Button><Button size="sm" variant="outline" disabled={verfügbar <= 0} onClick={() => setzeEntnahme(zeile)}>Lagerentnahme erfassen {kennung}</Button></div>
          </article>;
        })}</div>}
      {!laden && daten && daten.totalPages > 1 && <div className="flex items-center justify-center gap-3"><Button size="sm" variant="outline" disabled={seite === 0} onClick={() => setzeSeite(wert => Math.max(0, wert - 1))}>Vorige</Button><span className="text-sm text-slate-600">Seite {seite + 1} von {daten.totalPages}</span><Button size="sm" variant="outline" disabled={seite + 1 >= daten.totalPages} onClick={() => setzeSeite(wert => wert + 1)}>Weitere</Button></div>}
    </div>
    {dialogOffen && <BedarfDialog offen schließen={() => { setzeDialogOffen(false); setzeBearbeitung(undefined); }} gespeichert={gespeichert} ausgangsbedarf={bearbeitung} />}
    {anfrageKonflikt && <KonfliktAbgleichDialog konflikt={anfrageKonflikt} onAbbrechen={() => setzeAnfrageKonflikt(null)} onUebernehmen={wahl => { anfrageKonflikt.anwenden(wahl); setzeAnfrageKonflikt(null); }} />}
    {hicadOffen && <HiCadImportDialog schließen={() => setzeHicadOffen(false)} übernommen={() => { setzeHicadOffen(false); setzeAktualisierung(wert => wert + 1); meldungen.success('Ausgewählte HiCAD-Zeilen wurden übernommen.'); }} />}
    {direktOffen && <DirektbestellungDialog initialeTeilmengen={direkteTeilmengen} onClose={() => setzeDirektOffen(false)} onCreated={id => { setzeDirektOffen(false); navigiere(`/bestellungen/${id}`); }} />}
    {entnahme && <LagerentnahmeDialog bedarf={{ id: entnahme.id, version: entnahme.version, bezeichnung: entnahme.position.bezeichnung ?? 'Materialbedarf', einheit: entnahme.position.basis?.einheit ?? 'STUECK', offen: entnahme.mengen.disponierbar ?? 0 }} schließen={() => setzeEntnahme(null)} bestätigt={() => { setzeEntnahme(null); setzeAktualisierung(wert => wert + 1); }} />}
  </PageLayout>;
}
