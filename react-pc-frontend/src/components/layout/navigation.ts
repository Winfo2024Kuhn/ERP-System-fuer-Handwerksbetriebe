import { BarChart3, Briefcase, Building2, Calendar, CalendarDays, Clock, Euro, FileCheck, FileJson, FileText, Globe, Home, Layers, List, Mail, MailPlus, Package, Phone, Plane, Receipt, Settings, Settings2, ShoppingCart, Stethoscope, Truck, User, Voicemail, Wallet } from 'lucide-react';
import type { ComponentType } from 'react';
import { BESTELLBEDARF_PFAD } from '../../lib/freigaben';

// Navigation structure with subgroups for better organization
export interface NavItem {
    name: string;
    href: string;
    icon: ComponentType<{ className?: string }>;
}

export interface NavSubgroup {
    label: string;
    items: NavItem[];
}

export interface NavCategory {
    category: string;
    subgroups: NavSubgroup[];
}

export const NAVIGATION: NavCategory[] = [
    {
        category: 'Vorlagen & Stammdaten',
        subgroups: [
            {
                label: 'Dokumente',
                items: [
                    { name: 'Textvorlagen', href: '/textbausteine', icon: FileText },
                    { name: 'Leistungen', href: '/leistungen', icon: List },
                    { name: 'Stundensätze', href: '/arbeitszeitarten', icon: Clock },
                ]
            },
            {
                label: 'Kontakte',
                items: [
                    { name: 'Kunden', href: '/kunden', icon: User },
                    { name: 'Mitarbeiter', href: '/mitarbeiter', icon: User },
                    { name: 'Lieferanten', href: '/lieferanten', icon: Truck },
                ]
            },
            {
                label: 'Katalog',
                items: [
                    { name: 'Artikel', href: '/artikel', icon: Package },
                    { name: 'Arbeitsgänge', href: '/arbeitsgaenge', icon: Clock },
                    { name: 'Kategorien', href: '/produktkategorien', icon: Layers },
                ]
            },
            {
                label: 'Administration',
                items: [
                    { name: 'Firma', href: '/firma', icon: Building2 },
                    { name: 'Formularwesen', href: '/formulare', icon: FileJson },
                    { name: 'Einstellungen', href: '/einstellungen', icon: Settings },
                ]
            }
        ]
    },
    {
        category: 'Projektmanagement',
        subgroups: [
            {
                label: 'Aufträge',
                items: [
                    { name: 'Projekte', href: '/projekte', icon: Briefcase },
                    { name: 'Anfragen', href: '/anfragen', icon: FileCheck },
                ]
            },
            {
                label: 'Dokumente',
                items: [
                    { name: 'Dokumente', href: '/dokumentuebersicht', icon: FileText },
                ]
            },
            {
                label: 'Planung',
                items: [
                    { name: 'Kalender', href: '/kalender', icon: Calendar },
                ]
            },
            {
                label: 'Einkauf',
                items: [
                    { name: 'Bestellungen', href: '/bestellungen', icon: ShoppingCart },
                    { name: 'Bedarf', href: BESTELLBEDARF_PFAD, icon: List },
                ]
            }
        ]
    },
    {
        category: 'Zeiterfassung',
        subgroups: [
            {
                label: 'Übersicht',
                items: [
                    { name: 'Kalender', href: '/zeitbuchungen', icon: Calendar },
                    { name: 'Monatsabschluss', href: '/monatsabschluss', icon: FileCheck },
                ]
            },
            {
                label: 'Berichte',
                items: [
                    { name: 'Auswertung', href: '/auswertung', icon: BarChart3 },
                    { name: 'Steuerberater', href: '/steuerberater', icon: Mail },
                ]
            },
            {
                label: 'Einstellungen',
                items: [
                    { name: 'Zeitkonten', href: '/zeitkonten', icon: Clock },
                    { name: 'Feiertage', href: '/feiertage', icon: CalendarDays },
                ]
            },
            {
                label: 'Abwesenheiten',
                items: [
                    { name: 'Urlaubsanträge', href: '/urlaubsantraege', icon: Plane },
                    { name: 'Lange Krankheit', href: '/langzeitkrankmeldungen', icon: Stethoscope },
                ]
            }
        ]
    },
    {
        category: 'Kommunikation',
        subgroups: [
            {
                label: 'E-Mail',
                items: [
                    { name: 'E-Mail Center', href: '/emails/inbox', icon: Mail },
                    { name: 'E-Mail Vorlagen', href: '/email-textvorlagen', icon: MailPlus },
                    { name: 'E-Mail-Einstellungen', href: '/email-einstellungen', icon: Settings2 },
                ]
            },
            {
                label: 'Telefon',
                items: [
                    { name: 'Anrufe', href: '/telefon/anrufe', icon: Phone },
                    { name: 'Anrufbeantworter', href: '/telefon/anrufbeantworter', icon: Voicemail },
                ]
            },
            {
                label: 'Website',
                items: [
                    { name: 'Neuigkeiten', href: '/website', icon: Globe },
                ]
            }
        ]
    },
    {
        category: 'Finanzen & Controlling',
        subgroups: [
            {
                label: 'Buchhaltung',
                items: [
                    { name: 'Offene Posten', href: '/offeneposten', icon: Euro },
                    { name: 'Rechnungen', href: '/rechnungsuebersicht', icon: FileText },
                    { name: 'Belege & Kasse', href: '/belege-kasse', icon: Receipt },
                    { name: 'Mietabrechnung', href: '/miete', icon: Home },
                ]
            },
            {
                label: 'Auswertung',
                items: [
                    { name: 'Erfolgsanalyse', href: '/analyse', icon: BarChart3 },
                    { name: 'Kostenstellen', href: '/kostenstellen', icon: Wallet },
                ]
            }
        ]
    }
];

/** Name der Menü-Gruppe, die nur mit dem Abteilungs-Recht „Zeiterfassung im Menü sehen“ erscheint. */
export const ZEITERFASSUNG_KATEGORIE = 'Zeiterfassung';

/** Alle Seiten dieser Gruppe – direkt aus dem Menü abgeleitet, damit Menü und Seitenschutz nie auseinanderlaufen. */
export const ZEITERFASSUNG_PFADE: ReadonlySet<string> = new Set(
    NAVIGATION
        .filter((kategorie) => kategorie.category === ZEITERFASSUNG_KATEGORIE)
        .flatMap((kategorie) => kategorie.subgroups.flatMap((untergruppe) => untergruppe.items.map((eintrag) => eintrag.href))),
);

/** Gehört dieser Pfad (oder eine Unterseite davon) zur Zeiterfassung? */
export function istZeiterfassungPfad(pfad: string): boolean {
    for (const seite of ZEITERFASSUNG_PFADE) {
        if (pfad === seite || pfad.startsWith(`${seite}/`)) return true;
    }
    return false;
}
