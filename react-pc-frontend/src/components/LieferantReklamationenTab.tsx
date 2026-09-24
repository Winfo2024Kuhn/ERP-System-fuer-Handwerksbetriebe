/* eslint-disable react-refresh/only-export-components -- baueMailText ist reine Textlogik und wird separat getestet (siehe EmailComposeForm.tsx, gleiches Muster) */
import { useEffect, useState } from "react";
import { AlertTriangle, Calendar, FileText, User, CheckCircle, Trash, Plus, Mail, Loader2 } from "lucide-react";
import { Card } from "./ui/card";
import { Button } from "./ui/button";
import { ImageViewer } from "./ui/image-viewer";
import { CreateReklamationModal } from "./CreateReklamationModal";
import { EmailComposeModal } from "./EmailComposeModal";
import { MAX_ATTACHMENT_BYTES, formatFileSize } from "./EmailComposeForm";
import { escapeHtml } from "./emailContentFrameUtils";
import { waehleInfoEmpfaenger } from '../lib/emailAddress';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import type { LieferantReklamation } from '../types';
import { prependUniqueById } from '../lib/optimisticUploads';
import { komprimiereBilderFuerEmail } from '../lib/bildKomprimierung';

/**
 * Link auf die Datei eines Lieferanten-Dokuments.
 *
 * <p>Bewusst nicht `/api/dokumente/{name}`: Dieser Endpunkt sucht nur in den
 * Projekt- und Anfrage-Ordnern. Lieferanten-Dokumente liegen woanders (u. a.
 * unter `uploads/lieferanten/`) und werden über ihre ID ausgeliefert – sonst
 * antwortet der Server mit "Datei nicht gefunden".</p>
 */
export function lieferantDokumentUrl(lieferantId: number, dokumentId: number): string {
    return `/api/lieferanten/${lieferantId}/dokumente/${dokumentId}/download`;
}

/** Vorbereitete Reklamations-Mail, die im Compose-Formular geöffnet wird. */
interface ReklamationsMail {
    empfaenger: string;
    betreff: string;
    text: string;
    anhaenge: File[];
}

/**
 * Lädt eine Datei vom Server und verpackt sie als {@link File} für den E-Mail-Anhang.
 * @returns die Datei oder `null`, wenn sie nicht geladen werden konnte
 */
async function ladeAlsAnhang(url: string, dateiname: string): Promise<File | null> {
    try {
        const response = await fetch(url);
        if (!response.ok) return null;
        const blob = await response.blob();
        return new File([blob], dateiname, { type: blob.type || 'application/octet-stream' });
    } catch (err) {
        console.error(`Anhang "${dateiname}" konnte nicht geladen werden:`, err);
        return null;
    }
}

/**
 * Baut Betreff und vorformulierten Text der Reklamations-Mail.
 *
 * @param lieferscheinAngehaengt ob der Lieferschein tatsächlich als Anhang dranhängt.
 *        Konnte er nicht geladen werden, darf der Text ihn auch nicht ankündigen –
 *        sonst sucht der Lieferant nach einem Anhang, den es nicht gibt.
 * @param bilderAngehaengt Anzahl der tatsächlich angehängten Fotos
 */
