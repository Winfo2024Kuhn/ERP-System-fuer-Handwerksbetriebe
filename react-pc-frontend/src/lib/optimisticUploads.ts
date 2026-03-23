import type { ProjektDokument } from '../types';

type NotizMitBildern<TBild extends { id: number }> = {
    id: number;
    bilder?: TBild[];
};

export function mergeUploadedDokumente(
    existing: ProjektDokument[],
    uploaded: ProjektDokument[],
): ProjektDokument[] {
    if (uploaded.length === 0) {
        return existing;
    }

    const uploadedIds = new Set(uploaded.map((dokument) => dokument.id));
    const rest = existing.filter((dokument) => !uploadedIds.has(dokument.id));
    return [...uploaded, ...rest];
}

export function appendBildToNotiz<
    TBild extends { id: number },
    TNotiz extends NotizMitBildern<TBild>,
>(notes: TNotiz[], notizId: number, bild: TBild): TNotiz[] {
    return notes.map((note) => {
        if (note.id !== notizId) {
            return note;
        }

        const bilder = [bild, ...(note.bilder ?? []).filter((existingBild) => existingBild.id !== bild.id)];
        return { ...note, bilder };
    });
}

export function removeBildFromNotiz<
    TBild extends { id: number },
    TNotiz extends NotizMitBildern<TBild>,
>(notes: TNotiz[], notizId: number, bildId: number): TNotiz[] {
    return notes.map((note) => {
        if (note.id !== notizId || !note.bilder) {
            return note;
        }

        return {
            ...note,
            bilder: note.bilder.filter((bild) => bild.id !== bildId),
        };
    });
}