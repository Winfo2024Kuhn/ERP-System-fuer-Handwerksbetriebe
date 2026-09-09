import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    ArrowLeft, ScanLine, Loader2, CheckCircle2, AlertCircle,
    Receipt, RefreshCw, X, Plus, ChevronRight, SplitSquareHorizontal, CheckSquare, ListChecks, FileText,
} from 'lucide-react'
import ScannerModal from '../components/ScannerModal'
import { SupplierSelectionModal } from '../components/SupplierSelectionModal'
import { releaseCameraStream } from '../services/cameraStreamService'

interface LieferantOption {
    id: number
    firmenname: string
}

/**
 * Mobile-Beleg-Scanner mit Wizard-Flow.
 *
 * Schritte pro Beleg:
 *  1) Quelle: Scannen vs. Galerie (PDFs)
 *  2) Lieferant optional zuweisen (oder überspringen)
 *  3) Aufteilung: Ganz für Firma vs. Nur Teile
 *  4) Aufnahme: Kamera-Scan oder PDF-Auswahl
 *
 * Anschliessend laeuft der Upload im Hintergrund, der Scanner ist sofort
 * wieder frei. Bei "Nur Teile" extrahiert der Server die Positionen und
 * der Nutzer hakt sie auf einer Folgeseite an. Bei "Ganz" ist der Mobile-
 * Vorgang abgeschlossen — die Validierung findet am PC statt.
 */
// 'done' gibt es nicht mehr — erfolgreiche Uploads werden aus der Queue
// entfernt und tauchen in der Server-Liste (serverBelege) auf.
type ItemStatus = 'pending' | 'uploading' | 'failed'

type AufteilungsModus = 'VOLLSTAENDIG' | 'TEILWEISE'

type WizardStep = 'idle' | 'source' | 'lieferant' | 'aufteilung'
type CaptureSource = 'scan' | 'gallery'

interface QueueItem {
    localId: string
    file: File
    status: ItemStatus
    error?: string
    addedAt: number
    lieferantId?: number
    lieferantName?: string
    aufteilungsModus: AufteilungsModus
}

// Server-Sicht auf einen schon hochgeladenen Beleg (GET /mobile/belege).
// Bewusst nur die Felder, die wir am Handy brauchen — der Buchhalter
// validiert am PC, das Handy zeigt nur "ist angekommen / Positionen
// auswaehlen / fehler beim KI-Lesen".
interface ServerBeleg {
    id: number
    originalDateiname: string | null
    uploadDatum: string
    status: string
    kiAnalyseStatus: string | null
    aufteilungsModus: string
    lieferantName: string | null
    // Kommt vom Server erst nach der KI-Analyse (BelegDto.Response liefert
    // die Felder schon heute mit) — bis dahin null.
    betragBrutto?: number | null
    belegDatum?: string | null
    kiVorgeschlagenerLieferant?: string | null
}

// Serverstatus fuer "KI liest noch" heisst offiziell LAEUFT (BelegKiAnalyseStatus).
// ServerBelegRow prüfte hier frueher (Tippfehler) nur auf 'RUNNING' — wir
// akzeptieren beide, damit Anzeige und Poll-Bedingung nie auseinanderlaufen.
const kiIstOffen = (status: string | null): boolean =>
    status === 'PENDING' || status === 'LAEUFT' || status === 'RUNNING'

// Deutsche Zahl mit zwei Nachkommastellen, ohne neue Abhaengigkeit.
const betragFormatter = new Intl.NumberFormat('de-DE', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
})
const formatBetrag = (betrag: number): string => betragFormatter.format(betrag)

// beleg.belegDatum kommt vom Server als "yyyy-MM-dd" (LocalDate). Die
// Komponenten werden direkt als lokales Datum gebaut statt die ISO-Zeichen-
// kette zu parsen — sonst interpretiert der Date-Parser sie als UTC-
// Mitternacht und toLocaleDateString kann je nach Zeitzone einen Tag
// zurueckspringen.
const formatBelegDatum = (belegDatum: string): string => {
    const [jahr, monat, tag] = belegDatum.split('-').map(Number)
    if (!jahr || !monat || !tag) return belegDatum
    return new Date(jahr, monat - 1, tag).toLocaleDateString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
    })
}

interface Permissions {
    darfScannen: boolean
    darfSehen: boolean
}

