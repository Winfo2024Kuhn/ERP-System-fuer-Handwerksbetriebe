package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "einkauf_datei", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_datei_sha256", columnNames = "sha256"))
public class EinkaufDatei {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
    @Column(name = "gespeicherter_name", length = 36)
    private String gespeicherterName;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "mime_typ", nullable = false, length = 100)
    private String mimeTyp;
    @Column(name = "byte_anzahl", nullable = false)
    private long byteAnzahl;
    @Column(name = "email_attachment_id")
    private Long emailAttachmentId;
    @Column(name = "lieferant_dokument_id")
    private Long lieferantDokumentId;

    protected EinkaufDatei() {}
    public EinkaufDatei(String sha256, String gespeicherterName, String originalName, String mimeTyp, long byteAnzahl) {
        this.sha256 = sha256; this.gespeicherterName = gespeicherterName; this.originalName = originalName;
        this.mimeTyp = mimeTyp; this.byteAnzahl = byteAnzahl;
    }
    public EinkaufDatei(String sha256, String gespeicherterName, String originalName, String mimeTyp, long byteAnzahl,
            Long emailAttachmentId, Long lieferantDokumentId) {
        this(sha256, gespeicherterName, originalName, mimeTyp, byteAnzahl);
        this.emailAttachmentId = emailAttachmentId;
        this.lieferantDokumentId = lieferantDokumentId;
    }
    public Long getId() { return id; }
    public String getSha256() { return sha256; }
    public String getGespeicherterName() { return gespeicherterName; }
    public String getOriginalName() { return originalName; }
    public String getMimeTyp() { return mimeTyp; }
    public long getByteAnzahl() { return byteAnzahl; }
    public Long getEmailAttachmentId() { return emailAttachmentId; }
    public Long getLieferantDokumentId() { return lieferantDokumentId; }
}