export function baueMailText(
    rek: LieferantReklamation,
    lieferscheinAngehaengt: boolean,
    bilderAngehaengt: number,
): { betreff: string; text: string } {
    const lieferschein = rek.lieferscheinNummer || rek.lieferscheinDateiname;
    const datum = new Date(rek.erstelltAm).toLocaleDateString('de-DE');

    const betreff = lieferschein
        ? `Reklamation zu Lieferschein ${lieferschein}`
        : `Reklamation vom ${datum}`;

    const fotoHinweis = bilderAngehaengt === 1
        ? 'unser Foto dazu hängt an dieser E-Mail an'
        : `unsere ${bilderAngehaengt} Fotos dazu hängen an dieser E-Mail an`;

    let lieferscheinZeile = '';
    if (lieferschein && lieferscheinAngehaengt) {
        lieferscheinZeile = bilderAngehaengt > 0
            ? `<p>Es geht um den Lieferschein <strong>${escapeHtml(lieferschein)}</strong> – er hängt zusammen mit den Fotos an dieser E-Mail an.</p>`
            : `<p>Es geht um den Lieferschein <strong>${escapeHtml(lieferschein)}</strong>. Er hängt an dieser E-Mail an.</p>`;
    } else if (lieferschein) {
        lieferscheinZeile = bilderAngehaengt > 0
            ? `<p>Es geht um den Lieferschein <strong>${escapeHtml(lieferschein)}</strong>, ${fotoHinweis}.</p>`
            : `<p>Es geht um den Lieferschein <strong>${escapeHtml(lieferschein)}</strong>.</p>`;
    } else if (bilderAngehaengt > 0) {
        lieferscheinZeile = `<p>Dazu ${bilderAngehaengt === 1 ? 'haben wir ein Foto angehängt' : `haben wir ${bilderAngehaengt} Fotos angehängt`}.</p>`;
    }

    // Die vor Ort erfasste Beschreibung als Startpunkt einsetzen – ergänzt wird
    // sie direkt im Compose-Formular, dort steht der Cursor bereits im Text.
    const beschreibung = (rek.beschreibung || '').trim();
    const beschreibungsBlock = beschreibung
        ? `<p><strong>Das ist uns aufgefallen:</strong><br>${escapeHtml(beschreibung).replace(/\n/g, '<br>')}</p>`
        : '';

    const text = [
        `<p>Guten Tag,</p>`,
        `<p>bei einer Lieferung von Ihnen gibt es leider etwas zu beanstanden.</p>`,
        beschreibungsBlock,
        lieferscheinZeile,
        `<p><strong>Bitte hier noch ergänzen, woran es genau liegt:</strong><br>&nbsp;</p>`,
        `<p>Bitte sagen Sie uns kurz Bescheid, wie wir das regeln.</p>`,
        `<p>Vielen Dank und viele Grüße</p>`,
    ].filter(Boolean).join('');

    return { betreff, text };
}

interface LieferantReklamationenTabProps {
    lieferantId: number;
    /** Name des Lieferanten – nur für die Anzeige im Mail-Dialog. */
    lieferantName?: string;
    /** Hinterlegte Adressen. Die Mail geht an `info@`, sonst an die erste davon. */
    lieferantEmails?: string[];
}

