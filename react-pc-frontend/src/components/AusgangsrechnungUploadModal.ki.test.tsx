import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { AusgangsrechnungUploadModal } from './AusgangsrechnungUploadModal';
import { antwort, stubbeFetch } from '../features/telefon/telefonTestdaten';
import { benutzer } from '../features/ki/kiTestdaten';

/**
 * Ausgangsrechnung von Hand erfassen ohne KI-Schlüssel: Das Backend liest nur
 * E-Rechnungen selbst und meldet sonst „bitte manuell ausfüllen“
 * (OffenePostenController). Nur Dummy-Daten (DSGVO).
 */

function Angemeldet() {
    return <span>{useAuth().user?.displayName}</span>;
}

async function zeige(kiAktiv: boolean) {
    stubbeFetch(
        (url) => (url.pathname === '/api/auth/me' ? antwort(benutzer({ kiAktiv })) : undefined),
        (url) => (url.pathname === '/api/offene-posten/ausgang/analyze'
            ? antwort({ error: 'Analyse fehlgeschlagen - bitte manuell ausfüllen' })
            : undefined),
        (url) => (url.pathname === '/api/projekte/simple' ? antwort([]) : undefined),
    );
    const { container } = render(
        <AuthProvider>
            <Angemeldet />
            <AusgangsrechnungUploadModal isOpen onClose={vi.fn()} onSuccess={vi.fn()} />
        </AuthProvider>,
    );
    await screen.findByText('Max Mustermann');
    return container;
}

function waehleDatei() {
    const eingabe = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    const datei = new File(['%PDF-1.4'], 'rechnung-2026-001.pdf', { type: 'application/pdf' });
    fireEvent.change(eingabe, { target: { files: [datei] } });
}

beforeEach(() => {
    // jsdom kennt keine Blob-Adressen – die PDF-Vorschau braucht nur irgendeine.
    URL.createObjectURL = vi.fn(() => 'blob:vorschau');
    URL.revokeObjectURL = vi.fn();
});
afterEach(() => vi.unstubAllGlobals());

describe('AusgangsrechnungUploadModal – ohne KI-Schlüssel', () => {
    it('sagt vor dem Hochladen, dass nur E-Rechnungen automatisch gelesen werden', async () => {
        await zeige(false);

        expect(screen.getByText('E-Rechnungen werden automatisch gelesen, bei allen anderen Rechnungen tragen Sie die Angaben von Hand ein.')).toBeInTheDocument();
    });

    it('erklärt ruhig, warum nichts ausgefüllt ist, und lässt das Formular offen', async () => {
        await zeige(false);
        waehleDatei();

        expect(await screen.findByText(/Ohne KI liest das Programm nur E-Rechnungen automatisch/)).toBeInTheDocument();
        expect(screen.queryByText('Analyse fehlgeschlagen - bitte manuell ausfüllen')).not.toBeInTheDocument();
        expect(screen.queryByText('KI analysiert Dokument...')).not.toBeInTheDocument();
    });
});

describe('AusgangsrechnungUploadModal – mit KI-Schlüssel', () => {
    it('zeigt die Meldung des Servers unverändert und keinen E-Rechnungs-Hinweis', async () => {
        await zeige(true);
        expect(screen.queryByText(/E-Rechnungen werden automatisch gelesen/)).not.toBeInTheDocument();

        waehleDatei();

        expect(await screen.findByText('Analyse fehlgeschlagen - bitte manuell ausfüllen')).toBeInTheDocument();
    });
});