export default function BelegScannerPage() {
    const navigate = useNavigate()
    const [permission, setPermission] = useState<Permissions | null>(null)
    const [permissionLoading, setPermissionLoading] = useState(true)
    const [showScanner, setShowScanner] = useState(false)
    const [queue, setQueue] = useState<QueueItem[]>([])
    // Serverseitige Liste der zuletzt hochgeladenen Belege des Aufrufers.
    // Sie ueberlebt App-Wechsel, PWA-Reload und den TEILWEISE-Navigationssprung
    // — anders als die lokale Queue, die nur in-flight + failed Items haelt.
    const [serverBelege, setServerBelege] = useState<ServerBeleg[]>([])
    const [serverLoading, setServerLoading] = useState(false)
    const fileInputRef = useRef<HTMLInputElement>(null)

    // Wizard-State: ein Beleg wird in 3 Schritten konfiguriert, dann aufgenommen.
    const [wizardStep, setWizardStep] = useState<WizardStep>('idle')
    const [draftSource, setDraftSource] = useState<CaptureSource | null>(null)
    const [draftLieferant, setDraftLieferant] = useState<LieferantOption | null>(null)

    // Halte den aktiven Capture-Kontext zwischen Wizard-Ende und Datei-Eingang
    // ausserhalb des Render-Zyklus. Vermeidet State-Race wenn das File-Event
    // noch waehrend des Rerenders nach Sheet-Close hereinkommt.
    const captureContextRef = useRef<{ lieferant: LieferantOption | null; modus: AufteilungsModus } | null>(null)

    const token = typeof window !== 'undefined' ? localStorage.getItem('zeiterfassung_token') : null

    useEffect(() => {
        if (!token) {
            setPermission({ darfScannen: false, darfSehen: false })
            setPermissionLoading(false)
            return
        }
        fetch(`/api/buchhaltung/mobile/me/permissions?token=${token}`)
            .then(res => res.ok ? res.json() : { darfScannen: false, darfSehen: false })
            .then(setPermission)
            .catch(() => setPermission({ darfScannen: false, darfSehen: false }))
            .finally(() => setPermissionLoading(false))
    }, [token])

    // Server-Liste der zuletzt hochgeladenen Belege laden. Wird beim Mount,
    // nach jedem erfolgreichen Upload und beim Zurueck-Wechseln in den Tab
    // (visibilitychange) gerufen — letzteres weil iOS PWA die Page beim
    // Tab-Wechsel nicht remounted, der KI-Status aber serverseitig
    // weiterlaeuft.
    const reloadServerBelege = useCallback(async () => {
        if (!token) return
        setServerLoading(true)
        try {
            const res = await fetch(`/api/buchhaltung/mobile/belege?token=${encodeURIComponent(token)}`)
            if (!res.ok) return
            const data = (await res.json()) as ServerBeleg[]
            setServerBelege(data)
        } catch {
            // Liste ist nur Convenience — bei Netzwerkfehler weiter mit lokalem State.
        } finally {
            setServerLoading(false)
        }
    }, [token])

    useEffect(() => {
        if (!permission?.darfSehen) return
        void reloadServerBelege()
        const onVisible = () => {
            if (document.visibilityState === 'visible') void reloadServerBelege()
        }
        document.addEventListener('visibilitychange', onVisible)
        return () => document.removeEventListener('visibilitychange', onVisible)
    }, [permission?.darfSehen, reloadServerBelege])

    // Pollt alle 3s nach, solange mindestens ein eigener Beleg noch auf die
    // KI-Analyse wartet (PENDING/LAEUFT) — sonst muesste der Buchhalter die
    // App manuell neu laden, um den Betrag zu sehen. Vorbild: der Detail-
    // Poller in BelegeKasseEditor.tsx (BelegDetailModal) — cancelled-Flag +
    // setTimeout, Cleanup raeumt beides auf. Die Neuplanung selbst ergibt
    // sich hier daraus, dass reloadServerBelege() eine neue serverBelege-
    // Referenz setzt und der Effekt dadurch mit dem aktuellen Stand neu
    // entscheidet, ob noch ein Timer noetig ist — sind alle fertig, wird
    // keiner mehr gestellt.
    useEffect(() => {
        if (!permission?.darfSehen) return
        if (!serverBelege.some(b => kiIstOffen(b.kiAnalyseStatus))) return

        let cancelled = false
        const timer = setTimeout(() => {
            if (!cancelled) void reloadServerBelege()
        }, 3000)

        return () => { cancelled = true; clearTimeout(timer) }
    }, [serverBelege, permission?.darfSehen, reloadServerBelege])

    // Beim Verlassen der Scanner-Page Kamera-Tracks freigeben. Innerhalb der
    // Page bleibt der MediaStream im cameraStreamService gecacht — so wird
    // bei iOS PWA pro Page-Besuch nur EIN Permission-Prompt ausgeloest.
    useEffect(() => {
        return () => { releaseCameraStream() }
    }, [])

    // Vollbild-Overlay fuer den TEILWEISE-Flow: nach Capture wartet die UI
    // hier, bis der Upload durch ist und die Beleg-ID zurueckkommt. Danach
    // navigieren wir direkt zur Positionen-Auswahl-Page, die selbst weiter
    // pollt bis die KI-Extraktion fertig ist. So muss der Buchhalter nicht
    // nochmal in einer Queue auf "Auswaehlen" klicken.
    const [direktUploadDateiname, setDirektUploadDateiname] = useState<string | null>(null)
    // Schutz gegen Doppel-Auslsung (z.B. Doppel-Tap im ScannerModal) und
    // gegen State-Updates nach Unmount (User drueckt Back waehrend Upload).
    const direktUploadInFlightRef = useRef(false)
    const mountedRef = useRef(true)
    useEffect(() => {
        mountedRef.current = true
        return () => { mountedRef.current = false }
    }, [])

    const enqueue = (file: File, lieferant: LieferantOption | null, modus: AufteilungsModus) => {
        // TEILWEISE laeuft NICHT ueber die Hintergrund-Queue — der Nutzer wartet
        // bis die Beleg-ID da ist und landet sofort auf der Positionen-Auswahl.
        if (modus === 'TEILWEISE') {
            void uploadUndNavigiereZuPositionen(file, lieferant)
            return
        }
        const item: QueueItem = {
            localId: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
            file,
            status: 'pending',
            addedAt: Date.now(),
            lieferantId: lieferant?.id,
            lieferantName: lieferant?.firmenname,
            aufteilungsModus: modus,
        }
        setQueue(q => [item, ...q])
        // Fire-and-forget: kein await — Scanner ist sofort wieder bereit.
        void uploadItem(item)
    }

    const uploadUndNavigiereZuPositionen = async (file: File, lieferant: LieferantOption | null) => {
        // Doppel-Guard: Wenn schon ein TEILWEISE-Upload laeuft, ignoriere zweite
        // Auslsung (sonst gehen Belege verloren, weil Overlay ueberschrieben wird).
        if (direktUploadInFlightRef.current) return
        direktUploadInFlightRef.current = true
        setDirektUploadDateiname(file.name)
        try {
            const fd = new FormData()
            fd.append('datei', file)
            const url = new URL(`/api/buchhaltung/mobile/belege`, window.location.origin)
            if (token) url.searchParams.set('token', token)
            if (lieferant?.id != null) url.searchParams.set('lieferantId', String(lieferant.id))
            url.searchParams.set('aufteilungsModus', 'TEILWEISE')
            const res = await fetch(url.pathname + url.search, {
                method: 'POST',
                body: fd,
            })
            if (!res.ok) {
                const txt = await res.text().catch(() => '')
                throw new Error(`HTTP ${res.status}: ${txt}`)
            }
            const data = await res.json()
            // Wenn der User waehrend des Uploads die Page verlassen hat, NICHT
            // mehr navigieren — sonst springt er aus einer ganz anderen Page
            // ploetzlich zur Positionen-Auswahl.
            if (!mountedRef.current) return
            // Server-Liste refetchen, damit der Beleg auch bei Rueck-Navigation
            // sichtbar ist (Page bleibt gemountet, State bleibt erhalten).
            void reloadServerBelege()
            // Direkt rueber — die Page pollt selbst, bis kiAnalyseStatus DONE ist.
            navigate(`/belege/${data.id}/positionen`)
        } catch (err) {
            if (!mountedRef.current) return
            // Bei Fehler nicht spurlos verschwinden: in die Queue als failed,
            // damit der Buchhalter den Beleg sieht und retryen kann.
            const msg = err instanceof Error ? err.message : String(err)
            setQueue(q => [{
                localId: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
                file,
                status: 'failed',
                addedAt: Date.now(),
                error: msg,
                lieferantId: lieferant?.id,
                lieferantName: lieferant?.firmenname,
                aufteilungsModus: 'TEILWEISE',
            }, ...q])
        } finally {
            // Overlay garantiert zumachen, Lock loesen — auch bei Unmount.
            if (mountedRef.current) setDirektUploadDateiname(null)
            direktUploadInFlightRef.current = false
        }
    }

    // Sichtbare Fehler-Karte fuer Dateien, die wir gar nicht erst hochladen
    // (z.B. JPG aus der Galerie auf Android-Browsern, die das accept-Attribut
    // ignorieren). Ohne das wuerde der Tipp einfach stumm verschwinden.
    const pushRejected = (file: File, reason: string, lieferant: LieferantOption | null, modus: AufteilungsModus) => {
        setQueue(q => [{
            localId: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
            file,
            status: 'failed',
            addedAt: Date.now(),
            error: reason,
            lieferantId: lieferant?.id,
            lieferantName: lieferant?.firmenname,
            aufteilungsModus: modus,
        }, ...q])
    }

    const uploadItem = async (item: QueueItem) => {
        setQueue(q => q.map(x => x.localId === item.localId ? { ...x, status: 'uploading' } : x))
        try {
            const fd = new FormData()
            fd.append('datei', item.file)
            const url = new URL(`/api/buchhaltung/mobile/belege`, window.location.origin)
            if (token) url.searchParams.set('token', token)
            if (item.lieferantId != null) url.searchParams.set('lieferantId', String(item.lieferantId))
            url.searchParams.set('aufteilungsModus', item.aufteilungsModus)
            const res = await fetch(url.pathname + url.search, {
                method: 'POST',
                body: fd,
            })
            if (!res.ok) {
                const txt = await res.text().catch(() => '')
                throw new Error(`HTTP ${res.status}: ${txt}`)
            }
            // Erfolgreich hochgeladen — Item wandert aus der lokalen Queue in
            // die persistente Server-Liste. Dadurch ueberlebt es Reload/Tab-
            // Wechsel und der Buchhalter findet es auch nach App-Pause wieder.
            //
            // Optimistic Insert: ohne diesen Schritt waere das Item zwischen
            // setQueue(filter) und Eintreffen der reloadServerBelege()-Response
            // kurz weder lokal noch in der Server-Liste sichtbar — der
            // "Hochgeladen"-Counter wuerde flackern (3→2→3). Wir fuegen das
            // Item daher sofort optimistisch ein; der spaetere Refetch
            // dedupliziert per id.
            const data = (await res.json().catch(() => null)) as { id?: number } | null
            const serverId = data?.id
            if (serverId != null) {
                const optimistisch: ServerBeleg = {
                    id: serverId,
                    originalDateiname: item.file.name,
                    uploadDatum: new Date().toISOString(),
                    status: 'ERFASST',
                    kiAnalyseStatus: 'PENDING',
                    aufteilungsModus: item.aufteilungsModus,
                    lieferantName: item.lieferantName ?? null,
                }
                setServerBelege(prev =>
                    prev.some(b => b.id === serverId) ? prev : [optimistisch, ...prev]
                )
            }
            setQueue(q => q.filter(x => x.localId !== item.localId))
            void reloadServerBelege()
        } catch (err) {
            const msg = err instanceof Error ? err.message : String(err)
            setQueue(q => q.map(x => x.localId === item.localId
                ? { ...x, status: 'failed', error: msg }
                : x))
        }
    }

    const retry = (item: QueueItem) => {
        if (item.status === 'uploading') return
        void uploadItem(item)
    }

    const removeItem = (localId: string) => {
        setQueue(q => q.filter(x => x.localId !== localId))
    }

    // --- Wizard-Flow ---

    const startWizard = () => {
        setDraftSource(null)
        setDraftLieferant(null)
        setWizardStep('source')
    }

    const cancelWizard = () => {
        setWizardStep('idle')
        setDraftSource(null)
        setDraftLieferant(null)
    }

    const pickSource = (src: CaptureSource) => {
        setDraftSource(src)
        setWizardStep('lieferant')
    }

    const handleSupplierPicked = (l: LieferantOption | null) => {
        setDraftLieferant(l)
        setWizardStep('aufteilung')
    }

    const finishWizard = (modus: AufteilungsModus) => {
        const src = draftSource
        if (src === 'scan') {
            captureContextRef.current = { lieferant: draftLieferant, modus }
            setWizardStep('idle')
            setShowScanner(true)
        } else if (src === 'gallery') {
            captureContextRef.current = { lieferant: draftLieferant, modus }
            setWizardStep('idle')
            // Im naechsten Tick triggern, damit das Bottom-Sheet zuerst schliesst.
            setTimeout(() => fileInputRef.current?.click(), 0)
        }
    }

    const handleScanComplete = async (file: File) => {
        setShowScanner(false)
        const ctx = captureContextRef.current
        if (!ctx) {
            // SICHTBAR machen statt silent return — sonst verschwindet der Beleg
            // spurlos und der User sieht "0 in Queue / 0 Fehler" ohne Hinweis.
            pushRejected(file, 'Aufnahme-Kontext verloren — bitte Wizard neu starten', null, 'VOLLSTAENDIG')
            return
        }
        enqueue(file, ctx.lieferant, ctx.modus)
        // ctx bleibt erhalten (wird beim naechsten finishWizard ueberschrieben).
        // Frueher hier null gesetzt → Race mit ScannerModal-Renders moeglich.
    }

    const handleFileChoose = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files
        const ctx = captureContextRef.current
        if (!files || files.length === 0) {
            // User hat den File-Picker mit Cancel geschlossen — nichts zu tun.
            if (fileInputRef.current) fileInputRef.current.value = ''
            return
        }
        if (!ctx) {
            // Files da, aber kein Wizard-Kontext — sichtbar machen statt silent.
            for (let i = 0; i < files.length; i++) {
                pushRejected(files[i], 'Aufnahme-Kontext verloren — bitte Wizard neu starten', null, 'VOLLSTAENDIG')
            }
            if (fileInputRef.current) fileInputRef.current.value = ''
            return
        }
        for (let i = 0; i < files.length; i++) {
            const f = files[i]
            // Defensive: Filepicker akzeptiert zwar nur PDFs (accept-Attr),
            // aber Mobile-Browser ignorieren das teils — also Endung pruefen
            // und bei Mismatch sichtbare Fehler-Karte erzeugen.
            if (!f.type.includes('pdf') && !f.name.toLowerCase().endsWith('.pdf')) {
                pushRejected(f, 'Nur PDF-Dateien erlaubt', ctx.lieferant, ctx.modus)
                continue
            }
            enqueue(f, ctx.lieferant, ctx.modus)
        }
        // ctx NICHT nullen — bleibt bis zum naechsten finishWizard-Aufruf.
        if (fileInputRef.current) fileInputRef.current.value = ''
    }

    // --- Gating ---

    if (permissionLoading) {
        return (
            <div className="h-full flex items-center justify-center bg-slate-50">
                <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
            </div>
        )
    }

    if (!permission?.darfScannen) {
        return (
            <div className="h-full flex flex-col bg-slate-50 safe-area-top">
                <header className="bg-white border-b border-slate-200 px-4 py-4 flex items-center gap-3">
                    <button onClick={() => navigate('/')} className="p-2 hover:bg-slate-100 rounded-lg">
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <h1 className="font-bold text-slate-900">Beleg-Scanner</h1>
                </header>
                <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500">
                    <AlertCircle className="w-12 h-12 mb-3 text-slate-400" />
                    <p className="font-medium text-slate-700">Keine Berechtigung</p>
                    <p className="text-sm mt-2 max-w-xs">
                        Der Beleg-Scanner ist nur für Mitarbeiter der Buchhaltung verfügbar.
                        Berechtigungen werden unter Administration → Lieferanten-Dokumentenrechte vergeben.
                    </p>
                </div>
            </div>
        )
    }

    // "Hochgeladen" zaehlt jetzt aus der persistenten Server-Liste, nicht aus
    // der lokalen Queue — sonst wuerde der Counter beim Refetch zurueckspringen
    // auf 0, sobald ein Item aus der Queue in die Server-Liste wandert.
    const counts = {
        offen: queue.filter(q => q.status === 'pending' || q.status === 'uploading').length,
        fertig: serverBelege.length,
        fehler: queue.filter(q => q.status === 'failed').length,
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top flex items-center gap-3">
                <button onClick={() => navigate('/')} className="p-2 hover:bg-slate-100 rounded-lg active:scale-95">
                    <ArrowLeft className="w-5 h-5 text-slate-600" />
                </button>
                <div className="flex-1">
                    <h1 className="font-bold text-slate-900">Beleg-Scanner</h1>
                    <p className="text-xs text-slate-500">Schnellscan – Validierung erfolgt am PC</p>
                </div>
            </header>

            {/* Haupt-Aktion: ein einziger Einstieg in den Wizard */}
            <div className="p-4 bg-white border-b border-slate-100 shadow-sm">
                <button
                    onClick={startWizard}
                    className="w-full bg-rose-600 hover:bg-rose-700 active:scale-[0.98] text-white font-bold rounded-2xl py-5 flex items-center justify-center gap-3 shadow-lg transition-all"
                >
                    <Plus className="w-7 h-7" />
                    <span className="text-lg">Beleg hinzufügen</span>
                </button>
            </div>

            <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf"
                multiple
                onChange={handleFileChoose}
                className="hidden"
            />

            {/* Queue-Status */}
            <div className="grid grid-cols-3 gap-2 px-4 py-3 bg-slate-100/50">
                <StatBox label="In Queue" value={counts.offen} color="text-sky-600" />
                <StatBox label="Hochgeladen" value={counts.fertig} color="text-emerald-600" />
                <StatBox label="Fehler" value={counts.fehler} color="text-red-600" />
            </div>

            {/* Liste: erst lokale in-flight/fehlerhafte Items, danach die
                persistierte Server-Liste der zuletzt hochgeladenen Belege. */}
            <div className="flex-1 overflow-auto p-4 space-y-2 safe-area-bottom">
                {queue.length === 0 && serverBelege.length === 0 ? (
                    <div className="text-center text-slate-400 py-12">
                        <Receipt className="w-12 h-12 mx-auto mb-2 opacity-40" />
                        <p className="text-sm">
                            {serverLoading ? 'Belege werden geladen…' : 'Noch keine Belege gescannt.'}
                        </p>
                        <p className="text-xs mt-1">Tippe oben auf <strong>Beleg hinzufügen</strong>.</p>
                    </div>
                ) : (
                    <>
                        {queue.map(item => (
                            <QueueRow
                                key={item.localId}
                                item={item}
                                onRetry={retry}
                                onRemove={removeItem}
                            />
                        ))}
                        {serverBelege.map(b => (
                            <ServerBelegRow
                                key={b.id}
                                beleg={b}
                                onOpenPositionen={(id) => navigate(`/belege/${id}/positionen`)}
                            />
                        ))}
                    </>
                )}
            </div>

            {/* Wizard Schritt 1: Quelle waehlen */}
            {wizardStep === 'source' && (
                <SourceSheet
                    onPick={pickSource}
                    onCancel={cancelWizard}
                />
            )}

            {/* Wizard Schritt 2: Lieferant zuweisen oder ueberspringen */}
            <SupplierSelectionModal
                isOpen={wizardStep === 'lieferant'}
                onClose={cancelWizard}
                onSelect={handleSupplierPicked}
                onBack={() => setWizardStep('source')}
            />

            {/* Wizard Schritt 3: Aufteilung waehlen */}
            {wizardStep === 'aufteilung' && (
                <AufteilungSheet
                    onPick={finishWizard}
                    onBack={() => setWizardStep('lieferant')}
                    onCancel={cancelWizard}
                    lieferantName={draftLieferant?.firmenname ?? null}
                />
            )}

            {showScanner && (
                <ScannerModal
                    onClose={() => setShowScanner(false)}
                    onSave={handleScanComplete}
                />
            )}

            {direktUploadDateiname && (
                <DirektUploadOverlay dateiname={direktUploadDateiname} />
            )}
        </div>
    )
}

