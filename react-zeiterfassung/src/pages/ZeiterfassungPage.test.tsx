import 'fake-indexeddb/auto'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import ZeiterfassungPage from './ZeiterfassungPage'
import { OfflineService, sendeBuchung } from '../services/OfflineService'

// sendeBuchung ist gemockt: Senden, Einreihen und Reparatur-Liste prüft OfflineService.test.ts.
// Hier zählt, wie die Seite auf das Ergebnis reagiert.
vi.mock('../services/OfflineService', async importOriginal => ({
 ...await importOriginal<object>(),
 sendeBuchung: vi.fn(),
 OfflineService: {
  getZeitkontoStatus: vi.fn(async () => ({ status: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }, ausCache: false })),
  getProjekteMitHerkunft: vi.fn(async () => ({ daten: [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }], ausSpeicher: false })),
  getLastSyncTime: vi.fn(async () => null),
  getKategorien: vi.fn(async () => [{ id: 1, name: 'Testkategorie' }]),
  getArbeitsgaenge: vi.fn(async () => [{ id: 1, beschreibung: 'Montage' }]),
  addPendingEntryWithOperationId: vi.fn(),
 },
}))

const mockedSendeBuchung = vi.mocked(sendeBuchung)
const mockedOfflineService = vi.mocked(OfflineService)

const renderSeite = (pfad = '/zeiterfassung') => render(<MemoryRouter initialEntries={[pfad]}><ToastProvider><Routes>
 <Route path="/zeiterfassung" element={<ZeiterfassungPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} />} />
 <Route path="/" element={<div>Startseite</div>} />
</Routes></ToastProvider></MemoryRouter>)

beforeEach(() => {
 localStorage.setItem('zeiterfassung_token', 'test-token')
 localStorage.removeItem('zeiterfassung_active_session')
 vi.stubGlobal('alert', vi.fn())
 vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }), { status: 200 })))
})
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks() })

const startePerKlick = async (pfad = '/zeiterfassung') => {
 const user = userEvent.setup()
 renderSeite(pfad)
 await user.click(await screen.findByText('Testprojekt'))
 await user.click(await screen.findByText('Testkategorie'))
 await user.click(await screen.findByText('Montage'))
 await user.click(screen.getByRole('button', { name: /Zeiterfassung starten|Auftrag wechseln/i }))
}

it('meldet abgelehnte Buchung als Toast und legt keine Offlinebuchung an', async () => {
 mockedSendeBuchung.mockResolvedValue({ art: 'abgelehnt', status: 409, meldung: 'Buchung ist gesperrt' })

 await startePerKlick()

 expect(await screen.findByRole('alert')).toHaveTextContent('Buchung ist gesperrt')
 expect(window.alert).not.toHaveBeenCalled()
 // Echte Ablehnung: nicht einreihen, nicht in die Reparatur-Liste, nicht weiter
 expect(mockedSendeBuchung).toHaveBeenCalledWith('start', expect.objectContaining({ projektId: 1 }), expect.objectContaining({ ablehnungInReparaturListe: false }))
 expect(OfflineService.addPendingEntryWithOperationId).not.toHaveBeenCalled()
 expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
 expect(screen.queryByText('Startseite')).toBeNull()
})

it('übernimmt beim Start die Serverzeit und geht zurück zur Startseite', async () => {
 mockedSendeBuchung.mockResolvedValue({ art: 'ok', daten: { id: 55, startZeit: '2026-10-17T07:00:00' } })

 await startePerKlick()

 expect(await screen.findByText('Startseite')).toBeInTheDocument()
 const session = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
 expect(session).toMatchObject({ id: 55, projektId: 1, kundenName: 'Testbetrieb', auftragsnummer: 'TEST-1', startTime: '2026-10-17T07:00:00' })
})

it('läuft auf dem Handy weiter, wenn die Buchung nur eingereiht werden konnte', async () => {
 // Funkloch, Zeitlimit oder Server ausgelastet: sendeBuchung reiht ein.
 mockedSendeBuchung.mockResolvedValue({ art: 'eingereiht' })

 await startePerKlick()

 expect(await screen.findByText('Startseite')).toBeInTheDocument()
 const session = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
 expect(session.projektId).toBe(1)
 expect(String(session.id)).toMatch(/^offline-/)
 expect(screen.queryByRole('alert')).toBeNull()
})

