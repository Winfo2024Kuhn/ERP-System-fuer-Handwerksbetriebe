import { Dialog } from "./ui/dialog";
import { Button } from "./ui/button";
import { Download, X } from "lucide-react";

interface ImageViewerModalProps {
    isOpen: boolean;
    onClose: () => void;
    imageUrl: string | null;
    imageName?: string;
}

export function ImageViewerModal({ isOpen, onClose, imageUrl, imageName }: ImageViewerModalProps) {
    if (!imageUrl) return null;

    const handleDownload = async () => {
        try {
            const response = await fetch(imageUrl);
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = imageName || 'bild.jpg';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error("Download failed:", error);
        }
    };

    return (
        <Dialog
            open={isOpen}
            onOpenChange={onClose}
            className="max-w-5xl w-full h-[85vh] p-0 overflow-hidden bg-zinc-950/95 border-zinc-800 flex flex-col [&>button]:hidden shadow-2xl"
        >
            {/* Custom Header */}
            <div className="flex items-center justify-between p-4 border-b border-white/10 bg-black/20 backdrop-blur-sm absolute top-0 left-0 right-0 z-10">
                <h3 className="text-white font-medium truncate max-w-[70%] drop-shadow-sm px-2">
                    {imageName || "Bildansicht"}
                </h3>
                <div className="flex items-center gap-2">
                    <Button
                        variant="ghost"
                        size="sm"
                        className="text-zinc-400 hover:text-white hover:bg-white/10 h-8 w-8 p-0 rounded-full"
                        onClick={handleDownload}
                        title="Herunterladen"
                    >
                        <Download className="w-4 h-4" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        className="text-zinc-400 hover:text-white hover:bg-rose-500/20 hover:text-rose-400 h-8 w-8 p-0 rounded-full"
                        onClick={onClose}
                        title="Schließen"
                    >
                        <X className="w-5 h-5" />
                    </Button>
                </div>
            </div>

            {/* Image Container */}
            <div className="flex-1 w-full flex items-center justify-center p-4 pt-20 pb-4 bg-transparent overflow-hidden" onClick={onClose}>
                <div
                    className="relative w-full h-full flex items-center justify-center"
                    onClick={(e) => e.stopPropagation()}
                >
                    <img
                        src={imageUrl}
                        alt={imageName || "Detailansicht"}
                        className="max-w-full max-h-full object-contain rounded-sm shadow-xl"
                        style={{ maxHeight: 'calc(85vh - 100px)' }}
                    />
                </div>
            </div>
        </Dialog>
    );
}
