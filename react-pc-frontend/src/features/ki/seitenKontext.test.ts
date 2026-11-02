import { afterEach, describe, expect, it } from 'vitest';
import { sammleSeitenKontext } from './seitenKontext';

const keineEigenen = () => false;

afterEach(() => { document.body.replaceChildren(); document.title = ''; });

/** Baut die Testseite über DOM-Aufrufe (kein innerHTML). */
function el(tag: string, klassen = '', textInhalt?: string, kinder: Element[] = []): HTMLElement {
    const e = document.createElement(tag);
    if (klassen) e.className = klassen;
    if (textInhalt !== undefined) e.textContent = textInhalt;
    kinder.forEach((k) => e.appendChild(k));
    return e;
}

describe('sammleSeitenKontext', () => {
    it('fällt ohne Inhalt auf den Dokumenttitel zurück und lässt Leeres weg', () => {
        document.title = 'Handwerkerprogramm';
        expect(sammleSeitenKontext({ route: '/kunden', istEigeneMeldung: keineEigenen, standort: null })).toEqual({
            route: '/kunden', pageTitle: 'Handwerkerprogramm', visibleContent: undefined, errorMessages: undefined, latitude: undefined, longitude: undefined,
        });
    });

    it('sammelt Überschriften, Status, Kennzahlen, Reiter, Karten, Knöpfe, Hinweise und Fehler', () => {
        const kopf = el('div', '', undefined, [el('h1', '', 'Neubau Mustermann'), el('span', 'rounded-full', 'In Arbeit')]);
        const kennzahl = el('div', '', undefined, [el('span', 'text-[11px] text-slate-400 uppercase', 'Umsatz'), el('span', 'font-semibold', '1.000 €')]);
        const karte = el('div', 'bg-white rounded-xl border p-4', undefined, [
            el('div', 'flex items-center gap-2 mb-1', undefined, [el('span', 'rounded-full', 'Rechnung'), el('span', 'rounded-full', 'Offen')]),
            el('span', 'font-semibold text-slate-900', 'RE-2026-014'),
            el('span', 'text-sm text-slate-600', 'Geländer'),
            el('span', 'text-lg font-bold', '500,00 €'),
            el('span', 'text-[10px] text-slate-400', 'aus Angebot'),
        ]);
        const nurAbzeichen = el('div', 'bg-white rounded-xl border p-4', undefined, [
            el('div', 'flex items-center gap-2 mb-1', undefined, [el('span', 'rounded-full', 'Angebot')]),
        ]);
        const leereKarte = el('div', 'bg-white rounded-xl border p-4', undefined, [el('div', 'flex items-center gap-2 mb-1')]);
        const ohneZeile = el('div', 'bg-white rounded-xl border p-4');
        const knopf = el('button', '', 'Senden'); knopf.setAttribute('disabled', ''); knopf.title = 'Kunde fehlt';
        const knopf2 = el('button', '', 'Buchen'); knopf2.setAttribute('disabled', '');
        const reiter = el('button', '', 'Dokumente'); reiter.setAttribute('aria-selected', 'true');
        const main = el('main', '', undefined, [kopf, kennzahl, el('h2', '', 'Dokumente'), reiter, karte, nurAbzeichen, leereKarte, ohneZeile, knopf, knopf2]);
        const fehler = el('div', '', 'Speichern fehlgeschlagen'); fehler.setAttribute('role', 'alert');
        const eigener = el('div', 'bg-red-50', 'Fehler der KI-Hilfe');
        document.body.append(main, fehler, eigener, el('div', 'bg-blue-50', 'Hinweis A'),
            el('aside', '', undefined, [el('div', '', undefined, [el('span', 'text-slate-500', 'Kunde:'), el('span', 'font-medium', 'Max Mustermann')]),
                el('div', '', undefined, [el('span', 'text-slate-500', 'Telefon'), el('span', 'font-medium', '–')])]));

        const kontext = sammleSeitenKontext({ route: '/projekte', istEigeneMeldung: (e) => e === eigener, standort: { latitude: 50, longitude: 10 } });
        expect(kontext.pageTitle).toBe('Neubau Mustermann');
        expect(kontext.errorMessages).toBe('Speichern fehlgeschlagen');
        expect(kontext.latitude).toBe(50);
        expect(kontext.longitude).toBe(10);
        expect(kontext.visibleContent).toBe([
            'Ueberschriften: Neubau Mustermann, Dokumente',
            'Projekt-Status: In Arbeit',
            'Kennzahlen: Umsatz: 1.000 €',
            'Aktiver Tab: Dokumente',
            'Geschaeftsdokumente:\n  - Rechnung | Offen Nr. RE-2026-014 "Geländer" 500,00 € (aus Angebot)\n  - Angebot',
            'Seitenleiste: Kunde: Max Mustermann',
            'Deaktivierte Buttons: "Senden" (Kunde fehlt), "Buchen" (deaktiviert)',
            'Hinweise: Hinweis A',
        ].join('\n'));
    });

    it('nimmt ohne Seitenleiste und Karten allgemeine Formularangaben', () => {
        const feld = (l: string, v: string) => el('div', '', undefined, [el('span', 'text-slate-500', l), el('span', 'font-medium', v)]);
        const nurKennzahl = el('div', '', undefined, [el('span', 'text-[11px] text-slate-400 uppercase', 'Leer')]);
        const ohneWert = el('div', '', undefined, [el('span', 'text-slate-500', 'Allein')]);
        document.body.append(el('main', '', undefined, [feld('Ort:', 'Musterstadt'), feld('Fax', '–'), nurKennzahl, ohneWert]));
        const kontext = sammleSeitenKontext({ route: '/x', istEigeneMeldung: keineEigenen, standort: null });
        expect(kontext.visibleContent).toBe('Formulardaten: Ort: Musterstadt');
    });
});
