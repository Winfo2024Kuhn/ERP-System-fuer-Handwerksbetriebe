import { useEffect, useRef, useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi, EinkaufApiError } from '../api';
import type { BedarfResponse, EinkaufAnlage } from '../types';
import { StammdatenAuswahl, type StammdatenWahl } from './StammdatenAuswahl';
import { PositionsEditor } from './PositionsEditor';
import { fromPositionSnapshot, toPositionPayload, type PositionDraft } from '../positionDrafts';
import { KonfliktAbgleichDialog } from './KonfliktAbgleichDialog';
import type { EntwurfKonflikt } from './KonfliktAbgleichDialog';
import { abgeglichenerWert, abgleichFeld, positionsFelder, positionsWertText } from '../konfliktAbgleich';

const leererEntwurf = (): PositionDraft => ({ art: 'ARTIKEL', artikelId: null, interneReferenz: '', zeichnungsnummer: '', zeichnungsrevision: '', bezeichnung: '', werkstoff: '', abmessung: '', menge: '', einheit: 'STUECK', stueckzahl: '', einzelLaengeMm: '', kgJeMeter: '', faktorQuelle: '', schnittForm: '', winkelLinks: '', winkelRechts: '', bearbeitung: '', oberflaeche: '', dokumente: [], anlageVersionIds: [] });

