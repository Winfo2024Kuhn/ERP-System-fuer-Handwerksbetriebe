import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { PostfachSichtbarkeit } from './PostfachSichtbarkeit';
import { useNachladeListe } from './useNachladeListe';
import { ToastProvider } from '../../../ui/toast';
import {
    parseSichtbarkeitsAbteilungen, parseSichtbarkeitsBenutzer, type SichtbarkeitsAuswahl,
} from '../../../../features/email/postfach';

/** Häkchen-Listen von „Wer darf es sehen?“ im Detail. Nur Dummy-Daten (DSGVO). */

const ABTEILUNGEN = [
    { abteilungId: 3, abteilungName: 'Werkstatt' },
    { abteilungId: 2, abteilungName: 'Büro' },
];
const BENUTZER = [
    { id: 7, displayName: 'Max Mustermann', active: true },
    { id: 11, displayName: 'Moritz Muster', active: false },
];

type Antwort = { status?: number; body?: unknown };

function stubFetch(handler: (url: string) => Antwort | Promise<Antwort> | undefined = () => undefined) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const antwort = await handler(url)
            ?? (url === '/api/abteilungen/berechtigungen' ? { body: ABTEILUNGEN }
                : url === '/api/frontend-users' ? { body: BENUTZER } : { status: 404, body: {} });
        return new Response(JSON.stringify(antwort.body ?? {}), { status: antwort.status ?? 200 });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

interface RahmenProps {
    start: SichtbarkeitsAuswahl;
    onChange?: (wert: SichtbarkeitsAuswahl) => void;
    bekannteAbteilungen?: { id: number; name: string }[];
    bekannteBenutzer?: { id: number; displayName: string }[];
    disabled?: boolean;
}

