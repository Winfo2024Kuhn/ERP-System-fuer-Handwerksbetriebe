import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { TelefonSettingsSection } from './TelefonSettingsSection';
import { antwort, aufrufe, stubbeFetch } from '../../../features/telefon/telefonTestdaten';
import type { TelefonEinstellungen } from '../../../features/telefon/types';

const GESPEICHERT: TelefonEinstellungen = {
    aktiv: true,
    host: 'fritz.box',
    benutzer: 'erp',
    passwortGesetzt: true,
    verschluesselungEingerichtet: true,
    geschaeftsnummern: [],
    anrufbeantworter: [],
    aufbewahrungAnrufeMonate: 12,
    aufbewahrungSprachnachrichtenMonate: 12,
    landesvorwahl: '49',
    ortsvorwahl: '931',
    letzteAbholung: '2026-09-29T11:59:00',
    letzterFehler: null,
    anrufmonitorVerbunden: true,
};

function stubbe(stand: Partial<TelefonEinstellungen> = {}) {
    let aktuell: TelefonEinstellungen = { ...GESPEICHERT, ...stand };
    return stubbeFetch(
        (url, init) => {
            if (url.pathname !== '/api/telefon/einstellungen') return undefined;
            if (init?.method === 'PUT') {
                const body = JSON.parse(String(init.body));
                aktuell = { ...aktuell, ...body, passwortGesetzt: aktuell.passwortGesetzt || !!body.passwort };
            }
            return antwort(aktuell);
        },
        (url) => (url.pathname === '/api/telefon/einstellungen/test'
            ? antwort({
                erfolgreich: true,
                meldung: 'Verbindung zur FRITZ!Box steht.',
                eigeneNummern: ['0931 7654321', '0931 1111111'],
                anrufbeantworter: [{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }],
                landesvorwahl: '49',
                ortsvorwahl: '931',
            })
            : undefined),
        (url) => (url.pathname === '/api/telefon/admin/nachholen'
            ? antwort({ erfolgreich: true, meldung: 'ok', neueAnrufe: 120, neueSprachnachrichten: 4, nachtraeglichZugeordnet: 2 })
            : undefined),
    );
}

function zeige() {
    return render(<ToastProvider><TelefonSettingsSection /></ToastProvider>);
}

