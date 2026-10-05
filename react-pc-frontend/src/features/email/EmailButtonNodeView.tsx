import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react';
import { Link2, Pencil, Star, Trash2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { BEWERTUNGS_URL_PLATZHALTER, EMAIL_BUTTON_FARBE } from './emailButton';
import type { EmailButtonOptions } from './emailButtonExtension';

/**
 * Darstellung des E-Mail-Buttons im Vorlagen-Editor: der Button so, wie ihn
 * der Kunde sieht, darunter das Link-Ziel und die Aktionen Ändern/Entfernen.
 */
export function EmailButtonNodeView({ node, selected, deleteNode, getPos, extension, editor }: NodeViewProps) {
  const text = (node.attrs.text as string) || 'Button';
  const href = (node.attrs.href as string) || '';
  const istBewertung = href === BEWERTUNGS_URL_PLATZHALTER;
  const { onBearbeiten } = extension.options as EmailButtonOptions;
  const bearbeitbar = editor.isEditable;

  const bearbeiten = () => {
    const pos = typeof getPos === 'function' ? getPos() : undefined;
    if (pos !== undefined) onBearbeiten({ text, href }, pos);
  };

  return (
    <NodeViewWrapper
      className={cn(
        'not-prose my-2 flex w-fit flex-col gap-1.5 rounded-lg p-1.5 transition-colors',
        selected ? 'bg-rose-50 ring-2 ring-rose-200' : 'hover:bg-slate-50'
      )}
      contentEditable={false}
      data-drag-handle
    >
      <span
        className="inline-block w-fit rounded-md px-[26px] py-[13px] text-[15px] font-bold leading-tight text-white"
        style={{ backgroundColor: EMAIL_BUTTON_FARBE, fontFamily: 'Arial, Helvetica, sans-serif' }}
        onDoubleClick={bearbeitbar ? bearbeiten : undefined}
        title={bearbeitbar ? 'Doppelklick zum Ändern' : undefined}
      >
        {text}
      </span>
      <span className="flex items-center gap-2 text-xs text-slate-500">
        {istBewertung ? (
          <>
            <Star className="w-3.5 h-3.5 text-slate-400" aria-hidden />
            Führt zum Google-Bewertungs-Link
          </>
        ) : (
          <>
            <Link2 className="w-3.5 h-3.5 text-slate-400" aria-hidden />
            <span className="max-w-[320px] truncate">{href || 'Kein Ziel hinterlegt'}</span>
          </>
        )}
        {bearbeitbar && <span className="flex items-center gap-0.5">
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-rose-700 hover:bg-rose-100"
            onClick={bearbeiten}
          >
            <Pencil className="w-3 h-3" aria-hidden /> Ändern
          </button>
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-slate-500 hover:bg-rose-50 hover:text-rose-700"
            onClick={() => deleteNode()}
            aria-label="Button entfernen"
            title="Button entfernen"
          >
            <Trash2 className="w-3 h-3" aria-hidden />
          </button>
        </span>}
      </span>
    </NodeViewWrapper>
  );
}
