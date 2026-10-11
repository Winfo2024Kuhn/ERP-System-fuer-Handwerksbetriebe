import EmailSettings from '../components/EmailSettings';
import { PageLayout } from '../components/layout/PageLayout';

/**
 * Eigene Seite für die E-Mail-Einstellungen (Signaturen und Abwesenheitsnotizen),
 * erreichbar über Kommunikation › E-Mail › E-Mail-Einstellungen. Für jeden
 * angemeldeten Benutzer – welche Postfächer jemand für eine Abwesenheitsnotiz
 * wählen darf, entscheidet das Backend.
 */
export default function EmailEinstellungenPage() {
    return (
        <PageLayout
            ribbonCategory="Kommunikation"
            title="E-Mail-Einstellungen"
            subtitle="Signaturen und Abwesenheitsnotizen verwalten."
        >
            <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
                <EmailSettings />
            </div>
        </PageLayout>
    );
}
