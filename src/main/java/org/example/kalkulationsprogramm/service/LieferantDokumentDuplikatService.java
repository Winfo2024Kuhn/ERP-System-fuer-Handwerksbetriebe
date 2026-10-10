package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.example.kalkulationsprogramm.domain.EmailAttachment;
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
 * Räumt Lieferanten-Dokumente auf, die dieselbe gespeicherte Datei mehrfach zeigen
 * ({@link #bereinigeDateiDuplikate()}) oder deren Mail-Anhänge denselben Inhalt haben
 * ({@link #bereinigeInhaltsDuplikate()}).
 *
 * <p>Datei-Duplikate entstanden, wenn Mail-Anhänge erneut verarbeitet wurden: Für den
 * schon verarbeiteten Anhang wurde ein weiteres Dokument angelegt und der Anhang darauf
 * umgehängt, das alte blieb liegen. Inhalts-Duplikate entstehen, wenn ein Lieferant
 * dieselbe PDF (z. B. die Widerrufsbelehrung) jeder Mail anhängt. Je Gruppe (gleicher
 * Lieferant, gleiche Datei bzw. gleicher Inhalt) bleibt genau ein Dokument;
 * Verknüpfungen, von Hand gelöste Paare (Sperren) und die Mail-Anhänge gehen auf dieses
 * über. Rechnungen und Gutschriften werden nur bei derselben Datei zusammengeführt,
 * nie wegen gleichen Inhalts (GoBD).</p>
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
    private final EmailAttachmentProcessingService anhangService;
    private final TransactionTemplate transaktion;

    public LieferantDokumentDuplikatService(LieferantDokumentRepository dokumentRepository,
            EmailAttachmentRepository attachmentRepository, LieferantReklamationRepository reklamationRepository,
            LieferantDokumentVerknuepfungSperreRepository sperreRepository,
            EmailAttachmentProcessingService anhangService, PlatformTransactionManager transactionManager) {
        this.dokumentRepository = dokumentRepository;
        this.attachmentRepository = attachmentRepository;
        this.reklamationRepository = reklamationRepository;
        this.sperreRepository = sperreRepository;
        this.anhangService = anhangService;
        this.transaktion = new TransactionTemplate(transactionManager);
        this.transaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Ergebnisse zweier Läufe zusammenzählen. */
    public static Ergebnis summe(Ergebnis a, Ergebnis b) {
        return new Ergebnis(a.gruppen() + b.gruppen(), a.geloescht() + b.geloescht(),
                a.uebersprungen() + b.uebersprungen());
    }

    public Ergebnis bereinigeDateiDuplikate() {
        Map<String, List<Long>> gruppen = new LinkedHashMap<>();
        for (Object[] zeile : dokumentRepository.findDateiDuplikate()) {
            Long id = ((Number) zeile[0]).longValue();
            String schluessel = zeile[1] + "|" + zeile[2];
            gruppen.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(id);
        }
        return bereinigeGruppen(new ArrayList<>(gruppen.values()), "Datei");
    }

    /**
     * Führt Dokumente zusammen, deren Mail-Anhänge beim selben Lieferanten Byte für Byte
     * gleich sind, aber als eigene Dateien gespeichert wurden – z. B. die
     * Widerrufsbelehrung, die jeder Mail anhängt.
     */
    public Ergebnis bereinigeInhaltsDuplikate() {
        Map<String, List<Long>> gleicheGroesse = new LinkedHashMap<>();
        for (Object[] zeile : attachmentRepository.findDokumentAnhaengeMitGleicherGroesse()) {
            Long anhangId = ((Number) zeile[0]).longValue();
            gleicheGroesse.computeIfAbsent(zeile[1] + "|" + zeile[2], k -> new ArrayList<>()).add(anhangId);
        }
        List<List<Long>> gruppen = new ArrayList<>();
        for (List<Long> anhangIds : gleicheGroesse.values()) {
            // Lesend in einer Transaktion: der Pfad eines Anhangs hängt an Mail und Lieferant
            List<List<Long>> dokumentGruppen = transaktion.execute(status -> dokumenteMitGleichemInhalt(anhangIds));
            if (dokumentGruppen != null) {
                gruppen.addAll(dokumentGruppen);
            }
        }
        return bereinigeGruppen(vereinige(gruppen), "Inhalt");
    }

    /**
     * Vereinigt Gruppen, die sich ein Dokument teilen – z. B. eine E-Rechnung aus zwei
     * Mails: PDF und XML ergeben je eine Gruppe mit denselben zwei Dokumenten.
     */
    static List<List<Long>> vereinige(List<List<Long>> gruppen) {
        List<Set<Long>> vereinigt = new ArrayList<>();
        for (List<Long> gruppe : gruppen) {
            Set<Long> neu = new TreeSet<>(gruppe);
            for (Iterator<Set<Long>> it = vereinigt.iterator(); it.hasNext();) {
                Set<Long> vorhanden = it.next();
                if (!Collections.disjoint(vorhanden, neu)) {
                    neu.addAll(vorhanden);
                    it.remove();
                }
            }
            vereinigt.add(neu);
        }
        return vereinigt.stream().map(g -> (List<Long>) new ArrayList<>(g)).toList();
    }

    /** Teilt gleich große Anhänge in Gruppen gleichen Inhalts; je Gruppe die IDs ihrer Dokumente. */
    private List<List<Long>> dokumenteMitGleichemInhalt(List<Long> anhangIds) {
        List<List<EmailAttachment>> inhalte = new ArrayList<>();
        for (EmailAttachment anhang : attachmentRepository.findAllById(anhangIds)) {
            List<EmailAttachment> passend = inhalte.stream()
                    .filter(gruppe -> anhangService.gleicherInhalt(gruppe.get(0), anhang))
                    .findFirst().orElse(null);
            if (passend != null) {
                passend.add(anhang);
            } else {
                inhalte.add(new ArrayList<>(List.of(anhang)));
            }
        }
        List<List<Long>> dokumentGruppen = new ArrayList<>();
        for (List<EmailAttachment> gruppe : inhalte) {
            List<LieferantDokument> dokumente = gruppe.stream()
                    .map(EmailAttachment::getLieferantDokument)
                    .distinct().toList();
            if (dokumente.size() < 2) {
                continue;
            }
            // Rechnungen und Gutschriften: GoBD – die fängt beim Import schon der
            // Nummern-Check ab; hier nie automatisch löschen.
            if (dokumente.stream().anyMatch(d -> d.getTyp() == LieferantDokumentTyp.RECHNUNG
                    || d.getTyp() == LieferantDokumentTyp.GUTSCHRIFT)) {
                continue;
            }
            dokumentGruppen.add(dokumente.stream().map(LieferantDokument::getId).sorted().toList());
        }
        return dokumentGruppen;
    }

    private Ergebnis bereinigeGruppen(List<List<Long>> gruppen, String art) {
        int geloescht = 0;
        int uebersprungen = 0;
        for (List<Long> ids : gruppen) {
            try {
                Integer anzahl = transaktion.execute(status -> bereinigeGruppe(ids));
                if (anzahl == null || anzahl == UEBERSPRUNGEN) {
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
        log.info("[Duplikate] {} fertig: {} Gruppen, {} Duplikate gelöscht, {} Gruppen übersprungen",
                art, gruppen.size(), geloescht, uebersprungen);
        return new Ergebnis(gruppen.size(), geloescht, uebersprungen);
    }

    /** Rückgabe von {@link #bereinigeGruppe}: Gruppe bewusst stehen gelassen. */
    private static final int UEBERSPRUNGEN = -1;

    /**
     * @return Anzahl gelöschter Dokumente; 0, wenn nichts (mehr) zu tun ist;
     *         {@link #UEBERSPRUNGEN}, wenn die Gruppe bewusst stehen bleibt
     */
    private int bereinigeGruppe(List<Long> ids) {
        List<LieferantDokument> dokumente = dokumentRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(LieferantDokument::getId))
                .toList();
        if (dokumente.size() < 2) {
            return 0;
        }
        long lieferanten = dokumente.stream()
                .map(d -> d.getLieferant() != null ? d.getLieferant().getId() : null)
                .distinct().count();
        if (lieferanten > 1) {
            log.warn("[Duplikate] Gruppe {} übersprungen: Dokumente verschiedener Lieferanten", ids);
            return UEBERSPRUNGEN;
        }
        List<LieferantDokument> vonHandGepflegt = dokumente.stream().filter(this::istVonHandGepflegt).toList();
        if (vonHandGepflegt.size() > 1) {
            log.warn("[Duplikate] Gruppe {} übersprungen: mehrere Exemplare sind von Hand gepflegt – bitte von Hand prüfen",
                    ids);
            return UEBERSPRUNGEN;
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
