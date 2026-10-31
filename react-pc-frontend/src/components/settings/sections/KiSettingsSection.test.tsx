import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../../auth/AuthContext';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';
import { KiSettingsSection } from './KiSettingsSection';
import { antwort, aufrufe, stubbeFetch } from '../../../features/telefon/telefonTestdaten';
import { admin, berechneFunktionen, funktion, gemini, kiEinstellungen, kiModellListe, mistral } from '../../../features/ki/kiTestdaten';
import type { KiEigeneWahl, KiEinstellungen } from '../../../features/ki/kiEinstellungenApi';

/** Nur Test-Schlüssel, nie echte. */
const TEST_SCHLUESSEL = 'test-schluessel-123';

/**
 * Server mit Stand: Die KI-Endpunkte ändern ihn und schicken – wie das echte
 * Backend – den kompletten neuen Stand zurück. `/api/auth/me` meldet
 * „kiAktiv“, sobald irgendein Anbieter einen Schlüssel hat.
 */
function stubbeServer(start: KiEinstellungen = kiEinstellungen(), modelle: () => Response = () => antwort(kiModellListe())) {
    let stand = start;
    const eigene: Record<string, KiEigeneWahl> = {};
    start.funktionen.forEach((f) => { if (f.eigeneWahl) eigene[f.funktion] = f.eigeneWahl; });
    const neu = (teil: Partial<KiEinstellungen>) => {
        const zwischen = { ...stand, ...teil };
        stand = { ...zwischen, funktionen: berechneFunktionen(zwischen.anbieter, zwischen.ersatzErlaubt, zwischen.anbieterListe, eigene) };
        return antwort(stand);
    };
    const aendereAnbieter = (typ: string, aenderung: (a: KiEinstellungen['anbieterListe'][number]) => KiEinstellungen['anbieterListe'][number]) =>
        neu({ anbieterListe: stand.anbieterListe.map((a) => (a.typ === typ ? aenderung(a) : a)) });

    const mock = stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort(admin({ kiAktiv: stand.anbieterListe.some((a) => a.schluesselGesetzt) }))
            : undefined),
        (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
        (url) => (url.pathname === '/api/settings/ki/modelle' ? modelle() : undefined),
        (url, init) => {
            if (url.pathname !== '/api/settings/ki') return undefined;
            if (init?.method === 'PUT') {
                const body = JSON.parse(String(init.body));
                if (!stand.anbieterListe.some((a) => a.typ === body.anbieter)) {
                    return antwort({ message: 'Diesen KI-Anbieter gibt es nicht.' }, 400);
                }
                return neu({ anbieter: body.anbieter, ersatzErlaubt: body.ersatzErlaubt });
            }
            return antwort(stand);
        },
        (url, init) => {
            const treffer = /^\/api\/settings\/ki\/funktionen\/([^/]+)$/.exec(url.pathname);
            if (!treffer || init?.method !== 'PUT') return undefined;
            const body = JSON.parse(String(init.body));
            if (body.anbieter) eigene[treffer[1]] = { anbieter: body.anbieter, modell: body.modell || null };
            else delete eigene[treffer[1]];
            return neu({});
        },
        (url, init) => {
            const treffer = /^\/api\/settings\/ki\/anbieter\/([^/]+)\/(schluessel|test|modelle)$/.exec(url.pathname);
            if (!treffer) return undefined;
            const [, typ, was] = treffer;
            const body = init?.body ? JSON.parse(String(init.body)) : null;
            if (was === 'schluessel' && init?.method === 'PUT') return aendereAnbieter(typ, (a) => ({ ...a, schluesselGesetzt: true }));
            if (was === 'schluessel' && init?.method === 'DELETE') return aendereAnbieter(typ, (a) => ({ ...a, schluesselGesetzt: false }));
            if (was === 'test') {
                return antwort(body?.apiKey === 'falsch'
                    ? { success: false, message: 'Der Schlüssel wurde abgelehnt.' }
                    : { success: true, message: 'Verbindung klappt. Alle Modelle gibt es bei Mistral.' });
            }
            if (was === 'modelle') {
                return aendereAnbieter(typ, (a) => ({
                    ...a,
                    modelle: a.modelle.map((m) => ({ ...m, modell: body?.[m.stufe] || m.standardModell })),
                }));
            }
            return undefined;
        },
    );
    return mock;
}

function zeige() {
    return render(<ToastProvider><ConfirmProvider><AuthProvider><KiSettingsSection /></AuthProvider></ConfirmProvider></ToastProvider>);
}

function karte(typ: string) {
    return screen.getByTestId(`ki-anbieter-${typ}`);
}

function funktionsZeile(name: string) {
    return within(screen.getByRole('list', { name: 'KI-Funktionen' })).getByRole('listitem', { name });
}

function letzterBody(mock: ReturnType<typeof stubbeFetch>, pfad: string, methode: string) {
    const liste = aufrufe(mock, pfad, methode);
    return JSON.parse(String(liste[liste.length - 1][1]?.body));
}

afterEach(() => vi.unstubAllGlobals());

