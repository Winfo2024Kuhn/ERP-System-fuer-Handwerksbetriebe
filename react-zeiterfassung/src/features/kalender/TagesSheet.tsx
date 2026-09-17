import { useState } from 'react'
import { X } from 'lucide-react'
import { Drawer, DrawerClose, DrawerContent, DrawerDescription, DrawerTitle } from '../../components/ui/drawer'
import type { KalenderTermin } from './typen'
import { TagesListe, TagesListeSkeleton } from './TagesListe'
import { anzahlText, formatDatumOhneJahr, relativerTag } from './kalenderDaten'

interface TagesSheetProps {
    /** Der gezeigte Tag; `null` schließt das Sheet. */
    datum: string | null
    /** Termine des Tages; `null`, solange sie laden. */
    termine: KalenderTermin[] | null
    heute: string
    onSchliessen: () => void
    onTermin: (termin: KalenderTermin) => void
}

/**
 * Bottom-Sheet mit der Terminliste eines Tages (Monatsansicht → Tag antippen).
 * Der zuletzt gezeigte Tag bleibt während der Zu-Animation stehen, damit der
 * Inhalt nicht vor dem Sheet verschwindet.
 */
export function TagesSheet({ datum, termine, heute, onSchliessen, onTermin }: TagesSheetProps) {
    const [angezeigt, setAngezeigt] = useState(datum)
    if (datum && datum !== angezeigt) setAngezeigt(datum)
    const tag = datum ?? angezeigt
    const relativ = tag ? relativerTag(tag, heute) : null

    return (
        <Drawer open={datum !== null} onOpenChange={offen => { if (!offen) onSchliessen() }}>
            <DrawerContent>
                {tag && (
                    <>
                        <div className="flex items-start justify-between gap-3 px-5 pt-4 pb-3">
                            <div className="min-w-0">
                                {relativ && <p className="text-xs font-semibold uppercase tracking-wide text-rose-600">{relativ}</p>}
                                <DrawerTitle>{formatDatumOhneJahr(tag)}</DrawerTitle>
                                <DrawerDescription>
                                    {termine ? anzahlText(termine.length, 'Eintrag', 'Einträge') : 'Wird geladen …'}
                                </DrawerDescription>
                            </div>
                            <DrawerClose
                                aria-label="Schließen"
                                className="-mr-2 -mt-1 flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            >
                                <X aria-hidden="true" className="h-5 w-5" />
                            </DrawerClose>
                        </div>
                        <div className="pb-6 overflow-y-auto px-5">
                            {termine ? <TagesListe termine={termine} onTermin={onTermin} /> : <TagesListeSkeleton />}
                        </div>
                    </>
                )}
            </DrawerContent>
        </Drawer>
    )
}
