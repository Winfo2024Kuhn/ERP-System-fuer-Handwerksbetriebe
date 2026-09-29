package org.example.kalkulationsprogramm.service.telefon;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.TelefonKontaktZuordenbar;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordnet Rufnummern Kunden oder Lieferanten zu.
 * <p>
 * Gesucht wird in {@code telefon}/{@code mobiltelefon} von Kunden und Lieferanten
 * sowie in den gemerkten {@link KontaktRufnummer}n. Genau ein Treffer → automatisch
 * zugeordnet; mehrere → Kandidaten zur Auswahl; keiner → "Unbekannt".
 * Von Hand gesetzte Zuordnungen werden nie überschrieben.
 */
@Service
@RequiredArgsConstructor
public class RufnummernZuordnungService {

    private static final Duration CACHE_DAUER = Duration.ofSeconds(60);

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final KontaktRufnummerRepository kontaktRufnummerRepository;
    private final TelefonEinstellungenService einstellungen;
    private final EntityManager entityManager;
    private final Clock clock;

    private volatile Verzeichnis cache;
    private volatile Instant cacheBis = Instant.MIN;

    /** Ergebnis der Suche nach einer Rufnummer. */
    public record Treffer(List<KontaktKurzDto> kontakte) {
        public boolean eindeutig() {
            return kontakte.size() == 1;
        }

        public boolean mehrdeutig() {
            return kontakte.size() > 1;
        }
    }

    /** Rufnummern-Verzeichnis: normalisierte Nummer → Kontakte (ohne Doppelte). */
    public static final class Verzeichnis {
        private final Map<String, List<KontaktKurzDto>> eintraege = new HashMap<>();

        void fuegeHinzu(String normalisiert, KontaktKurzDto kontakt) {
            if (normalisiert == null) {
                return;
            }
            List<KontaktKurzDto> liste = eintraege.computeIfAbsent(normalisiert, k -> new ArrayList<>());
            boolean vorhanden = liste.stream().anyMatch(
                    k -> k.typ().equals(kontakt.typ()) && k.id().equals(kontakt.id()));
            if (!vorhanden) {
                liste.add(kontakt);
            }
        }

        public Treffer finde(String normalisiert) {
            if (normalisiert == null) {
                return new Treffer(List.of());
            }
            return new Treffer(List.copyOf(eintraege.getOrDefault(normalisiert, List.of())));
        }
    }

    /** Normalisiert mit den Vorwahlen aus den Einstellungen (von der FRITZ!Box übernommen). */
    public String normalisiere(String roh) {
        return RufnummerNormalisierer.normalisiere(roh, einstellungen.landesvorwahl(), einstellungen.ortsvorwahl());
    }

    /** Verzeichnis für Anzeigezwecke; höchstens 60 s alt. */
    @Transactional(readOnly = true)
    public Verzeichnis verzeichnis() {
        Instant jetzt = clock.instant();
        Verzeichnis v = cache;
        if (v == null || jetzt.isAfter(cacheBis)) {
            v = baueVerzeichnis();
            cache = v;
            cacheBis = jetzt.plus(CACHE_DAUER);
        }
        return v;
    }

    /** Frisches Verzeichnis (z.B. für jeden Abhollauf); erneuert auch den Anzeige-Cache. */
    @Transactional(readOnly = true)
    public Verzeichnis frischesVerzeichnis() {
        Verzeichnis v = baueVerzeichnis();
        cache = v;
        cacheBis = clock.instant().plus(CACHE_DAUER);
        return v;
    }

    public void verwerfeCache() {
        cache = null;
    }

    /**
     * Wendet die automatische Zuordnung an. Manuelle Zuordnungen bleiben unberührt.
     *
     * @return true, wenn jetzt ein Kontakt zugeordnet ist, der vorher nicht zugeordnet war
     */
    public boolean ordneAutomatischZu(TelefonKontaktZuordenbar eintrag, Verzeichnis verzeichnis) {
        if (eintrag.getZuordnung() == TelefonZuordnung.MANUELL) {
            return false;
        }
        boolean vorher = eintrag.getZuordnung() == TelefonZuordnung.AUTOMATISCH;
        Treffer treffer = verzeichnis.finde(eintrag.getNummerNormalisiert());
        if (treffer.eindeutig()) {
            setzeKontakt(eintrag, treffer.kontakte().getFirst(), TelefonZuordnung.AUTOMATISCH);
            return !vorher;
        }
        eintrag.setKunde(null);
        eintrag.setLieferant(null);
        eintrag.setZuordnung(TelefonZuordnung.KEINE);
        return false;
    }

    /** Setzt einen Kontakt von Hand. Genau eins von kundeId/lieferantId. */
    public KontaktKurzDto ordneManuellZu(TelefonKontaktZuordenbar eintrag, Long kundeId, Long lieferantId) {
        KontaktKurzDto kontakt = ladeKontakt(kundeId, lieferantId);
        setzeKontakt(eintrag, kontakt, TelefonZuordnung.MANUELL);
        return kontakt;
    }

