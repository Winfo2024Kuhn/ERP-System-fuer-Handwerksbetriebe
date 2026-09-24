import { useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select-custom';
import { DatePicker } from '../../../components/ui/datepicker';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import { validateNumberDrafts } from '../../../lib/numberDrafts';
import { einkaufApi, EinkaufApiError } from '../api';
import { fromPositionSnapshot, toPositionPayload, type PositionDraft } from '../positionDrafts';
import type { AnfrageDetail, BedarfResponse, KontaktSnapshot, EinkaufAnlage } from '../types';
import { PositionsEditor } from './PositionsEditor';

type Auswahl = { bedarf: BedarfResponse; menge: string };
type Lieferant = { id: number; lieferantenname: string; kundenNummer?: string };
type Kontakt = { id: number; email: string; name?: string | null; anrede?: string | null; standardAnfrage: boolean; aktiv: boolean };
type Liste<T> = { content: T[]; totalPages: number };
const text = (error: unknown) => error instanceof Error ? error.message : 'Die Anfrage konnte nicht bearbeitet werden.';
const dezimal = (value: number) => String(value).replace('.', ',');

export function AnfrageEntwurfEditor({ initial, onSaved, onCancel }: {
  initial?: AnfrageDetail; onSaved: (detail: AnfrageDetail) => void; onCancel?: () => void;
}) {
  const toast = useToast();
  const [auswahl, setAuswahl] = useState<Record<number, Auswahl>>({});
  const [empfaenger, setEmpfaenger] = useState<KontaktSnapshot[]>(initial?.lieferanten.flatMap(l => l.kontakt ? [l.kontakt] : []) ?? []);
  const [antwortfrist, setAntwortfrist] = useState(initial?.kopf.antwortfrist ?? '');
  const [liefertermin, setLiefertermin] = useState(initial?.kopf.liefertermin ?? '');
  const [version, setVersion] = useState(initial?.kopf.version ?? 0);
  const [laedt, setLaedt] = useState(Boolean(initial));
  const [busy, setBusy] = useState(false);
  const [fehler, setFehler] = useState('');
  const [bedarfSuche, setBedarfSuche] = useState('');
  const [bedarfSeite, setBedarfSeite] = useState(0);
  const [bedarfe, setBedarfe] = useState<Liste<BedarfResponse>>({ content: [], totalPages: 0 });
  const [lieferantSuche, setLieferantSuche] = useState('');
  const [lieferantSeite, setLieferantSeite] = useState(0);
  const [lieferanten, setLieferanten] = useState<Lieferant[]>([]);
  const [lieferantGesamt, setLieferantGesamt] = useState(0);
  const [kontaktLieferant, setKontaktLieferant] = useState<Lieferant | null>(null);
  const [kontakte, setKontakte] = useState<Kontakt[]>([]);
  const [kontaktId, setKontaktId] = useState('');
  const [bearbeitung, setBearbeitung] = useState<{ id: number; draft: PositionDraft; anlagen: EinkaufAnlage[] } | null>(null);
  const [konflikt, setKonflikt] = useState<{ kopf?: AnfrageDetail; bedarfe: BedarfResponse[] } | null>(null);
  const [key, setKey] = useState(() => crypto.randomUUID());
  const melden = (error: unknown) => { setFehler(text(error)); toast.error(text(error)); };

  useEffect(() => {
    let aktiv = true;
    if (!initial) return;
    const herkuenfte = initial.positionen.flatMap(p => p.herkuenfte);
    Promise.all(herkuenfte.map(async h => ({ bedarf: await einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${h.bedarfId}`), menge: dezimal(h.menge ?? 0) })))
      .then(items => { if (aktiv) setAuswahl(Object.fromEntries(items.map(item => [item.bedarf.id, item]))); })
      .catch(error => { if (aktiv) { setFehler(text(error)); toast.error(text(error)); } })
      .finally(() => { if (aktiv) setLaedt(false); });
    return () => { aktiv = false; };
  }, [initial, toast]);
  useEffect(() => {
    let aktiv = true;
    einkaufApi.get<Liste<BedarfResponse>>(`/api/einkauf/bedarf?q=${encodeURIComponent(bedarfSuche)}&page=${bedarfSeite}&size=10`)
      .then(data => { if (aktiv) setBedarfe(data); }).catch(error => { if (aktiv) { setFehler(text(error)); toast.error(text(error)); } });
    return () => { aktiv = false; };
  }, [bedarfSuche, bedarfSeite, toast]);
  useEffect(() => {
    let aktiv = true;
    einkaufApi.get<{ lieferanten: Lieferant[]; gesamt: number }>(`/api/lieferanten?q=${encodeURIComponent(lieferantSuche)}&page=${lieferantSeite}&size=10`)
      .then(data => { if (aktiv) { setLieferanten(data.lieferanten); setLieferantGesamt(data.gesamt ?? data.lieferanten.length); } })
      .catch(error => { if (aktiv) { setFehler(text(error)); toast.error(text(error)); } });
    return () => { aktiv = false; };
  }, [lieferantSuche, lieferantSeite, toast]);

  const kontaktWaehlen = async (lieferant: Lieferant) => {
    try {
      const items = await einkaufApi.get<Kontakt[]>(`/api/lieferanten/${lieferant.id}/einkauf-kontakte`);
      setKontakte(items.filter(k => k.aktiv)); setKontaktLieferant(lieferant);
      setKontaktId(String(items.find(k => k.aktiv && k.standardAnfrage)?.id ?? ''));
    } catch (error) { melden(error); }
  };
  const kontaktUebernehmen = () => {
    const kontakt = kontakte.find(k => String(k.id) === kontaktId);
    if (!kontakt || !kontaktLieferant) return;
    const l = kontaktLieferant;
    setEmpfaenger(old => [...old.filter(k => k.lieferantId !== l.id), { lieferantId: l.id, kontaktId: kontakt.id,
      lieferantenname: l.lieferantenname, email: kontakt.email, name: kontakt.name ?? null, anrede: kontakt.anrede ?? null, eigeneKundennummer: l.kundenNummer ?? null }]);
    setKontaktLieferant(null); setKey(crypto.randomUUID());
  };
  const positionBearbeiten = async (item: Auswahl) => {
    try {
      const anlagen = await einkaufApi.get<EinkaufAnlage[]>(`/api/einkauf/bedarfe/${item.bedarf.id}/anlagen`);
      setBearbeitung({ id: item.bedarf.id, draft: fromPositionSnapshot(item.bedarf.position), anlagen });
    } catch (error) { melden(error); }
  };
  const konfliktLaden = async () => {
    const [kopf, aktuell] = await Promise.all([
      initial ? einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${initial.kopf.id}`) : Promise.resolve(undefined),
      Promise.all(Object.keys(auswahl).map(id => einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${id}`))),
    ]);
    setKonflikt({ kopf, bedarfe: aktuell });
  };
  const bedarfSpeichern = async () => {
    if (!bearbeitung) return;
    const payload = toPositionPayload(bearbeitung.draft);
    if (!payload.valid) { melden(new Error(payload.message)); return; }
    const item = auswahl[bearbeitung.id];
    setBusy(true);
    try {
      const saved = await einkaufApi.put<BedarfResponse>(`/api/einkauf/bedarf/${bearbeitung.id}`, {
        version: item.bedarf.version, position: payload.value, liefergruppe: item.bedarf.liefergruppe,
      });
      setAuswahl(old => ({ ...old, [saved.id]: { ...old[saved.id], bedarf: saved } }));
      setBearbeitung(null); setKey(crypto.randomUUID()); toast.success('Bedarfsposition gespeichert. Die Anfragemenge bleibt separat.');
    } catch (error) {
      melden(error);
      if (error instanceof EinkaufApiError && error.status === 409) try { await konfliktLaden(); } catch (loadingError) { melden(loadingError); }
    } finally { setBusy(false); }
  };
  const speichern = async () => {
    setFehler('');
    if (!Object.keys(auswahl).length) { melden(new Error('Bitte mindestens einen Bedarf auswählen.')); return; }
    if (!empfaenger.length) { melden(new Error('Bitte mindestens einen Einkaufskontakt auswählen.')); return; }
    if (antwortfrist && liefertermin && antwortfrist > liefertermin) { melden(new Error('Die Antwortfrist darf nicht nach dem Liefertermin liegen.')); return; }
    const positionen = [];
    for (const item of Object.values(auswahl)) {
      const menge = validateNumberDrafts({ menge: item.menge }, { menge: { label: `Menge ${item.bedarf.position.bezeichnung}`, required: true,
        min: 0.000001, max: item.bedarf.mengen.disponierbar ?? 0, maxDecimalPlaces: 6, integer: item.bedarf.position.basis?.einheit === 'STUECK' } });
      if (!menge.valid) { melden(new Error(menge.message)); return; }
      positionen.push({ bedarfId: item.bedarf.id, version: item.bedarf.version, menge: menge.values.menge });
    }
    setBusy(true);
    try {
      const inhalt = { positionen, empfaenger, antwortfrist: antwortfrist || null, liefertermin: liefertermin || null,
        zustaendigId: initial?.kopf.zustaendigId ?? null, idempotenzKey: key };
      const saved = await einkaufApi.post<AnfrageDetail>(initial ? `/api/einkauf/anfragen/${initial.kopf.id}/revisionen` : '/api/einkauf/anfragen',
        initial ? { version, inhalt } : inhalt);
      toast.success('Anfragefassung gespeichert.'); onSaved(saved);
    } catch (error) {
      melden(error);
      if (error instanceof EinkaufApiError && error.status === 409) try { await konfliktLaden(); } catch (loadingError) { melden(loadingError); }
    } finally { setBusy(false); }
  };
  const konfliktPanel = konflikt && <section className="space-y-2 rounded border border-rose-200 bg-rose-50 p-3" aria-label="Änderungen abgleichen"><h3 className="font-semibold">Zwischenzeitliche Änderungen prüfen</h3><p>Ihre Eingaben bleiben erhalten. Aktuelle Anfrageversion: {konflikt.kopf?.kopf.version ?? 'Neuanlage'}.</p>{konflikt.bedarfe.map(b => <p key={b.id}>{b.position.bezeichnung}: bisher Version {auswahl[b.id]?.bedarf.version}, jetzt {b.version}; verfügbar {b.mengen.disponierbar?.toLocaleString('de-DE')}</p>)}<Button variant="outline" onClick={() => {
      setAuswahl(old => Object.fromEntries(Object.entries(old).map(([id, item]) => [id, { ...item, bedarf: konflikt.bedarfe.find(b => b.id === Number(id)) ?? item.bedarf }])));
      if (konflikt.kopf) setVersion(konflikt.kopf.kopf.version); setKonflikt(null); setKey(crypto.randomUUID()); setFehler('');
    }}>Aktuellen Stand übernehmen, Eingaben behalten</Button></section>;
  return <section className="space-y-5 rounded-lg border border-slate-200 bg-white p-4">
    <div className="sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white py-3">
      <div><h2 className="font-semibold">{initial ? 'Neue Anfragefassung' : 'Neue Anfrage vorbereiten'}</h2><p className="text-sm text-slate-600">Bedarfe, Teilmengen und Kontakte prüfen. Versand folgt erst nach Freigabe.</p></div>
      <div className="flex gap-2">{onCancel && <Button variant="outline" onClick={onCancel}>Abbrechen</Button>}<Button disabled={busy || laedt || Boolean(konflikt)} title={konflikt ? 'Bitte zuerst die Änderungen abgleichen.' : undefined} onClick={() => void speichern()}>{busy ? 'Speichert …' : initial ? 'Fassung speichern' : 'Anfrage speichern'}</Button></div>
    </div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    {laedt && <p role="status">Aktuelle Bedarfspositionen werden geladen …</p>}
    {konfliktPanel}
    <div><label className="text-sm font-medium" htmlFor="bedarf-suche">Bedarf suchen</label><Input id="bedarf-suche" value={bedarfSuche} onChange={e => { setBedarfSuche(e.target.value); setBedarfSeite(0); }} />
      <div className="mt-2 divide-y divide-slate-100">{bedarfe.content.map(b => <label key={b.id} className="flex items-center gap-2 py-2 text-sm"><input type="checkbox" checked={Boolean(auswahl[b.id])} onChange={e => {
        setAuswahl(old => { const next = { ...old }; if (e.target.checked) next[b.id] = { bedarf: b, menge: dezimal(b.mengen.disponierbar ?? 0) }; else delete next[b.id]; return next; }); setKey(crypto.randomUUID());
      }} />{b.position.bezeichnung} · {(b.mengen.disponierbar ?? 0).toLocaleString('de-DE')} verfügbar</label>)}</div>
      <div className="mt-2 flex items-center gap-3"><Button size="sm" variant="outline" disabled={bedarfSeite === 0} onClick={() => setBedarfSeite(p => p - 1)}>Vorige Bedarfe</Button><span>Seite {bedarfSeite + 1}</span><Button size="sm" variant="outline" disabled={bedarfSeite + 1 >= (bedarfe.totalPages ?? 1)} onClick={() => setBedarfSeite(p => p + 1)}>Weitere Bedarfe</Button></div>
    </div>
    <div className="space-y-3">{Object.values(auswahl).map(item => <div key={item.bedarf.id} className="flex flex-wrap items-end gap-3 rounded border border-slate-200 p-3"><div className="min-w-48 flex-1"><DecimalInput id={`anfragemenge-${item.bedarf.id}`} label={`Menge ${item.bedarf.position.bezeichnung}`} value={item.menge} onChange={menge => { setAuswahl(old => ({ ...old, [item.bedarf.id]: { ...item, menge } })); setKey(crypto.randomUUID()); }} /></div><Button variant="outline" size="sm" onClick={() => void positionBearbeiten(item)}>Position und Anlagen bearbeiten</Button><Button variant="outline" size="sm" onClick={() => { setAuswahl(old => { const next = { ...old }; delete next[item.bedarf.id]; return next; }); setKey(crypto.randomUUID()); }}>Entfernen</Button></div>)}</div>
    <div><label className="text-sm font-medium" htmlFor="lieferant-suche">Lieferanten suchen</label><Input id="lieferant-suche" value={lieferantSuche} onChange={e => { setLieferantSuche(e.target.value); setLieferantSeite(0); }} />
      <ul className="divide-y divide-slate-100">{lieferanten.map(l => <li key={l.id} className="flex items-center justify-between gap-3 py-2"><span>{l.lieferantenname}</span><Button variant="outline" size="sm" onClick={() => void kontaktWaehlen(l)}>Kontakt auswählen</Button></li>)}</ul>
      <div className="mt-2 flex items-center gap-3"><Button variant="outline" size="sm" disabled={lieferantSeite === 0} onClick={() => setLieferantSeite(p => p - 1)}>Vorige Lieferanten</Button><span>Seite {lieferantSeite + 1}</span><Button variant="outline" size="sm" disabled={(lieferantSeite + 1) * 10 >= lieferantGesamt} onClick={() => setLieferantSeite(p => p + 1)}>Weitere Lieferanten</Button></div>
    </div>
    <section aria-label="Ausgewählte Empfänger"><h3 className="font-medium">Ausgewählte Empfänger</h3>{empfaenger.map(k => <div key={k.lieferantId} className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 py-2 text-sm"><p>{k.lieferantenname} · {k.email} · Kundennummer {k.eigeneKundennummer || 'nicht hinterlegt'}</p><Button size="sm" variant="outline" onClick={() => { setEmpfaenger(old => old.filter(e => e.lieferantId !== k.lieferantId)); setKey(crypto.randomUUID()); }}>Empfänger entfernen</Button></div>)}</section>
    <div className="grid gap-4 sm:grid-cols-2"><div><label className="text-sm font-medium" htmlFor="anfrage-antwortfrist">Antwortfrist</label><DatePicker id="anfrage-antwortfrist" value={antwortfrist} onChange={v => { setAntwortfrist(v); setKey(crypto.randomUUID()); }} /></div><div><label className="text-sm font-medium" htmlFor="anfrage-liefertermin">Gewünschter Liefertermin</label><DatePicker id="anfrage-liefertermin" value={liefertermin} onChange={v => { setLiefertermin(v); setKey(crypto.randomUUID()); }} /></div></div>
    <Dialog open={Boolean(kontaktLieferant)} onOpenChange={open => { if (!open) setKontaktLieferant(null); }}><DialogContent><DialogHeader><DialogTitle>Einkaufskontakt {kontaktLieferant?.lieferantenname}</DialogTitle></DialogHeader><Select aria-label={`Einkaufskontakt ${kontaktLieferant?.lieferantenname}`} value={kontaktId} options={kontakte.map(k => ({ value: String(k.id), label: `${k.name || 'Einkauf'} · ${k.email}` }))} onChange={setKontaktId} /><DialogFooter><Button disabled={!kontaktId} title={!kontaktId ? 'Bitte einen aktiven Kontakt auswählen.' : undefined} onClick={kontaktUebernehmen}>Kontakt übernehmen</Button></DialogFooter></DialogContent></Dialog>
    <Dialog open={Boolean(bearbeitung)} onOpenChange={open => { if (!open) setBearbeitung(null); }} className="max-w-5xl"><DialogContent className="max-h-[85vh] overflow-y-auto"><DialogHeader><DialogTitle>Bedarfsposition bearbeiten</DialogTitle></DialogHeader>{fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}{konfliktPanel}<p className="text-sm text-slate-600">Änderungen werden ausdrücklich am Bedarf gespeichert. Die angefragte Teilmenge geben Sie separat an.</p>{bearbeitung && <PositionsEditor value={bearbeitung.draft} anlagen={bearbeitung.anlagen} onChange={draft => setBearbeitung({ ...bearbeitung, draft })} />}<DialogFooter><Button variant="outline" onClick={() => setBearbeitung(null)}>Zurück</Button><Button disabled={busy || Boolean(konflikt)} onClick={() => void bedarfSpeichern()}>Bedarf speichern</Button></DialogFooter></DialogContent></Dialog>
  </section>;
}
