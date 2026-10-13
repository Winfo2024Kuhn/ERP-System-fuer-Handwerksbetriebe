import type { Page, Route } from '@playwright/test';

/**
 * Stubbt alle /api-Routen, die das Formularwesen (/formulare) anfasst:
 * Vorlagenliste, eine gespeicherte Vorlage, Speichern und die Beispieldaten
 * fuer die Vorschau. Ohne echtes Backend, nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const block = (attrs: Record<string, string | number>, content?: string, style?: object) => {
    const daten = Object.entries(attrs).map(([k, v]) => `data-${k}="${v}"`).join(' ');
    const inhalt = content !== undefined ? ` data-content="${encodeURIComponent(content)}"` : '';
    const stil = style ? ` data-style="${encodeURIComponent(JSON.stringify(style))}"` : '';
    return `<div ${daten}${inhalt}${stil}></div>`;
};

/**
 * Eine Vorlage im Format, wie es der Editor vor der Ueberarbeitung gespeichert
 * hat: ohne Briefform-Meta, Anschrift (Textbaustein mit {{KUNDENADRESSE}})
 * genau in der Anschriftzone nach DIN 5008 Form B (57/178 pt, 240 × 77 pt).
 */
export const ALTE_VORLAGE_HTML =
    '<meta name="page-format" content="A4" /><style>body{font-family:Inter,Arial,sans-serif;}</style>'
    + '<div class="page">'
    + block({ 'block-type': 'logo', page: 1, x: 400, y: 24, z: 1, width: 170, height: 80 }, '/image001.png')
    + block({ 'block-type': 'text', page: 1, x: 57, y: 178, z: 2, width: 240, height: 77 }, '{{KUNDENADRESSE}}',
        { fontSize: 11, fontWeight: '400', color: '#111827', textAlign: 'left' })
    + block({ 'block-type': 'text', page: 1, x: 355, y: 150, z: 3, width: 200, height: 60 }, 'Rechnung {{DOKUMENTNUMMER}}\nDatum: {{DATUM}}',
        { fontSize: 11, fontWeight: '400', color: '#111827', textAlign: 'left' })
    + block({ 'block-type': 'table', page: 1, x: 57, y: 300, z: 4, width: 481, height: 450 })
    + '</div><div class="page">'
    + block({ 'block-type': 'table', page: 2, x: 57, y: 60, z: 1, width: 481, height: 720 })
    + '</div>';

export const VORLAGE_NAME = 'Briefpapier Musterbetrieb';

export interface FormularwesenMitschrift {
    /** Jeder POST auf /api/formulare/templates (Speichern). */
    gespeichert: { name: string; html: string; assignedDokumenttypen: string[] }[];
}

export async function stubbeFormularwesenApi(page: Page): Promise<FormularwesenMitschrift> {
    const mitschrift: FormularwesenMitschrift = { gespeichert: [] };

    // Erst die Auffang-Route, damit keine Anfrage an ein fehlendes Backend haengt.
    await page.route('**/api/**', route => json(route, []));

    await page.route('**/api/auth/me', route => json(route, {
        id: 1, username: 'admin', displayName: 'Max Mustermann',
        vorname: 'Max', nachname: 'Mustermann',
        active: true, admin: true, roles: ['ADMIN'], requiresInitialSetup: false,
    }));

    await page.route('**/api/kunden**', route => json(route, [{
        id: 1, name: 'Mustermann GmbH', kundennummer: 'K-4711', anrede: 'Herr',
        ansprechpartner: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt',
    }]));
    await page.route('**/api/projekte/simple**', route => json(route, [{
        id: 5, bauvorhaben: 'Dachsanierung Musterweg', auftragsnummer: 'A-2026-005', anlegedatum: '2026-10-01',
    }]));

    await page.route('**/api/formulare/templates**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        if (pfad.endsWith('/api/formulare/templates') && anfrage.method() === 'GET') {
            return json(route, [
                { name: VORLAGE_NAME, modified: '2026-10-01T09:00:00Z', assignedDokumenttypen: ['Rechnung', 'Angebot'] },
                { name: 'Mahnung schlicht', modified: '2026-09-12T09:00:00Z', assignedDokumenttypen: [] },
            ]);
        }
        if (pfad.endsWith('/api/formulare/templates') && anfrage.method() === 'POST') {
            const daten = anfrage.postDataJSON() as FormularwesenMitschrift['gespeichert'][number];
            mitschrift.gespeichert.push(daten);
            return json(route, { name: daten.name, html: daten.html, assignedDokumenttypen: daten.assignedDokumenttypen });
        }
        if (pfad.endsWith('/textbaustein-defaults')) return json(route, []);
        return json(route, { name: VORLAGE_NAME, html: ALTE_VORLAGE_HTML, assignedDokumenttypen: ['Rechnung', 'Angebot'] });
    });

    return mitschrift;
}

export async function oeffneFormularwesen(page: Page) {
    await page.goto('/formulare');
}
