import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { DokumentVerlaufDrawer, type DokumentAuditEintrag } from './DokumentVerlaufDrawer';
import type { FreigabeAuditDaten } from '../../features/freigabe/freigabeAudit';

const DOK_ID = 28;

function eintrag(teil: Partial<DokumentAuditEintrag>): DokumentAuditEintrag {
    return {
        id: 1, aktion: 'ERSTELLT', dokumentId: DOK_ID, dokumentNummer: 'AB-2026/10/00028',
        typ: 'AUFTRAGSBESTAETIGUNG', betragNetto: null, betragBrutto: null, gebucht: false,
        storniert: false, digitalAngenommen: false, inhaltHash: null, geaendertVon: 'Erika Musterfrau',
        geaendertVonId: 2, geaendertAm: '2026-10-13T09:00:00', aenderungsgrund: null, ipAdresse: null,
        ...teil,
    };
}

const ANNAHME = eintrag({
    id: 2, aktion: 'DIGITAL_ANGENOMMEN', geaendertVon: null, geaendertVonId: null,
    geaendertAm: '2026-10-13T09:30:00', aenderungsgrund: 'Digitale Annahme durch Kunden',
    ipAdresse: '192.0.2.10', inhaltHash: 'c'.repeat(64), digitalAngenommen: true,
});

const AUDIT: FreigabeAuditDaten = {
    status: 'ACCEPTED', dokumentArt: 'Angebot', dokumentNummer: 'AG-2026/10/00021',
    erstelltAm: '2026-10-10T08:00:00', ablaufDatum: '2026-10-24T00:00:00', akzeptiertAm: '2026-10-13T09:30:00',
    akzeptiertEmail: 'max@example.com', akzeptiertIp: '192.0.2.10', akzeptiertUserAgent: 'Mozilla/5.0',
    unterzeichnerVorname: 'Max', unterzeichnerNachname: 'Mustermann', unterzeichnerName: 'Max Mustermann',
    hashOriginal: 'a'.repeat(64), hashAcceptance: 'b'.repeat(64),
    codeBestaetigtAm: '2026-10-13T09:29:00', codeBestaetigtEmail: 'max@example.com',
    vorzeitigerBeginn: false, widerrufStand: '2026-10-13', datenschutzStand: '2026-10-13',
    bestaetigungText: 'Ich beauftrage Angebot AG-2026/10/00021 verbindlich.', knopfText: 'Zahlungspflichtig beauftragen',
    vorzeitigerBeginnText: 'Ich verlange ausdrücklich, dass Sie mit der Ausführung schon vor Ablauf der Widerrufsfrist beginnen.',
    akzeptierterBetrag: null, mitbeauftragteAlternativen: [],
};

const fetchMock = vi.fn();

function antwort(status: number, body: unknown) {
    return Promise.resolve({ ok: status >= 200 && status < 300, status, json: async () => body });
}

function stubApi(historie: DokumentAuditEintrag[], audit: { status: number; body?: unknown } | 'netzfehler') {
    fetchMock.mockImplementation((url: string) => {
        if (url === `/api/ausgangs-dokumente/${DOK_ID}/historie`) return antwort(200, historie);
        if (url === `/api/ausgangs-dokumente/${DOK_ID}/freigabe-audit`) {
            if (audit === 'netzfehler') return Promise.reject(new TypeError('Failed to fetch'));
            return antwort(audit.status, audit.body ?? null);
        }
        return antwort(404, null);
    });
}

function zeige(onOpenChange = vi.fn()) {
    return render(
        <ToastProvider>
            <DokumentVerlaufDrawer open onOpenChange={onOpenChange} dokumentId={DOK_ID} dokumentNummer="AB-2026/10/00028" />
        </ToastProvider>,
    );
}

const auditAufrufe = () => fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/freigabe-audit')).length;

afterEach(() => { vi.unstubAllGlobals(); });

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', fetchMock);
});

