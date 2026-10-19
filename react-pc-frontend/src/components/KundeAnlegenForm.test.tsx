import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KundeAnlegenForm } from './KundeAnlegenForm';

// DSGVO: ausschließlich Dummy-Daten in Tests.

/** Was das Formular an POST /api/kunden geschickt hat. */
let gesendet: Record<string, unknown>[];

function antwort(body: unknown, status = 200) {
    return Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(body),
    });
}

beforeEach(() => {
    gesendet = [];
    vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/kunden/next-kundennummer') return antwort({ kundennummer: 'K-2001' });
        if (url.startsWith('/api/kunden/duplikat-check')) return antwort({ duplikate: [], harterTreffer: false });
        if (url === '/api/kunden' && init?.method === 'POST') {
            gesendet.push(JSON.parse(String(init.body)));
            return antwort({ id: 77, kundennummer: 'K-2001', name: 'Max Mustermann' }, 201);
        }
        return antwort([]);
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
});

/** Name und Adresse ausfüllen – das Telefon-Feld bekommt dabei die Ortsvorwahl vorgeschlagen. */
async function fuelleKundeAus() {
    const onSuccess = vi.fn();
    render(<KundeAnlegenForm onSuccess={onSuccess} onBack={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText('Firma / Name'), { target: { value: 'Max Mustermann' } });
    fireEvent.change(screen.getByLabelText('PLZ'), { target: { value: '97070' } });
    fireEvent.change(screen.getByLabelText('Ort'), { target: { value: 'Würzburg' } });
    const telefon = screen.getByPlaceholderText('0511 12 34 56');
    await waitFor(() => expect(telefon).toHaveValue('0931 '));
    return { telefon, onSuccess };
}

describe('KundeAnlegenForm – vorgeschlagene Ortsvorwahl', () => {
    it('speichert eine unberührte Vorwahl nicht als Telefonnummer', async () => {
        const { onSuccess } = await fuelleKundeAus();

        await userEvent.click(screen.getByRole('button', { name: 'Kunde anlegen' }));

        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
        expect(gesendet).toHaveLength(1);
        expect(gesendet[0].telefon).toBeNull();
        expect(gesendet[0].mobiltelefon).toBeNull();
    });

    it('speichert eine ergänzte Nummer unverändert', async () => {
        const { telefon, onSuccess } = await fuelleKundeAus();
        fireEvent.change(telefon, { target: { value: '0931 123456' } });

        await userEvent.click(screen.getByRole('button', { name: 'Kunde anlegen' }));

        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
        expect(gesendet[0].telefon).toBe('0931 123456');
    });
});

describe('KundeAnlegenForm – Zahlungsziel', () => {
    it('übernimmt ein frei eingetipptes Zahlungsziel', async () => {
        const { onSuccess } = await fuelleKundeAus();
        const feld = screen.getByLabelText('Zahlungsziel (Tage)');
        fireEvent.change(feld, { target: { value: '' } });
        expect(feld).toHaveValue('');
        fireEvent.change(feld, { target: { value: '30' } });

        await userEvent.click(screen.getByRole('button', { name: 'Kunde anlegen' }));

        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
        expect(gesendet[0].zahlungsziel).toBe(30);
    });

    it('legt mit leerem Zahlungsziel nichts an', async () => {
        const { onSuccess } = await fuelleKundeAus();
        fireEvent.change(screen.getByLabelText('Zahlungsziel (Tage)'), { target: { value: '' } });

        await userEvent.click(screen.getByRole('button', { name: 'Kunde anlegen' }));

        expect(await screen.findByText('Das Zahlungsziel muss zwischen 1 und 365 Tagen liegen.')).toBeInTheDocument();
        expect(onSuccess).not.toHaveBeenCalled();
        expect(gesendet).toHaveLength(0);
    });
});
