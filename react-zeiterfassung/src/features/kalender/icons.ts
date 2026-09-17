import { Briefcase, CalendarDays, Clock, FileText, Flag, GraduationCap, Plane, Stethoscope, Truck, User } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'
import type { EintragArt, VerknuepfungArt } from './typen'

export type KalenderIcon = ComponentType<SVGProps<SVGSVGElement>>

/** Icon je Eintragsart – dasselbe Vokabular wie auf der Abwesenheiten-Seite. */
export const ART_ICONS: Record<EintragArt, KalenderIcon> = {
    URLAUB: Plane,
    KRANKHEIT: Stethoscope,
    FORTBILDUNG: GraduationCap,
    ZEITAUSGLEICH: Clock,
    FEIERTAG: Flag,
    TERMIN: CalendarDays,
}

/** Icon je Verknüpfungsart (Projekt, Kunde, Lieferant, Anfrage). */
export const VERKNUEPFUNG_ICONS: Record<VerknuepfungArt, KalenderIcon> = {
    projekt: Briefcase,
    kunde: User,
    lieferant: Truck,
    anfrage: FileText,
}