describe('DokumentVerlaufDrawer', () => {
    it('zeigt beim „Digital angenommen“ den Nachweis mit Wartepflicht und den Kunden statt „System“', async () => {
        stubApi([ANNAHME, eintrag({})], { status: 200, body: AUDIT });
        zeige();

        const nachweis = (await screen.findByText('Annahme-Nachweis')).closest('div.rounded-lg') as HTMLElement;
        expect(await within(nachweis).findByText('Angebot AG-2026/10/00021')).toBeInTheDocument();
        expect(within(nachweis).getByText('Angenommen wurde')).toBeInTheDocument();
        expect(within(nachweis).getByText('Max')).toBeInTheDocument();
        expect(within(nachweis).getByText('Mustermann')).toBeInTheDocument();
        // Checkliste und voller Annahme-Hash stehen ohne Aufklappen da.
        expect(within(nachweis).getByText('Freiwilliges Häkchen')).toBeInTheDocument();
        expect(within(nachweis).getByText('nicht angehakt')).toBeInTheDocument();
        expect(within(nachweis).getByText('b'.repeat(64))).toBeInTheDocument();
        expect(within(nachweis).getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toHaveClass('bg-amber-50');
        expect(screen.getByText('Kunde: Max Mustermann')).toBeInTheDocument();
        // Der Eintrag „Erstellt“ behält seinen Bearbeiter.
        expect(screen.getByText('Erika Musterfrau')).toBeInTheDocument();
        expect(screen.queryByText('System')).not.toBeInTheDocument();
        // Typ in Klartext statt Enum-Wert.
        expect(screen.getAllByText('Auftragsbestätigung')).toHaveLength(2);
        expect(screen.queryByText('AUFTRAGSBESTAETIGUNG')).not.toBeInTheDocument();
        // Technische Angaben erst auf Klick.
        expect(within(nachweis).queryByText('Original-Hash')).not.toBeInTheDocument();
        await userEvent.click(within(nachweis).getByRole('button', { name: 'Technische Angaben anzeigen' }));
        expect(within(nachweis).getByText('Original-Hash')).toBeInTheDocument();
        expect(within(nachweis).getByRole('button', { name: 'Technische Angaben ausblenden' })).toHaveAttribute('aria-expanded', 'true');
    });

    it('zeigt grünen Sofortbeginn und erklärt den Nachweis per Info-Knopf', async () => {
        stubApi([ANNAHME], { status: 200, body: { ...AUDIT, vorzeitigerBeginn: true } });
        zeige();

        expect(await screen.findByText(/Sofort beginnen erlaubt/)).toHaveClass('bg-emerald-50');
        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Annahme-Nachweis' }));
        expect(await screen.findByText(/Die Auftragsbestätigung zeigt den Nachweis des Angebots/)).toBeInTheDocument();
        expect(screen.getByText(/jedes Häkchen mit seinem genauen Wortlaut/)).toBeInTheDocument();
        expect(screen.getByText(/vergleichen Sie den Hash hier mit dem in der Mail des Kunden/)).toBeInTheDocument();
    });

    it('nennt bei älteren Annahmen ohne Angabe ausdrücklich, dass nichts gespeichert ist', async () => {
        stubApi([ANNAHME], { status: 200, body: { ...AUDIT, vorzeitigerBeginn: null, unterzeichnerName: null } });
        zeige();

        expect(await screen.findByText(/Keine Angabe gespeichert/)).toBeInTheDocument();
        expect(screen.getByText('Kunde (online)')).toBeInTheDocument();
    });

    it('zeigt bei 404 keinen Nachweis und „Kunde (online)“', async () => {
        stubApi([ANNAHME], { status: 404 });
        zeige();

        expect(await screen.findByText('Kunde (online)')).toBeInTheDocument();
        await waitFor(() => expect(auditAufrufe()).toBe(1));
        await waitFor(() => expect(screen.queryByText('Annahme-Nachweis')).not.toBeInTheDocument());
        expect(screen.getByText(/Begründung:/)).toBeInTheDocument();
    });

    it('zeigt bei Netzfehler einen Hinweis im Eintrag und lädt beim nächsten Öffnen neu', async () => {
        stubApi([ANNAHME], 'netzfehler');
        let offen = true;
        const onOpenChange = vi.fn((wert: boolean) => { offen = wert; });
        const ansicht = (o: boolean) => (
            <ToastProvider>
                <DokumentVerlaufDrawer open={o} onOpenChange={onOpenChange} dokumentId={DOK_ID} />
            </ToastProvider>
        );
        const { rerender } = render(ansicht(offen));

        expect(await screen.findByText(/Annahme-Nachweis konnte nicht geladen werden/)).toBeInTheDocument();
        expect(screen.getByText('Digital angenommen')).toBeInTheDocument();

        await userEvent.keyboard('{Escape}');
        expect(onOpenChange).toHaveBeenCalledWith(false);
        rerender(ansicht(offen));

        stubApi([ANNAHME], { status: 200, body: AUDIT });
        rerender(ansicht(true));
        expect(await screen.findByText('Angebot AG-2026/10/00021')).toBeInTheDocument();
        expect(auditAufrufe()).toBe(2);
    });

    it('lädt keinen Nachweis ohne digitale Annahme und zeigt unbekannte Typen roh', async () => {
        stubApi([eintrag({ typ: 'SONDERBELEG', geaendertVon: null })], { status: 200, body: AUDIT });
        zeige();

        expect(await screen.findByText('SONDERBELEG')).toBeInTheDocument();
        expect(screen.getByText('System')).toBeInTheDocument();
        expect(auditAufrufe()).toBe(0);
    });

    it('zeigt den Nachweis nur am neuesten „Digital angenommen“-Eintrag', async () => {
        const aelter = { ...ANNAHME, id: 1, geaendertAm: '2026-10-01T09:30:00' };
        stubApi([aelter, ANNAHME], { status: 200, body: AUDIT });
        zeige();

        expect(await screen.findByText('Angebot AG-2026/10/00021')).toBeInTheDocument();
        expect(screen.getAllByText('Annahme-Nachweis')).toHaveLength(1);
        // Der Name aus dem Nachweis gehört nur zum neuesten Eintrag, der ältere bleibt neutral.
        expect(screen.getAllByText(`Kunde: ${AUDIT.unterzeichnerName}`)).toHaveLength(1);
        expect(screen.getAllByText('Kunde (online)')).toHaveLength(1);
    });

    it('beschriftet den Hash jedes Eintrags als Fingerabdruck des Dokuments und kopiert ihn', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
        stubApi([ANNAHME], { status: 404 });
        zeige();

        const knopf = await screen.findByRole('button', { name: 'Fingerabdruck des Dokuments: cccccccccccc…' });
        expect(knopf).toHaveAccessibleDescription(/Fingerabdruck des Dokumentinhalts zu diesem Zeitpunkt – nicht der Annahme-Hash/);
        expect(screen.queryByText(/SHA-256/)).not.toBeInTheDocument();

        await userEvent.click(knopf);
        expect(writeText).toHaveBeenCalledWith('c'.repeat(64));
        expect(await screen.findByText('Fingerabdruck des Dokuments kopiert')).toBeInTheDocument();
    });

    it('meldet, wenn das Kopieren des Fingerabdrucks scheitert', async () => {
        vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: vi.fn().mockRejectedValue(new Error('nein')) } });
        stubApi([ANNAHME], { status: 404 });
        zeige();

        await userEvent.click(await screen.findByRole('button', { name: /Fingerabdruck des Dokuments/ }));
        expect(await screen.findByText('Kopieren fehlgeschlagen')).toBeInTheDocument();
    });

    it('zeigt eine lange Begründung mit Zeilenumbrüchen umbrechbar an', async () => {
        const grund = 'Digitale Annahme durch Max Mustermann\nAnnahme-Hash: ' + 'b'.repeat(64);
        stubApi([{ ...ANNAHME, aenderungsgrund: grund }], { status: 404 });
        zeige();

        const begruendung = (await screen.findByText(/Begründung:/)).parentElement as HTMLElement;
        expect(begruendung).toHaveClass('whitespace-pre-line', 'break-words');
        expect(begruendung.textContent).toContain('b'.repeat(64));
    });
});
