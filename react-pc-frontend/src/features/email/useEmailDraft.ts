import { useCallback, useEffect, useRef, useState } from 'react';
import { EmailDraftSession, type EmailDraftSnapshot } from './emailDraftPersistence';

export function useEmailDraft(snapshot: EmailDraftSnapshot, initialId: number | undefined,
    enabled: boolean, onError: (message: string) => void) {
    const [session] = useState(() => new EmailDraftSession(initialId));
    const [status, setStatus] = useState<'idle' | 'dirty' | 'saving' | 'saved' | 'error'>(initialId ? 'saved' : 'idle');
    const dirty = useRef(false);
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const latest = useRef(snapshot);
    const version = useRef(0);
    const paused = useRef(false);
    useEffect(() => { latest.current = snapshot; }, [snapshot]);

    const markDirty = useCallback(() => {
        dirty.current = true;
        version.current += 1;
        setStatus('dirty');
    }, []);

    const flush = useCallback(async () => {
        clearTimeout(timer.current);
        if (!enabled || paused.current || !dirty.current) return;
        const savingVersion = version.current;
        setStatus('saving');
        try {
            await session.save(latest.current);
            if (version.current === savingVersion) {
                dirty.current = false;
                setStatus('saved');
            } else setStatus('dirty');
        } catch (error) {
            setStatus('error');
            onError(error instanceof Error ? error.message : 'Entwurf konnte nicht gespeichert werden.');
            throw error;
        }
    }, [enabled, onError, session]);

    useEffect(() => {
        if (enabled && dirty.current && !paused.current) {
            timer.current = setTimeout(() => { void flush().catch(() => undefined); }, 2000);
        }
        return () => clearTimeout(timer.current);
    }, [snapshot, enabled, flush]);

    useEffect(() => {
        const beforeUnload = (event: BeforeUnloadEvent) => {
            if (dirty.current) { event.preventDefault(); event.returnValue = ''; }
        };
        window.addEventListener('beforeunload', beforeUnload);
        return () => window.removeEventListener('beforeunload', beforeUnload);
    }, []);

    const pause = useCallback(async () => {
        paused.current = true;
        clearTimeout(timer.current);
        await session.pause();
    }, [session]);
    const resume = useCallback(() => {
        paused.current = false;
        session.resume();
        if (dirty.current) timer.current = setTimeout(() => { void flush().catch(() => undefined); }, 2000);
    }, [flush, session]);
    const remove = useCallback(async () => {
        await pause();
        dirty.current = false;
        await session.remove();
    }, [pause, session]);
    const complete = useCallback(async () => {
        await pause();
        dirty.current = false;
        await session.complete();
    }, [pause, session]);
    const getDraftId = useCallback(() => session.id, [session]);
    const label = {
        idle: '', dirty: 'Änderungen noch nicht gespeichert', saving: 'Entwurf wird gespeichert…',
        saved: 'Entwurf gespeichert', error: 'Entwurf nicht gespeichert',
    }[status];
    return { markDirty, flush, pause, resume, remove, complete, getDraftId, label, status };
}
