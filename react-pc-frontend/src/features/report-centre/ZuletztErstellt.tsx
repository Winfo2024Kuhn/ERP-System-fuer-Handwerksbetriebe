import { Download, ExternalLink, FileText } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { formatBytes } from '../../lib/dateiGroesse';
import { starteDownload } from '../../lib/herunterladen';
import type { ErstellterBericht } from './useZuletztErstellt';

const UHRZEIT = new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' });

/** Liste der in dieser Sitzung erstellten PDFs mit „Öffnen“ und „Herunterladen“. */
export function ZuletztErstellt({ liste }: { liste: ErstellterBericht[] }) {
    return (
        <section aria-labelledby="zuletzt-erstellt" className="rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 px-5 py-4">
                <h2 id="zuletzt-erstellt" className="text-base font-semibold text-slate-900">Zuletzt erstellt</h2>
                <p className="text-sm text-slate-500">Nur bis du die Seite verlässt – gespeichert wird hier nichts.</p>
            </div>
            {liste.length === 0 ? (
                <div className="flex flex-col items-center gap-2 px-5 py-10 text-center">
                    <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                        <FileText aria-hidden="true" className="h-6 w-6" />
                    </span>
                    <p className="font-medium text-slate-700">Noch kein Bericht erstellt</p>
                    <p className="text-sm text-slate-500">Mit „Neuer Bericht“ erstellst du in drei Schritten dein erstes PDF.</p>
                </div>
            ) : (
                <ul className="divide-y divide-slate-100">
                    {liste.map(bericht => (
                        <li key={bericht.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                            <FileText aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600" />
                            <div className="min-w-0 flex-1">
                                <p className="truncate font-medium text-slate-900" title={bericht.dateiname}>{bericht.dateiname}</p>
                                <p className="text-sm text-slate-500">
                                    {bericht.titel} · {formatBytes(bericht.groesse)} · {UHRZEIT.format(bericht.erstelltUm)} Uhr
                                </p>
                            </div>
                            <div className="flex gap-2">
                                <Button size="sm" variant="outline" aria-label={`${bericht.dateiname} öffnen`}
                                    onClick={() => window.open(bericht.url, '_blank', 'noopener')}>
                                    <ExternalLink aria-hidden="true" className="h-4 w-4" />Öffnen
                                </Button>
                                <Button size="sm" variant="outline" aria-label={`${bericht.dateiname} herunterladen`}
                                    onClick={() => starteDownload(bericht.url, bericht.dateiname)}>
                                    <Download aria-hidden="true" className="h-4 w-4" />Herunterladen
                                </Button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
