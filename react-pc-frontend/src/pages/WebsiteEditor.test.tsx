import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebsiteEditor } from './WebsiteEditor';

beforeEach(() => {
    // Die Tab-Inhalte laden spaeter selbst nach; hier genuegt eine leere Antwort.
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
        ok: true, status: 200, json: () => Promise.resolve([]),
    })));
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('WebsiteEditor', () => {
    it('zeigt die Kopfzeile mit Kategorie und Titel', () => {
        render(<WebsiteEditor />);

        expect(screen.getByText('Website')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'NEUIGKEITEN' })).toBeInTheDocument();
    });

    it('startet auf dem Tab Beitrag erstellen', () => {
        render(<WebsiteEditor />);

        expect(screen.getByTestId('tab-beitraege')).toBeInTheDocument();
        expect(screen.queryByTestId('tab-insights')).not.toBeInTheDocument();
    });

    it('wechselt auf Insights und wieder zurueck', async () => {
        const user = userEvent.setup();
        render(<WebsiteEditor />);

        await user.click(screen.getByRole('button', { name: /Zahlen der Website/ }));
        expect(screen.getByTestId('tab-insights')).toBeInTheDocument();
        expect(screen.queryByTestId('tab-beitraege')).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Beitrag erstellen/ }));
        expect(screen.getByTestId('tab-beitraege')).toBeInTheDocument();
    });
});
