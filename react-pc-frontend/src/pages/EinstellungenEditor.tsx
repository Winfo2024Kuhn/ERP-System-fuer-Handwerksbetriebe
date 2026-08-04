import { PageLayout } from '../components/layout/PageLayout';
import { SystemSetupConfigurator } from '../components/settings/SystemSetupConfigurator';

/**
 * Seite „System-Einstellungen".
 *
 * <p>Der gesamte Inhalt steckt in {@link SystemSetupConfigurator} und ist dort
 * in Reiter aufgeteilt (E-Mail, Dateien, KI-Funktionen, Zeiterfassung). Diese
 * Seite setzt nur noch den Rahmen — dieselbe Komponente trägt auch die
 * Ersteinrichtung nach der ersten Anmeldung.</p>
 */
export default function EinstellungenEditor() {
    return (
        <PageLayout
            ribbonCategory="Administration"
            title="System-Einstellungen"
            subtitle="E-Mail, Dateiablage, KI-Funktionen und Zeiterfassung einrichten"
        >
            <SystemSetupConfigurator />
        </PageLayout>
    );
}