describe('KiSettingsSection – Statuskasten', () => {
    it('sagt ohne Schlüssel ruhig „KI-Funktionen sind aus“ und was wegfällt', async () => {
        stubbeServer();
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        const kasten = titel.closest('section')!;
        expect(kasten).toHaveClass('bg-slate-50');
        expect(within(kasten).getByText(/Das Programm funktioniert trotzdem vollständig/)).toBeInTheDocument();
        expect(within(kasten).getByText('KI-Hilfe in der Menüleiste')).toBeInTheDocument();
        expect(within(kasten).getByText('Spracheingabe in der Handy-App')).toBeInTheDocument();
        expect(within(kasten).getByText(/von Hand eingetragen/)).toHaveTextContent('E-Rechnungen (ZUGFeRD, XRechnung) liest das Programm weiter selbst.');
        // Der Spam-Filter läuft über ein eigenes Sprachmodell – er fällt also nicht weg.
        expect(within(kasten).queryByText(/Spaß-Anfragen/)).not.toBeInTheDocument();
        expect(screen.getByText('Freiwillig.')).toBeInTheDocument();
    });

    it('zeigt grün „KI-Funktionen sind an“, wenn alles läuft – mit Anbieter und Ersatz-Zahl', async () => {
        stubbeServer(kiEinstellungen({ anbieter: 'MISTRAL', anbieterListe: [mistral({ schluesselGesetzt: true }), gemini({ schluesselGesetzt: true })] }));
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind an' });
        const kasten = titel.closest('section')!;
        expect(kasten).toHaveClass('bg-emerald-50');
        expect(kasten).toHaveTextContent('Alle KI-Funktionen laufen – 1 über Mistral, 1 über einen Ersatz-Anbieter.');
    });

    it('zeigt „teilweise an“, wenn einzelne Funktionen aus sind', async () => {
        stubbeServer(kiEinstellungen({ anbieter: 'MISTRAL', ersatzErlaubt: false, anbieterListe: [mistral({ schluesselGesetzt: true }), gemini({ schluesselGesetzt: true })] }));
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind teilweise an' });
        expect(titel.closest('section')).toHaveClass('bg-amber-50');
        expect(titel.closest('section')).toHaveTextContent('Eine KI-Funktion ist gerade aus');
    });

    it('sagt „aus“ mit Grund, wenn nur der nicht gewählte Anbieter einen Schlüssel hat und Ersatz aus ist', async () => {
        stubbeServer(kiEinstellungen({ anbieter: 'MISTRAL', ersatzErlaubt: false, anbieterListe: [mistral(), gemini({ schluesselGesetzt: true })] }));
        zeige();

        const titel = await screen.findByRole('heading', { name: 'KI-Funktionen sind aus' });
        expect(titel.closest('section')).toHaveTextContent('Für Mistral ist kein Schlüssel hinterlegt, und kein Ersatz springt ein.');
        expect(screen.queryByText('Ohne Schlüssel fällt weg:')).not.toBeInTheDocument();
    });

    it('erklärt per Info-Knopf anbieterneutral, was im Hintergrund passiert', async () => {
        stubbeServer();
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('button', { name: 'Mehr zu: KI-Funktionen' }));
        const info = screen.getByRole('dialog', { name: 'KI-Funktionen' });
        expect(info).toHaveTextContent('an den gewählten KI-Anbieter');
        expect(info).toHaveTextContent('Ohne Schlüssel verlässt nichts davon den Betrieb.');
    });
});

