import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { FreigabeRechtsnachweis } from './FreigabeRechtsnachweis';
import { arbeitsbeginn, formatiereStand, fristEnde, type FreigabeRechtsnachweisDaten } from './rechtsnachweisLogik';

const leer: FreigabeRechtsnachweisDaten = {
    akzeptiertAm: null,
    codeBestaetigtAm: null,
    codeBestaetigtEmail: null,
    vorzeitigerBeginn: null,
    widerrufStand: null,
    datenschutzStand: null,
    bestaetigungText: null,
    knopfText: null,
};

describe('Hilfsfunktionen', () => {
    it('formatiereStand wandelt ISO-Datum und lässt Unlesbares stehen', () => {
        expect(formatiereStand('2026-10-13')).toBe('13.10.2026');
        expect(formatiereStand(' 2026-10-13 ')).toBe('13.10.2026');
        expect(formatiereStand('Herbst 2026')).toBe('Herbst 2026');
        expect(formatiereStand(null)).toBeNull();
        expect(formatiereStand('')).toBeNull();
    });

    it('fristEnde liegt 14 Tage nach dem Annahmetag, auch über den Monatswechsel', () => {
        expect(fristEnde('2026-10-13T09:30:00')?.toLocaleDateString('de-DE')).toBe('27.10.2026');
        expect(fristEnde('2026-10-25T23:59:00')?.toLocaleDateString('de-DE')).toBe('8.11.2026');
        expect(fristEnde(null)).toBeNull();
        expect(fristEnde('kaputt')).toBeNull();
    });

    it('arbeitsbeginn unterscheidet erlaubt, warten und keine Angabe', () => {
        expect(arbeitsbeginn({ vorzeitigerBeginn: true, akzeptiertAm: null })?.ton).toBe('erlaubt');
        expect(arbeitsbeginn({ vorzeitigerBeginn: false, akzeptiertAm: '2026-10-13T09:30:00' })).toEqual({
            text: 'Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).',
            ton: 'warten',
        });
        expect(arbeitsbeginn({ vorzeitigerBeginn: false, akzeptiertAm: null })?.text)
            .toBe('Erst nach Ablauf der Widerrufsfrist beginnen.');
        expect(arbeitsbeginn({ vorzeitigerBeginn: null, akzeptiertAm: null })).toBeNull();
    });
});

describe('FreigabeRechtsnachweis', () => {
    it('rendert nichts bei Altbestand ohne Angaben', () => {
        const { container } = render(<FreigabeRechtsnachweis daten={leer} />);
        expect(container).toBeEmptyDOMElement();
    });

    it('zeigt Code, Arbeitsbeginn, Fassungen und Knopf mit Info-Erklärung', async () => {
        render(<FreigabeRechtsnachweis daten={{
            akzeptiertAm: '2026-10-13T09:30:00',
            codeBestaetigtAm: '2026-10-13T09:30:00',
            codeBestaetigtEmail: 'max@example.de',
            vorzeitigerBeginn: false,
            widerrufStand: '2026-10-13',
            datenschutzStand: '2026-10-13',
            bestaetigungText: 'Ich beauftrage Angebot AN-1 verbindlich und zahlungspflichtig.',
            knopfText: 'Zahlungspflichtig beauftragen',
        }} />);

        expect(screen.getByText('Rechtsnachweis')).toBeInTheDocument();
        expect(screen.getByText(/13\.10\.2026, 09:30 an max@example\.de/)).toBeInTheDocument();
        expect(screen.getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toHaveClass('bg-amber-50');
        expect(screen.getByText('Widerrufsbelehrung vom 13.10.2026 · Datenschutzerklärung vom 13.10.2026')).toBeInTheDocument();
        expect(screen.getByText('„Zahlungspflichtig beauftragen“')).toBeInTheDocument();
        expect(screen.getByText(/Häkchen: Ich beauftrage Angebot AN-1/)).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /Arbeitsbeginn und Widerrufsfrist/ }));
        expect(await screen.findByText(/bekommen Sie für die geleistete Arbeit kein Geld/)).toBeInTheDocument();
    });

    it('zeigt sofortigen Beginn grün und lässt fehlende Teile weg', () => {
        render(<FreigabeRechtsnachweis daten={{ ...leer, vorzeitigerBeginn: true, codeBestaetigtAm: 'kaputt' }} />);

        expect(screen.getByText(/Sofort beginnen erlaubt/)).toHaveClass('bg-emerald-50');
        expect(screen.getByText('—')).toBeInTheDocument();
        expect(screen.queryByText('Geltende Fassungen')).not.toBeInTheDocument();
        expect(screen.queryByText('Ausgelöst mit')).not.toBeInTheDocument();
    });

    it('zeigt nur den Knopf, wenn kein Häkchentext vorliegt, und nur eine Fassung', () => {
        render(<FreigabeRechtsnachweis daten={{ ...leer, knopfText: 'Zahlungspflichtig beauftragen', datenschutzStand: '2026-10-13' }} />);

        expect(screen.getByText('Datenschutzerklärung vom 13.10.2026')).toBeInTheDocument();
        expect(screen.queryByText(/Häkchen:/)).not.toBeInTheDocument();
        expect(screen.queryByText('Arbeitsbeginn')).not.toBeInTheDocument();
    });

    it('zeigt den Häkchentext auch ohne Knopf', () => {
        render(<FreigabeRechtsnachweis daten={{ ...leer, bestaetigungText: 'Ich beauftrage verbindlich.' }} />);
        expect(screen.getByText('Häkchen: Ich beauftrage verbindlich.')).toBeInTheDocument();
    });
});
