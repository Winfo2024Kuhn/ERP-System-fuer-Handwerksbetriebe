package org.example.kalkulationsprogramm.domain.einkauf;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "einkauf_mailkonto")
public class EinkaufMailkonto {
    @Id
    @Column(length = 16, nullable = false)
    private String id = "EINKAUF";

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private boolean aktiv;
    @Column(name = "from_address", length = 254, nullable = false)
    private String fromAddress = "";
    @Column(name = "from_name", length = 120, nullable = false)
    private String fromName = "";
    @Column(name = "smtp_host", length = 253, nullable = false)
    private String smtpHost = "";
    @Column(name = "smtp_port", nullable = false)
    private int smtpPort = 465;
    @Column(name = "smtp_username", length = 254, nullable = false)
    private String smtpUsername = "";
    @Column(name = "smtp_password_ciphertext", columnDefinition = "TEXT")
    private String smtpPasswordCiphertext;
    @Column(name = "smtp_tls", nullable = false, length = 16)
    private String smtpTls = "TLS";
    @Column(name = "imap_host", length = 253, nullable = false)
    private String imapHost = "";
    @Column(name = "imap_port", nullable = false)
    private int imapPort = 993;
    @Column(name = "imap_username", length = 254, nullable = false)
    private String imapUsername = "";
    @Column(name = "imap_password_ciphertext", columnDefinition = "TEXT")
    private String imapPasswordCiphertext;
    @Column(name = "imap_tls", nullable = false, length = 16)
    private String imapTls = "TLS";
    @Column(length = 255, nullable = false)
    private String inbox = "INBOX";
    @Column(length = 255, nullable = false)
    private String sent = "Sent";
    @Column(name = "letzter_abruf")
    private Instant letzterAbruf;
    @Column(name = "letzter_fehler", length = 80)
    private String letzterFehler;

    public EinkaufMailkonto() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public boolean isAktiv() { return aktiv; }
    public void setAktiv(boolean aktiv) { this.aktiv = aktiv; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String value) { fromAddress = value; }
    public String getFromName() { return fromName; }
    public void setFromName(String value) { fromName = value; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String value) { smtpHost = value; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int value) { smtpPort = value; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String value) { smtpUsername = value; }
    public String getSmtpPasswordCiphertext() { return smtpPasswordCiphertext; }
    public void setSmtpPasswordCiphertext(String value) { smtpPasswordCiphertext = value; }
    public String getSmtpTls() { return smtpTls; }
    public void setSmtpTls(String value) { smtpTls = value; }
    public String getImapHost() { return imapHost; }
    public void setImapHost(String value) { imapHost = value; }
    public int getImapPort() { return imapPort; }
    public void setImapPort(int value) { imapPort = value; }
    public String getImapUsername() { return imapUsername; }
    public void setImapUsername(String value) { imapUsername = value; }
    public String getImapPasswordCiphertext() { return imapPasswordCiphertext; }
    public void setImapPasswordCiphertext(String value) { imapPasswordCiphertext = value; }
    public String getImapTls() { return imapTls; }
    public void setImapTls(String value) { imapTls = value; }
    public String getInbox() { return inbox; }
    public void setInbox(String value) { inbox = value; }
    public String getSent() { return sent; }
    public void setSent(String value) { sent = value; }
    public Instant getLetzterAbruf() { return letzterAbruf; }
    public void setLetzterAbruf(Instant value) { letzterAbruf = value; }
    public String getLetzterFehler() { return letzterFehler; }
    public void setLetzterFehler(String value) { letzterFehler = value; }
}
