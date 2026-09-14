import { useToast, mobileOverlayStyle } from './ui/toast'
import { useState, useRef, useCallback, useEffect } from 'react'
import Camera, { type CameraHandle } from './Camera'
import { X, Loader2, Check, ArrowRight, Plus, Trash2, Layers, Wand2, ScanLine } from 'lucide-react'
import { jsPDF } from "jspdf"
import { detectDocumentCorners, detectDocumentCornersOnCanvasSync, preloadEdgeDetector } from '../services/DocumentEdgeDetector'
import type { Point } from '../services/scannerOverlay'
import { quadsAreClose, clearOverlay, drawOverlayQuad } from '../services/scannerOverlay'

interface ScannerModalProps {
    onClose: () => void
    onSave: (file: File) => Promise<void>
}

// --- HELPER: Matrix Solver for Homography ---
// Solves for h0..h7 in the equation:  x_src = (h0*u + h1*v + h2) / (h6*u + h7*v + 1) ...
function solveHomography(srcPts: Point[], dstPts: Point[]) {
    const a: number[][] = [];
    const b: number[] = [];
    
    for(let i=0; i<4; i++) {
        const u = dstPts[i].x;
        const v = dstPts[i].y;
        const x = srcPts[i].x;
        const y = srcPts[i].y;
        
        a.push([u, v, 1, 0, 0, 0, -x*u, -x*v]);
        a.push([0, 0, 0, u, v, 1, -y*u, -y*v]);
        b.push(x);
        b.push(y);
    }
    
    // Gaussian Elimination
    const n = 8;
    for(let i=0; i<n; i++) {
        let maxEl = Math.abs(a[i][i]);
        let maxRow = i;
        for(let k=i+1; k<n; k++) {
            if(Math.abs(a[k][i]) > maxEl) {
                maxEl = Math.abs(a[k][i]);
                maxRow = k;
            }
        }
        
        // Swap
        for(let k=i; k<n; k++) {
            const tmp = a[maxRow][k];
            a[maxRow][k] = a[i][k];
            a[i][k] = tmp;
        }
        const tmpB = b[maxRow];
        b[maxRow] = b[i];
        b[i] = tmpB;
        
        // Subtract
        for(let k=i+1; k<n; k++) {
            const c = -a[k][i] / a[i][i];
            for(let j=i; j<n; j++) {
                if(i===j) a[k][j] = 0;
                else a[k][j] += c * a[i][j];
            }
            b[k] += c * b[i];
        }
    }
    
    // Back substitution
    const x = new Array(8).fill(0);
    for(let i=n-1; i>=0; i--) {
        let sum = 0;
        for(let j=i+1; j<n; j++) {
            sum += a[i][j] * x[j];
        }
        x[i] = (b[i] - sum) / a[i][i];
    }
    
    return [...x, 1]; // Return 3x3 matrix (flattened)
}

