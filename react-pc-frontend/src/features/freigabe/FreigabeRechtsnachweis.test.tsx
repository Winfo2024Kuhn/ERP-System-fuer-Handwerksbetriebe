import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ArbeitsbeginnAbschnitt, CodeBestaetigtZeile, WasBestaetigtAbschnitt } from './FreigabeRechtsnachweis';
import {
    arbeitsbeginn, formatiereBetrag, formatiereStand, fristEnde, haekchenListe, type FreigabeRechtsnachweisDaten,
} from './rechtsnachweisLogik';

const leer: FreigabeRechtsnachweisDaten = {
    akzeptiertAm: null,
    codeBestaetigtAm: null,
    codeBestaetigtEmail: null,
    vorzeitigerBeginn: null,
    widerrufStand: null,
    datenschutzStand: null,
    bestaetigungText: null,
    knopfText: null,
    vorzeitigerBeginnText: null,
    akzeptierterBetrag: null,
    mitbeauftragteAlternativen: [],
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

    it('haekchenListe kennt Pflicht- und freiwilliges Häkchen samt Wortlaut', () => {
        expect(haekchenListe(leer)).toEqual([]);
        expect(haekchenListe({ ...leer, bestaetigungText: '  ', vorzeitigerBeginn: false })).toEqual([
            { art: 'freiwillig', angehakt: false, wortlaut: null },
        ]);
        expect(haekchenListe({
            ...leer, bestaetigungText: ' Ich beauftrage verbindlich. ', vorzeitigerBeginn: true,
            vorzeitigerBeginnText: 'Ich verlange den sofortigen Beginn.',
        })).toEqual([
            { art: 'pflicht', angehakt: true, wortlaut: 'Ich beauftrage verbindlich.' },
            { art: 'freiwillig', angehakt: true, wortlaut: 'Ich verlange den sofortigen Beginn.' },
        ]);
    });

    it('formatiereBetrag zeigt Euro im deutschen Format', () => {
        expect(formatiereBetrag(4950.5)).toMatch(/^4\.950,50\s€$/);
        expect(formatiereBetrag(0)).toMatch(/^0,00\s€$/);
    });
});

describe('CodeBestaetigtZeile', () => {
    it('zeigt Zeit und Adresse, ohne Adresse nur die Zeit, ohne Code nichts', () => {
        const { rerender, container } = render(
            <CodeBestaetigtZeile daten={{ codeBestaetigtAm: '2026-10-30T12:26:00', codeBestaetigtEmail: 'max@example.com' }} />,
        );
        expect(screen.getByText('30.10.2026, 12:26 an max@example.com')).toBeInTheDocument();
        rerender(<CodeBestaetigtZeile daten={{ codeBestaetigtAm: '2026-10-30T12:26:00', codeBestaetigtEmail: null }} />);
        expect(screen.getByText('30.10.2026, 12:26')).toBeInTheDocument();
        rerender(<CodeBestaetigtZeile daten={{ codeBestaetigtAm: null, codeBestaetigtEmail: 'max@example.com' }} />);
        expect(container).toBeEmptyDOMElement();
    });
});

describe('WasBestaetigtAbschnitt', () => {
    it('rendert nichts bei Altbestand ohne Angaben', () => {
        const { container } = render(<WasBestaetigtAbschnitt daten={leer} />);
        expect(container).toBeEmptyDOMElement();
    });

    it('zeigt nur den Knopf und eine Fassung, wenn sonst nichts gespeichert ist', () => {
        render(<WasBestaetigtAbschnitt daten={{ ...leer, knopfText: 'Zahlungspflichtig beauftragen', datenschutzStand: '2026-10-13' }} />);
        expect(screen.getByText('„Zahlungspflichtig beauftragen“')).toBeInTheDocument();
        expect(screen.getByText('Datenschutzerklärung vom 13.10.2026')).toBeInTheDocument();
        expect(screen.queryByRole('list', { name: 'Häkchen bei der Annahme' })).not.toBeInTheDocument();
    });

    it('zeigt nur die Wahlpositionen, wenn kein Betrag geliefert wird', () => {
        render(<WasBestaetigtAbschnitt daten={{ ...leer, mitbeauftragteAlternativen: ['3.2 Geländer Edelstahl'] }} />);
        expect(screen.getByText('3.2 Geländer Edelstahl')).toBeInTheDocument();
        expect(screen.queryByText(/Verbindlicher Betrag/)).not.toBeInTheDocument();
    });

    it('zeigt nur das Pflicht-Häkchen, wenn zum Arbeitsbeginn nichts gespeichert ist', () => {
        render(<WasBestaetigtAbschnitt daten={{ ...leer, widerrufStand: '2026-10-13', bestaetigungText: 'Ich beauftrage verbindlich.' }} />);
        expect(screen.getByText('Pflicht-Häkchen')).toBeInTheDocument();
        expect(screen.getByText('„Ich beauftrage verbindlich.“')).toBeInTheDocument();
        expect(screen.queryByText('Freiwilliges Häkchen')).not.toBeInTheDocument();
        expect(screen.getByText('Widerrufsbelehrung vom 13.10.2026')).toBeInTheDocument();
        expect(screen.queryByText('Knopf gedrückt')).not.toBeInTheDocument();
    });
});

describe('ArbeitsbeginnAbschnitt', () => {
    it('zeigt die Wartepflicht gelb und erklärt die Folgen per Info-Knopf', async () => {
        render(<ArbeitsbeginnAbschnitt daten={{ vorzeitigerBeginn: false, akzeptiertAm: '2026-10-13T09:30:00' }} />);
        expect(screen.getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toHaveClass('bg-amber-50');
        await userEvent.click(screen.getByRole('button', { name: /Arbeitsbeginn und Widerrufsfrist/ }));
        expect(await screen.findByText(/bekommen Sie für die geleistete Arbeit kein Geld/)).toBeInTheDocument();
    });

    it('zeigt sofortigen Beginn grün', () => {
        render(<ArbeitsbeginnAbschnitt daten={{ vorzeitigerBeginn: true, akzeptiertAm: null }} />);
        expect(screen.getByText(/Sofort beginnen erlaubt/)).toHaveClass('bg-emerald-50');
    });

    it('ohne Angabe: nichts, oder auf Wunsch der Hinweis „Keine Angabe gespeichert“', () => {
        const { container, rerender } = render(<ArbeitsbeginnAbschnitt daten={{ vorzeitigerBeginn: null, akzeptiertAm: null }} />);
        expect(container).toBeEmptyDOMElement();
        rerender(<ArbeitsbeginnAbschnitt daten={{ vorzeitigerBeginn: null, akzeptiertAm: null }} ohneAngabeHinweis />);
        expect(screen.getByText(/Keine Angabe gespeichert/)).toBeInTheDocument();
    });
});