export function LieferantReklamationenTab({ lieferantId, lieferantName, lieferantEmails }: LieferantReklamationenTabProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [reklamationen, setReklamationen] = useState<LieferantReklamation[]>([]);
    const [loading, setLoading] = useState(true);

    // Image Viewer State
    const [reklamationBildViewer, setReklamationBildViewer] = useState<{ images: { url: string; name?: string }[]; startIndex: number } | null>(null);

    // Create Modal State
    const [createModalOpen, setCreateModalOpen] = useState(false);

    // E-Mail-Versand: vorbereitete Mail und die Reklamation, deren Anhänge gerade laden
    const [reklamationsMail, setReklamationsMail] = useState<ReklamationsMail | null>(null);
    const [mailWirdVorbereitetId, setMailWirdVorbereitetId] = useState<number | null>(null);

    const loadData = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/reklamationen/lieferant/${lieferantId}`);
            if (res.ok) {
                const data = await res.json();
                setReklamationen(data);
            }
        } catch (err) {
            console.error(err);
        }
        setLoading(false);
    };

    useEffect(() => {
        loadData();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [lieferantId]);

    const handleImageClick = (rek: LieferantReklamation, clickedIndex: number) => {
        const images = rek.bilder.map(b => ({ url: b.url, name: b.originalDateiname }));
        setReklamationBildViewer({ images, startIndex: clickedIndex });
    };

    /**
     * Bereitet die Reklamations-Mail vor: Alle Fotos und der verlinkte Lieferschein
     * werden geladen und als Anhänge ins Compose-Formular gehängt, dazu ein
     * vorformulierter Text. Empfänger ist die `info@`-Adresse des Lieferanten.
     *
     * <p>Angehängt werden die Fotos in voller Ansicht, nicht die briefmarkengroßen
     * Vorschauen: Der Lieferant soll den Mangel auf dem Bild auch erkennen können.
     * Vor dem Anhängen laufen sie durch {@link komprimiereBilderFuerEmail} – vier
     * Handyfotos im Original wären zusammen über 20 MB, und so viel nimmt unser
     * Mailserver gar nicht erst an.</p>
     */
    const handlePerEmailMelden = async (rek: LieferantReklamation) => {
        const empfaenger = waehleInfoEmpfaenger(lieferantEmails);
        if (!empfaenger) {
            toast.error("Für diesen Lieferanten ist keine E-Mail-Adresse hinterlegt.");
            return;
        }

        setMailWirdVorbereitetId(rek.id);
        try {
            const anhaenge: File[] = [];
            const fehlgeschlagen: string[] = [];

            // Herunterladen parallel (wartet nur auf das Netz), Verkleinern danach
            // nacheinander – siehe komprimiereBilderFuerEmail: gleichzeitig
            // dekodierte Fotos fressen den Arbeitsspeicher.
            const geladen = await Promise.all(
                (rek.bilder || []).map(bild => ladeAlsAnhang(bild.url, bild.originalDateiname))
            );
            geladen.forEach((datei, index) => {
                if (!datei) fehlgeschlagen.push(rek.bilder[index].originalDateiname);
            });
            const bilder = await komprimiereBilderFuerEmail(
                geladen.filter((datei): datei is File => datei !== null));
            anhaenge.push(...bilder);
            const bilderAngehaengt = anhaenge.length;

            // Gleiche Bedingung wie bei der Verlinkung in der Karte: Nur ein wirklich
            // verknüpfter Lieferschein wird geholt, sonst gäbe es einen Fehler-Toast
            // für einen Anhang, den die Oberfläche gar nicht anbietet.
            let lieferscheinAngehaengt = false;
            if (rek.lieferscheinId && rek.lieferscheinDateiname) {
                const name = rek.lieferscheinDateiname;
                const lieferschein = await ladeAlsAnhang(
                    lieferantDokumentUrl(lieferantId, rek.lieferscheinId), name);
                if (lieferschein) {
                    anhaenge.push(lieferschein);
                    lieferscheinAngehaengt = true;
                } else {
                    fehlgeschlagen.push(name);
                }
            }

            if (fehlgeschlagen.length > 0) {
                toast.error(`Nicht angehängt werden konnte: ${fehlgeschlagen.join(', ')}`);
            }

            // Der Versand würde sonst erst im letzten Moment scheitern. Die Anhänge
            // lassen sich im Formular einzeln entfernen, deshalb nur ein Hinweis.
            const gesamtGroesse = anhaenge.reduce((summe, datei) => summe + datei.size, 0);
            if (gesamtGroesse > MAX_ATTACHMENT_BYTES) {
                toast.error(
                    `Die Anhänge sind zusammen ${formatFileSize(gesamtGroesse)} groß. `
                    + `Erlaubt sind ${formatFileSize(MAX_ATTACHMENT_BYTES)} – bitte einzelne Bilder entfernen.`
                );
            }

            const { betreff, text } = baueMailText(rek, lieferscheinAngehaengt, bilderAngehaengt);
            setReklamationsMail({ empfaenger, betreff, text, anhaenge });
        } finally {
            setMailWirdVorbereitetId(null);
        }
    };

    const handleComplete = async (id: number) => {
        if (!await confirmDialog({ title: "Reklamation abschließen", message: "Möchten Sie diese Reklamation wirklich abschließen?", variant: "info", confirmLabel: "Abschließen" })) return;

        try {
            const res = await fetch(`/api/reklamationen/${id}/status?status=ABGESCHLOSSEN`, {
                method: 'PATCH'
            });

            if (res.ok) {
                loadData(); // Reload to update status and order
            } else {
                toast.error("Fehler beim Aktualisieren des Status.");
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    const handleDelete = async (id: number) => {
        if (!await confirmDialog({ title: "Reklamation löschen", message: "Möchten Sie diese Reklamation wirklich löschen? Dies kann nicht rückgängig gemacht werden.", variant: "danger", confirmLabel: "Löschen" })) return;

        try {
            const res = await fetch(`/api/reklamationen/${id}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                loadData();
            } else {
                toast.error("Fehler beim Löschen der Reklamation.");
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    if (loading) {
        return <div className="p-8 text-center text-slate-500">Lade Reklamationen...</div>;
    }



    return (
        <div className="space-y-4 p-4">
            {/* Header / Create Button */}
            <div className="flex justify-between items-center bg-slate-50 p-4 rounded-lg border border-slate-200">
                <div>
                    <h3 className="text-lg font-medium text-slate-900">Reklamationsübersicht</h3>
                    <p className="text-sm text-slate-500">Verwalten Sie hier alle offenen und abgeschlossenen Reklamationen.</p>
                </div>
                <Button onClick={() => setCreateModalOpen(true)} className="bg-rose-600 hover:bg-rose-700">
                    <Plus className="w-4 h-4 mr-2" />
                    Neue Reklamation
                </Button>
            </div>

            {reklamationen.length === 0 ? (
                <div className="p-12 text-center text-slate-500 border-2 border-dashed border-slate-200 rounded-xl bg-slate-50">
                    <AlertTriangle className="w-12 h-12 mx-auto mb-4 text-slate-300" />
                    <h3 className="text-lg font-medium text-slate-900 mb-2">Keine Reklamationen</h3>
                    <p>Für diesen Lieferanten liegen keine Reklamationen vor.</p>
                </div>
            ) : (
                reklamationen.map(rek => (
                    <Card key={rek.id} className="p-6 border-slate-200 relative overflow-hidden group">
                        {/* Status Stripe */}
                        <div className={`absolute left-0 top-0 bottom-0 w-1 ${rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-300' : 'bg-rose-500'}`} />

                        {/* Delete Button - Top Right */}
                        <div className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity">
                            <Button
                                variant="ghost"
                                size="sm"
                                className="text-slate-400 hover:text-red-600 hover:bg-red-50 h-8 w-8 p-0 rounded-full"
                                onClick={() => handleDelete(rek.id)}
                                title="Reklamation löschen"
                            >
                                <Trash className="w-4 h-4" />
                            </Button>
                        </div>

                        <div className="flex flex-col md:flex-row gap-6">
                            {/* Left: Info */}
                            <div className="flex-1 space-y-4 pl-2">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <div className="flex items-center gap-3 mb-1">
                                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${rek.status === 'OFFEN' ? 'bg-red-100 text-red-700' :
                                                rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-100 text-slate-600' :
                                                    'bg-blue-100 text-blue-700'
                                                }`}>
                                                {rek.status}
                                            </span>
                                            <span className="text-sm text-slate-500 flex items-center gap-1">
                                                <Calendar className="w-3 h-3" />
                                                {new Date(rek.erstelltAm).toLocaleDateString()} {new Date(rek.erstelltAm).toLocaleTimeString()}
                                            </span>
                                        </div>
                                        <h3 className="font-semibold text-slate-900 text-lg">
                                            Reklamation #{rek.id}
                                        </h3>
                                    </div>

                                    <div className="flex items-center gap-2 mr-8">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handlePerEmailMelden(rek)}
                                            disabled={mailWirdVorbereitetId === rek.id}
                                            className="border-rose-300 text-rose-700 hover:bg-rose-50 cursor-pointer"
                                            title="Fotos und Lieferschein als E-Mail an den Lieferanten schicken"
                                        >
                                            {mailWirdVorbereitetId === rek.id ? (
                                                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                            ) : (
                                                <Mail className="w-4 h-4 mr-2" />
                                            )}
                                            {mailWirdVorbereitetId === rek.id ? 'Wird vorbereitet…' : 'Per E-Mail melden'}
                                        </Button>

                                        {rek.status !== 'ABGESCHLOSSEN' && (
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                onClick={() => handleComplete(rek.id)}
                                                className="text-slate-600 hover:text-green-600 hover:bg-green-50 border-slate-200 hover:border-green-200 cursor-pointer"
                                            >
                                                <CheckCircle className="w-4 h-4 mr-2" />
                                                Abschließen
                                            </Button>
                                        )}
                                    </div>
                                </div>

                                <p className="text-slate-700 whitespace-pre-wrap bg-slate-50 p-3 rounded-lg border border-slate-100">
                                    {rek.beschreibung}
                                </p>

                                <div className="flex flex-wrap gap-4 text-sm text-slate-600">
                                    <div className="flex items-center gap-1.5">
                                        <User className="w-4 h-4 text-slate-400" />
                                        Erstellt von: <span className="font-medium text-slate-900">{rek.erstellerName}</span>
                                    </div>
                                    {(rek.lieferscheinNummer || rek.lieferscheinDateiname) && (
                                        <div className="flex items-center gap-1.5">
                                            <FileText className="w-4 h-4 text-slate-400" />
                                            Lieferschein:
                                            {rek.lieferscheinId ? (
                                                <a
                                                    href={lieferantDokumentUrl(lieferantId, rek.lieferscheinId)}
                                                    target="_blank"
                                                    rel="noreferrer"
                                                    className="font-medium text-rose-600 hover:underline"
                                                >
                                                    {rek.lieferscheinNummer || rek.lieferscheinDateiname}
                                                </a>
                                            ) : (
                                                <span className="font-medium text-slate-900">{rek.lieferscheinNummer || rek.lieferscheinDateiname}</span>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Right: Images */}
                            {rek.bilder && rek.bilder.length > 0 && (
                                <div className="w-full md:w-1/3">
                                    <h4 className="text-sm font-medium text-slate-900 mb-3">Bilder ({rek.bilder.length})</h4>
                                    <div className="grid grid-cols-2 gap-2">
                                        {rek.bilder.map((img, idx) => (
                                            <div
                                                key={img.id}
                                                onClick={() => handleImageClick(rek, idx)}
                                                className="block relative aspect-square rounded-lg overflow-hidden border border-slate-200 hover:border-rose-300 transition-colors group cursor-pointer"
                                            >
                                                {/* Kleine Vorschau statt Originalfoto – Handybilder sind
                                                    mehrere MB groß, hier aber nur briefmarkengroß zu sehen.
                                                    In der Großansicht kommt weiter das Original. */}
                                                <img
                                                    src={img.vorschauUrl || img.url}
                                                    alt={`Reklamationsbild ${idx + 1} von ${rek.bilder.length}: ${img.originalDateiname}`}
                                                    loading="lazy"
                                                    decoding="async"
                                                    className="w-full h-full object-cover"
                                                />
                                                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                                                    <div className="bg-white/90 rounded-full p-2 shadow-sm">
                                                        <FileText className="w-4 h-4 text-slate-700" />
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </Card>
                ))
            )}

            <ImageViewer
                src={reklamationBildViewer ? reklamationBildViewer.images[reklamationBildViewer.startIndex]?.url : null}
                onClose={() => setReklamationBildViewer(null)}
                alt="Reklamationsbild"
                images={reklamationBildViewer?.images}
                startIndex={reklamationBildViewer?.startIndex}
            />

            {reklamationsMail && (
                <EmailComposeModal
                    isOpen
                    onClose={() => setReklamationsMail(null)}
                    initialRecipient={reklamationsMail.empfaenger}
                    initialSubject={reklamationsMail.betreff}
                    initialBody={reklamationsMail.text}
                    initialAttachments={reklamationsMail.anhaenge}
                    onSuccess={() => {
                        setReklamationsMail(null);
                        toast.success(`Reklamation an ${lieferantName || 'den Lieferanten'} verschickt.`);
                    }}
                />
            )}

            <CreateReklamationModal
                isOpen={createModalOpen}
                onClose={() => setCreateModalOpen(false)}
                lieferantId={lieferantId}
                onSuccess={(reklamation) => {
                    setReklamationen(prev => prependUniqueById(prev, [reklamation]));
                    void loadData();
                }}
            />
        </div>
    );
}
