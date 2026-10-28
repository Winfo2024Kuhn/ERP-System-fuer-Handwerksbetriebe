import type { ComponentType } from 'react';
import {
    AlertTriangle, Bell, Briefcase, Building2, CalendarCheck, CalendarClock, CheckCircle2, Clock, FileText,
    Globe, HardDrive, Inbox, Mail, Package, Phone, PhoneMissed, Plane, RefreshCw, Truck, Users, Voicemail, Wallet,
} from 'lucide-react';
import type { CategoryDto, NotificationSummary, RecentItemDto } from '../../components/layout/notification-helpers';
import { kategorieFuerEintrag } from '../../components/layout/notification-helpers';

/**
 * Themen der Seite „Benachrichtigungen“: welche Kategorien und Einträge
 * zusammengehören, in welcher Reihenfolge sie stehen und wie sie aussehen.
 * Reine Daten und Funktionen ohne JSX, damit sie ohne Seiten-Mount testbar sind.
 */

type IconKomponente = ComponentType<{ className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }>;

export interface BenachrichtigungsGruppe {
    /** Steht in der Adresse (`?thema=<id>`) – nicht umbenennen, sonst brechen Links. */
    id: string;
    label: string;
    icon: IconKomponente;
    accentText: string;
    accentBg: string;
    /** Kategorie-Typen (CategoryDto.type) dieser Gruppe. */
    types: string[];
    /** Eintrags-Typen (RecentItemDto.type) dieser Gruppe. */
    recentTypes: string[];
}

/** id der Gruppe „Sicherung & Updates“ – wird auf der Seite besonders hervorgehoben. */
export const GRUPPE_BETRIEB = 'betrieb';

/** id der Sammelgruppe für Meldungen, die (noch) keinem Thema zugeordnet sind. */
export const GRUPPE_SONSTIGES = 'sonstiges';

// Reihenfolge bestimmt die Anzeige: Was den ganzen Betrieb betrifft
// (Datensicherung, Update), steht ganz oben, danach Webseiten-Anfragen.
export const GRUPPEN: BenachrichtigungsGruppe[] = [
    {
        // Nur Admins bekommen diese Meldungen vom Server. Sie erscheinen nur,
        // wenn die nächtliche Sicherung oder das Update gescheitert ist.
        id: GRUPPE_BETRIEB,
        label: 'Sicherung & Updates',
        icon: HardDrive,
        accentText: 'text-red-700',
        accentBg: 'bg-red-50',
        types: ['SICHERUNG', 'UPDATE'],
        recentTypes: ['SICHERUNG', 'UPDATE'],
    },
    {
        id: 'webseite',
        label: 'Neu von der Webseite',
        icon: Globe,
        accentText: 'text-rose-700',
        accentBg: 'bg-rose-100',
        types: ['ANFRAGEN_WEBSEITE'],
        recentTypes: ['ANFRAGE_WEBSEITE'],
    },
    {
        // Wer angerufen hat, wartet meist auf einen Rückruf. Nur für Benutzer
        // mit dem Telefon-Recht befüllt.
        id: 'telefon',
        label: 'Telefon',
        icon: Phone,
        accentText: 'text-sky-700',
        accentBg: 'bg-sky-50',
        types: ['SPRACHNACHRICHTEN', 'VERPASSTE_ANRUFE'],
        recentTypes: ['SPRACHNACHRICHT', 'VERPASSTER_ANRUF'],
    },
    {
        // Hier steht geschätzte statt gestempelter Arbeitszeit in den
        // Zeitkonten – oder eine Handy-Buchung hat einen Büro-Eintrag gekürzt
        // (Überschneidung). Das muss auffallen, bevor der Monat abgerechnet wird.
        id: 'zeiterfassung',
        label: 'Zeiten prüfen',
        icon: Clock,
        accentText: 'text-amber-700',
        accentBg: 'bg-amber-100',
        types: ['ZEITEN_AUTO_BEENDET', 'ZEITEN_UEBERSCHNEIDUNG', 'MONATSABSCHLUSS'],
        recentTypes: ['ZEIT_AUTO_BEENDET', 'ZEIT_UEBERSCHNEIDUNG', 'MONATSABSCHLUSS'],
    },
    {
        id: 'posteingaenge',
        label: 'Posteingänge',
        icon: Inbox,
        accentText: 'text-blue-600',
        accentBg: 'bg-blue-50',
        types: ['EMAILS', 'EMAILS_PROJECTS', 'EMAILS_OFFERS', 'EMAILS_SUPPLIERS', 'EMAILS_SPAM', 'EMAILS_NEWSLETTER'],
        recentTypes: ['EMAIL'],
    },
    {
        id: 'geschaeft',
        label: 'Geschäft',
        icon: Briefcase,
        accentText: 'text-emerald-600',
        accentBg: 'bg-emerald-50',
        types: ['FREIGABEN_ANGENOMMEN', 'BAUTAGEBUCH'],
        recentTypes: ['FREIGABE_ANGENOMMEN', 'BAUTAGEBUCH'],
    },
    {
        id: 'finanzen',
        label: 'Finanzen',
        icon: Wallet,
        accentText: 'text-orange-600',
        accentBg: 'bg-orange-50',
        types: ['EINGANG_FAELLIG', 'AUSGANG_UEBERFAELLIG', 'RECHNUNGEN'],
        recentTypes: ['EINGANG_FAELLIG', 'AUSGANG_UEBERFAELLIG', 'RECHNUNG'],
    },
    {
        id: 'termine',
        label: 'Termine',
        icon: CalendarClock,
        accentText: 'text-rose-600',
        accentBg: 'bg-rose-50',
        types: ['TERMINE'],
        recentTypes: ['TERMIN'],
    },
    {
        id: 'personal',
        label: 'Personal',
        icon: Users,
        accentText: 'text-emerald-600',
        accentBg: 'bg-emerald-50',
        types: ['URLAUBSANTRAEGE'],
        recentTypes: ['URLAUBSANTRAG'],
    },
    {
        id: 'lieferanten',
        label: 'Lieferanten',
        icon: Building2,
        accentText: 'text-cyan-600',
        accentBg: 'bg-cyan-50',
        types: ['LIEFERSCHEINE', 'REKLAMATIONEN'],
        recentTypes: ['LIEFERSCHEIN', 'REKLAMATION'],
    },
];

