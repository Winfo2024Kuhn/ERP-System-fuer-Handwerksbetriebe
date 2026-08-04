import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, ChevronUp, FolderOpen, Loader2, Share2, TestTube } from 'lucide-react';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard, TestResultBanner } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import type { TestResult } from '../settingsApi';

/**
 * Gemeinsamer Ablageort für Zeichnungen und Dateien (HiCAD, Tenado, Excel).
 *
 * <p>Der Ordner liegt auf dem Server-Rechner. Damit Kollegen ihn erreichen,
 * braucht es zusätzlich eine Netzwerk-Adresse — die Anleitung dafür steht
 * ausklappbar direkt darunter, statt in einer separaten Dokumentation.</p>
 */
export function DateiOrdnerSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [loading, setLoading] = useState(true);

    const [pfad, setPfad] = useState('');
    const [networkUrl, setNetworkUrl] = useState('');
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [freigebend, setFreigebend] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);
    const [anleitungOffen, setAnleitungOffen] = useState(false);

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/settings/datei-ordner');
            if (res.ok) {
                const data = await res.json();
                setPfad(data?.pfad || '');
                setNetworkUrl(data?.networkUrl || '');
            }
        } catch {
            toast.error('Datei-Ordner konnte nicht geladen werden.');
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad }),
            });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Prüfung fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setTesting(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/settings/datei-ordner', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad, networkUrl }),
            });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Datei-Ordner konnte nicht gespeichert werden.'));
                return;
            }
            toast.success('Datei-Ordner gespeichert.');
            onSaved?.();
        } catch {
            toast.error('Datei-Ordner konnte nicht gespeichert werden.');
        } finally {
            setSaving(false);
        }
    };

    const handleFreigeben = async () => {
        setFreigebend(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/freigeben', { method: 'POST' });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Freigabe fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setFreigebend(false);
        }
    };

    if (loading) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<FolderOpen className="w-5 h-5 text-rose-600" />}
            title="Wo sollen Zeichnungen und Dateien liegen?"
            description={
                <p>
                    Dieser Ordner ist der gemeinsame Ablageort für HiCAD- und Tenado-Zeichnungen,
                    Excel-Dateien und alles, was das Team teilt. Ein lokaler Ordner
                    (C:\Zeichnungen), ein Netzlaufwerk (Z:\Zeichnungen) oder eine Netzwerk-Adresse
                    (\\server\zeichnungen) funktionieren.
                </p>
            }
        >
            <div className="space-y-4">
                <div>
                    <Label htmlFor="dateiOrdnerPfad">Ordner auf diesem Rechner</Label>
                    <div className="flex flex-col sm:flex-row gap-2">
                        <Input
                            id="dateiOrdnerPfad"
                            className="flex-1 sm:max-w-lg"
                            value={pfad}
                            onChange={(e) => setPfad(e.target.value)}
                            placeholder="C:\Zeichnungen"
                        />
                        <Button variant="outline" onClick={handleTest} disabled={testing || !pfad.trim()}>
                            {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                            {testing ? 'Prüfe...' : 'Ordner prüfen'}
                        </Button>
                    </div>
                </div>

                <div>
                    <Label htmlFor="dateiOrdnerNetworkUrl">Netzwerk-Adresse für Kollegen (optional)</Label>
                    <Input
                        id="dateiOrdnerNetworkUrl"
                        className="sm:max-w-lg"
                        value={networkUrl}
                        onChange={(e) => setNetworkUrl(e.target.value)}
                        placeholder="\\server\zeichnungen"
                    />
                    <p className="text-xs text-slate-500 mt-1">
                        So erreichen andere Rechner den Ordner. Leer lassen, wenn nur dieser Rechner ihn nutzt.
                    </p>
                </div>
            </div>

            <TestResultBanner result={testResult} className="mt-3" />

            <button
                type="button"
                aria-expanded={anleitungOffen}
                className="mt-4 flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800"
                onClick={() => setAnleitungOffen((prev) => !prev)}
            >
                {anleitungOffen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                So gibst du den Ordner im Netzwerk frei
            </button>
            {anleitungOffen && (
                <div className="mt-2 p-4 rounded-lg bg-slate-50 text-sm text-slate-700 space-y-2">
                    <p>1. Im Windows-Explorer mit der rechten Maustaste auf den Ordner klicken → <strong>Eigenschaften</strong>.</p>
                    <p>2. Reiter <strong>Freigabe</strong> → <strong>Freigeben...</strong> → Kollegen oder „Jeder" hinzufügen → <strong>Freigeben</strong>.</p>
                    <p>3. Die angezeigte Adresse (z. B. \\DEIN-PC\Zeichnungen) oben als Netzwerk-Adresse eintragen.</p>
                    <div className="pt-2">
                        <Button variant="outline" onClick={handleFreigeben} disabled={freigebend || !pfad.trim()}>
                            {freigebend ? <Loader2 className="w-4 h-4 animate-spin" /> : <Share2 className="w-4 h-4" />}
                            {freigebend ? 'Gebe frei...' : 'Oder: Ordner automatisch freigeben'}
                        </Button>
                        <p className="text-xs text-slate-500 mt-1">
                            Windows fragt dabei einmal nach Administrator-Rechten. Der Ordner muss vorher gespeichert sein.
                        </p>
                    </div>
                </div>
            )}

            <SaveButton onClick={handleSave} saving={saving} disabled={!pfad.trim()}>
                Datei-Ordner speichern
            </SaveButton>
        </SettingsCard>
    );
}
