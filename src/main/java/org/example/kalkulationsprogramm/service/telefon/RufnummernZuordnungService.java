package org.example.kalkulationsprogramm.service.telefon;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonKontaktZuordenbar;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
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
 * Ordnet Rufnummern Kunden, Lieferanten oder Steuerberatern zu.
 * <p>
 * Gesucht wird in {@code telefon}/{@code mobiltelefon} von Kunden und Lieferanten,
 * in der Kanzleinummer und den Ansprechpartner-Nummern der Steuerberater sowie in
 * den gemerkten {@link KontaktRufnummer}n. Genau ein Treffer → automatisch
 * zugeordnet; mehrere → Kandidaten zur Auswahl; keiner → "Unbekannt".
 * Von Hand gesetzte Zuordnungen werden nie überschrieben.
 * <p>
 * <b>Durchwahlen:</b> Ist eine Nummer in Durchwahl-Schreibweise hinterlegt
 * ("09721 5555-0", "0931 4444-12"), gehört jeder Anruf von dieser Stammnummer
 * mit beliebiger Durchwahl zum Kontakt – so wird die Firma auch erkannt, wenn
 * jemand aus einer anderen Abteilung anruft. Die exakte Nummer hat Vorrang;
 * sonst gewinnt die längste passende Stammnummer.
 */
@Service
@RequiredArgsConstructor
public class RufnummernZuordnungService {

    private static final Duration CACHE_DAUER = Duration.ofSeconds(60);

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final SteuerberaterKontaktRepository steuerberaterRepository;
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

    /**
     * Rufnummern-Verzeichnis: normalisierte Nummer → Kontakte (ohne Doppelte),
     * dazu Stammnummern von Durchwahl-Anlagen → Kontakte.
     */
    public static final class Verzeichnis {
        private final Map<String, List<KontaktKurzDto>> eintraege = new HashMap<>();
        private final Map<String, List<KontaktKurzDto>> stammnummern = new HashMap<>();

        void fuegeHinzu(String normalisiert, KontaktKurzDto kontakt) {
            fuegeHinzu(eintraege, normalisiert, kontakt);
        }

        void fuegeStammnummerHinzu(String stammnummer, KontaktKurzDto kontakt) {
            fuegeHinzu(stammnummern, stammnummer, kontakt);
        }

        private static void fuegeHinzu(Map<String, List<KontaktKurzDto>> ziel, String nummer, KontaktKurzDto kontakt) {
            if (nummer == null) {
                return;
            }
            List<KontaktKurzDto> liste = ziel.computeIfAbsent(nummer, k -> new ArrayList<>());
            boolean vorhanden = liste.stream().anyMatch(
                    k -> k.typ().equals(kontakt.typ()) && k.id().equals(kontakt.id()));
            if (!vorhanden) {
                liste.add(kontakt);
            }
        }

        /** Erst die exakte Nummer, sonst die längste Stammnummer, von der aus angerufen wurde. */
        public Treffer finde(String normalisiert) {
            if (normalisiert == null) {
                return new Treffer(List.of());
            }
            List<KontaktKurzDto> exakt = eintraege.get(normalisiert);
            if (exakt != null) {
                return new Treffer(List.copyOf(exakt));
            }
            for (int durchwahl = 0; durchwahl <= RufnummerNormalisierer.MAX_DURCHWAHL; durchwahl++) {
                int laenge = normalisiert.length() - durchwahl;
                if (laenge - 1 < RufnummerNormalisierer.MIN_STAMM_ZIFFERN) {
                    break;
                }
                List<KontaktKurzDto> ueberStamm = stammnummern.get(normalisiert.substring(0, laenge));
                if (ueberStamm != null) {
                    return new Treffer(List.copyOf(ueberStamm));
                }
            }
            return new Treffer(List.of());
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
        hebeZuordnungAuf(eintrag);
        return false;
    }

    /** Setzt einen Kontakt von Hand. Genau eins von kundeId/lieferantId/steuerberaterId. */
    public KontaktKurzDto ordneManuellZu(TelefonKontaktZuordenbar eintrag, Long kundeId, Long lieferantId,
                                         Long steuerberaterId) {
        KontaktKurzDto kontakt = ladeKontakt(kundeId, lieferantId, steuerberaterId);
        setzeKontakt(eintrag, kontakt, TelefonZuordnung.MANUELL);
        return kontakt;
    }

    public void hebeZuordnungAuf(TelefonKontaktZuordenbar eintrag) {
        eintrag.setKunde(null);
        eintrag.setLieferant(null);
        eintrag.setSteuerberater(null);
        eintrag.setZuordnung(TelefonZuordnung.KEINE);
    }

