package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Krankenkasse;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterStundenlohn;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.domain.SvSatz;
import org.example.kalkulationsprogramm.domain.SvSatzTyp;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.AbteilungVorschlag;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.DatenLuecke;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.KostenstelleAnteil;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.LohnQuelle;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.Modus;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnUebernehmenRequest;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository;
import org.example.kalkulationsprogramm.repository.SvSatzRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Berechnet den Verrechnungslohn (Stundensatz) eines Jahres aus Lohn-Block,
 * Verkaeuflichen-Stunden-Block und Gemeinkosten-Block.
 *
 * Modus RUECKWIRKEND nutzt Ist-Daten (Lohnabrechnungen + Zeitbuchungen),
 * Modus HOCHRECHNUNG nutzt Stammstundenlohn × Sollstunden + Defaults und
 * markiert die Default-Werte fuer die UI ueber das DTO.
 */
@Service
@AllArgsConstructor
public class VerrechnungslohnService {

    private static final BigDecimal STUNDEN_PRO_TAG_DEFAULT = new BigDecimal("8.00");
    private static final BigDecimal KRANKHEITSTAGE_DEFAULT = new BigDecimal("8.00");
    private static final BigDecimal INTERNE_QUOTE_DEFAULT = new BigDecimal("0.05");
    private static final String BUNDESLAND_DEFAULT = "BY";
    private static final List<ProjektArt> PRODUKTIVE_PROJEKTARTEN =
            Arrays.stream(ProjektArt.values()).filter(ProjektArt::isProduktiv).toList();
    private static final List<ProjektArt> UNPRODUKTIVE_PROJEKTARTEN =
            Arrays.stream(ProjektArt.values()).filter(p -> !p.isProduktiv()).toList();

    private final MitarbeiterRepository mitarbeiterRepository;
    private final MitarbeiterStundenlohnRepository stundenlohnRepository;
    private final LohnabrechnungRepository lohnabrechnungRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitkontoRepository zeitkontoRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagRepository feiertagRepository;
    private final SvSatzRepository svSatzRepository;
    private final FirmeninformationRepository firmeninformationRepository;
    private final LieferantDokumentProjektAnteilRepository anteilRepository;
    private final AbteilungRepository abteilungRepository;
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final ArbeitsgangStundensatzRepository stundensatzRepository;
    private final BelegRepository belegRepository;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;

    @Transactional(readOnly = true)
    public VerrechnungslohnErgebnisDto berechne(int jahr) {
        return berechne(jahr, null);
    }

    /**
     * @param interneQuoteProzent Anteil interner (nicht verkaufbarer) Arbeitszeit
     *                            in Prozent, 0-100. null = Standardwert. Wirkt nur
     *                            im Modus HOCHRECHNUNG; rueckwirkend kommen die
     *                            internen Stunden aus den echten Zeitbuchungen.
     */
    @Transactional(readOnly = true)
    public VerrechnungslohnErgebnisDto berechne(int jahr, Integer interneQuoteProzent) {
        VerrechnungslohnErgebnisDto dto = new VerrechnungslohnErgebnisDto();
        dto.setJahr(jahr);
        Modus modus = jahr < Year.now().getValue() ? Modus.RUECKWIRKEND : Modus.HOCHRECHNUNG;
        dto.setModus(modus);

        BigDecimal interneQuote = normalisiereInterneQuote(interneQuoteProzent);
        dto.setInterneQuoteProzent(interneQuote.multiply(BigDecimal.valueOf(100)).intValue());

        LocalDate jahresStart = LocalDate.of(jahr, 1, 1);
        LocalDate jahresEnde = LocalDate.of(jahr, 12, 31);
        Firmeninformation firma = firmeninformationRepository.findById(1L).orElse(null);
        BigDecimal bgSatz = ermittleBgSatz(firma);
        SvKontext svKontext = ladeSvKontext(jahresStart);
        Set<LocalDate> feiertageWerktag = ladeFeiertage(jahr);

        List<Mitarbeiter> aktive = mitarbeiterRepository.findByAktivTrue();
        BigDecimal lohnsumme = BigDecimal.ZERO;
        BigDecimal stundenSumme = BigDecimal.ZERO;

        for (Mitarbeiter ma : aktive) {
            MitarbeiterLohnZeile lohnZeile = berechneLohnZeile(ma, jahr, modus, svKontext, bgSatz, dto.getDatenLuecken());
            dto.getLohnzeilen().add(lohnZeile);
            lohnsumme = lohnsumme.add(lohnZeile.getGesamtkosten());

            MitarbeiterStundenZeile stdZeile = berechneStundenZeile(ma, jahr, jahresStart, jahresEnde, modus, feiertageWerktag, interneQuote, dto.getDatenLuecken());
            dto.getStundenzeilen().add(stdZeile);
            stundenSumme = stundenSumme.add(stdZeile.getVerkaeuflicheStunden());
        }

        BigDecimal gemeinkosten = berechneGemeinkosten(dto.getKostenstellen(), jahr);

        dto.setLohnsummeGesamt(lohnsumme.setScale(2, RoundingMode.HALF_UP));
        dto.setVerkaeuflicheStundenGesamt(stundenSumme.setScale(2, RoundingMode.HALF_UP));
        dto.setGemeinkostenGesamt(gemeinkosten.setScale(2, RoundingMode.HALF_UP));

        BigDecimal selbstkosten = BigDecimal.ZERO;
        if (stundenSumme.compareTo(BigDecimal.ZERO) > 0) {
            selbstkosten = lohnsumme.add(gemeinkosten).divide(stundenSumme, 2, RoundingMode.HALF_UP);
        }
        dto.setSelbstkostenProStunde(selbstkosten);

        for (Abteilung abt : abteilungRepository.findAll()) {
            AbteilungVorschlag vorschlag = new AbteilungVorschlag();
            vorschlag.setAbteilungId(abt.getId());
            vorschlag.setName(abt.getName());
            vorschlag.setAufschlagEuro(BigDecimal.ZERO);
            dto.getAbteilungen().add(vorschlag);
        }

        return dto;
    }

