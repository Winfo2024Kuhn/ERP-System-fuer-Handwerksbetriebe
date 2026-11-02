import { afterEach, describe, expect, it, vi } from 'vitest';
import { starteDownload } from './herunterladen';

describe('starteDownload', () => {
    afterEach(() => vi.restoreAllMocks());

    it('klickt einen Link mit Dateinamen an und entfernt ihn wieder', () => {
        const klick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
            expect(this.getAttribute('href')).toBe('/api/firmendokumente/7/datei?download=true');
            expect(this.download).toBe('Torprüfung.pdf');
            expect(this.isConnected).toBe(true);
        });
        starteDownload('/api/firmendokumente/7/datei?download=true', 'Torprüfung.pdf');
        expect(klick).toHaveBeenCalledTimes(1);
        expect(document.querySelector('a[download]')).toBeNull();
    });
});
