import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import DocumentEditor from '../components/DocumentEditor';
import type { DocumentEditorHandle } from '../components/document-editor/types';
import { KiHilfeChat } from '../components/KiHilfeChat';
import { useDatensatzLock } from '../components/lock/useDatensatzLock';
import { GesperrtHinweis } from '../components/lock/GesperrtHinweis';
import { BearbeitenLeiste } from '../components/lock/BearbeitenLeiste';
import { useIdleTimer } from '../hooks/useIdleTimer';
import { useToast } from '../components/ui/toast';
import type { AusgangsGeschaeftsDokumentTyp } from '../types';

// Wortlaut fuer Inline-Hinweis UND Toast identisch (siehe LieferantDokumentModal,
// dieselbe Formulierung), damit der Nutzer nicht zwei verschiedene Texte fuer
// denselben Fehler liest.
const LOCK_FEHLER_TEXT = 'Sperre konnte nicht geholt werden — bitte neu laden.';

/**
 * Page wrapper for DocumentEditor that reads projektId/anfrageId from URL params.
 * Opens as fullscreen page (no MainLayout sidebar).
 *
 * Soft-Lock (Issue #82, Abschnitt 7a): der Datensatz-Sperren-Baustein
 * useDatensatzLock loest das bisherige, hart blockierende
 * DocumentLockedModal ab. Ein bestehendes (gespeichertes) Dokument darf nach
 * wie vor nur EIN Nutzer gleichzeitig bearbeiten -- ein Kollege sieht den
 * aktuellen Stand jetzt aber weiterhin (Editor `readOnly`, `GesperrtHinweis`
 * + `BearbeitenLeiste`), statt komplett ausgesperrt zu werden. Neu erstellte
 * (noch nicht gespeicherte) Dokumente brauchen kein Lock -- der Editor laedt
 * sofort, editierbar, wie heute.
 */