    @Transactional
    public int uebernehmen(VerrechnungslohnUebernehmenRequest request) {
        if (request.getBasisSatz() == null) {
            throw new IllegalArgumentException("basisSatz darf nicht null sein");
        }
        Map<Long, BigDecimal> aufschlaegeProAbteilung = new HashMap<>();
        if (request.getAbteilungAufschlaege() != null) {
            for (VerrechnungslohnUebernehmenRequest.AbteilungAufschlag a : request.getAbteilungAufschlaege()) {
                if (a.getAbteilungId() != null && a.getAufschlagEuro() != null) {
                    aufschlaegeProAbteilung.put(a.getAbteilungId(), a.getAufschlagEuro());
                }
            }
        }

        int aktualisiert = 0;
        for (Arbeitsgang ag : arbeitsgangRepository.findAll()) {
            BigDecimal aufschlag = BigDecimal.ZERO;
            if (ag.getAbteilung() != null && ag.getAbteilung().getId() != null) {
                aufschlag = aufschlaegeProAbteilung.getOrDefault(ag.getAbteilung().getId(), BigDecimal.ZERO);
            }
            BigDecimal satz = request.getBasisSatz().add(aufschlag).setScale(2, RoundingMode.HALF_UP);
            if (satz.signum() <= 0) {
                // Ein zu grosser Abschlag darf keinen negativen -- und auch keinen
                // Null- -- Stundensatz auf die Arbeitsgaenge schreiben. Beides
                // zerstoert die Kalkulation stillschweigend. @Transactional rollt
                // bereits geschriebene Saetze zurueck.
                String abteilung = ag.getAbteilung() != null ? ag.getAbteilung().getName() : "ohne Abteilung";
                throw new IllegalArgumentException(
                        "Der Abschlag fuer die Abteilung \"" + abteilung + "\" ist zu gross und wuerde einen"
                                + " Stundensatz von " + satz + " EUR ergeben. Bitte den Abschlag verringern.");
            }

            ArbeitsgangStundensatz eintrag = stundensatzRepository
                    .findTopByArbeitsgangIdAndJahrOrderByIdDesc(ag.getId(), request.getJahr())
                    .orElseGet(ArbeitsgangStundensatz::new);
            eintrag.setArbeitsgang(ag);
            eintrag.setJahr(request.getJahr());
            eintrag.setSatz(satz);
            stundensatzRepository.save(eintrag);
            aktualisiert++;
        }
        return aktualisiert;
    }

    // ==================== Lohn-Block ====================

