import { describe, it, expect, vi } from 'vitest'
import { createMobileFetch, validateLoginToken, TokenLoginError } from './mobileTransport'

describe('mobiler Transport', () => {
  it('sendet Tokens ausschließlich an die eigene API und bewahrt bestehende Request-Header', async () => {
    const send = vi.fn().mockResolvedValue(new Response('{}'))
    const fetch = createMobileFetch(send, () => 'test-token', 'https://erp.example')
    await fetch('/api/zeiterfassung/start', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })
    expect(new Headers(send.mock.calls[0][1].headers).get('X-Auth-Token')).toBe('test-token')
    expect(new Headers(send.mock.calls[0][1].headers).get('Content-Type')).toBe('application/json')
    await fetch(new Request('https://erp.example/api/projekte', { headers: { 'X-Test': 'erhalten' } }))
    expect(new Headers(send.mock.calls[1][1].headers).get('X-Test')).toBe('erhalten')
    await fetch('https://other.example/api/projekte')
    expect(send.mock.calls[2][1]).toBeUndefined()
    await fetch('/assets/logo.png')
    expect(send.mock.calls[3][1]).toBeUndefined()
  })
  it('überschreibt einen Anmeldeversuch nicht mit dem bisher gespeicherten Token', async () => {
    const send = vi.fn().mockResolvedValue(new Response('{}'))
    await createMobileFetch(send, () => 'old-token', 'https://erp.example')('/api/mitarbeiter/by-token/new-token')
    expect(new Headers(send.mock.calls[0][1]?.headers).has('X-Auth-Token')).toBe(false)
  })
  it('verhindert automatische Weiterleitungen von Requests mit dem persönlichen Token', async () => {
    const send = vi.fn().mockResolvedValue(new Response('{}'))
    await createMobileFetch(send, () => 'test-token', 'https://erp.example')('/api/projekte')
    const request = new Request('https://erp.example/api/projekte', send.mock.calls[0][1])
    expect(request.redirect).toBe('error')
  })
  it('übernimmt die serverseitige Wartezeit und unterscheidet ungültige Tokens von Serverausfall', async () => {
    const send = vi.fn().mockResolvedValue(new Response('{}', { status: 429, headers: { 'Retry-After': '4' } }))
    await expect(validateLoginToken('ungueltig', send)).rejects.toMatchObject({ retryAfterSeconds: 4 })
    send.mockResolvedValue(new Response('{}', { status: 401 }))
    await expect(validateLoginToken('ungueltig', send)).rejects.toBeInstanceOf(TokenLoginError)
    send.mockResolvedValue(new Response('{}', { status: 503 }))
    await expect(validateLoginToken('test', send)).rejects.toThrow('Server')
  })
})
