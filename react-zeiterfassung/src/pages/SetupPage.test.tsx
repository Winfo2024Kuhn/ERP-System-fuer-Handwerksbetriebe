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
describe('Token manuell eingeben', () => {
  it('blendet den Token per Augen-Symbol ein und wieder aus, damit Tippfehler sichtbar sind', () => {
    render(<SetupPage onTokenScanned={vi.fn()} />)
    fireEvent.click(screen.getByText('Token manuell eingeben'))
    const input = screen.getByPlaceholderText('Token eingeben...')
    expect(input).toHaveAttribute('type', 'password')

    fireEvent.click(screen.getByRole('button', { name: 'Token anzeigen' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Token verbergen' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Token verbergen' }))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('löst mit dem Augen-Symbol keine Anmeldung aus', () => {
    const login = vi.fn()
    render(<SetupPage onTokenScanned={login} />)
    fireEvent.click(screen.getByText('Token manuell eingeben'))
    fireEvent.change(screen.getByPlaceholderText('Token eingeben...'), { target: { value: 'test-token' } })
    fireEvent.click(screen.getByRole('button', { name: 'Token anzeigen' }))
    expect(login).not.toHaveBeenCalled()
  })
})
