import { useId } from 'react'
import { Mail, Phone, Smartphone, Users, type LucideIcon } from 'lucide-react'
import { anrufbareNummer } from '../../lib/telefon'
import type { LieferantAnsprechpartnerKurz } from './ansprechpartner'

interface Props {
    ansprechpartner: LieferantAnsprechpartnerKurz[]
}

/**
 * Karte „Ansprechpartner“ in der Lieferanten-Ansicht der Handy-App: je Person
 * Name, Aufgabe und Knöpfe zum Anrufen bzw. Mailen (mindestens 44 px hoch).
 * Ohne Ansprechpartner erscheint die Karte nicht.
 */
export function AnsprechpartnerKarte({ ansprechpartner }: Props) {
    const titelId = useId()
    if (ansprechpartner.length === 0) return null
    return (
        <section aria-labelledby={titelId} className="bg-white rounded-2xl p-4 border border-slate-200">
            <div className="flex items-center gap-2 mb-3">
                <Users aria-hidden="true" className="w-4 h-4 text-rose-600" />
                <h2 id={titelId} className="font-medium text-slate-700">Ansprechpartner</h2>
            </div>
            <ul className="divide-y divide-slate-100">
                {ansprechpartner.map(ap => <Person key={ap.id} ap={ap} />)}
            </ul>
        </section>
    )
}

function Person({ ap }: { ap: LieferantAnsprechpartnerKurz }) {
    const nameId = useId()
    // Eine reine Ortsvorwahl ist keine wählbare Nummer.
    const telefon = anrufbareNummer(ap.telefon)
    const handy = anrufbareNummer(ap.mobiltelefon)
    return (
        <li aria-labelledby={nameId} className="py-3 first:pt-0 last:pb-0">
            <p id={nameId} className="font-semibold text-slate-900 break-words">{ap.name}</p>
            {ap.aufgabe && <p className="text-sm text-slate-500 break-words">{ap.aufgabe}</p>}
            <div className="mt-2 flex flex-col gap-2">
                {handy && <Kontakt href={`tel:${handy}`} symbol={Smartphone} text={handy} label={`${ap.name} anrufen: Handy ${handy}`} />}
                {telefon && <Kontakt href={`tel:${telefon}`} symbol={Phone} text={telefon} label={`${ap.name} anrufen: Telefon ${telefon}`} />}
                {ap.email && <Kontakt href={`mailto:${encodeURI(ap.email)}`} symbol={Mail} text={ap.email} label={`E-Mail an ${ap.name}: ${ap.email}`} />}
            </div>
        </li>
    )
}

function Kontakt({ href, symbol: Symbol, text, label }: { href: string; symbol: LucideIcon; text: string; label: string }) {
    return (
        <a
            href={href}
            aria-label={label}
            className="flex min-h-11 items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-slate-900 active:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            <Symbol aria-hidden="true" className="w-5 h-5 shrink-0 text-slate-500" />
            <span className="min-w-0 break-all font-medium">{text}</span>
        </a>
    )
}
