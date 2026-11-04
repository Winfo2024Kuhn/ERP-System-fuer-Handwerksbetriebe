import { Briefcase, Calculator, FileEdit, FileText, Inbox, MailQuestionMark, Newspaper, Package, Send, ShieldAlert,
    Star, Trash2, AlertCircle, type LucideIcon } from 'lucide-react';
import type { EmailItem, EmailOrdnerWert, FolderType } from './emailCenterModel';

/** Ein Ordner des E-Mail-Centers – eine Quelle für Seitenleiste, Verschieben und Ordner-Schild. */
export interface EmailOrdner {
    id: FolderType;
    /** Name in der Seitenleiste. */
    label: string;
    icon: LucideIcon;
    /** Mails lassen sich hierher ziehen bzw. verschieben. */
    droppable?: boolean;
    /** Name auf dem Schild an einer einzelnen Mail (Einzahl), wenn er vom Ordnernamen abweicht. */
    schild?: string;
    /** Virtuelle Ordner (Markiert, Nicht zugeordnet, Entwürfe) liefert der Server nie als Ablageort. */
    ohneSchild?: boolean;
    /** Papierkorb und Spam: Schild dezent warnend. */
    warnend?: boolean;
}

export const POSTFACH_ORDNER: EmailOrdner[] = [
    { id: 'inbox', label: 'Posteingang', icon: Inbox, droppable: true },
    { id: 'sent', label: 'Gesendet', icon: Send },
    { id: 'drafts', label: 'Entwürfe', icon: FileEdit, ohneSchild: true },
    { id: 'starred', label: 'Markiert', icon: Star, ohneSchild: true },
    { id: 'newsletter', label: 'Newsletter', icon: Newspaper, droppable: true },
    { id: 'spam', label: 'Spam', icon: ShieldAlert, droppable: true, warnend: true },
    { id: 'trash', label: 'Papierkorb', icon: Trash2, droppable: true, warnend: true },
];

export const ZUGEORDNET_ORDNER: EmailOrdner[] = [
    { id: 'unassigned', label: 'Nicht zugeordnet', icon: AlertCircle, ohneSchild: true },
    { id: 'projects', label: 'Projekte', icon: Briefcase, schild: 'Projekt' },
    { id: 'offers', label: 'Anfragen', icon: FileText, schild: 'Anfrage' },
    { id: 'suppliers', label: 'Lieferanten', icon: Package, schild: 'Lieferant' },
    { id: 'tax-advisors', label: 'Steuerberater', icon: Calculator },
];

/** Ziele für „Verschieben nach“ und Ziehen auf die Seitenleiste. */
export const VERSCHIEBE_ZIELE = POSTFACH_ORDNER.filter(ordner => ordner.droppable) as
    (EmailOrdner & { id: 'inbox' | 'newsletter' | 'spam' | 'trash' })[];
export type VerschiebeZiel = typeof VERSCHIEBE_ZIELE[number]['id'];

export interface OrdnerSchild { label: string; icon: LucideIcon; warnend: boolean }

/** Ablageorte ohne eigenen Ordner in der Seitenleiste (der Server liefert sie trotzdem). */
const WEITERE_SCHILDER: Record<string, OrdnerSchild> = {
    inquiries: { label: 'Mögliche Anfrage', icon: MailQuestionMark, warnend: false },
};

/**
 * Schild für den Ablageort einer Mail (`folder` aus der API), z. B. „Papierkorb“ an einem Suchtreffer.
 * Unbekannte und virtuelle Ordner bekommen kein Schild.
 */
export function ordnerSchildFuer(folder: string | null | undefined): OrdnerSchild | null {
    if (!folder) return null;
    const ordner = [...POSTFACH_ORDNER, ...ZUGEORDNET_ORDNER].find(eintrag => eintrag.id === folder);
    if (ordner) {
        return ordner.ohneSchild ? null : { label: ordner.schild ?? ordner.label, icon: ordner.icon, warnend: !!ordner.warnend };
    }
    return Object.hasOwn(WEITERE_SCHILDER, folder) ? WEITERE_SCHILDER[folder] : null;
}

/** Der Wert, wenn er ein bekannter Ablageort ist – sonst `null` (nie geraten). */
export function bekannterOrdner(folder: string | null | undefined): EmailOrdnerWert | null {
    if (!folder) return null;
    if ([...POSTFACH_ORDNER, ...ZUGEORDNET_ORDNER].some(ordner => ordner.id === folder)) return folder as FolderType;
    return Object.hasOwn(WEITERE_SCHILDER, folder) ? folder as EmailOrdnerWert : null;
}

/**
 * Wo eine Mail liegt. Bei der Suche in allen Ordnern zählt allein ihr eigener Ordner – ein fehlender
 * oder unbekannter wird nie auf den geöffneten Ordner abgebildet (sonst löschte man im Papierkorb
 * eine Posteingangs-Mail endgültig). Sonst liegt jede Zeile im geöffneten Ordner.
 */
export function ablageOrdner(email: Pick<EmailItem, 'folder'>, sucheInAllenOrdnern: boolean, aktiverOrdner: FolderType): EmailOrdnerWert | null {
    return sucheInAllenOrdnern ? bekannterOrdner(email.folder) : aktiverOrdner;
}
