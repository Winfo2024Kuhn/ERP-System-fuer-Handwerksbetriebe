import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '../components/ui/button';
import { DetailLayout } from '../components/DetailLayout';
import { PageLayout } from '../components/layout/PageLayout';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { AnfrageLieferanten } from '../features/einkauf/components/AnfrageLieferanten';
import { AnfrageEntwurfEditor } from '../features/einkauf/components/AnfrageEntwurfEditor';
import { KommunikationsVerlauf } from '../features/einkauf/components/KommunikationsVerlauf';
import { Angebotsvergleich } from '../features/einkauf/components/Angebotsvergleich';
import { AngebotEditor } from '../features/einkauf/components/AngebotEditor';
import { KiVorschlaege } from '../features/einkauf/components/KiVorschlaege';
import { PreisUebernahmeDialog } from '../features/einkauf/components/PreisUebernahmeDialog';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { einkaufApi } from '../features/einkauf/api';
import type { AnfrageDetail, BestellungAusAngebot } from '../features/einkauf/types';
import type { AngebotVersion } from '../features/einkauf/types';
import type { AnfrageAngeboteEintrag } from '../features/einkauf/angebotsTypes';

type Revision = { id: number; nummer: number; status: string };
const datum = (value?: string | null) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('de-DE') : 'Nicht angegeben';
export default function EinkaufsanfrageDetail() {
  const { id } = useParams(); const navigate = useNavigate(); const toast = useToast();
  const [detail, setDetail] = useState<AnfrageDetail | null>(null);
  const [revisionen, setRevisionen] = useState<Revision[]>([]);
  const [fehler, setFehler] = useState(''); const [bearbeiten, setBearbeiten] = useState(false);
  const [wechselt, setWechselt] = useState(false); const neu = id === 'neu';
  const [angebote, setAngebote] = useState<AnfrageAngeboteEintrag[]>([]); const [angebotEditor, setAngebotEditor] = useState<{ beteiligungId: number; angebotId?: number; version?: AngebotVersion } | null>(null); const [angebotReload, setAngebotReload] = useState(0);
  const [kiJobId, setKiJobId] = useState<number | null>(null);
  const [preisQuelle, setPreisQuelle] = useState<{ angebotVersionId: number; angebotPositionId: number; artikelId: number; lieferantId: number; preis: number; waehrung: string; einheit: string; gueltigBis: string | null } | null>(null);
  useEffect(() => {
    if (neu) return;
    let aktiv = true;
    setDetail(null); setBearbeiten(false); setFehler('');
    Promise.all([einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${encodeURIComponent(id ?? '')}`),
      einkaufApi.get<Revision[]>(`/api/einkauf/anfragen/${encodeURIComponent(id ?? '')}/revisionen`)])
      .then(([d, r]) => { if (aktiv) { setDetail(d); setRevisionen(r); } })
      .catch(error => { if (aktiv) { setFehler(error.message); toast.error(error.message); } });
    return () => { aktiv = false; };
  }, [id, neu, toast]);
  useEffect(() => {
    if (neu || !detail) return;
    let aktiv = true;
    einkaufApi.get<AnfrageAngeboteEintrag[]>(`/api/einkauf/anfragen/${detail.kopf.id}/angebote`).then(value => { if (aktiv) { if (!Array.isArray(value)) throw new Error('Die Angebotsliste hat ein unerwartetes Format.'); setAngebote(value); } }).catch(error => { if (aktiv) toast.error(error instanceof Error ? error.message : 'Angebote konnten nicht geladen werden.'); });
    return () => { aktiv = false; };
  }, [detail, neu, angebotReload, toast]);
  const revisionWaehlen = async (value: string) => {
    setWechselt(true); setFehler(''); setBearbeiten(false);
    try { setDetail(await einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${id}/revisionen/${encodeURIComponent(value)}`)); }
    catch (error) { const message = error instanceof Error ? error.message : 'Fassung konnte nicht geladen werden.'; setFehler(message); toast.error(message); }
    finally { setWechselt(false); }
  };
  const bestellungVorbereiten = async (angebotVersionId: number, bestaetigtAbgelaufen = false) => {
    if (!detail || detail.historisch || !detail.angezeigteRevisionId) { toast.error('Nur Angebote der aktuellen Anfragefassung können als Bestellung vorbereitet werden.'); return; }
    const herkunftMap = new Map<number, { bedarfId: number; version: number; menge: number }>();
    for (const position of detail.positionen) for (const quelle of position.herkuenfte) {
      if (quelle.bedarfId == null || quelle.menge == null || quelle.menge <= 0) { toast.error('Die Herkunftsmenge einer Anfrageposition ist unvollständig.'); return; }
      const existing = herkunftMap.get(quelle.bedarfId);
      if (existing && existing.version !== quelle.version) { toast.error('Eine Anfrage enthält mehrere Fassungen desselben Bedarfs. Bitte zuerst die Anfrage prüfen.'); return; }
      herkunftMap.set(quelle.bedarfId, { bedarfId: quelle.bedarfId, version: quelle.version, menge: (existing?.menge ?? 0) + quelle.menge });
    }
    if (herkunftMap.size === 0) { toast.error('Für dieses Angebot fehlen zugeordnete Bedarfe.'); return; }
    try {
      const request: BestellungAusAngebot = { angebotVersionId, paket: [...herkunftMap.values()], entscheidungsgrund: bestaetigtAbgelaufen ? 'Der Betrieb bestätigt ausdrücklich die Bestellung zum abgelaufenen Angebotszeitraum.' : 'Vom Betrieb ausdrücklich zur Bestellung vorbereitet.', idempotenzKey: crypto.randomUUID() };
      const created = await einkaufApi.post<{ id: number }>('/api/einkauf/bestellungen/aus-angebot', request);
      navigate(`/bestellungen/${created.id}`);
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Bestellentwurf konnte nicht vorbereitet werden.'); }
  };
  const analyseStarten = async (angebotId: number, emailId: number | null) => {
    if (!emailId) { toast.error('Für die Analyse ist eine der Lieferantenkommunikation zugeordnete E-Mail nötig.'); return; }
    try { const job = await einkaufApi.post<{ id: number }>(`/api/einkauf/analysen?emailId=${emailId}&angebotId=${angebotId}`, {}); setKiJobId(job.id); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Analyse konnte nicht gestartet werden.'); }
  };
  const gespeichert = (saved: AnfrageDetail) => {
    if (neu) { navigate(`/einkaufsanfragen/${saved.kopf.id}`, { replace: true }); return; }
    setDetail(saved); setBearbeiten(false);
    void einkaufApi.get<Revision[]>(`/api/einkauf/anfragen/${saved.kopf.id}/revisionen`).then(setRevisionen).catch(error => toast.error(error.message));
  };
  return <PageLayout ribbonCategory="Einkauf" title={neu ? 'NEUE EINKAUFSANFRAGE' : detail?.kopf.paNummer ?? 'EINKAUFSANFRAGE'}
    subtitle={neu ? 'Bedarfe und Empfänger für eine Anfrage zusammenstellen.' : 'Anfragefassungen, Versand und Rückmeldungen prüfen.'}
    actions={<Link to="/einkaufsanfragen"><Button variant="outline" size="sm"><ArrowLeft className="mr-2 h-4 w-4" />Zur Übersicht</Button></Link>}>
    <div className="space-y-4"><EinkaufNavigation active="anfragen" />
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {neu ? <AnfrageEntwurfEditor onSaved={gespeichert} /> : !detail ? <p role="status">Anfrage wird geladen …</p> :
        <DetailLayout header={<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
          <div><p className="font-medium">Revision {detail.kopf.revisionsNummer}{detail.historisch ? ' · frühere Fassung (nur Lesen)' : ' · aktuelle Fassung'}</p>
            <p className="text-sm text-slate-600">Antwort bis {datum(detail.kopf.antwortfrist)} · Lieferung {datum(detail.kopf.liefertermin)}</p>
            {detail.historisch && <p className="text-sm text-slate-600">Schreibgeschützt. Frühere Antworten bleiben dieser Fassung zugeordnet.</p>}</div>
          <div className="flex flex-wrap items-center gap-2"><Select aria-label="Anfragefassung" value={String(detail.angezeigteRevisionId)}
            options={revisionen.map(r => ({ value: String(r.id), label: `Fassung ${r.nummer}` }))} disabled={wechselt || bearbeiten} onChange={v => void revisionWaehlen(v)} />
            {!detail.historisch && <Button size="sm" variant="outline" disabled={wechselt || bearbeiten} onClick={() => setBearbeiten(true)}>Neue Revision</Button>}</div>
        </div>}
        mainContent={<div className="space-y-4">
          {bearbeiten && !detail.historisch ? <AnfrageEntwurfEditor initial={detail} onCancel={() => setBearbeiten(false)} onSaved={gespeichert} /> :
            <AnfrageLieferanten key={detail.angezeigteRevisionId} anfrageId={detail.kopf.id} revisionId={detail.angezeigteRevisionId} lieferanten={detail.lieferanten} historisch={detail.historisch} />}
          <section className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="font-semibold">Positionen · Revision {detail.kopf.revisionsNummer}</h2>
            {detail.positionen.map(p => <div key={p.id} className="border-t border-slate-100 py-2"><p>{p.snapshot.bezeichnung}</p><p className="text-sm text-slate-600">{p.herkuenfte.map(h => h.menge?.toLocaleString('de-DE')).join(', ')} {p.snapshot.basis?.einheit?.toLowerCase()}</p></div>)}
          </section>
          {!detail.historisch && <section className="rounded-lg border border-slate-200 bg-white p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="font-semibold">Angebot erfassen oder korrigieren</h2><p className="text-sm text-slate-600">Manuelle Eingaben funktionieren auch dann, wenn keine automatische Analyse vorliegt.</p></div></div><div className="mt-3 flex flex-wrap gap-2">{detail.lieferanten.map(vendor => {
            const entry = angebote.find(item => item.angebot.beteiligungId === vendor.id);
            const latest = entry?.angebot.versionen.filter(v => v.anfrageRevisionId === detail.angezeigteRevisionId).sort((a, b) => a.nummer - b.nummer).at(-1);
            return <div key={vendor.id} className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => setAngebotEditor({ beteiligungId: vendor.id, angebotId: entry?.angebot.id, version: latest })}>{latest ? `Angebot von ${vendor.lieferantenname} bearbeiten` : `Angebot von ${vendor.lieferantenname} erfassen`}</Button>{entry && latest?.emailId != null && <Button variant="outline" size="sm" onClick={() => void analyseStarten(entry.angebot.id, latest.emailId)}>KI-Vorschläge prüfen · {vendor.lieferantenname}</Button>}</div>;
          })}</div>{angebotEditor && <div className="mt-4"><AngebotEditor key={`${angebotEditor.beteiligungId}-${angebotEditor.version?.id ?? 'new'}`} beteiligungId={angebotEditor.beteiligungId} anfrageRevisionId={detail.angezeigteRevisionId} angebotId={angebotEditor.angebotId} positionen={detail.positionen.map(p => ({ id: p.id, snapshot: { interneReferenz: p.snapshot.interneReferenz, bezeichnung: p.snapshot.bezeichnung, basis: p.snapshot.basis ? { menge: p.snapshot.basis.menge, einheit: p.snapshot.basis.einheit } : null } }))} initial={angebotEditor.version ? ({ anfrageRevisionId: angebotEditor.version.anfrageRevisionId, angebotsnummer: angebotEditor.version.angebotsnummer, datum: angebotEditor.version.datum, gueltigBis: angebotEditor.version.gueltigBis, waehrung: angebotEditor.version.waehrung, positionen: angebotEditor.version.positionen, kosten: angebotEditor.version.kosten, zahlungsbedingungen: angebotEditor.version.zahlungsbedingungen, skontoProzent: angebotEditor.version.skontoProzent, skontoTage: angebotEditor.version.skontoTage, emailId: angebotEditor.version.emailId, originalDateiId: angebotEditor.version.originalDateiId }) : undefined} onSaved={() => { setAngebotEditor(null); setAngebotReload(value => value + 1); }} /></div>}</section>}
          {kiJobId != null && <KiVorschlaege key={kiJobId} jobId={kiJobId} onUebernommen={() => { setKiJobId(null); setAngebotReload(value => value + 1); }} />}
          <Angebotsvergleich anfrageId={detail.kopf.id} revisionId={detail.angezeigteRevisionId} historisch={detail.historisch} reloadToken={angebotReload} onBestellung={bestellungVorbereiten} onBearbeiten={value => setAngebotEditor({ beteiligungId: value.beteiligungId, angebotId: value.angebotId, version: value.version })} onPreisUebernehmen={setPreisQuelle} onSaved={() => setAngebotReload(value => value + 1)} />
          {preisQuelle && <PreisUebernahmeDialog {...preisQuelle} onClose={() => setPreisQuelle(null)} onSaved={() => { setPreisQuelle(null); setAngebotReload(value => value + 1); }} />}
          <KommunikationsVerlauf typ="ANFRAGE" vorgangId={detail.kopf.id} revisionId={detail.angezeigteRevisionId} beteiligungen={detail.lieferanten} readOnly={detail.historisch} />
        </div>}
        sideContent={<section className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="font-semibold">Anfrage im Blick</h2><p className="mt-2 text-sm">{detail.lieferanten.length} Lieferanten · Fassung {detail.kopf.revisionsNummer}</p><p className="mt-2 text-sm text-slate-600">Eine Anfrage bestellt kein Material. Angebote werden anschließend geprüft und bewusst freigegeben.</p></section>} />}
    </div>
  </PageLayout>;
}