describe('TelefonSettingsSection', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('testet die Verbindung, übernimmt Nummern und Anrufbeantworter und speichert ohne Passwort', async () => {
        const fetchMock = stubbe();
        zeige();
        expect(await screen.findByLabelText('Adresse der FRITZ!Box')).toHaveValue('fritz.box');
        expect(screen.getByText('✓ gesetzt')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /Verbindung testen/ }));
        expect(await screen.findByText('Verbindung zur FRITZ!Box steht.')).toBeInTheDocument();
        const testBody = JSON.parse(String(aufrufe(fetchMock, '/api/telefon/einstellungen/test', 'POST')[0][1]?.body));
        expect(testBody).toEqual({ host: 'fritz.box', benutzer: 'erp' });

        const nummern = screen.getByRole('group', { name: 'Geschäftsnummern' });
        expect(within(nummern).getByRole('checkbox', { name: '0931 7654321' })).toBeChecked();
        fireEvent.click(within(nummern).getByRole('checkbox', { name: '0931 1111111' }));

        expect(screen.getByRole('textbox', { name: 'Name für Anrufbeantworter 2' })).toHaveValue('AB Nacht');
        fireEvent.change(screen.getByRole('textbox', { name: 'Name für Anrufbeantworter 1' }), { target: { value: 'AB Werkstatt' } });

        fireEvent.click(screen.getByRole('button', { name: /Telefon-Einstellungen speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/einstellungen', 'PUT')).toHaveLength(1));
        const body = JSON.parse(String(aufrufe(fetchMock, '/api/telefon/einstellungen', 'PUT')[0][1]?.body));
        expect(body).toEqual({
            aktiv: true,
            host: 'fritz.box',
            benutzer: 'erp',
            passwort: null,
            geschaeftsnummern: ['0931 7654321'],
            anrufbeantworter: [{ index: 0, name: 'AB Werkstatt' }, { index: 1, name: 'AB Nacht' }],
            aufbewahrungAnrufeMonate: 12,
            aufbewahrungSprachnachrichtenMonate: 12,
        });
        expect(await screen.findByText('Telefon-Einstellungen gespeichert.')).toBeInTheDocument();
    });

    it('schickt ein neu eingegebenes Passwort mit', async () => {
        const fetchMock = stubbe({ passwortGesetzt: false });
        zeige();
        fireEvent.change(await screen.findByLabelText('Passwort'), { target: { value: 'geheim-123' } });
        fireEvent.click(screen.getByRole('button', { name: /Telefon-Einstellungen speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/einstellungen', 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, '/api/telefon/einstellungen', 'PUT')[0][1]?.body)).passwort).toBe('geheim-123');
    });

    it('lehnt eine ungültige Aufbewahrung vor dem Speichern ab', async () => {
        const fetchMock = stubbe();
        zeige();
        fireEvent.change(await screen.findByLabelText('Anrufe aufbewahren (Monate)'), { target: { value: '500' } });
        fireEvent.click(screen.getByRole('button', { name: /Telefon-Einstellungen speichern/ }));
        expect(await screen.findByText('Aufbewahrung bitte als ganze Zahl zwischen 1 und 120 Monaten angeben.')).toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/telefon/einstellungen', 'PUT')).toHaveLength(0);
    });

    it('warnt, wenn das Passwort nicht geschützt gespeichert werden kann', async () => {
        stubbe({ verschluesselungEingerichtet: false });
        zeige();
        expect(await screen.findByText('Passwort kann nicht geschützt gespeichert werden.')).toBeInTheDocument();
        expect(screen.getByText('mail.credentials.encryption-key')).toBeInTheDocument();
    });

    it('zeigt den Stand und holt ältere Anrufe nach', async () => {
        const fetchMock = stubbe();
        zeige();
        expect(await screen.findByText('verbunden')).toBeInTheDocument();
        expect(screen.getByLabelText('Zeitraum (Tage)')).toHaveValue('365');
        fireEvent.change(screen.getByLabelText('Zeitraum (Tage)'), { target: { value: '90' } });
        fireEvent.click(screen.getByRole('button', { name: /Ältere Anrufe nachholen/ }));
        expect(await screen.findByText('120 Anrufe und 4 Nachrichten nachgeholt, 2 nachträglich zugeordnet.')).toBeInTheDocument();
        const [url] = aufrufe(fetchMock, '/api/telefon/admin/nachholen', 'POST')[0];
        expect(new URL(String(url), 'http://localhost').searchParams.get('tage')).toBe('90');
    });

    it('lehnt beim Nachholen mehr als 999 Tage ab, wie das Backend', async () => {
        const fetchMock = stubbe();
        zeige();
        await screen.findByText('verbunden');
        fireEvent.change(screen.getByLabelText('Zeitraum (Tage)'), { target: { value: '1000' } });
        fireEvent.click(screen.getByRole('button', { name: /Ältere Anrufe nachholen/ }));
        expect(await screen.findByText('Bitte eine ganze Zahl zwischen 1 und 999 Tagen angeben.')).toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/telefon/admin/nachholen', 'POST')).toHaveLength(0);
    });

    it('zeigt die Kurzanleitung für die FRITZ!Box', async () => {
        stubbe();
        zeige();
        const anleitung = await screen.findByRole('complementary', { name: 'So richten Sie die FRITZ!Box ein' });
        expect(within(anleitung).getByText('#96*5*')).toBeInTheDocument();
        expect(within(anleitung).getByText(/Zugriff für Anwendungen zulassen/)).toBeInTheDocument();
    });
});
