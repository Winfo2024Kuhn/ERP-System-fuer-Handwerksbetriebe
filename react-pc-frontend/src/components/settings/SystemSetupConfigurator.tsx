import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Brain, Check, FolderOpen, Mail, Smartphone } from 'lucide-react';
import { EmailSettingsSection } from './sections/EmailSettingsSection';
import { DateiOrdnerSection } from './sections/DateiOrdnerSection';
import { KiSettingsSection } from './sections/KiSettingsSection';
import { ZeiterfassungSection } from './sections/ZeiterfassungSection';

/**
 * Die System-Einstellungen als Reiter statt als eine endlose Rolle.
 *
 * <p>Vorher standen acht fachlich unabhängige Blöcke — Postfach, Absender,
 * Rechnungs-Postfach, Server, Datei-Ordner, KI-Key, Webseiten-Anfragen,
 * Zeiterfassung — untereinander in einer einzigen Komponente. Wer ein Feld
 * suchte, musste an allen anderen vorbeiscrollen. Jetzt trägt jeder Reiter
 * genau ein Thema, und ein Häkchen zeigt, was schon eingerichtet ist.</p>
 *
 * <p>Jeder Bereich lädt und speichert selbst. Diese Datei kennt nur die
 * Reiter — dadurch fasst eine Änderung am E-Mail-Bereich die anderen nicht
 * mehr an.</p>
 */

type TabId = 'email' | 'dateien' | 'ki' | 'zeiterfassung';

interface TabDefinition {
    id: TabId;
    label: string;
    icon: React.ReactNode;
}

const TABS: TabDefinition[] = [
    { id: 'email', label: 'E-Mail', icon: <Mail className="w-4 h-4" /> },
    { id: 'dateien', label: 'Dateien', icon: <FolderOpen className="w-4 h-4" /> },
    { id: 'ki', label: 'KI-Funktionen', icon: <Brain className="w-4 h-4" /> },
    { id: 'zeiterfassung', label: 'Zeiterfassung', icon: <Smartphone className="w-4 h-4" /> },
];

/** Welche Bereiche sind fertig eingerichtet — für die Häkchen an den Reitern. */
type SetupStatus = Partial<Record<TabId, boolean>>;

function istGueltigerTab(value: string): value is TabId {
    return TABS.some((tab) => tab.id === value);
}

/**
 * Merkt sich den offenen Reiter in der Adresszeile (z.B. `/einstellungen#ki`).
 *
 * <p>Bewusst `replaceState` statt `pushState`: Sonst müsste man den
 * Zurück-Knopf für jeden angetippten Reiter einmal extra drücken, um die
 * Seite überhaupt zu verlassen. So bleibt die Adresse trotzdem teilbar und
 * ein Lesezeichen landet im richtigen Reiter.</p>
 */
