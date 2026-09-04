import type { Page, Route } from '@playwright/test';

/**
 * Stubbt alle /api-Routen, die die Seite /dokument-editor beim Oeffnen und
 * beim Schliessen ueber den X-Button anfasst.
 *
 * Bewusst ohne echtes Backend, wie in e2e/hilfen/api.ts fuer "Website -
 * Neuigkeiten". Alle Namen und Texte sind Dummy-Daten (DSGVO).
 *
 * Die Seite (DocumentEditorPage.tsx) haelt ihr eigenes, aelteres Soft-Lock
 * noch selbst (useDocumentLock, /api/dokument-locks/AUSGANG/...) -- das ist
 * NICHT Teil dieses Tasks (Editor-Komponente) und wird erst in einem
 * spaeteren Abschnitt auf das neue Fundament (useDatensatzLock) umgestellt.
 * Diese Stubs bedienen es trotzdem, damit die Seite ueberhaupt laedt.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
    });
}

export interface AusgangsDokumentStand {
    id: number;
    typ: string;
    dokumentNummer: string;
    datum: string;
    betreff: string;
    betragNetto: number;
    htmlInhalt: string;
    positionenJson: string;
    kundenName: string;
    kundennummer: string;
    rechnungsadresse: string;
    rechnungsadresseOverride: string;
    gebucht: boolean;
    storniert: boolean;
    digitalAngenommen?: boolean;
    zahlungszielTage: number;
}

const HEUTE_ISO = new Date().toISOString().split('T')[0];

/** Dummy-Rechnung (DSGVO: nur Musterdaten). */
export const BEISPIEL_DOKUMENT: AusgangsDokumentStand = {
    id: 1,
    typ: 'RECHNUNG',
    dokumentNummer: 'RE-2026/09/00001',
    datum: HEUTE_ISO,
    betreff: 'Dachsanierung',
    betragNetto: 1000,
    htmlInhalt: '',
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    kundenName: 'Max Mustermann',
    kundennummer: 'K-4711',
    rechnungsadresse: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt',
    rechnungsadresseOverride: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt',
    gebucht: false,
    storniert: false,
    zahlungszielTage: 14,
};

/** Was der Stub waehrend eines Tests mitgeschrieben hat. */
export interface DokumentEditorMitschrift {
    /** Jeder POST an einen dokument-locks-Heartbeat-Pfad, mit Zeitstempel (ms seit Testbeginn). */
    heartbeatAufrufe: number[];
    /** Jeder PUT an /api/ausgangs-dokumente/{id}. */
    speicherAufrufe: Record<string, unknown>[];
}

export interface DokumentEditorStubOptionen {
    /** Antwort auf PUT /api/ausgangs-dokumente/{id}: 'ok' (Standard) oder 'fehler' (500). */
    speichern?: 'ok' | 'fehler';
}

export async function stubbeDokumentEditorApi(
    page: Page,
    optionen: DokumentEditorStubOptionen = {},
): Promise<DokumentEditorMitschrift> {
    const start = Date.now();
    const mitschrift: DokumentEditorMitschrift = { heartbeatAufrufe: [], speicherAufrufe: [] };
    let dokument: AusgangsDokumentStand = { ...BEISPIEL_DOKUMENT };

    // Generischer Fallback ZUERST registrieren -- Playwright prueft spaeter
    // registrierte Routen zuerst, dieser hier greift nur, wenn nichts
    // Spezifischeres unten passt (Textbausteine, Leistungen, Arbeitszeitarten,
    // Abrechnungsverlauf, ...).
    await page.route('**/api/**', route => json(route, []));

    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Max Mustermann', username: 'max.mustermann',
        active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
    }));

    await page.route('**/api/firma', route => json(route, {}));

    // Altes Seiten-Lock (useDocumentLock, nicht Teil dieses Tasks) -- muss
    // trotzdem bedient werden, sonst haengt die Seite auf "wird geoeffnet".
    await page.route('**/api/dokument-locks/**/acquire', route => json(route, {
        status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann',
        acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString(),
    }));
    await page.route('**/api/dokument-locks/**/heartbeat', route => {
        mitschrift.heartbeatAufrufe.push(Date.now() - start);
        return json(route, {
            status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann',
            acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString(),
        });
    });
    await page.route(/\/api\/dokument-locks\/[^/]+\/\d+$/, route => route.fulfill({ status: 204, body: '' }));

    await page.route('**/api/formulare/templates/selection**', route => route.fulfill({ status: 404, body: '' }));

    await page.route('**/api/dokument-generator/preview', route => route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF-1.4\n%%EOF'),
    }));

    await page.route('**/api/ausgangs-dokumente/1', async route => {
        const anfrage = route.request();
        if (anfrage.method() === 'GET') {
            return json(route, dokument);
        }
        if (anfrage.method() === 'PUT') {
            if (optionen.speichern === 'fehler') {
                return route.fulfill({
                    status: 500,
                    contentType: 'text/plain',
                    body: 'Serverfehler beim Speichern.',
                });
            }
            const gesendet = anfrage.postDataJSON() as Record<string, unknown>;
            mitschrift.speicherAufrufe.push(gesendet);
            dokument = { ...dokument, ...gesendet } as AusgangsDokumentStand;
            return json(route, dokument);
        }
        return json(route, dokument);
    });

    return mitschrift;
}

/** Macht window.close() zu einem beobachtbaren No-op (simuliert macOS Safari, Issue #82). */
export async function stubbeWindowClose(page: Page): Promise<void> {
    await page.addInitScript(() => {
        (window as unknown as { __windowCloseAufrufe: number }).__windowCloseAufrufe = 0;
        window.close = () => {
            (window as unknown as { __windowCloseAufrufe: number }).__windowCloseAufrufe += 1;
        };
    });
}

export async function windowCloseAufrufe(page: Page): Promise<number> {
    return page.evaluate(() => (window as unknown as { __windowCloseAufrufe?: number }).__windowCloseAufrufe ?? 0);
}

/** Oeffnet den Dokument-Editor fuer das gestubbte Beispieldokument (id 1). */
export async function oeffneDokumentEditor(page: Page) {
    await page.goto('/dokument-editor?dokumentId=1');
    await page.getByText('Musterweg 1').waitFor({ timeout: 10_000 });
}
