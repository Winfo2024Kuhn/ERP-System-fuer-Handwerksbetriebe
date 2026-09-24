import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import '../../src/index.css';
import { PositionsEditor } from '../../src/features/einkauf/components/PositionsEditor';
import { toPositionPayload, type PositionDraft } from '../../src/features/einkauf/positionDrafts';
import { VersandStatus } from '../../src/features/einkauf/components/VersandStatus';
import { ToastProvider } from '../../src/components/ui/toast';

export function Harness() {
  const [draft, setDraft] = useState<PositionDraft>({
    art: 'ARTIKEL', artikelId: null, interneReferenz: '', zeichnungsnummer: '', zeichnungsrevision: '',
    bezeichnung: '', werkstoff: '', abmessung: '', menge: '0', einheit: 'STUECK', stueckzahl: '',
    einzelLaengeMm: '', kgJeMeter: '', faktorQuelle: '', schnittForm: '', winkelLinks: '', winkelRechts: '',
    bearbeitung: '', oberflaeche: '', dokumente: [], anlageVersionIds: [],
  });
  const [feedback, setFeedback] = useState('');
  const [payload, setPayload] = useState('');
  const uebernehmen = () => {
    const result = toPositionPayload(draft);
    if (!result.valid) { setFeedback(result.message); setPayload(''); return; }
    setFeedback('Materialangaben übernommen.');
    setPayload(JSON.stringify(result.value));
  };
  return <ToastProvider><main className="mx-auto max-w-6xl space-y-5 p-6">
    <header className="sticky top-0 z-20 space-y-3 border-b border-slate-200 bg-white pb-3"><p className="text-sm font-semibold uppercase tracking-wide text-rose-600">Einkauf</p><h1 className="text-3xl font-bold text-slate-900">POSITION ERFASSEN</h1><p className="mt-1 text-slate-500">Artikelstamm als Grundlage nutzen und technische Angaben prüfen.</p>    <button type="button" className="rounded-lg border border-rose-600 bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500" onClick={uebernehmen}>Materialangaben übernehmen</button></header>
    <PositionsEditor value={draft} onChange={setDraft} anlagen={[{ id: 41, dateiId: 4, bedarfId: 1, revision: 'B', dateiname: 'Traeger.pdf', mimeTyp: 'application/pdf', byteAnzahl: 100, sha256: 'dummy', freigegeben: true, versendet: false, hochgeladenAm: '2026-09-24T00:00:00Z' }]} />

    {feedback && <p role="status" className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">{feedback}</p>}
    {payload && <output aria-label="Übernommene Position" className="block max-w-full break-all rounded-lg bg-slate-50 p-3 text-xs">{payload}</output>}
    {(['ANGENOMMEN', 'UNKLAR', 'SICHER_FEHLGESCHLAGEN'] as const).map(status => <VersandStatus key={status} versand={{ id: 1, version: 0, typ: 'BESTELLUNG', vorgangId: 1, revisionId: 1, status, fehlerCode: null, erstelltAm: '2026-09-24T00:00:00Z', angenommenAm: null, archiviert: false, messageId: null }} onKlaeren={() => setFeedback('Versandnachweis öffnen.')} />)}
  </main></ToastProvider>;
}

createRoot(document.getElementById('root')!).render(<MemoryRouter><Harness /></MemoryRouter>);
