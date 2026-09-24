import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Check, FileSpreadsheet, FileText, Filter, Loader2, Package, Pencil, Plus, Ruler, ShoppingCart } from 'lucide-react';
import { Button } from '../components/ui/button';
import { DecimalInput } from '../components/ui/decimal-input';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { MaterialbestellungModal } from '../components/MaterialbestellungModal';
import { BedarfDialog } from '../features/einkauf/components/BedarfDialog';
import { HiCadImportDialog } from '../features/einkauf/components/HiCadImportDialog';
import { DirektbestellungDialog } from '../features/einkauf/components/DirektbestellungDialog';
import { EinkaufNavigation } from '../features/einkauf/components/EinkaufNavigation';
import { einkaufApi, EinkaufApiError } from '../features/einkauf/api';
import { fehlmenge, ladeAlleBedarfe, materialGewicht, pruefeVorhanden, vorhandenesMaximum } from '../features/einkauf/bedarfApi';
import { einheitenAnzeige } from '../features/einkauf/einheiten';
import { formatDecimalInput } from '../lib/numberInput';
import type { BedarfResponse } from '../features/einkauf/types';

interface ProjektStamm { id: number; bauvorhaben?: string; auftragsnummer?: string; kunde?: string; excKlasse?: string | null }
type Filter = 'alle' | 'zu_bestellen' | 'vorhanden' | 'teilweise';
const formatMenge = (wert: number) => wert.toLocaleString('de-DE', { maximumFractionDigits: 6 });

