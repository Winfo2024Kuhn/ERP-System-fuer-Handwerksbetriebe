import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EmailFolderSidebar } from './EmailFolderSidebar';

const counts = {
    inbox: 3, sent: 0, drafts: 1, starred: 0, newsletter: 0, spam: 120, trash: 0,
    unassigned: 5, projects: 0, offers: 0, suppliers: 0, taxAdvisors: 7,
} as never;

function renderSidebar(overrides: Record<string, unknown> = {}) {
    const props = {
        width: 240, collapsed: false, resizing: false, activeFolder: 'inbox', counts, dragActive: false,
        showSettings: false, onToggle: vi.fn(), onCompose: vi.fn(), onSelect: vi.fn(), onSettings: vi.fn(),
        ...overrides,
    };
    render(<EmailFolderSidebar {...(props as never)} />);
    return props as unknown as Record<'onToggle' | 'onCompose' | 'onSelect' | 'onSettings', ReturnType<typeof vi.fn>>;
}

describe('EmailFolderSidebar', () => {
    it('zeigt Postfach- und zugeordnete Ordner mit Zaehlern', () => {
        renderSidebar();
        const nav = screen.getByRole('navigation', { name: 'E-Mail-Ordner' });
        for (const name of ['Posteingang', 'Gesendet', 'Entwürfe', 'Markiert', 'Newsletter', 'Spam', 'Papierkorb',
            'Nicht zugeordnet', 'Projekte', 'Anfragen', 'Lieferanten', 'Steuerberater']) {
            expect(within(nav).getByRole('button', { name })).toBeInTheDocument();
        }
        expect(within(screen.getByRole('button', { name: 'Posteingang' })).getByText('3')).toBeInTheDocument();
        // Steuerberater nutzt den Zaehler taxAdvisors
        expect(within(screen.getByRole('button', { name: 'Steuerberater' })).getByText('7')).toBeInTheDocument();
        // Nullwerte werden nicht angezeigt
        expect(within(screen.getByRole('button', { name: 'Gesendet' })).queryByText('0')).toBeNull();
    });

    it('markiert nur den aktiven Ordner mit aria-current, nicht bei geoeffneten Einstellungen', () => {
        renderSidebar({ activeFolder: 'spam' });
        expect(screen.getByRole('button', { name: 'Spam' })).toHaveAttribute('aria-current', 'page');
        expect(screen.getByRole('button', { name: 'Posteingang' })).not.toHaveAttribute('aria-current');
    });

    it('ohne Aktiv-Markierung, wenn die Einstellungen offen sind', () => {
        renderSidebar({ showSettings: true });
        expect(screen.getByRole('button', { name: 'Posteingang' })).not.toHaveAttribute('aria-current');
    });

    it('Klicks loesen die passenden Callbacks aus (tax-advisors als Ordner-ID)', async () => {
        const user = userEvent.setup();
        const p = renderSidebar();
        await user.click(screen.getByRole('button', { name: 'Steuerberater' }));
        expect(p.onSelect).toHaveBeenCalledWith('tax-advisors');
        await user.click(screen.getByRole('button', { name: 'Neue E-Mail' }));
        expect(p.onCompose).toHaveBeenCalledTimes(1);
        await user.click(screen.getByRole('button', { name: 'Einstellungen' }));
        expect(p.onSettings).toHaveBeenCalledTimes(1);
        await user.click(screen.getByRole('button', { name: 'Ordnerleiste einklappen' }));
        expect(p.onToggle).toHaveBeenCalledTimes(1);
    });

    it('Gruppe "Zugeordnet" laesst sich ein- und ausklappen', async () => {
        const user = userEvent.setup();
        renderSidebar();
        const toggle = screen.getByRole('button', { name: /Zugeordnet/ });
        expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await user.click(toggle);
        expect(toggle).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByRole('button', { name: 'Projekte' })).toBeNull();
        await user.click(toggle);
        expect(screen.getByRole('button', { name: 'Projekte' })).toBeInTheDocument();
    });

    it('eingeklappt: keine Beschriftungen, kein Gruppenkopf, Zaehler >99 als 99+, Toggle-Label wechselt', () => {
        renderSidebar({ collapsed: true });
        expect(screen.getByRole('button', { name: 'Ordnerleiste ausklappen' })).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByText('E-Mail Center')).toBeNull();
        expect(screen.queryByRole('button', { name: /Zugeordnet/ })).toBeNull();
        // zugeordnete Ordner bleiben trotzdem erreichbar
        expect(screen.getByRole('button', { name: 'Projekte' })).toBeInTheDocument();
        expect(within(screen.getByRole('button', { name: 'Spam' })).getByText('99+')).toBeInTheDocument();
        expect(screen.queryByText('Neue E-Mail')).toBeNull();
    });

    it('uebernimmt die Breite', () => {
        renderSidebar({ width: 333 });
        expect(screen.getByRole('navigation')).toHaveStyle({ width: '333px' });
    });
});
