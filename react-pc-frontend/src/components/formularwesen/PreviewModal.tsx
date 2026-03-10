import type { FormBlock, FormBlockStyles } from '../../types';
import { STYLE_DEFAULTS, PAGE_FORMATS, replacePlaceholders } from './constants';

interface PreviewModalProps {
    items: FormBlock[];
    backgroundImage: string | null;
    backgroundImagePage2: string | null;
    liveData: Record<string, string>;
    onClose: () => void;
}

const getBlockStyles = (item: FormBlock): FormBlockStyles => ({
    ...STYLE_DEFAULTS[item.type],
    ...item.styles
});

/** Full-screen preview modal showing both pages */
export default function PreviewModal({ items, backgroundImage, backgroundImagePage2, liveData, onClose }: PreviewModalProps) {
    const page1Items = items.filter(i => i.page === 1).sort((a, b) => a.z - b.z);
    const page2Items = items.filter(i => i.page === 2).sort((a, b) => a.z - b.z);

    const renderBlock = (item: FormBlock) => {
        const styles = getBlockStyles(item);
        switch (item.type) {
            case 'heading':
            case 'text': {
                const raw = item.content || '';
                const filled = replacePlaceholders(raw, liveData);
                return <div style={{ fontSize: styles.fontSize, color: styles.color, fontWeight: styles.fontWeight as React.CSSProperties['fontWeight'], textAlign: styles.textAlign, whiteSpace: 'pre-line' }}>{filled}</div>;
            }
            case 'doknr': return <div style={{ fontSize: 12 }}>{liveData.dokumentnummer}</div>;
            case 'projektnr': return <div style={{ fontSize: 12 }}>{liveData.projektnummer}</div>;
            case 'kundennummer': return <div style={{ fontSize: 12 }}>{liveData.kundennummer}</div>;
            case 'kunde': return <div style={{ fontSize: 14 }}>{liveData.kundenname}</div>;
            case 'adresse': return <div style={{ fontSize: 13, whiteSpace: 'pre-line' }}>{liveData.kundenadresse}</div>;
            case 'dokumenttyp': return <div style={{ fontSize: 16, fontWeight: 700 }}>{liveData.dokumenttyp}</div>;
            case 'datum': return <div style={{ fontSize: 12 }}>{liveData.datum}</div>;
            case 'seitenzahl': return <div style={{ fontSize: 12, textAlign: 'right' }}>{liveData.seitenzahl}</div>;
            case 'logo': return <img src={item.content || '/image001.png'} alt="Logo" className="max-w-full max-h-full object-contain" />;
            case 'table': return (
                <table className="w-full text-xs border-collapse">
                    <thead><tr className="bg-slate-100"><th className="p-2 text-left">Pos.</th><th className="p-2">Menge</th><th className="p-2">ME</th><th className="p-2 text-left">Bezeichnung</th><th className="p-2 text-right">EP</th><th className="p-2 text-right">GP</th></tr></thead>
                    <tbody>
                        <tr><td className="p-2">1.1</td><td className="p-2 text-center">12</td><td className="p-2 text-center">m²</td><td className="p-2">Trockenbauarbeiten nach DIN 18181</td><td className="p-2 text-right">45,00 €</td><td className="p-2 text-right">540,00 €</td></tr>
                        <tr><td className="p-2">1.2</td><td className="p-2 text-center">8</td><td className="p-2 text-center">Stk</td><td className="p-2">Gipskartonplatten 12,5mm</td><td className="p-2 text-right">18,50 €</td><td className="p-2 text-right">148,00 €</td></tr>
                        <tr><td className="p-2">2.1</td><td className="p-2 text-center">6</td><td className="p-2 text-center">Std</td><td className="p-2">Montagearbeiten Facharbeiter</td><td className="p-2 text-right">58,00 €</td><td className="p-2 text-right">348,00 €</td></tr>
                        <tr className="font-semibold border-t border-slate-300"><td className="p-2" colSpan={5}>Gesamt netto</td><td className="p-2 text-right">1.036,00 €</td></tr>
                    </tbody>
                </table>
            );
            default: return <div>{item.content || 'Block'}</div>;
        }
    };

    const renderPage = (pageItems: FormBlock[], bg: string | null, pageNum: number) => (
        <div className="flex-shrink-0">
            <p className="text-xs font-medium text-slate-500 mb-2 text-center">Seite {pageNum}</p>
            <div
                className="relative bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden"
                style={{
                    width: PAGE_FORMATS.A4.width,
                    height: PAGE_FORMATS.A4.height
                }}
            >
                {bg && (
                    <img src={bg} alt="" className="absolute inset-0 w-full h-full object-cover pointer-events-none opacity-30" style={{ zIndex: 0 }} />
                )}
                {pageItems.map(item => (
                    <div key={item.id} className={`absolute ${item.type === 'table' ? '' : 'flex items-center'}`} style={{ left: item.x, top: item.y, width: item.width, height: item.height, zIndex: item.z }}>
                        <div className="w-full">{renderBlock(item)}</div>
                    </div>
                ))}
            </div>
        </div>
    );

    return (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-6">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl max-h-[95vh] overflow-hidden flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
                    <h3 className="text-lg font-bold text-slate-900">Vorschau</h3>
                    <button
                        onClick={onClose}
                        className="p-2 rounded-xl bg-slate-100 text-slate-500 hover:bg-rose-100 hover:text-rose-600 transition-colors"
                    >
                        ×
                    </button>
                </div>

                {/* Preview Content */}
                <div className="flex-1 overflow-auto p-8">
                    <div className="flex gap-8 justify-center items-start flex-wrap">
                        {renderPage(page1Items, backgroundImage, 1)}
                        {page2Items.length > 0 && renderPage(page2Items, backgroundImagePage2, 2)}
                    </div>
                </div>
            </div>
        </div>
    );
}
