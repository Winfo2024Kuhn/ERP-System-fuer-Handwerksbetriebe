import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useWischen } from './useWischen'

function Flaeche({ onWischen }: { onWischen?: (richtung: 1 | -1) => void }) {
    const handler = useWischen(onWischen)
    return <div data-testid="flaeche" {...handler}>Wischfläche</div>
}

function wische(element: HTMLElement, von: { x: number; y: number }, nach: { x: number; y: number }) {
    fireEvent.touchStart(element, { touches: [{ clientX: von.x, clientY: von.y }] })
    fireEvent.touchEnd(element, { changedTouches: [{ clientX: nach.x, clientY: nach.y }] })
}

describe('useWischen', () => {
    it('meldet Wischen nach links als +1 und nach rechts als −1', () => {
        const onWischen = vi.fn()
        render(<Flaeche onWischen={onWischen} />)
        const flaeche = screen.getByTestId('flaeche')
        wische(flaeche, { x: 300, y: 100 }, { x: 100, y: 110 })
        wische(flaeche, { x: 100, y: 100 }, { x: 300, y: 90 })
        expect(onWischen.mock.calls).toEqual([[1], [-1]])
    })

    it('ignoriert kurze Bewegungen und senkrechtes Scrollen', () => {
        const onWischen = vi.fn()
        render(<Flaeche onWischen={onWischen} />)
        const flaeche = screen.getByTestId('flaeche')
        wische(flaeche, { x: 100, y: 100 }, { x: 140, y: 100 })
        wische(flaeche, { x: 300, y: 100 }, { x: 100, y: 200 })
        fireEvent.touchEnd(flaeche, { changedTouches: [{ clientX: 0, clientY: 0 }] })
        expect(onWischen).not.toHaveBeenCalled()
    })

    it('setzt ohne Rückruf keine Handler', () => {
        render(<Flaeche />)
        const flaeche = screen.getByTestId('flaeche')
        expect(() => wische(flaeche, { x: 300, y: 100 }, { x: 100, y: 100 })).not.toThrow()
    })
})
