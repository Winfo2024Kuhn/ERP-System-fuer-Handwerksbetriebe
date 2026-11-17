import { afterEach, describe, expect, it, vi } from 'vitest';
import { notifyInhaltZurueckgeholt, onDokumentChanged, onInhaltZurueckgeholt } from './dokumentChannel';

describe('dokumentChannel – Inhalt zurückgeholt', () => {
    const abmelden: (() => void)[] = [];
    afterEach(() => {
        abmelden.splice(0).forEach((f) => f());
    });

    it('meldet das Zurückholen sofort auch im eigenen Tab', () => {
        const empfaenger = vi.fn();
        abmelden.push(onInhaltZurueckgeholt(empfaenger));

        notifyInhaltZurueckgeholt({ dokumentId: 7, projektId: 3, standNummer: 2 });

        expect(empfaenger).toHaveBeenCalledTimes(1);
        expect(empfaenger).toHaveBeenCalledWith(expect.objectContaining({ dokumentId: 7, projektId: 3, standNummer: 2 }));
    });

    it('nach dem Abmelden kommt nichts mehr an', () => {
        const empfaenger = vi.fn();
        const ab = onInhaltZurueckgeholt(empfaenger);
        ab();

        notifyInhaltZurueckgeholt({ dokumentId: 7, standNummer: 2 });

        expect(empfaenger).not.toHaveBeenCalled();
    });

    it('andere Tabs bekommen es als normale Dokumentänderung mit Kennzeichen', async () => {
        const anderesTab = new BroadcastChannel('dokument-changes');
        const empfangen = new Promise<unknown>((resolve) => {
            anderesTab.onmessage = (e) => resolve(e.data);
        });

        notifyInhaltZurueckgeholt({ dokumentId: 7, anfrageId: 4, standNummer: 2 });

        expect(await empfangen).toEqual({
            type: 'dokument-changed', dokumentId: 7, anfrageId: 4, inhaltZurueckgeholt: { standNummer: 2 },
        });
        anderesTab.close();
    });

    it('eine gewöhnliche Änderung aus einem anderen Tab löst kein Zurückgeholt aus', async () => {
        const empfaenger = vi.fn();
        const geaendert = vi.fn();
        abmelden.push(onInhaltZurueckgeholt(empfaenger));
        abmelden.push(onDokumentChanged(geaendert));
        const anderesTab = new BroadcastChannel('dokument-changes');

        anderesTab.postMessage({ type: 'dokument-changed', dokumentId: 7 });
        await vi.waitFor(() => expect(geaendert).toHaveBeenCalled());

        expect(empfaenger).not.toHaveBeenCalled();
        anderesTab.close();
    });

    it('Zurückgeholt aus einem anderen Tab kommt an', async () => {
        const empfaenger = vi.fn();
        abmelden.push(onInhaltZurueckgeholt(empfaenger));
        const anderesTab = new BroadcastChannel('dokument-changes');

        anderesTab.postMessage({ type: 'dokument-changed', dokumentId: 9, inhaltZurueckgeholt: { standNummer: 4 } });

        await vi.waitFor(() => expect(empfaenger).toHaveBeenCalledWith({ dokumentId: 9, projektId: undefined, anfrageId: undefined, standNummer: 4 }));
        anderesTab.close();
    });
});
