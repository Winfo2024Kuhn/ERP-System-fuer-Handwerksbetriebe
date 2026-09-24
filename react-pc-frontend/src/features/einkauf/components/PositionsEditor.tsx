import { useState } from 'react';
import { Plus, Search, X } from 'lucide-react';
import { ArtikelSuche } from '../../../components/artikel/ArtikelSuche';
import { CreateArticleModal } from '../../../components/CreateArticleModal';
import { Button } from '../../../components/ui/button';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { Select } from '../../../components/ui/select-custom';
import type { Artikel } from '../../../types';
import type { Dokumentart, Einheit, EinkaufAnlage } from '../types';
import type { DokumentSoll, PositionDraft } from '../positionDrafts';

export interface PositionsEditorProps {
  value: PositionDraft;
  onChange: (value: PositionDraft) => void;
  readOnly?: boolean;
  anlagen?: readonly EinkaufAnlage[];
}

const einheiten: { value: Einheit; label: string }[] = [
  { value: 'STUECK', label: 'Stück' }, { value: 'METER', label: 'Meter' }, { value: 'KILOGRAMM', label: 'Kilogramm' },
  { value: 'TONNE', label: 'Tonne' }, { value: 'QUADRATMETER', label: 'Quadratmeter' },
];
const dokumentarten: { value: Dokumentart; label: string }[] = [
  { value: 'ZEUGNIS_2_1', label: 'Zeugnis 2.1' }, { value: 'ZEUGNIS_2_2', label: 'Zeugnis 2.2' },
  { value: 'ZEUGNIS_3_1', label: 'Zeugnis 3.1' }, { value: 'ZEUGNIS_3_2', label: 'Zeugnis 3.2' },
  { value: 'LEISTUNGSERKLAERUNG', label: 'Leistungserklärung' }, { value: 'CE_NACHWEIS', label: 'CE-Nachweis' },
];

