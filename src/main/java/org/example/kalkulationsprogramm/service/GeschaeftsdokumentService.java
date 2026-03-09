package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Geschaeftsdokument.*;
import org.example.kalkulationsprogramm.repository.GeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ZahlungRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeschaeftsdokumentService {

    private final GeschaeftsdokumentRepository dokumentRepository;
    private final ZahlungRepository zahlungRepository;
    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;

    // Dokumenttyp-Konstanten
    private static final Dokumenttyp TYP_ANGEBOT = Dokumenttyp.ANGEBOT;
    private static final Dokumenttyp TYP_AB = Dokumenttyp.AUFTRAGSBESTAETIGUNG;
    private static final Dokumenttyp TYP_ABSCHLAGSRECHNUNG = Dokumenttyp.ABSCHLAGSRECHNUNG;

    /**
     * Erstellt ein neues Geschäftsdokument.
     */
    @Transactional
    public Geschaeftsdokument erstellen(GeschaeftsdokumentErstellenDto dto) {
        Geschaeftsdokument dokument = new Geschaeftsdokument();

        // Dokumenttyp aus String auflösen
        Dokumenttyp typ = Dokumenttyp.fromLabel(dto.getDokumenttyp());
        dokument.setDokumenttyp(typ);

        dokument.setDatum(dto.getDatum() != null ? dto.getDatum() : LocalDate.now());
        dokument.setBetreff(dto.getBetreff());
        dokument.setBetragNetto(dto.getBetragNetto());
        dokument.setMwstSatz(dto.getMwstSatz() != null ? dto.getMwstSatz() : new BigDecimal("0.19"));
        dokument.setZahlungszielTage(dto.getZahlungszielTage());
        dokument.setHtmlInhalt(dto.getHtmlInhalt());

        // Bruttobetrag berechnen
        if (dto.getBetragNetto() != null && dokument.getMwstSatz() != null) {
            BigDecimal mwst = dto.getBetragNetto().multiply(dokument.getMwstSatz());
            dokument.setBetragBrutto(dto.getBetragNetto().add(mwst));
        }

        // Beziehungen setzen
        if (dto.getProjektId() != null) {
            dokument.setProjekt(projektRepository.findById(dto.getProjektId()).orElse(null));
        }
        if (dto.getKundeId() != null) {
            dokument.setKunde(kundeRepository.findById(dto.getKundeId()).orElse(null));
        }
        if (dto.getVorgaengerDokumentId() != null) {
            dokument.setVorgaengerDokument(dokumentRepository.findById(dto.getVorgaengerDokumentId()).orElse(null));
        }

        // Dokumentnummer generieren
        dokument.setDokumentNummer(generiereNummer(typ));

        // Bei Abschlagsrechnung: Nummer ermitteln
        if (TYP_ABSCHLAGSRECHNUNG == typ && dto.getVorgaengerDokumentId() != null) {
            int anzahl = countAbschlagsrechnungenByVorgaenger(dto.getVorgaengerDokumentId());
            dokument.setAbschlagsNummer(anzahl + 1);
        }

        return dokumentRepository.save(dokument);
    }

    /**
     * Konvertiert ein Dokument in einen anderen Typ.
     */
    @Transactional
    public Geschaeftsdokument konvertieren(Long vorgaengerId, String neuerDokumenttypName) {
        Geschaeftsdokument vorgaenger = dokumentRepository.findById(vorgaengerId)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + vorgaengerId));

        Dokumenttyp neuerTyp = Dokumenttyp.fromLabel(neuerDokumenttypName);

        Geschaeftsdokument neues = new Geschaeftsdokument();
        neues.setDokumenttyp(neuerTyp);
        neues.setDatum(LocalDate.now());
        neues.setBetreff(vorgaenger.getBetreff());
        neues.setBetragNetto(vorgaenger.getBetragNetto());
        neues.setBetragBrutto(vorgaenger.getBetragBrutto());
        neues.setMwstSatz(vorgaenger.getMwstSatz());
        neues.setProjekt(vorgaenger.getProjekt());
        neues.setKunde(vorgaenger.getKunde());
        neues.setVorgaengerDokument(vorgaenger);
        neues.setHtmlInhalt(vorgaenger.getHtmlInhalt());
        neues.setZahlungszielTage(vorgaenger.getZahlungszielTage());
        neues.setDokumentNummer(generiereNummer(neuerTyp));

        // Bei Abschlagsrechnung: Nummer ermitteln
        if (TYP_ABSCHLAGSRECHNUNG == neuerTyp) {
            Geschaeftsdokument ab = findeAuftragsbestaetigung(vorgaenger);
            if (ab != null) {
                int anzahl = countAbschlagsrechnungenByVorgaenger(ab.getId());
                neues.setAbschlagsNummer(anzahl + 1);
                neues.setVorgaengerDokument(ab);
            }
        }

        return dokumentRepository.save(neues);
    }

    /**
     * Berechnet die Abschluss-Informationen für ein Dokument.
     */
    public AbschlussInfoDto berechneAbschluss(Long dokumentId) {
        Geschaeftsdokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + dokumentId));

        AbschlussInfoDto info = new AbschlussInfoDto();

        // Grundbeträge
        info.setNettosumme(dokument.getBetragNetto());
        info.setMwstSatz(dokument.getMwstSatz());
        if (dokument.getMwstSatz() != null) {
            info.setMwstProzent(dokument.getMwstSatz().multiply(new BigDecimal("100")));
            if (dokument.getBetragNetto() != null) {
                info.setMwstBetrag(dokument.getBetragNetto().multiply(dokument.getMwstSatz())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        info.setGesamtsumme(dokument.getBetragBrutto());

        // Vorgänger-Referenzen finden
        findVorgaengerReferenzen(dokument, info);

        // Vorherige Zahlungen in der Kette sammeln
        List<VorherigeZahlungDto> vorherigeZahlungen = new ArrayList<>();
        BigDecimal summeVorherig = sammleVorherigeZahlungen(dokument, vorherigeZahlungen);
        info.setVorherigeZahlungen(vorherigeZahlungen);
        info.setSummeVorherigerZahlungen(summeVorherig);

        // Noch zu zahlen
        if (dokument.getBetragBrutto() != null) {
            info.setNochZuZahlen(dokument.getBetragBrutto().subtract(summeVorherig));
        }

        // Abschlagsrechnung-Info
        if (dokument.getAbschlagsNummer() != null) {
            info.setAktuelleAbschlagsNummer(dokument.getAbschlagsNummer());
            Geschaeftsdokument ab = findeAuftragsbestaetigung(dokument);
            if (ab != null) {
                int gesamt = countAbschlagsrechnungenByVorgaenger(ab.getId());
                info.setGesamtAbschlagsAnzahl(gesamt);
            }
        }

        return info;
    }

    /**
     * Erfasst eine Zahlung zu einem Dokument.
     */
    @Transactional
    public Zahlung zahlungErfassen(Long dokumentId, ZahlungErstellenDto dto) {
        Geschaeftsdokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + dokumentId));

        Zahlung zahlung = new Zahlung();
        zahlung.setGeschaeftsdokument(dokument);
        zahlung.setZahlungsdatum(dto.getZahlungsdatum() != null ? dto.getZahlungsdatum() : LocalDate.now());
        zahlung.setBetrag(dto.getBetrag());
        zahlung.setZahlungsart(dto.getZahlungsart());
        zahlung.setVerwendungszweck(dto.getVerwendungszweck());
        zahlung.setNotiz(dto.getNotiz());

        return zahlungRepository.save(zahlung);
    }

    /**
     * Findet alle Dokumente eines Projekts.
     */
    public List<GeschaeftsdokumentResponseDto> findByProjekt(Long projektId) {
        return dokumentRepository.findByProjektIdOrderByDatumDesc(projektId).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Findet ein Dokument und mappt es zu Response DTO.
     */
    public GeschaeftsdokumentResponseDto findById(Long id) {
        return dokumentRepository.findById(id)
                .map(this::toResponseDto)
                .orElse(null);
    }

    // --- Private Helper Methods ---

    private String generiereNummer(Dokumenttyp typ) {
        int year = Year.now().getValue();
        String kuerzel = getKuerzel(typ);
        String prefix = year + "-" + kuerzel + "-";
        Integer maxNummer = dokumentRepository.findMaxNummer(prefix).orElse(0);
        return prefix + "%04d".formatted(maxNummer + 1);
    }

    private String getKuerzel(Dokumenttyp typ) {
        if (typ == null)
            return "DOK";
        return switch (typ) {
            case ANGEBOT -> "A";
            case AUFTRAGSBESTAETIGUNG -> "AB";
            case RECHNUNG -> "R";
            case ABSCHLAGSRECHNUNG -> "AR";
            case TEILRECHNUNG -> "TR";
            case SCHLUSSRECHNUNG -> "SR";
            case STORNORECHNUNG -> "STO";
            case ZAHLUNGSERINNERUNG -> "ZE";
            case ERSTE_MAHNUNG -> "M1";
            case ZWEITE_MAHNUNG -> "M2";
            case GUTSCHRIFT -> "GS";
        };
    }

    private int countAbschlagsrechnungenByVorgaenger(Long vorgaengerId) {
        List<Geschaeftsdokument> nachfolger = dokumentRepository.findByVorgaengerDokumentId(vorgaengerId);
        return (int) nachfolger.stream()
                .filter(d -> TYP_ABSCHLAGSRECHNUNG == d.getDokumenttyp())
                .count();
    }

    private Geschaeftsdokument findeAuftragsbestaetigung(Geschaeftsdokument dokument) {
        Geschaeftsdokument current = dokument;
        while (current != null) {
            if (TYP_AB == current.getDokumenttyp()) {
                return current;
            }
            current = current.getVorgaengerDokument();
        }
        return null;
    }

    private void findVorgaengerReferenzen(Geschaeftsdokument dokument, AbschlussInfoDto info) {
        Geschaeftsdokument current = dokument.getVorgaengerDokument();
        while (current != null) {
            VorgaengerInfoDto vorgaengerInfo = new VorgaengerInfoDto();
            vorgaengerInfo.setId(current.getId());
            vorgaengerInfo.setDokumenttypId(null);
            vorgaengerInfo.setDokumenttypName(current.getDokumenttyp().getLabel());
            vorgaengerInfo.setDokumentNummer(current.getDokumentNummer());
            vorgaengerInfo.setBetragBrutto(current.getBetragBrutto());

            if (TYP_ANGEBOT == current.getDokumenttyp()) {
                info.setAngebotReferenz(vorgaengerInfo);
            } else if (TYP_AB == current.getDokumenttyp()) {
                info.setAuftragsbestaetigungReferenz(vorgaengerInfo);
            }

            current = current.getVorgaengerDokument();
        }
    }

    private BigDecimal sammleVorherigeZahlungen(Geschaeftsdokument dokument, List<VorherigeZahlungDto> liste) {
        BigDecimal summe = BigDecimal.ZERO;

        Geschaeftsdokument ab = findeAuftragsbestaetigung(dokument);
        if (ab != null) {
            List<Geschaeftsdokument> alleRechnungen = dokumentRepository.findByVorgaengerDokumentId(ab.getId());
            for (Geschaeftsdokument rechnung : alleRechnungen) {
                if (!rechnung.getId().equals(dokument.getId())) {
                    BigDecimal zahlungsSumme = rechnung.getSummeZahlungen();
                    if (zahlungsSumme.compareTo(BigDecimal.ZERO) > 0) {
                        VorherigeZahlungDto vzDto = new VorherigeZahlungDto();
                        String bezeichnung = rechnung.getAbschlagsNummer() != null
                                ? rechnung.getAbschlagsNummer() + ". " + rechnung.getDokumenttyp().getLabel()
                                : rechnung.getDokumenttyp().getLabel();
                        vzDto.setDokumentTypAnzeigename(bezeichnung);
                        vzDto.setDokumentNummer(rechnung.getDokumentNummer());
                        vzDto.setBetrag(zahlungsSumme);
                        liste.add(vzDto);
                        summe = summe.add(zahlungsSumme);
                    }
                }
            }
        }

        return summe;
    }

    private GeschaeftsdokumentResponseDto toResponseDto(Geschaeftsdokument dokument) {
        GeschaeftsdokumentResponseDto dto = new GeschaeftsdokumentResponseDto();
        dto.setId(dokument.getId());
        dto.setDokumenttypId(null);
        dto.setDokumenttypName(dokument.getDokumenttyp().getLabel());
        dto.setDokumentNummer(dokument.getDokumentNummer());
        dto.setDatum(dokument.getDatum());
        dto.setVersandDatum(dokument.getVersandDatum());
        dto.setBetragNetto(dokument.getBetragNetto());
        dto.setBetragBrutto(dokument.getBetragBrutto());
        dto.setMwstSatz(dokument.getMwstSatz());
        dto.setAbschlagsNummer(dokument.getAbschlagsNummer());
        dto.setBetreff(dokument.getBetreff());
        dto.setZahlungszielTage(dokument.getZahlungszielTage());
        dto.setStorniert(dokument.isStorniert());

        // MwSt-Betrag berechnen
        if (dokument.getBetragNetto() != null && dokument.getMwstSatz() != null) {
            dto.setMwstBetrag(dokument.getBetragNetto().multiply(dokument.getMwstSatz())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        // Beziehungen
        if (dokument.getProjekt() != null) {
            dto.setProjektId(dokument.getProjekt().getId());
            dto.setProjektBauvorhaben(dokument.getProjekt().getBauvorhaben());
        }
        if (dokument.getKunde() != null) {
            dto.setKundeId(dokument.getKunde().getId());
            dto.setKundenName(dokument.getKunde().getName());
        }

        // Vorgänger
        if (dokument.getVorgaengerDokument() != null) {
            VorgaengerInfoDto vorgaenger = new VorgaengerInfoDto();
            vorgaenger.setId(dokument.getVorgaengerDokument().getId());
            vorgaenger.setDokumenttypId(null);
            vorgaenger.setDokumenttypName(dokument.getVorgaengerDokument().getDokumenttyp().getLabel());
            vorgaenger.setDokumentNummer(dokument.getVorgaengerDokument().getDokumentNummer());
            vorgaenger.setBetragBrutto(dokument.getVorgaengerDokument().getBetragBrutto());
            dto.setVorgaenger(vorgaenger);
        }

        // Berechnete Felder
        dto.setSummeZahlungen(dokument.getSummeZahlungen());
        dto.setOffenerBetrag(dokument.getOffenerBetrag());
        dto.setBezahlt(dokument.istBezahlt());

        // Zahlungen
        dto.setZahlungen(dokument.getZahlungen().stream().map(z -> {
            ZahlungDto zDto = new ZahlungDto();
            zDto.setId(z.getId());
            zDto.setZahlungsdatum(z.getZahlungsdatum());
            zDto.setBetrag(z.getBetrag());
            zDto.setZahlungsart(z.getZahlungsart());
            zDto.setVerwendungszweck(z.getVerwendungszweck());
            zDto.setNotiz(z.getNotiz());
            return zDto;
        }).collect(Collectors.toList()));

        return dto;
    }
}
