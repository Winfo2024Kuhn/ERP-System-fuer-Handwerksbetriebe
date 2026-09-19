import { useEffect, useMemo } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, CalendarDays, ChevronRight, Clock, User } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'
import { useToast } from '../components/ui/toast'
import { EINTRAG_ARTEN, type KalenderTermin, type VerknuepfungArt } from '../features/kalender/typen'
import { formatDatumLang, formatZeitraum, heuteIso, istGueltigesIsoDatum } from '../features/kalender/kalenderDaten'
import { useKalenderDaten } from '../features/kalender/useKalenderDaten'
import { VERKNUEPFUNG_ICONS } from '../features/kalender/icons'
import { kalenderPfadMitSheet } from '../features/kalender/pfade'
import { LeerZustand } from '../features/kalender/TagesListe'

interface TerminDetailPageProps {
    token: string | null
}

/** Zustand, den die Kalenderseite beim Öffnen mitgibt – spart das Nachladen. */
interface TerminDetailState {
    termin?: KalenderTermin
    /** true, wenn es einen Verlaufseintrag der Kalenderseite gibt, zu dem `navigate(-1)` zurückführt. */
    zurueck?: boolean
}

const VERKNUEPFUNG_ZIELE: Record<VerknuepfungArt, { label: string; pfad: string }> = {
    projekt: { label: 'Projekt', pfad: '/projekte' },
    kunde: { label: 'Kunde', pfad: '/kunden' },
    lieferant: { label: 'Lieferant', pfad: '/lieferanten' },
    anfrage: { label: 'Anfrage', pfad: '/anfragen' },
}

/**
 * Unterseite für einen einzelnen Eintrag. Der Zurück-Pfeil führt in die
 * Kalenderansicht zurück, aus der man kam – inklusive geöffnetem Tages-Sheet.
 */
export default function TerminDetailPage({ token }: TerminDetailPageProps) {
    const navigate = useNavigate()
    const location = useLocation()
    const toast = useToast()
    const { datum: datumParam, key = '' } = useParams()
    const datum = istGueltigesIsoDatum(datumParam) ? datumParam : null
    const state = (location.state ?? {}) as TerminDetailState
    const bekannt = state.termin && state.termin.key === key ? state.termin : null

    // Ohne mitgegebenen Termin (Neuladen, geteilter Link) den Tag nachladen – nur bei gültigem Datum.
    const ladeDatum = datum ?? heuteIso()
    const bereich = useMemo(() => ({ von: ladeDatum, bis: ladeDatum }), [ladeDatum])
    const { termineProTag, laedt, fehler, neuLaden } = useKalenderDaten(bekannt || !datum ? null : token, bereich)
    const termin = bekannt ?? (datum ? termineProTag?.get(datum)?.find(t => t.key === key) : null) ?? null

    useEffect(() => {
        if (fehler) toast.error(fehler)
    }, [fehler, toast])

    const zurueck = () => {
        if (state.zurueck) navigate(-1)
        else navigate(datum ? kalenderPfadMitSheet(datum) : '/kalender', { replace: true })
    }

    return (
        <div className="flex h-full flex-col bg-slate-50">
            <header className="safe-area-top [--sa-base-top:2.25rem] sticky top-0 z-10 border-b border-slate-200 bg-white px-4 py-3">
                <div className="flex items-center gap-3">
                    <button
                        type="button"
                        onClick={zurueck}
                        aria-label="Zurück zum Kalender"
                        className="-ml-2 flex h-11 w-11 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <ArrowLeft aria-hidden="true" className="h-5 w-5" />
                    </button>
                    <div className="min-w-0 flex-1">
                        <h1 className="text-lg font-bold text-slate-900">Termin</h1>
                        {datum && <p className="text-xs text-slate-500">{formatDatumLang(datum)}</p>}
                    </div>
                </div>
            </header>

            <main className="flex-1 space-y-4 overflow-y-auto px-4 py-4 pb-10">
                {termin ? (
                    <TerminDetails termin={termin} onVerknuepfung={(art, id) => navigate(`${VERKNUEPFUNG_ZIELE[art].pfad}?id=${encodeURIComponent(String(id))}`)} />
                ) : laedt ? (
                    <DetailSkeleton />
                ) : fehler ? (
                    // Fehler ≠ „nicht gefunden“: sichtbar bleiben und einen neuen Versuch anbieten.
                    <div className="flex items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3">
                        <AlertTriangle aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600" />
                        <p className="min-w-0 flex-1 text-sm text-rose-900">Termin konnte nicht geladen werden.</p>
                        <button
                            type="button"
                            onClick={neuLaden}
                            className="shrink-0 rounded-lg border border-rose-300 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        >
                            Erneut versuchen
                        </button>
                    </div>
                ) : (
                    <LeerZustand text="Dieser Termin wurde nicht gefunden. Vielleicht wurde er inzwischen gelöscht." />
                )}
            </main>
        </div>
    )
}

