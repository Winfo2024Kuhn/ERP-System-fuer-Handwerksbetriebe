import { useEffect } from 'react';
import { useBlocker } from 'react-router-dom';

/** Auch Ribbon-Links und Zurück/Vorwärts warten auf den aktuellen Entwurf. */
export function EmailNavigationGuard({ active, persist }: { active: boolean; persist: () => Promise<boolean> }) {
    const blocker = useBlocker(active);
    useEffect(() => {
        if (blocker.state !== 'blocked') return;
        let current = true;
        void persist().then(saved => {
            if (!current) return;
            if (saved) blocker.proceed();
            else blocker.reset();
        });
        return () => { current = false; };
    }, [blocker, persist]);
    return null;
}
