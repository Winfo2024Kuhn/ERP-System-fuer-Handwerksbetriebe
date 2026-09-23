import type { Mengenstand as MengenstandDto } from '../types';

const zeilen: { feld: keyof MengenstandDto; titel: string }[] = [
  { feld: 'bedarf', titel: 'Bedarf' }, { feld: 'lagergedeckt', titel: 'Lagergedeckt' },
  { feld: 'angefragt', titel: 'Angefragt' }, { feld: 'reserviert', titel: 'Reserviert' },
  { feld: 'bestellt', titel: 'Bestellt' }, { feld: 'geliefert', titel: 'Geliefert' },
  { feld: 'storniert', titel: 'Storniert' }, { feld: 'ungedeckt', titel: 'Noch offen' },
  { feld: 'disponierbar', titel: 'Davon bestellbar' },
];

function formatMenge(value: number | null): string {
  return value === null ? 'Nicht angegeben' : new Intl.NumberFormat('de-DE', { maximumFractionDigits: 6 }).format(value);
}

export function Mengenstand({ stand }: { stand: MengenstandDto }) {
  return <section aria-label="Mengenstand">
    <p className="mb-3 text-xs text-slate-500">„Bestellt“ enthält bereits gelieferte Mengen. Geliefert zeigt davon den Fortschritt.</p>
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
    {zeilen.map(({ feld, titel }) => <div key={feld} className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
      <dt className="text-xs font-medium text-slate-500">{titel}</dt>
      <dd className="mt-1 break-words text-base font-semibold text-slate-900">{formatMenge(stand[feld])}</dd>
    </div>)}
    </dl>
  </section>;
}
