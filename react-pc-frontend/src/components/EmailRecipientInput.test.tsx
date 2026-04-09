import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { EmailRecipientInput } from './EmailRecipientInput';

// Mock fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Mock createPortal to render inline (so we can test dropdown content)
vi.mock('react-dom', async () => {
    const actual = await vi.importActual('react-dom');
    return {
        ...actual,
        createPortal: (node: React.ReactNode) => node,
    };
});

const sampleContacts = [
    { id: 'KUNDE_1', name: 'Max Mustermann', email: 'max@example.com', type: 'KUNDE', context: 'K-001' },
    { id: 'LIEFERANT_2', name: 'Muster GmbH', email: 'info@muster-gmbh.example.com', type: 'LIEFERANT', context: 'Stahl' },
    { id: 'PROJEKT_3', name: 'Test Firma', email: 'projekt@example.com', type: 'PROJEKT', context: 'Testprojekt' },
];

function renderInput(props: Partial<Parameters<typeof EmailRecipientInput>[0]> = {}) {
    const defaultProps = {
        value: '',
        onChange: vi.fn(),
        suggestions: [] as string[],
        placeholder: 'E-Mail eingeben...',
    };
    return {
        ...render(<EmailRecipientInput {...defaultProps} {...props} />),
        onChange: (props.onChange ?? defaultProps.onChange) as ReturnType<typeof vi.fn>,
    };
}

