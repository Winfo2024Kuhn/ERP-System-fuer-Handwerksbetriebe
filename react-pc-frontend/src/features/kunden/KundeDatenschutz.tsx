import React, { useId, useState } from 'react';
import { Lock, Trash2, UserX } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import type { KundeDetail } from '../../types';

/** Höchstlänge des Grundes für die Sperre – so prüft es auch das Backend. */
const GRUND_MAX_LAENGE = 500;

/**
 * ISO-Datum („2034-12-31“) oder ISO-Zeitpunkt als deutsches Datum. Ein reines
 * Datum wird ohne Zeitzonen-Umrechnung gelesen, damit aus dem 31.12. nicht
 * der 30.12. wird.
 */
function formatiereDatumDE(wert?: string | null): string {
    if (!wert) return '–';
    const nurDatum = /^(\d{4})-(\d{2})-(\d{2})$/.exec(wert);
    if (nurDatum) return `${nurDatum[3]}.${nurDatum[2]}.${nurDatum[1]}`;
    const datum = new Date(wert);
    return Number.isNaN(datum.getTime()) ? '–' : datum.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

async function fehlermeldung(res: Response, rueckfall: string): Promise<string> {
    const body = await res.json().catch(() => null) as { message?: unknown } | null;
    return typeof body?.message === 'string' && body.message.trim() ? body.message : rueckfall;
}

// ==================== INFO-TEXTE ====================

const LoeschenInfo: React.FC = () => (
    <InfoButton titel="Kunde löschen (Datenschutz)">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Rechnungen müssen 8 Jahre, Angebote und Geschäftsbriefe 6 Jahre aufbewahrt werden, jeweils ab Ende des Jahres, in dem sie entstanden sind. Hängen solche Unterlagen am Kunden, darf er noch nicht komplett gelöscht werden.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Dann werden alle Daten gelöscht, die nicht aufbewahrt werden müssen, und der Kunde wird gesperrt. Er erscheint nicht mehr in Listen und Suchen und kann nicht mehr bearbeitet werden. Name, Anschrift und die Unterlagen bleiben bis zum Fristende erhalten. Die Löschung wird mit Datum und Person protokolliert, ohne Kundendaten.</p>
        </InfoAbschnitt>
    </InfoButton>
);

const GesperrtInfo: React.FC = () => (
    <InfoButton titel="Gesperrter Kunde">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Der Kunde hat die Löschung seiner Daten verlangt. Was nicht aufbewahrt werden muss, ist bereits gelöscht. Kommt nach der Sperre noch eine Gutschrift, ein Storno oder eine Mahnung dazu, verlängert sich die Aufbewahrungsfrist automatisch.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Die Unterlagen bleiben für Finanzamt und Prüfungen erhalten und dürfen nur noch dafür genutzt werden. Nach Ablauf der Frist kann ein Admin den Kunden anonymisieren. Nichts davon passiert automatisch.</p>
        </InfoAbschnitt>
    </InfoButton>
);

const AnonymisierenInfo: React.FC = () => (
    <InfoButton titel="Kunde anonymisieren">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Der Name wird durch „Gelöschter Kunde“ und die Kundennummer ersetzt, Anrede und Anschrift werden geleert. Bei seinen Projekten und Anfragen wird die Baustellen-Anschrift geleert, die Bezeichnung bleibt. In Freigabe-Links wird der Name entfernt, die E-Mail-Adresse bei nicht angenommenen Links ebenfalls. Online angenommene Angebote behalten E-Mail-Adresse und Unterschrift als Auftragsnachweis. Der Vorgang wird mit Datum und Person protokolliert, ohne Kundendaten.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Der Kunde ist in den Stammdaten nicht mehr zu erkennen. Das lässt sich nicht rückgängig machen. Rechnungen, Angebote und E-Mails bleiben unverändert erhalten, in diesen alten Unterlagen stehen Name und Anschrift weiterhin. Kundennummer, Projekte und Beträge bleiben, damit die Buchhaltung stimmt.</p>
        </InfoAbschnitt>
    </InfoButton>
);

// ==================== GESPERRT-BAND ====================

interface GesperrtBandProps {
    kunde: KundeDetail;
    /** Kunde wurde anonymisiert; die Antwort ist die aktualisierte Akte. */
    onAnonymisiert: (kunde: KundeDetail) => void;
}

/**
 * Ruhiges Band oben in der Akte eines gesperrten Kunden. Ist die
 * Aufbewahrungsfrist abgelaufen, bekommt ein Admin hier „Anonymisieren…“
 * (das Backend befüllt `loeschPruefung` nur für Admins).
 */
export const KundeGesperrtBand: React.FC<GesperrtBandProps> = ({ kunde, onAnonymisiert }) => {
    const [dialogOffen, setDialogOffen] = useState(false);
    // Frisch berechnetes Fristende hat Vorrang: eine spätere Gutschrift verlängert die Frist.
    const frist = kunde.loeschPruefung?.aufbewahrenBis ?? kunde.aufbewahrenBis;
    const anonymisierbar = !kunde.anonymisiertAm && kunde.loeschPruefung?.anonymisierbar === true;

    return (
        <div
            role="status"
            data-testid="kunde-gesperrt-band"
            className="mb-4 flex flex-wrap items-start gap-3 rounded-lg border border-slate-300 bg-slate-100 px-4 py-3 text-sm text-slate-700"
        >
            <div className="flex min-w-0 flex-1 basis-72 items-start gap-3">
                <Lock className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                <p className="min-w-0 flex-1">
                    {kunde.anonymisiertAm ? (
                        <>Anonymisiert am {formatiereDatumDE(kunde.anonymisiertAm)}. Name und Anschrift wurden entfernt, die Unterlagen bleiben erhalten.</>
                    ) : (
                        <>
                            Aus Datenschutzgründen gesperrt seit {formatiereDatumDE(kunde.gesperrtAm)}. Unterlagen werden bis{' '}
                            {formatiereDatumDE(frist)} aufbewahrt, danach kann der Kunde anonymisiert werden.
                        </>
                    )}
                </p>
                <GesperrtInfo />
            </div>
            {anonymisierbar && (
                <>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setDialogOffen(true)}
                        className="shrink-0 border-red-200 bg-white text-red-700 hover:bg-red-50 focus-visible:ring-red-500"
                    >
                        <UserX className="h-4 w-4" /> Anonymisieren…
                    </Button>
                    <KundeAnonymisierenDialog
                        kunde={kunde}
                        open={dialogOffen}
                        onClose={() => setDialogOffen(false)}
                        onAnonymisiert={aktualisiert => {
                            setDialogOffen(false);
                            onAnonymisiert(aktualisiert);
                        }}
                    />
                </>
            )}
        </div>
    );
};

