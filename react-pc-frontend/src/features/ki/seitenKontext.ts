/**
 * Liest aus der gerade angezeigten Seite, was der KI-Hilfe beim Antworten
 * hilft: Titel, Überschriften, Kennzahlen, Fehlermeldungen, gesperrte Knöpfe,
 * Dokumentkarten und Seitenleisten-Angaben. Nur Text, kein HTML.
 */

export interface SeitenKontext {
    route: string;
    pageTitle: string;
    visibleContent?: string;
    errorMessages?: string;
    latitude?: number;
    longitude?: number;
}

export interface KontextOptionen {
    route: string;
    /** Eigene Meldungen der KI-Hilfe (Chatfenster, ihre Toasts) zählen nicht als Seitenfehler. */
    istEigeneMeldung: (el: Element) => boolean;
    standort: { latitude: number; longitude: number } | null;
}

const text = (el: Element | null | undefined) => el?.textContent?.trim();

/**
 * Beschriftung → Wert, z. B. „Kunde: Max Mustermann“; leer bei fehlendem oder „–“-Wert.
 * `ohneSelbst`: die Beschriftung selbst gilt nicht als Wert (Seitenleiste).
 */
function paar(label: Element, wertSelektor: string, ohneSelbst: boolean): string | null {
    const wert = label.parentElement?.querySelector(wertSelektor);
    if (!wert || (ohneSelbst && wert === label)) return null;
    const l = text(label)?.replace(/:$/, '');
    const v = text(wert);
    return l && v && v !== '–' ? `${l}: ${v}` : null;
}

function seitenFehler(istEigeneMeldung: (el: Element) => boolean): string {
    return Array.from(document.querySelectorAll('[role="alert"], .bg-red-50, .bg-yellow-50'))
        .filter((el) => !istEigeneMeldung(el))
        .map((el) => text(el))
        .filter(Boolean)
        .slice(0, 5)
        .join('; ');
}

function kennzahlen(main: Element | null): string[] {
    const werte: string[] = [];
    main?.querySelectorAll('.text-\\[11px\\].text-slate-400.uppercase').forEach((label) => {
        const l = text(label);
        const v = text(label.parentElement?.querySelector('.font-semibold'));
        if (l && v) werte.push(`${l}: ${v}`);
    });
    return werte;
}

function gesperrteKnoepfe(main: Element | null): string[] {
    return Array.from(main?.querySelectorAll('button[disabled]') ?? [])
        .map((btn) => {
            const grund = btn.getAttribute('title') || '';
            return grund ? `"${text(btn)}" (${grund})` : `"${text(btn)}" (deaktiviert)`;
        })
        .slice(0, 5);
}

/** Geschäftsdokument-Karten: Typ- und Status-Abzeichen, Nummer, Betreff, Betrag, Herkunft. */
function dokumentKarten(main: Element | null): string[] {
    const karten: string[] = [];
    main?.querySelectorAll('.bg-white.rounded-xl.border.p-4').forEach((card) => {
        const badgeRow = card.querySelector('.flex.items-center.gap-2.mb-1');
        if (!badgeRow) return;
        const badges = Array.from(badgeRow.querySelectorAll('.rounded-full')).map((b) => text(b)).filter(Boolean);
        const nummer = text(card.querySelector('.font-semibold.text-slate-900'));
        if (!nummer && !badges.length) return;
        let zeile = badges.join(' | ');
        if (nummer) zeile += ` Nr. ${nummer}`;
        const betreff = text(card.querySelector('.text-sm.text-slate-600'));
        const betrag = text(card.querySelector('.text-lg.font-bold'));
        const herkunft = text(card.querySelector('.text-\\[10px\\].text-slate-400'));
        if (betreff) zeile += ` "${betreff}"`;
        if (betrag) zeile += ` ${betrag}`;
        if (herkunft) zeile += ` (${herkunft})`;
        karten.push(zeile);
    });
    return karten;
}

function paare(labels: NodeListOf<Element> | undefined, wertSelektor: string, ohneSelbst: boolean): string[] {
    const ergebnis: string[] = [];
    labels?.forEach((label) => {
        const p = paar(label, wertSelektor, ohneSelbst);
        if (p) ergebnis.push(p);
    });
    return ergebnis;
}

export function sammleSeitenKontext({ route, istEigeneMeldung, standort }: KontextOptionen): SeitenKontext {
    const main = document.querySelector('main');
    const h1 = main?.querySelector('h1');
    const dokumente = dokumentKarten(main);
    const seitenleiste = paare(document.querySelectorAll('aside .text-slate-500, aside .text-xs.text-slate-400'), '.font-medium, .font-semibold, .text-slate-700', true);
    // Rückfall ohne Seitenleiste und Dokumentkarten: allgemeine Formularangaben.
    const formular = !seitenleiste.length && !dokumente.length ? paare(main?.querySelectorAll('.text-slate-500'), '.font-medium, .font-semibold', false) : [];
    const ueberschriften = Array.from(main?.querySelectorAll('h1, h2, h3, h4') ?? []).map((el) => text(el)).filter(Boolean).slice(0, 8).join(', ');
    const projektStatus = text(h1?.parentElement?.querySelector('.rounded-full'));
    const zahlen = kennzahlen(main);
    const aktiverReiter = text(main?.querySelector('[data-state="active"], [aria-selected="true"], .border-rose-500, .text-rose-600.border-b-2, .border-b-2.border-rose-500'));
    const knoepfe = gesperrteKnoepfe(main);
    const hinweise = Array.from(document.querySelectorAll('.bg-blue-50')).map((el) => text(el)).filter(Boolean).slice(0, 3);

    const teile: string[] = [];
    if (ueberschriften) teile.push(`Ueberschriften: ${ueberschriften}`);
    if (projektStatus) teile.push(`Projekt-Status: ${projektStatus}`);
    if (zahlen.length) teile.push(`Kennzahlen: ${zahlen.join(' | ')}`);
    if (aktiverReiter) teile.push(`Aktiver Tab: ${aktiverReiter}`);
    if (dokumente.length) teile.push(`Geschaeftsdokumente:\n${dokumente.map((d) => `  - ${d}`).join('\n')}`);
    if (seitenleiste.length) teile.push(`Seitenleiste: ${seitenleiste.slice(0, 15).join(' | ')}`);
    if (formular.length) teile.push(`Formulardaten: ${formular.slice(0, 15).join(' | ')}`);
    if (knoepfe.length) teile.push(`Deaktivierte Buttons: ${knoepfe.join(', ')}`);
    if (hinweise.length) teile.push(`Hinweise: ${hinweise.join('; ')}`);

    return {
        route,
        pageTitle: text(h1) || document.title,
        visibleContent: teile.join('\n') || undefined,
        errorMessages: seitenFehler(istEigeneMeldung) || undefined,
        latitude: standort?.latitude,
        longitude: standort?.longitude,
    };
}
