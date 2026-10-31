import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../auth/AuthContext';
import { AiButton } from './ai-button';
import { KI_AUS_ERKLAERUNG } from '../../features/ki/useKiAktiv';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { benutzer } from '../../features/ki/kiTestdaten';

function stubbeKi(kiAktiv: boolean, kiFunktionen?: string[]) {
    return stubbeFetch((url) => (url.pathname === '/api/auth/me'
        ? antwort(benutzer({ kiAktiv, ...(kiFunktionen ? { kiFunktionen } : {}) }))
        : undefined));
}

/** Zeigt den Namen, sobald die Anmeldung geladen ist – so wartet der Test auf den echten Stand. */
function Angemeldet() {
    return <span>{useAuth().user?.displayName}</span>;
}

async function zeige(props: Partial<React.ComponentProps<typeof AiButton>> = {}) {
    const onClick = vi.fn();
    render(<AuthProvider><Angemeldet /><AiButton funktion="EMAIL_TEXT" onClick={onClick} {...props} /></AuthProvider>);
    await screen.findByText('Max Mustermann');
    return onClick;
}

const knopf = () => screen.getByRole('button', { name: /KI-Optimierung/ });

afterEach(() => vi.unstubAllGlobals());

describe('AiButton', () => {
    it('ist ohne KI-Schlüssel gesperrt und erklärt warum', async () => {
        stubbeKi(false);
        const onClick = await zeige();

        expect(knopf()).toBeDisabled();
        expect(knopf()).toHaveAccessibleDescription(KI_AUS_ERKLAERUNG);
        // Tooltip an der Hülle – ein gesperrter Knopf bekommt nicht in jedem Browser ein Hover.
        expect(knopf().parentElement).toHaveAttribute('title', KI_AUS_ERKLAERUNG);

        fireEvent.click(knopf());
        expect(onClick).not.toHaveBeenCalled();
    });

    it('ist gesperrt, wenn zwar ein Schlüssel da ist, aber gerade seine Funktion aus ist', async () => {
        // Z. B. nur ein Mistral-Schlüssel bei gewähltem Gemini und abgeschaltetem Ersatz.
        stubbeKi(true, ['DOKUMENT_ANALYSE']);
        const onClick = await zeige();

        expect(knopf()).toBeDisabled();
        expect(knopf()).toHaveAccessibleDescription(KI_AUS_ERKLAERUNG);
        fireEvent.click(knopf());
        expect(onClick).not.toHaveBeenCalled();
    });

    it('nennt im Erklärtext, wo ein Admin die KI einschaltet', () => {
        expect(KI_AUS_ERKLAERUNG).toContain('Einstellungen → KI-Funktionen');
    });

    it('ist mit KI-Schlüssel klickbar und ohne Zusatzerklärung', async () => {
        stubbeKi(true);
        const onClick = await zeige();

        expect(knopf()).toBeEnabled();
        expect(knopf()).not.toHaveAccessibleDescription();
        await userEvent.click(knopf());
        expect(onClick).toHaveBeenCalledTimes(1);
    });

    it('bleibt während des Ladens gesperrt, auch wenn die KI an ist', async () => {
        stubbeKi(true);
        await zeige({ isLoading: true });

        // Gesperrt wegen isLoading – nicht wegen eines fehlenden Schlüssels.
        expect(knopf()).toBeDisabled();
        expect(knopf()).not.toHaveAccessibleDescription();
    });

    it('übernimmt die eigene Beschriftung auch im gesperrten Zustand', async () => {
        stubbeKi(false);
        await zeige({ label: 'KI Formulierung' });

        expect(screen.getByRole('button', { name: /KI Formulierung/ })).toBeDisabled();
    });

    it('funktioniert ohne Anmelde-Rahmen wie bisher (KI gilt als an)', async () => {
        const onClick = vi.fn();
        render(<AiButton funktion="EMAIL_TEXT" onClick={onClick} />);

        expect(knopf()).toBeEnabled();
        await userEvent.click(knopf());
        expect(onClick).toHaveBeenCalledTimes(1);
    });
});
