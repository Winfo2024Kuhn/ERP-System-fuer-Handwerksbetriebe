package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "email_signature_image")
public class EmailSignatureImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_id", nullable = false)
    private EmailSignature signature;

    @Column(name = "cid", nullable = false, length = 120)
    private String cid;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sort_order")
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public EmailSignature getSignature() { return signature; }
    public void setSignature(EmailSignature signature) { this.signature = signature; }
    public String getCid() { return cid; }
    public void setCid(String cid) { this.cid = cid; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
