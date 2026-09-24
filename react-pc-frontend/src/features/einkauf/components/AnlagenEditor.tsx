import type { EinkaufAnlage } from '../types';

export function AnlagenEditor({ anlagen, ausgewählt, geändert, zeichnungsteil = false }: {
  anlagen: readonly EinkaufAnlage[]; ausgewählt: readonly number[]; geändert: (ids: number[]) => void; zeichnungsteil?: boolean;
}) {
  const freigegebeneZeichnung = anlagen.some(anlage => anlage.freigegeben && ausgewählt.includes(anlage.id));
  return <fieldset className="space-y-2 rounded-lg border border-slate-200 p-3">
    <legend className="px-1 text-sm font-semibold text-slate-800">Zeichnungen und Anlagen</legend>
    {anlagen.length === 0 ? <p className="text-sm text-slate-600">Für diesen Bedarf sind noch keine Anlagen vorhanden.</p> : <div className="space-y-2">{anlagen.map(anlage => <label key={anlage.id} className="flex min-w-0 items-start gap-2 rounded-md bg-slate-50 p-2 text-sm">
      <input type="checkbox" aria-label={`${anlage.dateiname} auswählen`} checked={ausgewählt.includes(anlage.id)} onChange={ereignis => geändert(ereignis.target.checked ? [...ausgewählt, anlage.id] : ausgewählt.filter(id => id !== anlage.id))} />
      <span className="min-w-0 break-words">{anlage.dateiname} · Revision {anlage.revision || 'ohne Angabe'} · {anlage.freigegeben ? 'Freigegeben' : 'Nicht freigegeben'}</span>
    </label>)}</div>}
    {zeichnungsteil && !freigegebeneZeichnung && <p role="alert" className="text-sm text-rose-700">Für ein Zeichnungsteil wird eine freigegebene Zeichnungsanlage benötigt.</p>}
  </fieldset>;
}
