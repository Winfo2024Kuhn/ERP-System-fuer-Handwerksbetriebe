package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandversuch;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandAnnahmeereignis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.*;
import org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository;
import org.example.kalkulationsprogramm.repository.EinkaufVersandAnnahmeereignisRepository;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EinkaufOutboxService {
    private final EinkaufVersandauftragRepository repository;
    private final EinkaufVersandAnnahmeereignisRepository annahmeereignisse;
    private final MailkontoService mailkontoService;
    private final LocalTestMailPolicy localTestMailPolicy;
    private final KontoMailTransport transport;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public VersandDto einreihen(VersandSnapshot snapshot, UUID idempotenzKey, Long akteurId) {
        if (snapshot == null || idempotenzKey == null || akteurId == null || akteurId <= 0) {
            throw new IllegalArgumentException("Versandfreigabe, Idempotenzschlüssel und Benutzer sind erforderlich.");
        }
        final VersandSnapshot frozenSnapshot = mitStabilerMessageId(normalisiereAnlagen(snapshot), idempotenzKey);
        byte[] snapshotBytes = serialisiere(frozenSnapshot);
        String snapshotHash = sha256(snapshotBytes);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        VersandDto vorhanden = tx.execute(status -> repository.findByIdempotenzKey(idempotenzKey)
                .map(a -> gleicheIdempotenz(a, snapshotHash)).orElse(null));
        if (vorhanden != null) return vorhanden;

        // The policy runs before account resolution, which must never fall back to another mailbox.
        localTestMailPolicy.pruefeNetzwerkzugriff(frozenSnapshot.konto().kontoId());
        var konto = mailkontoService.resolve(frozenSnapshot.konto().kontoId());
        final String frozenMessageId = frozenSnapshot.nachricht().messageId();
        var nachricht = new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht(
                frozenMessageId, frozenSnapshot.nachricht().to(), frozenSnapshot.nachricht().subject(), frozenSnapshot.nachricht().html(),
                frozenSnapshot.nachricht().inReplyTo(), frozenSnapshot.nachricht().references(), frozenSnapshot.nachricht().anlagen());
        byte[] mime = transport.vorbereiten(konto, nachricht);
        String mimeHash = sha256(mime);
        VersandDto result;
        try {
            result = tx.execute(status -> {
                var prior = repository.findByIdempotenzKey(idempotenzKey).orElse(null);
                if (prior != null) return gleicheIdempotenz(prior, snapshotHash);
                var auftrag = repository.saveAndFlush(new EinkaufVersandauftrag(frozenSnapshot.typ(), frozenSnapshot.vorgangId(),
                        frozenSnapshot.revisionId(), frozenSnapshot.beteiligungId(), frozenSnapshot.konto().kontoId(), idempotenzKey,
                        snapshotHash, mimeHash, frozenSnapshot.freigabeHash(), snapshotBytes, mime, frozenMessageId, akteurId));
                return dto(auftrag);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            VersandDto concurrent = tx.execute(status -> repository.findByIdempotenzKey(idempotenzKey)
                    .map(a -> gleicheIdempotenz(a, snapshotHash)).orElse(null));
            if (concurrent != null) return concurrent;
            throw race;
        }
        log.info("[EinkaufOutbox] Versandauftrag {} angelegt, Status {}", result.id(), result.status());
        return result;
    }

    public VersandDto erneutVersuchen(Long id, long version, Long akteurId) {
        if (akteurId == null || akteurId <= 0) throw new IllegalArgumentException("Ein Benutzer ist erforderlich.");
        return transaktion(() -> {
            EinkaufVersandauftrag a = sperre(id);
            if (a.getVersion() != version) throw new IllegalStateException("Der Versandauftrag wurde zwischenzeitlich geändert.");
            if (a.getStatus() != EinkaufVersandauftrag.Status.FEHLGESCHLAGEN) {
                throw new IllegalStateException("Nur sicher fehlgeschlagene Versandaufträge können erneut versucht werden.");
            }
            a.erneutVorbereiten(akteurId);
            repository.flush();
            return dto(a);
        });
    }

    public void klaeren(Long id, Klaerung klaerung, Long akteurId) {
        if (klaerung == null || klaerung.beleg() == null || klaerung.beleg().isBlank()
                || klaerung.beleg().length() > 5000 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Ein Beleg und Benutzer sind erforderlich.");
        transaktion(() -> {
            EinkaufVersandauftrag a = sperre(id);
            if (a.getVersion() != klaerung.version() || a.getStatus() != EinkaufVersandauftrag.Status.UNKLAR)
                throw new IllegalStateException("Der unklare Versandauftrag wurde zwischenzeitlich geändert.");
            if (klaerung.entscheidung() == Entscheidung.BEREITS_ANGENOMMEN) {
                Instant now = Instant.now(); a.angenommen(now);
                a.klaere(klaerung.entscheidung().name(), klaerung.beleg(), akteurId, now);
                speichereAnnahmeereignis(a, now);
            } else if (klaerung.entscheidung() == Entscheidung.NACHWEISLICH_NICHT_GESENDET) {
                a.sicherFehlgeschlagen("MANUELL_GEKLAERT");
                a.klaere(klaerung.entscheidung().name(), klaerung.beleg(), akteurId, Instant.now());
            } else throw new IllegalArgumentException("Die Versandklärung ist ungültig.");
            repository.flush();
        });
    }

    public Claim beanspruche(Long id) {
        return transaktion(() -> {
            EinkaufVersandauftrag a = repository.sperreById(id)
                    .orElseThrow(() -> new java.util.NoSuchElementException("Versandauftrag nicht gefunden."));
            if (a.getStatus() == EinkaufVersandauftrag.Status.ANGENOMMEN && a.getArchiviertAm() == null) {
                if (a.getArchivClaimAm() != null) return null;
                a.beansprucheArchiv(Instant.now());
                repository.flush();
                return new Claim(a.getId(), a.getKontoId(), a.getMimeBytes(), a.getStatus(), a.getVersion(), true, a.getTyp(), a.getVorgangId(), a.getRevisionId());
            }
            if (a.getStatus() != EinkaufVersandauftrag.Status.VORBEREITET) return null;
            a.starte(a.getAkteurId());
            return new Claim(a.getId(), a.getKontoId(), a.getMimeBytes(), a.getStatus(), a.getVersion(), false, a.getTyp(), a.getVorgangId(), a.getRevisionId());
        });
    }

    public void abgeschlossen(Long id, org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis result) {
        if (result == null) throw new IllegalArgumentException("Das SMTP-Ergebnis fehlt.");
        transaktion(() -> {
            EinkaufVersandauftrag a = sperre(id);
            if (a.getStatus() != EinkaufVersandauftrag.Status.LAEUFT) return;
            Instant now = Instant.now();
            switch (result.status()) {
                case ANGENOMMEN -> {
                    a.angenommen(now);
                    speichereAnnahmeereignis(a, now);
                }
                case SICHER_FEHLGESCHLAGEN -> a.sicherFehlgeschlagen(result.fehlerCode());
                case UNKLAR -> a.unklar(result.fehlerCode());
            }
            repository.flush();
        });
    }

    /**
     * Replays durable acceptance facts. Consumer database changes and marking an event processed commit atomically;
     * on a listener error or process crash the transaction rolls back and the same stable event key is offered again.
     */
    public int verarbeiteOffeneAnnahmeereignisse(EinkaufAnnahmeereignisConsumer consumer, int limit) {
        if (consumer == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Ein Consumer und ein Limit zwischen 1 und 500 sind erforderlich.");
        }
        var unterstuetzteTypen = consumer.unterstuetzteVorgangstypen();
        if (unterstuetzteTypen == null || unterstuetzteTypen.isEmpty()
                || unterstuetzteTypen.stream().anyMatch(typ -> typ == null || typ.isBlank())) {
            throw new IllegalArgumentException("Der Consumer muss mindestens einen gültigen Vorgangstyp angeben.");
        }
        var vorgangstypen = java.util.Set.copyOf(unterstuetzteTypen);
        int verarbeitet = 0;
        while (verarbeitet < limit) {
            boolean erledigt = transaktion(() -> {
                var offene = annahmeereignisse.sperreOffene(vorgangstypen,
                        org.springframework.data.domain.PageRequest.of(0, 1));
                if (offene.isEmpty()) return false;
                EinkaufVersandAnnahmeereignis ereignis = offene.getFirst();
                consumer.verarbeite(new EinkaufVersandAngenommen(ereignis.getEreignisSchluessel(),
                        ereignis.getVersandauftragId(), ereignis.getVorgangTyp(), ereignis.getVorgangId(),
                        ereignis.getRevisionId(), ereignis.getBeteiligungId(), ereignis.getAngenommenAm()));
                ereignis.verarbeitet(Instant.now());
                annahmeereignisse.flush();
                return true;
            });
            if (!erledigt) break;
            verarbeitet++;
        }
        return verarbeitet;
    }

    public void archivErgebnis(Long id, org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis result) {
        if (result == null) throw new IllegalArgumentException("Das Archivierungsergebnis fehlt.");
        transaktion(() -> {
            EinkaufVersandauftrag a = sperre(id);
            if (a.getStatus() != EinkaufVersandauftrag.Status.ANGENOMMEN || a.getArchiviertAm() != null) return;
            if (result.erfolgreich()) a.archiviert(); else a.archivFehlgeschlagen(result.fehlerCode());
            repository.flush();
        });
    }

    /** A process restart converts interrupted sends to uncertain; no age-based automatic retry exists. */
    public int markiereUnterbrocheneAlsUnklar() {
        return transaktion(() -> {
            var laufende = repository.findAllByStatus(EinkaufVersandauftrag.Status.LAEUFT);
            laufende.forEach(a -> a.unklar("PROZESS_NEUSTART"));
            int archiveClaims = repository.loeseUnterbrocheneArchivClaims(EinkaufVersandauftrag.Status.ANGENOMMEN);
            return laufende.size() + archiveClaims;
        });
    }

    public record Claim(Long id, String kontoId, byte[] mime, EinkaufVersandauftrag.Status status,
            long version, boolean archivRetry, String typ, Long vorgangId, Long revisionId) {
        public Claim { mime = mime.clone(); }
        @Override public byte[] mime() { return mime.clone(); }
    }

    private VersandDto gleicheIdempotenz(EinkaufVersandauftrag a, String hash) {
        if (!a.getPayloadHash().equals(hash)) throw new IllegalStateException("Der Idempotenzschlüssel wurde mit anderem Inhalt verwendet.");
        return dto(a);
    }
    private EinkaufVersandauftrag sperre(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Der Versandauftrag ist ungültig.");
        return repository.sperreById(id).orElseThrow(() -> new java.util.NoSuchElementException("Versandauftrag nicht gefunden."));
    }
    private void speichereAnnahmeereignis(EinkaufVersandauftrag auftrag, Instant angenommenAm) {
        var ereignis = new EinkaufVersandAnnahmeereignis(auftrag.getId(), auftrag.getTyp(),
                auftrag.getVorgangId(), auftrag.getRevisionId(), auftrag.getBeteiligungId(), angenommenAm);
        annahmeereignisse.save(ereignis);
    }
    private VersandDto dto(EinkaufVersandauftrag a) {
        return new VersandDto(a.getId(), a.getVersion(), a.getTyp(), a.getVorgangId(), a.getRevisionId(),
                a.getStatus().name(), a.getFehlerCode(), a.getErstelltAm(), a.getAngenommenAm(),
                a.getArchiviertAm() != null, a.getMessageId());
    }
    private byte[] serialisiere(Object value) {
        try { return objectMapper.writeValueAsBytes(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Der Versand-Snapshot konnte nicht gespeichert werden.", ex); }
    }
    private VersandSnapshot normalisiereAnlagen(VersandSnapshot s) {
        try {
            var anlagen = new java.util.ArrayList<org.example.email.EmailService.Attachment>();
            for (var anlage : s.nachricht().anlagen()) {
                byte[] data = anlage.data();
                if (data == null && anlage.file() != null) data = java.nio.file.Files.readAllBytes(anlage.file().toPath());
                if (data == null) throw new IllegalArgumentException("Eine Versand-Anlage enthält keine Datei.");
                anlagen.add(new org.example.email.EmailService.Attachment(data.clone(), anlage.filename(), anlage.mimeType()));
            }
            var n = s.nachricht();
            var frozen = new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht(
                    n.messageId(), n.to(), n.subject(), n.html(), n.inReplyTo(), n.references(), anlagen);
            return new VersandSnapshot(s.typ(), s.vorgangId(), s.revisionId(), s.beteiligungId(), s.konto(), frozen, s.freigabeHash());
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Eine Versand-Anlage konnte nicht eingefroren werden.", ex);
        }
    }
    private VersandSnapshot mitStabilerMessageId(VersandSnapshot s, UUID idempotenzKey) {
        var n = s.nachricht();
        String messageId = n.messageId();
        if (messageId == null || messageId.isBlank()) messageId = "<" + idempotenzKey + "@erp.local>";
        var fixed = new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht(
                messageId, n.to(), n.subject(), n.html(), n.inReplyTo(), n.references(), n.anlagen());
        return new VersandSnapshot(s.typ(), s.vorgangId(), s.revisionId(), s.beteiligungId(), s.konto(), fixed, s.freigabeHash());
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 ist nicht verfügbar.", ex); }
    }
    private <T> T transaktion(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); } catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }
    private void transaktion(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
