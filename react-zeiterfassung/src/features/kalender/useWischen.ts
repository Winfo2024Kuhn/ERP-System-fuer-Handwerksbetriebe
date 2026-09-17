import { useRef, type TouchEvent } from 'react'

const MINDEST_WEITE = 48
const MAX_HOEHE = 40

/**
 * Erkennt ein horizontales Wischen mit dem Finger und meldet die Richtung:
 * nach links → +1 (weiter), nach rechts → −1 (zurück). Senkrechtes Scrollen
 * bleibt unberührt. Ohne Rückruf werden keine Handler gesetzt.
 */
export function useWischen(onWischen?: (richtung: 1 | -1) => void) {
    const start = useRef<{ x: number; y: number } | null>(null)
    if (!onWischen) return {}
    return {
        onTouchStart: (event: TouchEvent) => {
            const touch = event.touches[0]
            start.current = touch ? { x: touch.clientX, y: touch.clientY } : null
        },
        onTouchEnd: (event: TouchEvent) => {
            const touch = event.changedTouches[0]
            const anfang = start.current
            start.current = null
            if (!touch || !anfang) return
            const dx = touch.clientX - anfang.x
            const dy = touch.clientY - anfang.y
            if (Math.abs(dx) < MINDEST_WEITE || Math.abs(dy) > MAX_HOEHE) return
            onWischen(dx < 0 ? 1 : -1)
        },
    }
}
