import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, act, cleanup } from '@testing-library/react'
import SetupPage from './SetupPage'
vi.mock('html5-qrcode', () => ({ Html5Qrcode: vi.fn() }))
afterEach(() => { cleanup(); vi.useRealTimers() })
describe('Anmeldesperre', () => {
  it('verhindert Eingabe und QR-Anmeldung bis der Countdown abgelaufen ist', () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-09T10:00:00Z'))
    const login = vi.fn()
    render(<SetupPage retryAt={Date.now() + 2000} onTokenScanned={login} />)
    fireEvent.click(screen.getByText('Token manuell eingeben'))
    const input = screen.getByPlaceholderText('Token eingeben...')
    fireEvent.change(input, { target: { value: 'test-token' } })
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Kamera öffnen/ })).toBeDisabled()
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(login).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(2000))
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(login).toHaveBeenCalledWith('test-token')
  })
})