describe('EmailRecipientInput', () => {
    beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        mockFetch.mockReset();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('rendert das Eingabefeld mit Platzhaltertext', () => {
        renderInput({ placeholder: 'Name, Firma oder E-Mail eingeben' });
        expect(screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben')).toBeInTheDocument();
    });

    it('ruft onChange beim Tippen auf', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const onChange = vi.fn();
        renderInput({ onChange });

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await user.type(input, 'test');

        expect(onChange).toHaveBeenCalled();
    });

    it('zeigt verknüpfte E-Mail-Adressen als Vorschläge', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        renderInput({
            value: '',
            suggestions: ['test@example.com', 'info@example.com'],
        });

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await user.click(input);

        expect(screen.getByText('Verknüpfte E-Mail-Adressen')).toBeInTheDocument();
        expect(screen.getByText('test@example.com')).toBeInTheDocument();
        expect(screen.getByText('info@example.com')).toBeInTheDocument();
    });

    it('startet Server-Suche bei >= 2 Zeichen nach Debounce', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => sampleContacts,
        });

        const { rerender } = render(
            <EmailRecipientInput value="ma" onChange={vi.fn()} />
        );

        // Fokus setzen damit isOpen=true
        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });

        // Debounce abwarten (250ms)
        await act(async () => { vi.advanceTimersByTime(300); });

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/emails/contacts?q=ma',
            expect.objectContaining({ signal: expect.any(AbortSignal) })
        );
    });

    it('zeigt keine Server-Suche bei < 2 Zeichen', async () => {
        render(<EmailRecipientInput value="m" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        expect(mockFetch).not.toHaveBeenCalled();
    });

    it('zeigt Suchergebnisse mit Kontakttypen an', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => sampleContacts,
        });

        render(<EmailRecipientInput value="muster" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Suchergebnisse')).toBeInTheDocument();
        });

        expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText('max@example.com')).toBeInTheDocument();
        expect(screen.getByText('Kunde')).toBeInTheDocument();
        expect(screen.getByText('Muster GmbH')).toBeInTheDocument();
        expect(screen.getByText('Lieferant')).toBeInTheDocument();
    });

    it('wählt einen Kontakt aus der Dropdown-Liste aus', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const onChange = vi.fn();

        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => sampleContacts,
        });

        render(<EmailRecipientInput value="muster" onChange={onChange} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        });

        // Klick auf Kontakt soll onChange mit der E-Mail aufrufen
        await user.click(screen.getByText('max@example.com'));
        expect(onChange).toHaveBeenCalledWith('max@example.com');
    });

    it('wählt verknüpfte E-Mail per Klick aus', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const onChange = vi.fn();

        renderInput({
            value: '',
            onChange,
            suggestions: ['stamm@example.com'],
        });

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await user.click(input);

        await user.click(screen.getByText('stamm@example.com'));
        expect(onChange).toHaveBeenCalledWith('stamm@example.com');
    });

    it('zeigt "Keine Kontakte gefunden" bei leeren Ergebnissen', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => [],
        });

        render(<EmailRecipientInput value="xyznonexistent" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Keine Kontakte gefunden')).toBeInTheDocument();
        });
    });

    it('schließt Dropdown bei Escape-Taste', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

        renderInput({
            value: '',
            suggestions: ['escape@example.com'],
        });

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await user.click(input);
        expect(screen.getByText('escape@example.com')).toBeInTheDocument();

        await user.keyboard('{Escape}');
        expect(screen.queryByText('Verknüpfte E-Mail-Adressen')).not.toBeInTheDocument();
    });

    it('navigiert mit Pfeiltasten und wählt mit Enter', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const onChange = vi.fn();

        renderInput({
            value: '',
            onChange,
            suggestions: ['first@example.com', 'second@example.com'],
        });

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await user.click(input);

        // Pfeil nach unten → erstes Element markiert
        await user.keyboard('{ArrowDown}');
        // Nochmal nach unten → zweites Element
        await user.keyboard('{ArrowDown}');
        // Enter → zweites Element auswählen
        await user.keyboard('{Enter}');

        expect(onChange).toHaveBeenCalledWith('second@example.com');
    });

    it('dedupliziert verknüpfte and Server-Kontakte', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => [
                { id: 'KUNDE_1', name: 'Duplicate', email: 'shared@example.com', type: 'KUNDE', context: '' },
            ],
        });

        render(
            <EmailRecipientInput
                value="shared"
                onChange={vi.fn()}
                suggestions={['shared@example.com']}
            />
        );

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            // Die E-Mail soll nur einmal erscheinen (verknüpft), nicht duppliziert in Suchergebnissen
            const emails = screen.getAllByText('shared@example.com');
            expect(emails).toHaveLength(1);
        });
    });

    it('bricht vorherige Suche bei neuer Eingabe ab', async () => {
        let callCount = 0;
        mockFetch.mockImplementation(async (_url: string, opts?: { signal?: AbortSignal }) => {
            callCount++;
            if (callCount === 1) {
                // Erste Anfrage — soll abgebrochen werden
                return new Promise((_resolve, reject) => {
                    opts?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
                });
            }
            return {
                ok: true,
                json: async () => sampleContacts,
            };
        });

        const { rerender } = render(
            <EmailRecipientInput value="mu" onChange={vi.fn()} />
        );

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        // Zweiter Suchbegriff
        rerender(<EmailRecipientInput value="mus" onChange={vi.fn()} />);
        await act(async () => { vi.advanceTimersByTime(300); });

        // Beide Aufrufe sollten gestartet worden sein
        expect(mockFetch).toHaveBeenCalledTimes(2);
    });

    it('zeigt Lade-Indikator während der Suche', async () => {
        mockFetch.mockImplementation(() => new Promise(() => {})); // hängende Anfrage

        render(<EmailRecipientInput value="loading" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Suche läuft...')).toBeInTheDocument();
        });
    });

    it('zeigt Treffer-Anzahl im Footer', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => sampleContacts,
        });

        render(<EmailRecipientInput value="muster" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText(/3 Treffer/)).toBeInTheDocument();
        });
    });

    it('encodet Suchparameter in der URL', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => [],
        });

        render(<EmailRecipientInput value="test&foo=bar" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/emails/contacts?q=test%26foo%3Dbar',
            expect.any(Object)
        );
    });

    it('zeigt alle Kontakttypen mit korrekten Labels', async () => {
        const allTypes = [
            { id: 'KUNDE_1', name: 'Kunde Test', email: 'kunde@example.com', type: 'KUNDE', context: '' },
            { id: 'LIEFERANT_1', name: 'Liefer Test', email: 'liefer@example.com', type: 'LIEFERANT', context: '' },
            { id: 'PROJEKT_1', name: 'Projekt Test', email: 'projekt@example.com', type: 'PROJEKT', context: '' },
            { id: 'ANFRAGE_1', name: 'Anfrage Test', email: 'anfrage@example.com', type: 'ANFRAGE', context: '' },
        ];

        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => allTypes,
        });

        render(<EmailRecipientInput value="test" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Kunde')).toBeInTheDocument();
            expect(screen.getByText('Lieferant')).toBeInTheDocument();
            expect(screen.getByText('Projekt')).toBeInTheDocument();
            expect(screen.getByText('Anfrage')).toBeInTheDocument();
        });
    });

    it('zeigt Kontext-Information (z.B. Bauvorhaben)', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => [
                { id: 'PROJEKT_5', name: 'Bauherr GmbH', email: 'bau@example.com', type: 'PROJEKT', context: 'Neubau Musterstraße 1' },
            ],
        });

        render(<EmailRecipientInput value="bauherr" onChange={vi.fn()} />);

        const input = screen.getByPlaceholderText('E-Mail eingeben...');
        await act(async () => { input.focus(); });
        await act(async () => { vi.advanceTimersByTime(300); });

        await waitFor(() => {
            expect(screen.getByText('Neubau Musterstraße 1')).toBeInTheDocument();
        });
    });

    it('zeigt Entfernen-Button wenn onRemove gesetzt', async () => {
        const onRemove = vi.fn();
        renderInput({ onRemove });

        // X-Button sollte vorhanden sein (für CC-Entfernung)
        const removeButton = screen.getByRole('button');
        expect(removeButton).toBeInTheDocument();
    });
});
