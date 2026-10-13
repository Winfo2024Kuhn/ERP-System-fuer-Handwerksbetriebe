package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Löst beim Versand eines Angebots die noch offenen Online-Links anderer Angebote desselben
 * Vorgangs (Projekt bzw. Anfrage) ab. Sonst könnte der Kunde ein überholtes Angebot online
 * annehmen, obwohl er inzwischen ein neueres bekommen hat.
 *
 * <p><strong>Nur Angebot ersetzt Angebot.</strong> Nachtragsangebote sind nach
 * {@code AusgangsGeschaeftsDokumentService#erstellen} eigene, parallele Vorgänge mit eigener
 * Folgekette (zusätzliche Leistungen) – sie lösen weder das Hauptangebot noch andere Nachträge
 * ab und werden auch nicht abgelöst.</p>
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
     * Offene (noch nicht abgelaufene) Links anderer Angebote im selben Vorgang, ältester zuerst.
     * Leer für unbekannte Dokumente und für alle Typen außer {@link AusgangsGeschaeftsDokumentTyp#ANGEBOT}.
     */
    @Transactional(readOnly = true)
    public List<DokumentFreigabe> findeAbzuloesendeFreigaben(Long dokumentId)
    {
        if (dokumentId == null)
        {
            return List.of();
        }
        return dokumentRepository.findById(dokumentId)
                .map(dok -> offeneFreigabenAndererAngebote(dok, LocalDateTime.now()))
                .orElse(List.of());
    }

    /**
     * Zieht die Links aus {@link #findeAbzuloesendeFreigaben(Long)} zurück (REVOKED). Wird erst
     * nach dem erfolgreichen Versand aufgerufen und läuft in eigener Transaktion: Ein Fehler hier
     * darf den bereits erfolgten Versand nicht zurückrollen – der Aufrufer loggt nur.
     *
     * @return Anzahl der zurückgezogenen Links
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int loeseAb(Long dokumentId)
    {
        if (dokumentId == null)
        {
            return 0;
        }
        AusgangsGeschaeftsDokument dok = dokumentRepository.findById(dokumentId).orElse(null);
        if (dok == null)
        {
            return 0;
        }
        LocalDateTime jetzt = LocalDateTime.now();
        List<DokumentFreigabe> abzuloesen = offeneFreigabenAndererAngebote(dok, jetzt);
        for (DokumentFreigabe alt : abzuloesen)
        {
            alt.widerrufen(jetzt);
            freigabeRepository.save(alt);
            log.info("Online-Link zu {} durch Versand von {} abgelöst", alt.getDokumentNummer(), dok.getDokumentNummer());
        }
        return abzuloesen.size();
    }

    private List<DokumentFreigabe> offeneFreigabenAndererAngebote(AusgangsGeschaeftsDokument dok, LocalDateTime jetzt)
    {
        if (dok.getTyp() != AusgangsGeschaeftsDokumentTyp.ANGEBOT)
        {
            return List.of();
        }
        List<Long> andereAngebote = andereAngeboteImVorgang(dok);
        if (andereAngebote.isEmpty())
        {
            return List.of();
        }
        return freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, andereAngebote).stream()
                .filter(f -> f.getStatus() == FreigabeStatus.PENDING)
                .filter(f -> f.getAblaufDatum() != null && f.getAblaufDatum().isAfter(jetzt))
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
        List<Long> ids = new ArrayList<>();
        for (AusgangsGeschaeftsDokument d : kandidaten.values())
        {
            if (d.getId() != null && !d.getId().equals(dok.getId())
                    && d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
            {
                ids.add(d.getId());
            }
        }
        return ids;
    }
}
