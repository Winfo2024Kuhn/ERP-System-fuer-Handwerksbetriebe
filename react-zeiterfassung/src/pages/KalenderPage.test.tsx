import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import KalenderPage from './KalenderPage'
import TerminDetailPage from './TerminDetailPage'

// Dummy-Daten (DSGVO): nur Max Mustermann & Co.
const eintraege = [
    {
        id: 12, titel: 'Baustelle Musterstraße', beschreibung: 'Dachfenster einbauen.', datum: '2026-09-17',
        startZeit: '09:00:00', endeZeit: '10:30:00', ganztaegig: false, farbe: '#7c3aed',
        projektId: 7, projektName: 'Dachsanierung Musterhaus', kundeId: null, kundeName: null,
        lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null,
        erstellerId: 2, erstellerName: 'Erika Musterfrau', teilnehmer: [{ id: 1, name: 'Max Mustermann' }],
    },
    {
        id: 13, titel: 'Abnahme Terrasse', beschreibung: null, datum: '2026-09-03',
        startZeit: '14:00:00', endeZeit: '15:00:00', ganztaegig: false, farbe: null,
        projektId: null, projektName: null, kundeId: 3, kundeName: 'Max Mustermann',
        lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null,
        erstellerId: null, erstellerName: null, teilnehmer: [],
    },
]
const abwesenheiten = [
    { id: 1, datum: '2026-09-03', typ: 'URLAUB', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' },
    { id: 2, datum: '2026-09-18', typ: 'FORTBILDUNG', stunden: 8, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann' },
]
const feiertage = [{ datum: '2026-10-03', bezeichnung: 'Tag der Deutschen Einheit' }]

function antwort(json: unknown, status = 200) {
    return new Response(JSON.stringify(json), { status, headers: { 'Content-Type': 'application/json' } })
}

function fetchMock({ kalenderStatus = 200 }: { kalenderStatus?: number } = {}) {
    return vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(typeof input === 'string' ? input : input.toString(), 'http://localhost')
        if (url.pathname === '/api/kalender/mobile') {
            if (kalenderStatus !== 200) return antwort({}, kalenderStatus)
            return antwort(url.searchParams.get('monat') === '9' ? eintraege : [])
        }
        if (url.pathname === '/api/abwesenheit/team') {
            const von = url.searchParams.get('von') ?? ''
            const bis = url.searchParams.get('bis') ?? ''
            return antwort(abwesenheiten.filter(a => a.datum >= von && a.datum <= bis))
        }
        if (url.pathname === '/api/zeiterfassung/feiertage') return antwort(feiertage)
        return antwort({}, 404)
    })
}

function renderKalender(pfad = '/kalender', onSync?: () => void) {
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={[pfad]}>
                <Routes>
                    <Route path="/" element={<p>Startseite</p>} />
                    <Route path="/kunden" element={<p>Kundenseite</p>} />
                    <Route path="/kalender" element={<KalenderPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} token="tok-test" onSync={onSync} />} />
                    <Route path="/kalender/termin/:datum/:key" element={<TerminDetailPage token="tok-test" />} />
                </Routes>
            </MemoryRouter>
        </ToastProvider>,
    )
}