describe('KiSettingsSection – Anbieter anzeigen', () => {
    it('zeigt je Anbieter Datenstandort, Fähigkeiten, Schlüsselstatus und die Wahl als Radio', async () => {
        stubbeServer(kiEinstellungen({ anbieterListe: [mistral(), gemini({ schluesselGesetzt: true })] }));
        zeige();

        const gruppe = await screen.findByRole('radiogroup', { name: 'KI-Anbieter wählen' });
        expect(within(gruppe).getByRole('radio', { name: 'Mistral' })).toHaveAttribute('aria-checked', 'false');
        expect(within(gruppe).getByRole('radio', { name: 'Google Gemini' })).toHaveAttribute('aria-checked', 'true');

        const m = karte('MISTRAL');
        expect(m).toHaveTextContent('Daten in der EU');
        expect(m).toHaveTextContent('Kein Schlüssel');
        expect(m).toHaveTextContent('Kann: Text, Bilder, PDF-Dokumente');
        expect(m).toHaveTextContent('Kann in diesem Programm noch nicht: Sprache, Nachschlagen im Programm, Internet-Suche');

        const g = karte('GEMINI');
        expect(g).toHaveTextContent('Daten auch außerhalb der EU');
        expect(g).toHaveTextContent('Schlüssel hinterlegt');
        expect(g).toHaveTextContent('Gewählt');
        expect(g).not.toHaveTextContent('Kann in diesem Programm noch nicht');
        // Wording: kein „API Key“.
        expect(screen.queryByText(/API Key|API testen|Gemini Key/)).not.toBeInTheDocument();
    });

    it('hat alle Info-Knöpfe mit „Hintergrund“ und „Bewirkt“', async () => {
        stubbeServer();
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('radiogroup');

        const erwartet: [string, string][] = [
            ['KI-Anbieter', 'Der Wechsel gilt sofort für alle neuen Anfragen, ohne Neustart.'],
            ['Ersatz-Anbieter', 'Aus: Es geht nichts an andere Anbieter'],
            ['Modelle von Mistral', 'Ein leeres Feld nimmt das Standard-Modell des Programms.'],
            ['Schlüssel für Mistral', 'Der Schlüssel wird verschlüsselt gespeichert und danach nie wieder angezeigt.'],
            ['Welche Funktion nutzt was?', '„Ersatz“: Ihr Anbieter kann das noch nicht, ein anderer springt ein.'],
        ];
        for (const [titel, text] of erwartet) {
            await user.click(screen.getByRole('button', { name: `Mehr zu: ${titel}` }));
            const info = screen.getByRole('dialog', { name: titel });
            expect(info).toHaveTextContent('Was passiert im Hintergrund?');
            expect(info).toHaveTextContent('Was bewirkt das?');
            expect(info).toHaveTextContent(text);
            await user.keyboard('{Escape}');
        }
        // Gemini speichert den Schlüssel (noch) nicht verschlüsselt – dort kein Versprechen.
        expect(screen.queryByRole('button', { name: 'Mehr zu: Schlüssel für Google Gemini' })).not.toBeInTheDocument();
    });

    it('zeigt unbekannte Anbieter und Fähigkeiten roh statt gar nicht', async () => {
        stubbeServer(kiEinstellungen({
            anbieter: 'NEU',
            anbieterListe: [
                mistral(),
                { ...gemini(), typ: 'NEU', name: 'Neuer Anbieter', datenstandort: 'MOND', faehigkeiten: ['TEXT', 'VIDEO'], modelle: [] },
            ],
            funktionen: [funktion({ status: 'UNBEKANNT', anbieterName: 'Neuer Anbieter', modell: null })],
        }));
        zeige();

        const neu = await screen.findByRole('radio', { name: 'Neuer Anbieter' });
        expect(neu).toHaveAttribute('aria-checked', 'true');
        expect(karte('NEU')).toHaveTextContent('Kann: Text, VIDEO');
        expect(karte('NEU')).not.toHaveTextContent('Daten in der EU');
        // Mistral kann VIDEO nicht – das steht auch dort.
        expect(karte('MISTRAL')).toHaveTextContent('VIDEO');
        expect(funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen')).toHaveTextContent('UNBEKANNT');
    });
});

describe('KiSettingsSection – Anbieter wählen und Ersatz', () => {
    it('speichert die Wahl sofort, zeichnet aus der Antwort neu und frischt die Anmeldung auf', async () => {
        const mock = stubbeServer(kiEinstellungen({ anbieterListe: [mistral({ schluesselGesetzt: true }), gemini({ schluesselGesetzt: true })] }));
        const user = userEvent.setup();
        zeige();

        const mistralRadio = await screen.findByRole('radio', { name: 'Mistral' });
        await waitFor(() => expect(aufrufe(mock, '/api/auth/me')).toHaveLength(1));
        expect(funktionsZeile('Spracheingabe in der Handy-App')).toHaveTextContent('Läuft');

        await user.click(mistralRadio);

        await waitFor(() => expect(mistralRadio).toHaveAttribute('aria-checked', 'true'));
        expect(letzterBody(mock, '/api/settings/ki', 'PUT')).toEqual({ anbieter: 'MISTRAL', ersatzErlaubt: true });
        expect(await screen.findByText('Mistral ist jetzt der KI-Anbieter. Das gilt ab sofort.')).toBeInTheDocument();

        const dokumente = funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen');
        expect(dokumente).toHaveTextContent('Läuft');
        expect(dokumente).toHaveTextContent('Mistral');
        expect(dokumente).toHaveTextContent('mistral-medium-latest');
        const sprache = funktionsZeile('Spracheingabe in der Handy-App');
        expect(sprache).toHaveTextContent('Ersatz');
        expect(sprache).toHaveTextContent('Google Gemini');
        expect(sprache).toHaveTextContent('Mistral kann das in diesem Programm noch nicht. Es springt Google Gemini ein.');
        await waitFor(() => expect(aufrufe(mock, '/api/auth/me')).toHaveLength(2));
    });

    it('weist deutlich darauf hin, wenn der gewählte Anbieter noch keinen Schlüssel hat', async () => {
        stubbeServer(kiEinstellungen({ anbieter: 'MISTRAL', anbieterListe: [mistral(), gemini({ schluesselGesetzt: true })] }));
        zeige();

        const hinweis = await screen.findByRole('note');
        expect(hinweis).toHaveTextContent('Für Mistral ist noch kein Schlüssel hinterlegt.');
        expect(hinweis).toHaveTextContent('Welche Funktion nutzt was?');
    });

    it('zeigt die Server-Meldung, wenn die Wahl abgelehnt wird, und lässt die Anzeige stehen', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin()) : undefined),
            (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
            (url, init) => {
                if (url.pathname !== '/api/settings/ki') return undefined;
                if (init?.method === 'PUT') return antwort({ message: 'Diesen KI-Anbieter gibt es nicht.' }, 400);
                return antwort(kiEinstellungen());
            },
        );
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('radio', { name: 'Mistral' }));
        expect(await screen.findByText('Diesen KI-Anbieter gibt es nicht.')).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Google Gemini' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('radio', { name: 'Mistral' })).toHaveAttribute('aria-checked', 'false');
    });

    it('ist per Tastatur bedienbar: Pfeil wandert ohne zu speichern, Leertaste wählt', async () => {
        const mock = stubbeServer();
        const user = userEvent.setup();
        zeige();

        const geminiRadio = await screen.findByRole('radio', { name: 'Google Gemini' });
        const mistralRadio = screen.getByRole('radio', { name: 'Mistral' });
        // Nur der gewählte Anbieter liegt in der Tab-Reihenfolge.
        expect(geminiRadio).toHaveAttribute('tabindex', '0');
        expect(mistralRadio).toHaveAttribute('tabindex', '-1');

        geminiRadio.focus();
        await user.keyboard('{ArrowDown}');
        expect(mistralRadio).toHaveFocus();
        await user.keyboard('{ArrowUp}');
        expect(geminiRadio).toHaveFocus();
        await user.keyboard('{Home}');
        expect(mistralRadio).toHaveFocus();
        await user.keyboard('{End}');
        expect(geminiRadio).toHaveFocus();
        await user.keyboard('{ArrowRight}');
        expect(mistralRadio).toHaveFocus();
        expect(aufrufe(mock, '/api/settings/ki', 'PUT')).toHaveLength(0);

        await user.keyboard(' ');
        await waitFor(() => expect(mistralRadio).toHaveAttribute('aria-checked', 'true'));
        expect(aufrufe(mock, '/api/settings/ki', 'PUT')).toHaveLength(1);
    });

    it('der Ersatz-Schalter speichert sofort und die Liste zeigt danach „Aus“ mit Hinweis', async () => {
        const mock = stubbeServer(kiEinstellungen({ anbieter: 'MISTRAL', anbieterListe: [mistral({ schluesselGesetzt: true }), gemini({ schluesselGesetzt: true })] }));
        const user = userEvent.setup();
        zeige();

        const schalter = await screen.findByRole('switch', { name: 'Ersatz-Anbieter erlauben' });
        expect(schalter).toHaveAttribute('aria-checked', 'true');
        expect(schalter).toHaveAccessibleDescription(/springt ein anderer Anbieter mit Schlüssel ein/);
        expect(funktionsZeile('Spracheingabe in der Handy-App')).toHaveTextContent('Ersatz');

        await user.click(schalter);

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'false'));
        expect(letzterBody(mock, '/api/settings/ki', 'PUT')).toEqual({ anbieter: 'MISTRAL', ersatzErlaubt: false });
        expect(await screen.findByText('Ersatz-Anbieter ausgeschaltet.')).toBeInTheDocument();
        const sprache = funktionsZeile('Spracheingabe in der Handy-App');
        expect(sprache).toHaveTextContent('Aus');
        expect(sprache).toHaveTextContent('Kein Anbieter');
        expect(sprache).toHaveTextContent('kein Ersatz ist erlaubt');
        expect(schalter).toHaveAccessibleDescription(/Es geht nichts an andere Anbieter/);
    });
});

