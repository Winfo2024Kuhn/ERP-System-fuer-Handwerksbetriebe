import { useId, useRef, useState } from 'react';
import { ImageOff, Link2, Loader2, Trash2, Upload } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { cn } from '../../../lib/utils';
import { bildVerkleinern, ERLAUBTE_BILD_TYPEN, formatBytes, type BildArt } from './bildVerkleinern';
import { bildLinkAufbereiten, dataUriBytes, sichereBildUrl } from './signaturSicherheit';

/**
 * Ein Bild der Signatur (Foto, Logo, Banner): entweder als Link
 * (https-Adresse, z. B. von der eigenen Website oder Dropbox) oder
 * hochgeladen – dann wird es im Browser verkleinert und direkt eingebettet.
 */

interface BildFeldProps {
    art: BildArt;
    label: string;
    hinweis?: string;
    wert: string;
    onChange: (wert: string) => void;
}

type LadeStand = 'laedt' | 'fertig' | 'fehler';

/** Kleines Vorschaubild: Platzhalter beim Laden, Hinweis bei Fehler. */
function BildMiniatur({ src, alt }: { src: string; alt: string }) {
    const [stand, setStand] = useState<{ src: string; wert: LadeStand }>({ src, wert: 'laedt' });
    // Neuer Link → wieder „lädt“, ohne Effekt (Zustand hängt am src).
    const aktuell: LadeStand = stand.src === src ? stand.wert : 'laedt';
    return (
        <div className="relative h-16 w-16 shrink-0 overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
            {aktuell === 'laedt' && <div className="absolute inset-0 bg-slate-200 motion-safe:animate-pulse" aria-hidden="true" />}
            {aktuell === 'fehler' ? (
                <div role="img" aria-label="Bild nicht erreichbar" className="flex h-full w-full items-center justify-center bg-slate-100">
                    <ImageOff className="h-6 w-6 text-slate-400" aria-hidden="true" />
                </div>
            ) : (
                <img
                    src={src}
                    alt={alt}
                    className={cn('h-full w-full object-contain motion-safe:transition-opacity motion-safe:duration-200', aktuell === 'fertig' ? 'opacity-100' : 'opacity-0')}
                    onLoad={() => setStand({ src, wert: 'fertig' })}
                    onError={() => setStand({ src, wert: 'fehler' })}
                />
            )}
        </div>
    );
}

export function BildFeld({ art, label, hinweis, wert, onChange }: BildFeldProps) {
    const id = useId();
    const toast = useToast();
    const dateiRef = useRef<HTMLInputElement>(null);
    const [laedtHoch, setLaedtHoch] = useState(false);

    const hochgeladen = wert.startsWith('data:');
    const sicher = sichereBildUrl(wert);
    const ungueltig = wert.trim() !== '' && !sicher;

    const dateiGewaehlt = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const datei = event.target.files?.[0];
        event.target.value = '';
        if (!datei) return;
        setLaedtHoch(true);
        try {
            const ergebnis = await bildVerkleinern(datei, art);
            onChange(ergebnis.dataUri);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : `${label} konnte nicht hochgeladen werden.`);
        } finally {
            setLaedtHoch(false);
        }
    };

    return (
        <div className="rounded-lg border border-slate-200 p-3" role="group" aria-labelledby={`${id}-titel`}>
            <div className="flex items-start gap-3">
                {sicher ? <BildMiniatur src={sicher} alt={`${label} (Vorschau)`} /> : (
                    <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50" aria-hidden="true">
                        <ImageOff className="h-5 w-5 text-slate-300" />
                    </div>
                )}
                <div className="min-w-0 flex-1 space-y-2">
                    <div>
                        <p id={`${id}-titel`} className="text-sm font-medium text-slate-800">{label}</p>
                        {hinweis && <p className="text-xs text-slate-500">{hinweis}</p>}
                    </div>

                    {hochgeladen ? (
                        <p className="text-sm text-slate-600">
                            Hochgeladenes Bild <span className="text-slate-400">·</span> {formatBytes(dataUriBytes(wert))}
                        </p>
                    ) : (
                        <div className="relative">
                            <Link2 className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                            <Input
                                id={`${id}-link`}
                                type="url"
                                inputMode="url"
                                aria-label={`${label}: Link zum Bild`}
                                aria-invalid={ungueltig || undefined}
                                aria-describedby={ungueltig ? `${id}-fehler` : undefined}
                                placeholder="https://… Link zum Bild"
                                value={wert}
                                onChange={(e) => onChange(e.target.value)}
                                onBlur={(e) => {
                                    const aufbereitet = bildLinkAufbereiten(e.target.value);
                                    if (aufbereitet !== e.target.value) onChange(aufbereitet);
                                }}
                                className="pl-9"
                            />
                        </div>
                    )}
                    {ungueltig && (
                        <p id={`${id}-fehler`} className="text-xs font-medium text-rose-700">
                            Nur Links, die mit https:// oder http:// beginnen, sind möglich.
                        </p>
                    )}

                    <div className="flex flex-wrap items-center gap-2">
                        <input
                            ref={dateiRef}
                            type="file"
                            accept={ERLAUBTE_BILD_TYPEN.join(',')}
                            className="sr-only"
                            tabIndex={-1}
                            aria-hidden="true"
                            onChange={dateiGewaehlt}
                            data-testid={`bild-datei-${art}`}
                        />
                        <Button type="button" variant="outline" size="sm" onClick={() => dateiRef.current?.click()} disabled={laedtHoch}>
                            {laedtHoch ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Upload className="h-4 w-4" aria-hidden="true" />}
                            {laedtHoch ? 'Bild wird verkleinert …' : hochgeladen ? 'Anderes Bild hochladen' : 'Bild hochladen'}
                        </Button>
                        {wert && (
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => onChange('')}
                                aria-label={`${label} entfernen`}
                                className="text-slate-600 hover:bg-rose-50 hover:text-rose-700"
                            >
                                <Trash2 className="h-4 w-4" aria-hidden="true" /> Entfernen
                            </Button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
