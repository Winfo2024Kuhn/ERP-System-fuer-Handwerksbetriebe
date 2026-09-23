package org.example.kalkulationsprogramm.service.einkauf;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMengenbuchung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.Mengenstand;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMengenbuchungRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EinkaufMengenService {
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("9999999999999.999999");
    public enum Mengenaktion { RESERVIEREN, RESERVIERUNG_FREIGEBEN, BESTELLEN, LAGER_ENTNEHMEN,
        STORNO_BESTAETIGEN, LIEFERN }

    private final EinkaufBedarfRepository bedarfRepository;
    private final EinkaufMengenbuchungRepository buchungRepository;
    private EinkaufAnfrageMengenProvider angefragtMengenProvider;

    @Autowired(required = false)
    public void setAngefragtMengenProvider(EinkaufAnfrageMengenProvider provider) {
        this.angefragtMengenProvider = provider;
    }

    public EinkaufMengenService(EinkaufBedarfRepository bedarfRepository,
            EinkaufMengenbuchungRepository buchungRepository) {
        this.bedarfRepository = bedarfRepository;
        this.buchungRepository = buchungRepository;
    }

    /**
     * The process key is a stable immutable reference shared by all stages of
     * one persisted purchase order: {@code BESTELLUNG:<bestellungId>}. Keep it
     * unchanged from reservation through order conversion and release; each
     * individual mutation still gets its own idempotency UUID.
     */
    @Transactional
    public void buche(List<Herkunft> anteile, Mengenaktion aktion, String vorgangsschluessel,
            UUID idempotenzKey, Long akteurId) {
        validiere(anteile, aktion, vorgangsschluessel, idempotenzKey, akteurId);
        List<Herkunft> sortiert = anteile.stream().sorted(Comparator.comparing(Herkunft::bedarfId)).toList();
        String payload = payload(sortiert, aktion, vorgangsschluessel, akteurId);
        String hash = sha256(payload);
        List<Long> ids = sortiert.stream().map(Herkunft::bedarfId).distinct().toList();
        List<EinkaufBedarf> gesperrt = bedarfRepository.findeAlleFuerUpdate(ids);
        if (gesperrt.size() != ids.size()) throw new org.example.kalkulationsprogramm.exception.NotFoundException(
                "Mindestens ein Einkaufsbedarf fehlt.");
        // Recheck after taking all origin locks: concurrent retries with the same key
        // wait here and then return the first transaction's committed result.
        List<EinkaufMengenbuchung> bestehend = buchungRepository.findAllByIdempotenzKey(idempotenzKey);
        if (!bestehend.isEmpty()) {
            if (bestehend.stream().anyMatch(b -> !hash.equals(b.getPayloadHash()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Der Idempotenzschlüssel wurde bereits für andere Mengen verwendet.");
            }
            return;
        }
        Map<Long, EinkaufBedarf> nachId = gesperrt.stream().collect(Collectors.toMap(EinkaufBedarf::getId,
                Function.identity()));
        Map<Long, BigDecimal> reservierungenEigenerVorgaenge = ids.stream().collect(Collectors.toMap(
                Function.identity(), id -> reservierungFuer(id, vorgangsschluessel)));
        for (Herkunft herkunft : sortiert) {
            EinkaufBedarf bedarf = nachId.get(herkunft.bedarfId());
            if (bedarf.getVersion() == null || bedarf.getVersion() != herkunft.version()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Der Einkaufsbedarf wurde zwischenzeitlich geändert.");
            }
            pruefeBuchung(bedarf, aktion, herkunft.menge(), reservierungenEigenerVorgaenge.get(bedarf.getId()));
        }
        List<EinkaufMengenbuchung> buchungen = new ArrayList<>();
        for (Herkunft herkunft : sortiert) {
            EinkaufBedarf bedarf = nachId.get(herkunft.bedarfId());
            BigDecimal amount = herkunft.menge().setScale(6);
            wendeAn(bedarf, aktion, amount);
            buchungen.add(new EinkaufMengenbuchung(bedarf, aktion, amount, vorgangsschluessel,
                    idempotenzKey, hash, akteurId));
        }
        bedarfRepository.saveAll(gesperrt);
        buchungRepository.saveAll(buchungen);
    }

    @Transactional(readOnly = true)
    public Mengenstand stand(Long bedarfId) {
        if (bedarfId == null || bedarfId <= 0) throw new IllegalArgumentException("Die Bedarfs-ID ist ungültig.");
        EinkaufBedarf b = bedarfRepository.findById(bedarfId)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        Map<Long, BigDecimal> angefragtMengen = angefragtMengenProvider == null ? Map.of()
                : angefragtMengenProvider.angefragtFuerBedarfe(List.of(b.getId()));
        BigDecimal angefragt = angefragtMengen == null ? BigDecimal.ZERO : angefragtMengen.get(b.getId());
        return new Mengenstand(b.getBedarfMenge(), b.getLagergedeckt(), angefragt == null ? BigDecimal.ZERO : angefragt, b.getReserviert(),
                b.getBestellt(), b.getGeliefert(), b.getStorniert(), b.ungedeckt(), b.disponierbar());
    }

    private static void validiere(List<Herkunft> anteile, Mengenaktion aktion, String vorgang, UUID key, Long actor) {
        if (anteile == null || anteile.isEmpty() || anteile.stream().anyMatch(h -> h == null || h.bedarfId() == null
                || h.bedarfId() <= 0 || h.menge() == null || h.menge().signum() <= 0 || h.version() < 0
                || !istGueltigeMenge(h.menge()))) {
            throw new IllegalArgumentException("Bitte geben Sie gültige Bedarfsanteile und positive Mengen an.");
        }
        if (anteile.stream().map(Herkunft::bedarfId).distinct().count() != anteile.size()) {
            throw new IllegalArgumentException("Ein Bedarf darf nur einmal in einer Mengenbuchung vorkommen.");
        }
        if (aktion == null || vorgang == null || vorgang.isBlank() || vorgang.length() > 128
                || key == null || actor == null || actor <= 0) {
            throw new IllegalArgumentException("Aktion, Vorgang, Idempotenzschlüssel und Benutzer sind erforderlich.");
        }
    }

    private static boolean istGueltigeMenge(BigDecimal menge) {
        if (menge.compareTo(MAX_DECIMAL) > 0) return false;
        try { menge.setScale(6, RoundingMode.UNNECESSARY); return true; }
        catch (ArithmeticException ignored) { return false; }
    }

    private BigDecimal reservierungFuer(Long bedarfId, String vorgangsschluessel) {
        BigDecimal reserviert = BigDecimal.ZERO;
        for (EinkaufMengenbuchung buchung : buchungRepository
                .findAllByBedarf_IdAndVorgangsschluesselOrderByIdAsc(bedarfId, vorgangsschluessel)) {
            switch (buchung.getAktion()) {
                case RESERVIEREN -> reserviert = reserviert.add(buchung.getMenge());
                case RESERVIERUNG_FREIGEBEN -> reserviert = reserviert.subtract(buchung.getMenge());
                case BESTELLEN -> reserviert = reserviert.subtract(buchung.getMenge());
                default -> { }
            }
        }
        return reserviert.max(BigDecimal.ZERO);
    }

    private static void pruefeBuchung(EinkaufBedarf b, Mengenaktion a, BigDecimal menge,
            BigDecimal reservierungDieserVorgang) {
        if (b.isNachpflegeErforderlich() || b.getBedarfMenge() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Bedarf muss zuerst nachgepflegt werden.");
        }
        boolean gueltig = switch (a) {
            case RESERVIEREN, LAGER_ENTNEHMEN -> menge.compareTo(b.disponierbar()) <= 0;
            case RESERVIERUNG_FREIGEBEN -> menge.compareTo(reservierungDieserVorgang) <= 0;
            case BESTELLEN -> menge.compareTo(reservierungDieserVorgang) <= 0;
            case STORNO_BESTAETIGEN -> menge.compareTo(b.getBestellt().subtract(b.getGeliefert())) <= 0;
            case LIEFERN -> menge.compareTo(b.getBestellt().subtract(b.getGeliefert())) <= 0;
        };
        if (!gueltig) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Die Menge überschreitet den noch verfügbaren Bedarf.");
    }

    private static void wendeAn(EinkaufBedarf b, Mengenaktion a, BigDecimal m) {
        switch (a) {
            case RESERVIEREN -> b.setReserviert(b.getReserviert().add(m));
            case RESERVIERUNG_FREIGEBEN -> b.setReserviert(b.getReserviert().subtract(m));
            case BESTELLEN -> {
                b.setReserviert(b.getReserviert().subtract(m));
                b.setBestellt(b.getBestellt().add(m));
            }
            case LAGER_ENTNEHMEN -> b.setLagergedeckt(b.getLagergedeckt().add(m));
            case STORNO_BESTAETIGEN -> {
                b.setBestellt(b.getBestellt().subtract(m));
                b.setStorniert(b.getStorniert().add(m));
            }
            case LIEFERN -> b.setGeliefert(b.getGeliefert().add(m));
        }
    }

    private static String payload(List<Herkunft> items, Mengenaktion a, String process, Long actor) {
        StringBuilder out = new StringBuilder(a.name()).append('|').append(process).append('|').append(actor);
        for (Herkunft h : items) out.append('|').append(h.bedarfId()).append(':').append(h.version())
                .append(':').append(h.menge().stripTrailingZeros().toPlainString());
        return out.toString();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 fehlt.", e); }
    }
}