describe('KiSettingsSection – Schlüssel', () => {
    it('speichert den Schlüssel beim richtigen Anbieter und zeigt danach „Schlüssel hinterlegt“', async () => {
        const mock = stubbeServer();
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        const speichern = within(m).getByRole('button', { name: /Schlüssel speichern/ });
        expect(speichern).toBeDisabled();
        await user.type(within(m).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' }), TEST_SCHLUESSEL);
        await user.click(speichern);

        expect(await screen.findByText('Schlüssel für Mistral gespeichert.')).toBeInTheDocument();
        expect(letzterBody(mock, '/api/settings/ki/anbieter/MISTRAL/schluessel', 'PUT')).toEqual({ apiKey: TEST_SCHLUESSEL });
        expect(karte('MISTRAL')).toHaveTextContent('Schlüssel hinterlegt');
        expect(within(karte('MISTRAL')).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' })).toHaveValue('');
        // „kiAktiv“ ändert sich – Anmeldung wird neu geholt.
        await waitFor(() => expect(aufrufe(mock, '/api/auth/me')).toHaveLength(2));
    });

    it('testet mit dem eingetippten Schlüssel oder – leer – mit dem gespeicherten', async () => {
        const mock = stubbeServer(kiEinstellungen({ anbieterListe: [mistral({ schluesselGesetzt: true }), gemini()] }));
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.click(within(m).getByRole('button', { name: /Verbindung testen/ }));
        expect(await within(m).findByRole('status')).toHaveTextContent('Verbindung klappt. Alle Modelle gibt es bei Mistral.');
        expect(letzterBody(mock, '/api/settings/ki/anbieter/MISTRAL/test', 'POST')).toEqual({ apiKey: null });

        await user.type(within(m).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' }), 'falsch');
        await user.click(within(m).getByRole('button', { name: /Verbindung testen/ }));
        expect(await within(m).findByText('Der Schlüssel wurde abgelehnt.', { selector: '[role="status"]' })).toBeInTheDocument();
        expect(letzterBody(mock, '/api/settings/ki/anbieter/MISTRAL/test', 'POST')).toEqual({ apiKey: 'falsch' });

        // Gemini ohne Schlüssel: Testen erst nach dem Eintragen.
        expect(within(karte('GEMINI')).getByRole('button', { name: /Verbindung testen/ })).toBeDisabled();
    });

    it('fragt beim Entfernen nach und schaltet den Anbieter erst nach „Ja“ ab', async () => {
        const mock = stubbeServer(kiEinstellungen({ anbieterListe: [mistral(), gemini({ schluesselGesetzt: true })] }));
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('heading', { name: 'KI-Funktionen sind an' });
        await user.click(screen.getByRole('button', { name: 'Schlüssel für Google Gemini entfernen' }));
        const rueckfrage = await screen.findByRole('dialog', { name: 'Schlüssel für Google Gemini entfernen?' });
        expect(rueckfrage).toHaveTextContent('Schon ausgelesene Daten bleiben erhalten.');
        await user.click(within(rueckfrage).getByRole('button', { name: 'Abbrechen' }));
        expect(aufrufe(mock, '/api/settings/ki/anbieter/GEMINI/schluessel', 'DELETE')).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: 'Schlüssel für Google Gemini entfernen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Schlüssel für Google Gemini entfernen?' })).getByRole('button', { name: 'Schlüssel entfernen' }));

        expect(await screen.findByText('Schlüssel für Google Gemini entfernt.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/settings/ki/anbieter/GEMINI/schluessel', 'DELETE')).toHaveLength(1);
        expect(screen.getByRole('heading', { name: 'KI-Funktionen sind aus' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Schlüssel für Google Gemini entfernen' })).not.toBeInTheDocument();
    });

    it('sperrt verschlüsselte Schlüssel, solange der Server dafür nicht eingerichtet ist', async () => {
        stubbeServer(kiEinstellungen({ verschluesselungBereit: false }));
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        expect(m).toHaveTextContent('Der Server ist noch nicht für verschlüsselte Zugangsdaten eingerichtet. Bitte den Betreuer des Programms fragen.');
        const feld = within(m).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' });
        expect(feld).toBeDisabled();
        expect(feld).toHaveAccessibleDescription(/noch nicht für verschlüsselte Zugangsdaten eingerichtet/);
        expect(within(m).getByRole('button', { name: /Schlüssel speichern/ })).toBeDisabled();

        // Gemini speichert unverschlüsselt und bleibt bedienbar.
        const g = karte('GEMINI');
        expect(g).not.toHaveTextContent('verschlüsselte Zugangsdaten');
        await user.type(within(g).getByLabelText(/Schlüssel für Google Gemini/, { selector: 'input' }), TEST_SCHLUESSEL);
        expect(within(g).getByRole('button', { name: /Schlüssel speichern/ })).toBeEnabled();
    });

    it('zeigt die Server-Meldung, wenn das Speichern abgelehnt wird', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin()) : undefined),
            (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
            (url) => (url.pathname === '/api/settings/ki' ? antwort(kiEinstellungen()) : undefined),
            (url) => (url.pathname.endsWith('/schluessel') ? antwort({ message: 'Bitte einen Schlüssel eintragen.' }, 400) : undefined),
        );
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.type(within(m).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' }), '   x');
        await user.click(within(m).getByRole('button', { name: /Schlüssel speichern/ }));
        expect(await screen.findByText('Bitte einen Schlüssel eintragen.')).toBeInTheDocument();
        expect(m).toHaveTextContent('Kein Schlüssel');
    });
});

