package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenbuchAbschlussDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Schliesst Kassenbuch-Monate ab und wickelt Stornos ab.
 *
 * <p>Der Monatsabschluss ist der Moment, in dem aus Aufzeichnungen eine
 * Buchhaltung wird. Er tut drei Dinge auf einmal:</p>
 * <ol>
 *   <li>Jeder geprueften Buchung des Monats eine dauerhafte, lueckenlose
 *       Nummer geben.</li>
 *   <li>Diese Buchungen festschreiben -- ab hier sind Datum, Betrag, MwSt,
 *       Kategorie, Zahlungsart und Verwendungszweck gesperrt.</li>
 *   <li>Anfangs- und Endbestand des Monats festhalten, damit der naechste
 *       Monat lueckenlos daran anschliesst.</li>
 * </ol>
 *
 * <p>Ein abgeschlossener Monat wird nie wieder geoeffnet. Wer sich vertan
 * hat, bucht im laufenden Monat eine Gegenbuchung (Storno) und erfasst die
 * Sache neu -- die falsche Buchung bleibt dabei sichtbar stehen. Genau das
 * verlangt § 146 Abs. 4 AO: Aufzeichnungen duerfen nicht so veraendert
 * werden, dass der urspruengliche Inhalt nicht mehr feststellbar ist.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KassenbuchAbschlussService {

    /**
     * Untergrenze fuer den allerersten Abschluss. Der zieht alles mit, was je
     * erfasst wurde -- sonst blieben Altbelege dauerhaft unfestgeschrieben
     * zurueck und niemand wuerde sie je wieder anfassen.
     */
    private static final LocalDate BEGINN_DER_ZEITEN = LocalDate.of(1900, 1, 1);

    private final BelegRepository belegRepository;
    private final KassenbuchMonatsabschlussRepository abschlussRepository;
    private final BelegAuditService auditService;
    private final KasseSaldoService kasseSaldoService;

    // ===================== Vorschau =====================

    /**
     * Was passiert, wenn der Monat jetzt abgeschlossen wird -- und was
     * dagegen spricht. Das Frontend zeigt das vor dem Klick an, damit
     * niemand in eine Fehlermeldung laeuft.
     */
    @Transactional(readOnly = true)
    public KassenbuchAbschlussDto.Vorschau vorschau(int jahr, int monat) {
        YearMonth ym = pruefeMonat(jahr, monat);
        LocalDate bis = ym.atEndOfMonth();
        LocalDate von = ermittleZeitraumStart();

        List<String> hindernisse = sammleHindernisse(ym, von, bis);
        List<Beleg> festzuschreiben = hindernisse.isEmpty()
                ? belegRepository.findFestzuschreibendeImZeitraum(von, bis)
                : List.<Beleg>of();

        BigDecimal anfang = kasseSaldoService.berechneSaldoBis(von.minusDays(1));
        BigDecimal ende = kasseSaldoService.berechneSaldoBis(bis);

        return KassenbuchAbschlussDto.Vorschau.builder()
                .jahr(jahr)
                .monat(monat)
                .zeitraumVon(von.isEqual(BEGINN_DER_ZEITEN) ? null : von)
                .zeitraumBis(bis)
                .bereitsAbgeschlossen(abschlussRepository.existsByJahrAndMonat(jahr, monat))
                .anzahlFestzuschreiben(festzuschreiben.size())
                .anzahlUngeprueft((int) belegRepository.countUngeprueftImZeitraum(von, bis))
                .anzahlOhneDatum((int) belegRepository.countOhneBelegdatum())
                .anfangsbestand(anfang)
                .endbestand(ende)
                .hindernisse(hindernisse)
                .abschlussMoeglich(hindernisse.isEmpty())
                .build();
    }

    // ===================== Abschluss =====================

    /**
     * Schliesst den Monat ab. Laeuft in einer Transaktion: entweder bekommen
     * alle Belege ihre Nummer und ihre Festschreibung, oder keiner. Ein
     * halb abgeschlossener Monat waere schlimmer als gar keiner.
     */
    @Transactional
    public KassenbuchAbschlussDto.Ergebnis schliesseMonatAb(int jahr, int monat, String bemerkung,
                                                            Mitarbeiter bearbeiter, String ipAdresse) {
        YearMonth ym = pruefeMonat(jahr, monat);

        // Kettenkopf gleich zu Beginn sperren. Ohne das koennten zwei
        // gleichzeitige Abschlussversuche desselben Monats beide an der
        // "schon abgeschlossen?"-Pruefung vorbeikommen und erst ganz am Ende
        // am UNIQUE-Index auflaufen -- der Nutzer bekaeme einen Serverfehler
        // statt der verstaendlichen Meldung.
        auditService.sperreKette();

        LocalDate bis = ym.atEndOfMonth();
        LocalDate von = ermittleZeitraumStart();

        List<String> hindernisse = sammleHindernisse(ym, von, bis);
        if (!hindernisse.isEmpty()) {
            throw new KassenbuchGesperrtException(
                    "Der Monat kann noch nicht abgeschlossen werden: " + String.join(" ", hindernisse),
                    "Erst die offenen Punkte erledigen, dann erneut abschließen.");
        }
        if (bemerkung != null && bemerkung.length() > 1000) {
            throw new IllegalArgumentException("Bemerkung zu lang (max. 1000 Zeichen)");
        }

        List<Beleg> festzuschreiben = belegRepository.findFestzuschreibendeImZeitraum(von, bis);

        BigDecimal anfangsbestand = kasseSaldoService.berechneSaldoBis(von.minusDays(1));
        BigDecimal endbestand = kasseSaldoService.berechneSaldoBis(bis);
        BigDecimal summeEinnahmen = BigDecimal.ZERO;
        BigDecimal summeAusgaben = BigDecimal.ZERO;
        for (Beleg b : festzuschreiben) {
            BelegKategorie k = b.getBelegKategorie();
            if (k == null || !k.istKassenBewegung()) continue;
            BigDecimal brutto = nullSafe(b.getBetragBrutto());
            if (k.istAusgang()) {
                summeAusgaben = summeAusgaben.add(brutto);
            } else {
                summeEinnahmen = summeEinnahmen.add(brutto);
            }
        }

        // Erst den Abschluss anlegen: die Belege verweisen mit
        // monatsabschluss_id darauf, dafuer brauchen wir seine ID.
        KassenbuchMonatsabschluss abschluss = new KassenbuchMonatsabschluss();
        abschluss.setJahr(jahr);
        abschluss.setMonat(monat);
        abschluss.setAbgeschlossenAm(LocalDateTime.now());
        abschluss.setAbgeschlossenVon(bearbeiter);
        abschluss.setAnfangsbestand(scale(anfangsbestand));
        abschluss.setEndbestand(scale(endbestand));
        abschluss.setSummeEinnahmen(scale(summeEinnahmen));
        abschluss.setSummeAusgaben(scale(summeAusgaben));
        abschluss.setAnzahlBelege(festzuschreiben.size());
        abschluss.setBemerkung(bemerkung);
        abschluss = abschlussRepository.saveAndFlush(abschluss);

        LocalDateTime jetzt = LocalDateTime.now();
        Long ersteNummer = null;
        Long letzteNummer = null;
        for (Beleg b : festzuschreiben) {
            long nummer = auditService.zieheNaechsteLaufendeNummer();
            if (ersteNummer == null) ersteNummer = nummer;
            letzteNummer = nummer;

            b.setLaufendeNummer(nummer);
            b.setFestgeschrieben(true);
            b.setFestgeschriebenAm(jetzt);
            b.setFestgeschriebenVon(bearbeiter);
            b.setMonatsabschlussId(abschluss.getId());
            belegRepository.save(b);

            auditService.protokolliereFestschreibung(b, bearbeiter,
                    "Festgeschrieben mit Monatsabschluss " + monatLabel(ym)
                            + ", laufende Nummer " + nummer, ipAdresse);
        }

        abschluss.setErsteLaufendeNummer(ersteNummer);
        abschluss.setLetzteLaufendeNummer(letzteNummer);
        abschluss = abschlussRepository.saveAndFlush(abschluss);

        var auditEintrag = auditService.protokolliereMonatsabschluss(abschluss, bearbeiter, ipAdresse);
        abschluss.setChainIndex(auditEintrag.getChainIndex());
        abschluss.setEntryHash(auditEintrag.getEntryHash());
        abschluss = abschlussRepository.saveAndFlush(abschluss);

        log.info("Kassenbuch {} abgeschlossen: {} Belege festgeschrieben, Nummern {}..{}, Endbestand {}",
                monatLabel(ym), festzuschreiben.size(), ersteNummer, letzteNummer, endbestand);

        return toErgebnis(abschluss);
    }

    @Transactional(readOnly = true)
    public List<KassenbuchAbschlussDto.Ergebnis> listeAbschluesse() {
        return abschlussRepository.findAllByOrderByJahrDescMonatDesc().stream()
                .map(this::toErgebnis)
                .toList();
    }

    // ===================== Storno =====================

    /**
     * Storniert einen festgeschriebenen Beleg durch eine Gegenbuchung.
     *
     * <p>Das Original bleibt unangetastet im Kassenbuch stehen und bekommt
     * lediglich den Vermerk, durch welche Buchung es aufgehoben wurde. Die
     * Gegenbuchung faellt auf das heutige Datum -- sie gehoert in den
     * laufenden, offenen Monat, denn in einen abgeschlossenen Monat darf
     * nichts mehr hineingebucht werden.</p>
     *
     * <p>Bei Bar-Bewegungen dreht die Gegenbuchung die Richtung um (aus
     * einer Ausgabe wird eine Einnahme). Bei den uebrigen Kategorien gibt es
     * keine Richtung -- dort traegt die Gegenbuchung denselben Betrag mit
     * negativem Vorzeichen, damit sie sich in jeder Auswertung mit dem
     * Original aufhebt.</p>
     */
    @Transactional
    public Beleg storniere(Long belegId, String grund, Mitarbeiter bearbeiter, String ipAdresse) {
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Bitte kurz angeben, warum storniert wird");
        }
        if (grund.length() > 500) {
            throw new IllegalArgumentException("Stornogrund zu lang (max. 500 Zeichen)");
        }
        Beleg original = belegRepository.findById(belegId)
                .orElseThrow(() -> new IllegalArgumentException("Beleg nicht gefunden"));

        if (!original.istFestgeschrieben()) {
            throw new KassenbuchGesperrtException(
                    "Dieser Beleg ist noch nicht festgeschrieben.",
                    "Solange der Monat offen ist, kannst du den Beleg direkt korrigieren oder verwerfen.");
        }
        if (original.istStorniert()) {
            throw new KassenbuchGesperrtException(
                    "Dieser Beleg wurde bereits storniert.",
                    "Die Gegenbuchung steht schon im Kassenbuch.");
        }
        if (original.getStornoFuerBelegId() != null) {
            throw new KassenbuchGesperrtException(
                    "Eine Stornobuchung lässt sich nicht noch einmal stornieren.",
                    "Buche stattdessen den ursprünglichen Vorgang neu.");
        }

        LocalDate stornoDatum = LocalDate.now();
        if (abschlussRepository.existsByJahrAndMonat(stornoDatum.getYear(), stornoDatum.getMonthValue())) {
            throw new KassenbuchGesperrtException(
                    "Der laufende Monat ist bereits abgeschlossen – es kann nichts mehr hineingebucht werden.",
                    "Die Stornobuchung muss in einen offenen Monat fallen.");
        }

        Beleg storno = new Beleg();
        storno.setStatus(BelegStatus.VALIDIERT);
        storno.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE); // nichts auszulesen, ist eine reine Gegenbuchung
        storno.setIstUmbuchung(true);                          // keine eigene Belegdatei
        storno.setBelegDatum(stornoDatum);
        storno.setUploadDatum(LocalDateTime.now());
        storno.setUploadedBy(bearbeiter);
        storno.setValidiertAm(LocalDateTime.now());
        storno.setValidiertVon(bearbeiter);
        storno.setStornoFuerBelegId(original.getId());
        storno.setStornoGrund(grund);
        storno.setBelegNummer(original.getBelegNummer());
        storno.setZahlungsart(original.getZahlungsart());
        storno.setMwstSatz(original.getMwstSatz());
        storno.setSachkonto(original.getSachkonto());
        storno.setKostenstelle(original.getKostenstelle());
        storno.setLieferant(original.getLieferant());
        storno.setBeschreibung(stornoBeschreibung(original, grund));

        BelegKategorie originalKategorie = original.getBelegKategorie();
        if (originalKategorie != null && originalKategorie.istKassenBewegung()) {
            storno.setBelegKategorie(gegenrichtung(originalKategorie));
            storno.setBetragBrutto(nullSafe(original.getBetragBrutto()));
            storno.setBetragNetto(original.getBetragNetto());
        } else {
            storno.setBelegKategorie(originalKategorie);
            storno.setBetragBrutto(negiere(original.getBetragBrutto()));
            storno.setBetragNetto(negiere(original.getBetragNetto()));
        }

        // Eine stornierte Einnahme nimmt Geld aus der Kasse. Das darf den
        // Bestand nicht unter den eingestellten Mindestbestand druecken --
        // dieselbe Pruefung wie bei jeder anderen Bar-Buchung.
        if (storno.getBelegKategorie() != null && storno.getBelegKategorie().istKassenBewegung()) {
            BigDecimal projiziert = kasseSaldoService.projiziereSaldo(
                    null, null, storno.getBelegKategorie(), storno.getBetragBrutto());
            kasseSaldoService.assertSaldoMindestensMindestbestand(projiziert);
        }

        storno = belegRepository.saveAndFlush(storno);

        original.setStorniertDurchBelegId(storno.getId());
        original.setStorniertAm(LocalDateTime.now());
        original.setStornoGrund(grund);
        belegRepository.save(original);

        auditService.protokolliereStornoErfassung(storno, bearbeiter,
                "Gegenbuchung zu Beleg Nr. " + original.getLaufendeNummer() + ": " + grund, ipAdresse);
        auditService.protokolliereStornierung(original, bearbeiter, grund, ipAdresse);

        log.info("Beleg {} (Nr. {}) storniert durch Gegenbuchung {} -- {}",
                original.getId(), original.getLaufendeNummer(), storno.getId(), grund);
        return storno;
    }

    // ===================== Hilfen =====================

    /**
     * Alles, was einem Abschluss im Weg steht -- in Handwerker-Sprache, weil
     * der Text direkt im Frontend landet.
     */
    private List<String> sammleHindernisse(YearMonth ym, LocalDate von, LocalDate bis) {
        List<String> h = new ArrayList<>();

        if (abschlussRepository.existsByJahrAndMonat(ym.getYear(), ym.getMonthValue())) {
            h.add(monatLabel(ym) + " ist bereits abgeschlossen.");
            return h; // alles Weitere waere nur noch Rauschen
        }
        if (!bis.isBefore(LocalDate.now())) {
            h.add(monatLabel(ym) + " ist noch nicht vorbei – abschließen geht erst ab dem Ersten des Folgemonats.");
        }

        Optional<KassenbuchMonatsabschluss> letzter = abschlussRepository.findFirstByOrderByJahrDescMonatDesc();
        if (letzter.isPresent()) {
            YearMonth erwartet = YearMonth.of(letzter.get().getJahr(), letzter.get().getMonat()).plusMonths(1);
            if (!erwartet.equals(ym)) {
                h.add("Als Nächstes ist " + monatLabel(erwartet) + " an der Reihe – "
                        + "die Monate müssen lückenlos aufeinander folgen.");
            }
        }

        long ungeprueft = belegRepository.countUngeprueftImZeitraum(von, bis);
        if (ungeprueft > 0) {
            h.add("Es warten noch " + ungeprueft + " ungeprüfte Belege in diesem Zeitraum.");
        }
        long ohneDatum = belegRepository.countOhneBelegdatum();
        if (ohneDatum > 0) {
            h.add(ohneDatum + " Belege haben kein Datum und gehören damit in keinen Monat.");
        }
        // Belege, die vor dem Abschlusszeitraum liegen und trotzdem noch offen
        // sind, wuerden von diesem und jedem folgenden Abschluss uebersehen.
        // Sie muessen erst storniert oder verworfen werden, sonst bliebe ein
        // Stueck Kassenbuch dauerhaft ausserhalb der Festschreibung.
        if (!von.isEqual(BEGINN_DER_ZEITEN)) {
            long waisen = belegRepository.countNichtFestgeschriebenVor(von);
            if (waisen > 0) {
                h.add(waisen + " geprüfte Belege liegen vor diesem Zeitraum und sind noch nicht "
                        + "fest gebucht. Sie würden von keinem Abschluss mehr erfasst – "
                        + "bitte zuerst klären.");
            }
        }
        return h;
    }

    /**
     * Ab wann der Abschluss greift. Nach dem letzten Abschluss geht es
     * nahtlos weiter; beim allerersten Mal wird alles mitgenommen, was je
     * erfasst wurde.
     */
    private LocalDate ermittleZeitraumStart() {
        return abschlussRepository.findFirstByOrderByJahrDescMonatDesc()
                .map(m -> YearMonth.of(m.getJahr(), m.getMonat()).plusMonths(1).atDay(1))
                .orElse(BEGINN_DER_ZEITEN);
    }

    private YearMonth pruefeMonat(int jahr, int monat) {
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Monat muss zwischen 1 und 12 liegen");
        }
        if (jahr < 2000 || jahr > 2200) {
            throw new IllegalArgumentException("Jahr liegt außerhalb des gültigen Bereichs");
        }
        return YearMonth.of(jahr, monat);
    }

    private static BelegKategorie gegenrichtung(BelegKategorie k) {
        return switch (k) {
            case KASSE_EINNAHME -> BelegKategorie.KASSE_AUSGABE;
            case KASSE_AUSGABE -> BelegKategorie.KASSE_EINNAHME;
            case PRIVATENTNAHME -> BelegKategorie.PRIVATEINLAGE;
            case PRIVATEINLAGE -> BelegKategorie.PRIVATENTNAHME;
            default -> k;
        };
    }

    private String stornoBeschreibung(Beleg original, String grund) {
        String basis = original.getBeschreibung() != null && !original.getBeschreibung().isBlank()
                ? original.getBeschreibung().trim()
                : "Buchung";
        String nummer = original.getLaufendeNummer() != null
                ? ("Nr. " + original.getLaufendeNummer())
                : ("ID " + original.getId());
        String text = "Storno zu " + nummer + ": " + basis + " (" + grund.trim() + ")";
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private KassenbuchAbschlussDto.Ergebnis toErgebnis(KassenbuchMonatsabschluss m) {
        return KassenbuchAbschlussDto.Ergebnis.builder()
                .id(m.getId())
                .jahr(m.getJahr())
                .monat(m.getMonat())
                .abgeschlossenAm(m.getAbgeschlossenAm())
                .abgeschlossenVon(BelegAuditService.anzeigeName(m.getAbgeschlossenVon()))
                .anfangsbestand(m.getAnfangsbestand())
                .endbestand(m.getEndbestand())
                .summeEinnahmen(m.getSummeEinnahmen())
                .summeAusgaben(m.getSummeAusgaben())
                .anzahlBelege(m.getAnzahlBelege())
                .ersteLaufendeNummer(m.getErsteLaufendeNummer())
                .letzteLaufendeNummer(m.getLetzteLaufendeNummer())
                .chainIndex(m.getChainIndex())
                .entryHash(m.getEntryHash())
                .bemerkung(m.getBemerkung())
                .build();
    }

    private static String monatLabel(YearMonth ym) {
        String[] monate = {"Januar", "Februar", "März", "April", "Mai", "Juni",
                "Juli", "August", "September", "Oktober", "November", "Dezember"};
        return monate[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal negiere(BigDecimal v) {
        return v == null ? null : v.negate();
    }

    private static BigDecimal scale(BigDecimal v) {
        return nullSafe(v).setScale(2, RoundingMode.HALF_UP);
    }
}
