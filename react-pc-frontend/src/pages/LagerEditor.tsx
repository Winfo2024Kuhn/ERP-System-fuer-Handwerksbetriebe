import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { ArrowDownToLine, ArrowRightLeft, ArrowUpFromLine, Package, Plus, RefreshCw, Warehouse } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useToast } from '../components/ui/toast';

type BewegungTyp = 'EINGANG' | 'AUSGANG' | 'UMLAGERUNG' | 'KORREKTUR';

interface Lagerort {
  id: number;
  code: string;
  name: string;
  regal?: string;
  fach?: string;
}

interface Lagerbestand {
  id: number;
  artikelId: number;
  produktname?: string;
  produktlinie?: string;
  produkttext?: string;
  externeArtikelnummer?: string;
  lagerort?: Lagerort;
  menge: number;
  mindestbestand: number;
  unterMindestbestand: boolean;
  aktualisiertAm?: string;
}

interface Lagerbewegung {
  id: number;
  typ: BewegungTyp;
  produktname?: string;
  vonLagerort?: Lagerort;
  nachLagerort?: Lagerort;
  menge: number;
  grund?: string;
  referenz?: string;
  verantwortlicher?: string;
  erstelltAm?: string;
}

interface ArtikelOption {
  id: number;
  produktname?: string;
  produktlinie?: string;
  externeArtikelnummer?: string;
}

const bewegungOptionen: Array<{ value: BewegungTyp; label: string; icon: LucideIcon }> = [
  { value: 'EINGANG', label: 'Eingang', icon: ArrowDownToLine },
  { value: 'AUSGANG', label: 'Ausgang', icon: ArrowUpFromLine },
  { value: 'UMLAGERUNG', label: 'Umlagerung', icon: ArrowRightLeft },
  { value: 'KORREKTUR', label: 'Korrektur', icon: RefreshCw },
];

const formatNumber = (value?: number) =>
  new Intl.NumberFormat('de-DE', { maximumFractionDigits: 4 }).format(Number(value ?? 0));

const formatDate = (value?: string) => value ? new Date(value).toLocaleString('de-DE') : '-';