/** Bedarfsseite aus feature/en1090-echeck, angebunden an die gemeinsame Einkaufsabwicklung. */
export default function ProjektBedarfPage({ ohneProjekt = false }: { ohneProjekt?: boolean }) {
    const { projektId } = useParams<{ projektId: string }>();
    const projektIdNum = ohneProjekt ? null : Number(projektId);
    const toast = useToast();
    const navigate = useNavigate();
    const [projekt, setProjekt] = useState<ProjektStamm | null>(null);
    const [zeilen, setZeilen] = useState<BedarfResponse[]>([]);
    const [mengen, setMengen] = useState<Record<number, string>>({});
    const [auswahl, setAuswahl] = useState<number[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [fehler, setFehler] = useState('');
    const [konflikt, setKonflikt] = useState(false);
    const [filter, setFilter] = useState<Filter>('alle');
    const [materialOffen, setMaterialOffen] = useState(false);
    const [zeichnungOffen, setZeichnungOffen] = useState(false);
    const [editZeile, setEditZeile] = useState<BedarfResponse>();
    const [hicadOffen, setHicadOffen] = useState(false);
    const [direktOffen, setDirektOffen] = useState(false);
    const [bestellmengen, setBestellmengen] = useState<Array<{ bedarfId: number; menge: number }>>([]);
    const requestId = useRef(0);
    const sperre = useRef(false);

    const uebernehmeStand = useCallback((rows: BedarfResponse[]) => {
        setZeilen(rows);
        setMengen(Object.fromEntries(rows.map(row => [row.id, formatDecimalInput(row.mengen.lagergedeckt ?? 0)])));
        setAuswahl(rows.filter(row => (row.mengen.disponierbar ?? 0) > 0 && !row.nachpflegeErforderlich).map(row => row.id));
    }, []);
    const ladeZeilen = useCallback(async () => {
        const request = ++requestId.current;
        setLoading(true); setFehler('');
        try {
            if (projektIdNum !== null && (!Number.isSafeInteger(projektIdNum) || projektIdNum <= 0)) throw new Error('Das ausgewählte Projekt ist ungültig.');
            const [rows, projekte] = await Promise.all([
                ladeAlleBedarfe(projektIdNum),
                projektIdNum === null ? Promise.resolve([]) : einkaufApi.get<ProjektStamm[]>('/api/projekte/simple?size=500'),
            ]);
            if (request !== requestId.current) return;
            setProjekt(projekte.find(row => row.id === projektIdNum) ?? null);
            uebernehmeStand(rows); setKonflikt(false);
        } catch (error) {
            if (request !== requestId.current) return;
            const message = error instanceof Error ? error.message : 'Bedarfe konnten nicht geladen werden.';
            setFehler(message); toast.error(message);
        } finally { if (request === requestId.current) setLoading(false); }
    }, [projektIdNum, toast, uebernehmeStand]);
    useEffect(() => {
        const laufendeAnfragen = requestId;
        void ladeZeilen();
        return () => { laufendeAnfragen.current++; };
    }, [ladeZeilen]);

    const hatAenderungen = zeilen.some(z => mengen[z.id] !== formatDecimalInput(z.mengen.lagergedeckt ?? 0));
    const stand = useCallback((z: BedarfResponse) => {
        const vorhanden = pruefeVorhanden(z, mengen[z.id] ?? '');
        return vorhanden.valid && vorhanden.value !== null ? { vorhanden: vorhanden.value, bestellen: fehlmenge(z, vorhanden.value) } : null;
    }, [mengen]);
    const offeneZeilen = zeilen.filter(z => (stand(z)?.bestellen ?? 0) > 0 && !z.nachpflegeErforderlich);
    const gefilterteZeilen = useMemo(() => zeilen.filter(z => {
        const m = stand(z);
        if (filter === 'zu_bestellen') return (m?.bestellen ?? 0) > 0;
        if (filter === 'vorhanden') return m !== null && m.vorhanden > 0 && m.bestellen === 0;
        if (filter === 'teilweise') return m !== null && m.vorhanden > 0 && m.bestellen > 0;
        return true;
    }), [zeilen, filter, stand]);

    const speichern = async (): Promise<boolean> => {
        if (sperre.current || konflikt) return false;
        const positionen = [];
        for (const z of zeilen) {
            if (mengen[z.id] === formatDecimalInput(z.mengen.lagergedeckt ?? 0)) continue;
            const result = pruefeVorhanden(z, mengen[z.id] ?? '');
            if (!result.valid || result.value === null) { toast.error(result.valid ? 'Bitte die vorhandene Menge eingeben.' : result.message); return false; }
            positionen.push({ bedarfId: z.id, version: z.version, vorhanden: result.value });
        }
        if (!positionen.length) return true;
        sperre.current = true; setBusy(true);
        try {
            const aktuell = await einkaufApi.put<BedarfResponse[]>('/api/einkauf/bedarf/werkstattpruefung', { positionen });
            setZeilen(old => old.map(z => aktuell.find(row => row.id === z.id) ?? z));
            setMengen(old => ({ ...old, ...Object.fromEntries(aktuell.map(z => [z.id, formatDecimalInput(z.mengen.lagergedeckt ?? 0)])) }));
            setFehler(''); toast.success('Werkstattprüfung gespeichert.'); return true;
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Werkstattprüfung konnte nicht gespeichert werden.';
            setFehler(message); toast.error(message);
            if (error instanceof Error && 'status' in error && error.status === 409) setKonflikt(true);
            return false;
        } finally { sperre.current = false; setBusy(false); }
    };

    const ausgewaehlt = zeilen.filter(z => auswahl.includes(z.id) && (z.mengen.disponierbar ?? 0) > 0 && !z.nachpflegeErforderlich);
    const uebergeben = async (anfrage = false) => {
        if (hatAenderungen) { toast.error('Bitte zuerst die Werkstattprüfung speichern.'); return; }
        if (sperre.current) return;
        if (!ausgewaehlt.length) { toast.error('Bitte mindestens eine Position mit fehlendem Material auswählen.'); return; }
        const positionen = ausgewaehlt.map(z => ({ bedarfId: z.id, version: z.version, menge: z.mengen.disponierbar! }));
        if (!anfrage) { setBestellmengen(positionen); setDirektOffen(true); return; }
        sperre.current = true; setBusy(true);
        try {
            const response = await einkaufApi.post<{ kopf: { id: number } }>('/api/einkauf/anfragen', { positionen, empfaenger: [], antwortfrist: null, liefertermin: null, zustaendigId: null, idempotenzKey: crypto.randomUUID() });
            navigate(`/einkaufsanfragen/${response.kopf.id}`);
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Preisanfrage konnte nicht angelegt werden.';
            toast.error(message);
            if (error instanceof EinkaufApiError && error.status === 409) { setFehler(message); setKonflikt(true); }
        }
        finally { sperre.current = false; setBusy(false); }
    };
    const drucken = async () => {
        if (sperre.current) return;
        if (zeilen.length > 5000) { toast.error('Eine Bedarfsliste kann höchstens 5.000 Positionen enthalten. Bitte teilen Sie den Bedarf auf.'); return; }
        sperre.current = true; setBusy(true);
        try {
            const response = await fetch('/api/einkauf/bedarf/pdf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ bedarfIds: zeilen.map(z => z.id) }) });
            if (!response.ok) throw new Error('Die Bedarfsliste konnte nicht erstellt werden.');
            const url = URL.createObjectURL(await response.blob());
            const link = document.createElement('a'); link.href = url; link.download = `Bedarfsliste-${ohneProjekt ? 'Werkstatt' : projektIdNum}.pdf`;
            link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        } catch (error) { toast.error(error instanceof Error ? error.message : 'Bedarfsliste konnte nicht gedruckt werden.'); }
        finally { sperre.current = false; setBusy(false); }
    };
    const bearbeite = (z: BedarfResponse) => { setEditZeile(z); if (z.position.art === 'ZEICHNUNGSTEIL') setZeichnungOffen(true); else setMaterialOffen(true); };
    const titel = ohneProjekt ? 'Für Werkstatt / auf Vorrat' : projekt?.bauvorhaben ?? `Projekt #${projektId ?? ''}`;

    return <PageLayout ribbonCategory="Einkauf · Bedarf" title={titel}
        subtitle={ohneProjekt ? 'Material für den Betrieb ohne Projektbezug.' : [projekt?.kunde, projekt?.auftragsnummer].filter(Boolean).join(' · ') || 'Materialbedarf für dieses Projekt'}
        actions={<div className="flex flex-wrap items-center gap-2">
            <Link to="/bestellungen/bedarf" className="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-rose-600"><ArrowLeft className="h-4 w-4" />Zur Übersicht</Link>
            <Button size="sm" variant="outline" disabled={busy || loading || hatAenderungen} title={hatAenderungen ? 'Bitte zuerst die Werkstattprüfung speichern.' : 'Material zum Bedarf ergänzen'} onClick={() => { setEditZeile(undefined); setMaterialOffen(true); }}><Plus className="h-4 w-4" />Material hinzufügen</Button>
            <Button size="sm" variant="outline" disabled={busy || loading || !zeilen.length} title={!zeilen.length ? 'Noch keine Positionen zum Drucken.' : 'Bedarfsliste für die Werkstatt mit freien Eintragfeldern'} onClick={() => void drucken()}><FileText className="h-4 w-4" />Liste drucken</Button>
            <Button size="sm" disabled={busy || loading || konflikt || (!hatAenderungen && !ausgewaehlt.length)} title={konflikt ? 'Bitte zuerst den aktuellen Stand neu laden.' : !hatAenderungen && !ausgewaehlt.length ? 'Keine fehlenden Positionen ausgewählt.' : undefined} onClick={() => { if (hatAenderungen) void speichern(); else void uebergeben(); }}>
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : hatAenderungen ? <Check className="h-4 w-4" /> : <ShoppingCart className="h-4 w-4" />}
                {hatAenderungen ? 'Werkstattprüfung speichern' : `In Bestellung übernehmen (${ausgewaehlt.length})`}
            </Button>
        </div>}>
        <EinkaufNavigation active="bedarf" />
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <p className="text-sm text-slate-600">1. Bedarf aufschreiben <span className="px-2">→</span> 2. Liste in der Werkstatt prüfen <span className="px-2">→</span> 3. Fehlendes Material bestellen</p>
                <div className="flex gap-2"><Button size="sm" variant="outline" disabled={busy || hatAenderungen} onClick={() => setHicadOffen(true)} title={hatAenderungen ? 'Bitte zuerst die Werkstattprüfung speichern.' : 'Bedarf aus einer HiCAD-Datei übernehmen'}><FileSpreadsheet className="h-4 w-4" />HiCAD-Import</Button>
                {!ohneProjekt && <Button size="sm" variant="outline" disabled={busy || hatAenderungen} onClick={() => { setEditZeile(undefined); setZeichnungOffen(true); }}>Zeichnungsteil erfassen</Button>}</div>
            </div>
        </div>
        {fehler && <section className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800"><p>{fehler}</p>{konflikt && <><p className="mt-1">Der Bedarf wurde zwischenzeitlich geändert. Beim Neuladen werden Ihre noch ungespeicherten Mengeneingaben durch den aktuellen Stand ersetzt.</p><Button className="mt-2" variant="outline" onClick={() => void ladeZeilen()}>Aktuellen Stand neu laden</Button></>}</section>}
        {loading ? <div role="status" className="space-y-3 rounded-lg border border-slate-200 bg-white p-6"><span>Bedarfe werden geladen …</span>{[0, 1, 2].map(n => <div key={n} className="h-10 rounded bg-slate-100 motion-safe:animate-pulse" />)}</div>
        : fehler && !zeilen.length ? <Button variant="outline" onClick={() => void ladeZeilen()}>Erneut laden</Button>
        : !zeilen.length ? <EmptyState onHicad={() => setHicadOffen(true)} onManuell={() => { setEditZeile(undefined); setMaterialOffen(true); }} />
        : <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/60 px-4 py-3">
                <div className="flex flex-wrap items-center gap-2 text-sm"><Filter className="h-4 w-4 text-slate-500" /><span className="font-medium text-slate-600">Anzeigen:</span>
                    <FilterChip aktiv={filter === 'alle'} onClick={() => setFilter('alle')}>Alle ({zeilen.length})</FilterChip>
                    <FilterChip aktiv={filter === 'zu_bestellen'} onClick={() => setFilter('zu_bestellen')}>Zu bestellen ({offeneZeilen.length})</FilterChip>
                    <FilterChip aktiv={filter === 'teilweise'} onClick={() => setFilter('teilweise')}>Teilweise vorhanden</FilterChip>
                    <FilterChip aktiv={filter === 'vorhanden'} onClick={() => setFilter('vorhanden')}>Komplett vorhanden</FilterChip>
                </div><span className="text-xs text-slate-500">{hatAenderungen ? 'Noch nicht gespeichert' : 'Werkstattstand gespeichert'}</span>
            </div>
            <table className="w-full table-fixed text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 font-medium text-slate-600"><tr>
                <th className="w-[28%] px-4 py-3">Material</th><th className="w-[14%] px-3 py-3">Werkstoff / Fixmaß</th><th className="w-[12%] px-3 py-3 text-right">Benötigt</th><th className="w-[17%] px-3 py-3 text-center">Vorhanden</th><th className="w-[14%] px-3 py-3 text-right">Zu bestellen</th><th className="w-[15%] px-3 py-3 text-right">Bearbeiten</th>
            </tr></thead><tbody className="divide-y divide-slate-100">
            {gefilterteZeilen.length === 0 && <tr><td colSpan={6} className="px-4 py-10 text-center text-slate-500">Keine Positionen für diesen Filter.</td></tr>}
            {gefilterteZeilen.map(z => {
                const m = stand(z); const einheit = einheitenAnzeige(z.position.basis?.einheit); const name = z.position.bezeichnung ?? `Bedarf ${z.id}`;
                const gebunden = (z.mengen.bestellt ?? 0) + (z.mengen.reserviert ?? 0) + (z.mengen.lagergedeckt ?? 0) > 0;
                const gewicht = materialGewicht(z);
                return <tr key={z.id} className="group transition-colors hover:bg-rose-50/40">
                    <td className="px-4 py-3"><div className="flex min-w-0 items-start gap-3">
                        <input type="checkbox" className="mt-1 h-4 w-4 shrink-0 accent-rose-600" aria-label={`Bedarf ${name} auswählen`} checked={auswahl.includes(z.id)} disabled={busy || !m || m.bestellen <= 0 || z.nachpflegeErforderlich} title={z.nachpflegeErforderlich ? 'Bitte diesen Bedarf zuerst vervollständigen.' : !m || m.bestellen <= 0 ? 'Für diese Position fehlt kein Material.' : 'Fehlmenge in Bestellung übernehmen'} onChange={e => setAuswahl(old => e.target.checked ? [...old, z.id] : old.filter(id => id !== z.id))} />
                        <div className="min-w-0"><p className="break-words font-medium text-slate-900">{name}</p>{z.position.interneReferenz && <p className="mt-0.5 text-xs text-slate-500">{z.position.interneReferenz}</p>}
                        {z.position.bearbeitung && <p className="mt-1 break-words text-xs text-slate-600">{z.position.bearbeitung}</p>}
                        {gewicht !== null && <p className="mt-1 text-xs text-slate-500">{formatMenge(gewicht)} kg</p>}
                        {z.nachpflegeErforderlich && <p className="mt-1 text-xs text-rose-700">Angaben bitte vervollständigen.</p>}
                        {(z.mengen.reserviert ?? 0) > 0 && <p className="mt-1 text-xs text-slate-600">Im Bestellentwurf: {formatMenge(z.mengen.reserviert!)} {einheit}</p>}
                        {(z.mengen.bestellt ?? 0) > 0 && <p className="mt-1 text-xs text-slate-600">Bereits bestellt: {formatMenge(z.mengen.bestellt!)} {einheit}</p>}</div>
                    </div></td>
                    <td className="break-words px-3 py-3 text-slate-600">{z.position.werkstoff || '—'}{z.position.abmessung && <p className="text-xs">{z.position.abmessung}</p>}{z.position.basis?.einzelLaengeMm != null && <p className="mt-1 inline-flex items-center gap-1 text-xs"><Ruler className="h-3 w-3" />{formatMenge(z.position.basis.einzelLaengeMm)} mm</p>}{(z.position.winkelLinks || z.position.winkelRechts) && <p className="text-xs">{z.position.winkelLinks ?? '90°'} / {z.position.winkelRechts ?? '90°'}</p>}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{z.mengen.bedarf == null ? '—' : formatMenge(z.mengen.bedarf)} {einheit}</td>
                    <td className="px-3 py-3"><div className="flex items-center gap-1"><DecimalInput className="w-full min-w-0 text-center tabular-nums" aria-label={`Vorhanden ${name}`} value={mengen[z.id] ?? ''} required min={0} max={vorhandenesMaximum(z)} integer={z.position.basis?.einheit === 'STUECK'} disabled={busy || konflikt || z.nachpflegeErforderlich} onChange={value => setMengen(old => ({ ...old, [z.id]: value }))} /><Button size="sm" variant="ghost" className="shrink-0 px-2 text-xs" disabled={busy || konflikt || z.nachpflegeErforderlich} title={`Alle noch benötigten ${formatMenge(vorhandenesMaximum(z))} ${einheit} sind vorhanden`} onClick={() => setMengen(old => ({ ...old, [z.id]: formatDecimalInput(vorhandenesMaximum(z)) }))}>Alle</Button></div></td>
                    <td className="px-3 py-3 text-right font-semibold tabular-nums text-rose-700">{m ? `${formatMenge(m.bestellen)} ${einheit}` : 'Eingabe prüfen'}</td>
                    <td className="px-3 py-3 text-right"><Button size="sm" variant="ghost" aria-label={`Material ${name} bearbeiten`} disabled={busy || hatAenderungen || gebunden} title={hatAenderungen ? 'Bitte zuerst die Werkstattprüfung speichern.' : gebunden ? 'Material ist bereits vorhanden oder in einer Bestellung. Die Materialangaben bleiben erhalten.' : 'Material bearbeiten'} onClick={() => bearbeite(z)}><Pencil className="h-4 w-4" />Bearbeiten</Button></td>
                </tr>;
            })}</tbody></table>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-4 py-3"><p className="text-sm text-slate-600"><span className="font-semibold text-rose-700">{ausgewaehlt.length}</span> Positionen für eine Bestellung ausgewählt.</p><Button size="sm" variant="outline" disabled={busy || hatAenderungen || !ausgewaehlt.length} title={hatAenderungen ? 'Bitte zuerst die Werkstattprüfung speichern.' : !ausgewaehlt.length ? 'Bitte fehlende Positionen auswählen.' : 'Optional zuerst Preise bei Lieferanten anfragen'} onClick={() => void uebergeben(true)}>Preisanfrage vorbereiten</Button></div>
        </div>}
        {materialOffen && <MaterialbestellungModal isOpen onClose={() => { setMaterialOffen(false); setEditZeile(undefined); void ladeZeilen(); }} onSuccess={() => { setMaterialOffen(false); setEditZeile(undefined); void ladeZeilen(); }} initialProjekt={projekt ?? (projektIdNum ? { id: projektIdNum, bauvorhaben: titel } : null)} projektSperren ohneProjekt={ohneProjekt} ausgangsbedarf={editZeile} />}
        {zeichnungOffen && <BedarfDialog offen schließen={() => { setZeichnungOffen(false); setEditZeile(undefined); }} gespeichert={() => { setZeichnungOffen(false); setEditZeile(undefined); void ladeZeilen(); }} ausgangsbedarf={editZeile} />}
        {hicadOffen && <HiCadImportDialog schließen={() => setHicadOffen(false)} übernommen={() => { setHicadOffen(false); void ladeZeilen(); toast.success('HiCAD-Bedarf wurde übernommen.'); }} />}
        {direktOffen && <DirektbestellungDialog initialeTeilmengen={bestellmengen} onClose={() => setDirektOffen(false)} onCreated={id => { setDirektOffen(false); navigate(`/bestellungen/${id}`); }} />}
    </PageLayout>;
}

// Filter und Leerzustand aus der EN1090-Bedarfsseite; Farben an das gemeinsame Design-System angepasst.
function FilterChip({ aktiv, onClick, children }: { aktiv: boolean; onClick: () => void; children: React.ReactNode }) {
    return <button type="button" aria-pressed={aktiv} onClick={onClick} className={`rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${aktiv ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-100'}`}>{children}</button>;
}
function EmptyState({ onHicad, onManuell }: { onHicad: () => void; onManuell: () => void }) {
    return <div className="rounded-lg border border-slate-200 bg-white p-10 shadow-sm"><div className="mb-8 text-center"><Package className="mx-auto mb-3 h-12 w-12 text-slate-400" /><h3 className="text-lg font-semibold text-slate-800">Noch kein Material angelegt</h3><p className="mx-auto mt-1 max-w-md text-sm text-slate-500">Leg den Bedarf aus einer HiCAD-Sägeliste an oder gib das Material direkt ein.</p></div>
        <div className="mx-auto grid max-w-2xl grid-cols-2 gap-4"><button type="button" onClick={onHicad} className="group rounded-lg border-2 border-dashed border-slate-200 p-6 text-left transition-colors hover:border-rose-300 hover:bg-rose-50/30"><FileSpreadsheet className="mb-3 h-8 w-8 text-slate-400 group-hover:text-rose-600" /><p className="font-semibold text-slate-900">HiCAD-Sägeliste importieren</p><p className="mt-1 text-sm text-slate-500">Excel-Export aus HiCAD hochladen.</p></button>
        <button type="button" onClick={onManuell} className="group rounded-lg border-2 border-dashed border-slate-200 p-6 text-left transition-colors hover:border-rose-300 hover:bg-rose-50/30"><Plus className="mb-3 h-8 w-8 text-slate-400 group-hover:text-rose-600" /><p className="font-semibold text-slate-900">Material manuell hinzufügen</p><p className="mt-1 text-sm text-slate-500">Bezeichnung, Menge und bei Bedarf technische Angaben.</p></button></div></div>;
}
