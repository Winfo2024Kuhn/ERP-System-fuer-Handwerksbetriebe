import { useState } from 'react';
import { Download, CheckCircle, AlertCircle, Settings, HelpCircle } from 'lucide-react';
import { Button } from './ui/button';

/**
 * Komponente zum Herunterladen und Installieren des OpenFile Launchers.
 * Ermöglicht das Öffnen von CAD-Zeichnungen (.sza, .tcd) und Excel-Dateien
 * direkt aus der Webapp über das openfile:// Protokoll.
 */
export default function OpenFileLauncherSetup() {
    const [downloading, setDownloading] = useState(false);
    const [showHelp, setShowHelp] = useState(false);

    const downloadSetup = async () => {
        setDownloading(true);
        try {
            const response = await fetch('/api/system/openfile-launcher-setup');
            if (!response.ok) {
                throw new Error('Download fehlgeschlagen');
            }
            
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'OpenFileLauncher-Setup.zip';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } catch (error) {
            console.error('Fehler beim Download:', error);
            alert('Fehler beim Download des Setup-Pakets. Bitte kontaktieren Sie den Administrator.');
        } finally {
            setDownloading(false);
        }
    };

    return (
        <div className="bg-white border border-slate-200 rounded-lg p-4 shadow-sm">
            <div className="flex items-start gap-3">
                <div className="p-2 bg-rose-100 rounded-lg">
                    <Settings className="w-5 h-5 text-rose-600" />
                </div>
                <div className="flex-1">
                    <h3 className="font-semibold text-slate-900">OpenFile Launcher</h3>
                    <p className="text-sm text-slate-600 mt-1">
                        Um CAD-Zeichnungen und Excel-Dateien direkt öffnen zu können, 
                        muss der OpenFile Launcher auf Ihrem Computer installiert sein.
                    </p>
                    
                    <div className="mt-3 flex flex-wrap gap-2">
                        <Button
                            size="sm"
                            onClick={downloadSetup}
                            disabled={downloading}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        >
                            <Download className="w-4 h-4 mr-2" />
                            {downloading ? 'Wird heruntergeladen...' : 'Setup herunterladen'}
                        </Button>
                        
                        <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setShowHelp(!showHelp)}
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        >
                            <HelpCircle className="w-4 h-4 mr-2" />
                            Installationsanleitung
                        </Button>
                    </div>

                    {showHelp && (
                        <div className="mt-4 p-3 bg-slate-50 rounded-lg text-sm">
                            <h4 className="font-medium text-slate-900 mb-2">Installation:</h4>
                            <ol className="list-decimal list-inside space-y-2 text-slate-700">
                                <li>
                                    <span className="font-medium">ZIP herunterladen</span> - Klicken Sie auf den Button oben
                                </li>
                                <li>
                                    <span className="font-medium">ZIP entpacken</span> - Rechtsklick → "Alle extrahieren"
                                </li>
                                <li>
                                    <span className="font-medium">Rechtsklick</span> auf 
                                    <code className="mx-1 px-1 bg-slate-200 rounded text-xs">OpenFileLauncher-Install.bat</code>
                                </li>
                                <li>
                                    <span className="font-medium">"Als Administrator ausführen"</span> wählen
                                </li>
                                <li>
                                    Den Anweisungen im Installationsfenster folgen
                                </li>
                            </ol>
                            
                            <div className="mt-3 p-2 bg-amber-50 border border-amber-200 rounded">
                                <div className="flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
                                    <p className="text-amber-800 text-xs">
                                        <strong>Hinweis:</strong> Administrator-Rechte sind erforderlich, 
                                        um das openfile://-Protokoll in Windows zu registrieren.
                                    </p>
                                </div>
                            </div>
                            
                            <div className="mt-3 p-2 bg-green-50 border border-green-200 rounded">
                                <div className="flex items-start gap-2">
                                    <CheckCircle className="w-4 h-4 text-green-600 mt-0.5 flex-shrink-0" />
                                    <div className="text-green-800 text-xs">
                                        <strong>Nach der Installation:</strong>
                                        <ul className="list-disc list-inside mt-1">
                                            <li>HiCAD-Zeichnungen (.sza) öffnen sich automatisch</li>
                                            <li>Tenado-Dateien (.tcd) werden korrekt gestartet</li>
                                            <li>Excel-Tabellen öffnen sich direkt in Excel</li>
                                        </ul>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