    /**
     * Merkt eine Rufnummer beim Kontakt, sofern sie dort noch nicht bekannt ist.
     *
     * @return true, wenn ein neuer Eintrag angelegt wurde
     */
    @Transactional
    public boolean merkeNummer(String nummerRoh, String normalisiert, Long kundeId, Long lieferantId,
                               Long steuerberaterId) {
        if (normalisiert == null) {
            return false;
        }
        boolean schonBekannt = kundeId != null
                ? kontaktRufnummerRepository.existsByKundeIdAndNummerNormalisiert(kundeId, normalisiert)
                : lieferantId != null
                ? kontaktRufnummerRepository.existsByLieferantIdAndNummerNormalisiert(lieferantId, normalisiert)
                : kontaktRufnummerRepository.existsBySteuerberaterIdAndNummerNormalisiert(steuerberaterId, normalisiert);
        if (schonBekannt) {
            return false;
        }
        KontaktRufnummer r = new KontaktRufnummer();
        if (kundeId != null) {
            r.setKunde(entityManager.getReference(Kunde.class, kundeId));
        } else if (lieferantId != null) {
            r.setLieferant(entityManager.getReference(Lieferanten.class, lieferantId));
        } else {
            r.setSteuerberater(entityManager.getReference(SteuerberaterKontakt.class, steuerberaterId));
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
        if (eintrag.getSteuerberater() != null) {
            SteuerberaterKontakt s = eintrag.getSteuerberater();
            return steuerberaterKurz(s.getId(), s.getName());
        }
        return null;
    }

    private static KontaktKurzDto steuerberaterKurz(Long id, String name) {
        return new KontaktKurzDto(KontaktKurzDto.STEUERBERATER, id, name, null, null);
    }

    private KontaktKurzDto ladeKontakt(Long kundeId, Long lieferantId, Long steuerberaterId) {
        int gesetzt = (kundeId != null ? 1 : 0) + (lieferantId != null ? 1 : 0) + (steuerberaterId != null ? 1 : 0);
        if (gesetzt != 1) {
            throw new IllegalArgumentException("Bitte genau einen Kunden, Lieferanten oder Steuerberater auswählen.");
        }
        if (steuerberaterId != null) {
            SteuerberaterKontakt s = steuerberaterRepository.findById(steuerberaterId)
                    .orElseThrow(() -> new IllegalArgumentException("Steuerberater nicht gefunden."));
            return steuerberaterKurz(s.getId(), s.getName());
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
        eintrag.setKunde(KontaktKurzDto.KUNDE.equals(kontakt.typ())
                ? entityManager.getReference(Kunde.class, kontakt.id()) : null);
        eintrag.setLieferant(KontaktKurzDto.LIEFERANT.equals(kontakt.typ())
                ? entityManager.getReference(Lieferanten.class, kontakt.id()) : null);
        eintrag.setSteuerberater(KontaktKurzDto.STEUERBERATER.equals(kontakt.typ())
                ? entityManager.getReference(SteuerberaterKontakt.class, kontakt.id()) : null);
        eintrag.setZuordnung(art);
    }

    private Verzeichnis baueVerzeichnis() {
        String land = einstellungen.landesvorwahl();
        String ort = einstellungen.ortsvorwahl();
        Verzeichnis v = new Verzeichnis();
        for (Object[] z : kundeRepository.findeTelefonverzeichnis()) {
            KontaktKurzDto k = new KontaktKurzDto(KontaktKurzDto.KUNDE, (Long) z[0], (String) z[1], (String) z[2], (String) z[3]);
            trageEin(v, (String) z[4], k, land, ort);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[5], land, ort), k);
        }
        for (Object[] z : lieferantenRepository.findeTelefonverzeichnis()) {
            KontaktKurzDto l = new KontaktKurzDto(KontaktKurzDto.LIEFERANT, (Long) z[0], (String) z[1], null, (String) z[2]);
            trageEin(v, (String) z[3], l, land, ort);
            v.fuegeHinzu(RufnummerNormalisierer.normalisiere((String) z[4], land, ort), l);
        }
        for (Object[] z : steuerberaterRepository.findeTelefonverzeichnis()) {
            trageEin(v, (String) z[2], steuerberaterKurz((Long) z[0], (String) z[1]), land, ort);
        }
        for (KontaktRufnummer r : kontaktRufnummerRepository.findAllMitKontakt()) {
            KontaktKurzDto k = kontaktVon(r);
            if (k != null) {
                v.fuegeHinzu(r.getNummerNormalisiert(), k);
                v.fuegeStammnummerHinzu(RufnummerNormalisierer.stammnummer(r.getNummerRoh(), land, ort), k);
            }
        }
        return v;
    }

    /**
     * Festnetznummer exakt eintragen – und, falls in Durchwahl-Schreibweise, ihre
     * Stammnummer. Handyfelder werden nur exakt eingetragen (keine Durchwahlen).
     */
    private static void trageEin(Verzeichnis v, String roh, KontaktKurzDto kontakt, String land, String ort) {
        v.fuegeHinzu(RufnummerNormalisierer.normalisiere(roh, land, ort), kontakt);
        v.fuegeStammnummerHinzu(RufnummerNormalisierer.stammnummer(roh, land, ort), kontakt);
    }

    private static KontaktKurzDto kontaktVon(KontaktRufnummer r) {
        if (r.getKunde() != null) {
            return new KontaktKurzDto(KontaktKurzDto.KUNDE, r.getKunde().getId(), r.getKunde().getName(),
                    r.getKunde().getKundennummer(), r.getKunde().getOrt());
        }
        if (r.getLieferant() != null) {
            return new KontaktKurzDto(KontaktKurzDto.LIEFERANT, r.getLieferant().getId(),
                    r.getLieferant().getLieferantenname(), null, r.getLieferant().getOrt());
        }
        if (r.getSteuerberater() != null) {
            return steuerberaterKurz(r.getSteuerberater().getId(), r.getSteuerberater().getName());
        }
        return null;
    }
}
