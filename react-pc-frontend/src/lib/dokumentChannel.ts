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
