package org.example.kalkulationsprogramm.domain.einkauf;

import org.example.kalkulationsprogramm.domain.Email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "email_import_identitaet", uniqueConstraints = @UniqueConstraint(
        name = "uk_email_import_identitaet_uid", columnNames = {"konto_id", "folder", "uidvalidity", "uid"}))
public class EmailImportIdentitaet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "konto_id", nullable = false, length = 16)
    private String kontoId;

    @Column(nullable = false, length = 255)
    private String folder;

    @Column(name = "uidvalidity", nullable = false)
    private long uidValidity;

    @Column(nullable = false)
    private long uid;

    @Column(name = "pruefkonflikt", nullable = false)
    private boolean pruefkonflikt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    protected EmailImportIdentitaet() {}

    public EmailImportIdentitaet(String kontoId, String folder, long uidValidity, long uid, Email email) {
        this(kontoId, folder, uidValidity, uid, email, false);
    }

    public EmailImportIdentitaet(String kontoId, String folder, long uidValidity, long uid, Email email,
            boolean pruefkonflikt) {
        this.kontoId = kontoId;
        this.folder = folder;
        this.uidValidity = uidValidity;
        this.uid = uid;
        this.email = email;
        this.pruefkonflikt = pruefkonflikt;
    }

    public Long getId() { return id; }
    public String getKontoId() { return kontoId; }
    public String getFolder() { return folder; }
    public long getUidValidity() { return uidValidity; }
    public long getUid() { return uid; }
    public boolean isPruefkonflikt() { return pruefkonflikt; }
    public Email getEmail() { return email; }
}
