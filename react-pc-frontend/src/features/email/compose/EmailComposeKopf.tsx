import { Mail, X } from 'lucide-react';
import { Button } from '../../../components/ui/button';

interface EmailComposeKopfProps {
    titel: string;
    untertitel: string;
    onSchliessen: () => void;
    /** Während Senden oder Speichern gesperrt. */
    schliessenGesperrt?: boolean;
}

/** Kopf des E-Mail-Formulars: Brief-Symbol, Titel, Untertitel und X zum Schließen. */
export function EmailComposeKopf({ titel, untertitel, onSchliessen, schliessenGesperrt = false }: EmailComposeKopfProps) {
    return (
        <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-slate-200 bg-rose-50 flex-shrink-0">
            <div className="flex min-w-0 items-center gap-3 flex-1">
                <div className="w-11 h-11 rounded-full bg-white border border-rose-200 flex items-center justify-center shadow-sm flex-shrink-0">
                    <Mail aria-hidden="true" className="w-5 h-5 text-rose-600" />
                </div>
                <div className="min-w-0 overflow-hidden">
                    <h2 className="text-lg font-semibold text-slate-900 truncate">{titel}</h2>
                    <p className="text-sm text-slate-500 truncate hidden sm:block">{untertitel}</p>
                </div>
            </div>
            <Button
                variant="ghost"
                size="sm"
                onClick={onSchliessen}
                disabled={schliessenGesperrt}
                aria-label="Fenster schließen"
                title="Schließen"
                className="flex-shrink-0 text-slate-500 hover:text-slate-700"
            >
                <X aria-hidden="true" className="w-5 h-5" />
            </Button>
        </div>
    );
}