export function PositionsEditor({ value, onChange, readOnly = false, anlagen = [] }: PositionsEditorProps) {
  const [artikelSucheOffen, setArtikelSucheOffen] = useState(false);
  const [artikelAnlegenOffen, setArtikelAnlegenOffen] = useState(false);
  const [anlageId, setAnlageId] = useState('');
  const [dokumentart, setDokumentart] = useState<Dokumentart>('ZEUGNIS_3_1');
  const [grundlage, setGrundlage] = useState('');
  const [grundlageVersion, setGrundlageVersion] = useState('');
  const [dokumentFehler, setDokumentFehler] = useState('');
  const set = <K extends keyof PositionDraft>(key: K, next: PositionDraft[K]) => onChange({ ...value, [key]: next });
  const setText = (key: 'interneReferenz' | 'zeichnungsnummer' | 'zeichnungsrevision' | 'bezeichnung' | 'werkstoff' | 'abmessung' | 'menge' | 'stueckzahl' | 'einzelLaengeMm' | 'kgJeMeter' | 'faktorQuelle' | 'winkelLinks' | 'winkelRechts' | 'bearbeitung' | 'oberflaeche', next: string) => set(key, next);
  const artikelAuswaehlen = (artikel: Artikel) => {
    onChange({ ...value, art: 'ARTIKEL', artikelId: artikel.id, interneReferenz: artikel.artikelnummer ?? '', bezeichnung: artikel.produktname, werkstoff: artikel.werkstoffName ?? '', abmessung: artikel.abmessung ?? '' });
    setArtikelSucheOffen(false);
  };
  const dokumentHinzufuegen = () => {
    if (!grundlage.trim() || !grundlageVersion.trim()) {
      setDokumentFehler('Grundlage und Grundlagenversion sind Pflichtangaben.');
      return;
    }
    const dokument: DokumentSoll = { art: dokumentart, grundlage: grundlage.trim() || null, grundlageVersion: grundlageVersion.trim() || null, fachlichBestaetigt: false };
    set('dokumente', [...value.dokumente, dokument]);
    setGrundlage(''); setGrundlageVersion(''); setDokumentFehler('');
  };
  const anlageHinzufuegen = () => {
    if (!anlagen.some(anlage => anlage.id === Number(anlageId))) return;
    const id = Number(anlageId);
    if (!value.anlageVersionIds.includes(id)) set('anlageVersionIds', [...value.anlageVersionIds, id]);
    setAnlageId('');
  };

  return <section aria-label="Position bearbeiten" className="space-y-5 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h2 className="text-lg font-semibold text-slate-900">Position</h2><p className="text-sm text-slate-500">Artikel oder Zeichnungsteil mit technischen Angaben erfassen.</p></div>
      {!readOnly && <div className="flex gap-2"><Button type="button" variant="outline" size="sm" onClick={() => setArtikelSucheOffen(true)}><Search aria-hidden="true" className="h-4 w-4" />Artikel auswählen</Button><Button type="button" variant="outline" size="sm" onClick={() => setArtikelAnlegenOffen(true)}>Artikel neu anlegen</Button></div>}
    </div>
    <div className="max-w-sm"><Label htmlFor="positionsart">Positionsart</Label><Select id="positionsart" aria-label="Positionsart" value={value.art} options={[{ value: 'ARTIKEL', label: 'Artikel' }, { value: 'ZEICHNUNGSTEIL', label: 'Zeichnungsteil' }]} onChange={art => onChange({ ...value, art: art as PositionDraft['art'], artikelId: null })} disabled={readOnly} /></div>
    {value.art === 'ARTIKEL' && value.artikelId !== null && <p className="text-sm text-slate-600">Artikel aus dem Stamm ausgewählt · Preis wird bei der Bestellung ausdrücklich bestätigt.</p>}
    <div className="grid gap-4 md:grid-cols-2">
      <div><Label htmlFor="position-bezeichnung">Bezeichnung *</Label><Input id="position-bezeichnung" value={value.bezeichnung} onChange={e => setText('bezeichnung', e.target.value)} readOnly={readOnly} required /></div>
      <div><Label htmlFor="position-referenz">{value.art === 'ZEICHNUNGSTEIL' ? 'Projektkennung *' : 'Interne Artikelnummer'}</Label><Input id="position-referenz" value={value.interneReferenz} onChange={e => setText('interneReferenz', e.target.value)} readOnly={readOnly} /></div>
      <div><Label htmlFor="position-werkstoff">Werkstoff</Label><Input id="position-werkstoff" value={value.werkstoff} onChange={e => setText('werkstoff', e.target.value)} readOnly={readOnly} /></div>
      <div><Label htmlFor="position-abmessung">Abmessung / Profil</Label><Input id="position-abmessung" value={value.abmessung} onChange={e => setText('abmessung', e.target.value)} readOnly={readOnly} /></div>
      <div><DecimalInput id="position-menge" label="Menge *" value={value.menge} onChange={v => setText('menge', v)} min={0.000001} readOnly={readOnly} /></div>
      <div><Label htmlFor="position-einheit">Einheit *</Label><Select id="position-einheit" aria-label="Einheit" value={value.einheit} options={einheiten} onChange={v => set('einheit', v as Einheit)} disabled={readOnly} /></div>
    </div>
    {value.art === 'ZEICHNUNGSTEIL' && <fieldset className="space-y-4 rounded-lg border border-slate-200 p-4">
      <legend className="px-1 text-sm font-semibold text-slate-700">Zeichnungsteil</legend>
      <div className="grid gap-4 md:grid-cols-2">
        <div><Label htmlFor="zeichnung-nr">Zeichnungsnummer</Label><Input id="zeichnung-nr" value={value.zeichnungsnummer} onChange={e => setText('zeichnungsnummer', e.target.value)} readOnly={readOnly} /></div>
        <div><Label htmlFor="zeichnung-revision">Zeichnungsrevision</Label><Input id="zeichnung-revision" value={value.zeichnungsrevision} onChange={e => setText('zeichnungsrevision', e.target.value)} readOnly={readOnly} /></div>
        <div><Label htmlFor="schnittform">Schnittform</Label><Select id="schnittform" aria-label="Schnittform" value={value.schnittForm} options={[{ value: 'GERADE', label: 'Gerade' }, { value: 'GEHRUNG', label: 'Gehrung' }]} onChange={v => set('schnittForm', v)} disabled={readOnly} /></div>
        <div className="grid grid-cols-2 gap-3"><DecimalInput id="winkel-links" label="Linker Winkel" value={value.winkelLinks} onChange={v => setText('winkelLinks', v)} min={0} max={180} readOnly={readOnly} /><DecimalInput id="winkel-rechts" label="Rechter Winkel" value={value.winkelRechts} onChange={v => setText('winkelRechts', v)} min={0} max={180} readOnly={readOnly} /></div>
        <div><DecimalInput id="stueckzahl" label="Stückzahl" value={value.stueckzahl} onChange={v => setText('stueckzahl', v)} min={1} integer readOnly={readOnly} /></div>
        <div><DecimalInput id="einzel-laenge" label="Einzellänge in mm" value={value.einzelLaengeMm} onChange={v => setText('einzelLaengeMm', v)} min={0.000001} readOnly={readOnly} /></div>
        <div><DecimalInput id="kg-meter" label="Gewicht je Meter in kg" value={value.kgJeMeter} onChange={v => setText('kgJeMeter', v)} min={0.000001} readOnly={readOnly} /></div>
        <div><Label htmlFor="faktor-quelle">Quelle des Faktors</Label><Input id="faktor-quelle" value={value.faktorQuelle} onChange={e => setText('faktorQuelle', e.target.value)} readOnly={readOnly} /></div>
        <div><Label htmlFor="bearbeitung">Bearbeitung</Label><Input id="bearbeitung" value={value.bearbeitung} onChange={e => setText('bearbeitung', e.target.value)} readOnly={readOnly} /></div>
        <div><Label htmlFor="oberflaeche">Oberfläche</Label><Input id="oberflaeche" value={value.oberflaeche} onChange={e => setText('oberflaeche', e.target.value)} readOnly={readOnly} /></div>
      </div>
    </fieldset>}
    <section aria-labelledby="dokumente-title" className="space-y-3">
      <div><h3 id="dokumente-title" className="font-semibold text-slate-800">Benötigte Unterlagen</h3><p className="text-sm text-slate-500">Eine Anforderung wird erst nach fachlicher Bestätigung als geprüft behandelt.</p></div>
      {value.dokumente.map((dokument, index) => <div key={`${dokument.art}-${index}`} className="flex flex-wrap items-center gap-2 rounded-md bg-slate-50 px-3 py-2 text-sm">
        <span>{dokumentarten.find(option => option.value === dokument.art)?.label ?? dokument.art}</span>
        {dokument.grundlage && <span className="text-slate-600">{dokument.grundlage}</span>}
        <span className="ml-auto text-slate-500">{dokument.fachlichBestaetigt ? 'Fachlich bestätigt' : 'Noch nicht bestätigt'}</span>
        {!readOnly && <button type="button" aria-label={`${dokumentarten.find(option => option.value === dokument.art)?.label} entfernen`} className="rounded p-1 text-slate-500 hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500" onClick={() => set('dokumente', value.dokumente.filter((_, i) => i !== index))}><X className="h-4 w-4" /></button>}
      </div>)}
      {!readOnly && <div className="grid items-end gap-3 md:grid-cols-[1fr_1fr_1fr_auto]">
        <div><Label htmlFor="dokumentart">Dokumentart hinzufügen</Label><Select id="dokumentart" aria-label="Dokumentart hinzufügen" value={dokumentart} options={dokumentarten} onChange={v => setDokumentart(v as Dokumentart)} /></div>
        <div><Label htmlFor="dokument-grundlage">Grundlage *</Label><Input id="dokument-grundlage" value={grundlage} onChange={e => { setGrundlage(e.target.value); setDokumentFehler(''); }} placeholder="z. B. EN 10204" required /></div>
        <div><Label htmlFor="dokument-version">Grundlagenversion *</Label><Input id="dokument-version" value={grundlageVersion} onChange={e => { setGrundlageVersion(e.target.value); setDokumentFehler(''); }} placeholder="z. B. 2025" required /></div>
        <Button type="button" variant="outline" size="sm" onClick={dokumentHinzufuegen}><Plus aria-hidden="true" className="h-4 w-4" />Hinzufügen</Button>
      </div>}
      {dokumentFehler && <p role="alert" className="text-sm text-rose-700">{dokumentFehler}</p>}
    </section>
    <section aria-labelledby="anlagen-title" className="space-y-3">
      <div><h3 id="anlagen-title" className="font-semibold text-slate-800">Zeichnungen und Anlagen</h3><p className="text-sm text-slate-500">Die ausgewählte Dateiversion bleibt mit dieser Position verbunden.</p></div>
      <div className="flex flex-wrap gap-2">{value.anlageVersionIds.map(id => {
        const anlage = anlagen.find(item => item.id === id);
        const label = anlage ? `${anlage.dateiname} · Revision ${anlage.revision || 'ohne Angabe'} · ${anlage.freigegeben ? 'Freigegeben' : 'Noch nicht freigegeben'}` : 'Verknüpfte Datei – Angaben noch nicht geladen';
        return <span key={id} className="rounded-md border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm">{label}{!readOnly && <button type="button" className="ml-2 text-slate-500 hover:text-rose-700" aria-label={`${anlage?.dateiname || 'Verknüpfte Datei'} entfernen`} onClick={() => set('anlageVersionIds', value.anlageVersionIds.filter(existing => existing !== id))}><X aria-hidden="true" className="inline h-3.5 w-3.5" /></button>}</span>;
      })}</div>
      {!readOnly && (anlagen.length ? <div className="flex max-w-3xl items-end gap-2"><div className="min-w-0 flex-1"><Label htmlFor="anlage-version">Dateiversion auswählen</Label><Select id="anlage-version" aria-label="Dateiversion auswählen" value={anlageId} options={anlagen.map(anlage => ({ value: String(anlage.id), label: `${anlage.dateiname} · Revision ${anlage.revision || 'ohne Angabe'} · ${anlage.freigegeben ? 'Freigegeben' : 'Noch nicht freigegeben'}` }))} onChange={setAnlageId} /></div><Button type="button" variant="outline" size="sm" onClick={anlageHinzufuegen} disabled={!anlageId} title={!anlageId ? 'Bitte zuerst eine Dateiversion auswählen.' : undefined}><Plus aria-hidden="true" className="h-4 w-4" />Version hinzufügen</Button></div> : <p className="text-sm text-slate-500">Laden Sie zuerst eine Zeichnung oder Anlage zum Bedarf hoch.</p>)}
    </section>
    <Dialog open={artikelSucheOffen} onOpenChange={setArtikelSucheOffen} aria-labelledby="artikel-suche-title" aria-describedby="artikel-suche-hinweis" className="w-full max-w-5xl">
      <DialogContent className="overflow-y-auto">
        <DialogHeader><DialogTitle id="artikel-suche-title">Artikel auswählen</DialogTitle><DialogDescription id="artikel-suche-hinweis">Wähle einen Artikelstamm-Eintrag als technische Grundlage der Position.</DialogDescription></DialogHeader>
        <ArtikelSuche urlSync={false} seitenGroesse={8} onZeilenKlick={artikelAuswaehlen} />
        <DialogFooter><Button type="button" variant="outline" size="sm" onClick={() => setArtikelSucheOffen(false)}>Schließen</Button></DialogFooter>
      </DialogContent>
    </Dialog>
    {artikelAnlegenOffen && <CreateArticleModal onClose={() => setArtikelAnlegenOffen(false)} onSave={() => undefined} onCreated={artikel => { artikelAuswaehlen(artikel); setArtikelAnlegenOffen(false); }} />}
  </section>;
}
