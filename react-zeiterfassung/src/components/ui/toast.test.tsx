import { act, fireEvent, renderHook, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from './toast'
it('stapelt Meldungen, schließt einzeln und räumt Timer beim Unmount auf',async()=>{
 vi.useFakeTimers()
 const {result,unmount}=renderHook(useToast,{wrapper:ToastProvider})
 const toast=result.current
 act(()=>{toast.error('Upload fehlgeschlagen');toast.error('Speichern fehlgeschlagen');toast.success('Gespeichert');toast.warning('Offline');toast.info('Hinweis')})
 expect(screen.getAllByRole('alert')).toHaveLength(3)
 expect(screen.getByText('Gespeichert')).toBeVisible()
 fireEvent.click(screen.getAllByRole('button',{name:'Meldung schließen'})[0])
 expect(screen.queryByText('Upload fehlgeschlagen')).not.toBeInTheDocument()
 act(()=>vi.advanceTimersByTime(8000))
 expect(screen.queryByText('Gespeichert')).not.toBeInTheDocument()
 act(()=>toast.info('Neu'))
 unmount();expect(vi.getTimerCount()).toBe(0);vi.useRealTimers()
})
