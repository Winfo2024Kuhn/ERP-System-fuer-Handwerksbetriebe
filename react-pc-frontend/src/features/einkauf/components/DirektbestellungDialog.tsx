import { useEffect, useMemo, useRef, useState } from 'react';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../../../components/LieferantSearchModal';
import { Button } from '../../../components/ui/button';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { Input } from '../../../components/ui/input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { DatePicker } from '../../../components/ui/datepicker';
import { Select } from '../../../components/ui/select-custom';
import { useToast } from '../../../components/ui/toast';
import { formatDecimalInput, validateDecimalInput } from '../../../lib/numberInput';
import { EinkaufApiError, einkaufApi } from '../api';
import { ladeAlleBedarfe } from '../bedarfApi';
import type { BedarfResponse, BestellungDirekt, KontaktSnapshot } from '../types';

interface Kontakt { id: number; version: number; name: string | null; anrede: string | null; email: string; standardBestellung: boolean; aktiv: boolean }
interface Lieferant extends LieferantSuchErgebnis { eigeneKundennummer?: string | null }
interface Draft { menge: string; preis: string; bestaetigtAm: string; gueltigBis: string; quelle: string }
interface Props {
  onClose: () => void;
  onCreated: (bestellungId: number) => void;
  initialeTeilmengen?: readonly { bedarfId: number; menge: number }[];
}
const heute = () => new Date().toLocaleDateString('sv-SE');
const KEINE_TEILMENGEN: NonNullable<Props['initialeTeilmengen']> = [];