describe('KiSettingsSection – Standard-Modelle je Anbieter', () => {
    it('lädt die Modelle erst beim Öffnen, wählt per Suche und speichert – leer heißt Standard', async () => {
        const mock = stubbeServer();
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        expect(m).toHaveTextContent('Standard-Modelle des Programms');
        await user.click(within(m).getByRole('button', { name: 'Erweitert: Standard-Modelle' }));
        expect(aufrufe(mock, '/api/settings/ki/modelle')).toHaveLength(0);
        expect(within(m).getByRole('button', { name: /Modelle speichern/ })).toBeDisabled();
        expect(within(m).getByRole('button', { name: /Standard wiederherstellen/ })).toBeDisabled();

        const standard = within(m).getByLabelText('Standard');
        expect(standard).toHaveTextContent('Standard (mistral-medium-latest)');
        await user.click(standard);
        const liste = await within(m).findByRole('listbox');
        // Nur Modelle dieses Anbieters, mit Hinweis auf Bilder.
        expect(within(liste).getAllByRole('option')).toHaveLength(4);
        expect(within(liste).getByRole('option', { name: /Mistral Small/ })).toHaveTextContent('keine Bilder');
        await user.type(within(m).getByRole('combobox'), 'large');
        await user.click(within(liste).getByRole('option', { name: /Mistral Large/ }));
        expect(standard).toHaveTextContent('Mistral Large (mistral-large-latest)');

        await user.click(within(m).getByRole('button', { name: /Modelle speichern/ }));
        expect(await screen.findByText(/Modelle für Mistral gespeichert/)).toBeInTheDocument();
        expect(letzterBody(mock, '/api/settings/ki/anbieter/MISTRAL/modelle', 'PUT'))
            .toEqual({ SCHNELL: '', STANDARD: 'mistral-large-latest', GRUENDLICH: '' });
        expect(m).toHaveTextContent('Eigene Modelle eingetragen');
        // Die Liste wird nur einmal je Seite geholt.
        expect(aufrufe(mock, '/api/settings/ki/modelle')).toHaveLength(1);

        await user.click(within(m).getByRole('button', { name: /Standard wiederherstellen/ }));
        expect(await screen.findByText('Standard-Modelle für Mistral wiederhergestellt.')).toBeInTheDocument();
        expect(letzterBody(mock, '/api/settings/ki/anbieter/MISTRAL/modelle', 'PUT'))
            .toEqual({ SCHNELL: '', STANDARD: '', GRUENDLICH: '' });
        expect(within(m).getByLabelText('Standard')).toHaveTextContent('Standard (mistral-medium-latest)');
    });

    it('nimmt einen frei eingetragenen Namen, prüft ihn aber vorher', async () => {
        stubbeServer();
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.click(within(m).getByRole('button', { name: 'Erweitert: Standard-Modelle' }));
        const schnell = within(m).getByLabelText('Schnell');
        await user.click(schnell);
        expect(await within(m).findByText('Modell nicht in der Liste? Namen ins Suchfeld tippen und hier übernehmen.')).toBeInTheDocument();

        await user.type(within(m).getByRole('combobox'), 'mistral klein');
        await user.click(within(m).getByRole('button', { name: '„mistral klein“ als Modellnamen übernehmen' }));
        expect(within(m).getByRole('alert')).toHaveTextContent('Der Modellname „mistral klein“ ist ungültig.');

        await user.clear(within(m).getByRole('combobox'));
        await user.type(within(m).getByRole('combobox'), 'mistral-eigen-1{Enter}');
        expect(within(m).queryByRole('listbox')).not.toBeInTheDocument();
        expect(schnell).toHaveTextContent('mistral-eigen-1');
        expect(schnell).toHaveFocus();
    });

    it('merkt sich den Filter „Nur Modelle, die Bilder lesen“ seitenweit und über das Schließen hinaus', async () => {
        stubbeServer();
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.click(within(m).getByRole('button', { name: 'Erweitert: Standard-Modelle' }));
        await user.click(within(m).getByLabelText('Schnell'));
        const filter = await within(m).findByRole('button', { name: 'Nur Modelle, die Bilder lesen' });
        expect(filter).toHaveAttribute('aria-pressed', 'false');
        await within(m).findByText('4 von 4 Modellen');
        await user.click(filter);
        expect(filter).toHaveAttribute('aria-pressed', 'true');
        expect(within(m).getByText('2 von 4 Modellen')).toBeInTheDocument();
        expect(within(m).queryByRole('option', { name: /Mistral Small/ })).not.toBeInTheDocument();
        await user.keyboard('{Escape}');

        // Eine andere Modellsuche zeigt denselben Filter.
        await user.click(within(m).getByLabelText('Gründlich'));
        expect(await within(m).findByRole('button', { name: 'Nur Modelle, die Bilder lesen' })).toHaveAttribute('aria-pressed', 'true');
        expect(JSON.parse(localStorage.getItem('erp.gemerkt.ki-modellsuche-filter')!)).toEqual({ nurBilder: true });
    });

    it('zeigt Ladefehler und Hinweise der Modellliste und lädt auf Knopfdruck neu', async () => {
        let versuche = 0;
        const mock = stubbeServer(kiEinstellungen(), () => {
            versuche += 1;
            return versuche === 1
                ? antwort({ message: 'Mistral hat nicht geantwortet.' }, 502)
                : antwort(kiModellListe({ hinweise: ['Google Gemini: Für diesen Anbieter ist noch kein Schlüssel hinterlegt – seine Modelle erscheinen erst danach.'] }));
        });
        const user = userEvent.setup();
        zeige();

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.click(within(m).getByRole('button', { name: 'Erweitert: Standard-Modelle' }));
        await user.click(within(m).getByLabelText('Schnell'));
        const fehler = await within(m).findByRole('alert');
        expect(fehler).toHaveTextContent('Mistral hat nicht geantwortet.');
        expect(within(m).getByText('Keine Liste vom Anbieter – Namen unten selbst eintragen.')).toBeInTheDocument();

        await user.click(within(fehler).getByRole('button', { name: /Erneut laden/ }));
        expect(await within(m).findByText(/seine Modelle erscheinen erst danach/)).toBeInTheDocument();
        expect(within(m).getAllByRole('option')).toHaveLength(4);
        expect(aufrufe(mock, '/api/settings/ki/modelle')).toHaveLength(2);
    });
});