function DirektUploadOverlay({ dateiname }: { dateiname: string }) {
    return (
        <div className="fixed inset-0 z-[70] bg-slate-900/80 backdrop-blur-sm flex flex-col items-center justify-center p-6 safe-area-top safe-area-bottom">
            <div className="bg-white rounded-2xl shadow-2xl p-8 max-w-sm w-full flex flex-col items-center gap-4 text-center">
                <Loader2 className="w-12 h-12 text-rose-600 animate-spin" />
                <div>
                    <p className="text-lg font-bold text-slate-900">Beleg wird hochgeladen…</p>
                    <p className="text-sm text-slate-500 mt-1 truncate max-w-full">{dateiname}</p>
                </div>
                <p className="text-xs text-slate-500 leading-relaxed">
                    Gleich werden die einzelnen Positionen erkannt — du kannst sie dann direkt
                    anhaken. Bitte einen Moment nicht zurückgehen.
                </p>
            </div>
        </div>
    )
}

// --- Wizard-Bottom-Sheets ---

function SourceSheet({ onPick, onCancel }: {
    onPick: (s: CaptureSource) => void
    onCancel: () => void
}) {
    return (
        <SheetShell title="Wie möchtest du den Beleg erfassen?" onCancel={onCancel}>
            <button
                onClick={() => onPick('scan')}
                className="w-full bg-white border-2 border-slate-200 hover:border-rose-300 active:bg-rose-50 rounded-2xl p-5 flex items-center gap-4 text-left transition-all"
            >
                <div className="w-12 h-12 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <ScanLine className="w-6 h-6 text-rose-600" />
                </div>
                <div className="flex-1 min-w-0">
                    <p className="font-bold text-slate-900">Beleg scannen</p>
                    <p className="text-sm text-slate-500 mt-0.5">Mit der Kamera abfotografieren</p>
                </div>
                <ChevronRight className="w-5 h-5 text-slate-400 flex-shrink-0" />
            </button>

            <button
                onClick={() => onPick('gallery')}
                className="w-full bg-white border-2 border-slate-200 hover:border-rose-300 active:bg-rose-50 rounded-2xl p-5 flex items-center gap-4 text-left transition-all"
            >
                <div className="w-12 h-12 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <FileText className="w-6 h-6 text-rose-600" />
                </div>
                <div className="flex-1 min-w-0">
                    <p className="font-bold text-slate-900">Aus Galerie auswählen</p>
                    <p className="text-sm text-slate-500 mt-0.5">Nur PDF-Dateien</p>
                </div>
                <ChevronRight className="w-5 h-5 text-slate-400 flex-shrink-0" />
            </button>
        </SheetShell>
    )
}

