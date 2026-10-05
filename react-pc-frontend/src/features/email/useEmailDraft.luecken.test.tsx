import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { EmailDraftSnapshot } from './emailDraftPersistence';

const session = vi.hoisted(() => ({
    id: null as number | null,
    save: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    remove: vi.fn(),
    complete: vi.fn(),
}));
const ctorArgs = vi.hoisted(() => [] as unknown[]);

vi.mock('./emailDraftPersistence', () => ({
    EmailDraftSession: class {
        constructor(id?: number) { ctorArgs.push(id); }
        get id() { return session.id; }
        save = session.save;
        pause = session.pause;
        resume = session.resume;
        remove = session.remove;
        complete = session.complete;
    },
}));

import { useEmailDraft } from './useEmailDraft';

const snapshot = (subject = 'Betreff'): EmailDraftSnapshot => ({
    content: {
        recipient: 'max.mustermann@example.com', cc: '', subject, body: '<p>Hallo</p>', fromAddress: null,
        replyEmailId: null, projektId: null, anfrageId: null, geschaeftsdokument: false,
    },
    files: [],
});

describe('useEmailDraft', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        ctorArgs.length = 0;
        session.id = null;
        session.save.mockReset().mockResolvedValue(undefined);
        session.pause.mockReset().mockResolvedValue(undefined);
        session.resume.mockReset();
        session.remove.mockReset().mockResolvedValue(undefined);
        session.complete.mockReset().mockResolvedValue(undefined);
    });
    afterEach(() => vi.useRealTimers());

    it('startet ohne Draft-ID im Zustand idle, mit ID im Zustand saved', () => {
        const a = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        expect(a.result.current.status).toBe('idle');
        expect(a.result.current.label).toBe('');
        const b = renderHook(() => useEmailDraft(snapshot(), 7, true, vi.fn()));
        expect(b.result.current.status).toBe('saved');
        expect(b.result.current.label).toBe('Entwurf gespeichert');
        expect(ctorArgs).toContain(7);
    });

    it('markDirty setzt dirty und speichert nach 2 Sekunden automatisch', async () => {
        const { result, rerender } = renderHook(({ snap }) => useEmailDraft(snap, undefined, true, vi.fn()),
            { initialProps: { snap: snapshot() } });
        act(() => result.current.markDirty());
        expect(result.current.status).toBe('dirty');
        expect(result.current.label).toBe('Änderungen noch nicht gespeichert');
        // Timer wird erst beim naechsten Snapshot-Wechsel gesetzt
        rerender({ snap: snapshot('Neu') });
        expect(session.save).not.toHaveBeenCalled();
        await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
        expect(session.save).toHaveBeenCalledTimes(1);
        expect(session.save.mock.calls[0][0].content.subject).toBe('Neu');
        expect(result.current.status).toBe('saved');
    });

    it('flush speichert nichts, wenn nichts geaendert wurde oder deaktiviert ist', async () => {
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        await act(async () => { await result.current.flush(); });
        expect(session.save).not.toHaveBeenCalled();

        const disabled = renderHook(() => useEmailDraft(snapshot(), undefined, false, vi.fn()));
        act(() => disabled.result.current.markDirty());
        await act(async () => { await disabled.result.current.flush(); });
        expect(session.save).not.toHaveBeenCalled();
    });

    it('Fehler beim Speichern: Status error, onError mit Meldung, flush wirft weiter', async () => {
        session.save.mockRejectedValue(new Error('Server weg'));
        const onError = vi.fn();
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, onError));
        act(() => result.current.markDirty());
        await act(async () => { await expect(result.current.flush()).rejects.toThrow('Server weg'); });
        expect(result.current.status).toBe('error');
        expect(result.current.label).toBe('Entwurf nicht gespeichert');
        expect(onError).toHaveBeenCalledWith('Server weg');
    });

    it('Nicht-Error-Fehler nutzt die Standardmeldung', async () => {
        session.save.mockRejectedValue('kaputt');
        const onError = vi.fn();
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, onError));
        act(() => result.current.markDirty());
        await act(async () => { await expect(result.current.flush()).rejects.toBe('kaputt'); });
        expect(onError).toHaveBeenCalledWith('Entwurf konnte nicht gespeichert werden.');
    });

    it('Aenderung waehrend des Speicherns bleibt dirty', async () => {
        let finish!: () => void;
        session.save.mockImplementation(() => new Promise<void>(resolve => { finish = resolve; }));
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        act(() => result.current.markDirty());
        let promise!: Promise<void>;
        act(() => { promise = result.current.flush(); });
        expect(result.current.status).toBe('saving');
        act(() => result.current.markDirty());
        await act(async () => { finish(); await promise; });
        expect(result.current.status).toBe('dirty');
    });

    it('pause verhindert Speichern, resume plant es bei dirty neu', async () => {
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        act(() => result.current.markDirty());
        await act(async () => { await result.current.pause(); });
        expect(session.pause).toHaveBeenCalled();
        await act(async () => { await result.current.flush(); });
        expect(session.save).not.toHaveBeenCalled();

        act(() => result.current.resume());
        expect(session.resume).toHaveBeenCalled();
        await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
        expect(session.save).toHaveBeenCalledTimes(1);
    });

    it('resume ohne Aenderungen plant keinen Speichervorgang', async () => {
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        act(() => result.current.resume());
        await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
        expect(session.save).not.toHaveBeenCalled();
    });

    it('remove und complete pausieren zuerst und loeschen den dirty-Zustand', async () => {
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        act(() => result.current.markDirty());
        await act(async () => { await result.current.remove(); });
        expect(session.pause).toHaveBeenCalledTimes(1);
        expect(session.remove).toHaveBeenCalledTimes(1);
        const evt = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(evt);
        expect(evt.defaultPrevented).toBe(false);

        act(() => result.current.markDirty());
        await act(async () => { await result.current.complete(); });
        expect(session.complete).toHaveBeenCalledTimes(1);
    });

    it('getDraftId liefert die ID der Session', () => {
        session.id = 42;
        const { result } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        expect(result.current.getDraftId()).toBe(42);
    });

    it('beforeunload wird nur bei ungespeicherten Aenderungen blockiert', () => {
        const { result, unmount } = renderHook(() => useEmailDraft(snapshot(), undefined, true, vi.fn()));
        const clean = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(clean);
        expect(clean.defaultPrevented).toBe(false);

        act(() => result.current.markDirty());
        const dirty = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(dirty);
        expect(dirty.defaultPrevented).toBe(true);

        unmount();
        const after = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(false);
    });
});