    private MitarbeiterLohnZeile berechneLohnZeile(Mitarbeiter ma,
                                                   int jahr,
                                                   Modus modus,
                                                   SvKontext svKontext,
                                                   BigDecimal bgSatz,
                                                   List<DatenLuecke> luecken) {
        MitarbeiterLohnZeile zeile = new MitarbeiterLohnZeile();
        zeile.setMitarbeiterId(ma.getId());
        zeile.setName(ma.getVorname() + " " + ma.getNachname());
        zeile.setIstGeschaeftsfuehrer(Boolean.TRUE.equals(ma.getIstGeschaeftsfuehrer()));
        zeile.setBeschaeftigungsart(ma.getBeschaeftigungsart() != null ? ma.getBeschaeftigungsart().name() : null);

        if (zeile.isIstGeschaeftsfuehrer()) {
            BigDecimal kalk = nz(ma.getKalkulatorischerLohnMonat()).multiply(BigDecimal.valueOf(12));
            BigDecimal vorteil = nz(ma.getGeldwertVorteilMonat()).multiply(BigDecimal.valueOf(12));
            zeile.setBruttoJahr(kalk);
            zeile.setGeldwerterVorteilJahr(vorteil);
            zeile.setQuelle(LohnQuelle.KALKULATORISCH);
            BigDecimal gesamt = kalk.add(vorteil);
            // Fremd-GF mit SV-Pflicht: trotzdem AG-Anteile auf Brutto plus geldwerten
            // Vorteil rechnen (Firmenwagen etc. sind in DE i.d.R. SV-pflichtig).
            if (ma.getBeschaeftigungsart() == Beschaeftigungsart.GF_SV_PFLICHTIG) {
                BigDecimal svBasis = kalk.add(vorteil);
                BigDecimal agSv = berechneAgAnteilSv(svBasis, ma, svKontext);
                BigDecimal bg = berechneBg(svBasis, bgSatz);
                zeile.setAgAnteilSv(agSv);
                zeile.setBgBeitrag(bg);
                gesamt = gesamt.add(agSv).add(bg);
            }
            zeile.setGesamtkosten(gesamt.setScale(2, RoundingMode.HALF_UP));
            return zeile;
        }

        BigDecimal brutto = ermittleBrutto(ma, jahr, modus, zeile, luecken);
        zeile.setBruttoJahr(brutto);
        BigDecimal agSv = berechneAgAnteilSv(brutto, ma, svKontext);
        BigDecimal bg = berechneBg(brutto, bgSatz);
        zeile.setAgAnteilSv(agSv);
        zeile.setBgBeitrag(bg);
        zeile.setGesamtkosten(brutto.add(agSv).add(bg).setScale(2, RoundingMode.HALF_UP));
        return zeile;
    }

