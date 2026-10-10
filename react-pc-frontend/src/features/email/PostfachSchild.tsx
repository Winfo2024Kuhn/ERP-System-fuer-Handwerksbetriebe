import { cn } from '../../lib/utils';
import { formatPostfach, lokalerTeil, type PostfachKurz } from './postfach';

interface PostfachSchildProps {
    postfaecher?: PostfachKurz[] | null;
    className?: string;
}

/**
 * Kleines, dezentes Schild an jeder Mail: in welchem Postfach sie liegt
 * („info@“). Liegt eine Mail in mehreren Postfächern (z. B. an info@ und max@
 * gleichzeitig adressiert), stehen alle da. Die volle Adresse steht im Tooltip.
 */
export function PostfachSchild({ postfaecher, className }: PostfachSchildProps) {
    if (!postfaecher || postfaecher.length === 0) return null;
    return (
        <span className={cn('inline-flex min-w-0 flex-wrap items-center gap-1', className)} data-testid="postfach-schild">
            {postfaecher.map(postfach => (
                <span
                    key={postfach.id}
                    title={formatPostfach(postfach)}
                    // Volle Adresse steht im Tooltip – Kürzen sehr langer lokaler Teile ist gewollt.
                    data-kuerzung-erlaubt=""
                    className="max-w-[10rem] truncate rounded border border-slate-200 bg-white px-1.5 py-px text-[11px] font-medium leading-4 text-slate-500"
                >
                    <span className="sr-only">Postfach {postfach.emailAdresse}</span>
                    <span aria-hidden="true">{lokalerTeil(postfach.emailAdresse)}</span>
                </span>
            ))}
        </span>
    );
}