function useTabInHash(): [TabId, (tab: TabId) => void] {
    const [activeTab, setActiveTab] = useState<TabId>(() => {
        const raw = window.location.hash.replace(/^#/, '');
        return istGueltigerTab(raw) ? raw : 'email';
    });

    const wechsleTab = useCallback((tab: TabId) => {
        setActiveTab(tab);
        window.history.replaceState(null, '', `${window.location.pathname}#${tab}`);
    }, []);

    return [activeTab, wechsleTab];
}

/**
 * Holt nur, was für die Häkchen nötig ist. Die Bereiche laden ihre
 * vollständigen Daten selbst — hier geht es allein um die Frage
 * "steht das schon?", damit bei der Ersteinrichtung sichtbar ist, was
 * noch fehlt.
 *
 * <p>Reine Abfrage ohne eigenen Zustand: So bleibt das Setzen des Zustands
 * im Aufrufer und passiert erst, wenn die Antwort da ist.</p>
 */
async function holeSetupStatus(): Promise<SetupStatus> {
    try {
        const [smtpRes, geminiRes, dateiOrdnerRes] = await Promise.all([
            fetch('/api/settings/smtp'),
            fetch('/api/settings/gemini'),
            fetch('/api/settings/datei-ordner'),
        ]);
        const status: SetupStatus = {};
        if (smtpRes.ok) {
            const data = await smtpRes.json();
            status.email = !!data?.host?.trim() && !!data?.username?.trim() && !!data?.passwordSet;
        }
        if (geminiRes.ok) {
            const data = await geminiRes.json();
            status.ki = !!data?.apiKeySet;
        }
        if (dateiOrdnerRes.ok) {
            const data = await dateiOrdnerRes.json();
            status.dateien = !!data?.pfad?.trim();
        }
        return status;
    } catch {
        // Häkchen sind nur ein Hinweis — schlägt der Abruf fehl, bleiben die
        // Reiter eben unmarkiert. Eine Fehlermeldung wäre hier Lärm, denn die
        // Bereiche melden Ladefehler bereits selbst.
        return {};
    }
}

interface SystemSetupConfiguratorProps {
    onSaved?: () => void;
}

export function SystemSetupConfigurator({ onSaved }: SystemSetupConfiguratorProps) {
    const [activeTab, wechsleTab] = useTabInHash();
    const [status, setStatus] = useState<SetupStatus>({});
    // Hochzählen erzwingt ein erneutes Laden der Häkchen nach dem Speichern.
    const [statusVersion, setStatusVersion] = useState(0);
    const tabRefs = useRef<Partial<Record<TabId, HTMLButtonElement | null>>>({});

    useEffect(() => {
        let abgebrochen = false;
        holeSetupStatus().then((neu) => {
            // Nicht mehr setzen, wenn die Seite inzwischen verlassen wurde.
            if (!abgebrochen) setStatus(neu);
        });
        return () => {
            abgebrochen = true;
        };
    }, [statusVersion]);

    /** Nach jedem Speichern die Häkchen nachziehen und die Seite informieren. */
    const handleSaved = useCallback(() => {
        setStatusVersion((version) => version + 1);
        onSaved?.();
    }, [onSaved]);

    /** Pfeiltasten wandern durch die Reiter, wie es bei Reitern üblich ist. */
    const handleTabKeyDown = (event: React.KeyboardEvent, index: number) => {
        if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
        event.preventDefault();
        const richtung = event.key === 'ArrowRight' ? 1 : -1;
        const nächster = TABS[(index + richtung + TABS.length) % TABS.length];
        wechsleTab(nächster.id);
        tabRefs.current[nächster.id]?.focus();
    };

    return (
        <div>
            <div role="tablist" aria-label="Bereiche der Einstellungen" className="flex flex-wrap items-center gap-1 border-b border-slate-200 mb-6">
                {TABS.map((tab, index) => {
                    const aktiv = activeTab === tab.id;
                    const eingerichtet = status[tab.id];
                    return (
                        <button
                            key={tab.id}
                            ref={(el) => {
                                tabRefs.current[tab.id] = el;
                            }}
                            role="tab"
                            id={`settings-tab-${tab.id}`}
                            aria-selected={aktiv}
                            aria-controls={`settings-panel-${tab.id}`}
                            tabIndex={aktiv ? 0 : -1}
                            onClick={() => wechsleTab(tab.id)}
                            onKeyDown={(e) => handleTabKeyDown(e, index)}
                            className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors -mb-px ${
                                aktiv
                                    ? 'border-rose-600 text-rose-700'
                                    : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-200'
                            }`}
                        >
                            {tab.icon}
                            {tab.label}
                            {eingerichtet && (
                                <span
                                    title="Eingerichtet"
                                    className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-emerald-100 text-emerald-700"
                                >
                                    <Check className="w-3 h-3" />
                                    <span className="sr-only">Eingerichtet</span>
                                </span>
                            )}
                        </button>
                    );
                })}
            </div>

            {/*
                Nur der offene Reiter wird gebaut. Dadurch fragt die Seite beim
                Aufruf auch nur dessen Einstellungen ab statt aller acht Bereiche
                auf einmal.
            */}
            <div
                role="tabpanel"
                id={`settings-panel-${activeTab}`}
                aria-labelledby={`settings-tab-${activeTab}`}
            >
                {activeTab === 'email' && <EmailSettingsSection onSaved={handleSaved} />}
                {activeTab === 'dateien' && <DateiOrdnerSection onSaved={handleSaved} />}
                {activeTab === 'ki' && <KiSettingsSection onSaved={handleSaved} />}
                {activeTab === 'zeiterfassung' && <ZeiterfassungSection />}
            </div>
        </div>
    );
}
