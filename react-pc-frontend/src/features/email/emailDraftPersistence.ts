export interface EmailDraftContent {
    recipient: string;
    cc: string;
    subject: string;
    body: string;
    fromAddress: string | null;
    replyEmailId: number | null;
    projektId: number | null;
    anfrageId: number | null;
    geschaeftsdokument: boolean;
}

export interface EmailDraftSnapshot {
    content: EmailDraftContent;
    files: File[];
}

interface StoredDraft extends EmailDraftContent {
    id: number;
    attachments?: { id: number; filename: string; contentType: string; size: number }[];
}

/** One ordered writer per open composer, including its in-flight creation. */
export class EmailDraftSession {
    id: number | null;
    private pending: Promise<void> = Promise.resolve();
    private paused = false;
    private removed = false;

    constructor(id?: number) { this.id = id ?? null; }

    save(snapshot: EmailDraftSnapshot): Promise<void> {
        if (this.paused || this.removed) return Promise.resolve();
        const operation = this.pending.catch(() => undefined).then(async () => {
            if (this.paused || this.removed) return;
            const data = new FormData();
            data.append('dto', new Blob([JSON.stringify(snapshot.content)], { type: 'application/json' }));
            snapshot.files.forEach(file => data.append('attachments', file));
            const response = await fetch(this.id ? `/api/emails/drafts/${this.id}` : '/api/emails/drafts', {
                method: this.id ? 'PUT' : 'POST', body: data,
            });
            if (!response.ok) {
                const error = await response.json().catch(() => null) as { message?: string } | null;
                throw new Error(error?.message || 'Entwurf konnte nicht gespeichert werden. Bitte erneut versuchen.');
            }
            const saved = await response.json() as { id: number };
            if (!Number.isSafeInteger(saved.id) || saved.id <= 0) throw new Error('Entwurf wurde nicht bestätigt. Bitte erneut speichern.');
            this.id = saved.id;
        });
        this.pending = operation;
        return operation;
    }

    async pause(): Promise<void> {
        this.paused = true;
        // A failed autosave must not prevent sending the still-intact local message.
        await this.pending.catch(() => undefined);
    }

    resume() { if (!this.removed) this.paused = false; }

    /** The send endpoint has already removed the draft in its own transaction. */
    async complete(): Promise<void> {
        await this.pause();
        this.removed = true;
        this.id = null;
    }

    async remove(): Promise<void> {
        await this.pause();
        this.removed = true;
        if (!this.id) return;
        const response = await fetch(`/api/emails/drafts/${this.id}`, { method: 'DELETE' });
        if (!response.ok && response.status !== 404) {
            throw new Error('Die E-Mail wurde gesendet, aber der Entwurf konnte nicht gelöscht werden. Bitte den Entwurf in der Liste löschen.');
        }
        this.id = null;
    }
}

export async function loadEmailDraft(id: number): Promise<EmailDraftSnapshot> {
    const response = await fetch(`/api/emails/drafts/${id}`);
    if (!response.ok) throw new Error('Entwurf konnte nicht geladen werden. Bitte erneut versuchen.');
    const draft = await response.json() as StoredDraft;
    const files = await Promise.all((draft.attachments ?? []).map(async attachment => {
        const download = await fetch(`/api/emails/drafts/${id}/attachments/${attachment.id}`);
        if (!download.ok) throw new Error(`Anhang „${attachment.filename}“ konnte nicht geladen werden. Bitte erneut versuchen.`);
        const bytes = await download.arrayBuffer();
        if (bytes.byteLength !== attachment.size) throw new Error(`Anhang „${attachment.filename}“ ist unvollständig. Bitte erneut laden.`);
        return new File([new Uint8Array(bytes)], attachment.filename, { type: attachment.contentType });
    }));
    return { content: draft, files };
}
