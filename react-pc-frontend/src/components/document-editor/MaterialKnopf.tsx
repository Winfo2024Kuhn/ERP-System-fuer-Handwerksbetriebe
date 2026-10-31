import { ChevronDown, Package, ShoppingCart } from 'lucide-react';
import { Button } from '../ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../ui/dropdown-menu';
import { shopUnterzeile } from './shopUnterzeile';

/** Gleiche Optik wie die übrigen Knöpfe der Editor-Kopfleiste. */
const KNOPF_KLASSEN = 'h-7 px-1.5 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700';

interface MaterialKnopfProps {
    /** Öffnet die Auswahl aus dem Artikelstamm. */
    onArtikelstamm: () => void;
    /**
     * Öffnet den Warenkorb aus dem Lieferanten-Shop. Ohne diese Prop ist
     * „Material“ ein einfacher Knopf, der direkt den Artikelstamm öffnet.
     */
    onShop?: () => void;
    /** Namen der verfügbaren Shops – für die Unterzeile im Menü. */
    shopLieferantenNamen?: string[];
    disabled?: boolean;
}

/**
 * „Material“ in der Kopfleiste des Dokumenteditors. Mit Shop-Anbindung ein
 * kleines Menü mit zwei klar beschriebenen Wegen – so bleibt die Leiste
 * schmal, und niemand muss raten, woher das Material kommt.
 */
export function MaterialKnopf({ onArtikelstamm, onShop, shopLieferantenNamen, disabled }: MaterialKnopfProps) {
    if (!onShop) {
        return (
            <Button variant="ghost" size="sm" onClick={onArtikelstamm} disabled={disabled} className={KNOPF_KLASSEN}>
                <Package className="w-3 h-3" aria-hidden="true" />
                Material
            </Button>
        );
    }
    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild disabled={disabled}>
                <Button variant="ghost" size="sm" className={KNOPF_KLASSEN} title="Material aus dem Artikelstamm oder aus einem Lieferanten-Shop">
                    <Package className="w-3 h-3" aria-hidden="true" />
                    Material
                    <ChevronDown className="w-3 h-3 text-slate-400" aria-hidden="true" />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-80">
                <DropdownMenuItem onSelect={onArtikelstamm} className="items-start gap-3 py-2">
                    <Package className="mt-0.5 text-slate-500" aria-hidden="true" />
                    <span className="flex min-w-0 flex-col">
                        <span className="font-medium text-slate-900">Aus Artikelstamm</span>
                        <span className="text-xs text-slate-500">Artikel, die schon im Programm gepflegt sind</span>
                    </span>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={onShop} className="items-start gap-3 py-2">
                    <ShoppingCart className="mt-0.5 text-slate-500" aria-hidden="true" />
                    <span className="flex min-w-0 flex-col">
                        <span className="font-medium text-slate-900">Aus Lieferanten-Shop</span>
                        <span className="text-xs text-slate-500">{shopUnterzeile(shopLieferantenNamen)}</span>
                    </span>
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
