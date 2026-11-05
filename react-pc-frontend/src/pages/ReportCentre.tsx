import { useState } from 'react';
import { FilePlus2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { BerichtAssistent } from '../features/report-centre/BerichtAssistent';
import { useZuletztErstellt } from '../features/report-centre/useZuletztErstellt';
import { ZuletztErstellt } from '../features/report-centre/ZuletztErstellt';

/** Seite „Report Centre“: Berichte als PDF erstellen (Steuerberater, Mitarbeiter). */
export default function ReportCentre() {
    const [assistentOffen, setAssistentOffen] = useState(false);
    const { liste, hinzufuegen } = useZuletztErstellt();

    return (
        <PageLayout
            ribbonCategory="Zeiterfassung"
            title="Report Centre"
            subtitle="Berichte für Steuerberater und Mitarbeiter als PDF erstellen"
            actions={(
                <Button onClick={() => setAssistentOffen(true)} className="px-5 py-2.5 text-base">
                    <FilePlus2 aria-hidden="true" className="h-5 w-5" />
                    Neuer Bericht
                </Button>
            )}
        >
            <ZuletztErstellt liste={liste} />
            <BerichtAssistent offen={assistentOffen} onClose={() => setAssistentOffen(false)} onPdfErstellt={hinzufuegen} />
        </PageLayout>
    );
}