export default function LagerEditor() {
  const toast = useToast();
  const [bestand, setBestand] = useState<Lagerbestand[]>([]);
  const [bewegungen, setBewegungen] = useState<Lagerbewegung[]>([]);
  const [lagerorte, setLagerorte] = useState<Lagerort[]>([]);
  const [artikel, setArtikel] = useState<ArtikelOption[]>([]);
  const [query, setQuery] = useState('');
  const [artikelQuery, setArtikelQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [ortCode, setOrtCode] = useState('');
  const [ortName, setOrtName] = useState('');
  const [ortRegal, setOrtRegal] = useState('');
  const [ortFach, setOrtFach] = useState('');
  const [form, setForm] = useState({
    typ: 'EINGANG' as BewegungTyp,
    artikelId: '',
    lagerortId: '',
    vonLagerortId: '',
    nachLagerortId: '',
    menge: '1',
    mindestbestand: '0',
    grund: '',
    referenz: '',
    verantwortlicher: '',
  });

  const loadBestand = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (query.trim()) params.set('q', query.trim());
      const [bestandRes, bewegungRes, orteRes] = await Promise.all([
        fetch(`/api/lager/bestand?${params.toString()}`),
        fetch('/api/lager/bewegungen'),
        fetch('/api/lager/orte'),
      ]);
      if (!bestandRes.ok || !bewegungRes.ok || !orteRes.ok) throw new Error();
      setBestand(await bestandRes.json());
      setBewegungen(await bewegungRes.json());
      const nextOrte: Lagerort[] = await orteRes.json();
      setLagerorte(nextOrte);
      setForm(prev => prev.lagerortId || nextOrte.length === 0 ? prev : { ...prev, lagerortId: String(nextOrte[0].id), nachLagerortId: String(nextOrte[0].id) });
    } catch {
      toast.error('Lagerdaten konnten nicht geladen werden.');
    } finally {
      setLoading(false);
    }
  }, [query, toast]);

  const loadArtikel = useCallback(async () => {
    const params = new URLSearchParams({ size: '25', nurMitLieferantenpreis: 'false' });
    if (artikelQuery.trim()) params.set('q', artikelQuery.trim());
    const res = await fetch(`/api/artikel?${params.toString()}`);
    if (res.ok) {
      const data = await res.json();
      setArtikel(Array.isArray(data?.artikel) ? data.artikel : []);
    }
  }, [artikelQuery]);

  useEffect(() => { loadBestand(); }, [loadBestand]);
  useEffect(() => { loadArtikel(); }, [loadArtikel]);

  const summary = useMemo(() => {
    const artikelCount = new Set(bestand.map(b => b.artikelId)).size;
    const unterMindestbestand = bestand.filter(b => b.unterMindestbestand).length;
    const gesamtMenge = bestand.reduce((sum, b) => sum + Number(b.menge || 0), 0);
    return { artikelCount, unterMindestbestand, gesamtMenge };
  }, [bestand]);

  const updateForm = (key: keyof typeof form, value: string) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };

  const submitBewegung = async () => {
    if (!form.artikelId || Number(form.menge.replace(',', '.')) <= 0) {
      toast.error('Bitte Artikel und Menge eingeben.');
      return;
    }
    const payload: Record<string, unknown> = {
      typ: form.typ,
      artikelId: Number(form.artikelId),
      menge: Number(form.menge.replace(',', '.')),
      mindestbestand: Number(form.mindestbestand.replace(',', '.')),
      grund: form.grund,
      referenz: form.referenz,
      verantwortlicher: form.verantwortlicher,
    };
    if (form.typ === 'UMLAGERUNG') {
      payload.vonLagerortId = Number(form.vonLagerortId);
      payload.nachLagerortId = Number(form.nachLagerortId);
    } else {
      payload.lagerortId = Number(form.lagerortId || form.nachLagerortId);
    }
    setSaving(true);
    try {
      const res = await fetch('/api/lager/bewegungen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text);
      }
      toast.success('Lagerbewegung gespeichert.');
      setForm(prev => ({ ...prev, menge: '1', grund: '', referenz: '' }));
      await loadBestand();
    } catch (error) {
      toast.error(error instanceof Error && error.message ? error.message : 'Lagerbewegung konnte nicht gespeichert werden.');
    } finally {
      setSaving(false);
    }
  };

  const createLagerort = async () => {
    if (!ortCode.trim()) {
      toast.error('Bitte Lagerort-Code eingeben.');
      return;
    }
    const res = await fetch('/api/lager/orte', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: ortCode, name: ortName || ortCode, regal: ortRegal, fach: ortFach }),
    });
    if (res.ok) {
      toast.success('Lagerort gespeichert.');
      setOrtCode('');
      setOrtName('');
      setOrtRegal('');
      setOrtFach('');
      await loadBestand();
    } else {
      toast.error('Lagerort konnte nicht gespeichert werden.');
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Lagerverwaltung</p>
          <h1 className="text-2xl font-bold text-slate-900">Bestand & Warenbewegungen</h1>
        </div>
        <div className="flex gap-2">
          <Input value={query} onChange={e => setQuery(e.target.value)} placeholder="Bestand suchen..." className="w-64" />
          <Button variant="outline" onClick={loadBestand} disabled={loading}>
            <RefreshCw className={loading ? 'w-4 h-4 animate-spin' : 'w-4 h-4'} /> Laden
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <Metric icon={Package} label="Artikel im Lager" value={summary.artikelCount} />
        <Metric icon={Warehouse} label="Gesamtmenge" value={formatNumber(summary.gesamtMenge)} />
        <Metric icon={ArrowDownToLine} label="Unter Mindestbestand" value={summary.unterMindestbestand} danger={summary.unterMindestbestand > 0} />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-5">
        <Card className="p-0 overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
            <h2 className="font-semibold text-slate-900">Aktueller Bestand</h2>
            <span className="text-xs text-slate-500">{bestand.length} Positionen</span>
          </div>
          <div className="overflow-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-4 py-2 text-left">Artikel</th>
                  <th className="px-4 py-2 text-left">Lagerort</th>
                  <th className="px-4 py-2 text-right">Menge</th>
                  <th className="px-4 py-2 text-right">Mindestbestand</th>
                  <th className="px-4 py-2 text-left">Aktualisiert</th>
                </tr>
              </thead>
              <tbody>
                {bestand.map(item => (
                  <tr key={item.id} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{item.produktname || 'Artikel'}</div>
                      <div className="text-xs text-slate-500">{item.externeArtikelnummer || item.produktlinie || '-'}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">{item.lagerort?.code || '-'}</td>
                    <td className={item.unterMindestbestand ? 'px-4 py-3 text-right font-semibold text-red-700' : 'px-4 py-3 text-right font-semibold text-slate-900'}>
                      {formatNumber(item.menge)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">{formatNumber(item.mindestbestand)}</td>
                    <td className="px-4 py-3 text-slate-500">{formatDate(item.aktualisiertAm)}</td>
                  </tr>
                ))}
                {!loading && bestand.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-8 text-center text-slate-500">Kein Lagerbestand vorhanden.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        <div className="space-y-5">
          <Card className="p-4 space-y-4">
            <h2 className="font-semibold text-slate-900">Bewegung buchen</h2>
            <div className="grid grid-cols-2 gap-2">
              {bewegungOptionen.map(option => {
                const Icon = option.icon;
                return (
                  <button key={option.value} type="button" onClick={() => updateForm('typ', option.value)}
                    className={form.typ === option.value ? 'flex items-center justify-center gap-2 rounded border border-rose-500 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700' : 'flex items-center justify-center gap-2 rounded border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50'}>
                    <Icon className="w-4 h-4" /> {option.label}
                  </button>
                );
              })}
            </div>
            <Field label="Artikel suchen">
              <Input value={artikelQuery} onChange={e => setArtikelQuery(e.target.value)} placeholder="Name oder Nummer..." />
            </Field>
            <Field label="Artikel">
              <select className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" value={form.artikelId} onChange={e => updateForm('artikelId', e.target.value)}>
                <option value="">Bitte wählen</option>
                {artikel.map(a => <option key={`${a.id}-${a.externeArtikelnummer || ''}`} value={a.id}>{a.produktname || 'Artikel'} {a.externeArtikelnummer ? `(${a.externeArtikelnummer})` : ''}</option>)}
              </select>
            </Field>
            {form.typ === 'UMLAGERUNG' ? (
              <div className="grid grid-cols-2 gap-3">
                <OrtSelect label="Von" value={form.vonLagerortId} onChange={v => updateForm('vonLagerortId', v)} lagerorte={lagerorte} />
                <OrtSelect label="Nach" value={form.nachLagerortId} onChange={v => updateForm('nachLagerortId', v)} lagerorte={lagerorte} />
              </div>
            ) : (
              <OrtSelect label="Lagerort" value={form.lagerortId} onChange={v => updateForm('lagerortId', v)} lagerorte={lagerorte} />
            )}
            <div className="grid grid-cols-2 gap-3">
              <Field label={form.typ === 'KORREKTUR' ? 'Neue Menge' : 'Menge'}>
                <Input type="number" step="0.0001" value={form.menge} onChange={e => updateForm('menge', e.target.value)} />
              </Field>
              <Field label="Mindestbestand">
                <Input type="number" step="0.0001" value={form.mindestbestand} onChange={e => updateForm('mindestbestand', e.target.value)} disabled={form.typ === 'AUSGANG' || form.typ === 'UMLAGERUNG'} />
              </Field>
            </div>
            <Field label="Grund">
              <Input value={form.grund} onChange={e => updateForm('grund', e.target.value)} placeholder="z.B. Lieferung, Projekt, Inventur" />
            </Field>
            <Button className="w-full" onClick={submitBewegung} disabled={saving}>
              {saving ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />} Speichern
            </Button>
          </Card>

          <Card className="p-4 space-y-3">
            <h2 className="font-semibold text-slate-900">Lagerort anlegen</h2>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Code"><Input value={ortCode} onChange={e => setOrtCode(e.target.value)} placeholder="HL-A1" /></Field>
              <Field label="Name"><Input value={ortName} onChange={e => setOrtName(e.target.value)} placeholder="Hauptlager A1" /></Field>
              <Field label="Regal"><Input value={ortRegal} onChange={e => setOrtRegal(e.target.value)} /></Field>
              <Field label="Fach"><Input value={ortFach} onChange={e => setOrtFach(e.target.value)} /></Field>
            </div>
            <Button variant="outline" className="w-full" onClick={createLagerort}>Lagerort speichern</Button>
          </Card>
        </div>
      </div>

      <Card className="p-0 overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">Letzte Warenbewegungen</h2>
        </div>
        <div className="overflow-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-2 text-left">Zeit</th>
                <th className="px-4 py-2 text-left">Typ</th>
                <th className="px-4 py-2 text-left">Artikel</th>
                <th className="px-4 py-2 text-left">Von</th>
                <th className="px-4 py-2 text-left">Nach</th>
                <th className="px-4 py-2 text-right">Menge</th>
                <th className="px-4 py-2 text-left">Grund</th>
              </tr>
            </thead>
            <tbody>
              {bewegungen.map(b => (
                <tr key={b.id} className="border-t border-slate-100">
                  <td className="px-4 py-2 text-slate-500">{formatDate(b.erstelltAm)}</td>
                  <td className="px-4 py-2 font-medium text-slate-900">{b.typ}</td>
                  <td className="px-4 py-2">{b.produktname || '-'}</td>
                  <td className="px-4 py-2">{b.vonLagerort?.code || '-'}</td>
                  <td className="px-4 py-2">{b.nachLagerort?.code || '-'}</td>
                  <td className="px-4 py-2 text-right">{formatNumber(b.menge)}</td>
                  <td className="px-4 py-2 text-slate-500">{b.grund || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="space-y-1"><Label>{label}</Label>{children}</div>;
}

function OrtSelect({ label, value, onChange, lagerorte }: { label: string; value: string; onChange: (value: string) => void; lagerorte: Lagerort[] }) {
  return (
    <Field label={label}>
      <select className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" value={value} onChange={e => onChange(e.target.value)}>
        <option value="">Bitte wählen</option>
        {lagerorte.map(ort => <option key={ort.id} value={ort.id}>{ort.code} - {ort.name}</option>)}
      </select>
    </Field>
  );
}

function Metric({ icon: Icon, label, value, danger = false }: { icon: LucideIcon; label: string; value: string | number; danger?: boolean }) {
  return (
    <Card className="p-4 flex items-center gap-3">
      <div className={danger ? 'w-10 h-10 rounded bg-red-100 text-red-700 flex items-center justify-center' : 'w-10 h-10 rounded bg-slate-100 text-slate-700 flex items-center justify-center'}>
        <Icon className="w-5 h-5" />
      </div>
      <div>
        <div className="text-xs uppercase tracking-wide text-slate-500 font-medium">{label}</div>
        <div className={danger ? 'text-xl font-bold text-red-700' : 'text-xl font-bold text-slate-900'}>{value}</div>
      </div>
    </Card>
  );
}
