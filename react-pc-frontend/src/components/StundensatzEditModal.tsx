import React, { useState, useEffect } from 'react';
import { Save, AlertTriangle, X } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from './ui/dialog';
import { type Arbeitsgang } from '../types';

interface StundensatzEditModalProps {
    arbeitsgang: Arbeitsgang;
    isOpen: boolean;
    onClose: () => void;
    onSave: (arbeitsgangId: number, neuerStundensatz: number) => Promise<void>;
}

export const StundensatzEditModal: React.FC<StundensatzEditModalProps> = ({
    arbeitsgang,
    isOpen,
    onClose,
    onSave,
}) => {
    const [stundensatz, setStundensatz] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    const currentYear = new Date().getFullYear();
    const isOutdated = arbeitsgang.stundensatzJahr !== null &&
        currentYear - arbeitsgang.stundensatzJahr >= 1;

    useEffect(() => {
        if (isOpen) {
            setStundensatz(arbeitsgang.stundensatz?.toString() || '');
            setError('');
        }
    }, [isOpen, arbeitsgang]);

    const handleSave = async () => {
        const parsed = parseFloat(stundensatz.replace(',', '.'));
        if (isNaN(parsed) || parsed < 0) {
            setError('Bitte einen gültigen Stundensatz eingeben.');
            return;
        }

        setSaving(true);
        setError('');
        try {
            await onSave(arbeitsgang.id, parsed);
            onClose();
        } catch (err) {
            setError('Fehler beim Speichern des Stundensatzes.');
            console.error(err);
        } finally {
            setSaving(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleSave();
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
            <DialogContent className="sm:max-w-[420px]">
                <DialogHeader>
                    <DialogTitle>Stundensatz bearbeiten</DialogTitle>
                    <DialogDescription>{arbeitsgang.beschreibung}</DialogDescription>
                </DialogHeader>

                <div className="space-y-4 py-2">
                    {isOutdated && (
                        <div className="flex items-start gap-3 p-3 rounded-lg bg-orange-50 border border-orange-200">
                            <AlertTriangle className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                            <div>
                                <p className="text-sm font-medium text-orange-800">Stundensatz veraltet</p>
                                <p className="text-xs text-orange-600 mt-0.5">
                                    Letzter Stundensatz aus {arbeitsgang.stundensatzJahr}.
                                    Bitte aktuellen Wert für {currentYear} eingeben.
                                </p>
                            </div>
                        </div>
                    )}

                    {error && (
                        <div className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">
                            {error}
                        </div>
                    )}

                    <div className="space-y-2">
                        <Label htmlFor="stundensatz">Neuer Stundensatz (€/h)</Label>
                        <Input
                            id="stundensatz"
                            type="number"
                            min="0"
                            step="0.01"
                            value={stundensatz}
                            onChange={(e) => setStundensatz(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder="z.B. 65.00"
                            className="text-right font-mono"
                            autoFocus
                        />
                        {arbeitsgang.stundensatz !== null && (
                            <p className="text-xs text-slate-500">
                                Aktueller Wert: {arbeitsgang.stundensatz.toFixed(2)} €/h
                                {arbeitsgang.stundensatzJahr && ` (${arbeitsgang.stundensatzJahr})`}
                            </p>
                        )}
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={onClose} disabled={saving}>
                        <X className="w-4 h-4" /> Abbrechen
                    </Button>
                    <Button
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        size="sm"
                        onClick={handleSave}
                        disabled={saving}
                    >
                        <Save className="w-4 h-4" /> {saving ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default StundensatzEditModal;
