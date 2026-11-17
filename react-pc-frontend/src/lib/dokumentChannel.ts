/**
 * Cross-tab communication channel for document changes.
 * Uses BroadcastChannel API to notify other tabs (e.g. ProjektEditor, AnfrageEditor)
 * when a Geschäftsdokument is created or updated in the DocumentEditor.
 */

export interface DokumentChangedEvent {
    type: 'dokument-changed';
    projektId?: number;
    anfrageId?: number;
    dokumentId?: number;
    /**
     * Gesetzt, wenn ein älterer Stand wiederhergestellt wurde: Der gespeicherte Inhalt
     * ist jetzt ein anderer. Offene Editoren dieses Dokuments müssen neu laden, sonst
     * überschreibt ihr automatisches Speichern den wiederhergestellten Inhalt.
     */
    inhaltZurueckgeholt?: { standNummer: number };
}

/** Meldung „Stand X wurde wiederhergestellt“ für ein Dokument. */
export interface InhaltZurueckgeholtEreignis {
    dokumentId: number;
    projektId?: number;
    anfrageId?: number;
    standNummer: number;
}

const CHANNEL_NAME = 'dokument-changes';

let channel: BroadcastChannel | null = null;

function getChannel(): BroadcastChannel {
    if (!channel) {
        channel = new BroadcastChannel(CHANNEL_NAME);
    }
    return channel;
}

/** Broadcast that a document was created or updated */
export function notifyDokumentChanged(event: Omit<DokumentChangedEvent, 'type'>) {
    try {
        getChannel().postMessage({ ...event, type: 'dokument-changed' } as DokumentChangedEvent);
    } catch {
        // BroadcastChannel not supported or closed – silently ignore
    }
}

/** Subscribe to document-changed events from other tabs */
export function onDokumentChanged(callback: (event: DokumentChangedEvent) => void): () => void {
    const ch = getChannel();
    const handler = (e: MessageEvent<DokumentChangedEvent>) => {
        if (e.data?.type === 'dokument-changed') {
            callback(e.data);
        }
    };
    ch.addEventListener('message', handler);
    return () => ch.removeEventListener('message', handler);
}

/** Empfänger im eigenen Tab: BroadcastChannel liefert nie an den Absender selbst. */
const empfaengerImTab = new Set<(ereignis: InhaltZurueckgeholtEreignis) => void>();

/**
 * Meldet allen Tabs (auch dem eigenen), dass ein älterer Stand wiederhergestellt wurde.
 * Andere Tabs sehen es zusätzlich als gewöhnliche Dokumentänderung (Listen laden neu).
 */
export function notifyInhaltZurueckgeholt(ereignis: InhaltZurueckgeholtEreignis) {
    const { standNummer, ...bezug } = ereignis;
    notifyDokumentChanged({ ...bezug, inhaltZurueckgeholt: { standNummer } });
    [...empfaengerImTab].forEach((empfaenger) => empfaenger(ereignis));
}

/** Hört auf „Stand wiederhergestellt“ aus jedem Tab, auch dem eigenen. */
export function onInhaltZurueckgeholt(callback: (ereignis: InhaltZurueckgeholtEreignis) => void): () => void {
    empfaengerImTab.add(callback);
    const abmeldenKanal = onDokumentChanged((e) => {
        if (e.inhaltZurueckgeholt && e.dokumentId != null) {
            callback({ dokumentId: e.dokumentId, projektId: e.projektId, anfrageId: e.anfrageId, standNummer: e.inhaltZurueckgeholt.standNummer });
        }
    });
    return () => {
        empfaengerImTab.delete(callback);
        abmeldenKanal();
    };
}
