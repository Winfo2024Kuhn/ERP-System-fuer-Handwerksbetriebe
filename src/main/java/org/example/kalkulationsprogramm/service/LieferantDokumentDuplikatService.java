package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Räumt Lieferanten-Dokumente auf, die dieselbe gespeicherte Datei mehrfach zeigen.
 *
 * <p>Solche Duplikate entstanden, wenn Mail-Anhänge erneut verarbeitet wurden: Für den
 * schon verarbeiteten Anhang wurde ein weiteres Dokument angelegt und der Anhang darauf
 * umgehängt, das alte blieb liegen. Je Gruppe (gleicher Lieferant, gleiche Datei) bleibt
 * genau ein Dokument; Verknüpfungen, von Hand gelöste Paare (Sperren) und der
 * Mail-Anhang gehen auf dieses über.</p>
 *
 * <p>Von Hand Gepflegtes geht nicht verloren: Hat in einer Gruppe mehr als ein Dokument
 * eine Projekt-Zuordnung, einen Beleg, eine Reklamation oder ist bezahlt, freigegeben
 * oder als Lagerbestellung markiert, bleibt die Gruppe unangetastet und wird nur
 * protokolliert. Jede Gruppe läuft in einer eigenen Transaktion; scheitert sie (z. B. an
 * einem Fremdschlüssel), bleibt sie vollständig wie sie war.</p>
 */
@Slf4j
@Service
public class LieferantDokumentDuplikatService {

    /** Ergebnis eines Laufs. */
    public record Ergebnis(int gruppen, int geloescht, int uebersprungen) {
    }

    private final LieferantDokumentRepository dokumentRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final LieferantReklamationRepository reklamationRepository;
    private final LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    private final TransactionTemplate transaktion;

    public LieferantDokumentDuplikatService(LieferantDokumentRepository dokumentRepository,
            EmailAttachmentRepository attachmentRepository, LieferantReklamationRepository reklamationRepository,
            LieferantDokumentVerknuepfungSperreRepository sperreRepository,
            PlatformTransactionManager transactionManager) {
        this.dokumentRepository = dokumentRepository;
        this.attachmentRepository = attachmentRepository;
        this.reklamationRepository = reklamationRepository;
        this.sperreRepository = sperreRepository;
        this.transaktion = new TransactionTemplate(transactionManager);
        this.transaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Ergebnis bereinigeDateiDuplikate() {
        Map<String, List<Long>> gruppen = new LinkedHashMap<>();
        for (Object[] zeile : dokumentRepository.findDateiDuplikate()) {
            Long id = ((Number) zeile[0]).longValue();
            String schluessel = zeile[1] + "|" + zeile[2];
            gruppen.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(id);
        }

        int geloescht = 0;
        int uebersprungen = 0;
        for (List<Long> ids : gruppen.values()) {
            try {
                Integer anzahl = transaktion.execute(status -> bereinigeGruppe(ids));
                if (anzahl == null || anzahl == 0) {
                    uebersprungen++;
                } else {
                    geloescht += anzahl;
                }
            } catch (RuntimeException e) {
                // z. B. ein Fremdschlüssel ohne Entity – die Gruppe bleibt dann wie sie war
                log.warn("[Duplikate] Gruppe {} nicht bereinigt: {}: {}", ids, e.getClass().getSimpleName(),
                        e.getMessage());
                uebersprungen++;
            }
        }
        log.info("[Duplikate] fertig: {} Gruppen, {} Duplikate gelöscht, {} Gruppen übersprungen",
                gruppen.size(), geloescht, uebersprungen);
        return new Ergebnis(gruppen.size(), geloescht, uebersprungen);
    }

    /** @return Anzahl gelöschter Dokumente, 0 wenn die Gruppe unangetastet bleibt */
    private int bereinigeGruppe(List<Long> ids) {
        List<LieferantDokument> dokumente = dokumentRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(LieferantDokument::getId))
                .toList();
        if (dokumente.size() < 2) {
            return 0;
        }
        List<LieferantDokument> vonHandGepflegt = dokumente.stream().filter(this::istVonHandGepflegt).toList();
        if (vonHandGepflegt.size() > 1) {
            log.warn("[Duplikate] Gruppe {} übersprungen: mehrere Exemplare sind von Hand gepflegt – bitte von Hand prüfen",
                    ids);
            return 0;
        }

        LieferantDokument behalten = vonHandGepflegt.isEmpty() ? waehleBehalten(dokumente) : vonHandGepflegt.get(0);
        Set<Long> gruppenIds = new HashSet<>(ids);
        Set<Long> gesperrtMitBehalten = sperrPartner(behalten.getId());

        int geloescht = 0;
        for (LieferantDokument duplikat : dokumente) {
            if (duplikat == behalten) {
                continue;
            }
            uebernimmSperren(duplikat, behalten, gruppenIds, gesperrtMitBehalten);
            uebernimmVerknuepfungen(duplikat, behalten, gruppenIds, gesperrtMitBehalten);
            uebernimmAnhang(duplikat, behalten);
            loesche(duplikat);
            geloescht++;
        }
        dokumentRepository.save(behalten);
        log.info("[Duplikate] Dokument {} behalten, {} Duplikat(e) gelöscht (Gruppe {})", behalten.getId(), geloescht,
                ids);
        return geloescht;
    }