describe('KiSettingsSection – Anbieter und Modell je Funktion', () => {
    it('legt für eine Funktion Anbieter und Modell fest und zeigt die eigene Wahl', async () => {
        const mock = stubbeServer(kiEinstellungen({ anbieterListe: [mistral({ schluesselGesetzt: true }), gemini({ schluesselGesetzt: true })] }));
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('list', { name: 'KI-Funktionen' });
        const zeile = funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen');
        expect(zeile).toHaveTextContent('Folgt dem Standard-Anbieter');
        await user.click(within(zeile).getByRole('button', { name: 'Ändern: Rechnungen, Lieferscheine und Belege auslesen' }));

        const segmente = within(zeile).getByRole('radiogroup', { name: 'Anbieter für „Rechnungen, Lieferscheine und Belege auslesen“' });
        expect(within(segmente).getByRole('radio', { name: /Standard \(Google Gemini\)/ })).toHaveAttribute('aria-checked', 'true');
        expect(within(zeile).getByRole('button', { name: /Übernehmen/ })).toBeDisabled();

        await user.click(within(segmente).getByRole('radio', { name: /^Mistral/ }));
        const modell = within(zeile).getByLabelText('Modell von Mistral');
        expect(modell).toHaveTextContent('Standard-Modell von Mistral');
        await user.click(modell);
        await user.click(await within(zeile).findByRole('option', { name: /Mistral Large/ }));
        await user.click(within(zeile).getByRole('button', { name: /Übernehmen/ }));

        expect(await screen.findByText('„Rechnungen, Lieferscheine und Belege auslesen“ läuft jetzt über Mistral · mistral-large-latest.')).toBeInTheDocument();
        expect(letzterBody(mock, '/api/settings/ki/funktionen/DOKUMENT_ANALYSE', 'PUT')).toEqual({ anbieter: 'MISTRAL', modell: 'mistral-large-latest' });
        const neu = funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen');
        expect(neu).toHaveTextContent('Eigene Wahl: Mistral · mistral-large-latest');
        expect(neu).toHaveTextContent('Läuft');
        expect(within(neu).queryByRole('radiogroup')).not.toBeInTheDocument();
        expect(within(neu).getByRole('button', { name: /^Ändern:/ })).toHaveFocus();

        // Zurück auf Standard.
        await user.click(within(neu).getByRole('button', { name: /^Ändern:/ }));
        await user.click(within(neu).getByRole('button', { name: /Auf Standard zurücksetzen/ }));
        await waitFor(() => expect(funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen')).toHaveTextContent('Folgt dem Standard-Anbieter'));
        expect(letzterBody(mock, '/api/settings/ki/funktionen/DOKUMENT_ANALYSE', 'PUT')).toEqual({ anbieter: null, modell: null });
    });

    it('sperrt Anbieter, die die Funktion nicht können, und sagt warum', async () => {
        stubbeServer();
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('list', { name: 'KI-Funktionen' });
        const zeile = funktionsZeile('Spracheingabe in der Handy-App');
        await user.click(within(zeile).getByRole('button', { name: /^Ändern:/ }));

        const mistralSegment = within(zeile).getByRole('radio', { name: /^Mistral/ });
        expect(mistralSegment).toHaveAttribute('aria-disabled', 'true');
        expect(mistralSegment).toHaveAttribute('title', 'Mistral kann das in diesem Programm noch nicht (Sprache).');
        expect(zeile).toHaveTextContent('Mistral kann das in diesem Programm noch nicht (Sprache).');
        await user.click(mistralSegment);
        expect(mistralSegment).toHaveAttribute('aria-checked', 'false');
        // Ohne Schlüssel bleibt Gemini wählbar, sagt es aber.
        expect(within(zeile).getByRole('radio', { name: /Google Gemini.*noch kein Schlüssel/ })).not.toHaveAttribute('aria-disabled');

        await user.click(within(zeile).getByRole('button', { name: 'Mehr zu: Anbieter je Funktion' }));
        const info = screen.getByRole('dialog', { name: 'Anbieter je Funktion' });
        expect(info).toHaveTextContent('„Standard“ heißt: die Funktion folgt dem oben gewählten Anbieter.');
        await user.keyboard('{Escape}');

        await user.click(within(zeile).getByRole('button', { name: 'Abbrechen' }));
        expect(within(zeile).queryByRole('radiogroup')).not.toBeInTheDocument();
    });

    it('zeigt die Server-Meldung und lässt den Bereich offen, wenn die Wahl abgelehnt wird', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin()) : undefined),
            (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
            (url) => (url.pathname === '/api/settings/ki' ? antwort(kiEinstellungen()) : undefined),
            (url) => (url.pathname.startsWith('/api/settings/ki/funktionen/')
                ? antwort({ message: 'Der Modellname „x y“ ist ungültig.' }, 400) : undefined),
        );
        const user = userEvent.setup();
        zeige();

        await screen.findByRole('list', { name: 'KI-Funktionen' });
        const zeile = funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen');
        await user.click(within(zeile).getByRole('button', { name: /^Ändern:/ }));
        await user.click(within(zeile).getByRole('radio', { name: /^Mistral/ }));
        await user.click(within(zeile).getByRole('button', { name: /Übernehmen/ }));
        expect(await screen.findByText('Der Modellname „x y“ ist ungültig.')).toBeInTheDocument();
        expect(within(zeile).getByRole('radiogroup')).toBeInTheDocument();
    });
});

