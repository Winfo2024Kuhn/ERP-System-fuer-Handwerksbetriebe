import type { Editor } from '@tiptap/core';
import type { Fragment } from '@tiptap/pm/model';
import type { Transaction } from '@tiptap/pm/state';
import { TextSelection } from '@tiptap/pm/state';
import { ReplaceStep } from '@tiptap/pm/transform';

export type TiptapAenderungsArt = 'tippen' | 'sonstiges';

/**
 * Knotentypen, die ein reiner Tippvorgang erzeugen kann: Zeichen, Absaetze
 * (Enter/splitBlock), Zeilenumbrueche (Shift+Enter) und Listenpunkte (Enter
 * innerhalb einer Liste). Alles andere - Bilder, Marken (Fett/Kursiv/...),
 * Tabellen, ... - gilt als "sonstiges".
 */
const TIPP_KNOTENTYPEN = new Set(['text', 'paragraph', 'hardBreak', 'listItem']);

/** true, wenn der Fragment-Baum (rekursiv) ausschliesslich TIPP_KNOTENTYPEN enthaelt. */
function enthaeltNurTippKnoten(content: Fragment): boolean {
    let nurErlaubt = true;
    content.descendants((node) => {
        if (!TIPP_KNOTENTYPEN.has(node.type.name)) {
            nurErlaubt = false;
            return false;
        }
        return true;
    });
    return nurErlaubt;
}

/**
 * Tippen = nur Text-/Absatz-Ersetzungen ohne Paste/Drop/Cut und ohne
 * angehaengte Transaktionen (z.B. eine Eingaberegel, die "- " in eine Liste
 * verwandelt - das ist mehr als reines Tippen).
 */
export function aenderungsArtVon(
    transaction: Transaction,
    appendedTransactions: readonly Transaction[],
): TiptapAenderungsArt {
    if (transaction.getMeta('paste') || transaction.getMeta('uiEvent')) {
        return 'sonstiges';
    }
    if (appendedTransactions.some((t) => t.docChanged)) {
        return 'sonstiges';
    }
    if (transaction.steps.length === 0) {
        return 'sonstiges';
    }
    for (const step of transaction.steps) {
        if (!(step instanceof ReplaceStep) || !enthaeltNurTippKnoten(step.slice.content)) {
            return 'sonstiges';
        }
    }
    return 'tippen';
}

/**
 * Uebernimmt einen von aussen gesetzten Wert (z.B. nach Rueckgaengig/Wiederholen
 * im Dokumentverlauf). Im Standardmodus unveraendert wie bisher: setContent meldet
 * ein normales Update. Im Verlaufsmodus dagegen ohne Update-Meldung (der Wert kam
 * ja bereits aus dem Verlauf, kein neuer Schritt noetig) und mit dem Cursor an der
 * ersten Stelle, an der sich alter und neuer Inhalt unterscheiden - das ist meist
 * genau die Stelle, die der Rueckgaengig-Schritt veraendert hat.
 */
export function setzeInhaltVonAussen(editor: Editor, wert: string, verlaufsModus: boolean): void {
    if (!verlaufsModus) {
        editor.commands.setContent(wert);
        return;
    }

    const alterInhalt = editor.state.doc.content;
    editor.commands.setContent(wert, { emitUpdate: false });

    const diff = alterInhalt.findDiffStart(editor.state.doc.content);
    if (diff == null) return;

    const ziel = Math.min(Math.max(diff, 0), editor.state.doc.content.size);
    editor.view.dispatch(
        editor.state.tr.setSelection(TextSelection.near(editor.state.doc.resolve(ziel))),
    );
}