it('zeigt einen Fehler, wenn die Buchung nicht einmal auf dem Handy gespeichert werden kann', async () => {
 mockedSendeBuchung.mockRejectedValue(new Error('IndexedDB nicht verfügbar'))

 await startePerKlick()

 expect(await screen.findByRole('alert')).toHaveTextContent('Die Buchung konnte auf dem Handy nicht gespeichert werden')
 expect(screen.queryByText('Startseite')).toBeNull()
})

it('beendet beim Wechsel zuerst die alte Buchung und startet trotzdem, wenn am Server nichts mehr lief', async () => {
 localStorage.setItem('zeiterfassung_active_session', JSON.stringify({ projektId: 9, startTime: new Date(Date.now() - 45 * 60_000).toISOString(), typ: null }))
 mockedSendeBuchung.mockImplementation(async (typ) => typ === 'stop'
  ? { art: 'abgelehnt', status: 400, meldung: 'Keine aktive Buchung gefunden' }
  : { art: 'ok', daten: { id: 77 } })

 await startePerKlick('/zeiterfassung?switching=true')

 expect(await screen.findByText('Startseite')).toBeInTheDocument()
 expect(mockedSendeBuchung.mock.calls.map(([typ]) => typ)).toEqual(['stop', 'start'])
 expect(mockedSendeBuchung.mock.calls[0][2]).toMatchObject({ ablehnungInReparaturListe: false, durationMinutes: 45 })
 expect(JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}').id).toBe(77)
})

it('Wechsel löscht keine älteren Offline-Feierabende, sondern stellt sich dahinter an', async () => {
 // REGRESSION: Nach einem Online-Start beim Wechsel wurden ALLE wartenden Stops
 // gelöscht – auch ein echter, älterer Offline-Feierabend.
 const echt = await vi.importActual<typeof import('../services/OfflineService')>('../services/OfflineService')
 await echt.OfflineService.clearPending()
 mockedSendeBuchung.mockImplementation(echt.sendeBuchung)
 await echt.OfflineService.addPendingEntryWithOperationId('stop', { token: 'test-token' }, '2026-10-16T16:30:00Z', 'op-feierabend-gestern', 480)
 const posts: string[] = []
 vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
  if (init?.method === 'POST') posts.push(String(url))
  return new Response(JSON.stringify({ fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }), { status: 200 })
 }))
 localStorage.setItem('zeiterfassung_active_session', JSON.stringify({ projektId: 9, startTime: new Date().toISOString(), typ: null }))

 await startePerKlick('/zeiterfassung?switching=true')

 expect(await screen.findByText('Startseite')).toBeInTheDocument()
 // Nichts überholt die Warteschlange …
 expect(posts).toEqual([])
 // … und der alte Feierabend ist noch da, vor dem neuen Stop und Start.
 const wartend = await echt.OfflineService.getPendingEntries()
 expect(wartend.map(e => e.type).sort()).toEqual(['start', 'stop', 'stop'])
 expect(wartend.some(e => e.id === 'op-feierabend-gestern')).toBe(true)
 await echt.OfflineService.clearPending()
})

// ==================== OHNE VERBINDUNG ====================

it('zeigt ohne Netz mit gemerktem Stand die Projektliste und einen ruhigen Hinweis mit Uhrzeit', async () => {
 const heute = new Date()
 heute.setHours(7, 5, 0, 0)
 mockedOfflineService.getZeitkontoStatus.mockResolvedValueOnce({ status: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }, ausCache: true })
 mockedOfflineService.getProjekteMitHerkunft.mockResolvedValueOnce({ daten: [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }], ausSpeicher: true })
 mockedOfflineService.getLastSyncTime.mockResolvedValueOnce(heute)

 renderSeite()

 expect(await screen.findByText('Testprojekt')).toBeInTheDocument()
 const hinweis = await screen.findByText(/Ohne Verbindung – Stand 07:05 Uhr\./)
 expect(hinweis).toHaveTextContent('Deine Buchung wird übertragen, sobald wieder Netz da ist.')
 expect(screen.queryByText('Keine neue Zeitbuchung möglich')).toBeNull()
 // Info-Knopf erklärt den Hintergrund
 const user = userEvent.setup()
 await user.click(screen.getByRole('button', { name: 'Mehr zu: Ohne Verbindung buchen' }))
 expect(within(await screen.findByRole('dialog')).getByText(/schickt die App die Buchung von selbst ans Büro/)).toBeInTheDocument()
})