/**
 * Sammelgruppe am Ende: Liefert der Server einen Typ, den die Seite noch nicht
 * kennt, steht er hier – sonst zählte die Glocke Meldungen, die nirgends zu
 * sehen sind.
 */
const SONSTIGES: Omit<BenachrichtigungsGruppe, 'types' | 'recentTypes'> = {
    id: GRUPPE_SONSTIGES,
    label: 'Weitere Meldungen',
    icon: Bell,
    accentText: 'text-slate-600',
    accentBg: 'bg-slate-100',
};

export const EINTRAG_FARBEN: Record<string, string> = {
    SICHERUNG: 'text-red-600',
    UPDATE: 'text-red-600',
    EMAIL: 'text-blue-500',
    URLAUBSANTRAG: 'text-emerald-500',
    BAUTAGEBUCH: 'text-amber-500',
    EINGANG_FAELLIG: 'text-orange-500',
    AUSGANG_UEBERFAELLIG: 'text-red-500',
    RECHNUNG: 'text-violet-500',
    TERMIN: 'text-rose-500',
    LIEFERSCHEIN: 'text-cyan-500',
    REKLAMATION: 'text-pink-500',
    FREIGABE_ANGENOMMEN: 'text-emerald-500',
    ANFRAGE_WEBSEITE: 'text-rose-600',
    ZEIT_AUTO_BEENDET: 'text-amber-600',
    ZEIT_UEBERSCHNEIDUNG: 'text-amber-600',
    MONATSABSCHLUSS: 'text-rose-600',
    SPRACHNACHRICHT: 'text-amber-600',
    VERPASSTER_ANRUF: 'text-red-600',
};

