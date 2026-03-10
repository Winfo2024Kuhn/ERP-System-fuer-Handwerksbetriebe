package org.example.kalkulationsprogramm.mapper;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingConsumptionDto;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingCostCenterDto;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingPartyDto;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingResponseDto;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingShareDto;
import org.example.kalkulationsprogramm.dto.miete.KostenpositionDto;
import org.example.kalkulationsprogramm.dto.miete.KostenstelleDto;
import org.example.kalkulationsprogramm.dto.miete.MietobjektDto;
import org.example.kalkulationsprogramm.dto.miete.MietparteiDto;
import org.example.kalkulationsprogramm.dto.miete.RaumDto;
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselDto;
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselEintragDto;
import org.example.kalkulationsprogramm.dto.miete.VerbrauchsgegenstandDto;
import org.example.kalkulationsprogramm.dto.miete.ZaehlerstandDto;
import org.example.kalkulationsprogramm.service.miete.KostenpositionBerechner;
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MieteMapper {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final KostenpositionBerechner kostenpositionBerechner;

    private static BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public MietobjektDto toDto(Mietobjekt mietobjekt) {
        if (mietobjekt == null) {
            return null;
        }
        MietobjektDto dto = new MietobjektDto();
        dto.setId(mietobjekt.getId());
        dto.setName(mietobjekt.getName());
        dto.setStrasse(mietobjekt.getStrasse());
        dto.setPlz(mietobjekt.getPlz());
        dto.setOrt(mietobjekt.getOrt());
        return dto;
    }

    public Mietobjekt toEntity(MietobjektDto dto) {
        Mietobjekt entity = new Mietobjekt();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setStrasse(dto.getStrasse());
        entity.setPlz(dto.getPlz());
        entity.setOrt(dto.getOrt());
        return entity;
    }

    public MietparteiDto toDto(Mietpartei partei) {
        MietparteiDto dto = new MietparteiDto();
        dto.setId(partei.getId());
        dto.setName(partei.getName());
        dto.setRolle(partei.getRolle());
        dto.setEmail(partei.getEmail());
        dto.setTelefon(partei.getTelefon());
        dto.setMonatlicherVorschuss(partei.getMonatlicherVorschuss());
        return dto;
    }

    public Mietpartei toEntity(MietparteiDto dto) {
        Mietpartei entity = new Mietpartei();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setRolle(dto.getRolle());
        entity.setEmail(dto.getEmail());
        entity.setTelefon(dto.getTelefon());
        entity.setMonatlicherVorschuss(dto.getMonatlicherVorschuss());
        return entity;
    }

    public RaumDto toDto(org.example.kalkulationsprogramm.domain.miete.Raum raum) {
        RaumDto dto = new RaumDto();
        dto.setId(raum.getId());
        if (raum.getMietobjekt() != null) {
            dto.setMietobjektId(raum.getMietobjekt().getId());
        }
        dto.setName(raum.getName());
        dto.setBeschreibung(raum.getBeschreibung());
        dto.setFlaecheQuadratmeter(raum.getFlaecheQuadratmeter());
        return dto;
    }

    public org.example.kalkulationsprogramm.domain.miete.Raum toEntity(RaumDto dto) {
        org.example.kalkulationsprogramm.domain.miete.Raum entity = new org.example.kalkulationsprogramm.domain.miete.Raum();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setFlaecheQuadratmeter(dto.getFlaecheQuadratmeter());
        return entity;
    }

    public VerbrauchsgegenstandDto toDto(Verbrauchsgegenstand gegenstand) {
        VerbrauchsgegenstandDto dto = new VerbrauchsgegenstandDto();
        dto.setId(gegenstand.getId());
        if (gegenstand.getRaum() != null) {
            dto.setRaumId(gegenstand.getRaum().getId());
        }
        dto.setName(gegenstand.getName());
        dto.setSeriennummer(gegenstand.getSeriennummer());
        dto.setVerbrauchsart(gegenstand.getVerbrauchsart());
        dto.setEinheit(gegenstand.getEinheit());
        dto.setAktiv(gegenstand.isAktiv());
        return dto;
    }

    public Verbrauchsgegenstand toEntity(VerbrauchsgegenstandDto dto) {
        Verbrauchsgegenstand entity = new Verbrauchsgegenstand();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setSeriennummer(dto.getSeriennummer());
        entity.setVerbrauchsart(dto.getVerbrauchsart());
        entity.setEinheit(dto.getEinheit());
        entity.setAktiv(dto.isAktiv());
        return entity;
    }

    public ZaehlerstandDto toDto(Zaehlerstand zaehlerstand) {
        ZaehlerstandDto dto = new ZaehlerstandDto();
        dto.setId(zaehlerstand.getId());
        if (zaehlerstand.getVerbrauchsgegenstand() != null) {
            dto.setVerbrauchsgegenstandId(zaehlerstand.getVerbrauchsgegenstand().getId());
        }
        dto.setAbrechnungsJahr(zaehlerstand.getAbrechnungsJahr());
        dto.setStichtag(zaehlerstand.getStichtag());
        dto.setStand(zaehlerstand.getStand());
        dto.setVerbrauch(zaehlerstand.getVerbrauch());
        dto.setKommentar(zaehlerstand.getKommentar());
        return dto;
    }

    public Zaehlerstand toEntity(ZaehlerstandDto dto) {
        Zaehlerstand entity = new Zaehlerstand();
        entity.setId(dto.getId());
        entity.setAbrechnungsJahr(dto.getAbrechnungsJahr());
        entity.setStichtag(dto.getStichtag());
        entity.setStand(dto.getStand());
        entity.setVerbrauch(dto.getVerbrauch());
        entity.setKommentar(dto.getKommentar());
        if (dto.getVerbrauchsgegenstandId() != null) {
            Verbrauchsgegenstand gegenstand = new Verbrauchsgegenstand();
            gegenstand.setId(dto.getVerbrauchsgegenstandId());
            entity.setVerbrauchsgegenstand(gegenstand);
        }
        return entity;
    }

    public KostenstelleDto toDto(Kostenstelle kostenstelle) {
        KostenstelleDto dto = new KostenstelleDto();
        dto.setId(kostenstelle.getId());
        if (kostenstelle.getMietobjekt() != null) {
            dto.setMietobjektId(kostenstelle.getMietobjekt().getId());
        }
        dto.setName(kostenstelle.getName());
        dto.setBeschreibung(kostenstelle.getBeschreibung());
        dto.setUmlagefaehig(kostenstelle.isUmlagefaehig());
        dto.setStandardSchluesselId(Optional.ofNullable(kostenstelle.getStandardSchluessel())
                .map(Verteilungsschluessel::getId)
                .orElse(null));
        return dto;
    }

    public Kostenstelle toEntity(KostenstelleDto dto) {
        Kostenstelle entity = new Kostenstelle();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setUmlagefaehig(dto.isUmlagefaehig());
        if (dto.getStandardSchluesselId() != null) {
            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(dto.getStandardSchluesselId());
            entity.setStandardSchluessel(schluessel);
        }
        return entity;
    }

    public VerteilungsschluesselDto toDto(Verteilungsschluessel schluessel) {
        VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
        dto.setId(schluessel.getId());
        if (schluessel.getMietobjekt() != null) {
            dto.setMietobjektId(schluessel.getMietobjekt().getId());
        }
        dto.setName(schluessel.getName());
        dto.setBeschreibung(schluessel.getBeschreibung());
        dto.setTyp(schluessel.getTyp());
        if (schluessel.getEintraege() != null) {
            List<VerteilungsschluesselEintragDto> liste = new ArrayList<>();
            for (VerteilungsschluesselEintrag eintrag : schluessel.getEintraege()) {
                VerteilungsschluesselEintragDto eintragDto = new VerteilungsschluesselEintragDto();
                eintragDto.setId(eintrag.getId());
                eintragDto.setAnteil(eintrag.getAnteil());
                eintragDto.setKommentar(eintrag.getKommentar());
                if (eintrag.getMietpartei() != null) {
                    eintragDto.setMietparteiId(eintrag.getMietpartei().getId());
                }
                if (eintrag.getVerbrauchsgegenstand() != null) {
                    eintragDto.setVerbrauchsgegenstandId(eintrag.getVerbrauchsgegenstand().getId());
                }
                liste.add(eintragDto);
            }
            dto.setEintraege(liste);
        }
        return dto;
    }

    public Verteilungsschluessel toEntity(VerteilungsschluesselDto dto) {
        Verteilungsschluessel entity = new Verteilungsschluessel();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setTyp(dto.getTyp());
        if (dto.getEintraege() != null) {
            List<VerteilungsschluesselEintrag> liste = new ArrayList<>();
            for (VerteilungsschluesselEintragDto eintragDto : dto.getEintraege()) {
                VerteilungsschluesselEintrag eintrag = new VerteilungsschluesselEintrag();
                eintrag.setId(eintragDto.getId());
                eintrag.setAnteil(eintragDto.getAnteil());
                eintrag.setKommentar(eintragDto.getKommentar());
                if (eintragDto.getMietparteiId() != null) {
                    Mietpartei partei = new Mietpartei();
                    partei.setId(eintragDto.getMietparteiId());
                    eintrag.setMietpartei(partei);
                }
                if (eintragDto.getVerbrauchsgegenstandId() != null) {
                    Verbrauchsgegenstand gegenstand = new Verbrauchsgegenstand();
                    gegenstand.setId(eintragDto.getVerbrauchsgegenstandId());
                    eintrag.setVerbrauchsgegenstand(gegenstand);
                }
                eintrag.setVerteilungsschluessel(entity);
                liste.add(eintrag);
            }
            entity.setEintraege(liste);
        }
        return entity;
    }

    public KostenpositionDto toDto(Kostenposition kostenposition) {
        KostenpositionDto dto = new KostenpositionDto();
        dto.setId(kostenposition.getId());
        if (kostenposition.getKostenstelle() != null) {
            dto.setKostenstelleId(kostenposition.getKostenstelle().getId());
        }
        dto.setAbrechnungsJahr(kostenposition.getAbrechnungsJahr());
        dto.setBetrag(kostenposition.getBetrag());
        dto.setBerechnung(kostenposition.getBerechnung() != null ? kostenposition.getBerechnung() : KostenpositionBerechnung.BETRAG);
        dto.setVerbrauchsfaktor(kostenposition.getVerbrauchsfaktor());
        BigDecimal berechneterBetrag = kostenposition.getBetrag();
        BigDecimal verbrauchsmenge = null;
        Integer jahr = kostenposition.getAbrechnungsJahr();
        if (jahr != null) {
            try {
                var ergebnis = kostenpositionBerechner.berechne(kostenposition, jahr, new LinkedHashMap<>());
                if (ergebnis != null) {
                    berechneterBetrag = ergebnis.betrag();
                    verbrauchsmenge = ergebnis.verbrauchsSumme();
                }
            } catch (RuntimeException ignored) {
                // Bei fehlerhafter Konfiguration kann die Kostenposition dennoch angezeigt werden.
            }
        }
        dto.setBerechneterBetrag(berechneterBetrag);
        dto.setVerbrauchsmenge(verbrauchsmenge);
        dto.setBeschreibung(kostenposition.getBeschreibung());
        dto.setBelegNummer(kostenposition.getBelegNummer());
        dto.setBuchungsdatum(kostenposition.getBuchungsdatum());
        dto.setVerteilungsschluesselId(Optional.ofNullable(kostenposition.getVerteilungsschluesselOverride())
                .map(Verteilungsschluessel::getId)
                .orElse(null));
        return dto;
    }

    public Kostenposition toEntity(KostenpositionDto dto) {
        Kostenposition entity = new Kostenposition();
        entity.setId(dto.getId());
        entity.setAbrechnungsJahr(dto.getAbrechnungsJahr());
        entity.setBetrag(dto.getBetrag());
        entity.setBerechnung(dto.getBerechnung() != null ? dto.getBerechnung() : KostenpositionBerechnung.BETRAG);
        entity.setVerbrauchsfaktor(dto.getVerbrauchsfaktor());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setBelegNummer(dto.getBelegNummer());
        entity.setBuchungsdatum(dto.getBuchungsdatum());
        if (dto.getVerteilungsschluesselId() != null) {
            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(dto.getVerteilungsschluesselId());
            entity.setVerteilungsschluesselOverride(schluessel);
        }
        return entity;
    }

    public AnnualAccountingResponseDto toDto(AnnualAccountingResult result) {
        AnnualAccountingResponseDto dto = new AnnualAccountingResponseDto();
        dto.setMietobjektId(result.getMietobjektId());
        dto.setMietobjektName(result.getMietobjektName());
        dto.setMietobjektStrasse(result.getMietobjektStrasse());
        dto.setMietobjektPlz(result.getMietobjektPlz());
        dto.setMietobjektOrt(result.getMietobjektOrt());
        dto.setJahr(result.getAbrechnungsJahr());
        dto.setGesamtkosten(result.getGesamtkosten());
        dto.setGesamtkostenVorjahr(result.getGesamtkostenVorjahr());
        dto.setGesamtkostenDifferenz(result.getGesamtkostenDifferenz());

        if (result.getKostenstellen() != null) {
            for (AnnualAccountingResult.KostenstellenResult ks : result.getKostenstellen()) {
                AnnualAccountingCostCenterDto cDto = new AnnualAccountingCostCenterDto();
                cDto.setKostenstelleId(ks.getKostenstelle().getId());
                cDto.setKostenstelleName(ks.getKostenstelle().getName());
                cDto.setSumme(ks.getGesamtkosten());
                cDto.setVorjahr(ks.getGesamtkostenVorjahr());
                cDto.setDifferenz(safe(ks.getGesamtkosten()).subtract(safe(ks.getGesamtkostenVorjahr()), MC));
                if (ks.getParteianteile() != null) {
                    List<AnnualAccountingShareDto> teile = new ArrayList<>();
                    ks.getParteianteile().forEach(e -> {
                        AnnualAccountingShareDto shareDto = new AnnualAccountingShareDto();
                        shareDto.setMietparteiId(e.getMietpartei().getId());
                        shareDto.setMietparteiName(e.getMietpartei().getName());
                        shareDto.setRolle(e.getMietpartei().getRolle());
                        shareDto.setBetrag(e.getBetrag());
                        teile.add(shareDto);
                    });
                    cDto.setParteianteile(teile);
                }
                dto.getKostenstellen().add(cDto);
            }
        }

        if (result.getParteien() != null) {
            result.getParteien().forEach(p -> {
                AnnualAccountingPartyDto pDto = new AnnualAccountingPartyDto();
                pDto.setMietparteiId(p.getMietpartei().getId());
                pDto.setMietparteiName(p.getMietpartei().getName());
                pDto.setRolle(p.getMietpartei().getRolle());
                pDto.setSumme(p.getBetrag());
                pDto.setVorjahr(p.getBetragVorjahr());
                pDto.setDifferenz(p.getDifferenz());
                pDto.setMonatlicherVorschuss(p.getVorauszahlungMonatlich());
                pDto.setJahresVorauszahlung(p.getVorauszahlungJahr());
                pDto.setSaldo(p.getSaldo());
                dto.getParteien().add(pDto);
            });
        }

        if (result.getVerbrauchsvergleiche() != null) {
            result.getVerbrauchsvergleiche().forEach(v -> {
                AnnualAccountingConsumptionDto cDto = new AnnualAccountingConsumptionDto();
                Verbrauchsgegenstand g = v.getVerbrauchsgegenstand();
                cDto.setVerbrauchsgegenstandId(g.getId());
                cDto.setName(g.getName());
                cDto.setRaumName(v.getRaumName());
                cDto.setVerbrauchsart(g.getVerbrauchsart());
                cDto.setEinheit(g.getEinheit());
                cDto.setVerbrauchJahr(v.getVerbrauchJahr());
                cDto.setVerbrauchVorjahr(v.getVerbrauchVorjahr());
                cDto.setDifferenz(v.getDifferenz());
                dto.getVerbrauchsvergleiche().add(cDto);
            });
        }
        return dto;
    }
}
