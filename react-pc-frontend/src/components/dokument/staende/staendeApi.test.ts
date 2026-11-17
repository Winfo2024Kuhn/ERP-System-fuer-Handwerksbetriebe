import { afterEach, describe, expect, it, vi } from 'vitest';
import { halteStandFest } from './staendeApi';
import { jsonAntwort } from './staendeTestdaten';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('halteStandFest', () => {
    it('meldet dem Server den Export und warnt nicht bei Erfolg', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonAntwort(200, { festgehalten: true, stand: null }));
        vi.stubGlobal('fetch', fetchMock);
        const warnen = vi.fn();

        await halteStandFest(7, warnen);

        expect(fetchMock).toHaveBeenCalledWith('/api/ausgangs-dokumente/7/staende/festhalten', expect.objectContaining({ method: 'POST' }));
        expect(warnen).not.toHaveBeenCalled();
    });

    it('warnt bei Serverfehler, wirft aber nicht (Druck und Download laufen weiter)', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonAntwort(500, { message: 'kaputt' })));
        const warnen = vi.fn();

        await expect(halteStandFest(7, warnen)).resolves.toBeUndefined();

        expect(warnen).toHaveBeenCalledWith('Der Stand für den Verlauf konnte nicht festgehalten werden.');
    });

    it('warnt auch ohne Verbindung', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
        const warnen = vi.fn();
        await halteStandFest(7, warnen);
        expect(warnen).toHaveBeenCalledTimes(1);
    });
});
