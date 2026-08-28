import { useState } from 'react';
import { BarChart3, FileText } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';

type Tab = 'beitraege' | 'insights';

/**
 * Bereich "Website - Neuigkeiten". Haelt nur den aktiven Tab und die
 * Kopfzeile; alles Weitere steckt in den zwei Tab-Komponenten.
 *
 * Startet auf "Beitrag erstellen", weil das die taegliche Arbeit ist.
 * Die Zahlen schaut man sich seltener an.
 */
export function WebsiteEditor() {
    const [aktiverTab, setAktiverTab] = useState<Tab>('beitraege');

    return (
        <div className="p-6 max-w-[1600px] mx-auto">
            <PageHeader
                category="Website"
                title="NEUIGKEITEN"
                description="Beitraege fuer den Bereich Aktuelles auf der Firmen-Website pflegen."
            />

            <div className="flex gap-2 border-b border-slate-200 mb-6">
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
                    {/* Task 9 setzt hier <BeitraegeTab /> ein. */}
                </div>
            )}
            {aktiverTab === 'insights' && (
                <div data-testid="tab-insights">
                    {/* Task 5 setzt hier <InsightsTab /> ein. */}
                </div>
            )}
        </div>
    );
}

function TabKnopf({ aktiv, onClick, icon, label }: {
    aktiv: boolean;
    onClick: () => void;
    icon: React.ReactNode;
    label: string;
}) {
    return (
        <button
            onClick={onClick}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors
                ${aktiv
                    ? 'border-rose-600 text-rose-700'
                    : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
                }`}
        >
            {icon}
            {label}
        </button>
    );
}

export default WebsiteEditor;