export function BedarfDialog({ offen, schliessen, gespeichert, ausgangsbedarf }: {
  offen: boolean; schliessen: () => void; gespeichert: (bedarf: BedarfResponse) => void; ausgangsbedarf?: BedarfResponse;
}) {
  const toast = useToast();
  const dateieingabe = useRef<HTMLInputElement>(null);
  const [projekt, setProjekt] = useState<StammdatenWahl | null>(null);
  const [entwurf, setEntwurf] = useState<PositionDraft>(leererEntwurf);
  const [anlagen, setAnlagen] = useState<EinkaufAnlage[]>([]);
  const [hochzuladendeDatei, setHochzuladendeDatei] = useState<File | null>(null);
  const [anlagenrevision, setAnlagenrevision] = useState('');
  const [lagerzweck, setLagerzweck] = useState('');
  const [fehler, setFehler] = useState('');
  const [ladend, setLadend] = useState(false);
  const [konflikt, setKonflikt] = useState<EntwurfKonflikt | null>(null);

  useEffect(() => {
    if (!offen) return;
    setEntwurf(ausgangsbedarf ? fromPositionSnapshot(ausgangsbedarf.position) : leererEntwurf());
    setProjekt(null); setLagerzweck(ausgangsbedarf?.liefergruppe.lagerzweck ?? ''); setFehler(''); setKonflikt(null);
    setHochzuladendeDatei(null); setAnlagenrevision('');
    if (ausgangsbedarf) void Promise.all([
      einkaufApi.get<EinkaufAnlage[]>(`/api/einkauf/bedarfe/${ausgangsbedarf.id}/anlagen`),
      einkaufApi.get<Array<{ id: number; auftragsnummer?: string | null; bauvorhaben?: string | null }>>('/api/projekte/simple?size=500'),
    ]).then(([dateien, projekte]) => {
      setAnlagen(dateien);
      const zugeordnet = projekte.find(eintrag => eintrag.id === ausgangsbedarf.liefergruppe.projektId);
      if (zugeordnet) setProjekt({ id: zugeordnet.id, name: [zugeordnet.auftragsnummer, zugeordnet.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt' });
    }).catch(error => { const meldung = error instanceof Error ? error.message : 'Bedarfsdaten konnten nicht geladen werden.'; setFehler(meldung); toast.error(meldung); });
    else setAnlagen([]);
  }, [offen, ausgangsbedarf, toast]);

  const anlageHochladen = async () => {
    if (!ausgangsbedarf || !hochzuladendeDatei) return;
    if (hochzuladendeDatei.size > 10 * 1024 * 1024) { const meldung = 'Die Datei darf höchstens 10 MiB groß sein.'; setFehler(meldung); toast.error(meldung); return; }
    if (!anlagenrevision.trim()) { const meldung = 'Bitte vor dem Upload eine Revision angeben.'; setFehler(meldung); toast.error(meldung); return; }
    setLadend(true); setFehler('');
    try {
      const form = new FormData(); form.append('datei', hochzuladendeDatei); form.append('revision', anlagenrevision.trim());
      const hochgeladen = await fetch(`/api/einkauf/bedarfe/${ausgangsbedarf.id}/anlagen`, { method: 'POST', body: form });
      if (!hochgeladen.ok) throw new Error(`Anlage konnte nicht hochgeladen werden (HTTP ${hochgeladen.status}).`);
      const anlage = await hochgeladen.json() as EinkaufAnlage;
      const freigabe = await einkaufApi.post<EinkaufAnlage>(`/api/einkauf/anlagen/${anlage.id}/freigeben`, {});
      setAnlagen(aktuell => [...aktuell, freigabe]);
      setEntwurf(aktuell => ({ ...aktuell, anlageVersionIds: [...aktuell.anlageVersionIds, freigabe.id] }));
      setHochzuladendeDatei(null); setAnlagenrevision(''); toast.success('Zeichnungsanlage wurde hochgeladen und freigegeben.');
    } catch (error) { const meldung = error instanceof Error ? error.message : 'Anlage konnte nicht vollständig freigegeben werden.'; setFehler(meldung); toast.error(meldung); }
    finally { setLadend(false); }
  };

  const speichere = async (basis = ausgangsbedarf) => {
    const position = toPositionPayload(entwurf, { anlageBeiErstanlage: !basis && entwurf.art === 'ZEICHNUNGSTEIL' });
    if (!position.valid) { setFehler(position.message); toast.error(position.message); return; }
    if (position.value.art === 'ZEICHNUNGSTEIL' && (!projekt || !position.value.interneReferenz?.trim() || !position.value.zeichnungsrevision?.trim())) {
      const meldung = 'Zeichnungsteile brauchen ein Projekt, eine Projektkennung und eine Zeichnungsrevision.'; setFehler(meldung); toast.error(meldung); return;
    }
    if (!projekt && !lagerzweck.trim()) { const meldung = 'Bitte ein Projekt oder einen Lagerzweck auswählen.'; setFehler(meldung); toast.error(meldung); return; }
    const liefergruppe = { lieferadresse: basis?.liefergruppe.lieferadresse ?? null, bedarfstermin: basis?.liefergruppe.bedarfstermin ?? null, projektId: projekt?.id ?? null, lagerzweck: lagerzweck.trim() || null };
    const koerper = { ...(basis ? { version: basis.version } : {}), position: position.value, liefergruppe, artikelInProjektId: basis ? undefined : null };
    setLadend(true); setFehler('');
    try {
      let ergebnis: BedarfResponse;
      if (!basis && position.value.art === 'ZEICHNUNGSTEIL') {
        if (!hochzuladendeDatei || !anlagenrevision.trim()) throw new Error('Bitte wählen Sie eine Zeichnungsdatei aus und geben Sie deren Revision an.');
        const form = new FormData(); form.append('bedarf', new Blob([JSON.stringify({ ...koerper, position: { ...position.value, anlageVersionIds: [] } })], { type: 'application/json' })); form.append('datei', hochzuladendeDatei); form.append('revision', anlagenrevision.trim());
        const antwort = await fetch('/api/einkauf/bedarf/zeichnungsteil', { method: 'POST', body: form });
        if (!antwort.ok) throw new EinkaufApiError(`Zeichnungsteil konnte nicht angelegt werden (HTTP ${antwort.status}).`, antwort.status);
        ergebnis = await antwort.json() as BedarfResponse;
      } else {
        ergebnis = await (basis ? einkaufApi.put<BedarfResponse>(`/api/einkauf/bedarf/${basis.id}`, koerper) : einkaufApi.post<BedarfResponse>('/api/einkauf/bedarf', koerper));
      }
      gespeichert(ergebnis);
    } catch (error) {
      const meldung = error instanceof Error ? error.message : 'Bedarf konnte nicht gespeichert werden.';
      setFehler(meldung); toast.error(meldung);
      if (basis && error instanceof EinkaufApiError && error.status === 409) {
        try {
          const [aktuell, projekte] = await Promise.all([
            einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${basis.id}`),
            einkaufApi.get<Array<{ id: number; auftragsnummer?: string | null; bauvorhaben?: string | null }>>('/api/projekte/simple?size=500'),
          ]);
          const serverEntwurf = fromPositionSnapshot(aktuell.position);
          const serverProjekt = projekte.find(eintrag => eintrag.id === aktuell.liefergruppe.projektId);
          const serverProjektName = serverProjekt ? [serverProjekt.auftragsnummer, serverProjekt.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt' : aktuell.liefergruppe.lagerzweck ?? '';
          const lokalesProjekt = projekte.find(eintrag => eintrag.id === projekt?.id);
          const lokalesProjektName = lokalesProjekt ? [lokalesProjekt.auftragsnummer, lokalesProjekt.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt' : projekt?.name ?? lagerzweck;
          const felder: EntwurfKonflikt['felder'] = [];
          (Object.keys(positionsFelder) as Array<keyof PositionDraft>).forEach(feld => abgleichFeld(felder, feld, positionsFelder[feld], entwurf[feld], serverEntwurf[feld], wert => positionsWertText(feld, wert as PositionDraft[keyof PositionDraft], anlagen)));
          abgleichFeld(felder, 'projekt', 'Projekt oder Lagerzweck', lokalesProjektName, serverProjektName, String);
          setKonflikt({ felder, hinweise: ['Der Bedarf wurde zwischenzeitlich geändert. Mengen und technische Angaben werden einzeln abgeglichen; Ihr Entwurf bleibt erhalten.'], anwenden: wahl => { setEntwurf(vorher => Object.fromEntries((Object.keys(positionsFelder) as Array<keyof PositionDraft>).map(feld => [feld, abgeglichenerWert(wahl, feld, vorher[feld], serverEntwurf[feld])])) as unknown as PositionDraft); const projektId = abgeglichenerWert(wahl, 'projekt', projekt?.id ?? null, aktuell.liefergruppe.projektId); const lager = abgeglichenerWert(wahl, 'projekt', lagerzweck, aktuell.liefergruppe.lagerzweck ?? ''); const gewaehltesProjekt = projekte.find(eintrag => eintrag.id === projektId); setProjekt(gewaehltesProjekt ? { id: gewaehltesProjekt.id, name: [gewaehltesProjekt.auftragsnummer, gewaehltesProjekt.bauvorhaben].filter(Boolean).join(' · ') || 'Projekt' } : null); setLagerzweck(projektId ? '' : lager); gespeichertesBasis.current = aktuell; setFehler(''); } });
        } catch (ladeFehler) { toast.error(ladeFehler instanceof Error ? ladeFehler.message : 'Aktueller Bedarf konnte nicht geladen werden.'); }
      }
    } finally { setLadend(false); }
  };
  const gespeichertesBasis = useRef<BedarfResponse | undefined>(ausgangsbedarf);
  useEffect(() => { gespeichertesBasis.current = ausgangsbedarf; }, [ausgangsbedarf]);

  return <Dialog open={offen} onOpenChange={wert => { if (!wert && !ladend) schliessen(); }}><DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
    <DialogHeader><DialogTitle>{ausgangsbedarf ? 'Bedarf bearbeiten' : 'Bedarf erfassen'}</DialogTitle></DialogHeader>
    <div className="space-y-4"><PositionsEditor value={entwurf} onChange={setEntwurf} anlagen={anlagen} />
      <StammdatenAuswahl art="Projekt" value={projekt} onChange={setProjekt} />
      {!projekt && <label className="space-y-1 text-sm font-medium">Lagerzweck<Input aria-label="Lagerzweck" value={lagerzweck} onChange={ereignis => setLagerzweck(ereignis.target.value)} maxLength={160} /></label>}
      {entwurf.art === 'ZEICHNUNGSTEIL' && !ausgangsbedarf && <section className="space-y-2 rounded-lg border border-slate-200 p-3"><h3 className="font-semibold">Zeichnung für den neuen Bedarf</h3><label className="space-y-1 text-sm">Zeichnungsrevision der Datei<Input aria-label="Anlagenrevision" value={anlagenrevision} onChange={ereignis => setAnlagenrevision(ereignis.target.value)} /></label><input ref={dateieingabe} hidden aria-label="Zeichnungsdatei" type="file" onChange={ereignis => setHochzuladendeDatei(ereignis.target.files?.[0] ?? null)} /><Button type="button" variant="outline" onClick={() => dateieingabe.current?.click()}>Zeichnungsdatei auswählen</Button><span className="ml-2 text-sm text-slate-600">{hochzuladendeDatei?.name ?? 'Keine Datei ausgewählt'}</span></section>}
      {ausgangsbedarf && <section className="space-y-2 rounded-lg border border-slate-200 p-3"><h3 className="font-semibold">Zeichnungen und Anlagen</h3><label className="space-y-1 text-sm">Neue Anlagenrevision<Input aria-label="Anlagenrevision" value={anlagenrevision} onChange={ereignis => setAnlagenrevision(ereignis.target.value)} /></label><input ref={dateieingabe} hidden aria-label="Zeichnung oder Anlage hochladen" type="file" onChange={ereignis => setHochzuladendeDatei(ereignis.target.files?.[0] ?? null)} /><Button type="button" variant="outline" onClick={() => dateieingabe.current?.click()}>Zeichnung oder Anlage auswählen</Button><span className="ml-2 text-sm text-slate-600">{hochzuladendeDatei?.name ?? 'Keine Datei ausgewählt'}</span><Button type="button" variant="outline" disabled={ladend || !hochzuladendeDatei} onClick={() => void anlageHochladen()}>Datei hochladen und freigeben</Button></section>}
    </div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <DialogFooter><Button variant="outline" disabled={ladend} onClick={schliessen}>Abbrechen</Button><Button disabled={ladend} onClick={() => void speichere(gespeichertesBasis.current)}>{ladend ? 'Wird gespeichert …' : 'Bedarf speichern'}</Button></DialogFooter>
    {konflikt && <KonfliktAbgleichDialog konflikt={konflikt} onAbbrechen={() => setKonflikt(null)} onUebernehmen={wahl => { konflikt.anwenden(wahl); setKonflikt(null); }} />}
  </DialogContent></Dialog>;
}