function AufteilungSheet({ onPick, onBack, onCancel, lieferantName }: {
    onPick: (m: AufteilungsModus) => void
    onBack: () => void
    onCancel: () => void
    lieferantName: string | null
}) {
    return (
        <SheetShell
            title="Was ist auf dem Beleg?"
            subtitle={lieferantName ? `Lieferant: ${lieferantName}` : 'Ohne Lieferant'}
            onCancel={onCancel}
            onBack={onBack}
        >
            <button
                onClick={() => onPick('VOLLSTAENDIG')}
                className="w-full bg-white border-2 border-slate-200 hover:border-rose-300 active:bg-rose-50 rounded-2xl p-5 flex items-center gap-4 text-left transition-all"
            >
                <div className="w-12 h-12 bg-emerald-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <CheckSquare className="w-6 h-6 text-emerald-600" />
                </div>
                <div className="flex-1 min-w-0">
                    <p className="font-bold text-slate-900">Ganz für die Firma</p>
                    <p className="text-sm text-slate-500 mt-0.5">Kompletter Beleg wird gebucht</p>
                </div>
                <ChevronRight className="w-5 h-5 text-slate-400 flex-shrink-0" />
            </button>

            <button
                onClick={() => onPick('TEILWEISE')}
                className="w-full bg-white border-2 border-slate-200 hover:border-rose-300 active:bg-rose-50 rounded-2xl p-5 flex items-center gap-4 text-left transition-all"
            >
                <div className="w-12 h-12 bg-amber-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <SplitSquareHorizontal className="w-6 h-6 text-amber-600" />
                </div>
                <div className="flex-1 min-w-0">
                    <p className="font-bold text-slate-900">Nur Teile</p>
                    <p className="text-sm text-slate-500 mt-0.5">Positionen nach dem Scan auswählen</p>
                </div>
                <ChevronRight className="w-5 h-5 text-slate-400 flex-shrink-0" />
            </button>
        </SheetShell>
    )
}

