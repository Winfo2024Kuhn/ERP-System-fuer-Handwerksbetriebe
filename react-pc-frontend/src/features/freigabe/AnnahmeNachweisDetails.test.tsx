import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AnnahmeNachweisDetails } from './AnnahmeNachweisDetails';
import { ladeFreigabeAudit, type FreigabeAuditDaten } from './freigabeAudit';

const PFLICHT = 'Ich beauftrage Angebot AG-2026/10/00021 verbindlich und zahlungspflichtig.';
const FREIWILLIG = 'Ich verlange ausdrücklich, dass Sie mit der Ausführung schon vor Ablauf der Widerrufsfrist '
    + 'beginnen. Mir ist bekannt, dass mein Widerrufsrecht erlischt, sobald Sie die Leistung vollständig erbracht haben.';
const ANNAHME_HASH = '0123456789abcdef'.repeat(4);

const AUDIT: FreigabeAuditDaten = {
    status: 'ACCEPTED', dokumentArt: 'Angebot', dokumentNummer: 'AG-2026/10/00021',
    erstelltAm: '2026-10-10T08:00:00', ablaufDatum: '2026-10-24T00:00:00', akzeptiertAm: '2026-10-13T09:30:15',
    akzeptiertEmail: 'max@example.com', akzeptiertIp: '192.0.2.10', akzeptiertUserAgent: 'Mozilla/5.0 Testbrowser',
    unterzeichnerVorname: 'Max', unterzeichnerNachname: 'Mustermann', unterzeichnerName: 'Max Mustermann',
    hashOriginal: 'a'.repeat(64), hashAcceptance: null,
    codeBestaetigtAm: null, codeBestaetigtEmail: null, vorzeitigerBeginn: false,
    widerrufStand: null, datenschutzStand: null, bestaetigungText: null, knopfText: null,
    vorzeitigerBeginnText: null, akzeptierterBetrag: null, mitbeauftragteAlternativen: [],
};

/** Neue Annahme mit allen Angaben, wie sie die Website ab dem nächsten Update speichert. */
const VOLL: FreigabeAuditDaten = {
    ...AUDIT, akzeptiertAm: '2026-10-30T12:26:40', hashAcceptance: ANNAHME_HASH,
    codeBestaetigtAm: '2026-10-30T12:26:00', codeBestaetigtEmail: 'max@example.com',
    widerrufStand: '2026-10-13', datenschutzStand: '2026-10-13',
    bestaetigungText: PFLICHT, knopfText: 'Zahlungspflichtig beauftragen', vorzeitigerBeginnText: FREIWILLIG,
};

function zeige(daten: FreigabeAuditDaten, kompakt?: boolean) {
    return render(<ToastProvider><AnnahmeNachweisDetails daten={daten} kompakt={kompakt} /></ToastProvider>);
}

