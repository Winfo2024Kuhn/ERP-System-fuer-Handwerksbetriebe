package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenzaehlungDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Kassensturz: das Bargeld in der Lade zaehlen und mit dem Kassenbuch
 * abgleichen.
 *
 * <p>Eine Barkasse muss jederzeit "kassensturzfaehig" sein -- gezaehltes
 * Geld und gefuehrtes Kassenbuch muessen zusammenpassen. Eine Abweichung ist
 * dabei nicht per se ein Problem; ein Problem ist eine Abweichung, die
 * niemand festgehalten hat.</p>
 *
 * <p>Zaehlungen werden nur angelegt, nie geaendert und nie geloescht. Der
 * Service bietet deshalb bewusst keinen Update-Pfad an -- eine Zaehlung, die
 * man hinterher zurechtruecken kann, beweist gar nichts.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KassenzaehlungService {

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Obergrenze fuer den Zaehlzettel -- schuetzt vor aufgeblaehten Requests. */
    private static final int MAX_STUECKELUNG_EINTRAEGE = 40;

    private final KassenzaehlungRepository zaehlungRepository;
    private final KassenbuchMonatsabschlussRepository abschlussRepository;
    private final BelegRepository belegRepository;
    private final KasseSaldoService kasseSaldoService;
    private final BelegAuditService auditService;

    /**
     * Nimmt eine Zaehlung auf, rechnet die Differenz zum Kassenbuch aus und
     * bucht sie auf Wunsch gleich aus.
     */
    @Transactional
    public KassenzaehlungDto.Response zaehle(KassenzaehlungDto.ZaehlRequest req,
                                             Mitarbeiter erfasser, String ipAdresse) {
        if (req == null) {
            throw new IllegalArgumentException("Anfrage fehlt");
        }
        LocalDate stichtag = req.getStichtag() != null ? req.getStichtag() : LocalDate.now();
        if (stichtag.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Es kann kein Bargeld für die Zukunft gezählt werden");
        }
        if (abschlussRepository.existsByJahrAndMonat(stichtag.getYear(), stichtag.getMonthValue())) {
            throw new KassenbuchGesperrtException(
                    "Der Monat des Stichtags ist bereits abgeschlossen.",
                    "Zähle auf einen Stichtag im laufenden Monat.");
        }
        if (req.getBemerkung() != null && req.getBemerkung().length() > 1000) {
            throw new IllegalArgumentException("Bemerkung zu lang (max. 1000 Zeichen)");
        }

        String stueckelungJson = null;
        BigDecimal gezaehlt;
        if (req.getStueckelung() != null && !req.getStueckelung().isEmpty()) {
            // Gezaehlt ist gezaehlt: liegt ein Zaehlzettel vor, gilt dessen
            // Summe -- auch wenn daneben eine abweichende Endsumme steht.
            Map<String, Integer> bereinigt = pruefeStueckelung(req.getStueckelung());
            gezaehlt = summiere(bereinigt);
            stueckelungJson = toJson(bereinigt);
        } else if (req.getGezaehlterBestand() != null) {
            gezaehlt = req.getGezaehlterBestand();
        } else {
            throw new IllegalArgumentException("Bitte den gezählten Betrag oder einen Zählzettel angeben");
        }

        gezaehlt = gezaehlt.setScale(2, RoundingMode.HALF_UP);
        if (gezaehlt.signum() < 0) {
            throw new IllegalArgumentException("In einer Kasse kann kein negativer Betrag liegen");
        }

        BigDecimal rechnerisch = kasseSaldoService.berechneSaldoBis(stichtag);
        BigDecimal differenz = gezaehlt.subtract(rechnerisch).setScale(2, RoundingMode.HALF_UP);

        boolean hatDifferenz = differenz.signum() != 0;
        if (hatDifferenz && (req.getBemerkung() == null || req.getBemerkung().isBlank())) {
            throw new IllegalArgumentException(
                    "Es fehlen " + differenz.abs() + " € gegenüber dem Kassenbuch. "
                            + "Bitte kurz festhalten, woran das liegt.");
        }

        Kassenzaehlung z = new Kassenzaehlung();
        z.setStichtag(stichtag);
        z.setGezaehltAm(LocalDateTime.now());
        z.setGezaehlterBestand(gezaehlt);
        z.setRechnerischerBestand(rechnerisch);
        z.setDifferenz(differenz);
        z.setStueckelungJson(stueckelungJson);
        z.setBemerkung(req.getBemerkung());
        z.setErfasstVon(erfasser);

        if (hatDifferenz && Boolean.TRUE.equals(req.getDifferenzBuchen())) {
            Beleg ausgleich = bucheDifferenz(differenz, stichtag, req.getBemerkung(), erfasser, ipAdresse);
            z.setAusgleichBelegId(ausgleich.getId());
        }

        z = zaehlungRepository.saveAndFlush(z);
        auditService.protokolliereKassenzaehlung(z, erfasser, ipAdresse);

        log.info("Kassensturz zum {}: gezählt {} €, laut Kassenbuch {} €, Differenz {} €",
                stichtag, gezaehlt, rechnerisch, differenz);
        return toDto(z);
    }

    @Transactional(readOnly = true)
    public List<KassenzaehlungDto.Response> liste(LocalDate von, LocalDate bis) {
        List<Kassenzaehlung> list = (von != null && bis != null)
                ? zaehlungRepository.findByStichtagBetweenOrderByStichtagAscIdAsc(von, bis)
                : zaehlungRepository.findTop20ByOrderByStichtagDescIdDesc();
        return list.stream().map(this::toDto).toList();
    }

    /** Was laut Kassenbuch heute in der Lade liegen muesste. */
    @Transactional(readOnly = true)
    public BigDecimal rechnerischerBestand(LocalDate stichtag) {
        return kasseSaldoService.berechneSaldoBis(stichtag != null ? stichtag : LocalDate.now());
    }

    // ===================== Intern =====================

    /**
     * Bucht eine festgestellte Differenz als Kassenueberschuss (zu viel Geld
     * in der Lade) oder Kassenfehlbetrag (zu wenig), damit gezaehlter und
     * rechnerischer Bestand danach wieder uebereinstimmen.
     *
     * <p>Der Mindestbestand wird hier bewusst <strong>nicht</strong> geprueft:
     * das Geld ist real schon weg, die Buchung bildet nur ab, was in der
     * Lade tatsaechlich liegt. Die Buchung zu blockieren wuerde das Kassenbuch
     * dauerhaft von der Wirklichkeit entkoppeln -- und genau das ist der
     * Zustand, den ein Kassensturz aufloesen soll.</p>
     */
    private Beleg bucheDifferenz(BigDecimal differenz, LocalDate stichtag, String bemerkung,
                                 Mitarbeiter erfasser, String ipAdresse) {
        boolean ueberschuss = differenz.signum() > 0;
        Beleg b = new Beleg();
        b.setStatus(BelegStatus.VALIDIERT);
        b.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
        b.setIstUmbuchung(true);
        b.setBelegKategorie(ueberschuss ? BelegKategorie.KASSE_EINNAHME : BelegKategorie.KASSE_AUSGABE);
        b.setBetragBrutto(differenz.abs());
        b.setBelegDatum(stichtag);
        b.setUploadDatum(LocalDateTime.now());
        b.setUploadedBy(erfasser);
        b.setValidiertAm(LocalDateTime.now());
        b.setValidiertVon(erfasser);
        String text = (ueberschuss ? "Kassenüberschuss" : "Kassenfehlbetrag")
                + " aus Zählung vom " + stichtag.format(TAG);
        if (bemerkung != null && !bemerkung.isBlank()) {
            text = text + " – " + bemerkung.trim();
        }
        b.setBeschreibung(text.length() > 500 ? text.substring(0, 500) : text);

        Beleg gespeichert = belegRepository.saveAndFlush(b);
        auditService.protokolliereErfassung(gespeichert, erfasser, ipAdresse);
        return gespeichert;
    }

    /**
     * Prueft den Zaehlzettel: Schluessel muessen positive Betraege sein
     * (Wert des Scheins oder der Muenze), Anzahlen nicht negativ. Eintraege
     * mit Anzahl 0 fliegen raus, damit der gespeicherte Zettel dem
     * entspricht, was tatsaechlich in der Lade lag.
     */
    private Map<String, Integer> pruefeStueckelung(Map<String, Integer> roh) {
        if (roh.size() > MAX_STUECKELUNG_EINTRAEGE) {
            throw new IllegalArgumentException("Zählzettel hat zu viele Positionen");
        }
        Map<String, Integer> bereinigt = new TreeMap<>(KassenzaehlungService::vergleicheBetragsSchluessel);
        for (Map.Entry<String, Integer> e : roh.entrySet()) {
            BigDecimal wert;
            try {
                wert = new BigDecimal(e.getKey().trim().replace(',', '.'));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("'" + e.getKey() + "' ist kein gültiger Geldschein-/Münzwert");
            }
            if (wert.signum() <= 0) {
                throw new IllegalArgumentException("Geldschein-/Münzwerte müssen größer als 0 sein");
            }
            Integer anzahl = e.getValue();
            if (anzahl == null || anzahl < 0) {
                throw new IllegalArgumentException("Anzahl darf nicht negativ sein");
            }
            if (anzahl == 0) continue;
            if (anzahl > 100_000) {
                throw new IllegalArgumentException("Anzahl ist unrealistisch hoch");
            }
            bereinigt.put(wert.stripTrailingZeros().toPlainString(), anzahl);
        }
        return bereinigt;
    }

    private BigDecimal summiere(Map<String, Integer> stueckelung) {
        BigDecimal summe = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> e : stueckelung.entrySet()) {
            summe = summe.add(new BigDecimal(e.getKey()).multiply(BigDecimal.valueOf(e.getValue())));
        }
        return summe;
    }

    /** Grosse Scheine zuerst -- so, wie man auch zaehlt. */
    private static int vergleicheBetragsSchluessel(String a, String b) {
        return new BigDecimal(b).compareTo(new BigDecimal(a));
    }

    /**
     * Minimaler JSON-Writer fuer den Zaehlzettel. Schluessel und Werte sind
     * an dieser Stelle bereits auf Zahlen geprueft, es kann also nichts
     * Ausbrechendes mehr im String stehen.
     */
    private String toJson(Map<String, Integer> stueckelung) {
        if (stueckelung.isEmpty()) return null;
        List<String> teile = new ArrayList<>(stueckelung.size());
        for (Map.Entry<String, Integer> e : stueckelung.entrySet()) {
            teile.add("\"" + e.getKey() + "\":" + e.getValue());
        }
        return "{" + String.join(",", teile) + "}";
    }

    private KassenzaehlungDto.Response toDto(Kassenzaehlung z) {
        return KassenzaehlungDto.Response.builder()
                .id(z.getId())
                .stichtag(z.getStichtag())
                .gezaehltAm(z.getGezaehltAm())
                .gezaehlterBestand(z.getGezaehlterBestand())
                .rechnerischerBestand(z.getRechnerischerBestand())
                .differenz(z.getDifferenz())
                .stueckelungJson(z.getStueckelungJson())
                .bemerkung(z.getBemerkung())
                .ausgleichBelegId(z.getAusgleichBelegId())
                .erfasstVon(BelegAuditService.anzeigeName(z.getErfasstVon()))
                .build();
    }
}
