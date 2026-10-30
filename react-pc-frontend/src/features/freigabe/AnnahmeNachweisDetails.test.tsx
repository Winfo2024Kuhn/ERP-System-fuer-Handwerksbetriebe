import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AnnahmeNachweisDetails } from './AnnahmeNachweisDetails';
import { ladeFreigabeAudit, type FreigabeAuditDaten } from './freigabeAudit';

const AUDIT: FreigabeAuditDaten = {
    status: 'ACCEPTED', dokumentArt: 'Angebot', dokumentNummer: 'AG-2026/10/00021',
    erstelltAm: '2026-10-10T08:00:00', ablaufDatum: '2026-10-24T00:00:00', akzeptiertAm: '2026-10-13T09:30:15',
    akzeptiertEmail: 'max@example.com', akzeptiertIp: '192.0.2.10', akzeptiertUserAgent: 'Mozilla/5.0 Testbrowser',
    unterzeichnerVorname: 'Max', unterzeichnerNachname: 'Mustermann', unterzeichnerName: 'Max Mustermann',
    hashOriginal: 'a'.repeat(64), hashAcceptance: null,
    codeBestaetigtAm: null, codeBestaetigtEmail: null, vorzeitigerBeginn: false,
    widerrufStand: null, datenschutzStand: null, bestaetigungText: null, knopfText: null,
};

function zeige(daten: FreigabeAuditDaten, kompakt?: boolean) {
    return render(<ToastProvider><AnnahmeNachweisDetails daten={daten} kompakt={kompakt} /></ToastProvider>);
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
        expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText('max@example.com')).toBeInTheDocument();
        expect(screen.getByText('192.0.2.10')).toBeInTheDocument();
        expect(screen.getByText('Mozilla/5.0 Testbrowser')).toBeInTheDocument();
        expect(screen.getByText(/Erst nach Ablauf der Widerrufsfrist beginnen/)).toBeInTheDocument();
        expect(screen.getByText('Kryptographischer Beweis')).toBeInTheDocument();
        expect(screen.getByText('aaaaaaaa…aaaaaaaa')).toBeInTheDocument();
        // Fehlender Annahme-Hash zeigt einen Strich, keinen Kopieren-Knopf.
        expect(screen.getAllByRole('button', { name: 'Kopieren' })).toHaveLength(1);
        expect(screen.queryByRole('button', { name: /Technische Angaben/ })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Kopieren' }));
        expect(writeText).toHaveBeenCalledWith('a'.repeat(64));
        expect(await screen.findByText('Hash in Zwischenablage kopiert')).toBeInTheDocument();
    });

    it('meldet, wenn das Kopieren scheitert', async () => {
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: vi.fn().mockRejectedValue(new Error('nein')) } });
        zeige(AUDIT);
        await userEvent.click(screen.getByRole('button', { name: 'Kopieren' }));
        expect(await screen.findByText('Kopieren fehlgeschlagen')).toBeInTheDocument();
    });

    it('zeigt Striche für fehlende Angaben und lässt den Browser weg', () => {
        zeige({
            ...AUDIT, dokumentArt: null, dokumentNummer: null, akzeptiertAm: null, unterzeichnerName: null,
            akzeptiertEmail: null, akzeptiertIp: null, akzeptiertUserAgent: null, hashOriginal: null,
            vorzeitigerBeginn: null,
        });
        expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(7);
        expect(screen.queryByText('Browser')).not.toBeInTheDocument();
        // Der Hinweis „Keine Angabe gespeichert“ gehört nur in die kompakte Fassung.
        expect(screen.queryByText(/Keine Angabe gespeichert/)).not.toBeInTheDocument();
    });

    it('lässt ein unlesbares Annahmedatum stehen', () => {
        zeige({ ...AUDIT, akzeptiertAm: 'kaputt', vorzeitigerBeginn: true });
        expect(screen.getByText('kaputt')).toBeInTheDocument();
    });

    it('klappt in der kompakten Fassung die technischen Angaben auf und zu', async () => {
        zeige(AUDIT, true);

        expect(screen.getByText('Angenommen wurde')).toBeInTheDocument();
        expect(screen.queryByText('192.0.2.10')).not.toBeInTheDocument();
        const knopf = screen.getByRole('button', { name: 'Technische Angaben anzeigen' });
        expect(knopf).toHaveAttribute('aria-expanded', 'false');

        await userEvent.click(knopf);
        expect(screen.getByText('192.0.2.10')).toBeInTheDocument();
        expect(screen.getByText('Kryptographischer Beweis')).toBeInTheDocument();

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
