import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi } from '../api';
import type { ChargeZuordnungDto, ZeugnisErwartung, ZeugnisPruefungResponse } from '../types';
import type { Lieferung } from '../bestellTypes';
import { BelegAuswahl } from './BelegAuswahl';
import type { BestellBelegDatei } from '../belegApi';

interface ZeugnisZuordnungResponse { erwartungen: ZeugnisErwartung[]; klaerungNoetig: boolean; chargen: ChargeZuordnungDto[] }
interface PruefEntwurf { ergebnis: 'BESTANDEN' | 'ABGELEHNT' | 'KLAERUNG'; begruendung: string }

export function ZeugnisZuordnung({ bestellungId }: { bestellungId: number }) {
  const toast = useToast();
  const [erwartungen, setErwartungen] = useState<ZeugnisErwartung[]>([]);
  const [lieferungen, setLieferungen] = useState<Lieferung[]>([]);
  const [rechte, setRechte] = useState<string[]>([]);
  const [datei, setDatei] = useState<BestellBelegDatei | null>(null);
  const [zuordnungen, setZuordnungen] = useState<ChargeZuordnungDto[]>([]);
  const [ausgewaehlteErwartungen, setAusgewaehlteErwartungen] = useState<number[]>([]);
  const [ausgewaehlteLieferungen, setAusgewaehlteLieferungen] = useState<number[]>([]);
  const [ausgewaehlteChargen, setAusgewaehlteChargen] = useState<number[]>([]);
  const [pruefung, setPruefung] = useState<PruefEntwurf>({ ergebnis: 'BESTANDEN', begruendung: '' });
  const [pruefCharge, setPruefCharge] = useState<ChargeZuordnungDto | null>(null);
  const [fehler, setFehler] = useState('');
  const [laden, setLaden] = useState(true);
  const [speichert, setSpeichert] = useState(false);
  const darfPruefen = rechte.includes('ZEUGNIS_PRUEFEN');

  const ladeDaten = useCallback(async () => {
    setLaden(true); setFehler('');
    try {
      const [expected, batches, permissions] = await Promise.all([
        einkaufApi.get<ZeugnisErwartung[]>(`/api/einkauf/zeugnisse?bestellungId=${bestellungId}`),
        einkaufApi.get<Lieferung[]>(`/api/einkauf/bestellungen/${bestellungId}/lieferungen`),
        einkaufApi.get<string[]>('/api/einkauf/berechtigungen'),
      ]);
      setErwartungen(expected); setLieferungen(batches); setRechte(permissions);
      setAusgewaehlteErwartungen(expected.map(x => x.id));
      const positions = batches.flatMap(b => b.positionen).filter(p => expected.some(e => e.bestellPositionId === p.bestellPositionId));
      setAusgewaehlteLieferungen(positions.map(p => p.id));
      setAusgewaehlteChargen(positions.flatMap(p => p.chargen.map(c => c.id)));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Zeugnisse konnten nicht geladen werden.';
      setFehler(message); toast.error(message);
    } finally { setLaden(false); }
  }, [bestellungId, toast]);

  useEffect(() => { void ladeDaten(); }, [ladeDaten]);

  const positions = useMemo(() => lieferungen.flatMap(batch => batch.positionen), [lieferungen]);
  const selectedExpected = erwartungen.filter(x => ausgewaehlteErwartungen.includes(x.id));
  const selectedPositionIds = positions.filter(p => ausgewaehlteLieferungen.includes(p.id)
    && selectedExpected.some(e => e.bestellPositionId === p.bestellPositionId)).map(p => p.id);
  const selectedChargeIds = positions.filter(p => ausgewaehlteLieferungen.includes(p.id)
    && selectedExpected.some(e => e.bestellPositionId === p.bestellPositionId))
    .flatMap(p => p.chargen.filter(c => ausgewaehlteChargen.includes(c.id)).map(c => c.id));

  const zuordnen = async () => {
    if (!datei?.dateiId) { setFehler('Bitte wählen Sie zuerst ein vorhandenes Zeugnis oder laden Sie ein PDF hoch.'); return; }
    if (!selectedExpected.length || !selectedPositionIds.length || !selectedChargeIds.length) {
      setFehler('Wählen Sie mindestens eine passende Zeugnisanforderung, Lieferposition und Charge aus.'); return;
    }
    setSpeichert(true); setFehler('');
    try {
      await Promise.all(selectedExpected.map(expected => einkaufApi.postVoid(
        `/api/einkauf/zeugnisse/${expected.id}/eingang/${datei.dateiId}`, {})));
      const response = await einkaufApi.post<ZeugnisZuordnungResponse>(`/api/einkauf/zeugnisse/${datei.dateiId}/zuordnen`, {
        dokumentId: datei.dateiId,
        erwartungIds: selectedExpected.map(x => x.id),
        lieferPositionIds: selectedPositionIds,
        chargeIds: selectedChargeIds,
        schmelznummer: null,
      });
      setZuordnungen(response.chargen);
      toast.success(response.klaerungNoetig ? 'Zeugnis zugeordnet. Charge braucht eine Klärung.' : 'Zeugnis zugeordnet.');
      await ladeDaten();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Zeugnis konnte nicht zugeordnet werden.';
      setFehler(message); toast.error(message);
    } finally { setSpeichert(false); }
  };

  const pruefen = async () => {
    if (!pruefCharge) return;
    if (!pruefung.begruendung.trim()) { setFehler('Bitte begründen Sie die Zeugnisprüfung.'); return; }
    const expected = erwartungen.find(x => x.id === pruefCharge.erwartungId);
    if (!expected?.grundlageVersion) { setFehler('Für diese Zeugnisanforderung fehlt eine dokumentierte Grundlage.'); return; }
    setSpeichert(true); setFehler('');
    try {
      const result = await einkaufApi.post<ZeugnisPruefungResponse>(`/api/einkauf/zeugnisse/${pruefCharge.zuordnungId}/pruefen`, {
        version: pruefCharge.version, ergebnis: pruefung.ergebnis, begruendung: pruefung.begruendung.trim(), grundlageVersion: expected.grundlageVersion,
      });
      setZuordnungen(current => current.map(x => x.zuordnungId === pruefCharge.zuordnungId
        ? { ...x, status: result.materialFreigegeben ? 'GEPRUEFT' : pruefung.ergebnis === 'KLAERUNG' ? 'KLAERUNG_NOETIG' : 'EINGEGANGEN', materialFreigegeben: result.materialFreigegeben }
        : x));
      setPruefCharge(null); setPruefung({ ergebnis: 'BESTANDEN', begruendung: '' });
      toast.success(result.materialFreigegeben ? 'Material ist für diese Charge freigegeben.' : 'Prüfung wurde gespeichert.');
      await ladeDaten();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Zeugnisprüfung konnte nicht gespeichert werden.';
      setFehler(message); toast.error(message);
    } finally { setSpeichert(false); }
  };

  return <section aria-label="Zeugnisse und Materialfreigabe" className="space-y-4">
    <header><h2 className="text-lg font-semibold text-slate-900">Zeugnisse und Materialfreigabe</h2><p className="text-sm text-slate-600">Ein PDF kann mehrere passende Lieferpositionen belegen. Der Eingang ersetzt nicht die Prüfung.</p></header>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    {laden ? <p role="status">Zeugnisse werden geladen …</p> : erwartungen.length === 0 ? <p className="rounded-lg border border-slate-200 bg-white p-3 text-sm text-slate-600">Für diese Bestellung sind keine Zeugnisse hinterlegt.</p> : <>
      {!darfPruefen && <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">Nur Personen mit Zeugnis-Prüfrecht können Zeugnisse prüfen und Material freigeben.</p>}
      <div className="grid gap-3 md:grid-cols-2">{erwartungen.map(expected => <article key={expected.id} className="rounded-lg border border-slate-200 bg-white p-3">
        <h3 className="font-semibold text-slate-800">{expected.art.replaceAll('_', ' ')} · Position {expected.bestellPositionId}</h3>
        <p className="text-sm text-slate-600">Grundlage: {expected.grundlage ?? 'manuelle Prüfung erforderlich'} · Version {expected.grundlageVersion ?? 'nicht angegeben'}</p>
        <p className="mt-1 text-sm">{expected.dateiIds.length ? 'PDF eingegangen' : 'Zeugnis fehlt'} · {expected.materialFreigegeben ? 'Material freigegeben' : 'Material nicht freigegeben'}</p>
        <label className="mt-2 flex items-center gap-2 text-sm"><input type="checkbox" checked={ausgewaehlteErwartungen.includes(expected.id)} onChange={event => setAusgewaehlteErwartungen(current => event.target.checked ? [...current, expected.id] : current.filter(id => id !== expected.id))} />Für diese Anforderung zuordnen</label>
      </article>)}</div>
      <div className="grid gap-4 md:grid-cols-2">
        <fieldset className="space-y-2 rounded-lg border border-slate-200 p-3"><legend className="px-1 text-sm font-semibold">Passende Lieferpositionen</legend>
          {positions.length ? positions.filter(p => erwartungen.some(e => e.bestellPositionId === p.bestellPositionId)).map(p => <label key={p.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={ausgewaehlteLieferungen.includes(p.id)} onChange={event => setAusgewaehlteLieferungen(current => event.target.checked ? [...current, p.id] : current.filter(id => id !== p.id))} />Lieferposition {p.id} · Position {p.bestellPositionId} · {p.menge.toLocaleString('de-DE')}</label>) : <p className="text-sm text-slate-600">Noch keine passende Lieferung. Zeugnisse können nach dem Wareneingang zugeordnet werden.</p>}
        </fieldset>
        <fieldset className="space-y-2 rounded-lg border border-slate-200 p-3"><legend className="px-1 text-sm font-semibold">Passende Chargen</legend>
          {positions.filter(p => erwartungen.some(e => e.bestellPositionId === p.bestellPositionId)).flatMap(p => p.chargen.map(charge => <label key={charge.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={ausgewaehlteChargen.includes(charge.id)} onChange={event => setAusgewaehlteChargen(current => event.target.checked ? [...current, charge.id] : current.filter(id => id !== charge.id))} />Charge {charge.kennung ?? charge.id}{charge.schmelznummer ? ` · Schmelze ${charge.schmelznummer}` : ''}</label>))}
        </fieldset>
      </div>
      <BelegAuswahl bestellungId={bestellungId} typ="SONSTIG" value={datei} onChange={setDatei} />
      <Button type="button" onClick={() => void zuordnen()} disabled={speichert || !selectedExpected.length || !selectedPositionIds.length || !selectedChargeIds.length}>Zeugnis zuordnen</Button>
      {zuordnungen.map(link => <article key={link.zuordnungId} className="rounded-lg border border-slate-200 bg-white p-3 text-sm">
        <p>Charge {link.chargeId}: {link.status === 'GEPRUEFT' && link.materialFreigegeben ? 'Material freigegeben' : link.status === 'KLAERUNG_NOETIG' ? 'Klärung nötig' : link.status}</p>
        {darfPruefen ? <Button type="button" variant="outline" size="sm" onClick={() => setPruefCharge(link)} disabled={!['ZUGEORDNET', 'GEPRUEFT'].includes(link.status)}>Zeugnis prüfen · Charge {link.chargeId}</Button>
          : <p className="text-slate-600">Nur Personen mit Zeugnis-Prüfrecht können Material freigeben.</p>}
      </article>)}
      {pruefCharge && darfPruefen && <section aria-label="Zeugnis prüfen" className="space-y-2 rounded-lg border border-rose-200 bg-rose-50 p-3">
        <h3 className="font-semibold">Zeugnis prüfen · Charge {pruefCharge.chargeId}</h3>
        <div className="flex flex-wrap gap-2">{(['BESTANDEN', 'ABGELEHNT', 'KLAERUNG'] as const).map(result => <Button key={result} type="button" variant={pruefung.ergebnis === result ? 'default' : 'outline'} size="sm" onClick={() => setPruefung(current => ({ ...current, ergebnis: result }))}>{result === 'BESTANDEN' ? 'Bestanden' : result === 'ABGELEHNT' ? 'Abgelehnt' : 'Klärung nötig'}</Button>)}</div>
        <label className="block text-sm font-medium">Begründung<Input value={pruefung.begruendung} onChange={event => setPruefung(current => ({ ...current, begruendung: event.target.value }))} maxLength={2000} /></label>
        <div className="flex gap-2"><Button type="button" onClick={() => void pruefen()} disabled={speichert}>Prüfung speichern</Button><Button type="button" variant="outline" onClick={() => setPruefCharge(null)} disabled={speichert}>Abbrechen</Button></div>
      </section>}
    </>}
  </section>;
}
