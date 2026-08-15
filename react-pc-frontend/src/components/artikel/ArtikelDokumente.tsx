import { useCallback, useEffect, useRef, useState } from "react";
import { FileText, ImageIcon, Loader2, Paperclip, Trash2, Upload } from "lucide-react";
import { Button } from "../ui/button";
import { Label } from "../ui/label";
import { Select } from "../ui/select-custom";
import { ImageViewer } from "../ui/image-viewer";
import { ThumbnailImage } from "../ui/ThumbnailImage";
import DocumentPreviewModal, { type PreviewDoc } from "../DocumentPreviewModal";
import { useToast } from "../ui/toast";
import { useConfirm } from "../ui/confirm-dialog";
import { ARTIKEL_DOKUMENT_TYPEN, type ArtikelDokument, type ArtikelDokumentTyp } from "../../types";

interface ArtikelDokumenteProps {
    artikelId: number;
}

const BILD_ENDUNGEN = new Set(["png", "jpg", "jpeg", "webp", "gif"]);

/** Erkennt anhand des Originalnamens, ob eine Datei ein Bild ist - fuer die Wahl ImageViewer vs. DocumentPreviewModal. */
function istBildDatei(dateiname: string): boolean {
    const endung = dateiname.split(".").pop()?.toLowerCase() ?? "";
    return BILD_ENDUNGEN.has(endung);
}

function formatDatum(iso?: string): string {
    return iso ? new Date(iso).toLocaleDateString("de-DE") : "—";
}

function typLabel(typ: ArtikelDokumentTyp): string {
    return ARTIKEL_DOKUMENT_TYPEN.find((t) => t.value === typ)?.label ?? typ;
}

/**
 * Liest die Fehlermeldung aus dem Antwortkoerper ({"message": "..."}), den der
 * Server bei 400 in Alltagssprache liefert ("Die Datei ist zu gross...", "Dieser
 * Dateityp wird nicht unterstuetzt..."). Diese Meldung wird 1:1 angezeigt - kein
 * eigener Text, kein Statuscode. Faellt auf einen generischen Text zurueck, falls
 * der Koerper (z.B. bei einem 500er) kein JSON oder kein message-Feld enthaelt.
 */
async function leseFehlermeldung(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json();
        return typeof body?.message === "string" && body.message ? body.message : fallback;
    } catch {
        return fallback;
    }
}

/**
 * Abschnitt "Bilder & Unterlagen" auf der Artikel-Detailseite.
 *
 * Zeigt das Vorschaubild gross (Klick oeffnet ImageViewer) und daneben die
 * Zusatzunterlagen (Zulassung, Zeichnung, Datenblatt, Montageanleitung,
 * Sonstiges) als Liste; Klick auf eine PDF-Unterlage oeffnet DocumentPreviewModal.
 * Fuer Zukaufteile wie Handlaufhalter oder Glasklemmen zeigt das Bild, welches
 * Teil gemeint ist - Zulassung und Zeichnung braucht der Monteur beim Einbau.
 */