export const EINTRAG_ICONS: Record<string, IconKomponente> = {
    SICHERUNG: HardDrive,
    UPDATE: RefreshCw,
    EMAIL: Mail,
    URLAUBSANTRAG: Plane,
    BAUTAGEBUCH: FileText,
    EINGANG_FAELLIG: AlertTriangle,
    AUSGANG_UEBERFAELLIG: AlertTriangle,
    RECHNUNG: Truck,
    TERMIN: CalendarClock,
    LIEFERSCHEIN: Package,
    REKLAMATION: AlertTriangle,
    FREIGABE_ANGENOMMEN: CheckCircle2,
    ANFRAGE_WEBSEITE: Globe,
    ZEIT_AUTO_BEENDET: Clock,
    ZEIT_UEBERSCHNEIDUNG: CalendarClock,
    MONATSABSCHLUSS: CalendarCheck,
    SPRACHNACHRICHT: Voicemail,
    VERPASSTER_ANRUF: PhoneMissed,
};

/** Eine Kategorie, von der der Server mehr meldet, als er Einträge mitschickt. */
export interface Rest {
    kategorie: CategoryDto;
    /** So viele Einträge der Kategorie stehen in der Liste. */
    angezeigt: number;
    /** So viele fehlen in der Liste (count − angezeigt). */
    weitere: number;
}

export interface GefuellteGruppe {
    gruppe: BenachrichtigungsGruppe;
    kategorien: CategoryDto[];
    eintraege: RecentItemDto[];
    /** Summe der Zähler aller Kategorien – das, was offen ist, nicht nur das Mitgeschickte. */
    anzahl: number;
    /** Kategorien mit mehr offenen Meldungen als mitgeschickten Einträgen. */
    reste: Rest[];
}

function restAus(kategorien: CategoryDto[], eintraege: RecentItemDto[]): Rest[] {
    return kategorien
        .map(kategorie => {
            const angezeigt = eintraege.filter(e => kategorieFuerEintrag(e) === kategorie.type).length;
            return { kategorie, angezeigt, weitere: kategorie.count - angezeigt };
        })
        .filter(r => r.weitere > 0);
}

function fuelle(gruppe: BenachrichtigungsGruppe, kategorien: CategoryDto[], eintraege: RecentItemDto[]): GefuellteGruppe {
    // Ohne Kategorie-Zähler zählt die Gruppe ihre Einträge.
    const anzahl = kategorien.length > 0 ? kategorien.reduce((summe, k) => summe + k.count, 0) : eintraege.length;
    return { gruppe, kategorien, eintraege, anzahl, reste: restAus(kategorien, eintraege) };
}

/**
 * Sortiert die Antwort des Servers in die Themen. Themen ohne Daten fallen
 * weg; unbekannte Typen landen in „Weitere Meldungen“ ganz am Ende.
 */
export function gruppiere(summary: NotificationSummary): GefuellteGruppe[] {
    const bekannteKategorien = new Set(GRUPPEN.flatMap(g => g.types));
    const bekannteEintraege = new Set(GRUPPEN.flatMap(g => g.recentTypes));

    const ergebnis = GRUPPEN
        .map(gruppe => fuelle(
            gruppe,
            summary.categories.filter(c => gruppe.types.includes(c.type)),
            summary.recentItems.filter(i => gruppe.recentTypes.includes(i.type)),
        ))
        .filter(g => g.kategorien.length > 0 || g.eintraege.length > 0);

    const fremdeKategorien = summary.categories.filter(c => !bekannteKategorien.has(c.type));
    const fremdeEintraege = summary.recentItems.filter(i => !bekannteEintraege.has(i.type));
    if (fremdeKategorien.length > 0 || fremdeEintraege.length > 0) {
        ergebnis.push(fuelle(
            {
                ...SONSTIGES,
                types: fremdeKategorien.map(c => c.type),
                recentTypes: [...new Set(fremdeEintraege.map(i => i.type))],
            },
            fremdeKategorien,
            fremdeEintraege,
        ));
    }
    return ergebnis;
}

/** Bekanntes Thema aus der Adresse, sonst `null` (= alle Themen). */
export function themaAusAdresse(wert: string | null): string | null {
    if (!wert) return null;
    return wert === GRUPPE_SONSTIGES || GRUPPEN.some(g => g.id === wert) ? wert : null;
}

/** Anzeige-Infos eines Themas, auch wenn es gerade keine Daten hat. */
export function gruppeZuId(id: string): Pick<BenachrichtigungsGruppe, 'id' | 'label' | 'icon' | 'accentText' | 'accentBg'> | undefined {
    if (id === GRUPPE_SONSTIGES) return SONSTIGES;
    return GRUPPEN.find(g => g.id === id);
}