/** Die Zeile eines Häkchens in der Checkliste („Pflicht-Häkchen“/„Freiwilliges Häkchen“). */
function haekchen(art: string) {
    const liste = screen.getByRole('list', { name: 'Häkchen bei der Annahme' });
    return within(liste).getByText(art).closest('li') as HTMLElement;
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('AnnahmeNachweisDetails', () => {
    it('zeigt im Dialog alle Angaben samt Fingerabdrücken und kopiert den vollen Hash', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
        zeige(AUDIT);

        expect(screen.getByText('Dokument')).toBeInTheDocument();
        expect(screen.getByText('Angebot AG-2026/10/00021')).toBeInTheDocument();
        expect(screen.getByText('13.10.2026, 09:30:15')).toBeInTheDocument();
        expect(screen.getByText('max@example.com')).toBeInTheDocument();
        expect(screen.getByText('192.0.2.10')).toBeInTheDocument();
        expect(screen.getByText('Mozilla/5.0 Testbrowser')).toBeInTheDocument();
        expect(screen.getByText(/Erst nach Ablauf der Widerrufsfrist beginnen/)).toBeInTheDocument();
        expect(screen.getByText('Technische Angaben')).toBeInTheDocument();
        expect(screen.getByText('aaaaaaaa…aaaaaaaa')).toBeInTheDocument();
        // Fehlender Annahme-Hash zeigt einen Strich, keinen Kopieren-Knopf.
        expect(screen.getAllByRole('button', { name: /kopieren/ })).toHaveLength(1);
        expect(screen.queryByRole('button', { name: /Technische Angaben/ })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Original-Hash kopieren' }));
        expect(writeText).toHaveBeenCalledWith('a'.repeat(64));
        expect(await screen.findByText('Hash in Zwischenablage kopiert')).toBeInTheDocument();
    });

    it('meldet, wenn das Kopieren scheitert', async () => {
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: vi.fn().mockRejectedValue(new Error('nein')) } });
        zeige(AUDIT);
        await userEvent.click(screen.getByRole('button', { name: 'Original-Hash kopieren' }));
        expect(await screen.findByText('Kopieren fehlgeschlagen')).toBeInTheDocument();
    });

    it('zeigt Vor- und Nachname getrennt, so wie eingetragen, und die Code-Bestätigung', () => {
        zeige({ ...VOLL, unterzeichnerVorname: 'Max ', unterzeichnerNachname: 'Mustermann' });

        expect(screen.getByText('Wer hat angenommen')).toBeInTheDocument();
        expect(screen.getByText('Vorname (eingetragen)').parentElement?.nextElementSibling?.textContent).toBe('Max ');
        expect(screen.getByText('Nachname (eingetragen)').parentElement?.nextElementSibling?.textContent).toBe('Mustermann');
        expect(screen.queryByText('Angenommen von')).not.toBeInTheDocument();
        expect(screen.getByText('30.10.2026, 12:26 an max@example.com')).toBeInTheDocument();
    });

    it('zeigt bei älteren Annahmen ohne getrennten Namen den zusammengesetzten Namen', () => {
        zeige({ ...AUDIT, unterzeichnerVorname: null, unterzeichnerNachname: null });
        expect(screen.getByText('Angenommen von')).toBeInTheDocument();
        expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.queryByText('Vorname (eingetragen)')).not.toBeInTheDocument();
    });

    it('zeigt einen Strich, wenn nur ein Namensteil gespeichert ist', () => {
        zeige({ ...AUDIT, unterzeichnerVorname: null, unterzeichnerNachname: 'Mustermann' });
        expect(screen.getByText('Vorname (eingetragen)').parentElement?.nextElementSibling?.textContent).toBe('—');
    });

    it('zeigt die Checkliste: Pflicht-Häkchen angehakt, freiwilliges nicht angehakt, je mit Wortlaut', () => {
        zeige(VOLL);

        expect(screen.getByText('Was der Kunde bestätigt hat')).toBeInTheDocument();
        const pflicht = haekchen('Pflicht-Häkchen');
        expect(pflicht).toHaveTextContent('angehakt');
        expect(within(pflicht).getByText(`„${PFLICHT}“`)).toBeInTheDocument();
        expect(pflicht.querySelector('svg')).toHaveClass('lucide-square-check', 'text-emerald-600');

        const freiwillig = haekchen('Freiwilliges Häkchen');
        expect(within(freiwillig).getByText('nicht angehakt')).toBeInTheDocument();
        expect(within(freiwillig).getByText(`„${FREIWILLIG}“`)).toBeInTheDocument();
        expect(freiwillig.querySelector('svg')).toHaveClass('lucide-square', 'text-slate-400');

        expect(screen.getByText('Knopf gedrückt')).toBeInTheDocument();
        expect(screen.getByText('„Zahlungspflichtig beauftragen“')).toBeInTheDocument();
        expect(screen.getByText('Widerrufsbelehrung vom 13.10.2026 · Datenschutzerklärung vom 13.10.2026')).toBeInTheDocument();
        // Die alte Zeile „Ausgelöst mit“ ist in der Checkliste aufgegangen.
        expect(screen.queryByText('Ausgelöst mit')).not.toBeInTheDocument();
        // Ohne Wahlpositionen keine Zeilen dazu.
        expect(screen.queryByText('Mitbeauftragte Wahlpositionen')).not.toBeInTheDocument();
        expect(screen.queryByText(/Verbindlicher Betrag/)).not.toBeInTheDocument();
    });

    it('zeigt das freiwillige Häkchen angehakt und grünen Sofortbeginn', () => {
        zeige({ ...VOLL, vorzeitigerBeginn: true });
        const freiwillig = haekchen('Freiwilliges Häkchen');
        expect(within(freiwillig).getByText('angehakt')).toHaveClass('text-emerald-700');
        expect(freiwillig.querySelector('svg')).toHaveClass('lucide-square-check');
        expect(screen.getByText(/Sofort beginnen erlaubt/)).toHaveClass('bg-emerald-50');
    });

    it('nennt fehlenden Wortlaut des freiwilligen Häkchens, zeigt den Zustand aber trotzdem', () => {
        zeige({ ...VOLL, vorzeitigerBeginnText: null, vorzeitigerBeginn: true });
        const freiwillig = haekchen('Freiwilliges Häkchen');
        expect(within(freiwillig).getByText('angehakt')).toBeInTheDocument();
        expect(within(freiwillig).getByText('Wortlaut bei dieser Annahme nicht gespeichert')).toBeInTheDocument();
    });

    it('lässt bei sehr alten Annahmen ohne Entscheidung das freiwillige Häkchen weg', () => {
        zeige({ ...VOLL, vorzeitigerBeginn: null }, true);
        expect(screen.queryByText('Freiwilliges Häkchen')).not.toBeInTheDocument();
        expect(screen.getByText('Pflicht-Häkchen')).toBeInTheDocument();
        expect(screen.getByText(/Keine Angabe gespeichert/)).toBeInTheDocument();
    });

    it('zeigt mitbeauftragte Wahlpositionen und den verbindlichen Betrag', () => {
        zeige({ ...VOLL, mitbeauftragteAlternativen: ['3.2 Geländer Edelstahl', '4.1 Handlauf beidseitig'], akzeptierterBetrag: 4950.5 });
        expect(screen.getByText('Mitbeauftragte Wahlpositionen')).toBeInTheDocument();
        expect(screen.getByText('3.2 Geländer Edelstahl')).toBeInTheDocument();
        expect(screen.getByText('4.1 Handlauf beidseitig')).toBeInTheDocument();
        expect(screen.getByText('Verbindlicher Betrag (brutto)')).toBeInTheDocument();
        expect(screen.getByText(/4\.950,50\s€/)).toBeInTheDocument();
    });

    it('zeigt nur den Betrag, wenn die Wahlpositionen fehlen (älteres Backend)', () => {
        const ohneListe = { ...AUDIT, akzeptierterBetrag: 100 } as Partial<FreigabeAuditDaten>;
        delete ohneListe.mitbeauftragteAlternativen;
        zeige(ohneListe as FreigabeAuditDaten);
        expect(screen.getByText(/100,00\s€/)).toBeInTheDocument();
        expect(screen.queryByText('Mitbeauftragte Wahlpositionen')).not.toBeInTheDocument();
    });

    it('zeigt den vollen Annahme-Hash, kopiert ihn und erklärt ihn per Info-Knopf', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
        zeige(VOLL);

        expect(screen.getByText('steht so auch in der Mail an den Kunden', { exact: false })).toBeInTheDocument();
        expect(screen.getByText(ANNAHME_HASH)).toHaveClass('break-all');
        await userEvent.click(screen.getByRole('button', { name: 'Annahme-Hash kopieren' }));
        expect(writeText).toHaveBeenCalledWith(ANNAHME_HASH);

        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Annahme-Hash' }));
        expect(await screen.findByText(/der Kunde kann ihn deshalb nicht selbst nachrechnen/)).toBeInTheDocument();
        expect(screen.getByText(/Er gehört zum Inhalt der Auftragsbestätigung/)).toBeInTheDocument();
    });

    it('zeigt Striche für fehlende Angaben und lässt Browser und Abschnitte ohne Inhalt weg', () => {
        zeige({
            ...AUDIT, dokumentArt: null, dokumentNummer: null, akzeptiertAm: null, unterzeichnerName: null,
            unterzeichnerVorname: null, unterzeichnerNachname: null,
            akzeptiertEmail: null, akzeptiertIp: null, akzeptiertUserAgent: null, hashOriginal: null,
            vorzeitigerBeginn: null,
        });
        // Dokument, Angenommen am, Angenommen von, E-Mail, Annahme-Hash, IP, Original-Hash.
        expect(screen.getAllByText('—')).toHaveLength(7);
        expect(screen.queryByText('Browser')).not.toBeInTheDocument();
        expect(screen.queryByText('Was der Kunde bestätigt hat')).not.toBeInTheDocument();
        expect(screen.queryByText('Bestätigt per E-Mail-Code')).not.toBeInTheDocument();
        // Der Hinweis „Keine Angabe gespeichert“ gehört nur in die kompakte Fassung.
        expect(screen.queryByText(/Keine Angabe gespeichert/)).not.toBeInTheDocument();
        expect(screen.queryByText('Arbeitsbeginn')).not.toBeInTheDocument();
    });

    it('lässt ein unlesbares Annahmedatum stehen und zeigt bei kaputter Code-Zeit einen Strich', () => {
        zeige({ ...AUDIT, akzeptiertAm: 'kaputt', vorzeitigerBeginn: true, codeBestaetigtAm: 'kaputt' });
        expect(screen.getByText('kaputt')).toBeInTheDocument();
        expect(screen.getByText('Bestätigt per E-Mail-Code').parentElement?.nextElementSibling?.textContent).toBe('—');
    });

    it('zeigt in der kompakten Fassung den Annahme-Hash sofort, die technischen Angaben auf Klick', async () => {
        zeige(VOLL, true);

        expect(screen.getByText('Angenommen wurde')).toBeInTheDocument();
        expect(screen.getByText(ANNAHME_HASH)).toBeVisible();
        expect(screen.getByRole('button', { name: 'Mehr zu: Annahme-Hash' })).toBeInTheDocument();
        expect(screen.getByText('Pflicht-Häkchen')).toBeInTheDocument();
        expect(screen.queryByText('192.0.2.10')).not.toBeInTheDocument();
        expect(screen.queryByText('aaaaaaaa…aaaaaaaa')).not.toBeInTheDocument();
        const knopf = screen.getByRole('button', { name: 'Technische Angaben anzeigen' });
        expect(knopf).toHaveAttribute('aria-expanded', 'false');

        await userEvent.click(knopf);
        expect(screen.getByText('192.0.2.10')).toBeInTheDocument();
        expect(screen.getByText('aaaaaaaa…aaaaaaaa')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Technische Angaben ausblenden' }));
        expect(screen.queryByText('192.0.2.10')).not.toBeInTheDocument();
    });
});

