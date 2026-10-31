import { useCallback, useId, useState } from 'react';
import type { Editor } from '@tiptap/core';
import { Save, Trash2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { useConfirm } from '../ui/confirm-dialog';
import { useToast } from '../ui/toast';
import { TiptapEditor } from '../TiptapEditor';
import { DokumentartSchalter } from './DokumentartSchalter';
import { PlatzhalterLeiste } from './PlatzhalterLeiste';
import { chipDarstellung, chipHtmlZuKuerzel, kuerzelZuChipHtml } from './platzhalter';
import { PlatzhalterChip, fuegePlatzhalterEin } from './platzhalterChipExtension';
import { fehlerMeldung, TextbausteinFehler } from './textbausteinApi';
import { NAME_MAX_LAENGE, hatAenderungen, speichernDaten } from './textbausteinEntwurf';
import type { DokumenttypGruppe, Platzhalter, TextbausteinDto, TextbausteinEntwurf, TextbausteinSpeichernDaten } from './typen';

/** Feste Liste: TiptapEditor wertet Erweiterungen nur beim Erzeugen aus. */
const EDITOR_ERWEITERUNGEN = [PlatzhalterChip];

interface TextbausteinBearbeitenDialogProps {
    entwurf: TextbausteinEntwurf;
    katalog: readonly Platzhalter[];
    gruppen: readonly DokumenttypGruppe[];
    onSpeichern: (id: number | null, daten: TextbausteinSpeichernDaten) => Promise<TextbausteinDto>;
    onLoeschen: (id: number) => Promise<boolean>;
    onSchliessen: () => void;
}

/**
 * Großer Dialog zum Anlegen und Bearbeiten: links Name, „Verwendet in“ und
 * Text (Platzhalter als Chips mit Beispiel), rechts die Platzhalter-Leiste.
 * Schließen mit ungespeicherten Änderungen fragt nach.
 */
export function TextbausteinBearbeitenDialog({ entwurf, katalog, gruppen, onSpeichern, onLoeschen, onSchliessen }: TextbausteinBearbeitenDialogProps) {
    const confirm = useConfirm();
    const toast = useToast();
    const titelId = useId();
    const nameFehlerId = useId();
    // Startwert einmal berechnen; der Editor arbeitet danach mit seinem eigenen HTML weiter.
    const [startChipHtml] = useState(() => kuerzelZuChipHtml(entwurf.html, katalog));
    const [name, setName] = useState(entwurf.name);
    const [dokumenttypen, setDokumenttypen] = useState(entwurf.dokumenttypen);
    const [chipHtml, setChipHtml] = useState(startChipHtml);
    const [nameFehler, setNameFehler] = useState('');
    const [aktion, setAktion] = useState<'speichern' | 'loeschen' | null>(null);
    const [editor, setEditor] = useState<Editor | null>(null);
    const istNeu = entwurf.id === null;
    const beschaeftigt = aktion !== null;

    // Vergleichsstand für „ungespeicherte Änderungen“: sobald der Editor bereit ist, sein
    // normalisiertes HTML – sonst zählte schon Tiptaps Umformatierung (Tippen + Rückgängig) als Änderung.
    const [vergleichsHtml, setVergleichsHtml] = useState(() => chipHtmlZuKuerzel(startChipHtml));
    const editorBereit = useCallback((bereit: Editor | null) => {
        setEditor(bereit);
        if (!bereit) return;
        // Beide Seiten vom selben, von Tiptap normalisierten Stand: sonst fragte schon
        // Öffnen + Schließen nach, wenn Tiptap das gespeicherte HTML umformt.
        const normalisiert = bereit.getHTML();
        setChipHtml(normalisiert);
        setVergleichsHtml(chipHtmlZuKuerzel(normalisiert));
    }, []);

    const schliessen = async () => {
        if (beschaeftigt) return;
        if (hatAenderungen(entwurf, { name, dokumenttypen, chipHtml }, vergleichsHtml)) {
            const verwerfen = await confirm({
                title: 'Änderungen verwerfen?',
                message: 'Ihre Änderungen an diesem Textbaustein sind noch nicht gespeichert.',
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter bearbeiten',
                variant: 'warning',
            });
            if (!verwerfen) return;
        }
        onSchliessen();
    };

    const speichern = async () => {
        if (!name.trim()) {
            setNameFehler('Bitte geben Sie einen Namen ein.');
            toast.warning('Bitte geben Sie dem Textbaustein einen Namen.');
            return;
        }
        setAktion('speichern');
        try {
            await onSpeichern(entwurf.id, speichernDaten(entwurf, { name, dokumenttypen, chipHtml }));
            onSchliessen();
        } catch (fehler) {
            if (fehler instanceof TextbausteinFehler && fehler.felder.includes('name')) setNameFehler(fehler.message);
            toast.error(fehlerMeldung(fehler, 'Der Textbaustein konnte nicht gespeichert werden.'));
            setAktion(null);
        }
    };

    const loeschen = async () => {
        if (entwurf.id === null) return;
        // Die Datenbank entfernt den Textbaustein auch aus den Standard-Texten der Formularvorlagen
        // (ON DELETE CASCADE); erstellte Dokumente speichern ihren Text selbst.
        const bestaetigt = await confirm({
            title: 'Textbaustein löschen?',
            message: `„${entwurf.name || 'Ohne Namen'}“ wird endgültig gelöscht. Ist er in einer Formularvorlage als Standard-Text hinterlegt, fällt er dort ebenfalls weg. Bereits erstellte Dokumente behalten ihren Text.`,
            confirmLabel: 'Löschen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!bestaetigt) return;
        setAktion('loeschen');
        if (await onLoeschen(entwurf.id)) onSchliessen();
        else setAktion(null);
    };

    const einfuegen = (platzhalter: Platzhalter) => {
        if (editor) fuegePlatzhalterEin(editor, chipDarstellung(katalog, platzhalter.token));
    };

    return (
        <Dialog
            open
            onOpenChange={(offen) => { if (!offen) void schliessen(); }}
            aria-labelledby={titelId}
            className="h-[calc(100vh-2rem)] max-h-[920px] w-full max-w-6xl p-0"
        >
            <DialogContent className="gap-0">
                <DialogHeader className="border-b border-slate-200 px-6 py-4 pr-16">
                    <DialogTitle id={titelId} className="text-xl text-slate-900">{istNeu ? 'Neuer Textbaustein' : 'Textbaustein bearbeiten'}</DialogTitle>
                    <DialogDescription>Schreiben Sie den Text wie im fertigen Dokument. Platzhalter fügen Sie rechts per Klick ein.</DialogDescription>
                </DialogHeader>
                <div className="flex min-h-0 flex-1">
                    <div className="min-w-0 flex-1 space-y-6 overflow-y-auto px-6 py-5">
                        <div className="max-w-xl">
                            <Label htmlFor="textbaustein-name">Name <span className="text-rose-600" aria-hidden="true">*</span></Label>
                            <Input
                                id="textbaustein-name"
                                value={name}
                                maxLength={NAME_MAX_LAENGE}
                                required
                                aria-invalid={nameFehler ? true : undefined}
                                aria-describedby={nameFehler ? nameFehlerId : undefined}
                                onChange={(event) => { setName(event.target.value); setNameFehler(''); }}
                                placeholder="z. B. Anschreiben Angebot"
                                className={nameFehler ? 'border-red-400' : undefined}
                            />
                            {nameFehler && <p id={nameFehlerId} className="mt-1 text-sm text-red-600">{nameFehler}</p>}
                        </div>
                        {/* Mindesthöhe ca. 10 Zeilen: der Text ist die Hauptsache und steht deshalb vor „Verwendet in“. */}
                        <div className="[&_.tiptap-editor-content]:min-h-[15rem]">
                            <p className="mb-1.5 text-sm font-semibold text-slate-800">Text</p>
                            <TiptapEditor
                                value={chipHtml}
                                onChange={(html) => setChipHtml(html)}
                                onEditorReady={editorBereit}
                                zusatzErweiterungen={EDITOR_ERWEITERUNGEN}
                            />
                        </div>
                        <DokumentartSchalter gruppen={gruppen} ausgewaehlt={dokumenttypen} onChange={setDokumenttypen} />
                    </div>
                    <aside className="flex min-h-0 w-72 shrink-0 flex-col border-l border-slate-200 bg-slate-50 px-4 pb-0 pt-5">
                        <PlatzhalterLeiste katalog={katalog} onEinfuegen={einfuegen} gesperrt={!editor} />
                    </aside>
                </div>
                <DialogFooter className="gap-2 border-t border-slate-200 px-6 py-4 sm:justify-between sm:space-x-0">
                    <div>
                        {!istNeu && (
                            <Button variant="ghost" size="sm" onClick={loeschen} disabled={beschaeftigt} className="text-red-600 hover:bg-red-50 hover:text-red-700">
                                <Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen
                            </Button>
                        )}
                    </div>
                    <div className="flex flex-col-reverse gap-2 sm:flex-row">
                        <Button variant="outline" size="sm" onClick={() => void schliessen()} disabled={beschaeftigt}>Abbrechen</Button>
                        <Button size="sm" onClick={speichern} disabled={beschaeftigt}>
                            <Save className="h-4 w-4" aria-hidden="true" /> {aktion === 'speichern' ? 'Wird gespeichert …' : 'Speichern'}
                        </Button>
                    </div>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