export default function ScannerModal({ onClose, onSave }: ScannerModalProps) {
    const toast = useToast()
    const webcamRef = useRef<CameraHandle>(null)
    const [step, setStep] = useState<'CAMERA' | 'CROP' | 'PREVIEW'>('CAMERA')
    const [isProcessing, setIsProcessing] = useState(false)
    // Fehler-Text, wenn getUserMedia rejectet (Permission-Deny, kein Geraet,
    // belegte Hardware). Wird ueber dem Video-Feed eingeblendet, damit der
    // User nicht einfach auf ein schwarzes Bild starrt.
    const [cameraError, setCameraError] = useState<string | null>(null)

    // Multi-Page State
    const [pages, setPages] = useState<Blob[]>([])

    // Current Image Data
    const [originalImageSrc, setOriginalImageSrc] = useState<string | null>(null)
    const [currentProcessedBlob, setCurrentProcessedBlob] = useState<Blob | null>(null)
    const [currentPreviewUrl, setCurrentPreviewUrl] = useState<string | null>(null)

    // Cropping State
    const [corners, setCorners] = useState<Point[]>([])
    const imageRef = useRef<HTMLImageElement>(null)
    const containerRef = useRef<HTMLDivElement>(null)
    
    // Magnifier State
    interface MagnifierState {
        screenX: number
        screenY: number
        sourceX: number
        sourceY: number
    }
    const [magnifier, setMagnifier] = useState<MagnifierState | null>(null)

    // Auto-Erkennung der Dokumentkanten (jscanify + OpenCV.js, lazy geladen).
    // 'idle' = noch nicht versucht, 'detecting' = läuft, 'detected' = erkannt,
    // 'fallback' = manuelle Defaults verwendet.
    const [autoDetectStatus, setAutoDetectStatus] = useState<'idle' | 'detecting' | 'detected' | 'fallback'>('idle')

    // Live-Erkennung im Kamerabild
    // 'loading'  = OpenCV wird gerade geladen
    // 'searching'= bereit, aber gerade kein Dokument erkannt
    // 'tracking' = Dokument erkannt (slate-Overlay, neutraler "Halte still"-Zustand)
    // 'locking'  = stabile Erkennung, Auto-Capture in Kürze (rose-Overlay + Countdown)
    type LiveStatus = 'loading' | 'searching' | 'tracking' | 'locking'
    const [liveStatus, setLiveStatus] = useState<LiveStatus>('loading')
    const [autoCaptureCountdown, setAutoCaptureCountdown] = useState<number | null>(null)
    const overlayCanvasRef = useRef<HTMLCanvasElement>(null)
    const detectionCanvasRef = useRef<HTMLCanvasElement | null>(null)
    const detectionRafRef = useRef<number | null>(null)
    const lastDetectedQuadRef = useRef<Point[] | null>(null)
    const stableFramesRef = useRef(0)
    const opencvReadyRef = useRef(false)
    // Pre-erkannte Ecken aus dem Live-Tracking: werden in handleImageLoad
    // verwendet, statt die Defaults zu setzen oder erneut zu detektieren.
    // Enthält zusätzlich die Quell-Auflösung des Video-Frames, damit
    // handleImageLoad bei abweichender Screenshot-Auflösung (z.B. iOS Safari
    // ignoriert forceScreenshotSourceSize) auf die natural-Größe skalieren kann.
    const pendingCornersRef = useRef<{ quad: Point[]; sourceW: number; sourceH: number } | null>(null)
    // Race-Guard: verhindert, dass ein manueller Shutter und der Auto-Capture
    // gleichzeitig getScreenshot()+setStep auslösen.
    const captureInFlightRef = useRef(false)

    // Warm-Start: OpenCV.js schon laden, sobald der Modal aufgeht — dann
    // ist die Runtime in der Regel fertig, bevor das erste Foto da ist.
    useEffect(() => {
        preloadEdgeDetector()
        // Beobachte, ab wann window.cv tatsächlich nutzbar ist.
        let cancelled = false
        let pollTimer: ReturnType<typeof setTimeout> | null = null
        const poll = () => {
            if (cancelled) return
            const w = window as unknown as { cv?: { imread?: unknown }, jscanify?: unknown }
            if (w.cv && typeof w.cv.imread === 'function' && w.jscanify) {
                opencvReadyRef.current = true
                setLiveStatus(prev => (prev === 'loading' ? 'searching' : prev))
                return
            }
            pollTimer = setTimeout(poll, 250)
        }
        poll()
        return () => {
            cancelled = true
            if (pollTimer !== null) clearTimeout(pollTimer)
        }
    }, [])

    // Reset des Capture-Guards beim Zurückkehren zur Kamera (z.B. nach
    // "Seite hinzufügen" oder über den Zurück-Button im CROP-Schritt).
    useEffect(() => {
        if (step === 'CAMERA') captureInFlightRef.current = false
    }, [step])

    // Object-URL-Lifecycle: jeder Preview-URL aus URL.createObjectURL muss
    // wieder freigegeben werden, sonst lecken pro Multi-Page-Scan Blobs in
    // der MB-Klasse — auf einem Smartphone-Tab schnell relevant.
    useEffect(() => {
        if (!currentPreviewUrl) return
        return () => { URL.revokeObjectURL(currentPreviewUrl) }
    }, [currentPreviewUrl])

    // Capture Photo (manueller Auslöser)
    const capture = useCallback(() => {
        if (captureInFlightRef.current) return
        if (!webcamRef.current) return
        const imageSrc = webcamRef.current.getScreenshot()
        if (imageSrc) {
            captureInFlightRef.current = true
            pendingCornersRef.current = null // manuelle Aufnahme -> normale Detection
            setAutoDetectStatus('idle')
            setOriginalImageSrc(imageSrc)
            setStep('CROP')
        }
    }, [])

    // Auto-Capture: gleiche Logik, aber mit vor-erkannten Ecken aus dem
    // Live-Tracker. Die Ecken sind in Video-Pixel-Koordinaten; handleImageLoad
    // skaliert sie ggf. auf die natural-Größe des Screenshots.
    const autoCapture = useCallback((quad: Point[]) => {
        if (captureInFlightRef.current) return
        if (!webcamRef.current) return
        const video = webcamRef.current.video
        const sourceW = video?.videoWidth ?? 0
        const sourceH = video?.videoHeight ?? 0
        if (!sourceW || !sourceH) return
        const imageSrc = webcamRef.current.getScreenshot()
        if (imageSrc) {
            captureInFlightRef.current = true
            pendingCornersRef.current = { quad, sourceW, sourceH }
            setAutoDetectStatus('detected')
            setOriginalImageSrc(imageSrc)
            setStep('CROP')
        }
    }, [])

    // Live-Detection-Loop: läuft nur im CAMERA-Schritt, sobald OpenCV bereit ist.
    useEffect(() => {
        if (step !== 'CAMERA') {
            if (detectionRafRef.current !== null) {
                cancelAnimationFrame(detectionRafRef.current)
                detectionRafRef.current = null
            }
            lastDetectedQuadRef.current = null
            stableFramesRef.current = 0
            setAutoCaptureCountdown(null)
            return
        }

        // Detection-Canvas einmalig anlegen (off-screen, zum Downscalen).
        if (!detectionCanvasRef.current) {
            detectionCanvasRef.current = document.createElement('canvas')
        }

        const TICK_MS = 120                       // ~8 fps – genug für UX, schont CPU
        const SCAN_WIDTH = 480                    // downscale auf 480 px breit
        const STABLE_FRAMES_REQUIRED = 6          // ~0.7 s ruhig halten
        const MOVEMENT_TOL_PERCENT = 0.025        // 2.5 % Bildbreite max. Bewegung

        let lastTick = 0
        let cancelled = false
        // Snapshot des Overlay-Canvas-Refs für die Cleanup-Funktion. Beim
        // Step-Wechsel ist der Canvas-Knoten gleich unmounted; den Wert von
        // damals zu lesen, ist sauberer als `ref.current` zur Cleanup-Zeit.
        const overlayAtMount = overlayCanvasRef.current

        const tick = (now: number) => {
            if (cancelled) return
            detectionRafRef.current = requestAnimationFrame(tick)
            if (now - lastTick < TICK_MS) return
            lastTick = now

            if (!opencvReadyRef.current) return
            const video = webcamRef.current?.video
            if (!video || video.readyState < 2 || !video.videoWidth || !video.videoHeight) return

            const scanCanvas = detectionCanvasRef.current
            if (!scanCanvas) return
            const scale = SCAN_WIDTH / video.videoWidth
            const scanH = Math.round(video.videoHeight * scale)
            if (scanCanvas.width !== SCAN_WIDTH || scanCanvas.height !== scanH) {
                scanCanvas.width = SCAN_WIDTH
                scanCanvas.height = scanH
            }
            const sctx = scanCanvas.getContext('2d')
            if (!sctx) return
            sctx.drawImage(video, 0, 0, SCAN_WIDTH, scanH)

            const overlay = overlayCanvasRef.current
            const detected = detectDocumentCornersOnCanvasSync(scanCanvas, 0.18)

            if (!detected) {
                lastDetectedQuadRef.current = null
                stableFramesRef.current = 0
                setAutoCaptureCountdown(prev => prev === null ? prev : null)
                setLiveStatus(prev => prev === 'searching' ? prev : 'searching')
                clearOverlay(overlay)
                return
            }

            // Auf Video-Pixel-Koordinaten zurückskalieren – das ist auch die
            // Auflösung des Screenshots (forceScreenshotSourceSize).
            const inv = 1 / scale
            const quad: Point[] = detected.map(p => ({ x: p.x * inv, y: p.y * inv }))

            // Stabilität prüfen
            const tolPx = video.videoWidth * MOVEMENT_TOL_PERCENT
            const prev = lastDetectedQuadRef.current
            if (prev && quadsAreClose(prev, quad, tolPx)) {
                stableFramesRef.current += 1
            } else {
                stableFramesRef.current = 0
            }
            lastDetectedQuadRef.current = quad

            const isLocking = stableFramesRef.current >= STABLE_FRAMES_REQUIRED
            drawOverlayQuad(overlay, video.videoWidth, video.videoHeight, quad, isLocking)

            if (isLocking) {
                setLiveStatus(prev => prev === 'locking' ? prev : 'locking')
                setAutoCaptureCountdown(prev => prev === null ? prev : null)
                // Stoppe den Loop und löse die Aufnahme aus
                if (detectionRafRef.current !== null) {
                    cancelAnimationFrame(detectionRafRef.current)
                    detectionRafRef.current = null
                }
                cancelled = true
                autoCapture(quad)
            } else {
                setLiveStatus(prev => prev === 'tracking' ? prev : 'tracking')
                const remaining = STABLE_FRAMES_REQUIRED - stableFramesRef.current
                setAutoCaptureCountdown(prev => prev === remaining ? prev : remaining)
            }
        }

        detectionRafRef.current = requestAnimationFrame(tick)
        return () => {
            cancelled = true
            if (detectionRafRef.current !== null) {
                cancelAnimationFrame(detectionRafRef.current)
                detectionRafRef.current = null
            }
            clearOverlay(overlayAtMount)
        }
    }, [step, autoCapture])

    const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
        const img = e.currentTarget;
        const width = img.naturalWidth;
        const height = img.naturalHeight;

        // Aus Auto-Capture vor-erkannte Ecken? Direkt verwenden und
        // weitere Detection sparen. Falls iOS Safari den Screenshot in einer
        // anderen Auflösung als videoWidth/Height geliefert hat (kein
        // forceScreenshotSourceSize-Support), skalieren wir die Quad-Werte
        // entsprechend dem Verhältnis natural/source.
        const pre = pendingCornersRef.current
        pendingCornersRef.current = null
        if (pre && pre.quad.length === 4 && pre.sourceW > 0 && pre.sourceH > 0) {
            const sx = width / pre.sourceW
            const sy = height / pre.sourceH
            const scaled = pre.quad.map(p => ({
                x: Math.max(0, Math.min(width, p.x * sx)),
                y: Math.max(0, Math.min(height, p.y * sy)),
            }))
            const valid = scaled.every(p => p.x >= 0 && p.y >= 0 && p.x <= width && p.y <= height)
            if (valid) {
                setCorners(scaled)
                setAutoDetectStatus('detected')
                return
            }
        }

        // Erst mit Defaults (10 % Rand) initialisieren, damit der User sofort
        // etwas sieht. Die Auto-Erkennung läuft asynchron und überschreibt
        // die Ecken, sobald sie ein gutes Ergebnis hat.
        setCorners([
            { x: width * 0.1, y: height * 0.1 },             // TL
            { x: width * 0.9, y: height * 0.1 },             // TR
            { x: width * 0.9, y: height * 0.9 },             // BR
            { x: width * 0.1, y: height * 0.9 }              // BL
        ]);

        setAutoDetectStatus('detecting')
        detectDocumentCorners(img).then(detected => {
            // Falls der User zwischenzeitlich schon eine Ecke gepackt hat,
            // nicht mehr drüberschreiben.
            setDraggingIndex(currentDragging => {
                if (currentDragging === null && detected) {
                    setCorners(detected)
                    setAutoDetectStatus('detected')
                } else {
                    setAutoDetectStatus('fallback')
                }
                return currentDragging
            })
        }).catch(() => {
            setAutoDetectStatus('fallback')
        })
    };

    // DRAG LOGIC
    const [draggingIndex, setDraggingIndex] = useState<number | null>(null);

    const handleTouchStart = (index: number) => setDraggingIndex(index);
    
    const handleTouchMove = (e: React.TouchEvent | React.MouseEvent) => {
        if (draggingIndex === null || !imageRef.current) return;

        // 1. Get client coordinates
        let clientX, clientY;
        if ('touches' in e) {
            clientX = e.touches[0].clientX;
            clientY = e.touches[0].clientY;
        } else {
            clientX = (e as React.MouseEvent).clientX;
            clientY = (e as React.MouseEvent).clientY;
        }

        // 2. Map to Image Natural Coordinates
        const img = imageRef.current;
        const rect = img.getBoundingClientRect();
        
        // Scale factor (Natural / Displayed)
        const scaleX = img.naturalWidth / rect.width;
        const scaleY = img.naturalHeight / rect.height;

        // Relative to image element
        const relX = clientX - rect.left;
        const relY = clientY - rect.top;

        // Calculate source pixel coordinates
        // Clamp to image bounds
        const srcX = Math.max(0, Math.min(img.naturalWidth, relX * scaleX));
        const srcY = Math.max(0, Math.min(img.naturalHeight, relY * scaleY));

        // 3. Update Magnifier State
        setMagnifier({
            screenX: clientX,
            screenY: clientY - 80,
            sourceX: srcX,
            sourceY: srcY
        });

        // 4. Update Corner Position
        setCorners(prev => {
            const newCorners = [...prev];
            newCorners[draggingIndex] = { x: srcX, y: srcY };
            return newCorners;
        });
    };

    const handleTouchEnd = () => {
        setDraggingIndex(null);
        setMagnifier(null); // Hide magnifier
    };

    // Perform Perspective Crop
    const performCrop = async () => {
        if (!imageRef.current || corners.length !== 4) return;
        setIsProcessing(true);

        // Delay to allow UI render (spinner)
        await new Promise(resolve => setTimeout(resolve, 100));

        try {
            const img = imageRef.current;
            const [tl, tr, br, bl] = corners;

            // 1. Calculate Target Dimensions
            const widthTop = Math.hypot(tr.x - tl.x, tr.y - tl.y);
            const widthBottom = Math.hypot(br.x - bl.x, br.y - bl.y);
            const heightLeft = Math.hypot(bl.x - tl.x, bl.y - tl.y);
            const heightRight = Math.hypot(br.x - tr.x, br.y - tr.y);

            const targetWidth = Math.max(widthTop, widthBottom);
            const targetHeight = Math.max(heightLeft, heightRight);

            // Cap output resolution (Increased for quality)
            // 2480px approx A4 @ 200dpi (good for AI reading)
            const MAX_OUTPUT_DIM = 2480; 
            let outputScale = 1;
            if(targetWidth > MAX_OUTPUT_DIM || targetHeight > MAX_OUTPUT_DIM) {
                outputScale = Math.min(MAX_OUTPUT_DIM / targetWidth, MAX_OUTPUT_DIM / targetHeight);
            }

            const finalW = Math.floor(targetWidth * outputScale);
            const finalH = Math.floor(targetHeight * outputScale);

            // 2. Prepare Source Data
            // Max source dimension 3500px (approx 8-10MP source preserved)
            const MAX_SRC_DIM = 3500;
            let srcScale = 1;
            if (img.naturalWidth > MAX_SRC_DIM || img.naturalHeight > MAX_SRC_DIM) {
                srcScale = Math.min(MAX_SRC_DIM / img.naturalWidth, MAX_SRC_DIM / img.naturalHeight);
            }
            
            const srcW = Math.floor(img.naturalWidth * srcScale);
            const srcH = Math.floor(img.naturalHeight * srcScale);

            const srcCanvas = document.createElement('canvas');
            srcCanvas.width = srcW;
            srcCanvas.height = srcH;
            const srcCtx = srcCanvas.getContext('2d');
            if(!srcCtx) throw new Error("Context error");
            
            srcCtx.drawImage(img, 0, 0, srcW, srcH);
            const srcImageData = srcCtx.getImageData(0, 0, srcW, srcH);
            const srcData = srcImageData.data;

            // 3. Prepare Destination
            const destCanvas = document.createElement('canvas');
            destCanvas.width = finalW;
            destCanvas.height = finalH;
            const destCtx = destCanvas.getContext('2d');
            if(!destCtx) throw new Error("Context error");
            const destImageData = destCtx.createImageData(finalW, finalH);
            const destData = destImageData.data;

            // 4. Compute Homography Matrix
            const dstPoints = [
                { x: 0, y: 0 },
                { x: finalW, y: 0 },
                { x: finalW, y: finalH },
                { x: 0, y: finalH }
            ];
            
            const scaledCorners = corners.map(p => ({ x: p.x * srcScale, y: p.y * srcScale }));
            const H = solveHomography(scaledCorners, dstPoints);

            // 5. Pixel Iteration
            for (let v = 0; v < finalH; v++) {
                for (let u = 0; u < finalW; u++) {
                    const denom = H[6]*u + H[7]*v + 1;
                    const srcX = (H[0]*u + H[1]*v + H[2]) / denom;
                    const srcY = (H[3]*u + H[4]*v + H[5]) / denom;

                    const x0 = Math.floor(srcX);
                    const y0 = Math.floor(srcY);
                    const dx = srcX - x0;
                    const dy = srcY - y0;

                    if (x0 >= 0 && x0 < srcW - 1 && y0 >= 0 && y0 < srcH - 1) {
                        const i00 = (y0 * srcW + x0) * 4;
                        const i10 = (y0 * srcW + (x0+1)) * 4;
                        const i01 = ((y0+1) * srcW + x0) * 4;
                        const i11 = ((y0+1) * srcW + (x0+1)) * 4;

                        const destIndex = (v * finalW + u) * 4;

                        for (let c = 0; c < 3; c++) {
                             const val00 = srcData[i00 + c];
                             const val10 = srcData[i10 + c];
                             const val01 = srcData[i01 + c];
                             const val11 = srcData[i11 + c];
                             const val0 = val00 * (1 - dx) + val10 * dx;
                             const val1 = val01 * (1 - dx) + val11 * dx;
                             destData[destIndex + c] = val0 * (1 - dy) + val1 * dy;
                        }
                        destData[destIndex + 3] = 255;
                    }
                }
            }

            destCtx.putImageData(destImageData, 0, 0);

            // Export to blob with high quality
            destCanvas.toBlob((blob) => {
                if (blob) {
                    setCurrentProcessedBlob(blob);
                    setCurrentPreviewUrl(URL.createObjectURL(blob));
                    setStep('PREVIEW');
                }
                setIsProcessing(false);
            }, 'image/jpeg', 0.95); // High quality JPEG

        } catch (e) {
            console.error("Crop failed", e);
            toast.error("Fehler beim Verarbeiten. Bitte erneut versuchen.");
            setIsProcessing(false);
        }
    };

    // Add another page
    const handleAddPage = () => {
        if(currentProcessedBlob) {
            setPages(prev => [...prev, currentProcessedBlob]);
            setCurrentProcessedBlob(null);
            setCurrentPreviewUrl(null);
            setStep('CAMERA');
        }
    };

    // Save Logic (Generate PDF from ALL pages)
    const handleFinish = async () => {
        if (!currentProcessedBlob && pages.length === 0) return;

        setIsProcessing(true);

        try {
            // Combine previously saved pages plus current one (if exists)
            const allPages = [...pages];
            if (currentProcessedBlob) {
                allPages.push(currentProcessedBlob);
            }

            if (allPages.length === 0) return;

            // Bilder dekodieren, um pro Seite das Seitenverhältnis zu kennen.
            // Wir setzen das PDF-Seitenformat pro Seite exakt auf das AR des
            // gecroppten Belegs — sonst werden Sonderformate (Tankbons,
            // schmale Kassenzettel) auf A4-Breite gestreckt und der untere
            // Teil abgeschnitten. Apple-Scanner verhält sich genauso.
            const pageData = await Promise.all(allPages.map(async (blob, idx) => {
                const dataUrl = await new Promise<string>((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onloadend = () => resolve(reader.result as string);
                    reader.onerror = () => reject(new Error(`FileReader failed for page ${idx + 1}`));
                    reader.readAsDataURL(blob);
                });
                const dims = await new Promise<{ w: number; h: number }>((resolve, reject) => {
                    const probe = new Image();
                    probe.onload = () => resolve({ w: probe.naturalWidth, h: probe.naturalHeight });
                    probe.onerror = () => reject(new Error(`Image decode failed for page ${idx + 1}`));
                    probe.src = dataUrl;
                });
                return { dataUrl, ...dims };
            }));

            // Längsseite = 297 mm (A4-Höhe) als Referenz, kurze Seite folgt
            // dem AR. Die Bild-Pixel bleiben verlustfrei eingebettet — es
            // ändert sich nur das logische Seitenformat im PDF.
            //
            // jsPDF-Eigenheit: `format`-Array MUSS in Hochformat-Reihenfolge
            // angegeben werden ([short, long]), die `orientation` entscheidet
            // dann über Quer-/Hochformat. Wer [long, short] + 'l' übergibt,
            // bekommt eine intern transponierte Seite — `addImage(0,0,w,h)`
            // zeichnet dann ggf. außerhalb der Seitengrenzen.
            const MAX_MM = 297;
            const pageGeometry = (w: number, h: number) => {
                const long = MAX_MM;
                const short = (Math.min(w, h) / Math.max(w, h)) * MAX_MM;
                const landscape = w >= h;
                return {
                    format: [short, long] as [number, number],   // immer Hochformat
                    orientation: (landscape ? 'l' : 'p') as 'l' | 'p',
                    imgW: landscape ? long : short,              // tatsächliche Bildmaße
                    imgH: landscape ? short : long,              // im jeweiligen Modus
                };
            };

            const first = pageGeometry(pageData[0].w, pageData[0].h);
            const pdf = new jsPDF({
                orientation: first.orientation,
                unit: 'mm',
                format: first.format,
            });
            pdf.addImage(pageData[0].dataUrl, 'JPEG', 0, 0, first.imgW, first.imgH);

            for (let i = 1; i < pageData.length; i++) {
                const g = pageGeometry(pageData[i].w, pageData[i].h);
                pdf.addPage(g.format, g.orientation);
                pdf.addImage(pageData[i].dataUrl, 'JPEG', 0, 0, g.imgW, g.imgH);
            }

            // Generate PDF Blob
            const pdfBlob = pdf.output('blob');
            const filename = `Scan_${new Date().toISOString().replace(/[:.]/g, '-')}.pdf`;
            const file = new File([pdfBlob], filename, { type: 'application/pdf' });

            // Pass to Parent
            await onSave(file);
            onClose();

        } catch (e) {
            console.error("PDF Generation failed", e);
            toast.error("Fehler beim Erstellen des PDFs");
        } finally {
            setIsProcessing(false);
        }
    };

    // Helper for Corner Style
    const getCornerStyle = (index: number) => {
        if (!imageRef.current || !corners[index]) return {};
        const img = imageRef.current;
        return {
            left: `${(corners[index].x / img.naturalWidth) * 100}%`,
            top: `${(corners[index].y / img.naturalHeight) * 100}%`
        };
    };

    // Magnifier Settings
    const ZOOM = 1.15;
    const MAG_SIZE = 96; 
    const MAG_RADIUS = MAG_SIZE / 2;

    return (
        <div style={mobileOverlayStyle} className="fixed inset-0 bg-black z-50 flex flex-col safe-area-top safe-area-bottom select-none touch-none">
            {/* Header */}
            <div className="flex justify-between items-center p-4 bg-black/80 text-white z-10">
                <button onClick={onClose}><X /></button>
                <div className="flex flex-col items-center">
                    <span className="font-bold">
                        {step === 'CAMERA' ? `Seite ${pages.length + 1} scannen` : step === 'CROP' ? 'Zuschneiden' : 'Vorschau'}
                    </span>
                    {step === 'CAMERA' && (
                        <span className="text-xs text-rose-400 font-semibold mt-1">
                            Reihenfolge: 1. Scan = 1. Seite
                        </span>
                    )}
                </div>
                <div className="w-6"></div>
            </div>

            {/* Content */}
            <div
                className="flex-1 relative bg-black flex items-center justify-center overflow-hidden"
                onMouseMove={(e) => step === 'CROP' && handleTouchMove(e)}
                onMouseUp={handleTouchEnd}
                onTouchMove={(e) => step === 'CROP' && handleTouchMove(e)}
                onTouchEnd={handleTouchEnd}
            >
                {step === 'CAMERA' && (
                    <>
                        <Camera
                            ref={webcamRef}
                            screenshotQuality={1}
                            videoConstraints={{
                                facingMode: 'environment',
                                width: { min: 1920, ideal: 3840, max: 4096 },
                                height: { min: 1080, ideal: 2160, max: 2160 }
                            }}
                            className="absolute inset-0 w-full h-full object-cover"
                            onError={(err) => {
                                const msg = err instanceof Error ? err.message : String(err)
                                const isPermission = /denied|not allowed|notallowed/i.test(msg)
                                const message = isPermission
                                        ? 'Kein Zugriff auf die Kamera. Bitte in den iOS-Einstellungen unter Safari → Kamera erlauben und die App neu starten.'
                                        : `Kamera konnte nicht gestartet werden: ${msg}`
                                setCameraError(message)
                                toast.error(message)
                            }}
                        />

                        {cameraError && (
                            <div className="absolute inset-0 z-30 flex items-center justify-center p-6 bg-black/80">
                                <div className="bg-rose-50 border border-rose-200 rounded-2xl p-5 max-w-sm text-center">
                                    <p className="text-rose-800 font-semibold mb-2">Kamera nicht verfügbar</p>
                                    <p className="text-sm text-slate-700">{cameraError}</p>
                                </div>
                            </div>
                        )}

                        {/* Live-Erkennungs-Overlay: Canvas hat dieselbe object-cover-
                            Geometrie wie das Video, daher decken sich die gezeichneten
                            Polygone exakt mit den sichtbaren Pixeln. */}
                        <canvas
                            ref={overlayCanvasRef}
                            className="absolute inset-0 w-full h-full object-cover pointer-events-none z-10"
                            aria-hidden="true"
                        />

                        {/* Statischer A4-Rahmen nur, solange noch nicht live erkannt wird */}
                        {(liveStatus === 'loading' || liveStatus === 'searching') && (
                            <div className="absolute inset-0 pointer-events-none flex items-center justify-center z-10">
                                <div className="w-[70%] h-[85%] border-2 border-white/30 rounded-lg"></div>
                            </div>
                        )}

                        {/* Live-Status-Pille oben. aria-live="polite" für alle Übergänge:
                            "assertive" wäre semantisch passender für die Locking-Phase,
                            würde aber als dynamischer Ausdruck gegen die jsx-a11y/
                            aria-proptypes-Regel verstoßen. Die Locking-Phase ist mit
                            ~200 ms ohnehin so kurz, dass "polite" ausreicht. */}
                        <div
                            className="absolute top-3 left-1/2 -translate-x-1/2 z-20 pointer-events-none"
                            role="status"
                            aria-live="polite"
                            aria-atomic="true"
                        >
                            {liveStatus === 'loading' && (
                                <div className="bg-black/70 text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg">
                                    <Loader2 className="w-3 h-3 animate-spin" />
                                    Live-Erkennung lädt…
                                </div>
                            )}
                            {liveStatus === 'searching' && (
                                <div className="bg-black/60 text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg">
                                    <ScanLine className="w-3 h-3" />
                                    Beleg ins Bild halten
                                </div>
                            )}
                            {liveStatus === 'tracking' && (
                                <div className="bg-slate-700/90 text-rose-200 text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg font-semibold">
                                    <ScanLine className="w-3 h-3" />
                                    Halten… {autoCaptureCountdown !== null && autoCaptureCountdown > 0 ? `(${autoCaptureCountdown})` : ''}
                                </div>
                            )}
                            {liveStatus === 'locking' && (
                                <div className="bg-rose-600/95 text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg font-semibold">
                                    <Check className="w-3 h-3" />
                                    Auto-Aufnahme!
                                </div>
                            )}
                        </div>

                        <div className="absolute bottom-12 left-0 right-0 flex flex-col gap-4 items-center justify-center z-20">
                            {pages.length > 0 && (
                                <div className="bg-black/50 px-3 py-1 rounded-full flex items-center gap-2 mb-2">
                                    <Layers className="w-4 h-4 text-white" />
                                    <span className="text-white text-sm">{pages.length} Seiten bereit</span>
                                </div>
                            )}
                            <button
                                onClick={capture}
                                className="w-20 h-20 rounded-full border-4 border-white bg-white/20 active:bg-white/50 transition-all flex items-center justify-center"
                                aria-label="Foto manuell aufnehmen"
                            >
                                <div className="w-16 h-16 rounded-full bg-white"></div>
                            </button>
                        </div>
                    </>
                )}

                {step === 'CROP' && originalImageSrc && (
                    <div ref={containerRef} className="relative w-full max-h-full p-4 flex items-center justify-center">
                        {/* Status-Pille zur Auto-Erkennung */}
                        {autoDetectStatus !== 'idle' && (
                            <div className="absolute top-2 left-1/2 -translate-x-1/2 z-30 pointer-events-none">
                                {autoDetectStatus === 'detecting' && (
                                    <div className="bg-black/70 text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg">
                                        <Loader2 className="w-3 h-3 animate-spin" />
                                        Ecken werden erkannt…
                                    </div>
                                )}
                                {autoDetectStatus === 'detected' && (
                                    <div className="bg-rose-600/90 text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg">
                                        <Wand2 className="w-3 h-3" />
                                        Ecken automatisch erkannt
                                    </div>
                                )}
                                {autoDetectStatus === 'fallback' && (
                                    <div className="bg-slate-700/90 text-rose-200 text-xs px-3 py-1.5 rounded-full flex items-center gap-2 shadow-lg">
                                        Bitte Ecken manuell anpassen
                                    </div>
                                )}
                            </div>
                        )}
                        <div className="relative inline-block">
                            <img
                                ref={imageRef}
                                src={originalImageSrc}
                                onLoad={handleImageLoad}
                                className="max-h-[80vh] max-w-full object-contain pointer-events-none" 
                            />

                            <svg className="absolute inset-0 w-full h-full pointer-events-none">
                                <polygon
                                    points={corners.map(p => {
                                        if (!imageRef.current) return '0,0';
                                        const w = imageRef.current.clientWidth;
                                        const h = imageRef.current.clientHeight;
                                        const rx = w / imageRef.current.naturalWidth;
                                        const ry = h / imageRef.current.naturalHeight;
                                        return `${p.x * rx},${p.y * ry}`;
                                    }).join(' ')}
                                    fill="rgba(220, 38, 38, 0.2)"
                                    stroke="#e11d48"
                                    strokeWidth="2"
                                />
                            </svg>

                            {corners.map((_, i) => (
                                <div
                                    key={i}
                                    style={getCornerStyle(i)}
                                    className={`absolute w-12 h-12 -ml-6 -mt-6 flex items-center justify-center z-20 touch-none cursor-move ${ 
                                        draggingIndex === i ? 'scale-110' : ''
                                    } transition-transform`}
                                    onMouseDown={(e) => { e.preventDefault(); handleTouchStart(i); }}
                                    onTouchStart={() => { handleTouchStart(i); }}
                                >
                                    <div className="absolute inset-0 bg-transparent" />
                                    <div className={`w-5 h-5 bg-rose-600 rounded-full border-2 border-white shadow-lg flex items-center justify-center ${ 
                                        draggingIndex === i ? 'bg-rose-500 ring-4 ring-rose-300/50' : ''
                                    }`}>
                                        <div className="w-2 h-2 bg-white rounded-full" />
                                    </div>
                                </div>
                            ))}
                        </div>

                        {magnifier && originalImageSrc && (
                            <div 
                                className="fixed rounded-full border-4 border-white shadow-2xl overflow-hidden pointer-events-none z-50 bg-black"
                                style={{
                                    width: MAG_SIZE,
                                    height: MAG_SIZE,
                                    left: magnifier.screenX - MAG_RADIUS,
                                    top: magnifier.screenY - MAG_RADIUS,
                                    boxShadow: '0 10px 40px rgba(0,0,0,0.5), inset 0 0 0 2px rgba(255,255,255,0.5)'
                                }}
                            >
                                <div 
                                    style={{
                                        width: '100%',
                                        height: '100%',
                                        backgroundImage: `url(${originalImageSrc})`,
                                        backgroundRepeat: 'no-repeat',
                                        backgroundPosition: `${MAG_RADIUS - (magnifier.sourceX * ZOOM)}px ${MAG_RADIUS - (magnifier.sourceY * ZOOM)}px`,
                                        backgroundSize: `${(imageRef.current?.naturalWidth || 0) * ZOOM}px ${(imageRef.current?.naturalHeight || 0) * ZOOM}px`
                                    }}
                                />
                                <div className="absolute inset-0 flex items-center justify-center opacity-70">
                                    <div className="w-0.5 h-3 bg-rose-500"></div>
                                    <div className="h-0.5 w-3 bg-rose-500 absolute"></div>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {step === 'PREVIEW' && currentPreviewUrl && (
                    <div className="p-4 w-full h-full flex flex-col items-center justify-center">
                        <img src={currentPreviewUrl} className="max-w-full max-h-[75vh] shadow-xl border border-white/10 mb-4" />
                        <span className="text-gray-400 text-sm">Seite {pages.length + 1} bereit</span>
                    </div>
                )}
            </div>

            {/* Footer */}
            <div className="p-6 bg-black text-white flex justify-between items-center gap-4">
                {step === 'CROP' && (
                    <>
                        <button onClick={() => setStep('CAMERA')} className="text-sm text-gray-400">Zurück</button>
                        <div className="flex-1"></div>
                        <button
                            onClick={performCrop}
                            disabled={isProcessing}
                            className="bg-rose-600 px-6 py-2 rounded-full font-bold flex items-center gap-2"
                        >
                            {isProcessing ? <Loader2 className="animate-spin" /> : <ArrowRight />}
                            Weiter
                        </button>
                    </>
                )}
                {step === 'PREVIEW' && (
                    <>
                        <button 
                            onClick={() => { setStep('CROP'); setCurrentPreviewUrl(null); }} 
                            className="p-2 text-gray-400 hover:text-white"
                        >
                           <Trash2 className="w-6 h-6" />
                        </button>
                        
                        <button
                            onClick={handleAddPage}
                            className="bg-slate-700 px-4 py-3 rounded-full font-bold flex items-center gap-2 flex-1 justify-center"
                        >
                            <Plus className="w-5 h-5" />
                            Seite hinzufügen
                        </button>

                        <button
                            onClick={handleFinish}
                            disabled={isProcessing}
                            className="bg-rose-600 hover:bg-rose-700 text-white border border-rose-600 px-6 py-3 rounded-full font-bold flex items-center gap-2 flex-1 justify-center"
                        >
                            {isProcessing ? <Loader2 className="animate-spin" /> : <Check />}
                            Fertig
                        </button>
                    </>
                )}
            </div>
        </div>
    )
}