function SheetShell({ title, subtitle, onCancel, onBack, children }: {
    title: string
    subtitle?: string
    onCancel: () => void
    onBack?: () => void
    children: React.ReactNode
}) {
    return (
        <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-slate-900/50">
            <div className="w-full sm:max-w-md bg-slate-50 rounded-t-3xl sm:rounded-3xl shadow-2xl safe-area-bottom">
                <div className="px-5 pt-5 pb-3 flex items-start gap-3">
                    {onBack && (
                        <button
                            onClick={onBack}
                            className="p-2 -ml-2 hover:bg-slate-200 rounded-lg"
                            aria-label="Zurück"
                        >
                            <ArrowLeft className="w-5 h-5 text-slate-600" />
                        </button>
                    )}
                    <div className="flex-1 min-w-0">
                        <h2 className="text-lg font-bold text-slate-900">{title}</h2>
                        {subtitle && <p className="text-sm text-slate-500 mt-0.5">{subtitle}</p>}
                    </div>
                    <button
                        onClick={onCancel}
                        className="p-2 -mr-2 hover:bg-slate-200 rounded-lg"
                        aria-label="Abbrechen"
                    >
                        <X className="w-5 h-5 text-slate-600" />
                    </button>
                </div>
                <div className="px-5 pb-6 space-y-3">
                    {children}
                </div>
            </div>
        </div>
    )
}

