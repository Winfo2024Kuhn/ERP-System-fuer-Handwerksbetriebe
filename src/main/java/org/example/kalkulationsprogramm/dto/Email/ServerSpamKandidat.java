package org.example.kalkulationsprogramm.dto.Email;

/**
 * Eine Mail, die der Spam-Abgleich auf dem Server verschieben soll – nur das, was er dafür
 * braucht (kein Inhalt). Absender und Betreff dienen nur dem Abgleich und werden nie geloggt.
 *
 * @param zuordnungId Id der {@code EmailPostfachZuordnung}
 * @param imapOrdner  Ordner, in dem sie auf dem Server liegt
 * @param imapUid     UID in diesem Ordner, {@code null} wenn unbekannt
 * @param messageId   Message-ID der Mail – Sicherheitsabgleich, damit nie eine falsche Mail wandert
 * @param absender    gespeicherter Absender – zusätzlicher Abgleich, wenn per Suche statt UID gefunden
 * @param betreff     gespeicherter Betreff – ebenso
 */
public record ServerSpamKandidat(Long zuordnungId, String imapOrdner, Long imapUid, String messageId,
        String absender, String betreff) {

    /** Ohne Absender, Betreff und Message-ID – ein versehentlich geloggter Kandidat verrät nichts (DSGVO). */
    @Override
    public String toString() {
        return "ServerSpamKandidat[zuordnungId=" + zuordnungId + ", imapOrdner=" + imapOrdner + ", imapUid=" + imapUid + "]";
    }
}
