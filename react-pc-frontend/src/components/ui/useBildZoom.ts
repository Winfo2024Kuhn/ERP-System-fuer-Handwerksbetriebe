import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';

export const MIN_SCALE = 1;
export const MAX_SCALE = 6;
const WHEEL_STEP = 0.2;
export const BUTTON_STEP = 0.5;
const DBL_CLICK_TARGET = 2.5;

/**
 * Zoom und Verschieben im ImageViewer: Mausrad (um den Mauszeiger), Knöpfe, Doppelklick,
 * Ziehen bei Vergrößerung. `buehneRef` ist die Fläche, auf der das Mausrad zoomt; `aktiv`
 * bindet den Mausrad-Lauscher, sobald die Fläche wirklich da ist.
 */
export function useBildZoom(buehneRef: RefObject<HTMLElement | null>, aktiv: boolean) {
    const [scale, setScale] = useState(1);
    const [offset, setOffset] = useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const dragStart = useRef({ x: 0, y: 0, ox: 0, oy: 0 });

    const resetZoom = useCallback(() => {
        setScale(1);
        setOffset({ x: 0, y: 0 });
    }, []);

    /**
     * Stabil, weil `setScale` funktional arbeitet. Der optionale Fokuspunkt hält den Bildpunkt
     * unter dem Mauszeiger fest. Liefert `true`, wenn sich die Vergrößerung geändert hat.
     */
    const zoomBy = useCallback((delta: number, focusClientX?: number, focusClientY?: number): boolean => {
        const stage = buehneRef.current;
        let changed = false;
        setScale(prev => {
            const next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, prev + delta));
            if (next === prev) return prev;
            changed = true;
            if (next === 1) {
                setOffset({ x: 0, y: 0 });
                return next;
            }
            if (stage && focusClientX !== undefined && focusClientY !== undefined) {
                const rect = stage.getBoundingClientRect();
                const focusX = focusClientX - (rect.left + rect.width / 2);
                const focusY = focusClientY - (rect.top + rect.height / 2);
                const ratio = next / prev;
                setOffset(o => ({
                    x: focusX - (focusX - o.x) * ratio,
                    y: focusY - (focusY - o.y) * ratio,
                }));
            }
            return next;
        });
        return changed;
    }, [buehneRef]);

    // Nicht-passiver Lauscher, damit das Mausrad die Seite nicht scrollt, solange es zoomt.
    useEffect(() => {
        const stage = buehneRef.current;
        if (!aktiv || !stage) return;
        const onWheel = (e: WheelEvent) => {
            const changed = zoomBy(e.deltaY < 0 ? WHEEL_STEP : -WHEEL_STEP, e.clientX, e.clientY);
            if (changed) e.preventDefault();
        };
        stage.addEventListener('wheel', onWheel, { passive: false });
        return () => stage.removeEventListener('wheel', onWheel);
    }, [aktiv, buehneRef, zoomBy]);

    useEffect(() => {
        if (!isDragging) return;
        const onMove = (e: MouseEvent) => {
            setOffset({
                x: dragStart.current.ox + (e.clientX - dragStart.current.x),
                y: dragStart.current.oy + (e.clientY - dragStart.current.y),
            });
        };
        const onUp = () => setIsDragging(false);
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
        return () => {
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };
    }, [isDragging]);

    const isZoomed = scale > 1;

    const beiMausDruck = (e: React.MouseEvent) => {
        if (!isZoomed) return;
        e.stopPropagation();
        e.preventDefault();
        setIsDragging(true);
        dragStart.current = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y };
    };

    const beiDoppelklick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (isZoomed) resetZoom();
        else zoomBy(DBL_CLICK_TARGET - 1, e.clientX, e.clientY);
    };

    return { scale, offset, isDragging, isZoomed, zoomBy, resetZoom, beiMausDruck, beiDoppelklick };
}