function TerminDetails({ termin, onVerknuepfung }: { termin: KalenderTermin; onVerknuepfung: (art: VerknuepfungArt, id: number) => void }) {
    const art = EINTRAG_ARTEN[termin.art]
    return (
        <>
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                    <span aria-hidden="true" className="h-2 w-2 rounded-full" style={{ backgroundColor: termin.farbe }} />
                    {art.label}
                </span>
                <h2 className="mt-3 text-xl font-bold leading-snug text-slate-900">{termin.titel}</h2>
                {termin.untertitel !== art.label && termin.verknuepfungen.length === 0 && (
                    <p className="mt-1 text-sm text-slate-500">{termin.untertitel}</p>
                )}
                <dl className="mt-4 divide-y divide-slate-100">
                    <InfoZeile icon={CalendarDays} label="Datum" wert={formatDatumLang(termin.datum)} />
                    <InfoZeile icon={Clock} label="Uhrzeit" wert={formatZeitraum(termin)} />
                    {termin.erstellerName && <InfoZeile icon={User} label="Erstellt von" wert={termin.erstellerName} />}
                </dl>
            </section>

            {termin.beschreibung && (
                <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Beschreibung</h3>
                    <p className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-700">{termin.beschreibung}</p>
                </section>
            )}

            {termin.teilnehmer.length > 0 && (
                <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Teilnehmer</h3>
                    <ul className="mt-3 space-y-2">
                        {termin.teilnehmer.map(t => (
                            <li key={t.id} className="flex items-center gap-3">
                                <span aria-hidden="true" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-rose-100 text-xs font-semibold text-rose-700">
                                    {initialen(t.name)}
                                </span>
                                <span className="text-sm font-medium text-slate-900">{t.name}</span>
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            {termin.verknuepfungen.length > 0 && (
                <section className="rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
                    <h3 className="px-3 pt-3 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Verknüpft mit</h3>
                    <ul className="divide-y divide-slate-100">
                        {termin.verknuepfungen.map(v => {
                            const Icon = VERKNUEPFUNG_ICONS[v.art]
                            return (
                                <li key={`${v.art}-${v.id}`}>
                                    <button
                                        type="button"
                                        onClick={() => onVerknuepfung(v.art, v.id)}
                                        className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                    >
                                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100">
                                            <Icon aria-hidden="true" className="h-4 w-4 text-slate-600" />
                                        </span>
                                        <span className="min-w-0 flex-1">
                                            <span className="block text-xs text-slate-500">{VERKNUEPFUNG_ZIELE[v.art].label}</span>
                                            <span className="block break-words text-sm font-medium text-slate-900">{v.name}</span>
                                        </span>
                                        <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-300" />
                                    </button>
                                </li>
                            )
                        })}
                    </ul>
                </section>
            )}
        </>
    )
}

function InfoZeile({ icon: Icon, label, wert }: { icon: ComponentType<SVGProps<SVGSVGElement>>; label: string; wert: string }) {
    return (
        <div className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100">
                <Icon aria-hidden="true" className="h-4 w-4 text-slate-600" />
            </span>
            <div className="min-w-0">
                <dt className="text-xs text-slate-500">{label}</dt>
                <dd className="text-sm font-medium text-slate-900">{wert}</dd>
            </div>
        </div>
    )
}

function DetailSkeleton() {
    return (
        <div role="status" aria-label="Termin wird geladen" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="h-5 w-20 rounded-full bg-slate-100 motion-safe:animate-pulse" />
            <div className="mt-3 h-6 w-3/4 rounded bg-slate-200 motion-safe:animate-pulse" />
            <div className="mt-5 space-y-3">
                {[0, 1].map(i => (
                    <div key={i} className="flex items-center gap-3">
                        <div className="h-9 w-9 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                        <div className="flex-1 space-y-1.5">
                            <div className="h-3 w-16 rounded bg-slate-100 motion-safe:animate-pulse" />
                            <div className="h-3.5 w-1/2 rounded bg-slate-200 motion-safe:animate-pulse" />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    )
}

function initialen(name: string): string {
    return name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(teil => teil[0]?.toUpperCase() ?? '')
        .join('')
}
