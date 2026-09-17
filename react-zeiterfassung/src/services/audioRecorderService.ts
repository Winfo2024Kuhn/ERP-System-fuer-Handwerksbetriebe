/**
 * Anders als `cameraStreamService.ts` wird der Mikrofon-Stream bewusst NICHT
 * ueber mehrere Aufnahmen hinweg gehalten. Solange ein Mikrofon-Stream offen
 * ist, zeigt iOS einen orangen Punkt in der Statusleiste. Der bliebe nach dem
 * Diktieren stehen und saehe aus, als lausche die App weiter, obwohl niemand
 * mehr aufnimmt. Dafuer nehmen wir in Kauf, dass iOS in der installierten PWA
 * je Aufnahme erneut nach der Erlaubnis fragen kann.
 *
 * Jede Aufnahme fordert deshalb per `starteAufnahme()` einen frischen
 * MediaStream per `getUserMedia` an und gibt ihn in `stoppe()`/`brichAb()`
 * sofort wieder frei (`getTracks().forEach(t => t.stop())`). Der Modul-Cache
 * aus `cameraStreamService.ts` (`cachedStream`, `pendingAcquire`) ist hier
 * also bewusst KEIN Vorbild, sondern das Gegenbeispiel, gegen das sich diese
 * Datei entscheidet.
 */

/** Reihenfolge = Praeferenz. Der erste von MediaRecorder unterstuetzte Typ gewinnt. */
const MIME_TYP_KANDIDATEN = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/aac']

export interface AufnahmeSitzung {
    /** Der vom Browser tatsaechlich gewaehlte Inhaltstyp, z.B. "audio/webm;codecs=opus". */
    readonly mimeType: string
    /** Date.now() beim Start — Grundlage fuer die 1s- und 2min-Regel. */
    readonly gestartetAm: number
    /** Beendet die Aufnahme, gibt das Mikrofon SOFORT frei, liefert die Aufnahme. */
    stoppe(): Promise<Blob>
    /** Bricht ab, verwirft die Daten, gibt das Mikrofon SOFORT frei. */
    brichAb(): void
}

/** Der Nutzer hat den Mikrofonzugriff abgelehnt oder er ist gesperrt. */
export class MikrofonNichtErlaubtError extends Error {}

export function mikrofonWirdUnterstuetzt(): boolean {
    return Boolean(navigator.mediaDevices?.getUserMedia) && typeof MediaRecorder !== 'undefined'
}

/** Erster von MediaRecorder unterstuetzter Kandidat, oder '' wenn der Browser selbst waehlen soll. */
function waehleMimeType(): string {
    if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
        return ''
    }
    return MIME_TYP_KANDIDATEN.find(kandidat => MediaRecorder.isTypeSupported(kandidat)) ?? ''
}

/** Liest `.name` robust aus, auch bei DOMException (die NICHT von Error erbt). */
function nameDesFehlers(fehler: unknown): string | undefined {
    if (typeof fehler === 'object' && fehler !== null && 'name' in fehler) {
        return String((fehler as { name: unknown }).name)
    }
    return undefined
}

/** Wie `cameraStreamService.releaseCameraStream()`: Freigeben darf niemals werfen. */
function gibMikrofonFrei(stream: MediaStream): void {
    try {
        stream.getTracks().forEach(track => track.stop())
    } catch {
        /* ignore */
    }
}

export async function starteAufnahme(): Promise<AufnahmeSitzung> {
    let stream: MediaStream
    try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (fehler) {
        const name = nameDesFehlers(fehler)
        if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
            throw new MikrofonNichtErlaubtError('Mikrofonzugriff wurde nicht erlaubt.')
        }
        throw fehler
    }

    let chunks: Blob[] = []
    let recorder: MediaRecorder
    let mimeType: string
    let gestartetAm: number
    try {
        // Zwischen dem erfolgreichen getUserMedia oben und dem fertigen Recorder
        // kann noch einiges schiefgehen: waehleMimeType() ruft
        // MediaRecorder.isTypeSupported() auf, der Konstruktor wirft
        // NotSupportedError, weil
        // isTypeSupported() nur den MIME-Typ prueft, nicht die tatsaechliche
        // Track-Konfiguration (Safari), start() wirft InvalidStateError/
        // SecurityError, oder MediaRecorder fehlt global komplett (altes iOS,
        // starteAufnahme() prueft mikrofonWirdUnterstuetzt() bewusst nicht selbst).
        // In jedem dieser Faelle ist bisher NIE eine AufnahmeSitzung entstanden,
        // also koennten stoppe()/brichAb() nie aufgerufen werden und das Mikrofon
        // bliebe offen — genau das Symptom (oranger iOS-Punkt bleibt haengen),
        // das Entscheidung 1 verhindern soll. Deshalb hier freigeben und den
        // Originalfehler unveraendert weiterwerfen, statt ihn zu verschlucken
        // oder in einen eigenen Fehlertyp zu verpacken.
        //
        // waehleMimeType() steht bewusst INNERHALB des try. Nach Spec liefert
        // isTypeSupported() nur einen Boolean und wirft nicht — aber die Zusage
        // dieser Datei lautet, dass das Mikrofon nach getUserMedia auf KEINEM
        // Pfad offen bleibt, und eine Zusage mit Ausnahme ist keine.
        const gewaehlterTyp = waehleMimeType()
        recorder = new MediaRecorder(stream, gewaehlterTyp ? { mimeType: gewaehlterTyp } : undefined)
        mimeType = recorder.mimeType || gewaehlterTyp || 'audio/webm'
        recorder.ondataavailable = (event: BlobEvent) => {
            chunks.push(event.data)
        }
        gestartetAm = Date.now()
        recorder.start()
    } catch (fehler) {
        gibMikrofonFrei(stream)
        throw fehler
    }

    let stoppenPromise: Promise<Blob> | null = null

    function stoppe(): Promise<Blob> {
        if (!stoppenPromise) {
            stoppenPromise = new Promise<Blob>((resolve, reject) => {
                recorder.onstop = () => {
                    const blob = new Blob(chunks, { type: mimeType })
                    gibMikrofonFrei(stream)
                    chunks = []
                    resolve(blob)
                }
                try {
                    recorder.stop()
                } catch (fehler) {
                    // Z.B. wenn der Nutzer die Berechtigung mitten in der Aufnahme
                    // entzogen hat und der Recorder schon von sich aus inactive
                    // wurde: stop() wirft synchron, onstop feuert dann nie, also
                    // muss das Freigeben hier explizit passieren.
                    gibMikrofonFrei(stream)
                    reject(fehler)
                }
            })
        }
        return stoppenPromise
    }

    function brichAb(): void {
        try {
            if (recorder.state !== 'inactive') {
                recorder.stop()
            }
        } catch {
            /* ignore */
        }
        chunks = []
        gibMikrofonFrei(stream)
    }

    return { mimeType, gestartetAm, stoppe, brichAb }
}