// ==================== ANONYMISIEREN-DIALOG ====================

interface AnonymisierenDialogProps {
    kunde: KundeDetail;
    open: boolean;
    onClose: () => void;
    onAnonymisiert: (kunde: KundeDetail) => void;
}

const KundeAnonymisierenDialog: React.FC<AnonymisierenDialogProps> = ({ kunde, open, onClose, onAnonymisiert }) => {
    const toast = useToast();
    const titelId = useId();
    const [laeuft, setLaeuft] = useState(false);

    const schliessen = () => {
        if (!laeuft) onClose();
    };

    const anonymisieren = async () => {
        setLaeuft(true);
        try {
            const res = await fetch(`/api/kunden/${kunde.id}/anonymisieren`, { method: 'POST' });
            if (!res.ok) {
                toast.error(await fehlermeldung(res, 'Der Kunde konnte nicht anonymisiert werden.'));
                return;
            }
            const aktualisiert = await res.json() as KundeDetail;
            onAnonymisiert(aktualisiert);
            toast.success('Kunde wurde anonymisiert.');
        } catch {
            toast.error('Der Kunde konnte nicht anonymisiert werden. Bitte die Verbindung prüfen.');
        } finally {
            setLaeuft(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={offen => { if (!offen) schliessen(); }} className="w-full max-w-xl" aria-labelledby={titelId}>
            <DialogContent className="overflow-y-auto">
                <DialogHeader className="pr-10">
                    <div className="flex items-center gap-2">
                        <DialogTitle id={titelId} className="text-slate-900">Kunde anonymisieren?</DialogTitle>
                        <AnonymisierenInfo />
                    </div>
                    <DialogDescription>
                        {kunde.kundennummer} · {kunde.name}
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4 text-sm text-slate-700">
                    <div>
                        <p className="font-semibold text-slate-900">Das passiert</p>
                        <ul className="mt-1 list-disc space-y-0.5 pl-5">
                            <li>Der Name wird zu „Gelöschter Kunde {kunde.kundennummer}“, Anrede und Anschrift werden geleert.</li>
                            <li>Die Baustellen-Anschrift seiner Projekte und Anfragen wird geleert, die Bezeichnung bleibt.</li>
                            <li>Name in Freigabe-Links wird entfernt (angenommene Angebote behalten den Auftragsnachweis).</li>
                        </ul>
                    </div>
                    <div>
                        <p className="font-semibold text-slate-900">Bleibt erhalten</p>
                        <p className="mt-1">Rechnungen, Angebote und E-Mails bleiben unverändert.</p>
                    </div>
                    <p className="rounded-lg border border-red-200 bg-red-50 p-3 font-medium text-red-800">
                        Das lässt sich nicht rückgängig machen.
                    </p>
                </div>

                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={schliessen} disabled={laeuft}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={anonymisieren}
                        disabled={laeuft}
                        className="border-red-600 bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500"
                    >
                        <UserX className="h-4 w-4" />
                        {laeuft ? 'Wird anonymisiert…' : 'Endgültig anonymisieren'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

// ==================== DATENSCHUTZ-DIALOG ====================

interface DatenschutzDialogProps {
    kunde: KundeDetail;
    open: boolean;
    onClose: () => void;
    onGesperrt: (kunde: KundeDetail) => void;
}

const KundeDatenschutzDialog: React.FC<DatenschutzDialogProps> = ({ kunde, open, onClose, onGesperrt }) => {
    const toast = useToast();
    const grundId = useId();
    const titelId = useId();
    const [grund, setGrund] = useState('');
    const [laeuft, setLaeuft] = useState(false);
    const pruefung = kunde.loeschPruefung;
    const hindernisse = pruefung?.hindernisse ?? [];

    const schliessen = () => {
        if (laeuft) return;
        setGrund('');
        onClose();
    };

    const sperren = async () => {
        const text = grund.trim();
        if (text.length > GRUND_MAX_LAENGE) {
            toast.error(`Der Grund darf höchstens ${GRUND_MAX_LAENGE} Zeichen lang sein.`);
            return;
        }
        setLaeuft(true);
        try {
            const res = await fetch(`/api/kunden/${kunde.id}/datenschutz-sperre`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(text ? { grund: text } : {}),
            });
            if (!res.ok) {
                toast.error(await fehlermeldung(res, 'Die Kundendaten konnten nicht gelöscht werden.'));
                return;
            }
            const aktualisiert = await res.json() as KundeDetail;
            setGrund('');
            onGesperrt(aktualisiert);
            toast.success('Kundendaten wurden gelöscht, der Kunde ist gesperrt.');
        } catch {
            toast.error('Die Kundendaten konnten nicht gelöscht werden. Bitte die Verbindung prüfen.');
        } finally {
            setLaeuft(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={offen => { if (!offen) schliessen(); }} className="w-full max-w-xl" aria-labelledby={titelId}>
            <DialogContent className="overflow-y-auto">
                <DialogHeader className="pr-10">
                    <div className="flex items-center gap-2">
                        <DialogTitle id={titelId} className="text-slate-900">Kundendaten löschen (Datenschutz)</DialogTitle>
                        <LoeschenInfo />
                    </div>
                    <DialogDescription>
                        {kunde.kundennummer} · {kunde.name}
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4 text-sm text-slate-700">
                    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-900">
                        <p className="font-medium">
                            An diesem Kunden hängen: {hindernisse.join(', ')}.
                        </p>
                        <p className="mt-1">Diese Unterlagen müssen aufbewahrt werden. Der Kunde kann deshalb noch nicht komplett gelöscht werden.</p>
                    </div>

                    <div>
                        <p className="font-semibold text-slate-900">Wird sofort gelöscht</p>
                        <ul className="mt-1 list-disc space-y-0.5 pl-5">
                            <li>Telefon- und Handynummern sowie weitere Rufnummern</li>
                            <li>E-Mail-Adressen</li>
                            <li>Ansprechpartner</li>
                            <li>Notizen</li>
                            <li>Kalendereinträge, Anrufe und Sprachnachrichten verlieren den Bezug zum Kunden</li>
                        </ul>
                    </div>

                    <div>
                        <p className="font-semibold text-slate-900">Bleibt erhalten</p>
                        <p className="mt-1">
                            Name, Anschrift und Kundennummer sowie Projekte, Angebote und Rechnungen, bis{' '}
                            <span className="font-medium text-slate-900">{formatiereDatumDE(pruefung?.aufbewahrenBis)}</span>.
                            Danach kann der Kunde anonymisiert werden.
                        </p>
                    </div>

                    <div>
                        <Label htmlFor={grundId}>Grund (optional)</Label>
                        <textarea
                            id={grundId}
                            value={grund}
                            onChange={e => setGrund(e.target.value)}
                            maxLength={GRUND_MAX_LAENGE}
                            rows={3}
                            placeholder="z. B. Löschwunsch per E-Mail vom 20.10.2026"
                            className="mt-1 w-full resize-none rounded-lg border border-slate-200 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <div className="mt-1 flex justify-between gap-2 text-xs text-slate-500">
                            <p>Bitte keinen Namen und keine Adresse eintragen – der Grund wird mit protokolliert.</p>
                            <p className="shrink-0">{grund.length}/{GRUND_MAX_LAENGE} Zeichen</p>
                        </div>
                    </div>
                </div>

                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={schliessen} disabled={laeuft}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={sperren}
                        disabled={laeuft}
                        className="border-red-600 bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500"
                    >
                        <Lock className="h-4 w-4" />
                        {laeuft ? 'Wird gelöscht…' : 'Daten löschen und sperren'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

// ==================== KNOPF „LÖSCHEN…“ ====================

interface KundeLoeschenKnopfProps {
    kunde: KundeDetail;
    /** Kunde wurde endgültig gelöscht. */
    onGeloescht: () => void;
    /** Kunde wurde gesperrt; die Antwort ist die aktualisierte Akte. */
    onGesperrt: (kunde: KundeDetail) => void;
}

/**
 * „Löschen…“ in der Kopfkarte der Kundenakte (nur für Admins einbinden).
 * Ohne aufzubewahrende Unterlagen wird nach Rückfrage gelöscht, sonst öffnet
 * sich der Datenschutz-Dialog, der den Kunden sperrt.
 */
export const KundeLoeschenKnopf: React.FC<KundeLoeschenKnopfProps> = ({ kunde, onGeloescht, onGesperrt }) => {
    const toast = useToast();
    const confirm = useConfirm();
    const [dialogOffen, setDialogOffen] = useState(false);
    const [laeuft, setLaeuft] = useState(false);
    const pruefung = kunde.loeschPruefung;

    if (!pruefung || kunde.gesperrt) return null;

    const loeschen = async () => {
        const ok = await confirm({
            title: 'Kunde endgültig löschen?',
            message: `${kunde.kundennummer} · ${kunde.name}\n\nNotizen, E-Mail-Adressen und Telefonnummern werden mitgelöscht. Das lässt sich nicht rückgängig machen.`,
            confirmLabel: 'Endgültig löschen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!ok) return;
        setLaeuft(true);
        try {
            const res = await fetch(`/api/kunden/${kunde.id}`, { method: 'DELETE' });
            if (!res.ok) {
                toast.error(await fehlermeldung(res, 'Der Kunde konnte nicht gelöscht werden.'));
                return;
            }
            toast.success('Kunde wurde gelöscht.');
            onGeloescht();
        } catch {
            toast.error('Der Kunde konnte nicht gelöscht werden. Bitte die Verbindung prüfen.');
        } finally {
            setLaeuft(false);
        }
    };

    return (
        <>
            <Button
                variant="outline"
                onClick={() => (pruefung.loeschbar ? void loeschen() : setDialogOffen(true))}
                disabled={laeuft}
                className="border-red-200 text-red-700 hover:bg-red-50 focus-visible:ring-red-500"
            >
                <Trash2 className="h-4 w-4" /> {laeuft ? 'Wird gelöscht…' : 'Löschen…'}
            </Button>
            {!pruefung.loeschbar && (
                <KundeDatenschutzDialog
                    kunde={kunde}
                    open={dialogOffen}
                    onClose={() => setDialogOffen(false)}
                    onGesperrt={aktualisiert => {
                        setDialogOffen(false);
                        onGesperrt(aktualisiert);
                    }}
                />
            )}
        </>
    );
};
