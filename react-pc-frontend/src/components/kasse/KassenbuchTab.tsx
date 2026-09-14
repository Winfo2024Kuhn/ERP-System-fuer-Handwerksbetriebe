import { useEffect, useState } from 'react';
import { useToast } from '../ui/toast';
import type { SaldoInfo } from './NeueBuchungDialog';
import type { Kassenbuch, Sachkonto } from '../../types';
import { KasseShortcuts } from './KasseShortcuts';
import { KassenbuchJournal } from './KassenbuchJournal';
import { KasseErklaerkasten } from './KasseErklaerkasten';

export function KassenbuchTab({
    sachkonten,
    kassenbuch,
    kassenLoading,
    kasseVon,
    kasseBis,
    onVonChange,
    onBisChange,
    kasseSearch,
    onSearchChange,
    onSelectBeleg,
    onGeaendert,
}: {
    sachkonten: Sachkonto[];
    kassenbuch: Kassenbuch | null;
    kassenLoading: boolean;
    kasseVon: string;
    kasseBis: string;
    onVonChange: (v: string) => void;
    onBisChange: (v: string) => void;
    kasseSearch: string;
    onSearchChange: (v: string) => void;
    onSelectBeleg: (id: number) => void;
    onGeaendert: () => void;
}) {
    const [saldo, setSaldo] = useState<SaldoInfo | null>(null);
    const toast = useToast();
    useEffect(() => {
        // Erst nach dem Journal-Abruf laden: so entsteht beim Oeffnen nicht
        // ein zweiter Abruf, sobald die zuvor leere Liste angekommen ist.
        if (kassenLoading || !kassenbuch) return;
        let abgebrochen = false;
        void (async () => {
            try {
                const antwort = await fetch('/api/buchhaltung/kasse/saldo');
                if (!antwort.ok) throw new Error('Kassenstand nicht verfügbar');
                const daten: SaldoInfo = await antwort.json();
                if (!Number.isFinite(daten.saldo) || !Number.isFinite(daten.mindestbestand)) {
                    throw new Error('Kassenstand oder Mindestbestand fehlt');
                }
                if (!abgebrochen) setSaldo(daten);
            } catch {
                if (!abgebrochen) {
                    setSaldo(null);
                    toast.error('Der aktuelle Kassenstand konnte nicht geladen werden.');
                }
            }
        })();
        return () => { abgebrochen = true; };
    }, [kassenbuch, kassenLoading, toast]);

    return (
        <div className="min-w-0 space-y-3">
            <KasseErklaerkasten />
            <KasseShortcuts
                sachkonten={sachkonten}
                saldo={saldo}
                zeigeSaldo={false}
                onChanged={onGeaendert}
            />
            <KassenbuchJournal
                kassenbuch={kassenbuch}
                saldo={saldo}
                loading={kassenLoading}
                von={kasseVon}
                bis={kasseBis}
                onVonChange={onVonChange}
                onBisChange={onBisChange}
                search={kasseSearch}
                onSearchChange={onSearchChange}
                onSelectBeleg={onSelectBeleg}
                onGeaendert={onGeaendert}
            />
        </div>
    );
}
