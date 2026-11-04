import { createContext, useContext } from 'react';

/** Verschachtelungstiefe gemeinsamer Dialoge (`Dialog`, `DialogEbene` in `dialog.tsx`). */
export const DialogDepth = createContext(0);

/**
 * Tiefe an dieser Stelle (0 außerhalb jedes Dialogs). Vorschauen legen sich damit über den
 * Dialog, aus dem sie geöffnet werden (`vorschauZIndex` in `lib/modalEbene.ts`).
 */
export function useDialogTiefe(): number {
    return useContext(DialogDepth);
}
