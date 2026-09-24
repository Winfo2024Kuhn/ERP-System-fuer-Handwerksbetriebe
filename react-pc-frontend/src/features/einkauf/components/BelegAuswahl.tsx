import { useCallback, useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { useToast } from '../../../components/ui/toast';
import {
  ladeBestellBelege,
  ladeNeueBestellDatei,
  registriereBestellBeleg,
  type BestellBeleg,
  type BestellBelegDatei,
} from '../belegApi';

export interface BelegAuswahlProps {
  bestellungId: number;
  typ: string;
  value: BestellBelegDatei | null;
  onChange: (beleg: BestellBelegDatei | null) => void;
}

export function BelegAuswahl({ bestellungId, typ, value, onChange }: BelegAuswahlProps) {
  const toast = useToast();
  const [belege, setBelege] = useState<BestellBeleg[]>([]);
  const [laden, setLaden] = useState(true);
  const [fehler, setFehler] = useState('');
  const [wirdGespeichert, setWirdGespeichert] = useState(false);

  const ladeBelege = useCallback(async () => {
    setLaden(true);
    setFehler('');
    try {
      setBelege(await ladeBestellBelege(bestellungId));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Belege konnten nicht geladen werden.';
      setFehler(message);
      toast.error(message);
    } finally {
      setLaden(false);
    }
  }, [bestellungId, toast]);

  useEffect(() => { void ladeBelege(); }, [ladeBelege, typ]);

  const waehle = async (beleg: BestellBeleg) => {
    if (!beleg.verfuegbar || wirdGespeichert) return;
    setWirdGespeichert(true);
    try {
      onChange(await registriereBestellBeleg(bestellungId, beleg.lieferantDokumentId));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Der Beleg konnte nicht zugeordnet werden.';
      toast.error(message);
    } finally {
      setWirdGespeichert(false);
    }
  };

  const ladeHoch = async (file?: File) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.pdf') || file.type !== 'application/pdf') {
      toast.error('Bitte wählen Sie eine PDF-Datei aus.');
      return;
    }
    if (file.size === 0 || file.size > 10 * 1024 * 1024) {
      toast.error('Die PDF-Datei darf höchstens 10 MiB groß sein.');
      return;
    }
    setWirdGespeichert(true);
    try {
      const uploaded = await ladeNeueBestellDatei(bestellungId, typ, file);
      onChange(uploaded);
      await ladeBelege();
      toast.success('PDF-Beleg wurde gespeichert.');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Die PDF-Datei konnte nicht gespeichert werden.';
      toast.error(message);
    } finally {
      setWirdGespeichert(false);
    }
  };

  const passendeBelege = belege.filter((beleg) => beleg.typ === typ);
  return <section aria-label="Beleg auswählen" className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
    <div>
      <h3 className="text-sm font-semibold text-slate-800">Vorhandenen PDF-Beleg auswählen</h3>
      <p className="text-xs text-slate-600">Fehlende historische Dateien bleiben sichtbar und müssen neu hochgeladen werden.</p>
    </div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    {laden ? <p role="status" className="text-sm text-slate-600">Belege werden geladen …</p>
      : passendeBelege.length === 0 ? <p className="text-sm text-slate-600">Für diese Belegart ist noch kein PDF hinterlegt.</p>
        : <ul className="space-y-2">{passendeBelege.map((beleg) => <li key={beleg.lieferantDokumentId} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-white p-2 text-sm">
          <span>{beleg.dateiname}{!beleg.verfuegbar && <span className="ml-1 text-rose-700">· Datei fehlt</span>}</span>
          <Button type="button" size="sm" variant="outline" disabled={!beleg.verfuegbar || wirdGespeichert || value?.lieferantDokumentId === beleg.lieferantDokumentId}
            title={!beleg.verfuegbar ? 'Diese historische PDF-Datei fehlt. Laden Sie sie erneut hoch.' : undefined}
            aria-label={`${beleg.dateiname} zuordnen`}
            onClick={() => void waehle(beleg)}>
            {value?.lieferantDokumentId === beleg.lieferantDokumentId ? 'Ausgewählt' : 'Zuordnen'}
          </Button>
        </li>)}</ul>}
    {value && <p className="text-sm text-emerald-800">Ausgewählt: {value.dateiname}</p>}
    <label className="inline-flex cursor-pointer items-center">
      <span className="sr-only">PDF hochladen</span>
      <input aria-label="PDF hochladen" type="file" accept="application/pdf,.pdf" disabled={wirdGespeichert}
        onChange={(event) => { void ladeHoch(event.currentTarget.files?.[0]); event.currentTarget.value = ''; }}
        className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border file:border-rose-300 file:bg-white file:px-3 file:py-2 file:text-sm file:font-medium file:text-rose-700 hover:file:bg-rose-50" />
    </label>
  </section>;
}
