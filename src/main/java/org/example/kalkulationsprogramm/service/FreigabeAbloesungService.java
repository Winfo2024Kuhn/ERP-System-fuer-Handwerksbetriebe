package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.dto.Freigabe.AbzuloesendeFreigabeDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Löst beim Versand eines Angebots noch offene Online-Links ab, die danach nicht mehr gelten
 * dürfen. Sonst könnte der Kunde eine überholte Fassung online annehmen.
 *
 * <ul>
 *   <li><strong>Eigene ältere Links</strong> (Angebot und Nachtragsangebot): Geht das Dokument
 *       erneut raus – auch ohne neuen Link –, gilt die alte Fassung nicht mehr. Der Link, der in
 *       der gerade verschickten Mail steht, bleibt natürlich gültig.</li>
 *   <li><strong>Links anderer Angebote</strong> im selben Vorgang (Projekt bzw. Anfrage).
 *       <em>Nur Angebot ersetzt Angebot:</em> Nachtragsangebote sind nach
 *       {@code AusgangsGeschaeftsDokumentService#erstellen} eigene, parallele Vorgänge mit eigener
 *       Folgekette (zusätzliche Leistungen) – sie lösen weder das Hauptangebot noch andere
 *       Nachträge ab und werden auch nicht von ihnen abgelöst.</li>
 * </ul>
 *
 * <p>Angenommene, abgelaufene und schon zurückgezogene Links bleiben unverändert.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreigabeAbloesungService
{
    private final DokumentFreigabeRepository freigabeRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;

    /**
     * Offene (noch nicht abgelaufene) Links, die beim Versand dieses Dokuments ungültig werden,
     * ältester zuerst. Leer für unbekannte Dokumente und Typen ohne Online-Annahme.
     */
    @Transactional(readOnly = true)
    public List<AbzuloesendeFreigabeDto> findeAbzuloesendeFreigaben(Long dokumentId)
    {
        if (dokumentId == null)
        {
            return List.of();
        }
        return dokumentRepository.findById(dokumentId)
                .map(dok -> offeneAbzuloesendeFreigaben(dok, LocalDateTime.now(), Set.of()).stream()
                        .map(f -> new AbzuloesendeFreigabeDto(f.getQuellDokumentId(), f.getDokumentNummer(),
                                f.getDokumentArt(), f.getAblaufDatum(), dokumentId.equals(f.getQuellDokumentId())))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Zieht nach dem erfolgreichen Versand die abzulösenden Links zurück (REVOKED).
     *
     * <p>Die Liste wird hier <strong>beim Senden neu berechnet</strong>, nicht aus der Rückfrage
     * übernommen: Zwischen Rückfrage und Senden kann ein Link abgelaufen oder angenommen worden
     * sein – der bleibt dann unberührt.</p>
     *
     * <p>Die Dokument-ID kommt vom Client. Deshalb wird sie an die verschickte Mail gebunden: Sie
     * muss ein Angebot/Nachtragsangebot sein und zum Projekt bzw. zur Anfrage der Mail gehören,
     * sonst passiert nichts. Läuft in eigener Transaktion: Ein Fehler hier darf den bereits
     * erfolgten Versand nicht zurückrollen – der Aufrufer loggt nur.</p>
     *
     * @param projektId          Projekt der verschickten Mail (oder {@code null})
     * @param anfrageId          Anfrage der verschickten Mail (oder {@code null})
     * @param uuidsImVersand     Freigabe-Links, die in der verschickten Mail stehen – bleiben gültig
     * @return Anzahl der zurückgezogenen Links
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int loeseAbNachVersand(Long dokumentId, Long projektId, Long anfrageId, Set<String> uuidsImVersand)
    {
        if (dokumentId == null)
        {
            return 0;
        }
        AusgangsGeschaeftsDokument dok = dokumentRepository.findById(dokumentId).orElse(null);
        if (dok == null || !hatOnlineAnnahme(dok.getTyp()) || !gehoertZurMail(dok, projektId, anfrageId))
        {
            log.warn("Ablösen alter Online-Links ignoriert: Dokument {} passt nicht zur Mail (Projekt {}, Anfrage {})",
                    dokumentId, projektId, anfrageId);
            return 0;
        }
        LocalDateTime jetzt = LocalDateTime.now();
        List<DokumentFreigabe> abzuloesen = offeneAbzuloesendeFreigaben(dok, jetzt,
                uuidsImVersand == null ? Set.of() : uuidsImVersand);
        for (DokumentFreigabe alt : abzuloesen)
        {
            alt.widerrufen(jetzt);
            freigabeRepository.save(alt);
            log.info("Online-Link zu {} durch Versand von {} abgelöst", alt.getDokumentNummer(), dok.getDokumentNummer());
        }
        return abzuloesen.size();
    }

    private static boolean hatOnlineAnnahme(AusgangsGeschaeftsDokumentTyp typ)
    {
        return typ == AusgangsGeschaeftsDokumentTyp.ANGEBOT || typ == AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT;
    }

    /** Gehört das Dokument zum Projekt bzw. zur Anfrage der verschickten Mail (auch über Anfrage→Projekt)? */
    static boolean gehoertZurMail(AusgangsGeschaeftsDokument dok, Long projektId, Long anfrageId)
    {
        Long dokProjekt = dok.getProjekt() != null ? dok.getProjekt().getId() : null;
        Long dokAnfrage = dok.getAnfrage() != null ? dok.getAnfrage().getId() : null;
        Long anfrageProjekt = dok.getAnfrage() != null && dok.getAnfrage().getProjekt() != null
                ? dok.getAnfrage().getProjekt().getId() : null;
        if (projektId != null && (projektId.equals(dokProjekt) || projektId.equals(anfrageProjekt)))
        {
            return true;
        }
        return anfrageId != null && anfrageId.equals(dokAnfrage);
    }

    private List<DokumentFreigabe> offeneAbzuloesendeFreigaben(AusgangsGeschaeftsDokument dok, LocalDateTime jetzt,
                                                               Set<String> behalten)
    {
        if (!hatOnlineAnnahme(dok.getTyp()))
        {
            return List.of();
        }
        List<Long> quellen = new ArrayList<>();
        quellen.add(dok.getId());
        if (dok.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
        {
            quellen.addAll(andereAngeboteImVorgang(dok));
        }
        return freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, quellen).stream()
                .filter(f -> f.getStatus() == FreigabeStatus.PENDING)
                .filter(f -> f.getAblaufDatum() != null && f.getAblaufDatum().isAfter(jetzt))
                .filter(f -> f.getUuid() == null || !behalten.contains(f.getUuid().toLowerCase(java.util.Locale.ROOT)))
                .sorted(Comparator.comparing(DokumentFreigabe::getErstelltAm,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Andere Angebote im selben Vorgang: im selben Projekt (auch solche, die noch an der
     * Anfrage des Projekts hängen) und in derselben Anfrage.
     */
    private List<Long> andereAngeboteImVorgang(AusgangsGeschaeftsDokument dok)
    {
        Map<Long, AusgangsGeschaeftsDokument> kandidaten = new LinkedHashMap<>();
        if (dok.getProjekt() != null && dok.getProjekt().getId() != null)
        {
            Long projektId = dok.getProjekt().getId();
            dokumentRepository.findByProjektIdOrderByDatumDesc(projektId).forEach(d -> kandidaten.put(d.getId(), d));
            dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(projektId).forEach(d -> kandidaten.put(d.getId(), d));
        }
        if (dok.getAnfrage() != null && dok.getAnfrage().getId() != null)
        {
            dokumentRepository.findByAnfrageIdOrderByDatumDesc(dok.getAnfrage().getId())
                    .forEach(d -> kandidaten.put(d.getId(), d));
        }
        return kandidaten.values().stream()
                .filter(d -> d.getId() != null && !Objects.equals(d.getId(), dok.getId()))
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(AusgangsGeschaeftsDokument::getId)
                .toList();
    }
}
