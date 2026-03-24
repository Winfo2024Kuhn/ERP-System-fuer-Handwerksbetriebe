import { useState, useEffect, useCallback, useMemo } from 'react';
import { X, Percent, Tag, FileText } from 'lucide-react';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import { formatCurrency, getAllServiceBlocks, buildPositionMap, serviceLineTotal } from './helpers';
import type { DocBlock } from './types';

type RabattMode = 'positionen' | 'dokument';

interface PositionDiscount {
    id: string;
    enabled: boolean;
    discount: number;
}

interface RabattDialogProps {
    blocks: DocBlock[];
    globalRabatt: number;
    onApply: (positionDiscounts: Record<string, number>, globalRabatt: number) => void;
    onClose: () => void;
}

export function RabattDialog({ blocks, globalRabatt: initialGlobalRabatt, onApply, onClose }: RabattDialogProps) {
    const [mode, setMode] = useState<RabattMode>(() => initialGlobalRabatt > 0 ? 'dokument' : 'positionen');
    const [localGlobalRabatt, setLocalGlobalRabatt] = useState(initialGlobalRabatt);
    const serviceBlocks = useMemo(() => getAllServiceBlocks(blocks), [blocks]);
    const posMap = useMemo(() => buildPositionMap(blocks), [blocks]);
    const initialPosDiscounts = useMemo(() => serviceBlocks.map(b => ({
        id: b.id,
        enabled: (b.discount ?? 0) > 0,
        discount: b.discount ?? 0,
    })), [serviceBlocks]);
    const [posDiscounts, setPosDiscounts] = useState<PositionDiscount[]>(initialPosDiscounts);

    // Netto for document-mode preview
    const netto = useMemo(() => {
        let n = 0;
        for (const b of serviceBlocks) {
            if (!b.optional) n += serviceLineTotal(b);
        }
        return n;
    }, [serviceBlocks]);

    const rabattPreview = localGlobalRabatt > 0 ? netto * (localGlobalRabatt / 100) : 0;

    useEffect(() => {
        const syncDiscounts = window.setTimeout(() => setPosDiscounts(initialPosDiscounts), 0);
        return () => window.clearTimeout(syncDiscounts);
    }, [initialPosDiscounts]);

    const togglePosition = useCallback((id: string) => {
        setPosDiscounts(prev => prev.map(p =>
            p.id === id ? { ...p, enabled: !p.enabled, discount: !p.enabled ? (p.discount || 10) : 0 } : p
        ));
    }, []);

    const updatePositionDiscount = useCallback((id: string, value: number) => {
        setPosDiscounts(prev => prev.map(p =>
            p.id === id ? { ...p, discount: Math.min(100, Math.max(0, value)) } : p
        ));
    }, []);

    const setAllPositions = useCallback((enable: boolean, discount?: number) => {
        setPosDiscounts(prev => prev.map(p => ({
            ...p,
            enabled: enable,
            discount: enable ? (discount ?? (p.discount || 10)) : 0,
        })));
    }, []);

    const handleApply = () => {
        if (mode === 'positionen') {
            const positionDiscounts: Record<string, number> = {};
            for (const p of posDiscounts) {
                positionDiscounts[p.id] = p.enabled ? p.discount : 0;
            }
            onApply(positionDiscounts, 0); // Globalen Rabatt entfernen bei Positions-Rabatt
        } else {
            // Bei Dokument-Rabatt: Positions-Rabatte entfernen
            const positionDiscounts: Record<string, number> = {};
            for (const p of posDiscounts) {
                positionDiscounts[p.id] = 0;
            }
            onApply(positionDiscounts, localGlobalRabatt);
        }
        onClose();
    };

    // Count how many positions have discounts
    const activeCount = posDiscounts.filter(p => p.enabled && p.discount > 0).length;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center">
            {/* Backdrop */}
            <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />

            {/* Dialog */}
            <div className="relative bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg mx-4 max-h-[85vh] flex flex-col animate-in fade-in zoom-in-95 duration-200">
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
                    <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 bg-rose-50 border border-rose-100 rounded-xl flex items-center justify-center">
                            <Percent className="w-4.5 h-4.5 text-rose-600" />
                        </div>
                        <div>
                            <h2 className="text-base font-bold text-slate-900">Rabatt verwalten</h2>
                            <p className="text-xs text-slate-400">Rabatt auf Positionen oder gesamtes Dokument</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors">
                        <X className="w-4 h-4 text-slate-400" />
                    </button>
                </div>

                {/* Mode Tabs */}
                <div className="flex gap-1 px-5 pt-4 pb-2">
                    <button
                        onClick={() => setMode('positionen')}
                        className={cn(
                            "flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-all",
                            mode === 'positionen'
                                ? "bg-rose-50 text-rose-700 border border-rose-200 shadow-sm"
                                : "text-slate-500 hover:text-slate-700 hover:bg-slate-50 border border-transparent"
                        )}
                    >
                        <Tag className="w-3.5 h-3.5" />
                        Einzelne Positionen
                    </button>
                    <button
                        onClick={() => setMode('dokument')}
                        className={cn(
                            "flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-all",
                            mode === 'dokument'
                                ? "bg-rose-50 text-rose-700 border border-rose-200 shadow-sm"
                                : "text-slate-500 hover:text-slate-700 hover:bg-slate-50 border border-transparent"
                        )}
                    >
                        <FileText className="w-3.5 h-3.5" />
                        Gesamtes Dokument
                    </button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto px-5 py-3 min-h-0">
                    {mode === 'positionen' ? (
                        <div className="space-y-2">
                            {/* Bulk actions */}
                            <div className="flex items-center justify-between mb-3">
                                <span className="text-xs text-slate-400">
                                    {activeCount} von {serviceBlocks.length} Positionen rabattiert
                                </span>
                                <div className="flex gap-1.5">
                                    <button
                                        onClick={() => setAllPositions(true, 10)}
                                        className="text-[10px] font-medium text-rose-600 hover:text-rose-700 px-2 py-1 rounded-md hover:bg-rose-50 transition-colors"
                                    >
                                        Alle auswählen
                                    </button>
                                    <button
                                        onClick={() => setAllPositions(false)}
                                        className="text-[10px] font-medium text-slate-500 hover:text-slate-700 px-2 py-1 rounded-md hover:bg-slate-50 transition-colors"
                                    >
                                        Alle abwählen
                                    </button>
                                </div>
                            </div>

                            {serviceBlocks.length === 0 && (
                                <div className="text-center py-8 text-sm text-slate-400">
                                    Keine Positionen vorhanden
                                </div>
                            )}

                            {serviceBlocks.map((block) => {
                                const posDiscount = posDiscounts.find(p => p.id === block.id);
                                const isEnabled = posDiscount?.enabled ?? false;
                                const discountValue = posDiscount?.discount ?? 0;
                                const pos = posMap.get(block.id) || '';
                                const rawTotal = (block.quantity || 0) * (block.price || 0);
                                const discountedTotal = discountValue > 0 ? rawTotal * (1 - discountValue / 100) : rawTotal;

                                return (
                                    <div
                                        key={block.id}
                                        className={cn(
                                            "flex items-center gap-3 p-3 rounded-xl border transition-all",
                                            isEnabled
                                                ? "bg-rose-50/50 border-rose-200"
                                                : "bg-white border-slate-100 hover:border-slate-200",
                                            block.optional && "opacity-50"
                                        )}
                                    >
                                        {/* Checkbox */}
                                        <button
                                            onClick={() => !block.optional && togglePosition(block.id)}
                                            disabled={block.optional}
                                            className={cn(
                                                "w-5 h-5 rounded-md border-2 flex items-center justify-center flex-shrink-0 transition-all",
                                                isEnabled
                                                    ? "bg-rose-600 border-rose-600"
                                                    : "border-slate-300 hover:border-rose-400",
                                                block.optional && "opacity-40 cursor-not-allowed"
                                            )}
                                        >
                                            {isEnabled && (
                                                <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                                </svg>
                                            )}
                                        </button>

                                        {/* Pos badge */}
                                        <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-500 flex items-center justify-center text-xs font-bold flex-shrink-0">
                                            {pos}
                                        </div>

                                        {/* Title + amount */}
                                        <div className="flex-1 min-w-0">
                                            <div className="text-sm font-medium text-slate-800 truncate">
                                                {block.title || 'Ohne Titel'}
                                                {block.optional && <span className="text-amber-500 text-xs ml-1">(Alt.)</span>}
                                            </div>
                                            <div className="text-xs text-slate-400">
                                                {(block.quantity || 0)} {block.unit || 'Stk'} × {formatCurrency(block.price || 0)} €
                                                {isEnabled && discountValue > 0 && (
                                                    <span className="ml-2 text-rose-500">
                                                        → {formatCurrency(discountedTotal)} €
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {/* Discount input */}
                                        {isEnabled && (
                                            <div className="flex items-center gap-1 flex-shrink-0">
                                                <input
                                                    type="number"
                                                    step="0.5"
                                                    min="0"
                                                    max="100"
                                                    value={discountValue || ''}
                                                    placeholder="0"
                                                    onFocus={(e) => { if (e.target.value === '0') e.target.value = ''; }}
                                                    onChange={(e) => {
                                                        const val = parseFloat(e.target.value);
                                                        if (e.target.value === '' || e.target.value === '-') { updatePositionDiscount(block.id, 0); return; }
                                                        if (!isNaN(val) && val >= 0) updatePositionDiscount(block.id, Math.min(100, val));
                                                    }}
                                                    className="w-16 text-right text-sm font-semibold text-rose-600 bg-white border border-rose-200 rounded-lg px-2 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all"
                                                />
                                                <span className="text-rose-400 text-xs font-medium">%</span>
                                            </div>
                                        )}

                                        {/* Amount (when no discount) */}
                                        {!isEnabled && (
                                            <div className="text-sm font-medium text-slate-600 flex-shrink-0">
                                                {formatCurrency(rawTotal)} €
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        /* Global document discount */
                        <div className="py-6">
                            <div className="text-center mb-6">
                                <div className="w-16 h-16 bg-rose-50 border border-rose-100 rounded-2xl flex items-center justify-center mx-auto mb-3">
                                    <Percent className="w-7 h-7 text-rose-500" />
                                </div>
                                <h3 className="text-sm font-semibold text-slate-700 mb-1">Pauschalrabatt auf gesamtes Dokument</h3>
                                <p className="text-xs text-slate-400">Wird auf die Nettosumme aller Positionen angewandt</p>
                            </div>

                            <div className="max-w-xs mx-auto">
                                <div className="relative">
                                    <input
                                        type="number"
                                        step="0.5"
                                        min="0"
                                        max="100"
                                        value={localGlobalRabatt || ''}
                                        placeholder="0"
                                        onFocus={(e) => { if (e.target.value === '0') e.target.value = ''; }}
                                        onChange={(e) => {
                                            const val = parseFloat(e.target.value);
                                            if (e.target.value === '' || e.target.value === '-') { setLocalGlobalRabatt(0); return; }
                                            if (!isNaN(val) && val >= 0) setLocalGlobalRabatt(Math.min(100, val));
                                        }}
                                        className="w-full text-center text-3xl font-bold text-rose-600 bg-rose-50/50 border-2 border-rose-200 rounded-2xl px-6 py-5 focus:ring-4 focus:ring-rose-500/20 focus:border-rose-400 transition-all placeholder:text-rose-200"
                                    />
                                    <span className="absolute right-5 top-1/2 -translate-y-1/2 text-rose-300 text-2xl font-medium">%</span>
                                </div>

                                {/* Preview calculation */}
                                {localGlobalRabatt > 0 && (
                                    <div className="mt-4 bg-slate-50 rounded-xl border border-slate-100 p-4 space-y-2">
                                        <div className="flex justify-between text-sm">
                                            <span className="text-slate-500">Nettosumme</span>
                                            <span>{formatCurrency(netto)} €</span>
                                        </div>
                                        <div className="flex justify-between text-sm text-rose-500">
                                            <span>Rabatt {formatCurrency(localGlobalRabatt)}%</span>
                                            <span>−{formatCurrency(rabattPreview)} €</span>
                                        </div>
                                        <div className="border-t border-slate-200 pt-2 mt-2">
                                            <div className="flex justify-between text-sm">
                                                <span className="font-semibold text-slate-700">Netto nach Rabatt</span>
                                                <span className="font-bold text-slate-800">{formatCurrency(netto - rabattPreview)} €</span>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between px-5 py-4 border-t border-slate-100 bg-slate-50/50 rounded-b-2xl">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
                    >
                        Abbrechen
                    </button>
                    <Button
                        size="sm"
                        onClick={handleApply}
                        className="h-9 px-5 text-sm bg-rose-600 hover:bg-rose-700 text-white shadow-sm"
                    >
                        Rabatt übernehmen
                    </Button>
                </div>
            </div>
        </div>
    );
}