describe('KalenderPage', () => {
    beforeEach(() => {
        vi.setSystemTime(new Date(2026, 8, 17, 10, 0, 0))
        vi.stubGlobal('fetch', fetchMock())
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        vi.useRealTimers()
    })

    it('zeigt nur das Monatsraster mit Legende – Termine erst im Sheet', async () => {
        renderKalender()
        expect(screen.getByRole('heading', { name: 'September 2026' })).toBeInTheDocument()
        expect(await screen.findByRole('button', { name: /Donnerstag, 17\. September 2026, 1 Eintrag/ })).toHaveAttribute('aria-current', 'date')
        expect(screen.getByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /Samstag, 3\. Oktober 2026, 1 Eintrag/ })).toBeInTheDocument()
        expect(screen.getByRole('list', { name: 'Legende' })).toHaveTextContent('Feiertag')
        expect(screen.queryByText('Baustelle Musterstraße')).not.toBeInTheDocument()
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    // Klicks per fireEvent statt userEvent: vaul reagiert auf Pointer-Events mit
    // Drag-Logik (setPointerCapture, getComputedStyle().transform), die jsdom nicht bietet.
    it('öffnet per Tag-Tipp das Tages-Sheet, führt zur Termin-Unterseite und zurück ins Sheet', async () => {
        renderKalender()
        await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })

        fireEvent.click(screen.getByRole('button', { name: /Donnerstag, 3\. September 2026/ }))
        const sheet = await screen.findByRole('dialog')
        expect(within(sheet).getByText('Donnerstag, 3. September')).toBeInTheDocument()
        expect(within(sheet).getByText('2 Einträge')).toBeInTheDocument()
        expect(within(sheet).getByText('Erika Musterfrau')).toBeInTheDocument()

        fireEvent.click(within(sheet).getByText('Abnahme Terrasse'))
        expect(await screen.findByRole('heading', { name: 'Abnahme Terrasse' })).toBeInTheDocument()
        expect(screen.getByText('14:00 – 15:00 Uhr')).toBeInTheDocument()
        expect(screen.getByText('Max Mustermann')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'Zurück zum Kalender' }))
        const sheetDanach = await screen.findByRole('dialog')
        expect(within(sheetDanach).getByText('Abnahme Terrasse')).toBeInTheDocument()
        expect(screen.getByRole('heading', { name: 'September 2026' })).toBeInTheDocument()
    })

    it('blättert in den nächsten Monat und zeigt den Feiertag im Sheet', async () => {
        renderKalender()
        await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })
        fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))
        expect(await screen.findByRole('heading', { name: 'Oktober 2026' })).toBeInTheDocument()
        fireEvent.click(await screen.findByRole('button', { name: /Samstag, 3\. Oktober 2026, 1 Eintrag/ }))
        const sheet = await screen.findByRole('dialog')
        expect(within(sheet).getByText('Tag der Deutschen Einheit')).toBeInTheDocument()
        expect(within(sheet).getByText('Gesetzlicher Feiertag')).toBeInTheDocument()
        fireEvent.click(within(sheet).getByRole('button', { name: 'Schließen' }))
        fireEvent.click(screen.getByRole('button', { name: 'Heute' }))
        expect(await screen.findByRole('heading', { name: 'September 2026' })).toBeInTheDocument()
    })

    it('meldet einen Ladefehler als Toast und Hinweisband; „Erneut versuchen“ lädt neu', async () => {
        vi.stubGlobal('fetch', fetchMock({ kalenderStatus: 500 }))
        renderKalender()
        expect(await screen.findByRole('alert')).toHaveTextContent('Termine konnten nicht geladen werden')
        await waitFor(() => expect(screen.getByRole('button', { name: /Donnerstag, 17\. September 2026, 0 Einträge/ })).toBeInTheDocument())
        fireEvent.click(screen.getByRole('button', { name: /Donnerstag, 17\. September 2026/ }))
        expect(within(await screen.findByRole('dialog')).getByText('Keine Termine an diesem Tag.')).toBeInTheDocument()
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Schließen' }))

        // Das Band bleibt stehen, bis ein neuer Ladeversuch klappt.
        vi.stubGlobal('fetch', fetchMock())
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }))
        expect(await screen.findByRole('button', { name: /Donnerstag, 17\. September 2026, 1 Eintrag/ })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Erneut versuchen' })).not.toBeInTheDocument()
    })

    it('fragt jeden Monat nur einmal an, auch wenn die Antworten gestaffelt eintreffen', async () => {
        const mock = fetchMock()
        const verzoegert = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            const wartezeit = url.includes('monat=8') ? 5 : url.includes('monat=9') ? 25 : url.includes('feiertage') ? 45 : 65
            await new Promise(resolve => setTimeout(resolve, wartezeit))
            return mock(input)
        })
        vi.stubGlobal('fetch', verzoegert)
        renderKalender()
        await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })
        const aufrufe = verzoegert.mock.calls.map(([input]) => (typeof input === 'string' ? input : input.toString()))
        // Raster August–Oktober: 3 Monate × (Termine + Team-Abwesenheiten) + 1 Feiertagsjahr = 7 Anfragen.
        expect(aufrufe).toHaveLength(7)
        expect(new Set(aufrufe).size).toBe(7)
    })

    it('„Aktualisieren“ lädt neu und stößt den Sync an, „Zurück“ führt zur Übersicht', async () => {
        const mock = fetchMock()
        vi.stubGlobal('fetch', mock)
        const onSync = vi.fn()
        renderKalender('/kalender', onSync)
        await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })
        const vorher = mock.mock.calls.length

        fireEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }))
        expect(onSync).toHaveBeenCalledTimes(1)
        await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 2 Einträge/ })
        await waitFor(() => expect(mock.mock.calls.length).toBe(vorher * 2))

        fireEvent.click(screen.getByRole('button', { name: 'Zurück zur Übersicht' }))
        expect(await screen.findByText('Startseite')).toBeInTheDocument()
    })

    it('„Aktualisieren“ während des Ladens verwirft die alte Antwort', async () => {
        let septemberAufrufe = 0
        const basis = fetchMock()
        const langsamZuerst = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/api/kalender/mobile') && url.includes('monat=9')) {
                septemberAufrufe += 1
                if (septemberAufrufe === 1) {
                    await new Promise(resolve => setTimeout(resolve, 150))
                    return antwort(eintraege) // alte, langsame Antwort: zwei Termine im September
                }
                return antwort([eintraege[0]]) // frische Antwort nach „Aktualisieren“: nur noch die Baustelle
            }
            return basis(input)
        })
        vi.stubGlobal('fetch', langsamZuerst)
        renderKalender()
        await waitFor(() => expect(septemberAufrufe).toBe(1))
        fireEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }))
        expect(await screen.findByRole('button', { name: /Donnerstag, 3\. September 2026, 1 Eintrag/ })).toBeInTheDocument()
        // Die alte Antwort trifft erst jetzt ein und darf den frischen Stand nicht überschreiben.
        await new Promise(resolve => setTimeout(resolve, 200))
        expect(screen.getByRole('button', { name: /Donnerstag, 3\. September 2026, 1 Eintrag/ })).toBeInTheDocument()
    })

    it('Unterseite: Ladefehler zeigt Toast und Hinweisband statt „nicht gefunden“, „Erneut versuchen“ lädt nach', async () => {
        vi.stubGlobal('fetch', fetchMock({ kalenderStatus: 500 }))
        renderKalender('/kalender/termin/2026-09-17/t-12')
        expect(await screen.findByRole('alert')).toHaveTextContent('Termine konnten nicht geladen werden')
        expect(await screen.findByText('Termin konnte nicht geladen werden.')).toBeInTheDocument()
        expect(screen.queryByText(/nicht gefunden/)).not.toBeInTheDocument()

        vi.stubGlobal('fetch', fetchMock())
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }))
        expect(await screen.findByRole('heading', { name: 'Baustelle Musterstraße' })).toBeInTheDocument()
    })

    it('öffnet einen Deep-Link ?termin=ID direkt als Unterseite', async () => {
        renderKalender('/kalender?termin=12')
        expect(await screen.findByRole('heading', { name: 'Baustelle Musterstraße' })).toBeInTheDocument()
        expect(screen.getByText('Dachfenster einbauen.')).toBeInTheDocument()
    })

    it('Verknüpfungen auf der Unterseite führen zur Zielseite', async () => {
        renderKalender('/kalender?termin=13')
        expect(await screen.findByRole('heading', { name: 'Abnahme Terrasse' })).toBeInTheDocument()
        fireEvent.click(screen.getByRole('button', { name: /Kunde.*Max Mustermann/ }))
        expect(await screen.findByText('Kundenseite')).toBeInTheDocument()
    })

    it('zeigt bei ungültigem Datum in der URL den Leerzustand statt „Invalid Date“', async () => {
        renderKalender('/kalender/termin/abc/t-1')
        expect(await screen.findByText(/Dieser Termin wurde nicht gefunden/)).toBeInTheDocument()
        expect(screen.queryByText(/Invalid Date/)).not.toBeInTheDocument()
        expect(screen.getByRole('heading', { name: 'Termin' })).toBeInTheDocument()
    })
})
