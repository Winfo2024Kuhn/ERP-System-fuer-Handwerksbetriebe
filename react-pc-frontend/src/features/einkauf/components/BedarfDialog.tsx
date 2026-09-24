import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select-custom';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi } from '../api';
import type { BedarfResponse, Einheit, EinkaufAnlage, Positionsart } from '../types';
import { StammdatenAuswahl, type StammdatenWahl } from './StammdatenAuswahl';
import { AnlagenEditor } from './AnlagenEditor';

const einheiten: { value: Einheit; label: string }[] = [
  { value: 'STUECK', label: 'Stück' }, { value: 'METER', label: 'Meter' },
  { value: 'KILOGRAMM', label: 'Kilogramm' }, { value: 'TONNE', label: 'Tonne' },
  { value: 'QUADRATMETER', label: 'Quadratmeter' },
];
const mengeLesen = (value: string): number | null => {
  const normalized = value.trim().replace(/\s/g, '').replace(',', '.');
  if (!normalized || !/^(?:\d+)(?:\.\d{1,6})?$/.test(normalized)) return null;
  const result = Number(normalized);
  return Number.isFinite(result) && result > 0 ? result : null;
};

export function BedarfDialog({ open, onClose, onSaved, initial }: {
  open: boolean; onClose: () => void; onSaved: (bedarf: BedarfResponse) => void; initial?: BedarfResponse;
}) {
  const toast = useToast();
  const [artikel, setArtikel] = useState<StammdatenWahl | null>(null);
  const [projekt, setProjekt] = useState<StammdatenWahl | null>(null);
  const [bezeichnung, setBezeichnung] = useState('');
  const [interneReferenz, setInterneReferenz] = useState('');
  const [menge, setMenge] = useState('');
  const [einheit, setEinheit] = useState<Einheit>('STUECK');
  const [lagerzweck, setLagerzweck] = useState('');
  const [art, setArt] = useState<Positionsart>('ARTIKEL');
  const [zeichnungsnummer, setZeichnungsnummer] = useState('');
  const [zeichnungsrevision, setZeichnungsrevision] = useState('');
  const [werkstoff, setWerkstoff] = useState('');
  const [abmessung, setAbmessung] = useState('');
  const [anlagen, setAnlagen] = useState<EinkaufAnlage[]>([]);
  const [anlageVersionIds, setAnlageVersionIds] = useState<number[]>([]);
  const [revision, setRevision] = useState('');
  const [dateiLaden, setDateiLaden] = useState(false);
  const [laden, setLaden] = useState(false);
  const [fehler, setFehler] = useState('');

  useEffect(() => {
    if (!open) return;
    setArtikel(initial?.position.artikelId && initial.position.bezeichnung
      ? { id: initial.position.artikelId, name: initial.position.bezeichnung } : null);
    setProjekt(initial?.liefergruppe.projektId
      ? { id: initial.liefergruppe.projektId, name: 'Zugeordnetes Projekt' } : null);
    setBezeichnung(initial?.position.bezeichnung ?? '');
    setInterneReferenz(initial?.position.interneReferenz ?? '');
    setMenge(initial?.position.basis?.menge == null ? '' : String(initial.position.basis.menge).replace('.', ','));
    setEinheit(initial?.position.basis?.einheit ?? 'STUECK');
    setLagerzweck(initial?.liefergruppe.lagerzweck ?? '');
    setArt(initial?.position.art ?? 'ARTIKEL');
    setZeichnungsnummer(initial?.position.zeichnungsnummer ?? '');
    setZeichnungsrevision(initial?.position.zeichnungsrevision ?? '');
    setWerkstoff(initial?.position.werkstoff ?? ''); setAbmessung(initial?.position.abmessung ?? '');
    setAnlageVersionIds(initial?.position.anlageVersionIds ?? []); setRevision('');
    setFehler('');
    if (initial) void einkaufApi.get<EinkaufAnlage[]>(`/api/einkauf/bedarfe/${initial.id}/anlagen`).then(setAnlagen).catch(error => setFehler(error instanceof Error ? error.message : 'Anlagen konnten nicht geladen werden.'));
    else setAnlagen([]);
  }, [open, initial]);

  const anlageHochladen = async (datei?: File) => {
    if (!datei || !initial) return;
    if (datei.size > 10 * 1024 * 1024) { setFehler('Die Datei darf höchstens 10 MiB groß sein.'); return; }
    if (!revision.trim()) { setFehler('Bitte vor dem Upload eine Revision angeben.'); return; }
    const form = new FormData(); form.append('datei', datei); form.append('revision', revision.trim());
    setDateiLaden(true); setFehler('');
    try {
      const response = await fetch(`/api/einkauf/bedarfe/${initial.id}/anlagen`, { method: 'POST', body: form });
      if (!response.ok) throw new Error(`Anlage konnte nicht hochgeladen werden (HTTP ${response.status}).`);
      setAnlagen(await einkaufApi.get<EinkaufAnlage[]>(`/api/einkauf/bedarfe/${initial.id}/anlagen`));
    } catch (error) { setFehler(error instanceof Error ? error.message : 'Anlage konnte nicht hochgeladen werden.'); }
    finally { setDateiLaden(false); }
  };

  const speichern = async () => {
    const parsed = mengeLesen(menge);
    if (!parsed) { setFehler('Bitte eine Menge größer als 0 eingeben.'); return; }
    if (!bezeichnung.trim()) { setFehler('Bitte eine Bezeichnung eingeben.'); return; }
    if (!projekt && !lagerzweck.trim()) { setFehler('Bitte ein Projekt oder einen Lagerzweck auswählen.'); return; }
    if (art === 'ZEICHNUNGSTEIL' && (!zeichnungsnummer.trim() || !anlageVersionIds.some(id => anlagen.some(item => item.id === id && item.freigegeben)))) {
      setFehler('Für ein Zeichnungsteil sind Zeichnungsnummer und mindestens eine ausgewählte, freigegebene Zeichnungsanlage erforderlich.'); return;
    }
    const body = {
      ...(initial ? { version: initial.version } : {}),
      position: {
        art, artikelId: art === 'ARTIKEL' ? (artikel?.id ?? null) : null, interneReferenz: interneReferenz.trim() || null,
        zeichnungsnummer: art === 'ZEICHNUNGSTEIL' ? zeichnungsnummer.trim() : null, zeichnungsrevision: art === 'ZEICHNUNGSTEIL' ? (zeichnungsrevision.trim() || null) : null, bezeichnung: bezeichnung.trim(), werkstoff: werkstoff.trim() || null,
        abmessung: abmessung.trim() || null, basis: { menge: parsed, einheit, stueckzahl: einheit === 'STUECK' ? parsed : null, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null },
        schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null,
        dokumente: [], anlageVersionIds: art === 'ZEICHNUNGSTEIL' ? anlageVersionIds : [],
      },
      liefergruppe: { lieferadresse: null, bedarfstermin: null, projektId: projekt?.id ?? null, lagerzweck: lagerzweck.trim() || null },
      ...(!initial ? { artikelInProjektId: null } : {}),
    };
    setLaden(true); setFehler('');
    try {
      const result = await (initial
        ? einkaufApi.put<BedarfResponse>(`/api/einkauf/bedarf/${initial.id}`, body)
        : einkaufApi.post<BedarfResponse>('/api/einkauf/bedarf', body));
      onSaved(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Bedarf konnte nicht gespeichert werden.';
      setFehler(message); toast.error(message);
    } finally { setLaden(false); }
  };

  return <Dialog open={open} onOpenChange={value => { if (!value && !laden) onClose(); }}>
    <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader><DialogTitle>{initial ? 'Bedarf bearbeiten' : 'Bedarf erfassen'}</DialogTitle></DialogHeader>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1 text-sm font-medium"><span>Positionsart</span><Select aria-label="Positionsart" options={[{ value: 'ARTIKEL', label: 'Artikel' }, { value: 'ZEICHNUNGSTEIL', label: 'Zeichnungsteil' }]} value={art} onChange={value => setArt(value as Positionsart)} /></div>
        {art === 'ARTIKEL' && <div className="sm:col-span-2"><StammdatenAuswahl art="Artikel" value={artikel} onChange={setArtikel} /></div>}
        <label className="space-y-1 text-sm font-medium">Bezeichnung<Input aria-label="Bezeichnung" value={bezeichnung} onChange={event => setBezeichnung(event.target.value)} maxLength={240} /></label>
        <label className="space-y-1 text-sm font-medium">Interne Nummer<Input aria-label="Interne Nummer" value={interneReferenz} onChange={event => setInterneReferenz(event.target.value)} maxLength={80} /></label>
        <label className="space-y-1 text-sm font-medium">Menge<Input aria-label="Menge" inputMode="decimal" value={menge} onChange={event => setMenge(event.target.value)} placeholder="z. B. 10,5" /></label>
        <div className="space-y-1 text-sm font-medium"><span>Einheit</span><Select aria-label="Einheit" options={einheiten} value={einheit} onChange={value => setEinheit(value as Einheit)} /></div>
        {art === 'ZEICHNUNGSTEIL' && <>
          <label className="space-y-1 text-sm font-medium">Zeichnungsnummer<Input aria-label="Zeichnungsnummer" value={zeichnungsnummer} onChange={event => setZeichnungsnummer(event.target.value)} maxLength={100} /></label>
          <label className="space-y-1 text-sm font-medium">Zeichnungsrevision<Input aria-label="Zeichnungsrevision" value={zeichnungsrevision} onChange={event => setZeichnungsrevision(event.target.value)} maxLength={40} /></label>
        </>}
        <label className="space-y-1 text-sm font-medium">Werkstoff<Input aria-label="Werkstoff" value={werkstoff} onChange={event => setWerkstoff(event.target.value)} maxLength={160} /></label>
        <label className="space-y-1 text-sm font-medium">Abmessung / Profil<Input aria-label="Abmessung / Profil" value={abmessung} onChange={event => setAbmessung(event.target.value)} maxLength={160} /></label>
        <div className="sm:col-span-2"><StammdatenAuswahl art="Projekt" value={projekt} onChange={setProjekt} /></div>
        {!projekt && <label className="space-y-1 text-sm font-medium sm:col-span-2">Lagerzweck<Input aria-label="Lagerzweck" value={lagerzweck} onChange={event => setLagerzweck(event.target.value)} maxLength={160} /></label>}
        {initial && <div className="sm:col-span-2 space-y-3"><div className="flex flex-wrap items-end gap-2"><label className="space-y-1 text-sm font-medium">Anlagenrevision<Input aria-label="Anlagenrevision" value={revision} onChange={event => setRevision(event.target.value)} placeholder="z. B. B" /></label><label className="space-y-1 text-sm font-medium">Zeichnung oder Anlage<input aria-label="Zeichnung oder Anlage hochladen" type="file" className="block max-w-full rounded-md border border-slate-200 p-2 text-sm" disabled={dateiLaden} onChange={event => { void anlageHochladen(event.target.files?.[0]); event.currentTarget.value = ''; }} /></label></div><AnlagenEditor anlagen={anlagen} ausgewaehlt={anlageVersionIds} onChange={setAnlageVersionIds} zeichnungsteil={art === 'ZEICHNUNGSTEIL'} /></div>}
      </div>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      <DialogFooter><Button variant="outline" disabled={laden} onClick={onClose}>Abbrechen</Button><Button disabled={laden} onClick={() => void speichern()}>{laden ? 'Wird gespeichert …' : 'Bedarf speichern'}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