export function ArtikelDokumente({ artikelId }: ArtikelDokumenteProps) {
    const toast = useToast();
    const confirm = useConfirm();

    const [dokumente, setDokumente] = useState<ArtikelDokument[]>([]);
    const [loading, setLoading] = useState(true);
    const [bildLaedt, setBildLaedt] = useState(false);
    const [dokumentLaedt, setDokumentLaedt] = useState(false);
    const [loeschtId, setLoeschtId] = useState<number | null>(null);

    const [neuerTyp, setNeuerTyp] = useState<ArtikelDokumentTyp>("ZULASSUNG");
    const [gewaehlteDatei, setGewaehlteDatei] = useState<File | null>(null);

    const [vollbild, setVollbild] = useState<{ url: string; name: string } | null>(null);
    const [pdfVorschau, setPdfVorschau] = useState<PreviewDoc | null>(null);

    const bildInputRef = useRef<HTMLInputElement>(null);
    const dokumentInputRef = useRef<HTMLInputElement>(null);

    const laden = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(artikelId)}/dokumente`);
            if (!res.ok) throw new Error("Laden fehlgeschlagen");
            const daten = await res.json();
            setDokumente(Array.isArray(daten) ? daten : []);
        } catch (err) {
            console.error(err);
            toast.error("Die Bilder und Unterlagen konnten nicht geladen werden.");
        } finally {
            setLoading(false);
        }
    }, [artikelId, toast]);

    useEffect(() => { laden(); }, [laden]);

    const vorschaubild = dokumente.find((d) => d.typ === "VORSCHAUBILD");
    const unterlagen = dokumente.filter((d) => d.typ !== "VORSCHAUBILD");

    const hochladen = async (datei: File, typ: ArtikelDokumentTyp) => {
        const formData = new FormData();
        formData.append("datei", datei);
        formData.append("typ", typ);

        const res = await fetch(`/api/artikel/${encodeURIComponent(artikelId)}/dokumente`, {
            method: "POST",
            body: formData,
        });
        if (!res.ok) {
            throw new Error(await leseFehlermeldung(res, "Die Datei konnte nicht hochgeladen werden."));
        }
        await laden();
    };

    const bildAuswaehlen = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const datei = e.target.files?.[0];
        e.target.value = "";
        if (!datei) return;
        setBildLaedt(true);
        try {
            // Ein neues Vorschaubild ersetzt serverseitig ein vorhandenes - das ist
            // kein Fehlerfall, sondern das erwartete Verhalten (siehe Backend-Doku).
            await hochladen(datei, "VORSCHAUBILD");
            toast.success("Das Bild ist gespeichert.");
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Das Bild konnte nicht hochgeladen werden.");
        } finally {
            setBildLaedt(false);
        }
    };

    const dokumentAuswaehlen = (e: React.ChangeEvent<HTMLInputElement>) => {
        const datei = e.target.files?.[0];
        e.target.value = "";
        if (datei) setGewaehlteDatei(datei);
    };

    const dokumentHochladen = async () => {
        if (!gewaehlteDatei) {
            toast.error("Bitte zuerst eine Datei auswählen.");
            return;
        }
        setDokumentLaedt(true);
        try {
            await hochladen(gewaehlteDatei, neuerTyp);
            toast.success("Die Unterlage ist gespeichert.");
            setGewaehlteDatei(null);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Die Unterlage konnte nicht hochgeladen werden.");
        } finally {
            setDokumentLaedt(false);
        }
    };

    const loeschen = async (dok: ArtikelDokument) => {
        const bestaetigt = await confirm({
            title: "Datei löschen?",
            message: `„${dok.originalDateiname}" wirklich löschen? Das kann nicht rückgängig gemacht werden.`,
            confirmLabel: "Ja, löschen",
            cancelLabel: "Abbrechen",
            variant: "danger",
        });
        if (!bestaetigt) return;

        setLoeschtId(dok.id);
        try {
            const res = await fetch(`/api/artikel/dokumente/${encodeURIComponent(dok.id)}`, { method: "DELETE" });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, "Die Datei konnte nicht gelöscht werden."));
            setDokumente((prev) => prev.filter((d) => d.id !== dok.id));
            toast.success("Die Datei ist gelöscht.");
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Die Datei konnte nicht gelöscht werden.");
        } finally {
            setLoeschtId(null);
        }
    };

    const oeffnen = (dok: ArtikelDokument) => {
        if (istBildDatei(dok.originalDateiname)) {
            setVollbild({ url: dok.url, name: dok.originalDateiname });
        } else {
            // Alles ausser Bildern ist laut Backend-Whitelist ein PDF - isPdf also
            // fest true statt der URL-Heuristik ueberlassen, die bei einem
            // erweiterungslosen API-Pfad wie /dokumente/{id}/datei ohnehin nicht traegt.
            setPdfVorschau({ url: dok.url, title: dok.originalDateiname });
        }
    };

    return (
        <section className="bg-white border border-slate-200 rounded-lg p-5">
            <h2 className="text-lg font-semibold text-slate-900 mb-1">Bilder &amp; Unterlagen</h2>
            <p className="text-sm text-slate-500 mb-4">
                Zeigt, welches Teil gemeint ist, und liefert Zulassung, Zeichnung oder Anleitung für den Einbau.
            </p>

            {loading ? (
                <div className="animate-pulse space-y-3" aria-busy="true" aria-label="Bilder und Unterlagen werden geladen">
                    <div className="h-40 bg-slate-100 rounded-lg" />
                    <div className="h-10 bg-slate-100 rounded-lg" />
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6">
                    {/* ---- Vorschaubild ---- */}
                    <div>
                        {vorschaubild ? (
                            <button
                                type="button"
                                onClick={() => setVollbild({ url: vorschaubild.url, name: vorschaubild.originalDateiname })}
                                className="block w-full aspect-square rounded-lg border border-slate-200 overflow-hidden bg-slate-50 hover:opacity-90 transition-opacity"
                                title="Bild groß anzeigen"
                            >
                                <ThumbnailImage
                                    src={vorschaubild.url}
                                    alt={`Vorschaubild: ${vorschaubild.originalDateiname}`}
                                    className="object-contain"
                                />
                            </button>
                        ) : (
                            <div className="flex flex-col items-center justify-center gap-2 w-full aspect-square rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 text-center px-3">
                                <ImageIcon className="w-8 h-8 text-slate-300" aria-hidden="true" />
                                <p className="text-xs text-slate-500">Noch kein Bild hinterlegt</p>
                            </div>
                        )}
                        <Button
                            variant="outline"
                            size="sm"
                            className="w-full mt-2 border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={() => bildInputRef.current?.click()}
                            disabled={bildLaedt}
                        >
                            {bildLaedt
                                ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                : <Upload className="w-4 h-4" aria-hidden="true" />}
                            {vorschaubild ? "Bild ersetzen" : "Bild hochladen"}
                        </Button>
                        <input
                            ref={bildInputRef}
                            type="file"
                            className="hidden"
                            aria-label={vorschaubild ? "Bild ersetzen" : "Bild hochladen"}
                            accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                            onChange={bildAuswaehlen}
                        />
                    </div>

                    {/* ---- Zusatzunterlagen ---- */}
                    <div>
                        {unterlagen.length === 0 ? (
                            <div className="flex flex-col items-center justify-center gap-2 py-8 mb-4 text-center border border-dashed border-slate-200 rounded-lg bg-slate-50">
                                <Paperclip className="w-6 h-6 text-slate-300" aria-hidden="true" />
                                <p className="text-sm text-slate-500">
                                    Noch keine Zulassung, Zeichnung oder Anleitung hinterlegt.
                                </p>
                            </div>
                        ) : (
                            <ul className="divide-y divide-slate-100 border border-slate-200 rounded-lg mb-4">
                                {unterlagen.map((dok) => (
                                    <li key={dok.id} className="flex items-center gap-3 px-3 py-2.5">
                                        <button
                                            type="button"
                                            onClick={() => oeffnen(dok)}
                                            className="flex items-center gap-3 flex-1 min-w-0 text-left hover:text-rose-700"
                                        >
                                            <FileText className="w-4 h-4 text-slate-400 shrink-0" aria-hidden="true" />
                                            <span className="shrink-0 text-xs font-medium text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                                                {typLabel(dok.typ)}
                                            </span>
                                            <span className="truncate text-sm text-slate-900">{dok.originalDateiname}</span>
                                            <span className="shrink-0 text-xs text-slate-400 ml-auto pr-2">
                                                {formatDatum(dok.erstelltAm)}
                                            </span>
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => loeschen(dok)}
                                            disabled={loeschtId === dok.id}
                                            title="Löschen"
                                            aria-label={`${dok.originalDateiname} löschen`}
                                            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded shrink-0 disabled:opacity-50"
                                        >
                                            {loeschtId === dok.id
                                                ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                                : <Trash2 className="w-4 h-4" aria-hidden="true" />}
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        )}

                        {/* ---- Neue Unterlage hochladen ---- */}
                        <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-end">
                            <div className="w-full sm:w-44">
                                <Label>Art der Unterlage</Label>
                                <Select
                                    value={neuerTyp}
                                    onChange={(v) => setNeuerTyp(v as ArtikelDokumentTyp)}
                                    options={ARTIKEL_DOKUMENT_TYPEN}
                                />
                            </div>
                            <div className="flex-1 w-full">
                                <Label>Datei</Label>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    className="w-full justify-start min-h-[40px] border-slate-300 text-slate-700 hover:bg-slate-50"
                                    onClick={() => dokumentInputRef.current?.click()}
                                >
                                    <Paperclip className="w-4 h-4" aria-hidden="true" />
                                    <span className="truncate">{gewaehlteDatei ? gewaehlteDatei.name : "Datei auswählen"}</span>
                                </Button>
                                <input
                                    ref={dokumentInputRef}
                                    type="file"
                                    className="hidden"
                                    aria-label="Datei auswählen"
                                    accept=".pdf,.png,.jpg,.jpeg,.webp,.gif,application/pdf,image/png,image/jpeg,image/webp,image/gif"
                                    onChange={dokumentAuswaehlen}
                                />
                            </div>
                            <Button
                                size="sm"
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 shrink-0"
                                onClick={dokumentHochladen}
                                disabled={dokumentLaedt || !gewaehlteDatei}
                            >
                                {dokumentLaedt
                                    ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                    : <Upload className="w-4 h-4" aria-hidden="true" />}
                                Hochladen
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            {vollbild && (
                <ImageViewer src={vollbild.url} alt={vollbild.name} onClose={() => setVollbild(null)} />
            )}
            {pdfVorschau && (
                <DocumentPreviewModal doc={pdfVorschau} isPdf onClose={() => setPdfVorschau(null)} />
            )}
        </section>
    );
}