it('nennt beim Listenstand von einem anderen Tag auch das Datum', async () => {
 mockedOfflineService.getProjekteMitHerkunft.mockResolvedValueOnce({ daten: [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }], ausSpeicher: true })
 mockedOfflineService.getLastSyncTime.mockResolvedValueOnce(new Date(2026, 9, 27, 16, 30))

 renderSeite()

 expect(await screen.findByText(/Ohne Verbindung – Stand 27\.10\.2026, 16:30 Uhr\./)).toBeInTheDocument()
})

it('sperrt ohne Netz und ohne je gemerkten Stand nicht, sondern zeigt die Projekte', async () => {
 mockedOfflineService.getZeitkontoStatus.mockResolvedValueOnce({ status: null, ausCache: true })
 mockedOfflineService.getProjekteMitHerkunft.mockResolvedValueOnce({ daten: [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }], ausSpeicher: true })

 renderSeite()

 expect(await screen.findByText('Testprojekt')).toBeInTheDocument()
 expect(screen.queryByText('Keine neue Zeitbuchung möglich')).toBeNull()
 expect(screen.queryByText(/noch keine Arbeitszeit eingerichtet/)).toBeNull()
 expect(screen.getByText(/Ohne Verbindung – Stand vom letzten Abgleich\./)).toBeInTheDocument()
})

it('startet ohne Netz eine Buchung auf dem Handy (wird eingereiht)', async () => {
 mockedOfflineService.getZeitkontoStatus.mockResolvedValueOnce({ status: null, ausCache: true })
 mockedOfflineService.getProjekteMitHerkunft.mockResolvedValueOnce({ daten: [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }], ausSpeicher: true })
 mockedSendeBuchung.mockResolvedValue({ art: 'eingereiht' })

 await startePerKlick()

 expect(await screen.findByText('Startseite')).toBeInTheDocument()
 expect(JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}').projektId).toBe(1)
})

it('sagt ohne Netz und leerem Speicher, dass die Projekte nachgeladen werden', async () => {
 mockedOfflineService.getProjekteMitHerkunft.mockResolvedValueOnce({ daten: [], ausSpeicher: true })

 renderSeite()

 expect(await screen.findByText(/Noch keine Projekte auf dem Handy gespeichert/)).toBeInTheDocument()
})

it('behält den Hinweis „nicht eingerichtet“, wenn der Server es so sagt', async () => {
 mockedOfflineService.getZeitkontoStatus.mockResolvedValueOnce({ status: { fuehrtZeitkonto: true, eingerichtet: false, hinweis: 'Für Sie ist noch keine Arbeitszeit eingerichtet.' }, ausCache: false })

 renderSeite()

 expect(await screen.findByText('Keine neue Zeitbuchung möglich')).toBeInTheDocument()
 expect(screen.getByText('Für Sie ist noch keine Arbeitszeit eingerichtet.')).toBeInTheDocument()
 expect(mockedOfflineService.getProjekteMitHerkunft).not.toHaveBeenCalled()
})

it('sperrt auch ohne Netz, wenn der gemerkte Stand „nicht eingerichtet“ sagt', async () => {
 mockedOfflineService.getZeitkontoStatus.mockResolvedValueOnce({ status: { fuehrtZeitkonto: false, eingerichtet: false, hinweis: 'Die Zeiterfassung ist ausgeschaltet.' }, ausCache: true })

 renderSeite()

 expect(await screen.findByText('Die Zeiterfassung ist ausgeschaltet.')).toBeInTheDocument()
 expect(screen.queryByText('Testprojekt')).toBeNull()
})

it('zeigt online keinen Ohne-Verbindung-Hinweis', async () => {
 renderSeite()

 expect(await screen.findByText('Testprojekt')).toBeInTheDocument()
 expect(screen.queryByText(/Ohne Verbindung/)).toBeNull()
})
