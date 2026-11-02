/**
 * Startet den Download einer Datei vom eigenen Server über einen kurzzeitig
 * eingehängten Link – die Seite bleibt dabei stehen.
 */
export function starteDownload(url: string, dateiname: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = dateiname;
    link.rel = 'noopener';
    document.body.appendChild(link);
    try {
        link.click();
    } finally {
        link.remove();
    }
}
