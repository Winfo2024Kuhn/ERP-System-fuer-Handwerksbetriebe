import type { ReactNode, Ref } from 'react';
import { AlertTriangle, FileEdit, Globe, Mail, RefreshCw, Search } from 'lucide-react';
import { Button } from '../../components/ui/button';
import type { SuchModus } from './emailSuche';

/** Skeleton der Mail-Liste beim ersten Laden. */
export function EmailListeLaedt() {
    return (
        <div role="status" className="relative divide-y divide-slate-100 motion-safe:animate-pulse">
            <span className="sr-only">E-Mails werden geladen</span>
            {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="px-4 py-3 border-l-[3px] border-l-transparent">
                    <div className="flex items-start justify-between gap-2 mb-1">
                        <div className="h-4 bg-slate-200 rounded w-32" />
                        <div className="h-3 bg-slate-100 rounded w-16" />
                    </div>
                    <div className="h-4 bg-slate-200 rounded w-3/4 mb-1" />
                    <div className="h-3 bg-slate-100 rounded w-full" />
                    <div className="h-3 bg-slate-100 rounded w-2/3 mt-1" />
                </div>
            ))}
        </div>
    );
}

export type LeerArt = 'suche' | 'entwuerfe' | 'ungelesen' | 'ordner';

const LEER_TEXTE: Record<Exclude<LeerArt, 'suche'>, { titel: string; text: string; icon: typeof Mail }> = {
    entwuerfe: { titel: 'Keine Entwürfe', text: 'Entwürfe erscheinen hier sofort nach dem automatischen Speichern', icon: FileEdit },
    ungelesen: { titel: 'Keine ungelesenen E-Mails in diesem Ordner', text: 'Alles gelesen. Neue Nachrichten erscheinen hier automatisch.', icon: Mail },
    ordner: { titel: 'Keine E-Mails', text: 'In diesem Ordner ist nichts vorhanden', icon: Mail },
};

function Rahmen({ icon: Icon, titel, text, children }: { icon: typeof Mail; titel: string; text?: string; children?: ReactNode }) {
    return (
        <div className="flex flex-col items-center justify-center min-h-48 px-6 py-8 text-center gap-2">
            <div className="w-14 h-14 rounded-full bg-slate-100 flex items-center justify-center">
                <Icon aria-hidden="true" className="w-7 h-7 text-slate-400" />
            </div>
            <p className="text-sm font-medium text-slate-600 break-words">{titel}</p>
            {text && <p className="text-xs text-slate-500">{text}</p>}
            {children}
        </div>
    );
}

/** Leere Liste – bei der Suche mit Begriff und dem Angebot, in allen Ordnern zu suchen. */
export function EmailListeLeer({ art, begriff, modus, onAlleOrdner }: {
    art: LeerArt; begriff: string; modus: SuchModus; onAlleOrdner: () => void;
}) {
    if (art !== 'suche') {
        const { titel, text, icon } = LEER_TEXTE[art];
        return <Rahmen icon={icon} titel={titel} text={text} />;
    }
    return (
        <Rahmen icon={modus === 'alle' ? Globe : Search} titel={`Keine E-Mails zu „${begriff.trim()}“ gefunden.`}
            text={modus === 'alle' ? 'Andere oder weniger Wörter versuchen.' : 'Papierkorb, Spam und Gesendet durchsucht „Alle Ordner“.'}>
            {modus === 'ordner' && (
                <Button variant="outline" size="sm" onClick={onAlleOrdner} className="mt-1">
                    <Globe className="w-4 h-4" />In allen Ordnern suchen
                </Button>
            )}
        </Rahmen>
    );
}

/** Die erste Trefferseite kam nicht – Hinweis statt leerer Liste. */
export function EmailSuchFehler({ onErneut }: { onErneut: () => void }) {
    return (
        <Rahmen icon={AlertTriangle} titel="Die Suche hat nicht geklappt." text="Bitte noch einmal versuchen.">
            <Button variant="outline" size="sm" onClick={onErneut} className="mt-1">
                <RefreshCw className="w-4 h-4" />Erneut suchen
            </Button>
        </Rahmen>
    );
}

/** Listenende: lädt beim Hinscrollen (Beobachter am `ref`) oder per Knopf nach. */
export function EmailListenEnde({ laedtMehr, hatMehr, onMehr, ref }: {
    laedtMehr: boolean; hatMehr: boolean; onMehr: () => void; ref?: Ref<HTMLDivElement>;
}) {
    return (
        <div ref={ref} className="px-4 py-3 text-center">
            {laedtMehr ? (
                <div role="status" className="inline-flex items-center gap-2 text-xs text-slate-500">
                    <RefreshCw aria-hidden="true" className="w-3.5 h-3.5 motion-safe:animate-spin" />
                    Weitere E-Mails werden geladen …
                </div>
            ) : hatMehr ? (
                <Button variant="ghost" size="sm" onClick={onMehr} className="text-xs">Weitere laden</Button>
            ) : (
                <span className="text-xs text-slate-400">Ende der Liste</span>
            )}
        </div>
    );
}
