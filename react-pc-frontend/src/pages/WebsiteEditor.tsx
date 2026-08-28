import { useState } from 'react';
import { BarChart3, FileText } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { BeitraegeTab } from '../components/website/BeitraegeTab';
import { BeitragAssistent } from '../components/website/BeitragAssistent';
import { InsightsTab } from '../components/website/InsightsTab';
import { cn } from '../lib/utils';

type Tab = 'beitraege' | 'insights';

/**
 * Bereich "Website - Neuigkeiten". Haelt nur den aktiven Tab und die
 * Kopfzeile; alles Weitere steckt in den zwei Tab-Komponenten.
 *
 * Startet auf "Beitrag erstellen", weil das die taegliche Arbeit ist.
 * Die Zahlen schaut man sich seltener an.
 *
 * Der Assistent fuer neue Beitraege haengt hier und nicht in BeitraegeTab,
 * weil BeitraegeTab den Knopf bewusst gesperrt haelt, solange kein
 * onNeuerBeitrag uebergeben wird (siehe BeitraegeTab.test.tsx). Diese Seite
 * liefert die echte Funktion und zaehlt neuLadenSignal hoch, damit die
 * Liste nach dem Assistenten neu laedt.
 *
 * onFertig laeuft nicht nur bei vollem Erfolg, sondern auch, wenn der
 * Nutzer den Assistenten nach einem Teilabbruch schliesst (Bild-Upload
 * abgebrochen, Beitrag aber schon als Entwurf angelegt) -- siehe
 * BeitragAssistent.tsx. Sonst bliebe der neue Entwurf in der Liste
 * unsichtbar und der Nutzer legt ihn versehentlich doppelt an.
 */
export function WebsiteEditor() {
    const [aktiverTab, setAktiverTab] = useState<Tab>('beitraege');
    const [assistentOffen, setAssistentOffen] = useState(false);
    const [neuLadenSignal, setNeuLadenSignal] = useState(0);

    return (
        <div className="p-6 max-w-[1600px] mx-auto">
            <PageHeader
                category="Website"
                title="NEUIGKEITEN"
                description="Beiträge für den Bereich Aktuelles auf der Firmen-Website pflegen."
            />

            <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2 overflow-x-auto">
                <TabKnopf
                    aktiv={aktiverTab === 'beitraege'}
                    onClick={() => setAktiverTab('beitraege')}
                    icon={<FileText className="w-4 h-4" />}
                    label="Beitrag erstellen"
                />
                <TabKnopf
                    aktiv={aktiverTab === 'insights'}
                    onClick={() => setAktiverTab('insights')}
                    icon={<BarChart3 className="w-4 h-4" />}
                    label="Zahlen der Website"
                />
            </div>

            {aktiverTab === 'beitraege' && (
                <div data-testid="tab-beitraege">
                    <BeitraegeTab
                        onNeuerBeitrag={() => setAssistentOffen(true)}
                        neuLadenSignal={neuLadenSignal}
                    />
                </div>
            )}
            {aktiverTab === 'insights' && (
                <div data-testid="tab-insights">
                    <InsightsTab />
                </div>
            )}

            <BeitragAssistent
                offen={assistentOffen}
                onAbbrechen={() => setAssistentOffen(false)}
                onFertig={() => {
                    setAssistentOffen(false);
                    setNeuLadenSignal(n => n + 1);
                }}
            />
        </div>
    );
}

function TabKnopf({ aktiv, onClick, icon, label }: {
    aktiv: boolean;
    onClick: () => void;
    icon: React.ReactNode;
    label: string;
}) {
    // Gleiches Muster wie die Tabs in AnfrageEditor.tsx, damit sich die
    // Seiten im Programm nicht unterschiedlich anfuehlen.
    return (
        <button
            onClick={onClick}
            aria-current={aktiv ? 'page' : undefined}
            className={cn(
                'flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap cursor-pointer',
                aktiv
                    ? 'bg-rose-50 text-rose-700 border-b-2 border-rose-600'
                    : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            )}
        >
            {icon}
            {label}
        </button>
    );
}

export default WebsiteEditor;
