package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart
import org.example.kalkulationsprogramm.domain.Firmeninformation
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.MitarbeiterStundenlohn
import org.example.kalkulationsprogramm.domain.ProjektArt
import org.example.kalkulationsprogramm.domain.SvSatz
import org.example.kalkulationsprogramm.domain.SvSatzTyp
import org.example.kalkulationsprogramm.domain.Zeitkonto
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.AbteilungVorschlag
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.DatenLuecke
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.KostenstelleAnteil
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.LohnQuelle
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.Modus
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnUebernehmenRequest
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.FeiertagRepository
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository
import org.example.kalkulationsprogramm.repository.SvSatzRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.util.Optional

@Service
class VerrechnungslohnService(
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val stundenlohnRepository: MitarbeiterStundenlohnRepository,
    private val lohnabrechnungRepository: LohnabrechnungRepository,
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val zeitkontoRepository: ZeitkontoRepository,
    private val abwesenheitRepository: AbwesenheitRepository,
    private val feiertagRepository: FeiertagRepository,
    private val svSatzRepository: SvSatzRepository,
    private val firmeninformationRepository: FirmeninformationRepository,
    private val anteilRepository: LieferantDokumentProjektAnteilRepository,
    private val abteilungRepository: AbteilungRepository,
    private val arbeitsgangRepository: ArbeitsgangRepository,
    private val stundensatzRepository: ArbeitsgangStundensatzRepository,
    private val belegRepository: BelegRepository,
    private val belegKostenstellenAnteilRepository: BelegKostenstellenAnteilRepository,
) {
    @Transactional(readOnly = true)
    fun berechne(jahr: Int): VerrechnungslohnErgebnisDto {
        val dto = VerrechnungslohnErgebnisDto()
        dto.jahr = jahr
        val modus = if (jahr < Year.now().value) Modus.RUECKWIRKEND else Modus.HOCHRECHNUNG
        dto.modus = modus

        val jahresStart = LocalDate.of(jahr, 1, 1)
        val jahresEnde = LocalDate.of(jahr, 12, 31)
        val firma = firmeninformationRepository.findById(1L).orElse(null)
        val bgSatz = ermittleBgSatz(firma)
        val svKontext = ladeSvKontext(jahresStart)
        val feiertageWerktag = ladeFeiertage(jahr)

        var lohnsumme = BigDecimal.ZERO
        var stundenSumme = BigDecimal.ZERO

        for (ma in mitarbeiterRepository.findByAktivTrue()) {
            val lohnZeile = berechneLohnZeile(ma, jahr, modus, svKontext, bgSatz, dto.datenLuecken)
            dto.lohnzeilen.add(lohnZeile)
            lohnsumme = lohnsumme.add(lohnZeile.gesamtkosten)

            val stdZeile = berechneStundenZeile(ma, jahr, jahresStart, jahresEnde, modus, feiertageWerktag, dto.datenLuecken)
            dto.stundenzeilen.add(stdZeile)
            stundenSumme = stundenSumme.add(stdZeile.verkaeuflicheStunden)
        }

        val gemeinkosten = berechneGemeinkosten(dto.kostenstellen, jahr)
        dto.lohnsummeGesamt = lohnsumme.setScale(2, RoundingMode.HALF_UP)
        dto.verkaeuflicheStundenGesamt = stundenSumme.setScale(2, RoundingMode.HALF_UP)
        dto.gemeinkostenGesamt = gemeinkosten.setScale(2, RoundingMode.HALF_UP)

        dto.selbstkostenProStunde = if (stundenSumme.compareTo(BigDecimal.ZERO) > 0) {
            lohnsumme.add(gemeinkosten).divide(stundenSumme, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        for (abt: Abteilung in abteilungRepository.findAll()) {
            val vorschlag = AbteilungVorschlag()
            vorschlag.abteilungId = abt.id
            vorschlag.name = abt.name
            vorschlag.aufschlagEuro = BigDecimal.ZERO
            dto.abteilungen.add(vorschlag)
        }
        return dto
    }

    @Transactional
    fun uebernehmen(request: VerrechnungslohnUebernehmenRequest): Int {
        val basisSatz = request.basisSatz ?: throw IllegalArgumentException("basisSatz darf nicht null sein")
        val aufschlaegeProAbteilung = HashMap<Long, BigDecimal>()
        request.abteilungAufschlaege?.forEach { a ->
            val abteilungId = a.abteilungId
            val aufschlagEuro = a.aufschlagEuro
            if (abteilungId != null && aufschlagEuro != null) {
                aufschlaegeProAbteilung[abteilungId] = aufschlagEuro
            }
        }

        var aktualisiert = 0
        for (ag in arbeitsgangRepository.findAll()) {
            val aufschlag = ag.abteilung?.id?.let { aufschlaegeProAbteilung.getOrDefault(it, BigDecimal.ZERO) } ?: BigDecimal.ZERO
            val satz = basisSatz.add(aufschlag).setScale(2, RoundingMode.HALF_UP)
            val eintrag = stundensatzRepository
                .findTopByArbeitsgangIdAndJahrOrderByIdDesc(ag.id, request.jahr)
                .orElseGet { ArbeitsgangStundensatz() }
            eintrag.arbeitsgang = ag
            eintrag.jahr = request.jahr
            eintrag.satz = satz
            stundensatzRepository.save(eintrag)
            aktualisiert++
        }
        return aktualisiert
    }

    private fun berechneLohnZeile(
        ma: Mitarbeiter,
        jahr: Int,
        modus: Modus,
        svKontext: SvKontext,
        bgSatz: BigDecimal,
        luecken: MutableList<DatenLuecke>,
    ): MitarbeiterLohnZeile {
        val zeile = MitarbeiterLohnZeile()
        zeile.mitarbeiterId = ma.id
        zeile.name = "${ma.vorname} ${ma.nachname}"
        zeile.isIstGeschaeftsfuehrer = ma.istGeschaeftsfuehrer == true
        zeile.beschaeftigungsart = ma.beschaeftigungsart?.name

        if (zeile.isIstGeschaeftsfuehrer) {
            val kalk = nz(ma.kalkulatorischerLohnMonat).multiply(BigDecimal.valueOf(12))
            val vorteil = nz(ma.geldwertVorteilMonat).multiply(BigDecimal.valueOf(12))
            zeile.bruttoJahr = kalk
            zeile.geldwerterVorteilJahr = vorteil
            zeile.quelle = LohnQuelle.KALKULATORISCH
            var gesamt = kalk.add(vorteil)
            if (ma.beschaeftigungsart == Beschaeftigungsart.GF_SV_PFLICHTIG) {
                val svBasis = kalk.add(vorteil)
                val agSv = berechneAgAnteilSv(svBasis, ma, svKontext)
                val bg = berechneBg(svBasis, bgSatz)
                zeile.agAnteilSv = agSv
                zeile.bgBeitrag = bg
                gesamt = gesamt.add(agSv).add(bg)
            }
            zeile.gesamtkosten = gesamt.setScale(2, RoundingMode.HALF_UP)
            return zeile
        }

        val brutto = ermittleBrutto(ma, jahr, modus, zeile, luecken)
        zeile.bruttoJahr = brutto
        val agSv = berechneAgAnteilSv(brutto, ma, svKontext)
        val bg = berechneBg(brutto, bgSatz)
        zeile.agAnteilSv = agSv
        zeile.bgBeitrag = bg
        zeile.gesamtkosten = brutto.add(agSv).add(bg).setScale(2, RoundingMode.HALF_UP)
        return zeile
    }

    private fun ermittleBrutto(
        ma: Mitarbeiter,
        jahr: Int,
        modus: Modus,
        zeile: MitarbeiterLohnZeile,
        luecken: MutableList<DatenLuecke>,
    ): BigDecimal {
        if (modus == Modus.RUECKWIRKEND) {
            val sum = nz(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(ma.id, jahr))
            val anzahl = lohnabrechnungRepository.countByMitarbeiterIdAndJahr(ma.id, jahr)
            if (sum.compareTo(BigDecimal.ZERO) > 0 && anzahl >= 12) {
                zeile.quelle = LohnQuelle.LOHNABRECHNUNG
                return sum
            }
            if (sum.compareTo(BigDecimal.ZERO) > 0 && anzahl > 0) {
                val l = DatenLuecke()
                l.mitarbeiterId = ma.id
                l.mitarbeiterName = zeile.name
                l.problem = "Nur $anzahl von 12 Lohnabrechnungen vorhanden - Brutto wird hochgerechnet"
                luecken.add(l)
                val hoch = sum.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(anzahl), 2, RoundingMode.HALF_UP)
                zeile.quelle = LohnQuelle.LOHNABRECHNUNG
                zeile.isBruttoIstDefault = true
                return hoch
            }
            val l = DatenLuecke()
            l.mitarbeiterId = ma.id
            l.mitarbeiterName = zeile.name
            l.problem = "Keine Lohnabrechnungen fuer $jahr - Stammstundenlohn als Default"
            luecken.add(l)
        }
        val hochgerechnet = hochrechnungAusStundenlohn(ma, jahr)
        zeile.quelle = if (modus == Modus.RUECKWIRKEND) LohnQuelle.STAMMSTUNDENLOHN else LohnQuelle.STUNDENLOHN_HOCHRECHNUNG
        zeile.isBruttoIstDefault = true
        return hochgerechnet
    }

    private fun hochrechnungAusStundenlohn(ma: Mitarbeiter, jahr: Int): BigDecimal {
        val stichtag = LocalDate.of(jahr, 1, 1)
        val versionOpt: Optional<MitarbeiterStundenlohn> = stundenlohnRepository
            .findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(ma.id, stichtag.plusYears(1).minusDays(1))
        val stundenlohn = nz(versionOpt.map { it.stundenlohn }.orElse(null))
        return stundenlohn.multiply(jahresSollstunden(ma)).setScale(2, RoundingMode.HALF_UP)
    }

    private fun berechneAgAnteilSv(brutto: BigDecimal?, ma: Mitarbeiter, svKontext: SvKontext): BigDecimal {
        if (brutto == null || brutto.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO
        val art = ma.beschaeftigungsart ?: Beschaeftigungsart.REGULAER
        if (art == Beschaeftigungsart.GF_SV_FREI) return BigDecimal.ZERO
        if (art == Beschaeftigungsart.MINIJOB) {
            val gesamtProzent = svKontext.minijobAgKv
                .add(svKontext.minijobAgRv)
                .add(svKontext.minijobAgPauschal)
                .add(svKontext.u1)
                .add(svKontext.u2)
                .add(svKontext.insolvenzgeldUmlage)
            return prozentVon(brutto, gesamtProzent)
        }
        val kvAg = svKontext.kvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
        val pvAg = svKontext.pvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
        val rvAg = svKontext.rvGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
        val avAg = svKontext.avGesamt.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
        val kkZusatzAg = ermittleKkZusatzAgAnteil(ma)
        val gesamtProzent = kvAg.add(pvAg).add(rvAg).add(avAg).add(kkZusatzAg)
            .add(svKontext.u1).add(svKontext.u2).add(svKontext.insolvenzgeldUmlage)
        return prozentVon(brutto, gesamtProzent)
    }

    private fun ermittleKkZusatzAgAnteil(ma: Mitarbeiter): BigDecimal {
        val zusatz = ma.krankenkasse?.zusatzbeitragProzent ?: return BigDecimal.ZERO
        return zusatz.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
    }

    private fun berechneBg(brutto: BigDecimal?, bgProzent: BigDecimal?): BigDecimal {
        if (brutto == null || brutto.compareTo(BigDecimal.ZERO) <= 0 || bgProzent == null) return BigDecimal.ZERO
        return prozentVon(brutto, bgProzent)
    }

    private fun ermittleBgSatz(firma: Firmeninformation?): BigDecimal {
        if (firma == null) return BigDecimal.ZERO
        firma.bgSatzOverride?.let { return it }
        return firma.gewerk?.bgSatzProzent ?: BigDecimal.ZERO
    }

    private fun ladeSvKontext(stichtag: LocalDate): SvKontext {
        val k = SvKontext()
        k.kvGesamt = lookupSvProzent(SvSatzTyp.KV_GESAMT, stichtag)
        k.pvGesamt = lookupSvProzent(SvSatzTyp.PV_GESAMT, stichtag)
        k.rvGesamt = lookupSvProzent(SvSatzTyp.RV_GESAMT, stichtag)
        k.avGesamt = lookupSvProzent(SvSatzTyp.AV_GESAMT, stichtag)
        k.minijobAgKv = lookupSvProzent(SvSatzTyp.MINIJOB_AG_KV, stichtag)
        k.minijobAgRv = lookupSvProzent(SvSatzTyp.MINIJOB_AG_RV, stichtag)
        k.minijobAgPauschal = lookupSvProzent(SvSatzTyp.MINIJOB_AG_PAUSCHALSTEUER, stichtag)
        k.u1 = lookupSvProzent(SvSatzTyp.U1_UMLAGE, stichtag)
        k.u2 = lookupSvProzent(SvSatzTyp.U2_UMLAGE, stichtag)
        k.insolvenzgeldUmlage = lookupSvProzent(SvSatzTyp.INSOLVENZGELDUMLAGE, stichtag)
        return k
    }

    private fun lookupSvProzent(typ: SvSatzTyp, stichtag: LocalDate): BigDecimal =
        svSatzRepository
            .findFirstBySatzTypAndGueltigAbLessThanEqualOrderByGueltigAbDesc(typ, stichtag)
            .map { nz(it.prozent) }
            .orElse(BigDecimal.ZERO)

    private fun berechneStundenZeile(
        ma: Mitarbeiter,
        jahr: Int,
        jahresStart: LocalDate,
        jahresEnde: LocalDate,
        modus: Modus,
        feiertageWerktag: Set<LocalDate>,
        luecken: MutableList<DatenLuecke>,
    ): MitarbeiterStundenZeile {
        val zeile = MitarbeiterStundenZeile()
        zeile.mitarbeiterId = ma.id
        zeile.name = "${ma.vorname} ${ma.nachname}"
        zeile.isIstGeschaeftsfuehrer = ma.istGeschaeftsfuehrer == true

        val zeitkontoOpt = zeitkontoRepository.findByMitarbeiterId(ma.id)
        val jahresSoll = zeitkontoOpt.map { jahresSollstundenAusZeitkonto(it, jahresStart, jahresEnde, feiertageWerktag) }.orElse(BigDecimal.ZERO)
        val feiertagsSoll = zeitkontoOpt.map { feiertagSoll(it, feiertageWerktag) }.orElse(BigDecimal.ZERO)

        zeile.sollstunden = jahresSoll
        zeile.feiertagsstunden = feiertagsSoll

        var urlaub = nz(abwesenheitRepository.sumStundenByMitarbeiterIdAndTypAndDatumBetween(ma.id, AbwesenheitsTyp.URLAUB, jahresStart, jahresEnde))
        var krank = nz(abwesenheitRepository.sumStundenByMitarbeiterIdAndTypAndDatumBetween(ma.id, AbwesenheitsTyp.KRANKHEIT, jahresStart, jahresEnde))

        if (urlaub.compareTo(BigDecimal.ZERO) == 0 && ma.jahresUrlaub != null) {
            urlaub = BigDecimal.valueOf(ma.jahresUrlaub!!.toLong()).multiply(stundenProTag(zeitkontoOpt))
            zeile.isUrlaubIstDefault = true
        }
        if (krank.compareTo(BigDecimal.ZERO) == 0) {
            krank = KRANKHEITSTAGE_DEFAULT.multiply(stundenProTag(zeitkontoOpt))
            zeile.isKrankheitIstDefault = true
        }
        zeile.urlaubsstunden = urlaub
        zeile.krankheitsstunden = krank

        var interne = BigDecimal.ZERO
        var verkaeuflich: BigDecimal
        if (modus == Modus.RUECKWIRKEND) {
            val produktiv = nz(zeitbuchungRepository.sumStundenByMitarbeiterAndProjektArtAndZeitraum(
                ma.id,
                jahresStart.atStartOfDay(),
                jahresEnde.plusDays(1).atStartOfDay(),
                PRODUKTIVE_PROJEKTARTEN,
            ))
            interne = nz(zeitbuchungRepository.sumStundenByMitarbeiterAndProjektArtAndZeitraum(
                ma.id,
                jahresStart.atStartOfDay(),
                jahresEnde.plusDays(1).atStartOfDay(),
                UNPRODUKTIVE_PROJEKTARTEN,
            ))
            verkaeuflich = produktiv
            if (produktiv.compareTo(BigDecimal.ZERO) == 0 && interne.compareTo(BigDecimal.ZERO) == 0) {
                val l = DatenLuecke()
                l.mitarbeiterId = ma.id
                l.mitarbeiterName = zeile.name
                l.problem = "Keine Zeitbuchungen in $jahr - Mitarbeiter wird mit 0 verkaeuflichen Stunden gerechnet"
                luecken.add(l)
            }
        } else {
            interne = jahresSoll.multiply(INTERNE_QUOTE_DEFAULT).setScale(2, RoundingMode.HALF_UP)
            zeile.isInterneIstDefault = true
            verkaeuflich = jahresSoll.subtract(urlaub).subtract(krank).subtract(interne)
            if (verkaeuflich.compareTo(BigDecimal.ZERO) < 0) verkaeuflich = BigDecimal.ZERO
        }
        zeile.interneStunden = interne
        zeile.verkaeuflicheStunden = verkaeuflich.setScale(2, RoundingMode.HALF_UP)
        return zeile
    }

    private fun jahresSollstundenAusZeitkonto(zk: Zeitkonto, von: LocalDate, bis: LocalDate, feiertage: Set<LocalDate>): BigDecimal {
        var summe = BigDecimal.ZERO
        var d = von
        while (!d.isAfter(bis)) {
            if (!feiertage.contains(d)) {
                val dow = d.dayOfWeek.value
                summe = summe.add(nz(zk.getSollstundenFuerTag(dow)))
            }
            d = d.plusDays(1)
        }
        return summe.setScale(2, RoundingMode.HALF_UP)
    }

    private fun feiertagSoll(zk: Zeitkonto, feiertage: Set<LocalDate>): BigDecimal {
        var summe = BigDecimal.ZERO
        for (d in feiertage) {
            summe = summe.add(nz(zk.getSollstundenFuerTag(d.dayOfWeek.value)))
        }
        return summe.setScale(2, RoundingMode.HALF_UP)
    }

    private fun stundenProTag(zeitkontoOpt: Optional<Zeitkonto>): BigDecimal {
        if (zeitkontoOpt.isEmpty) return STUNDEN_PRO_TAG_DEFAULT
        val zk = zeitkontoOpt.get()
        val summe = nz(zk.montagStunden)
            .add(nz(zk.dienstagStunden))
            .add(nz(zk.mittwochStunden))
            .add(nz(zk.donnerstagStunden))
            .add(nz(zk.freitagStunden))
        if (summe.compareTo(BigDecimal.ZERO) == 0) return STUNDEN_PRO_TAG_DEFAULT
        return summe.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP)
    }

    private fun jahresSollstunden(ma: Mitarbeiter): BigDecimal {
        val zk = zeitkontoRepository.findByMitarbeiterId(ma.id)
        if (zk.isEmpty) return BigDecimal("2080.00")
        return zk.get().getWochenstunden().multiply(BigDecimal.valueOf(52))
    }

    private fun ladeFeiertage(jahr: Int): Set<LocalDate> {
        val resultat = HashSet<LocalDate>()
        for (f in feiertagRepository.findByJahrAndBundesland(jahr, BUNDESLAND_DEFAULT)) {
            val datum = f.datum ?: continue
            val dow = datum.dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue
            if (!f.isHalbTag()) resultat.add(datum)
        }
        return resultat
    }

    private fun berechneGemeinkosten(bucket: MutableList<KostenstelleAnteil>, jahr: Int): BigDecimal {
        val proKs = HashMap<Long, KostenstelleAnteil>()
        var summe = BigDecimal.ZERO

        for (anteil: LieferantDokumentProjektAnteil in anteilRepository.findAll()) {
            val kostenstelle = anteil.kostenstelle ?: continue
            if (!kostenstelle.isIstFixkosten()) continue
            if (!anteil.isStreckungAktivFuerJahr(jahr)) continue
            val jahresAnteil = nz(anteil.jahresanteil)
            summe = summe.add(jahresAnteil)
            val ksId = requireNotNull(kostenstelle.id)
            val bucketEintrag = proKs.computeIfAbsent(ksId) {
                KostenstelleAnteil().apply {
                    kostenstelleId = ksId
                    bezeichnung = kostenstelle.bezeichnung
                    jahresbetrag = BigDecimal.ZERO
                    isGestreckt = (anteil.streckungJahre ?: 0) > 1
                }
            }
            bucketEintrag.jahresbetrag = bucketEintrag.jahresbetrag.add(jahresAnteil)
            if ((anteil.streckungJahre ?: 0) > 1) bucketEintrag.isGestreckt = true
        }

        val jahresStart = LocalDate.of(jahr, 1, 1)
        val jahresEnde = LocalDate.of(jahr, 12, 31)
        for (beleg in belegRepository.findValidierteFixkostenBelegeImZeitraum(jahresStart, jahresEnde)) {
            val basis = beleg.betragNetto ?: nz(beleg.betragBrutto)
            if (basis.signum() <= 0) continue
            summe = summe.add(basis)
            val kostenstelle = requireNotNull(beleg.kostenstelle)
            val ksId = requireNotNull(kostenstelle.id)
            val bezeichnung = kostenstelle.bezeichnung
            val bucketEintrag = proKs.computeIfAbsent(ksId) {
                KostenstelleAnteil().apply {
                    kostenstelleId = ksId
                    this.bezeichnung = bezeichnung
                    jahresbetrag = BigDecimal.ZERO
                    isGestreckt = false
                }
            }
            bucketEintrag.jahresbetrag = bucketEintrag.jahresbetrag.add(basis)
        }

        for (anteil: BelegKostenstellenAnteil in belegKostenstellenAnteilRepository.findAktiveFixkostenAnteileImJahr(jahr)) {
            val kostenstelle = anteil.kostenstelle ?: continue
            val jahresAnteil = nz(anteil.jahresanteil)
            if (jahresAnteil.signum() <= 0) continue
            summe = summe.add(jahresAnteil)
            val ksId = requireNotNull(kostenstelle.id)
            val bezeichnung = kostenstelle.bezeichnung
            val bucketEintrag = proKs.computeIfAbsent(ksId) {
                KostenstelleAnteil().apply {
                    kostenstelleId = ksId
                    this.bezeichnung = bezeichnung
                    jahresbetrag = BigDecimal.ZERO
                    isGestreckt = false
                }
            }
            bucketEintrag.jahresbetrag = bucketEintrag.jahresbetrag.add(jahresAnteil)
            if ((anteil.streckungJahre ?: 0) > 1) bucketEintrag.isGestreckt = true
        }

        for (k in proKs.values) {
            k.jahresbetrag = k.jahresbetrag.setScale(2, RoundingMode.HALF_UP)
            bucket.add(k)
        }
        return summe
    }

    private class SvKontext {
        var kvGesamt: BigDecimal = BigDecimal.ZERO
        var pvGesamt: BigDecimal = BigDecimal.ZERO
        var rvGesamt: BigDecimal = BigDecimal.ZERO
        var avGesamt: BigDecimal = BigDecimal.ZERO
        var minijobAgKv: BigDecimal = BigDecimal.ZERO
        var minijobAgRv: BigDecimal = BigDecimal.ZERO
        var minijobAgPauschal: BigDecimal = BigDecimal.ZERO
        var u1: BigDecimal = BigDecimal.ZERO
        var u2: BigDecimal = BigDecimal.ZERO
        var insolvenzgeldUmlage: BigDecimal = BigDecimal.ZERO
    }

    companion object {
        private val STUNDEN_PRO_TAG_DEFAULT = BigDecimal("8.00")
        private val KRANKHEITSTAGE_DEFAULT = BigDecimal("8.00")
        private val INTERNE_QUOTE_DEFAULT = BigDecimal("0.05")
        private const val BUNDESLAND_DEFAULT = "BY"
        private val PRODUKTIVE_PROJEKTARTEN = ProjektArt.entries.filter { it.isProduktiv() }
        private val UNPRODUKTIVE_PROJEKTARTEN = ProjektArt.entries.filter { !it.isProduktiv() }

        private fun nz(v: BigDecimal?): BigDecimal = v ?: BigDecimal.ZERO

        private fun prozentVon(basis: BigDecimal?, prozent: BigDecimal?): BigDecimal {
            if (basis == null || prozent == null) return BigDecimal.ZERO
            return basis.multiply(prozent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        }
    }
}