    private BigDecimal ermittleBrutto(Mitarbeiter ma,
                                      int jahr,
                                      Modus modus,
                                      MitarbeiterLohnZeile zeile,
                                      List<DatenLuecke> luecken) {
        if (modus == Modus.RUECKWIRKEND) {
            BigDecimal sum = nz(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(ma.getId(), jahr));
            long anzahl = lohnabrechnungRepository.countByMitarbeiterIdAndJahr(ma.getId(), jahr);
            if (sum.compareTo(BigDecimal.ZERO) > 0 && anzahl >= 12) {
                zeile.setQuelle(LohnQuelle.LOHNABRECHNUNG);
                return sum;
            }
            if (sum.compareTo(BigDecimal.ZERO) > 0 && anzahl > 0) {
                DatenLuecke l = new DatenLuecke();
                l.setMitarbeiterId(ma.getId());
                l.setMitarbeiterName(zeile.getName());
                l.setProblem("Nur " + anzahl + " von 12 Lohnabrechnungen vorhanden - Brutto wird hochgerechnet");
                luecken.add(l);
                BigDecimal hoch = sum.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(anzahl), 2, RoundingMode.HALF_UP);
                zeile.setQuelle(LohnQuelle.LOHNABRECHNUNG);
                zeile.setBruttoIstDefault(true);
                return hoch;
            }
            DatenLuecke l = new DatenLuecke();
            l.setMitarbeiterId(ma.getId());
            l.setMitarbeiterName(zeile.getName());
            l.setProblem("Keine Lohnabrechnungen fuer " + jahr + " - Stammstundenlohn als Default");
            luecken.add(l);
        }
        BigDecimal hochgerechnet = hochrechnungAusStundenlohn(ma, jahr);
        zeile.setQuelle(modus == Modus.RUECKWIRKEND ? LohnQuelle.STAMMSTUNDENLOHN : LohnQuelle.STUNDENLOHN_HOCHRECHNUNG);
        zeile.setBruttoIstDefault(true);
        return hochgerechnet;
    }

    private BigDecimal hochrechnungAusStundenlohn(Mitarbeiter ma, int jahr) {
        LocalDate stichtag = LocalDate.of(jahr, 1, 1);
        Optional<MitarbeiterStundenlohn> versionOpt = stundenlohnRepository
                .findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(ma.getId(), stichtag.plusYears(1).minusDays(1));
        BigDecimal stundenlohn = versionOpt.map(MitarbeiterStundenlohn::getStundenlohn)
                .orElse(nz(ma.getStundenlohn()));
        BigDecimal jahresSoll = jahresSollstunden(ma);
        return stundenlohn.multiply(jahresSoll).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal berechneAgAnteilSv(BigDecimal brutto, Mitarbeiter ma, SvKontext svKontext) {
        if (brutto == null || brutto.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        Beschaeftigungsart art = ma.getBeschaeftigungsart() != null ? ma.getBeschaeftigungsart() : Beschaeftigungsart.REGULAER;
        if (art == Beschaeftigungsart.GF_SV_FREI) {
            return BigDecimal.ZERO;
        }
        if (art == Beschaeftigungsart.MINIJOB) {
            BigDecimal gesamtProzent = svKontext.minijobAgKv
                    .add(svKontext.minijobAgRv)
                    .add(svKontext.minijobAgPauschal)
                    .add(svKontext.u1)
                    .add(svKontext.u2)
                    .add(svKontext.insolvenzgeldUmlage);
            return prozentVon(brutto, gesamtProzent);
        }
        BigDecimal kvAg = svKontext.kvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal pvAg = svKontext.pvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal rvAg = svKontext.rvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal avAg = svKontext.avGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal kkZusatzAg = ermittleKkZusatzAgAnteil(ma);
        BigDecimal gesamtProzent = kvAg.add(pvAg).add(rvAg).add(avAg).add(kkZusatzAg)
                .add(svKontext.u1).add(svKontext.u2).add(svKontext.insolvenzgeldUmlage);
        return prozentVon(brutto, gesamtProzent);
    }

    private BigDecimal ermittleKkZusatzAgAnteil(Mitarbeiter ma) {
        Krankenkasse kk = ma.getKrankenkasse();
        if (kk == null || kk.getZusatzbeitragProzent() == null) {
            return BigDecimal.ZERO;
        }
        return kk.getZusatzbeitragProzent().divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal berechneBg(BigDecimal brutto, BigDecimal bgProzent) {
        if (brutto == null || brutto.compareTo(BigDecimal.ZERO) <= 0 || bgProzent == null) return BigDecimal.ZERO;
        return prozentVon(brutto, bgProzent);
    }

    private BigDecimal ermittleBgSatz(Firmeninformation firma) {
        if (firma == null) return BigDecimal.ZERO;
        if (firma.getBgSatzOverride() != null) {
            return firma.getBgSatzOverride();
        }
        if (firma.getGewerk() != null && firma.getGewerk().getBgSatzProzent() != null) {
            return firma.getGewerk().getBgSatzProzent();
        }
        return BigDecimal.ZERO;
    }

    private SvKontext ladeSvKontext(LocalDate stichtag) {
        SvKontext k = new SvKontext();
        k.kvGesamt = lookupSvProzent(SvSatzTyp.KV_GESAMT, stichtag);
        k.pvGesamt = lookupSvProzent(SvSatzTyp.PV_GESAMT, stichtag);
        k.rvGesamt = lookupSvProzent(SvSatzTyp.RV_GESAMT, stichtag);
        k.avGesamt = lookupSvProzent(SvSatzTyp.AV_GESAMT, stichtag);
        k.minijobAgKv = lookupSvProzent(SvSatzTyp.MINIJOB_AG_KV, stichtag);
        k.minijobAgRv = lookupSvProzent(SvSatzTyp.MINIJOB_AG_RV, stichtag);
        k.minijobAgPauschal = lookupSvProzent(SvSatzTyp.MINIJOB_AG_PAUSCHALSTEUER, stichtag);
        k.u1 = lookupSvProzent(SvSatzTyp.U1_UMLAGE, stichtag);
        k.u2 = lookupSvProzent(SvSatzTyp.U2_UMLAGE, stichtag);
        k.insolvenzgeldUmlage = lookupSvProzent(SvSatzTyp.INSOLVENZGELDUMLAGE, stichtag);
        return k;
    }

    private BigDecimal lookupSvProzent(SvSatzTyp typ, LocalDate stichtag) {
        return svSatzRepository
                .findFirstBySatzTypAndGueltigAbLessThanEqualOrderByGueltigAbDesc(typ, stichtag)
                .map(SvSatz::getProzent)
                .orElse(BigDecimal.ZERO);
    }

    // ==================== Stunden-Block ====================

    private MitarbeiterStundenZeile berechneStundenZeile(Mitarbeiter ma,
                                                         int jahr,
                                                         LocalDate jahresStart,
                                                         LocalDate jahresEnde,
                                                         Modus modus,
                                                         Set<LocalDate> feiertageWerktag,
                                                         BigDecimal interneQuote,
                                                         List<DatenLuecke> luecken) {
        MitarbeiterStundenZeile zeile = new MitarbeiterStundenZeile();
        zeile.setMitarbeiterId(ma.getId());
        zeile.setName(ma.getVorname() + " " + ma.getNachname());
        zeile.setIstGeschaeftsfuehrer(Boolean.TRUE.equals(ma.getIstGeschaeftsfuehrer()));

        Optional<Zeitkonto> zeitkontoOpt = zeitkontoRepository.findByMitarbeiterId(ma.getId());
        BigDecimal jahresSoll;
        BigDecimal feiertagsSoll;
        if (zeitkontoOpt.isPresent()) {
            jahresSoll = jahresSollstundenAusZeitkonto(zeitkontoOpt.get(), jahresStart, jahresEnde, feiertageWerktag);
            feiertagsSoll = feiertagSoll(zeitkontoOpt.get(), feiertageWerktag);
        } else {
            // Ohne Zeitkonto wurden bisher 0 verkaeufliche Stunden gerechnet, waehrend
            // der Lohn-Block denselben Mitarbeiter mit vollen Jahreskosten ansetzt.
            // Ergebnis: der Stundensatz schoss nach oben. Wir rechnen deshalb mit
            // demselben Default wie der Lohn-Block (8 h je Werktag) und melden es.
            jahresSoll = werktagsSollOhneZeitkonto(jahresStart, jahresEnde, feiertageWerktag);
            feiertagsSoll = STUNDEN_PRO_TAG_DEFAULT.multiply(BigDecimal.valueOf(feiertageWerktag.size()));
            zeile.setSollIstDefault(true);
            DatenLuecke l = new DatenLuecke();
            l.setMitarbeiterId(ma.getId());
            l.setMitarbeiterName(zeile.getName());
            l.setProblem("Keine Arbeitszeiten hinterlegt - es wird mit "
                    + STUNDEN_PRO_TAG_DEFAULT.stripTrailingZeros().toPlainString() + " h pro Werktag gerechnet");
            luecken.add(l);
        }

        zeile.setSollstunden(jahresSoll);
        zeile.setFeiertagsstunden(feiertagsSoll);

        BigDecimal urlaub = nz(abwesenheitRepository.sumStundenByMitarbeiterIdAndTypAndDatumBetween(
                ma.getId(), AbwesenheitsTyp.URLAUB, jahresStart, jahresEnde));
        BigDecimal krank = nz(abwesenheitRepository.sumStundenByMitarbeiterIdAndTypAndDatumBetween(
                ma.getId(), AbwesenheitsTyp.KRANKHEIT, jahresStart, jahresEnde));

        if (urlaub.compareTo(BigDecimal.ZERO) == 0 && ma.getJahresUrlaub() != null) {
            urlaub = BigDecimal.valueOf(ma.getJahresUrlaub()).multiply(stundenProTag(zeitkontoOpt));
            zeile.setUrlaubIstDefault(true);
        }
        if (krank.compareTo(BigDecimal.ZERO) == 0) {
            krank = KRANKHEITSTAGE_DEFAULT.multiply(stundenProTag(zeitkontoOpt));
            zeile.setKrankheitIstDefault(true);
        }
        zeile.setUrlaubsstunden(urlaub);
        zeile.setKrankheitsstunden(krank);

        BigDecimal interne = BigDecimal.ZERO;
        BigDecimal verkaeuflich;
        if (modus == Modus.RUECKWIRKEND) {
            BigDecimal produktiv = nz(zeitbuchungRepository.sumStundenByMitarbeiterAndProjektArtAndZeitraum(
                    ma.getId(), jahresStart.atStartOfDay(), jahresEnde.plusDays(1).atStartOfDay(), PRODUKTIVE_PROJEKTARTEN));
            interne = nz(zeitbuchungRepository.sumStundenByMitarbeiterAndProjektArtAndZeitraum(
                    ma.getId(), jahresStart.atStartOfDay(), jahresEnde.plusDays(1).atStartOfDay(), UNPRODUKTIVE_PROJEKTARTEN));
            verkaeuflich = produktiv;
            if (produktiv.compareTo(BigDecimal.ZERO) == 0 && interne.compareTo(BigDecimal.ZERO) == 0) {
                DatenLuecke l = new DatenLuecke();
                l.setMitarbeiterId(ma.getId());
                l.setMitarbeiterName(zeile.getName());
                l.setProblem("Keine Zeitbuchungen in " + jahr + " - Mitarbeiter wird mit 0 verkaeuflichen Stunden gerechnet");
                luecken.add(l);
            }
        } else {
            interne = jahresSoll.multiply(interneQuote).setScale(2, RoundingMode.HALF_UP);
            zeile.setInterneIstDefault(true);
            verkaeuflich = jahresSoll.subtract(urlaub).subtract(krank).subtract(interne);
            if (verkaeuflich.compareTo(BigDecimal.ZERO) < 0) {
                verkaeuflich = BigDecimal.ZERO;
            }
        }
        zeile.setInterneStunden(interne);
        zeile.setVerkaeuflicheStunden(verkaeuflich.setScale(2, RoundingMode.HALF_UP));
        return zeile;
    }

    private BigDecimal jahresSollstundenAusZeitkonto(Zeitkonto zk,
                                                     LocalDate von,
                                                     LocalDate bis,
                                                     Set<LocalDate> feiertage) {
        BigDecimal summe = BigDecimal.ZERO;
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            if (feiertage.contains(d)) {
                continue;
            }
            int dow = d.getDayOfWeek().getValue();
            summe = summe.add(nz(zk.getSollstundenFuerTag(dow)));
        }
        return summe.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal feiertagSoll(Zeitkonto zk, Set<LocalDate> feiertage) {
        BigDecimal summe = BigDecimal.ZERO;
        for (LocalDate d : feiertage) {
            int dow = d.getDayOfWeek().getValue();
            summe = summe.add(nz(zk.getSollstundenFuerTag(dow)));
        }
        return summe.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Jahressoll fuer Mitarbeiter ohne Zeitkonto: 8 h je Werktag (Mo-Fr) ohne
     * Feiertage -- dieselbe Annahme, die der Lohn-Block ueber die 2080-h-Woche
     * trifft.
     */
    private BigDecimal werktagsSollOhneZeitkonto(LocalDate von, LocalDate bis, Set<LocalDate> feiertage) {
        BigDecimal summe = BigDecimal.ZERO;
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || feiertage.contains(d)) {
                continue;
            }
            summe = summe.add(STUNDEN_PRO_TAG_DEFAULT);
        }
        return summe.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Durchschnittliche Stunden je Arbeitstag -- Basis fuer die Bewertung von
     * Urlaubs- und Krankheitstagen. Es wird durch die Anzahl der tatsaechlichen
     * Arbeitstage geteilt, nicht fix durch 5: bei einer 4-Tage-Woche
     * (4 × 8 h) kam sonst 6,4 h statt 8 h heraus und der Urlaub wurde zu
     * niedrig bewertet. Samstagsarbeit zaehlt ebenfalls mit.
     */
    private BigDecimal stundenProTag(Optional<Zeitkonto> zeitkontoOpt) {
        if (zeitkontoOpt.isEmpty()) return STUNDEN_PRO_TAG_DEFAULT;
        Zeitkonto zk = zeitkontoOpt.get();
        BigDecimal summe = BigDecimal.ZERO;
        int arbeitstage = 0;
        for (int dow = 1; dow <= 7; dow++) {
            BigDecimal stunden = nz(zk.getSollstundenFuerTag(dow));
            if (stunden.signum() > 0) {
                summe = summe.add(stunden);
                arbeitstage++;
            }
        }
        if (arbeitstage == 0) return STUNDEN_PRO_TAG_DEFAULT;
        return summe.divide(BigDecimal.valueOf(arbeitstage), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal jahresSollstunden(Mitarbeiter ma) {
        Optional<Zeitkonto> zk = zeitkontoRepository.findByMitarbeiterId(ma.getId());
        if (zk.isEmpty()) {
            return new BigDecimal("2080.00"); // 40h × 52
        }
        BigDecimal wochenstunden = wochenstundenSicher(zk.get());
        if (wochenstunden.signum() <= 0) {
            return new BigDecimal("2080.00");
        }
        return wochenstunden.multiply(BigDecimal.valueOf(52));
    }

    /**
     * Wochenstunden ohne NPE-Risiko: {@link Zeitkonto#getWochenstunden()}
     * addiert die Tagesfelder ungeprueft, in der Datenbank koennen sie null sein.
     */
    private static BigDecimal wochenstundenSicher(Zeitkonto zk) {
        BigDecimal summe = BigDecimal.ZERO;
        for (int dow = 1; dow <= 7; dow++) {
            summe = summe.add(nz(zk.getSollstundenFuerTag(dow)));
        }
        return summe;
    }

    /**
     * Bringt die interne Quote auf einen Anteil zwischen 0 und 1.
     * null oder Werte ausserhalb 0-100 fallen auf den Standard zurueck.
     */
    private static BigDecimal normalisiereInterneQuote(Integer prozent) {
        if (prozent == null || prozent < 0 || prozent > 100) {
            return INTERNE_QUOTE_DEFAULT;
        }
        return BigDecimal.valueOf(prozent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private Set<LocalDate> ladeFeiertage(int jahr) {
        List<Feiertag> feiertage = feiertagRepository.findByJahrAndBundesland(jahr, BUNDESLAND_DEFAULT);
        Set<LocalDate> resultat = new HashSet<>();
        for (Feiertag f : feiertage) {
            DayOfWeek dow = f.getDatum().getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            if (!f.isHalbTag()) {
                resultat.add(f.getDatum());
            }
        }
        return resultat;
    }

    // ==================== Gemeinkosten-Block ====================

    private BigDecimal berechneGemeinkosten(List<KostenstelleAnteil> bucket, int jahr) {
        Map<Long, KostenstelleAnteil> proKs = new HashMap<>();
        BigDecimal summe = BigDecimal.ZERO;

        // 1) Lieferantenrechnungen ueber LieferantDokumentProjektAnteil (Bestandslogik).
        for (LieferantDokumentProjektAnteil anteil : anteilRepository.findAll()) {
            if (anteil.getKostenstelle() == null) continue;
            if (!anteil.getKostenstelle().isIstFixkosten()) continue;
            if (!anteil.isStreckungAktivFuerJahr(jahr)) continue;
            BigDecimal jahresAnteil = nz(anteil.getJahresanteil());
            summe = summe.add(jahresAnteil);
            KostenstelleAnteil bucketEintrag = proKs.computeIfAbsent(
                    anteil.getKostenstelle().getId(),
                    id -> {
                        KostenstelleAnteil ka = new KostenstelleAnteil();
                        ka.setKostenstelleId(anteil.getKostenstelle().getId());
                        ka.setBezeichnung(anteil.getKostenstelle().getBezeichnung());
                        ka.setJahresbetrag(BigDecimal.ZERO);
                        ka.setGestreckt(anteil.getStreckungJahre() != null && anteil.getStreckungJahre() > 1);
                        return ka;
                    });
            bucketEintrag.setJahresbetrag(bucketEintrag.getJahresbetrag().add(jahresAnteil));
            if (anteil.getStreckungJahre() != null && anteil.getStreckungJahre() > 1) {
                bucketEintrag.setGestreckt(true);
            }
        }

        // 2) Belege mit Fixkosten-Kostenstelle aus dem Belegscan: Tankquittungen,
        //    Telefonrechnungen, Bueromaterial vom Kassenbon, etc. Wenn die KI
        //    den Beleg einer Fixkosten-Kostenstelle zugeordnet hat (oder der
        //    Buchhalter manuell), fliesst der NETTO-Betrag in dem Jahr in den
        //    Gemeinkostentopf — Vorsteuer geht an das Finanzamt, nicht in die
        //    Stundenlohn-Kalkulation. Fallback Brutto nur, wenn Netto nicht
        //    extrahiert werden konnte (Kassenbons ohne MwSt-Ausweis).
        LocalDate jahresStart = LocalDate.of(jahr, 1, 1);
        LocalDate jahresEnde = LocalDate.of(jahr, 12, 31);
        // Am PC bleibt beim Anlegen einer Kostenstellen-Aufteilung die direkte
        // Beleg.kostenstelle stehen (anders als am Handy, wo sie geleert wird).
        // Ueber die direkte Kostenstelle zaehlen wir deshalb nur den Teil des
        // Belegs, der NICHT aufgeteilt ist:
        //   - voll aufgeteilt  -> Rest 0, Punkt 3 uebernimmt ihn  (keine Doppelzaehlung)
        //   - zu 60 % aufgeteilt -> die restlichen 40 % bleiben hier erhalten
        //   - gar nicht aufgeteilt -> voller Betrag wie bisher
        Map<Long, BigDecimal> bereitsAufgeteilt = new HashMap<>();
        for (Object[] zeile : belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg()) {
            if (zeile == null || zeile.length < 2 || zeile[0] == null) continue;
            bereitsAufgeteilt.put((Long) zeile[0], nz((BigDecimal) zeile[1]));
        }
        for (Beleg beleg : belegRepository.findValidierteFixkostenBelegeImZeitraum(jahresStart, jahresEnde)) {
            // Buchungsbetrag: bei einem Mischbon nur der Firmenanteil. Der
            // private Teil gehoert nicht in die Gemeinkosten -- sonst treibt
            // der eigene Wocheneinkauf den Stundensatz hoch.
            BigDecimal gesamt = nz(beleg.getBuchungsbetragNetto());
            if (gesamt.signum() <= 0) continue;
            BigDecimal basis = gesamt.subtract(nz(bereitsAufgeteilt.get(beleg.getId())));
            if (basis.signum() <= 0) continue;
            summe = summe.add(basis);
            final Long ksId = beleg.getKostenstelle().getId();
            final String bezeichnung = beleg.getKostenstelle().getBezeichnung();
            KostenstelleAnteil bucketEintrag = proKs.computeIfAbsent(ksId, id -> {
                KostenstelleAnteil ka = new KostenstelleAnteil();
                ka.setKostenstelleId(ksId);
                ka.setBezeichnung(bezeichnung);
                ka.setJahresbetrag(BigDecimal.ZERO);
                ka.setGestreckt(false);
                return ka;
            });
            bucketEintrag.setJahresbetrag(bucketEintrag.getJahresbetrag().add(basis));
        }

        // 3) Beleg-Kostenstellen-Splits (Issue #60): mehrere Kostenstellen pro
        //    Beleg mit Prozent/Absolut-Verteilung und optionaler Streckung.
        //    Filter (VALIDIERT, istFixkosten, Streckungs-Jahr) erledigt die
        //    Repository-Query — kein Java-seitiges findAll mehr noetig.
        for (BelegKostenstellenAnteil anteil :
                belegKostenstellenAnteilRepository.findAktiveFixkostenAnteileImJahr(jahr)) {
            if (anteil.getKostenstelle() == null) continue;
            BigDecimal jahresAnteil = nz(anteil.getJahresanteil());
            if (jahresAnteil.signum() <= 0) continue;
            summe = summe.add(jahresAnteil);
            final Long ksId = anteil.getKostenstelle().getId();
            final String bezeichnung = anteil.getKostenstelle().getBezeichnung();
            KostenstelleAnteil bucketEintrag = proKs.computeIfAbsent(ksId, id -> {
                KostenstelleAnteil ka = new KostenstelleAnteil();
                ka.setKostenstelleId(ksId);
                ka.setBezeichnung(bezeichnung);
                ka.setJahresbetrag(BigDecimal.ZERO);
                ka.setGestreckt(false);
                return ka;
            });
            bucketEintrag.setJahresbetrag(bucketEintrag.getJahresbetrag().add(jahresAnteil));
            if (anteil.getStreckungJahre() != null && anteil.getStreckungJahre() > 1) {
                bucketEintrag.setGestreckt(true);
            }
        }

        for (KostenstelleAnteil k : proKs.values()) {
            k.setJahresbetrag(k.getJahresbetrag().setScale(2, RoundingMode.HALF_UP));
            bucket.add(k);
        }
        return summe;
    }

    // ==================== Helpers ====================

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal prozentVon(BigDecimal basis, BigDecimal prozent) {
        if (basis == null || prozent == null) return BigDecimal.ZERO;
        return basis.multiply(prozent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static class SvKontext {
        BigDecimal kvGesamt = BigDecimal.ZERO;
        BigDecimal pvGesamt = BigDecimal.ZERO;
        BigDecimal rvGesamt = BigDecimal.ZERO;
        BigDecimal avGesamt = BigDecimal.ZERO;
        BigDecimal minijobAgKv = BigDecimal.ZERO;
        BigDecimal minijobAgRv = BigDecimal.ZERO;
        BigDecimal minijobAgPauschal = BigDecimal.ZERO;
        BigDecimal u1 = BigDecimal.ZERO;
        BigDecimal u2 = BigDecimal.ZERO;
        BigDecimal insolvenzgeldUmlage = BigDecimal.ZERO;
    }
}
