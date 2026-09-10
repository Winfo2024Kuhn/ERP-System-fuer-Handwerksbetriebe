import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { it, expect, vi } from 'vitest'
import { ToastProvider } from './ui/toast'
import ScannerModal from './ScannerModal'
vi.mock('./Camera', () => ({ default: ({ onError }: { onError: (error: Error) => void }) => <button onClick={() => onError(new Error('Permission denied'))}>Kamerafehler auslösen</button> }))
vi.mock('../services/DocumentEdgeDetector', () => ({ preloadEdgeDetector: vi.fn(async () => {}), detectDocumentCorners: vi.fn(), detectDocumentCornersOnCanvasSync: vi.fn() }))
it('zeigt Kamerafehler zusätzlich zum Inlinehinweis im eigenen Toast', async () => {
 const user = userEvent.setup(); const save = vi.fn()
 render(<ToastProvider><ScannerModal onClose={vi.fn()} onSave={save} /></ToastProvider>)
 await user.click(screen.getByRole('button', { name: 'Kamerafehler auslösen' }))
 expect(await screen.findByRole('alert')).toHaveTextContent(/Zugriff auf die Kamera/)
 expect(screen.getByText('Kamera nicht verfügbar')).toBeVisible()
 expect(save).not.toHaveBeenCalled()
})
