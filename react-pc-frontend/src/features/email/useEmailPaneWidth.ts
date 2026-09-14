import { useEffect, useRef, useState } from 'react';

/** Misst den verfügbaren Platz einschließlich Toasts und Fensteränderungen. */
export function useEmailPaneWidth() {
    const ref = useRef<HTMLDivElement>(null);
    const [width, setWidth] = useState(() => window.innerWidth);
    useEffect(() => {
        const element = ref.current;
        if (!element || typeof ResizeObserver === 'undefined') return;
        const observer = new ResizeObserver(([entry]) => {
            if (entry.contentRect.width > 0) setWidth(entry.contentRect.width);
        });
        observer.observe(element);
        return () => observer.disconnect();
    }, []);
    return { ref, width };
}