describe('ladeFreigabeAudit', () => {
    const mitAntwort = (antwort: Promise<unknown>) => vi.stubGlobal('fetch', vi.fn(() => antwort));

    it('liefert die Daten bei Erfolg', async () => {
        mitAntwort(Promise.resolve({ ok: true, status: 200, json: async () => AUDIT }));
        await expect(ladeFreigabeAudit(28)).resolves.toEqual({ art: 'gefunden', daten: AUDIT });
        expect(fetch).toHaveBeenCalledWith('/api/ausgangs-dokumente/28/freigabe-audit');
    });

    it('unterscheidet „keine Online-Annahme“ (404) von Fehlern', async () => {
        mitAntwort(Promise.resolve({ ok: false, status: 404, json: async () => null }));
        await expect(ladeFreigabeAudit(28)).resolves.toEqual({ art: 'keine' });

        mitAntwort(Promise.resolve({ ok: false, status: 500, json: async () => null }));
        await expect(ladeFreigabeAudit(28)).resolves.toEqual({ art: 'fehler' });

        mitAntwort(Promise.reject(new TypeError('Failed to fetch')));
        await expect(ladeFreigabeAudit(28)).resolves.toEqual({ art: 'fehler' });

        mitAntwort(Promise.resolve({ ok: true, status: 200, json: () => Promise.reject(new SyntaxError('kein JSON')) }));
        await expect(ladeFreigabeAudit(28)).resolves.toEqual({ art: 'fehler' });
    });
});
