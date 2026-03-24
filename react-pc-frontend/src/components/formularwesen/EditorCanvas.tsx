import { useRef, useCallback, useState } from 'react';
import { Upload, X, ZoomIn, ZoomOut } from 'lucide-react';
import { Button } from '../ui/button';
import type { FormBlock, FormBlockStyles } from '../../types';
import { PAGE_FORMATS, SNAP_THRESHOLD, MIN_WIDTH, MIN_HEIGHT, STYLE_DEFAULTS, BLOCK_LABELS, replacePlaceholders } from './constants';

interface EditorCanvasProps {
    items: FormBlock[];
    activePage: number;
    selectedId: string | null;
    backgroundImage: string | null;
    backgroundImagePage2: string | null;
    liveData: Record<string, string>;
    onSelectBlock: (id: string | null) => void;
    onUpdateBlock: (id: string, updates: Partial<FormBlock>) => void;
    onDeleteBlock: (id: string) => void;
    onSetActivePage: (page: number) => void;
    onSetBackgroundImage: (img: string | null) => void;
    onSetBackgroundImagePage2: (img: string | null) => void;
}

const getBlockStyles = (item: FormBlock): FormBlockStyles => ({
    ...STYLE_DEFAULTS[item.type],
    ...item.styles
});

/** Central DIN A4 canvas with blocks, snap guides, and page navigation */
export default function EditorCanvas({
    items,
    activePage,
    selectedId,
    backgroundImage,
    backgroundImagePage2,
    liveData,
    onSelectBlock,
    onUpdateBlock,
    onDeleteBlock,
    onSetActivePage,
    onSetBackgroundImage,
    onSetBackgroundImagePage2
}: EditorCanvasProps) {
    const canvasRef = useRef<HTMLDivElement>(null);
    const bgInputRef = useRef<HTMLInputElement>(null);
    const bgInputPage2Ref = useRef<HTMLInputElement>(null);
    const draggingRef = useRef<{ id: string; startX: number; startY: number; origX: number; origY: number } | null>(null);
    const resizingRef = useRef<{ id: string; startX: number; startY: number; startW: number; startH: number } | null>(null);
    const [snapGuides, setSnapGuides] = useState<{ horizontal: number[]; vertical: number[] }>({ horizontal: [], vertical: [] });
    const [zoom, setZoom] = useState(1);

    const pageFormat = 'A4' as const;
    const pageItems = items.filter(i => i.page === activePage).sort((a, b) => a.z - b.z);

    // Calculate snap + apply
    const applyMagneticSnap = useCallback((blockId: string, newX: number, newY: number, width: number, height: number) => {
        const canvas = PAGE_FORMATS[pageFormat];
        let snappedX = newX;
        let snappedY = newY;

        const blockCenterH = newX + width / 2;
        const blockCenterV = newY + height / 2;
        const canvasCenterH = Math.round(canvas.width / 2);
        const canvasCenterV = Math.round(canvas.height / 2);

        if (Math.abs(blockCenterH - canvasCenterH) < SNAP_THRESHOLD) snappedX = canvasCenterH - width / 2;
        if (Math.abs(blockCenterV - canvasCenterV) < SNAP_THRESHOLD) snappedY = canvasCenterV - height / 2;
        if (Math.abs(newX) < SNAP_THRESHOLD) snappedX = 0;
        if (Math.abs(newY) < SNAP_THRESHOLD) snappedY = 0;
        if (Math.abs(newX + width - canvas.width) < SNAP_THRESHOLD) snappedX = canvas.width - width;
        if (Math.abs(newY + height - canvas.height) < SNAP_THRESHOLD) snappedY = canvas.height - height;

        const otherBlocks = items.filter(i => i.id !== blockId && i.page === activePage);
        for (const other of otherBlocks) {
            if (Math.abs(newX - other.x) < SNAP_THRESHOLD) snappedX = other.x;
            if (Math.abs(newX - (other.x + other.width)) < SNAP_THRESHOLD) snappedX = other.x + other.width;
            if (Math.abs(newX + width - other.x) < SNAP_THRESHOLD) snappedX = other.x - width;
            if (Math.abs(newX + width - (other.x + other.width)) < SNAP_THRESHOLD) snappedX = other.x + other.width - width;
            if (Math.abs(newY - other.y) < SNAP_THRESHOLD) snappedY = other.y;
            if (Math.abs(newY - (other.y + other.height)) < SNAP_THRESHOLD) snappedY = other.y + other.height;
            if (Math.abs(newY + height - other.y) < SNAP_THRESHOLD) snappedY = other.y - height;
            if (Math.abs(newY + height - (other.y + other.height)) < SNAP_THRESHOLD) snappedY = other.y + other.height - height;
        }

        return { x: Math.round(snappedX), y: Math.round(snappedY) };
    }, [items, activePage]);

    const calculateSnapGuides = useCallback((blockId: string, newX: number, newY: number, width: number, height: number) => {
        const canvas = PAGE_FORMATS[pageFormat];
        const h: number[] = [];
        const v: number[] = [];
        const canvasCenterH = Math.round(canvas.width / 2);
        const canvasCenterV = Math.round(canvas.height / 2);

        const bL = newX, bCH = newX + width / 2, bR = newX + width;
        const bT = newY, bCV = newY + height / 2, bB = newY + height;

        if (Math.abs(bL) < SNAP_THRESHOLD) v.push(0);
        if (Math.abs(bCH - canvasCenterH) < SNAP_THRESHOLD) v.push(canvasCenterH);
        if (Math.abs(bR - canvas.width) < SNAP_THRESHOLD) v.push(canvas.width);
        if (Math.abs(bT) < SNAP_THRESHOLD) h.push(0);
        if (Math.abs(bCV - canvasCenterV) < SNAP_THRESHOLD) h.push(canvasCenterV);
        if (Math.abs(bB - canvas.height) < SNAP_THRESHOLD) h.push(canvas.height);

        const others = items.filter(i => i.id !== blockId && i.page === activePage);
        for (const o of others) {
            const oL = o.x, oCH = o.x + o.width / 2, oR = o.x + o.width;
            const oT = o.y, oCV = o.y + o.height / 2, oB = o.y + o.height;
            if (Math.abs(bL - oL) < SNAP_THRESHOLD) v.push(oL);
            if (Math.abs(bL - oR) < SNAP_THRESHOLD) v.push(oR);
            if (Math.abs(bR - oL) < SNAP_THRESHOLD) v.push(oL);
            if (Math.abs(bR - oR) < SNAP_THRESHOLD) v.push(oR);
            if (Math.abs(bCH - oCH) < SNAP_THRESHOLD) v.push(oCH);
            if (Math.abs(bT - oT) < SNAP_THRESHOLD) h.push(oT);
            if (Math.abs(bT - oB) < SNAP_THRESHOLD) h.push(oB);
            if (Math.abs(bB - oT) < SNAP_THRESHOLD) h.push(oT);
            if (Math.abs(bB - oB) < SNAP_THRESHOLD) h.push(oB);
            if (Math.abs(bCV - oCV) < SNAP_THRESHOLD) h.push(oCV);
        }

        return { horizontal: [...new Set(h)], vertical: [...new Set(v)] };
    }, [items, activePage]);

    const handleMouseMove = useCallback((e: MouseEvent) => {
        if (draggingRef.current) {
            const { id, startX, startY, origX, origY } = draggingRef.current;
            const dx = (e.clientX - startX) / zoom;
            const dy = (e.clientY - startY) / zoom;
            const rawX = origX + dx;
            const rawY = origY + dy;
            const item = items.find(i => i.id === id);
            if (item) {
                setSnapGuides(calculateSnapGuides(id, rawX, rawY, item.width, item.height));
                const snapped = applyMagneticSnap(id, rawX, rawY, item.width, item.height);
                onUpdateBlock(id, { x: snapped.x, y: snapped.y });
            }
        }
        if (resizingRef.current) {
            const { id, startX, startY, startW, startH } = resizingRef.current;
            const newW = Math.max(MIN_WIDTH, startW + (e.clientX - startX) / zoom);
            const newH = Math.max(MIN_HEIGHT, startH + (e.clientY - startY) / zoom);
            onUpdateBlock(id, { width: newW, height: newH });
        }
    }, [items, zoom, calculateSnapGuides, applyMagneticSnap, onUpdateBlock]);

    const handleMouseUp = useCallback(() => {
        draggingRef.current = null;
        resizingRef.current = null;
        setSnapGuides({ horizontal: [], vertical: [] });
        document.removeEventListener('mousemove', handleMouseMove);
    }, [handleMouseMove]);

    const handleBlockMouseDown = (e: React.MouseEvent, id: string) => {
        if ((e.target as HTMLElement).dataset.resize) return;
        const item = items.find(i => i.id === id);
        if (!item) return;
        e.preventDefault();
        onSelectBlock(id);
        draggingRef.current = { id, startX: e.clientX, startY: e.clientY, origX: item.x, origY: item.y };
        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp, { once: true });
    };

    const handleResizeStart = (e: React.MouseEvent, id: string) => {
        e.stopPropagation();
        const item = items.find(i => i.id === id);
        if (!item) return;
        resizingRef.current = { id, startX: e.clientX, startY: e.clientY, startW: item.width, startH: item.height };
        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp, { once: true });
    };

    const handleBgUpload = (e: React.ChangeEvent<HTMLInputElement>, setter: (img: string | null) => void) => {
        const file = e.target.files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (ev) => setter(ev.target?.result as string);
            reader.readAsDataURL(file);
        }
    };

    const currentBg = activePage === 1 ? backgroundImage : backgroundImagePage2;

    return (
        <div className="flex flex-col h-full">
            {/* Canvas Toolbar */}
            <div className="flex items-center justify-between gap-3 mb-3 px-1 flex-wrap">
                {/* Page Navigation */}
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => onSetActivePage(1)}
                        className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-all ${activePage === 1
                            ? 'bg-rose-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                    >
                        Seite 1
                    </button>
                    <button
                        onClick={() => onSetActivePage(2)}
                        className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-all ${activePage === 2
                            ? 'bg-rose-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                    >
                        Seite 2+
                    </button>
                </div>

                {/* Zoom */}
                <div className="flex items-center gap-1.5">
                    <button onClick={() => setZoom(z => Math.max(0.5, z - 0.1))} className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors">
                        <ZoomOut className="w-3.5 h-3.5" />
                    </button>
                    <span className="text-xs text-slate-500 min-w-[3rem] text-center font-medium">{Math.round(zoom * 100)}%</span>
                    <button onClick={() => setZoom(z => Math.min(2, z + 0.1))} className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors">
                        <ZoomIn className="w-3.5 h-3.5" />
                    </button>
                </div>

                {/* Background Upload */}
                <div className="flex items-center gap-1.5">
                    <input ref={bgInputRef} type="file" accept="image/*" className="hidden" onChange={e => handleBgUpload(e, onSetBackgroundImage)} />
                    <input ref={bgInputPage2Ref} type="file" accept="image/*" className="hidden" onChange={e => handleBgUpload(e, onSetBackgroundImagePage2)} />
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => (activePage === 1 ? bgInputRef : bgInputPage2Ref).current?.click()}
                        className="text-xs"
                    >
                        <Upload className="w-3 h-3 mr-1" />
                        {currentBg ? 'Hintergrund ändern' : 'Hintergrund'}
                    </Button>
                    {currentBg && (
                        <button
                            onClick={() => (activePage === 1 ? onSetBackgroundImage : onSetBackgroundImagePage2)(null)}
                            className="p-1.5 rounded-lg bg-red-50 text-red-500 hover:bg-red-100 transition-colors"
                        >
                            <X className="w-3 h-3" />
                        </button>
                    )}
                </div>
            </div>

            {/* Canvas Area */}
            <div className="flex-1 flex items-start justify-center overflow-auto bg-slate-100/50 rounded-2xl p-6" onClick={() => onSelectBlock(null)}>
                <div
                    ref={canvasRef}
                    className="relative bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden flex-shrink-0"
                    style={{
                        width: PAGE_FORMATS[pageFormat].width * zoom,
                        height: PAGE_FORMATS[pageFormat].height * zoom,
                        transformOrigin: 'top center'
                    }}
                    onClick={e => e.stopPropagation()}
                >
                    {/* Inner scale container */}
                    <div style={{ width: PAGE_FORMATS[pageFormat].width, height: PAGE_FORMATS[pageFormat].height, transform: `scale(${zoom})`, transformOrigin: 'top left' }}>
                        {/* Background Image */}
                        {currentBg && (
                            <img
                                src={currentBg}
                                alt="Hintergrund"
                                className="absolute inset-0 w-full h-full object-cover pointer-events-none opacity-30"
                                style={{ zIndex: 0 }}
                            />
                        )}

                        {/* Snap Guide Lines */}
                        {snapGuides.vertical.map((x, idx) => (
                            <div key={`v-${idx}`} className="absolute top-0 bottom-0 border-l border-dashed border-rose-400 pointer-events-none" style={{ left: x, zIndex: 1000 }} />
                        ))}
                        {snapGuides.horizontal.map((y, idx) => (
                            <div key={`h-${idx}`} className="absolute left-0 right-0 border-t border-dashed border-rose-400 pointer-events-none" style={{ top: y, zIndex: 1000 }} />
                        ))}

                        {/* Blocks */}
                        {pageItems.map(item => (
                            <div
                                key={item.id}
                                className={`absolute cursor-move group/block ${selectedId === item.id
                                    ? 'ring-2 ring-rose-500 ring-offset-1'
                                    : 'hover:ring-2 hover:ring-slate-300 hover:ring-offset-1'
                                    } transition-shadow duration-100`}
                                style={{
                                    left: item.x,
                                    top: item.y,
                                    width: item.width,
                                    height: item.height,
                                    zIndex: item.z + 1,
                                    borderRadius: 4
                                }}
                                onMouseDown={e => handleBlockMouseDown(e, item.id)}
                                onClick={e => { e.stopPropagation(); onSelectBlock(item.id); }}
                            >
                                <div className={`w-full h-full overflow-hidden p-1 bg-white/90 rounded ${item.type === 'table' ? '' : 'flex items-center'}`} style={{ borderRadius: 3 }}>
                                    <div className="w-full">{renderBlockContent(item, liveData)}</div>
                                </div>

                                {/* Block label on hover */}
                                <div className="absolute -top-5 left-0 px-1.5 py-0.5 text-[9px] font-medium bg-slate-800 text-white rounded opacity-0 group-hover/block:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                                    {BLOCK_LABELS[item.type]}
                                </div>

                                {selectedId === item.id && (
                                    <>
                                        {/* Delete button */}
                                        <button
                                            onClick={e => { e.stopPropagation(); onDeleteBlock(item.id); }}
                                            className="absolute -top-2 -right-2 w-5 h-5 bg-red-500 text-white rounded-full text-xs flex items-center justify-center hover:bg-red-600 shadow-sm transition-colors z-10"
                                        >
                                            ×
                                        </button>
                                        {/* Resize handle */}
                                        <div
                                            data-resize="true"
                                            className="absolute -bottom-1 -right-1 w-3.5 h-3.5 bg-rose-500 rounded-sm cursor-se-resize hover:bg-rose-600 shadow-sm transition-colors z-10"
                                            onMouseDown={e => handleResizeStart(e, item.id)}
                                        />
                                    </>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Footer info */}
            <p className="text-[10px] text-slate-400 text-center mt-2">
                {PAGE_FORMATS[pageFormat].label} · Magnetisches Einrasten · {pageItems.length} Blöcke auf Seite {activePage}
            </p>
        </div>
    );
}

// ─── Block Content Renderer ──────────────────────────────────────────

function renderBlockContent(item: FormBlock, data: Record<string, string>) {
    const styles = getBlockStyles(item);
    switch (item.type) {
        case 'heading':
        case 'text': {
            const raw = item.content || 'Text...';
            const filled = replacePlaceholders(raw, data);
            return (
                <div style={{
                    fontSize: styles.fontSize,
                    color: styles.color,
                    fontWeight: styles.fontWeight as React.CSSProperties['fontWeight'],
                    textAlign: styles.textAlign,
                    whiteSpace: 'pre-line'
                }}>
                    {filled}
                </div>
            );
        }
        case 'doknr': return <div style={{ fontSize: 12 }}><span className="text-[9px] text-slate-400 block">Dokumentnr.</span>{data.dokumentnummer}</div>;
        case 'projektnr': return <div style={{ fontSize: 12 }}><span className="text-[9px] text-slate-400 block">Projektnr.</span>{data.projektnummer}</div>;
        case 'kundennummer': return <div style={{ fontSize: 12 }}><span className="text-[9px] text-slate-400 block">Kundennr.</span>{data.kundennummer}</div>;
        case 'kunde': return <div style={{ fontSize: 14 }}><span className="text-[9px] text-slate-400 block">Kunde</span>{data.kundenname}</div>;
        case 'adresse': return <div style={{ fontSize: 13, whiteSpace: 'pre-line' }}><span className="text-[9px] text-slate-400 block">Adresse</span>{data.kundenadresse}</div>;
        case 'dokumenttyp': return <div style={{ fontSize: 16, fontWeight: 700 }}>{data.dokumenttyp}</div>;
        case 'datum': return <div style={{ fontSize: 12 }}><span className="text-[9px] text-slate-400 block">Datum</span>{data.datum}</div>;
        case 'seitenzahl': return <div style={{ fontSize: 12, textAlign: 'right' }}>{data.seitenzahl}</div>;
        case 'logo': return <img src={item.content || '/image001.png'} alt="Logo" className="max-w-full max-h-full object-contain" />;
        case 'table': return (
            <table className="w-full text-[10px] border-collapse">
                <thead>
                    <tr className="bg-slate-100">
                        <th className="p-1.5 text-left font-semibold">Pos.</th>
                        <th className="p-1.5 font-semibold">Menge</th>
                        <th className="p-1.5 font-semibold">ME</th>
                        <th className="p-1.5 text-left font-semibold">Bezeichnung</th>
                        <th className="p-1.5 text-right font-semibold">EP</th>
                        <th className="p-1.5 text-right font-semibold">GP</th>
                    </tr>
                </thead>
                <tbody>
                    <tr className="border-t border-slate-100">
                        <td className="p-1.5">1.1</td>
                        <td className="p-1.5 text-center">10</td>
                        <td className="p-1.5 text-center">Stk</td>
                        <td className="p-1.5">Beispielleistung</td>
                        <td className="p-1.5 text-right">0,00 €</td>
                        <td className="p-1.5 text-right">0,00 €</td>
                    </tr>
                </tbody>
            </table>
        );
        default: return <div>{item.content || 'Block'}</div>;
    }
}