function StatBox({ label, value, color }: { label: string; value: number; color: string }) {
    return (
        <div className="bg-white rounded-xl p-3 text-center border border-slate-200">
            <div className={`text-2xl font-bold tabular-nums ${color}`}>{value}</div>
            <div className="text-xs text-slate-500 mt-0.5">{label}</div>
        </div>
    )
}

function QueueRow({ item, onRetry, onRemove }: {
    item: QueueItem
    onRetry: (item: QueueItem) => void
    onRemove: (localId: string) => void
}) {
    // 'done' kommt hier nicht mehr vor — erfolgreiche Uploads wandern in die
    // Server-Liste. QueueRow zeigt also nur in-flight (pending/uploading) und
    // failed Items.
    const Icon = item.status === 'failed' ? AlertCircle
        : item.status === 'uploading' ? Loader2
        : Receipt
    const iconCls = item.status === 'failed' ? 'text-red-500'
        : item.status === 'uploading' ? 'text-sky-500 animate-spin'
        : 'text-slate-400'
    const bg = item.status === 'failed' ? 'bg-red-50 border-red-100'
        : 'bg-white border-slate-200'

    return (
        <div className={`flex items-center gap-3 p-3 rounded-xl border ${bg}`}>
            <Icon className={`w-6 h-6 flex-shrink-0 ${iconCls}`} />
            <div className="flex-1 min-w-0">
                <p className="font-medium text-sm text-slate-900 truncate">{item.file.name}</p>
                <p className="text-xs text-slate-500">
                    {item.status === 'pending' && 'Wartet…'}
                    {item.status === 'uploading' && 'Wird hochgeladen…'}
                    {item.status === 'failed' && (item.error || 'Fehler')}
                </p>
                {item.lieferantName && (
                    <p className="text-xs text-slate-500 truncate">→ {item.lieferantName}</p>
                )}
            </div>
            {item.status === 'failed' && (
                <button onClick={() => onRetry(item)} className="p-2 hover:bg-white rounded-lg" aria-label="Erneut versuchen">
                    <RefreshCw className="w-4 h-4 text-slate-600" />
                </button>
            )}
            {item.status === 'failed' && (
                <button onClick={() => onRemove(item.localId)} className="p-2 hover:bg-white rounded-lg" aria-label="Entfernen">
                    <X className="w-4 h-4 text-slate-500" />
                </button>
            )}
        </div>
    )
}

