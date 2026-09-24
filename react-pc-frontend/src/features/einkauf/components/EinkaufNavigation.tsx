import { Link } from 'react-router-dom';
import { ClipboardList, FileText, PackageCheck, Truck } from 'lucide-react';

export type EinkaufBereich = 'bedarf' | 'anfragen' | 'bestellungen' | 'lieferungen';
const schritte: { id: EinkaufBereich; text: string; href: string; icon: typeof ClipboardList }[] = [
  { id: 'bedarf', text: 'Bedarf', href: '/bestellungen/bedarf', icon: ClipboardList },
  { id: 'anfragen', text: 'Anfragen', href: '/einkauf/anfragen', icon: FileText },
  { id: 'bestellungen', text: 'Bestellungen', href: '/bestellungen', icon: PackageCheck },
  { id: 'lieferungen', text: 'Lieferungen und Unterlagen', href: '/einkauf/lieferungen', icon: Truck },
];

export function EinkaufNavigation({ active }: { active: EinkaufBereich }) {
  return <nav aria-label="Einkauf" className="rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
    <ul className="flex flex-wrap gap-1">
      {schritte.map(({ id, text, href, icon: Icon }) => <li key={id}>
        <Link to={href} aria-current={active === id ? 'page' : undefined}
          className={`inline-flex min-h-10 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-rose-500 ${active === id ? 'bg-rose-50 text-rose-800' : 'text-slate-700 hover:bg-slate-50'}`}>
          <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />{text}
        </Link>
      </li>)}
    </ul>
  </nav>;
}