export function DirektbestellungDialog({ onClose, onCreated, initialeTeilmengen = KEINE_TEILMENGEN }: Props) {
  const toast = useToast();
  const [bedarfe, setBedarfe] = useState<BedarfResponse[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [drafts, setDrafts] = useState<Record<number, Draft>>({});
  const [loading, setLoading] = useState(true);
  const [ladefehler, setLadefehler] = useState('');
  const [busy, setBusy] = useState(false);
  const [picker, setPicker] = useState(false);
  const [lieferant, setLieferant] = useState<Lieferant | null>(null);
  const [lieferantLaedt, setLieferantLaedt] = useState(false);
  const [kontakte, setKontakte] = useState<Kontakt[]>([]);
  const [kontaktId, setKontaktId] = useState('');
  const [liefertermin, setLiefertermin] = useState('');
  const [bestaetigungsfrist, setBestaetigungsfrist] = useState('');
  const [bedingungen, setBedingungen] = useState('');
  const [unklar, setUnklar] = useState(false);
  const requestRef = useRef<BestellungDirekt | null>(null);
  const busyRef = useRef(false);
  const supplierRequest = useRef(0);
  const aktivRef = useRef(true);
  useEffect(() => { aktivRef.current = true; return () => { aktivRef.current = false; supplierRequest.current += 1; }; }, []);

  useEffect(() => {
    let aktiv = true;
    const laden = initialeTeilmengen.length
      ? Promise.all([...new Set(initialeTeilmengen.map(item => item.bedarfId))].map(id => einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${id}`)))
      : ladeAlleBedarfe();
    laden.then(rows => {
      if (!aktiv) return;
      const eligible = rows.filter(need => (need.mengen?.disponierbar ?? 0) > 0);
      if (initialeTeilmengen.length && eligible.length !== rows.length) throw new Error('Ein ausgewählter Bedarf ist nicht mehr verfügbar. Bitte die Bedarfsliste neu laden.');
      setBedarfe(eligible);
      setDrafts(Object.fromEntries(eligible.map(need => [need.id, {
        menge: formatDecimalInput(initialeTeilmengen.find(item => item.bedarfId === need.id)?.menge ?? need.mengen.disponierbar ?? 0),
        preis: '', bestaetigtAm: '', gueltigBis: '', quelle: '',
      }])));
      setSelected([...new Set(initialeTeilmengen.map(item => item.bedarfId))]);
    }).catch(error => {
      if (!aktiv) return;
      const message = error instanceof Error ? error.message : 'Bedarfe konnten nicht geladen werden.';
      setLadefehler(message); toast.error(message);
    }).finally(() => { if (aktiv) setLoading(false); });
    return () => { aktiv = false; };
  }, [toast, initialeTeilmengen]);

  const kontakt = useMemo(() => kontakte.find(item => String(item.id) === kontaktId), [kontakte, kontaktId]);
  const chooseSupplier = async (value: LieferantSuchErgebnis) => {
    const request = ++supplierRequest.current;
    setPicker(false); setLieferant(null); setKontakte([]); setKontaktId(''); setLieferantLaedt(true);
    try {
      const [detail, rows] = await Promise.all([einkaufApi.get<Lieferant>(`/api/lieferanten/${value.id}`), einkaufApi.get<Kontakt[]>(`/api/lieferanten/${value.id}/einkauf-kontakte`)]);
      if (!aktivRef.current || request !== supplierRequest.current) return;
      setLieferant({ ...value, ...detail });
      const active = rows.filter(item => item.aktiv);
      setKontakte(active); setKontaktId(String(active.find(item => item.standardBestellung)?.id ?? active[0]?.id ?? ''));
      if (!active.length) toast.error('Für diesen Lieferanten ist kein aktiver Bestellkontakt hinterlegt.');
    } catch (error) {
      if (aktivRef.current && request === supplierRequest.current) toast.error(error instanceof Error ? error.message : 'Lieferantenkontakt konnte nicht geladen werden.');
    } finally { if (aktivRef.current && request === supplierRequest.current) setLieferantLaedt(false); }
  };
  const update = (id: number, key: keyof Draft, value: string) => setDrafts(current => ({ ...current, [id]: { ...current[id], [key]: value } }));
  const create = async () => {
    if (busyRef.current || loading || ladefehler || lieferantLaedt) return;
    if (!lieferant || !kontakt) { toast.error('Bitte Lieferant und Bestellkontakt auswählen.'); return; }
    if (!selected.length) { toast.error('Bitte mindestens einen verfügbaren Bedarf auswählen.'); return; }
    if (!requestRef.current) {
      const paket: BestellungDirekt['paket'] = [];
      const preise: BestellungDirekt['preise'] = [];
      for (const id of selected) {
        const need = bedarfe.find(item => item.id === id)!;
        const values = drafts[id];
        const key = need.position.interneReferenz ?? id;
        const qty = validateDecimalInput(values.menge, { label: `Menge ${key}`, required: true, min: 0, max: need.mengen.disponierbar ?? 0, integer: need.position.basis?.einheit === 'STUECK' });
        if (!qty.valid) { toast.error(qty.message); return; }
        if ((values.menge.trim().split(',')[1]?.length ?? 0) > 6) { toast.error(`Menge ${key} darf höchstens 6 Nachkommastellen haben.`); return; }
        if (qty.value == null || qty.value <= 0) { toast.error(`Menge ${key} muss größer als 0 sein.`); return; }
        paket.push({ bedarfId: id, version: need.version, menge: qty.value });
        const price = validateDecimalInput(values.preis, { label: `Preis ${key}`, required: false, min: 0 });
        if (!price.valid) { toast.error(price.message); return; }
        if (price.value == null) continue;
        if (price.value <= 0) { toast.error('Der bestätigte Preis muss größer als 0 sein.'); return; }
        if (!values.bestaetigtAm || values.bestaetigtAm > heute()) { toast.error('Bitte ein gültiges, nicht zukünftiges Bestätigungsdatum angeben.'); return; }
        if (values.gueltigBis && values.gueltigBis < heute()) { toast.error('Dieser Preis ist abgelaufen. Bitte einen aktuellen Preis bestätigen oder den Preis offen lassen.'); return; }
        if (!values.quelle.trim()) { toast.error('Bitte Quelle oder Belegreferenz je Preis angeben.'); return; }
        if (!need.position.basis?.einheit) { toast.error('Für diesen Bedarf fehlt eine eindeutige Preiseinheit.'); return; }
        preise.push({ bedarfId: id, preis: price.value, einheit: need.position.basis.einheit, basisMenge: 1, preisHistorieId: null, bestaetigtAm: values.bestaetigtAm, gueltigBis: values.gueltigBis || null, bestaetigungsbeleg: values.quelle.trim() });
      }
      const empfaenger: KontaktSnapshot = { lieferantId: lieferant.id, kontaktId: kontakt.id, lieferantenname: lieferant.lieferantenname, email: kontakt.email, name: kontakt.name, anrede: kontakt.anrede, eigeneKundennummer: lieferant.eigeneKundennummer ?? null };
      requestRef.current = { lieferantId: lieferant.id, empfaenger, paket, preise, liefertermin: liefertermin || null, bestaetigungsfrist: bestaetigungsfrist || null, bedingungen: bedingungen.trim() || null, idempotenzKey: crypto.randomUUID() };
    }
    busyRef.current = true; setBusy(true);
    try {
      const result = await einkaufApi.post<{ id: number }>('/api/einkauf/bestellungen/direkt', requestRef.current);
      toast.success('Bestellentwurf mit interner Bestellnummer wurde angelegt.'); onCreated(result.id);
    } catch (error) {
      // Bei unklarem Ausgang exakt dieselbe Anfrage erneut senden, niemals eine zweite Bestellung erzeugen.
      const sicherAbgelehnt = error instanceof EinkaufApiError && error.status >= 400 && error.status < 500;
      if (sicherAbgelehnt) requestRef.current = null;
      setUnklar(!sicherAbgelehnt);
      toast.error(error instanceof Error ? error.message : 'Direktbestellung konnte nicht angelegt werden.');
    } finally { busyRef.current = false; setBusy(false); }
  };
  const gesperrt = busy || unklar;
  return <Dialog open onOpenChange={open => { if (!open && !busy) onClose(); }} className="w-[min(64rem,calc(100vw-2rem))]">
    <DialogHeader className="px-6 pt-6"><DialogTitle>Direktbestellung vorbereiten</DialogTitle><DialogDescription>Lieferant und Mengen auswählen. Preise können offen bleiben – auch beim Versand. Der Entwurf erhält eine interne Bestellnummer.</DialogDescription></DialogHeader>
    <DialogContent className="max-h-[70vh] space-y-4 overflow-y-auto px-6 py-4">
      {unklar && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Die Antwort ist ausgeblieben. Bitte dieselbe Bestellung erneut versuchen; sie wird dabei nicht doppelt angelegt.</p>}
      <fieldset disabled={gesperrt} className="space-y-4">
        <div className="flex flex-wrap items-center gap-3"><Button variant="outline" disabled={gesperrt || lieferantLaedt} onClick={() => setPicker(true)}>{lieferantLaedt ? 'Lieferant wird geladen …' : lieferant ? `Lieferant: ${lieferant.lieferantenname}` : 'Lieferant wählen'}</Button>{kontakt && <span className="text-sm text-slate-600">Kontakt: {kontakt.name ?? kontakt.email}</span>}{kontakte.length > 0 && <Select disabled={gesperrt} aria-label="Bestellkontakt" value={kontaktId} options={kontakte.map(item => ({ value: String(item.id), label: `${item.name ?? item.email}${item.standardBestellung ? ' · Standard' : ''}` }))} onChange={setKontaktId} />}</div>
        {loading ? <p role="status">Verfügbare Bedarfe werden geladen …</p> : ladefehler ? <p role="alert" className="text-sm text-rose-700">{ladefehler}</p> : <div className="space-y-3">{bedarfe.length === 0 ? <p className="text-sm text-slate-600">Es gibt keine verfügbaren Bedarfe.</p> : bedarfe.map(need => {
          const item = drafts[need.id]; const key = need.position.interneReferenz ?? String(need.id);
          return <article key={need.id} className="rounded-lg border border-slate-200 p-3">
            <label className="flex items-center gap-2 font-medium"><input aria-label={`Bedarf ${key} auswählen`} type="checkbox" checked={selected.includes(need.id)} onChange={event => setSelected(current => event.target.checked ? [...current, need.id] : current.filter(id => id !== need.id))} />{key} · {need.position.bezeichnung ?? 'Materialbedarf'}</label>
            <p className="ml-6 text-sm text-slate-600">Noch verfügbar: {(need.mengen.disponierbar ?? 0).toLocaleString('de-DE')} {need.position.basis?.einheit?.toLowerCase()}</p>
            {selected.includes(need.id) && <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <DecimalInput label={`Menge ${key}`} value={item.menge} required integer={need.position.basis?.einheit === 'STUECK'} min={0} max={need.mengen.disponierbar ?? 0} onChange={value => update(need.id, 'menge', value)} disabled={gesperrt} />
              <div><DecimalInput label={`Preis ${key} / ${need.position.basis?.einheit?.toLowerCase()} (optional)`} aria-label={`Preis ${key}`} value={item.preis} min={0} placeholder="Preis offen" onChange={value => update(need.id, 'preis', value)} disabled={gesperrt} />{!item.preis.trim() && <p className="mt-1 text-sm text-slate-500">Preis offen</p>}</div>
              {!!item.preis.trim() && <><label className="text-sm">Preis bestätigt am {key}<DatePicker disabled={gesperrt} aria-label={`Preis bestätigt am ${key}`} max={heute()} value={item.bestaetigtAm} onChange={value => update(need.id, 'bestaetigtAm', value)} /></label><label className="text-sm">Gültig bis {key}<DatePicker disabled={gesperrt} aria-label={`Preis gültig bis ${key}`} min={heute()} value={item.gueltigBis} onChange={value => update(need.id, 'gueltigBis', value)} /></label><label className="text-sm sm:col-span-2">Preisquelle {key}<Input aria-label={`Preisquelle ${key}`} placeholder="z. B. Angebotsnummer oder Lieferantenbeleg" value={item.quelle} onChange={event => update(need.id, 'quelle', event.target.value)} /></label></>}
            </div>}
          </article>;
        })}</div>}
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Liefertermin<DatePicker disabled={gesperrt} className="mt-1 w-full" value={liefertermin} onChange={setLiefertermin} /></label><label className="text-sm">Bestätigungsfrist<DatePicker disabled={gesperrt} className="mt-1 w-full" value={bestaetigungsfrist} onChange={setBestaetigungsfrist} /></label></div>
        <label className="block text-sm">Zusätzliche Bedingungen<textarea className="mt-1 w-full rounded border border-slate-300 p-2" value={bedingungen} onChange={event => setBedingungen(event.target.value)} /></label>
      </fieldset>
    </DialogContent>
    <DialogFooter className="border-t border-slate-200 px-6 py-4"><Button variant="outline" disabled={busy} onClick={onClose}>Abbrechen</Button><Button disabled={busy || loading || !!ladefehler || lieferantLaedt || selected.length === 0} onClick={() => void create()}>{busy ? 'Legt Entwurf an …' : 'Direktbestellung als Entwurf anlegen'}</Button></DialogFooter>
    <LieferantSearchModal isOpen={picker} onClose={() => setPicker(false)} onSelect={value => void chooseSupplier(value)} />
  </Dialog>;
}