function ServerBelegRow({ beleg, onOpenPositionen }: {
    beleg: ServerBeleg
    onOpenPositionen: (belegId: number) => void
}) {
    const istValidiert = beleg.status === 'VALIDIERT'
    const kiLaeuft = kiIstOffen(beleg.kiAnalyseStatus)
    const kiFehler = beleg.kiAnalyseStatus === 'FAILED'
    const istTeilweise = beleg.aufteilungsModus === 'TEILWEISE'
    const kannPositionenWaehlen = istTeilweise && beleg.kiAnalyseStatus === 'DONE' && !istValidiert

    const Icon = istValidiert ? CheckCircle2
        : kiFehler ? AlertCircle
        : kiLaeuft ? Loader2
        : CheckCircle2
    const iconCls = istValidiert ? 'text-emerald-500'
        : kiFehler ? 'text-amber-500'
        : kiLaeuft ? 'text-sky-500 animate-spin'
        : 'text-emerald-500'
    const bg = istValidiert ? 'bg-emerald-50 border-emerald-100'
        : kiFehler ? 'bg-amber-50 border-amber-100'
        : 'bg-white border-slate-200'

    const zeit = (() => {
        try {
            return new Date(beleg.uploadDatum).toLocaleString('de-DE', {
                day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
            })
        } catch { return beleg.uploadDatum }
    })()

    // Sobald die KI fertig ist und einen Betrag geliefert hat, ersetzt der
    // Betrag die reine "Hochgeladen"-Zeile — das ist die erste Zahl, die der
    // Buchhalter am Handy zu sehen bekommt, auch ohne die App neu zu laden.
    const betragText = beleg.kiAnalyseStatus === 'DONE' && beleg.betragBrutto != null
        ? `${formatBetrag(beleg.betragBrutto)} €${beleg.belegDatum ? ` · ${formatBelegDatum(beleg.belegDatum)}` : ''}`
        : null

    const statusText = istValidiert
        ? `Validiert · ${zeit}`
        : kiLaeuft
        ? `KI liest Beleg… · ${zeit}`
        : kiFehler
        ? `KI-Lesefehler · ${zeit}`
        : betragText
        ? betragText
        : `Hochgeladen · ${zeit}`

    // Abweichende KI-Lesung: der Buchhalter hat am Handy einen Lieferanten
    // gewaehlt (oder keinen), die KI liest aus dem Beleg selbst einen Namen.
    // Weichen die voneinander ab, ist das nur ein Hinweis — bearbeiten laesst
    // sich das am Handy bewusst nicht (Nicht-Ziel), nur am PC pruefen.
    const kiLieferantWeichtAb = !!beleg.kiVorgeschlagenerLieferant
        && beleg.kiVorgeschlagenerLieferant !== beleg.lieferantName

    return (
        <div className={`flex items-center gap-3 p-3 rounded-xl border ${bg}`}>
            <Icon className={`w-6 h-6 flex-shrink-0 ${iconCls}`} />
            <div className="flex-1 min-w-0">
                <p className="font-medium text-sm text-slate-900 truncate">
                    {beleg.originalDateiname || `Beleg #${beleg.id}`}
                </p>
                <p className="text-xs text-slate-500">{statusText}</p>
                {beleg.lieferantName && (
                    <p className="text-xs text-slate-500 truncate">→ {beleg.lieferantName}</p>
                )}
                {kiLieferantWeichtAb && (
                    <p className="text-xs text-amber-700 mt-0.5 truncate">
                        KI hat "{beleg.kiVorgeschlagenerLieferant}" gelesen – Am PC prüfen
                    </p>
                )}
                {istTeilweise && !kannPositionenWaehlen && !istValidiert && (
                    <p className="text-xs text-amber-700 mt-0.5">Positionen-Auswahl folgt nach KI-Analyse</p>
                )}
            </div>
            {kannPositionenWaehlen && (
                <button
                    onClick={() => onOpenPositionen(beleg.id)}
                    className="px-2 py-1.5 bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold rounded-lg flex items-center gap-1"
                    aria-label="Positionen auswählen"
                >
                    <ListChecks className="w-4 h-4" />
                    Auswählen
                </button>
            )}
        </div>
    )
}
