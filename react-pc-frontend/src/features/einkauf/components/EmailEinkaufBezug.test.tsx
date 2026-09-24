import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmailEinkaufBezug } from './EmailEinkaufBezug';
import { ToastProvider } from '../../../components/ui/toast';
import userEvent from '@testing-library/user-event';
import { waitFor } from '@testing-library/react';
import type { EmailItem } from '../../email/emailCenterModel';

describe('EmailEinkaufBezug', () => {
    beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 }))));
    afterEach(() => vi.unstubAllGlobals());
    it('verlinkt eine zugeordnete Anfrage und zeigt parallel den Lieferanten', () => {
        const email: EmailItem = { id: 4, type: 'email', direction: 'IN', kontoId: 'EINKAUF', einkaufTyp: 'ANFRAGE', einkaufVorgangId: 17, einkaufNummer: 'PA-17', lieferantName: 'Musterlieferant' };
        render(<ToastProvider><EmailEinkaufBezug email={email} /></ToastProvider>);
        expect(screen.getByRole('link', { name: 'PA-17' })).toHaveAttribute('href', '/einkaufsanfragen/17');
        expect(screen.getByText('Lieferant: Musterlieferant')).toBeInTheDocument();
    });

    it('verlinkt eine zugeordnete Bestellung', () => {
        render(<ToastProvider><EmailEinkaufBezug email={{ id: 4, type: 'email', direction: 'IN', kontoId: 'EINKAUF', einkaufTyp: 'BESTELLUNG', einkaufVorgangId: 73, einkaufNummer: 'B-73' }} /></ToastProvider>);
        expect(screen.getByRole('link', { name: 'B-73' })).toHaveAttribute('href', '/bestellungen/73');
    });

    it('fordert bei offener Zuordnung sichtbar zur Prüfung auf', () => {
        render(<ToastProvider><EmailEinkaufBezug email={{ id: 5, type: 'email', direction: 'IN', kontoId: 'EINKAUF', zuordnungPruefen: true }} /></ToastProvider>);
        expect(screen.getByText('Zuordnung prüfen')).toBeInTheDocument();
    });

    it('bietet den versionsgebundenen Wiederholungsversuch für sicher fehlgeschlagenen Versand an', async () => {
        const calls: { url: string; init?: RequestInit }[] = [];
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input); calls.push({ url, init });
            if (init?.method === 'POST') return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
            return new Response(JSON.stringify({ id: 910, version: 3, status: 'FEHLGESCHLAGEN' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }));
        const user = userEvent.setup();
        render(<ToastProvider><EmailEinkaufBezug email={{ id: 55, type: 'email', direction: 'IN', kontoId: 'EINKAUF' }} /></ToastProvider>);
        await user.click(await screen.findByRole('button', { name: 'Erneut versuchen' }));
        await waitFor(() => expect(calls.some(call => call.url === '/api/einkauf/mail/55/antworten/910/erneut')).toBe(true));
        expect(JSON.parse(String(calls.find(call => call.init?.method === 'POST')?.init?.body))).toEqual({ version: 3 });
    });
});
