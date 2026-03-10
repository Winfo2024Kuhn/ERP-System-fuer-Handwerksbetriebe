import { useState, useRef, useCallback } from 'react'
import Webcam from 'react-webcam'
import { X, Loader2, Check, ArrowRight, Plus, Trash2, Layers } from 'lucide-react'
import { jsPDF } from "jspdf"

interface ScannerModalProps {
    onClose: () => void
    onSave: (file: File) => Promise<void>
}

// Coordinate type
interface Point { x: number; y: number }

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
    const webcamRef = useRef<Webcam>(null)
    const [step, setStep] = useState<'CAMERA' | 'CROP' | 'PREVIEW'>('CAMERA')
    const [isProcessing, setIsProcessing] = useState(false)

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

    // Capture Photo
    const capture = useCallback(() => {
        if (!webcamRef.current) return
        const imageSrc = webcamRef.current.getScreenshot()
        if (imageSrc) {
            setOriginalImageSrc(imageSrc)
            setStep('CROP')
        }
    }, [])

    const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
        const img = e.currentTarget;
        const width = img.naturalWidth;
        const height = img.naturalHeight;

        // Initialize corners (TL, TR, BR, BL) with 10% margin
        setCorners([
            { x: width * 0.1, y: height * 0.1 },             // TL
            { x: width * 0.9, y: height * 0.1 },             // TR
            { x: width * 0.9, y: height * 0.9 },             // BR
            { x: width * 0.1, y: height * 0.9 }              // BL
        ]);
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
            alert("Fehler beim Verarbeiten. Bitte erneut versuchen.");
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

            // Initialize jsPDF
            const pdf = new jsPDF({
                orientation: 'p',
                unit: 'mm',
                format: 'a4'
            });

            // Process each page
            for(let i=0; i<allPages.length; i++) {
                const blob = allPages[i];
                
                // Convert Blob to Data URL
                const imgData = await new Promise<string>((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onloadend = () => resolve(reader.result as string);
                    reader.onerror = reject;
                    reader.readAsDataURL(blob);
                });

                if (i > 0) pdf.addPage();

                // Get Image Properties
                const props = pdf.getImageProperties(imgData);
                const pdfWidth = pdf.internal.pageSize.getWidth();
                const pdfHeight = (props.height * pdfWidth) / props.width;

                pdf.addImage(imgData, 'JPEG', 0, 0, pdfWidth, pdfHeight);
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
            alert("Fehler beim Erstellen des PDFs");
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
        <div className="fixed inset-0 bg-black z-50 flex flex-col safe-area-top safe-area-bottom select-none touch-none">
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
                        <Webcam
                            ref={webcamRef}
                            audio={false}
                            screenshotFormat="image/jpeg"
                            screenshotQuality={1}
                            forceScreenshotSourceSize={true}
                            videoConstraints={{
                                facingMode: 'environment',
                                width: { min: 1920, ideal: 3840, max: 4096 },
                                height: { min: 1080, ideal: 2160, max: 2160 }
                            }}
                            className="absolute inset-0 w-full h-full object-cover"
                        />
                         {/* Visual Guide Overlay (A4 approx) */}
                         <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                            <div className="w-[70%] h-[85%] border-2 border-white/30 rounded-lg"></div>
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
                            >
                                <div className="w-16 h-16 rounded-full bg-white"></div>
                            </button>
                        </div>
                    </>
                )}

                {step === 'CROP' && originalImageSrc && (
                    <div ref={containerRef} className="relative w-full max-h-full p-4 flex items-center justify-center">
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
                            className="bg-green-600 px-6 py-3 rounded-full font-bold flex items-center gap-2 flex-1 justify-center"
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