import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { LieferantDokumentImportModal } from './LieferantDokumentImportModal';
import { KI_AUS_ERKLAERUNG } from '../features/ki/useKiAktiv';
import { antwort, stubbeFetch } from '../features/telefon/telefonTestdaten';
import { benutzer } from '../features/ki/kiTestdaten';

/**
 * Dokument-Import beim Lieferanten ohne KI-Schlüssel: E-Rechnungen liest das
 * Backend weiter selbst, alles andere kommt leer zurück (Quelle „KEINE“) und
 * wird von Hand eingetragen. Nur Dummy-Daten (DSGVO).
 */

/** Antwort des Backends, wenn ohne KI nichts gelesen werden konnte (LieferantenController-Fallback). */
const NICHTS_GELESEN = [{
    pageRange: 'alle',
    splitPdfBase64: null,
    analyzeResponse: { dokumentTyp: 'RECHNUNG', dokumentDatum: '2026-10-11', aiConfidence: 0, analyseQuelle: 'KEINE' },
}];

function Angemeldet() {
    return <span>{useAuth().user?.displayName}</span>;
}

async function zeige(kiAktiv: boolean) {
    stubbeFetch(
        (url) => (url.pathname === '/api/auth/me' ? antwort(benutzer({ kiAktiv })) : undefined),
        (url) => (url.pathname === '/api/lieferanten/1/dokumente/analyze' ? antwort(NICHTS_GELESEN) : undefined),
    );
    render(
        <AuthProvider>
            <Angemeldet />
            <LieferantDokumentImportModal isOpen onClose={vi.fn()} onSuccess={vi.fn()} lieferantId={1} />
        </AuthProvider>,
    );
    await screen.findByText('Max Mustermann');
}

function waehleDatei() {
    const datei = new File(['<rechnung />'], 'rechnung-musterlieferant.xml', { type: 'text/xml' });
    fireEvent.change(screen.getByLabelText('Dokumentdatei auswählen'), { target: { files: [datei] } });
}

afterEach(() => vi.unstubAllGlobals());

describe('LieferantDokumentImportModal – ohne KI-Schlüssel', () => {
    it('verspricht keine KI-Analyse, sondern nennt E-Rechnungen und das Eintragen von Hand', async () => {
        await zeige(false);

        expect(screen.getByText(/E-Rechnungen werden automatisch gelesen, bei allen anderen Dokumenten tragen Sie die Angaben von Hand ein/)).toBeInTheDocument();
        expect(screen.queryByText(/per KI analysiert/)).not.toBeInTheDocument();
    });

    it('lässt die Datei trotzdem hochladen und erklärt, warum nichts ausgefüllt ist', async () => {
        await zeige(false);
        waehleDatei();

        const hinweis = await screen.findByRole('note');
        expect(hinweis).toHaveTextContent('Ohne KI liest das Programm nur E-Rechnungen automatisch');
        expect(hinweis).toHaveTextContent(KI_AUS_ERKLAERUNG);
        // Kein Technik-Wort „KEINE“ neben dem Hinweis
        expect(screen.queryByText(/Quelle:/)).not.toBeInTheDocument();
        // Von Hand ausfüllen und speichern bleibt möglich.
        expect(screen.getByRole('button', { name: /Importieren|Speichern/ })).toBeInTheDocument();
    });
});

describe('LieferantDokumentImportModal – mit KI-Schlüssel', () => {
    it('kündigt die KI-Analyse an und zeigt keinen KI-aus-Hinweis', async () => {
        await zeige(true);

        expect(screen.getByText(/Die Daten werden automatisch per KI analysiert/)).toBeInTheDocument();
        waehleDatei();
        expect(await screen.findByText('Dokumenttyp')).toBeInTheDocument();
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
    });
});
