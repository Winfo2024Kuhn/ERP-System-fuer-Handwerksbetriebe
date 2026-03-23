import { describe, expect, it } from 'vitest';
import type { ProjektDokument } from '../types';
import { appendBildToNotiz, mergeUploadedDokumente, removeBildFromNotiz } from './optimisticUploads';

describe('optimisticUploads', () => {
    it('stellt hochgeladene Dokumente sofort voran und entfernt Dubletten', () => {
        const existing = [
            { id: 1, originalDateiname: 'alt.pdf', url: '/api/dokumente/alt.pdf', dokumentGruppe: 'DIVERSE_DOKUMENTE' },
            { id: 2, originalDateiname: 'alt-2.pdf', url: '/api/dokumente/alt-2.pdf', dokumentGruppe: 'BILDER' },
        ] satisfies ProjektDokument[];

        const uploaded = [
            { id: 3, originalDateiname: 'neu.pdf', url: '/api/dokumente/neu.pdf', dokumentGruppe: 'DIVERSE_DOKUMENTE' },
            { id: 2, originalDateiname: 'alt-2-ersetzt.pdf', url: '/api/dokumente/alt-2-ersetzt.pdf', dokumentGruppe: 'BILDER' },
        ] satisfies ProjektDokument[];

        expect(mergeUploadedDokumente(existing, uploaded)).toEqual([
            uploaded[0],
            uploaded[1],
            existing[0],
        ]);
    });

    it('fügt ein neues Bild an einer Notiz optimistisch ein', () => {
        const notes = [
            { id: 10, bilder: [{ id: 1, originalDateiname: 'alt.jpg', url: '/img/alt.jpg' }] },
            { id: 20, bilder: [] },
        ];

        expect(appendBildToNotiz(notes, 10, { id: 2, originalDateiname: 'neu.jpg', url: '/img/neu.jpg' })).toEqual([
            {
                id: 10,
                bilder: [
                    { id: 2, originalDateiname: 'neu.jpg', url: '/img/neu.jpg' },
                    { id: 1, originalDateiname: 'alt.jpg', url: '/img/alt.jpg' },
                ],
            },
            { id: 20, bilder: [] },
        ]);
    });

    it('entfernt ein Bild an einer Notiz sofort aus dem Zustand', () => {
        const notes = [
            {
                id: 10,
                bilder: [
                    { id: 1, originalDateiname: 'eins.jpg', url: '/img/eins.jpg' },
                    { id: 2, originalDateiname: 'zwei.jpg', url: '/img/zwei.jpg' },
                ],
            },
        ];

        expect(removeBildFromNotiz(notes, 10, 1)).toEqual([
            {
                id: 10,
                bilder: [{ id: 2, originalDateiname: 'zwei.jpg', url: '/img/zwei.jpg' }],
            },
        ]);
    });
});