/** Wie die Karte: Listen laden, sobald „Nur bestimmte“ gewählt ist. */
function Rahmen({ start, onChange, ...rest }: RahmenProps) {
    const [wert, setWert] = useState(start);
    const abteilungen = useNachladeListe('/api/abteilungen/berechtigungen', !wert.sichtbarFuerAlle,
        parseSichtbarkeitsAbteilungen, 'Abteilungen konnten nicht geladen werden.');
    const benutzer = useNachladeListe('/api/frontend-users', !wert.sichtbarFuerAlle,
        parseSichtbarkeitsBenutzer, 'Benutzer konnten nicht geladen werden.');
    return (
        <PostfachSichtbarkeit postfachName="max@musterbetrieb.example" wert={wert} abteilungen={abteilungen} benutzer={benutzer}
            onChange={(neu) => { setWert(neu); onChange?.(neu); }} {...rest} />
    );
}
const renderListe = (props: RahmenProps) => render(<ToastProvider><Rahmen {...props} /></ToastProvider>);
const leer: SichtbarkeitsAuswahl = { sichtbarFuerAlle: false, abteilungIds: [], benutzerIds: [] };

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('PostfachSichtbarkeit', () => {
    it('benennt die Auswahl für Screenreader nach dem Postfach', () => {
        stubFetch();
        renderListe({ start: { ...leer, sichtbarFuerAlle: true } });
        expect(screen.getByRole('group', { name: 'Wer darf max@musterbetrieb.example sehen?' })).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Abteilungen' })).not.toBeInTheDocument();
    });

    it('durchsucht lange Listen und sagt, wenn nichts passt', async () => {
        const user = userEvent.setup();
        const viele = Array.from({ length: 8 }, (_, i) => ({ id: 100 + i, displayName: `Monteur ${i + 1}`, active: true }));
        stubFetch((url) => (url === '/api/frontend-users' ? { body: [...viele, BENUTZER[0]] } : undefined));
        renderListe({ start: leer });

        const benutzer = screen.getByRole('group', { name: 'Benutzer' });
        const suche = await within(benutzer).findByRole('searchbox', { name: 'Benutzer durchsuchen' });
        expect(within(screen.getByRole('group', { name: 'Abteilungen' })).queryByRole('searchbox')).not.toBeInTheDocument();
        await user.type(suche, 'muster');
        expect(within(benutzer).getAllByRole('checkbox')).toHaveLength(1);
        await user.clear(suche);
        await user.type(suche, 'Erika');
        expect(within(benutzer).getByText('Kein Treffer für „Erika“.')).toBeInTheDocument();
    });

    it('zeigt angehakte ausgeschaltete und nicht mehr gelieferte Einträge, damit man sie abwählen kann', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        stubFetch();
        renderListe({
            start: { sichtbarFuerAlle: false, abteilungIds: [99], benutzerIds: [11, 77] },
            bekannteAbteilungen: [{ id: 99, name: 'Alte Abteilung' }, { id: 2, name: 'Büro' }],
            bekannteBenutzer: [{ id: 77, displayName: 'Gelöschter Benutzer' }, { id: 11, displayName: 'Moritz Muster' }],
            onChange,
        });

        const abteilungen = screen.getByRole('group', { name: 'Abteilungen' });
        expect(await within(abteilungen).findByRole('checkbox', { name: 'Alte Abteilung' })).toBeChecked();
        expect(within(abteilungen).getAllByRole('checkbox', { name: 'Büro' })).toHaveLength(1);
        const benutzer = screen.getByRole('group', { name: 'Benutzer' });
        expect(await within(benutzer).findByRole('checkbox', { name: /Moritz Muster/ })).toBeChecked();
        expect(within(benutzer).getByText('(ausgeschaltet)')).toBeInTheDocument();
        expect(within(benutzer).getByRole('checkbox', { name: 'Gelöschter Benutzer' })).toBeChecked();

        await user.click(within(abteilungen).getByRole('checkbox', { name: 'Alte Abteilung' }));
        expect(onChange).toHaveBeenLastCalledWith({ sichtbarFuerAlle: false, abteilungIds: [], benutzerIds: [11, 77] });
    });

    it('erklärt leere Listen', async () => {
        stubFetch(() => ({ body: [] }));
        renderListe({ start: leer });
        expect(await screen.findByText('Noch keine Abteilungen angelegt.')).toBeInTheDocument();
        expect(await screen.findByText('Noch keine Benutzer angelegt.')).toBeInTheDocument();
        expect(screen.getAllByText('keine gewählt')).toHaveLength(2);
    });

    it('sperrt alles, solange gespeichert wird', async () => {
        stubFetch();
        renderListe({ start: { ...leer, abteilungIds: [2] }, disabled: true });
        expect(screen.getByRole('radio', { name: /Alle im Betrieb/ })).toBeDisabled();
        expect(await screen.findByRole('checkbox', { name: 'Büro' })).toBeDisabled();
    });

    it('lädt erneut, wenn vor der Antwort auf „Alle“ und zurück geschaltet wurde', async () => {
        const user = userEvent.setup();
        let freigeben: () => void = () => {};
        let erster = true;
        const fetchMock = stubFetch((url) => {
            if (url !== '/api/abteilungen/berechtigungen' || !erster) return undefined;
            erster = false;
            return new Promise<Antwort>(resolve => { freigeben = () => resolve({ body: ABTEILUNGEN }); });
        });
        renderListe({ start: leer });
        const anzahl = () => fetchMock.mock.calls.filter(([u]) => String(u) === '/api/abteilungen/berechtigungen').length;
        await waitFor(() => expect(anzahl()).toBe(1));
        // Ladezustand ist sichtbar, kein leerer Kasten.
        expect(screen.getByLabelText('Abteilungen werden geladen')).toHaveAttribute('aria-busy', 'true');

        await user.click(screen.getByRole('radio', { name: /Alle im Betrieb/ }));
        freigeben();
        await user.click(screen.getByRole('radio', { name: /Nur bestimmte/ }));
        expect(await screen.findByRole('checkbox', { name: 'Werkstatt' })).toBeInTheDocument();
        expect(anzahl()).toBe(2);
    });
});