    /**
     * Welches Exemplar bleibt, wenn keins von Hand gepflegt ist: ein sichtbares vor einem
     * ausgeblendeten (Duplikate wurden oft von Hand weggeblendet), ein von Hand
     * umgestellter Typ vor „Sonstiges“, das mit dem Mail-Anhang verknüpfte, sonst das älteste.
     */
    private LieferantDokument waehleBehalten(List<LieferantDokument> dokumente) {
        Map<LieferantDokument, Boolean> hatAnhang = new IdentityHashMap<>();
        dokumente.forEach(d -> hatAnhang.put(d, hatAnhang(d)));
        return dokumente.stream()
                .min(Comparator.comparing(LieferantDokument::isAusgeblendet)
                        .thenComparing(d -> d.getTyp() == null || d.getTyp() == LieferantDokumentTyp.SONSTIG)
                        .thenComparing(d -> !hatAnhang.get(d))
                        .thenComparing(LieferantDokument::getId))
                .orElseThrow();
    }

    private boolean hatAnhang(LieferantDokument dokument) {
        return dokument.getAttachment() != null
                || !attachmentRepository.findByLieferantDokumentId(dokument.getId()).isEmpty();
    }

    private boolean istVonHandGepflegt(LieferantDokument dokument) {
        LieferantGeschaeftsdokument gd = dokument.getGeschaeftsdaten();
        boolean gdGepflegt = gd != null && (Boolean.TRUE.equals(gd.getBezahlt())
                || Boolean.TRUE.equals(gd.getGenehmigt())
                || Boolean.TRUE.equals(gd.getLagerbestellung()));
        return gdGepflegt
                || !dokument.getProjektAnteile().isEmpty()
                || dokument.getBeleg() != null
                || reklamationRepository.existsByLieferscheinId(dokument.getId());
    }

    /** IDs der Dokumente, mit denen dieses Dokument nicht verknüpft werden darf (von Hand gelöst). */
    private Set<Long> sperrPartner(Long dokumentId) {
        Set<Long> partner = new HashSet<>();
        for (LieferantDokumentVerknuepfungSperre sperre : sperreRepository.findByBeteiligtemDokument(dokumentId)) {
            partner.add(dokumentId.equals(sperre.getDokumentId()) ? sperre.getVerknuepftId() : sperre.getDokumentId());
        }
        return partner;
    }

    /**
     * Schreibt die Sperren des Duplikats (in gleicher Richtung) auf das behaltene Dokument
     * um – die alten löscht die Datenbank mit dem Duplikat.
     */
    private void uebernimmSperren(LieferantDokument duplikat, LieferantDokument behalten, Set<Long> gruppenIds,
            Set<Long> gesperrtMitBehalten) {
        Long duplikatId = duplikat.getId();
        for (LieferantDokumentVerknuepfungSperre sperre : sperreRepository.findByBeteiligtemDokument(duplikatId)) {
            boolean duplikatIstNachfolger = duplikatId.equals(sperre.getDokumentId());
            Long partner = duplikatIstNachfolger ? sperre.getVerknuepftId() : sperre.getDokumentId();
            // Ist das behaltene Dokument mit dem Partner verknüpft, gilt diese Verknüpfung
            if (gruppenIds.contains(partner) || istVerknuepft(behalten, partner)
                    || !gesperrtMitBehalten.add(partner)) {
                continue;
            }
            sperreRepository.save(duplikatIstNachfolger
                    ? new LieferantDokumentVerknuepfungSperre(behalten.getId(), partner, sperre.getGesperrtAm())
                    : new LieferantDokumentVerknuepfungSperre(partner, behalten.getId(), sperre.getGesperrtAm()));
        }
    }

    private static boolean istVerknuepft(LieferantDokument dokument, Long partnerId) {
        return dokument.getVerknuepfteDokumente().stream().anyMatch(d -> partnerId.equals(d.getId()))
                || dokument.getVerknuepftVon().stream().anyMatch(d -> partnerId.equals(d.getId()));
    }

    private static void uebernimmVerknuepfungen(LieferantDokument duplikat, LieferantDokument behalten,
            Set<Long> gruppenIds, Set<Long> gesperrtMitBehalten) {
        for (LieferantDokument ziel : duplikat.getVerknuepfteDokumente()) {
            if (darfVerknuepftWerden(ziel, gruppenIds, gesperrtMitBehalten)) {
                behalten.getVerknuepfteDokumente().add(ziel);
            }
        }
        for (LieferantDokument quelle : List.copyOf(duplikat.getVerknuepftVon())) {
            if (darfVerknuepftWerden(quelle, gruppenIds, gesperrtMitBehalten)) {
                quelle.getVerknuepfteDokumente().add(behalten);
            }
        }
    }

    private static boolean darfVerknuepftWerden(LieferantDokument partner, Set<Long> gruppenIds,
            Set<Long> gesperrtMitBehalten) {
        return !gruppenIds.contains(partner.getId()) && !gesperrtMitBehalten.contains(partner.getId());
    }

    private void uebernimmAnhang(LieferantDokument duplikat, LieferantDokument behalten) {
        attachmentRepository.findByLieferantDokumentId(duplikat.getId()).forEach(anhang -> {
            anhang.setLieferantDokument(behalten);
            attachmentRepository.save(anhang);
        });
        if (behalten.getAttachment() == null && duplikat.getAttachment() != null) {
            behalten.setAttachment(duplikat.getAttachment());
        }
    }

    /** Wie das Löschen von Hand, nur ohne die Datei: die gehört weiter dem behaltenen Dokument. */
    private void loesche(LieferantDokument duplikat) {
        duplikat.getVerknuepfteDokumente().clear();
        List.copyOf(duplikat.getVerknuepftVon()).forEach(quelle -> quelle.getVerknuepfteDokumente().remove(duplikat));
        duplikat.setAttachment(null);
        dokumentRepository.saveAndFlush(duplikat);
        dokumentRepository.delete(duplikat);
    }
}
