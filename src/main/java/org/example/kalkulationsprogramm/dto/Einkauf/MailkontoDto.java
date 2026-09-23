package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.Instant;

public final class MailkontoDto {
    private MailkontoDto() {}

    public enum Verschluesselung { TLS, STARTTLS }

    public record ServerZugang(String host, int port, String username, String password,
            Verschluesselung tls) {
        @Override
        public String toString() {
            return "ServerZugang[host=" + host + ", port=" + port + ", username=" + username
                    + ", password=[geschützt], tls=" + tls + "]";
        }
    }

    public record Update(Long version, boolean aktiv, String fromAddress, String fromName,
            String smtpHost, int smtpPort, String smtpUsername, Verschluesselung smtpTls,
            String imapHost, int imapPort, String imapUsername, Verschluesselung imapTls,
            String inbox, String sent, String smtpPassword, String imapPassword) {
        @Override
        public String toString() {
            return "Update[version=" + version + ", aktiv=" + aktiv + ", fromAddress=" + fromAddress
                    + ", fromName=" + fromName + ", smtpHost=" + smtpHost + ", smtpPort=" + smtpPort
                    + ", smtpUsername=" + smtpUsername + ", smtpTls=" + smtpTls + ", imapHost=" + imapHost
                    + ", imapPort=" + imapPort + ", imapUsername=" + imapUsername + ", imapTls=" + imapTls
                    + ", inbox=" + inbox + ", sent=" + sent + ", smtpPassword=[geschützt], imapPassword=[geschützt]]";
        }
    }

    public record Response(String id, long version, boolean aktiv, String fromAddress, String fromName,
            String smtpHost, int smtpPort, String smtpUsername, Verschluesselung smtpTls,
            String imapHost, int imapPort, String imapUsername, Verschluesselung imapTls,
            String inbox, String sent, boolean smtpPasswordSet, boolean imapPasswordSet,
            Instant letzterAbruf, String letzterFehler) {}
}
