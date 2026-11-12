import { befehlAktiv, befehlWert, linkBeiAuswahl } from './mailEditorDom';

/** Befehle, die die Leiste direkt an `execCommand` gibt. */
export type MailBefehl =
    | 'bold' | 'italic' | 'underline' | 'strikeThrough' | 'removeFormat'
    | 'insertUnorderedList' | 'insertOrderedList'
    | 'justifyLeft' | 'justifyCenter' | 'justifyRight' | 'indent' | 'outdent';

/** Welche Formate an der Auswahl gelten – für `aria-pressed` und Haken im Menü. */
export interface MailFormatStand {
    fett: boolean;
    kursiv: boolean;
    unterstrichen: boolean;
    durchgestrichen: boolean;
    aufzaehlung: boolean;
    nummerierung: boolean;
    ueberschrift: boolean;
    link: boolean;
}

export const KEIN_FORMAT: MailFormatStand = {
    fett: false, kursiv: false, unterstrichen: false, durchgestrichen: false,
    aufzaehlung: false, nummerierung: false, ueberschrift: false, link: false,
};

/**
 * Textfarben der Leiste. Das sind Inhaltsfarben der verschickten Mail, kein
 * Farbschema der Oberfläche – darum stehen hier auch Grün und Blau.
 */
export const MAIL_TEXTFARBEN = [
    { name: 'Schwarz (Standard)', wert: '#000000' },
    { name: 'Grau', wert: '#64748b' },
    { name: 'Rot', wert: '#dc2626' },
    { name: 'Firmenfarbe', wert: '#e11d48' },
    { name: 'Grün', wert: '#16a34a' },
    { name: 'Blau', wert: '#2563eb' },
] as const;

export const MARKER_GELB = '#fef08a';

function istUeberschrift(): boolean {
    return /^(<)?h3(>)?$/i.test(befehlWert('formatBlock').trim());
}

/** Liest die Formate an der aktuellen Auswahl aus dem Browser. */
export function formatStand(editor: HTMLElement | null, range: Range | null): MailFormatStand {
    if (!editor || !range) return KEIN_FORMAT;
    const imLink = !!linkBeiAuswahl(editor, range);
    return {
        fett: befehlAktiv('bold'),
        kursiv: befehlAktiv('italic'),
        // Der Browser meldet Links als unterstrichen – das ist aber nur die Link-Darstellung.
        unterstrichen: !imLink && befehlAktiv('underline'),
        durchgestrichen: befehlAktiv('strikeThrough'),
        aufzaehlung: befehlAktiv('insertUnorderedList'),
        nummerierung: befehlAktiv('insertOrderedList'),
        ueberschrift: istUeberschrift(),
        link: imLink,
    };
}

/** Gleicher Stand? Spart unnötiges Neuzeichnen bei jeder Cursorbewegung. */
export function gleicherStand(a: MailFormatStand, b: MailFormatStand): boolean {
    return (Object.keys(a) as (keyof MailFormatStand)[]).every(schluessel => a[schluessel] === b[schluessel]);
}