describe('KiSettingsSection – Funktionsliste', () => {
    it('zeigt Läuft, Ersatz und Aus mit Wort, Anbieter · Modell und Hinweis', async () => {
        stubbeServer(kiEinstellungen({
            funktionen: [
                funktion({ funktion: 'DOKUMENT_ANALYSE', anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-medium-latest' }),
                funktion({
                    funktion: 'SPRACHEINGABE', name: 'Spracheingabe in der Handy-App', braucht: ['AUDIO'], status: 'ERSATZ',
                    hinweis: 'Mistral kann das in diesem Programm noch nicht (Sprache). Es springt Google Gemini ein.',
                }),
                funktion({
                    funktion: 'PROGRAMM_HILFE', name: 'KI-Hilfe in der Menüleiste', braucht: ['WERKZEUGE'], status: 'AUS',
                    anbieter: null, anbieterName: null, modell: null,
                    hinweis: 'Kein eingerichteter Anbieter kann das. Hinterlegen Sie einen Schlüssel für Google Gemini.',
                }),
            ],
        }));
        zeige();

        const dokumente = await waitFor(() => funktionsZeile('Rechnungen, Lieferscheine und Belege auslesen'));
        expect(dokumente).toHaveTextContent('Läuft');
        expect(dokumente).toHaveTextContent('Mistral');
        expect(dokumente).toHaveTextContent('Modell mistral-medium-latest');
        expect(dokumente).toHaveTextContent('Braucht: PDF-Dokumente, Bilder');

        const sprache = funktionsZeile('Spracheingabe in der Handy-App');
        expect(sprache).toHaveTextContent('Ersatz');
        expect(sprache).toHaveTextContent('Braucht: Sprache');
        expect(sprache).toHaveTextContent('Es springt Google Gemini ein.');

        const hilfe = funktionsZeile('KI-Hilfe in der Menüleiste');
        expect(hilfe).toHaveTextContent('Aus');
        expect(hilfe).toHaveTextContent('Kein Anbieter');
        expect(hilfe).toHaveTextContent('Braucht: Nachschlagen im Programm');
        expect(hilfe).toHaveTextContent('Hinterlegen Sie einen Schlüssel für Google Gemini.');

        expect(screen.getByTestId('ki-funktionen-zusammenfassung')).toHaveTextContent('Läuft1 FunktionErsatz1 FunktionAus1 Funktion');
    });

    it('zeigt einen leeren Zustand, wenn der Server keine Funktionen meldet', async () => {
        stubbeServer(kiEinstellungen({ funktionen: [], anbieterListe: [] }));
        zeige();

        expect(await screen.findByText('Der Server hat keine KI-Funktionen gemeldet.')).toBeInTheDocument();
        expect(screen.getByText('Der Server hat keine KI-Anbieter gemeldet.')).toBeInTheDocument();
    });
});

describe('KiSettingsSection – Laden', () => {
    it('zeigt einen Fehlerkasten mit der Server-Meldung und lädt auf Knopfdruck neu', async () => {
        let versuche = 0;
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin()) : undefined),
            (url) => (url.pathname === '/api/settings/anfrage-funnel-spamfilter' ? antwort({ aktiv: true }) : undefined),
            (url) => {
                if (url.pathname !== '/api/settings/ki') return undefined;
                versuche += 1;
                return versuche === 1 ? antwort({ message: 'Nur Administratoren dürfen das.' }, 403) : antwort(kiEinstellungen());
            },
        );
        const user = userEvent.setup();
        zeige();

        const fehler = (await screen.findByText('Die KI-Einstellungen konnten nicht geladen werden.')).closest('[role="alert"]') as HTMLElement;
        expect(fehler).toHaveTextContent('Die KI-Einstellungen konnten nicht geladen werden.');
        expect(fehler).toHaveTextContent('Nur Administratoren dürfen das.');
        // Der Spam-Filter hängt nicht an den KI-Anbietern und bleibt bedienbar.
        expect(screen.getByRole('checkbox', { name: /Spaß-Anfragen automatisch prüfen/ })).toBeEnabled();

        await user.click(within(fehler).getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByRole('radiogroup', { name: 'KI-Anbieter wählen' })).toBeInTheDocument();
        expect(screen.queryByText('Die KI-Einstellungen konnten nicht geladen werden.')).not.toBeInTheDocument();
    });

    it('funktioniert auch ohne Anmelde-Rahmen', async () => {
        const mock = stubbeServer();
        const user = userEvent.setup();
        render(<ToastProvider><ConfirmProvider><KiSettingsSection /></ConfirmProvider></ToastProvider>);

        const m = await screen.findByTestId('ki-anbieter-MISTRAL');
        await user.type(within(m).getByLabelText(/Schlüssel für Mistral/, { selector: 'input' }), TEST_SCHLUESSEL);
        await user.click(within(m).getByRole('button', { name: /Schlüssel speichern/ }));
        expect(await screen.findByText('Schlüssel für Mistral gespeichert.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(0);
    });
});

describe('KiSettingsSection – Spam-Filter', () => {
    it('erklärt beim Spam-Filter, dass er ohne Gemini-Schlüssel läuft und keine Anfrage abweist', async () => {
        stubbeServer();
        zeige();

        const beschreibung = await screen.findByText(/prüft ein Sprachmodell jede neue Anfrage/);
        expect(beschreibung).toHaveTextContent('Dafür braucht es keinen KI-Schlüssel');
        expect(beschreibung).toHaveTextContent('Die Anfrage wird trotzdem immer gespeichert');
        expect(beschreibung).toHaveTextContent('löschen Sie von Hand unter „Anfragen“');
        expect(screen.queryByText(/nicht gesendet werden konnte|blockiert/)).not.toBeInTheDocument();
    });
});
