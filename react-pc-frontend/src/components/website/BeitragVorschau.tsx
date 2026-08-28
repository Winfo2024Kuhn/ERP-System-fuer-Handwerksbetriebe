import { useMemo } from 'react';
import { bereinigeBeitragsHtml } from './textumwandlung';

export interface BeitragVorschauProps {
    titel: string;
    /** Richtext-HTML des Beitrags. Wird vor der Ausgabe bereinigt. */
    textHtml: string;
    bildUrls: { url: string; altText: string }[];
    /** SQLite-Zeitstempel der Website oder null bei einem Entwurf. */
    veroeffentlichtAm: string | null;
}

const MONATE = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
];

/**
 * Zeigt einen Beitrag so, wie er auf bauschlosserei-kuhn.de erscheint.
 *
 * Nachbau von molecular-mercury/src/pages/aktuelles/[slug].astro. Farben,
 * Abstaende und Schriftschnitte sind von dort abgelesen und sollen sich mit
 * der echten Seite decken. Aendert sich das Website-Layout, gehoert diese
 * Datei nachgezogen.
 *
 * Haelt bewusst keinen Zustand und laedt nichts nach, damit sie im Editor
 * bei jedem Tastendruck neu gerendert werden kann.
 */
export function BeitragVorschau({ titel, textHtml, bildUrls, veroeffentlichtAm }: BeitragVorschauProps) {
    const sauber = useMemo(() => bereinigeBeitragsHtml(textHtml), [textHtml]);
    const angezeigterTitel = titel.trim() || 'Ohne Titel';

    return (
        <div className="bg-[#faf9f6] rounded-xl border border-slate-200 overflow-y-auto">
            <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
                <nav className="mb-10">
                    <ol className="flex items-center gap-2 text-xs tracking-[0.15em] uppercase text-stone-400">
                        <li>Start</li>
                        <li className="text-stone-300">/</li>
                        <li>Aktuelles</li>
                        <li className="text-stone-300">/</li>
                        <li className="text-stone-600 font-medium truncate">{angezeigterTitel}</li>
                    </ol>
                </nav>

                <p className="text-xs tracking-[0.15em] uppercase text-stone-400 mb-3">
                    {veroeffentlichtAm ? monatUndJahr(veroeffentlichtAm) : 'Noch nicht veröffentlicht'}
                </p>

                <h1 className="text-4xl sm:text-5xl font-light text-stone-800 mb-6 leading-[1.1]">
                    {angezeigterTitel}
                </h1>
                <div className="w-12 h-px bg-[#500010]/40 mb-8" />

                {bildUrls.length > 0 && (
                    <div className="columns-1 sm:columns-2 gap-4 sm:gap-5 space-y-4 sm:space-y-5 mb-10">
                        {bildUrls.map(bild => (
                            <div key={bild.url} className="break-inside-avoid overflow-hidden rounded-2xl">
                                <img
                                    src={bild.url}
                                    alt={bild.altText || angezeigterTitel}
                                    className="w-full h-auto block"
                                    loading="lazy"
                                />
                            </div>
                        ))}
                    </div>
                )}

                {sauber ? (
                    <div
                        className="beitrag-inhalt text-stone-600 leading-relaxed"
                        dangerouslySetInnerHTML={{ __html: sauber }}
                    />
                ) : (
                    <p className="text-stone-400 italic">Hier erscheint der Text.</p>
                )}
            </div>

            <style>{`
                .beitrag-inhalt p { margin-bottom: 1rem; }
                .beitrag-inhalt p:last-child { margin-bottom: 0; }
                .beitrag-inhalt p:empty { display: none; }
                .beitrag-inhalt ul { list-style: disc; padding-left: 1.5rem; margin-bottom: 1rem; }
                .beitrag-inhalt ol { list-style: decimal; padding-left: 1.5rem; margin-bottom: 1rem; }
                .beitrag-inhalt li { margin-bottom: 0.25rem; }
                .beitrag-inhalt b, .beitrag-inhalt strong { font-weight: 600; }
                .beitrag-inhalt i, .beitrag-inhalt em { font-style: italic; }
            `}</style>
        </div>
    );
}

/**
 * Macht aus dem Zeitstempel der Website "August 2026". Die Website speichert
 * SQLite-Zeitstempel im Format "2026-08-27 10:00:00", das der Date-Konstruktor
 * nicht ueberall zuverlaessig liest. Deshalb wird der Anfang selbst zerlegt.
 */
function monatUndJahr(zeitstempel: string): string {
    const treffer = /^(\d{4})-(\d{2})/.exec(zeitstempel.trim());
    if (!treffer) return zeitstempel;
    const jahr = treffer[1];
    const monat = MONATE[Number(treffer[2]) - 1] ?? '';
    return `${monat} ${jahr}`.trim();
}