    public void hebeZuordnungAuf(TelefonKontaktZuordenbar eintrag) {
        eintrag.setKunde(null);
        eintrag.setLieferant(null);
        eintrag.setZuordnung(TelefonZuordnung.KEINE);
    }

    /**
     * Merkt eine Rufnummer beim Kontakt, sofern sie dort noch nicht bekannt ist.
     *
     * @return true, wenn ein neuer Eintrag angelegt wurde
     */
    @Transactional
    public boolean merkeNummer(String nummerRoh, String normalisiert, Long kundeId, Long lieferantId) {
        if (normalisiert == null) {
            return false;
        }
        boolean schonBekannt = kundeId != null
                ? kontaktRufnummerRepository.existsByKundeIdAndNummerNormalisiert(kundeId, normalisiert)
                : kontaktRufnummerRepository.existsByLieferantIdAndNummerNormalisiert(lieferantId, normalisiert);
        if (schonBekannt) {
            return false;
        }
        KontaktRufnummer r = new KontaktRufnummer();
        if (kundeId != null) {
            r.setKunde(entityManager.getReference(Kunde.class, kundeId));
        } else {
            r.setLieferant(entityManager.getReference(Lieferanten.class, lieferantId));
        }
        r.setNummerRoh(nummerRoh == null || nummerRoh.isBlank() ? normalisiert : nummerRoh.trim());
        r.setNummerNormalisiert(normalisiert);
        r.setAngelegtAm(java.time.LocalDateTime.now(clock));
        kontaktRufnummerRepository.save(r);
        verwerfeCache();
        return true;
    }

    /** Kurzform eines bereits zugeordneten Eintrags (oder null). */
    public static KontaktKurzDto kontaktVon(TelefonKontaktZuordenbar eintrag) {
        if (eintrag.getKunde() != null) {
            Kunde k = eintrag.getKunde();
            return new KontaktKurzDto(KontaktKurzDto.KUNDE, k.getId(), k.getName(), k.getKundennummer(), k.getOrt());
        }
        if (eintrag.getLieferant() != null) {
            Lieferanten l = eintrag.getLieferant();
            return new KontaktKurzDto(KontaktKurzDto.LIEFERANT, l.getId(), l.getLieferantenname(), null, l.getOrt());
        }
        return null;
    }

    private KontaktKurzDto ladeKontakt(Long kundeId, Long lieferantId) {
        if ((kundeId == null) == (lieferantId == null)) {
            throw new IllegalArgumentException("Bitte genau einen Kunden oder einen Lieferanten auswählen.");
        }
        if (kundeId != null) {
            Kunde k = kundeRepository.findById(kundeId)
                    .orElseThrow(() -> new IllegalArgumentException("Kunde nicht gefunden."));
            return new KontaktKurzDto(KontaktKurzDto.KUNDE, k.getId(), k.getName(), k.getKundennummer(), k.getOrt());
        }
        Lieferanten l = lieferantenRepository.findById(lieferantId)
                .orElseThrow(() -> new IllegalArgumentException("Lieferant nicht gefunden."));
        return new KontaktKurzDto(KontaktKurzDto.LIEFERANT, l.getId(), l.getLieferantenname(), null, l.getOrt());
    }

    private void setzeKontakt(TelefonKontaktZuordenbar eintrag, KontaktKurzDto kontakt, TelefonZuordnung art) {
        if (KontaktKurzDto.KUNDE.equals(kontakt.typ())) {
            eintrag.setKunde(entityManager.getReference(Kunde.class, kontakt.id()));
            eintrag.setLieferant(null);
        } else {
            eintrag.setLieferant(entityManager.getReference(Lieferanten.class, kontakt.id()));
            eintrag.setKunde(null);
        }
        eintrag.setZuordnung(art);
    }

    private Verzeichnis baueVerzeichnis() {
        String land = einstellungen.landesvorwahl();
        String ort = einstellungen.ortsvorwahl();
        Verzeichnis v = new Verzeichnis();
        for (Object[] z : kundeRepository.findeTelefonverzeichnis()) {
            KontaktKurzDto k = new KontaktKurzDto(KontaktKurzDto.KUNDE, (Long) z[0], (String) z[1], (String) z[2], (String) z[3]);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[4], land, ort), k);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[5], land, ort), k);
        }
        for (Object[] z : lieferantenRepository.findeTelefonverzeichnis()) {
            KontaktKurzDto l = new KontaktKurzDto(KontaktKurzDto.LIEFERANT, (Long) z[0], (String) z[1], null, (String) z[2]);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[3], land, ort), l);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[4], land, ort), l);
        }
        for (KontaktRufnummer r : kontaktRufnummerRepository.findAllMitKontakt()) {
            KontaktKurzDto k = r.getKunde() != null
                    ? new KontaktKurzDto(KontaktKurzDto.KUNDE, r.getKunde().getId(), r.getKunde().getName(),
                    r.getKunde().getKundennummer(), r.getKunde().getOrt())
                    : new KontaktKurzDto(KontaktKurzDto.LIEFERANT, r.getLieferant().getId(),
                    r.getLieferant().getLieferantenname(), null, r.getLieferant().getOrt());
            v.fuegeHinzu(r.getNummerNormalisiert(), k);
        }
        return v;
    }
}
