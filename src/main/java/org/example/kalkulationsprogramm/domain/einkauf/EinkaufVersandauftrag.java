package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkauf_versandauftrag", uniqueConstraints = @UniqueConstraint(
        name = "uk_einkauf_versand_idempotenz", columnNames = "idempotenz_key"))
@Getter
@NoArgsConstructor
public class EinkaufVersandauftrag {
    public enum Status { VORBEREITET, LAEUFT, ANGENOMMEN, FEHLGESCHLAGEN, UNKLAR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long version;
    @Column(name = "typ", nullable = false, length = 40)
    private String typ;
    @Column(name = "vorgang_id", nullable = false)
    private Long vorgangId;
    @Column(name = "revision_id")
    private Long revisionId;
    @Column(name = "beteiligung_id")
    private Long beteiligungId;
    @Column(name = "konto_id", nullable = false, length = 30)
    private String kontoId;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "idempotenz_key", nullable = false, length = 16, updatable = false)
    private UUID idempotenzKey;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;
    @Column(name = "mime_hash", nullable = false, length = 64, updatable = false)
    private String mimeHash;
    @Column(name = "freigabe_hash", nullable = false, length = 128, updatable = false)
    private String freigabeHash;
    @JdbcTypeCode(SqlTypes.LONGVARBINARY) @Column(name = "snapshot_json", nullable = false, updatable = false)
    private byte[] snapshotJson;
    @JdbcTypeCode(SqlTypes.LONGVARBINARY) @Column(name = "mime_bytes", nullable = false, updatable = false)
    private byte[] mimeBytes;
    @Column(name = "message_id", nullable = false, length = 255, updatable = false)
    private String messageId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "ENUM('VORBEREITET','LAEUFT','ANGENOMMEN','FEHLGESCHLAGEN','UNKLAR')")
    private Status status = Status.VORBEREITET;
    @Column(name = "fehler_code", length = 80)
    private String fehlerCode;
    @Column(name = "erstellt_am", nullable = false, updatable = false)
    private Instant erstelltAm;
    @Column(name = "angenommen_am")
    private Instant angenommenAm;
    @Column(name = "archiviert_am")
    private Instant archiviertAm;
    @Column(name = "archiv_claim_am")
    private Instant archivClaimAm;
    @Column(name = "archiv_fehler_code", length = 80)
    private String archivFehlerCode;
    @Column(name = "annahmeereignis_am")
    private Instant annahmeereignisAm;
    @Column(name = "akteur_id")
    private Long akteurId;
    @Column(name = "klaerung_entscheidung", length = 40)
    private String klaerungEntscheidung;
    @Column(name = "klaerung_beleg", length = 5000)
    private String klaerungBeleg;
    @Column(name = "klaerung_akteur_id")
    private Long klaerungAkteurId;
    @Column(name = "klaerung_am")
    private Instant klaerungAm;
    @OneToMany(mappedBy = "auftrag", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("nummer ASC")
    private List<EinkaufVersandversuch> versuche = new ArrayList<>();

    public EinkaufVersandauftrag(String typ, Long vorgangId, Long revisionId, Long beteiligungId,
            String kontoId, UUID idempotenzKey, String payloadHash, String mimeHash, String freigabeHash,
            byte[] snapshotJson, byte[] mimeBytes, String messageId, Long akteurId) {
        this.typ = typ; this.vorgangId = vorgangId; this.revisionId = revisionId;
        this.beteiligungId = beteiligungId; this.kontoId = kontoId; this.idempotenzKey = idempotenzKey;
        this.payloadHash = payloadHash; this.mimeHash = mimeHash; this.freigabeHash = freigabeHash;
        this.snapshotJson = snapshotJson.clone(); this.mimeBytes = mimeBytes.clone();
        this.messageId = messageId; this.akteurId = akteurId; this.erstelltAm = Instant.now();
    }

    public byte[] getSnapshotJson() { return snapshotJson == null ? null : snapshotJson.clone(); }
    public byte[] getMimeBytes() { return mimeBytes == null ? null : mimeBytes.clone(); }

    public void starte(Long akteurId) {
        status = Status.LAEUFT; fehlerCode = null;
        versuche.add(new EinkaufVersandversuch(this, versuche.size() + 1, akteurId, Instant.now()));
    }
    public void erneutVorbereiten(Long akteurId) {
        status = Status.VORBEREITET;
        fehlerCode = null;
        this.akteurId = akteurId;
    }
    public void angenommen(Instant zeit) {
        status = Status.ANGENOMMEN; angenommenAm = zeit; fehlerCode = null;
        annahmeereignisAm = zeit;
        letzterVersuch(EinkaufVersandversuch.Ergebnis.ANGENOMMEN, null, zeit);
    }
    public void beansprucheArchiv(Instant zeit) { archivClaimAm = zeit; }
    public void sicherFehlgeschlagen(String code) {
        status = Status.FEHLGESCHLAGEN; fehlerCode = code;
        letzterVersuch(EinkaufVersandversuch.Ergebnis.SICHER_FEHLGESCHLAGEN, code, Instant.now());
    }
    public void unklar(String code) {
        status = Status.UNKLAR; fehlerCode = code;
        letzterVersuch(EinkaufVersandversuch.Ergebnis.UNKLAR, code, Instant.now());
    }
    public void archivFehlgeschlagen(String code) {
        archivFehlerCode = code; archivClaimAm = null;
        versuche.add(new EinkaufVersandversuch(this, versuche.size() + 1, null, Instant.now()));
        letzterVersuch(EinkaufVersandversuch.Ergebnis.ARCHIV_FEHLER, code, Instant.now());
    }
    public void klaere(String entscheidung, String beleg, Long akteurId, Instant zeit) {
        this.klaerungEntscheidung = entscheidung;
        this.klaerungBeleg = beleg;
        this.klaerungAkteurId = akteurId;
        this.klaerungAm = zeit;
    }
    public void archiviert() {
        archiviertAm = Instant.now(); archivFehlerCode = null; archivClaimAm = null;
        versuche.add(new EinkaufVersandversuch(this, versuche.size() + 1, null, Instant.now()));
        letzterVersuch(EinkaufVersandversuch.Ergebnis.ARCHIVIERT, null, Instant.now());
    }
    private void letzterVersuch(EinkaufVersandversuch.Ergebnis ergebnis, String code, Instant zeit) {
        if (!versuche.isEmpty()) versuche.get(versuche.size() - 1).beendet(ergebnis, code, zeit);
    }
}