export default function DocumentEditorPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const toast = useToast();

    const projektId = searchParams.get('projektId');
    const anfrageId = searchParams.get('anfrageId');
    const dokumentId = searchParams.get('dokumentId');
    const dokumentTyp = searchParams.get('dokumentTyp');

    const dokumentIdNum = parseDokumentId(dokumentId);
    const hatId = dokumentIdNum != null;

    const lock = useDatensatzLock('AUSGANG', dokumentIdNum);

    // --- Kein Flackern / kein Remount beim Anlegen eines neuen Dokuments ---
    //
    // Ein Dokument OHNE Id braucht kein Lock (siehe Klassenkommentar) -- der
    // Editor ist von Anfang an editierbar. Sobald der Editor beim ersten
    // Speichern die neu vergebene Id per Router in die URL schreibt
    // (setSearchParams, siehe document-editor/index.tsx), bekommt diese Seite
    // sie als Prop-Aenderung mit: `dokumentIdNum` wechselt von undefined zu
    // einer Zahl, useDatensatzLock erwirbt daraufhin das Lock (der Server
    // antwortet dem ERSTELLER garantiert mit ACQUIRED). Zwischen diesem
    // Id-Wechsel und der Server-Antwort liegt aber ein echter, wenn auch
    // kurzer Roundtrip: `lock.status` durchlaeuft 'idle'/'loading', bevor er
    // 'acquired' erreicht, und `lock.modus` haengt dem (er wechselt erst BEIM
    // erfolgreichen Erwerb auf 'bearbeiten') einen Tick hinterher.
    //
    // Ein readOnly, das sich strikt an `lock.modus` orientiert, wuerde in
    // genau diesem Fenster kurz auf `true` springen -- der Editor (der schon
    // die ganze Zeit editierbar war) wuerde fuer einen Moment schreibgeschuetzt
    // wirken, obwohl der Nutzer ununterbrochen weiterschreibt. Das ist exakt
    // das "Flackern", das die Spec verbietet. `kamOhneIdRef` haelt fest, ob
    // die Seite OHNE Id gestartet ist (unveraenderlich fuer die Lebensdauer
    // der Komponente); `ersterErwerbNachAnlageGeschehenRef` schaltet die
    // optimistische Behandlung nach der ERSTEN Aufloesung dieses Erwerbs
    // endgueltig ab (egal ob sie erfolgreich war oder nicht) -- ein SPAETERER
    // Lock-Verlust (Heartbeat-409) oder ein manueller Uebernahmeversuch nach
    // Fremdsperre/Fehler durchlaeuft danach wieder ganz normal die echten
    // Zustaende, ohne die Optimistik erneut anzuwenden.
    const kamOhneIdRef = useRef(dokumentIdNum == null);
    // Vollbild-Ladeanzeige ("Dokument wird geoeffnet ..."): nur bis zur
    // ERSTEN Aufloesung eines Locks fuer ein Dokument, das schon MIT Id
    // geoeffnet wurde. Ein Dokument ohne Id am Seitenstart hat nichts
    // aufzuloesen -- Startwert daher bereits "true" (niemals anzeigen).
    // Einmal aufgeloest, bleibt der Editor auch bei einem SPAETEREN erneuten
    // Erwerbsversuch (Klick auf "Bearbeiten" nach Fremdsperre/Fehler) sicht-
    // bar -- kein Remount, kein Ersetzen durch die Ladeanzeige, genau wie im
    // Lieferant-Dokument-Modal (dort blendet ein Retry ebenfalls nur den
    // Knopf-Zustand um, nie die ganze Ansicht).
    const ersteLockAufloesungGeschehenRef = useRef(dokumentIdNum == null);
    const ersterErwerbNachAnlageGeschehenRef = useRef(false);

    if (lock.status === 'acquired' || lock.status === 'locked-by-other' || lock.status === 'error') {
        ersteLockAufloesungGeschehenRef.current = true;
        ersterErwerbNachAnlageGeschehenRef.current = true;
    }

    const zeigeLadeSeite =
        hatId && !ersteLockAufloesungGeschehenRef.current && (lock.status === 'idle' || lock.status === 'loading');
    const optimistischEditierbarNachAnlage = kamOhneIdRef.current && !ersterErwerbNachAnlageGeschehenRef.current;

    const readOnly = hatId ? (optimistischEditierbarNachAnlage ? false : lock.modus !== 'bearbeiten') : false;
    // Nur fuer die ANZEIGE (Leiste): optimistisch "bearbeiten", solange die
    // Seite so tut, als waere der Erwerb schon durch (siehe oben). Fuer
    // FUNKTIONALE Entscheidungen (Untaetigkeits-Timer, Freigabe) zaehlt
    // dagegen ausschliesslich der echte `lock.modus`/`lock.freigeben`.
    const effektiverModus: 'lesen' | 'bearbeiten' = hatId
        ? (optimistischEditierbarNachAnlage ? 'bearbeiten' : lock.modus)
        : 'bearbeiten';

    const gesperrtDurchAnderen = hatId && lock.status === 'locked-by-other';
    const zeigeFehler = hatId && lock.status === 'error';
    // "Sie lesen nur mit.": eigenes "Fertig" (idle nach Freigabe), NICHT der
    // kurze idle-artige Zwischenzustand direkt nach dem Anlegen (siehe oben)
    // und NICHT die Fremdsperre (die hat ihren eigenen Hinweis).
    const zeigeNurLesenHinweis = hatId && !optimistischEditierbarNachAnlage && lock.status === 'idle';

    // Fehler beim Sperren: Hinweis auf der Seite (unten) UND Toast, wie von
    // den Frontend-Regeln fuer fehlgeschlagene Aktionen verlangt (siehe
    // LieferantDokumentModal, dieselbe Formulierung). Nur beim UEBERGANG in
    // 'error' ausloesen, nicht bei jedem Render.
    useEffect(() => {
        if (lock.status === 'error') {
            toast.error(LOCK_FEHLER_TEXT);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [lock.status]);

    // Der Editor haelt selbst keine Lock-Logik mehr (siehe document-editor/
    // index.tsx) -- die Seite braucht darum einen Weg, ihn vor dem
    // automatischen Freigeben (Untaetigkeit) zum Speichern zu bringen. Siehe
    // DocumentEditorHandle (types.ts) und Kontext-Log, Abweichung vom Plan.
    const editorRef = useRef<DocumentEditorHandle>(null);

    // Untaetigkeits-Timer: nur waehrend WIRKLICH bearbeitet wird (echter,
    // nicht der optimistische Modus) -- ein rein lesend geoeffnetes,
    // gesperrtes oder fehlerhaftes Dokument soll niemandem die Sperre
    // wegnehmen, es gibt nichts zu verlieren. onIdle speichert ERST (falls
    // noetig, siehe DocumentEditorHandle) und gibt DANACH die Sperre frei --
    // niemals umgekehrt, sonst waeren ungespeicherte Aenderungen weg.
    const idleTimer = useIdleTimer({
        enabled: lock.modus === 'bearbeiten',
        onIdle: () => {
            void (async () => {
                await editorRef.current?.speichernFuerFreigabe();
                await lock.freigeben();
            })();
        },
    });

    // Der X-Button-Ablauf im Editor (tabSchliessen(), siehe document-editor/
    // index.tsx) ruft NACH dem Speichern genau diesen Prop auf, BEVOR er
    // window.close() versucht -- danach zeigt der Editor bei Bedarf
    // TabSchliessenHinweis, eine bewusst alleinstehende Vollbild-Bestaetigung
    // OHNE weitere Aktion ("nichts mehr zu tun, ausser den Tab zu schliessen").
    // Ohne diese Markierung wuerde `lock.freigeben()` sofort modus='lesen'
    // setzen -- die Leiste der Seite haette dann faelschlich weiterhin einen
    // aktiven "Bearbeiten"-Knopf sichtbar, direkt UEBER dieser Bestaetigung
    // (kein Layout-Ueberlapp, aber ein widerspruechlicher, ablenkender
    // zweiter Kopf: "Sie koennen schliessen" + ein Knopf, der wieder oeffnet).
    // Ein normales "Fertig" (BearbeitenLeiste.onFertig, direkt an
    // lock.onFertig gebunden, siehe unten) durchlaeuft diesen Wrapper NICHT
    // -- dort bleibt die Leiste bewusst sichtbar (Spec: "'lesen' nach eigenem
    // Fertig ⇒ ... Leiste mit zeigeNurLesenHinweis").
    const [schliesstGerade, setSchliesstGerade] = useState(false);
    const onLockFreigebenFuerSchliessen = useCallback(async () => {
        setSchliesstGerade(true);
        await lock.freigeben();
    }, [lock]);

    // Unveraendert seit Abschnitt 6a: Fallback fuer den Fall, dass der Editor
    // keinen Tab zum Schliessen hat (onLockFreigeben fehlt, z.B. weil die
    // Seite kein Lock haelt -- neues, noch ungespeichertes Dokument). Haelt
    // der Editor dagegen ein Lock, uebernimmt er selbst per
    // onLockFreigeben -> window.close() -> ggf. TabSchliessenHinweis die
    // volle Verantwortung fuers Schliessen, `onClose` wird dann nicht mehr
    // aufgerufen (siehe document-editor/index.tsx, tabSchliessen()).
    const handleClose = () => {
        if (window.history.length > 1) {
            navigate(-1);
        } else {
            window.close();
        }
    };

    if (zeigeLadeSeite) {
        return (
            <div className="fixed inset-0 flex items-center justify-center bg-slate-50 text-slate-500 text-sm">
                Dokument wird geöffnet ...
            </div>
        );
    }

    return (
        <div className="fixed inset-0 z-50 flex flex-col">
            {hatId && !schliesstGerade && (
                <div className="shrink-0 bg-white border-b border-slate-200 px-4 py-2.5 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex-1 min-w-0">
                        {gesperrtDurchAnderen && <GesperrtHinweis halterName={lock.halterName} seit={lock.seit} />}
                        {zeigeFehler && (
                            <div
                                role="alert"
                                className="flex items-center gap-2 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            >
                                <AlertTriangle className="w-4 h-4 shrink-0 text-red-600" aria-hidden="true" />
                                {LOCK_FEHLER_TEXT}
                            </div>
                        )}
                    </div>
                    <BearbeitenLeiste
                        modus={effektiverModus}
                        kannBearbeiten={lock.kannBearbeiten}
                        verbleibendeSekunden={idleTimer.verbleibendeSekunden}
                        verbindungWeg={lock.verbindungWeg}
                        bearbeitenGesperrtGrund={zeigeFehler ? LOCK_FEHLER_TEXT : undefined}
                        zeigeNurLesenHinweis={zeigeNurLesenHinweis}
                        onBearbeiten={lock.onBearbeiten}
                        onFertig={lock.onFertig}
                    />
                </div>
            )}
            {/*
              `[transform:translateZ(0)]` ist kein optischer Effekt, sondern
              erzeugt fuer den Editor darunter ein eigenes "containing block"
              (CSS-Spezifikation: transform/filter/perspective/will-change
              tun das fuer position:fixed-Nachfahren). DocumentEditor legt
              sich selbst als `fixed inset-0` an (er ist unveraendert eine
              eigenstaendige Vollbild-Komponente, siehe document-editor/
              index.tsx) -- ohne diesen Container wuerde er die eigene
              Bearbeiten-Leiste der Seite darueber vollstaendig verdecken.
              Mit dem Container bezieht sich sein `inset-0` stattdessen auf
              GENAU diesen Bereich (den freien Platz unterhalb der Leiste),
              er passt sich also von selbst an, ob die Leiste gerade sichtbar
              ist oder nicht.
            */}
            <div className="flex-1 min-h-0 [transform:translateZ(0)]" data-testid="dokument-editor-flaeche">
                <DocumentEditor
                    ref={editorRef}
                    projektId={parseDokumentId(projektId)}
                    anfrageId={parseDokumentId(anfrageId)}
                    dokumentId={dokumentIdNum}
                    initialDokumentTyp={dokumentTyp as AusgangsGeschaeftsDokumentTyp | undefined}
                    onClose={handleClose}
                    readOnly={readOnly}
                    onLockFreigeben={hatId ? onLockFreigebenFuerSchliessen : undefined}
                />
            </div>
            <KiHilfeChat />
        </div>
    );
}

function parseDokumentId(raw: string | null): number | undefined {
    if (raw == null || raw === '') return undefined;
    const n = Number(raw);
    return Number.isFinite(n) ? n : undefined;
}
