import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnrufFensterHost } from './AnrufFensterHost';
import { antwort, KUNDE_MAX, stubbeFetch } from './telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from './useTelefonBerechtigung';

type Hoerer = (e: MessageEvent) => void;
const quellen: { url: string; hoerer: Map<string, Hoerer>; close: () => void }[] = [];

class FakeEventSource {
    url: string;
    readyState = 1;
    onopen = null;
    onerror = null;
    hoerer = new Map<string, Hoerer>();
    constructor(url: string) {
        this.url = url;
        quellen.push(this);
    }
    addEventListener(typ: string, fn: Hoerer) { this.hoerer.set(typ, fn); }
    removeEventListener(typ: string) { this.hoerer.delete(typ); }
    close() { this.readyState = 2; }
}

function sende(daten: object) {
    act(() => quellen.at(-1)!.hoerer.get('anruf')!(new MessageEvent('anruf', { data: JSON.stringify(daten) })));
}

function Ort() {
    const ort = useLocation();
    return <p data-testid="ort">{ort.pathname}{ort.search}</p>;
}

describe('AnrufFensterHost', () => {
    beforeEach(() => {
        quellen.length = 0;
        setzeTelefonBerechtigungZurueck();
        vi.stubGlobal('EventSource', FakeEventSource);
    });
    afterEach(() => vi.unstubAllGlobals());

    it('ohne Telefon-Recht keine Verbindung und kein Fenster', async () => {
        const fetchMock = stubbeFetch(() => antwort({ darfTelefonSehen: false }));
        render(<MemoryRouter><AnrufFensterHost /></MemoryRouter>);
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        await new Promise((r) => setTimeout(r, 0));
        expect(quellen).toHaveLength(0);
    });

    it('zeigt den Anruf und öffnet die Kundenakte', async () => {
        stubbeFetch(() => antwort({ darfTelefonSehen: true }));
        render(
            <MemoryRouter initialEntries={['/projekte']}>
                <Routes><Route path="*" element={<><AnrufFensterHost /><Ort /></>} /></Routes>
            </MemoryRouter>,
        );
        await waitFor(() => expect(quellen).toHaveLength(1));
        sende({ verbindungsId: 'x', status: 'KLINGELT', nummer: '0931 1234567', kontakt: KUNDE_MAX, kandidaten: [], angenommen: false });
        fireEvent.click(await screen.findByRole('button', { name: 'Akte öffnen' }));
        expect(screen.getByTestId('ort')).toHaveTextContent('/kunden?kundeId=7');
        expect(screen.queryByTestId('anruf-fenster')).toBeNull();
    });
});
