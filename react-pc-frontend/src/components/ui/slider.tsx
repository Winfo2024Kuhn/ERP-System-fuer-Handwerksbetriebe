import { useMemo, type ComponentProps } from 'react';
import * as SliderPrimitive from '@radix-ui/react-slider';
import { cn } from '../../lib/utils';

interface SliderProps extends ComponentProps<typeof SliderPrimitive.Root> {
    /** Name des Reglers für Screenreader (am Griff), z. B. „Größe der Vorschaubilder“. */
    beschriftung: string;
}

/**
 * Schieberegler (shadcn/Radix), auf Rose/Slate umgestellt. Tastatur:
 * Pfeile, Bild auf/ab, Pos1/Ende. `onValueChange` meldet jede Bewegung,
 * `onValueCommit` erst das Loslassen – teure Folgen (Speichern) dorthin.
 */
export function Slider({ className, defaultValue, value, min = 0, max = 100, beschriftung, ...props }: SliderProps) {
    const werte = useMemo(
        () => (Array.isArray(value) ? value : Array.isArray(defaultValue) ? defaultValue : [min]),
        [value, defaultValue, min],
    );
    return (
        <SliderPrimitive.Root
            data-slot="slider"
            defaultValue={defaultValue}
            value={value}
            min={min}
            max={max}
            className={cn('relative flex w-full touch-none select-none items-center py-2 data-[disabled]:opacity-50', className)}
            {...props}
        >
            <SliderPrimitive.Track data-slot="slider-track" className="relative h-1.5 w-full grow overflow-hidden rounded-full bg-slate-200">
                <SliderPrimitive.Range data-slot="slider-range" className="absolute h-full bg-rose-600" />
            </SliderPrimitive.Track>
            {werte.map((_, index) => (
                <SliderPrimitive.Thumb
                    key={index}
                    data-slot="slider-thumb"
                    aria-label={beschriftung}
                    className="block h-4 w-4 shrink-0 cursor-grab rounded-full border-2 border-rose-600 bg-white shadow-sm transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2 active:cursor-grabbing disabled:pointer-events-none"
                />
            ))}
        </SliderPrimitive.Root>
    );
}
