package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreigabeAbloesungServiceTest
{
    @Mock
    private DokumentFreigabeRepository freigabeRepository;
    @Mock
    private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @InjectMocks
    private FreigabeAbloesungService service;

    private static Projekt projekt(long id)
    {
        Projekt p = new Projekt();
        p.setId(id);
        return p;
    }

    private static Anfrage anfrage(long id)
    {
        Anfrage a = new Anfrage();
        a.setId(id);
        return a;
    }

    private static AusgangsGeschaeftsDokument dok(long id, AusgangsGeschaeftsDokumentTyp typ, Projekt projekt, Anfrage anfrage)
    {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setId(id);
        d.setTyp(typ);
        d.setProjekt(projekt);
        d.setAnfrage(anfrage);
        d.setDokumentNummer("AG-" + id);
        return d;
    }

    private static DokumentFreigabe freigabe(long quellId, FreigabeStatus status, LocalDateTime ablauf)
    {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        f.setQuellDokumentId(quellId);
        f.setDokumentNummer("AG-" + quellId);
        f.setStatus(status);
        f.setAblaufDatum(ablauf);
        f.setDokumentDatei("alt.pdf");
        f.setErstelltAm(LocalDateTime.now().minusDays(3));
        return f;
    }

    @Test
    @DisplayName("Findet nur offene, nicht abgelaufene Links anderer Angebote im selben Projekt")
    void findetOffeneLinksImProjekt()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        AusgangsGeschaeftsDokument altesAngebot = dok(11L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        AusgangsGeschaeftsDokument nachtrag = dok(12L, AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT, p, null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu, altesAngebot, nachtrag));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of());
        DokumentFreigabe offen = freigabe(11L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        DokumentFreigabe abgelaufen = freigabe(11L, FreigabeStatus.PENDING, LocalDateTime.now().minusMinutes(1));
        DokumentFreigabe angenommen = freigabe(11L, FreigabeStatus.ACCEPTED, LocalDateTime.now().plusDays(5));
        DokumentFreigabe zurueckgezogen = freigabe(11L, FreigabeStatus.REVOKED, LocalDateTime.now().plusDays(5));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(11L)))
                .thenReturn(List.of(offen, abgelaufen, angenommen, zurueckgezogen));

        assertThat(service.findeAbzuloesendeFreigaben(10L)).containsExactly(offen);
    }

    @Test
    @DisplayName("Angebot der Anfrage und Angebot am daraus entstandenen Projekt gelten als ein Vorgang")
    void anfrageUndProjektSindEinVorgang()
    {
        Projekt p = projekt(1L);
        Anfrage a = anfrage(2L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        AusgangsGeschaeftsDokument anfrageAngebot = dok(20L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, null, a);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of(anfrageAngebot));
        DokumentFreigabe offen = freigabe(20L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(20L))).thenReturn(List.of(offen));

        assertThat(service.findeAbzuloesendeFreigaben(10L)).containsExactly(offen);
    }

    @Test
    @DisplayName("Angebote derselben Anfrage (ohne Projekt) werden gefunden")
    void angeboteDerselbenAnfrage()
    {
        Anfrage a = anfrage(2L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, null, a);
        AusgangsGeschaeftsDokument alt = dok(21L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, null, a);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(2L)).thenReturn(List.of(alt, neu));
        DokumentFreigabe offen = freigabe(21L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(21L))).thenReturn(List.of(offen));

        assertThat(service.findeAbzuloesendeFreigaben(10L)).containsExactly(offen);
    }

    @Test
    @DisplayName("Nachtragsangebote loesen nichts ab – sie sind eigene, zusaetzliche Vorgaenge")
    void nachtragLoestNichtsAb()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument nachtrag = dok(12L, AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT, p, null);
        when(dokumentRepository.findById(12L)).thenReturn(Optional.of(nachtrag));

        assertThat(service.findeAbzuloesendeFreigaben(12L)).isEmpty();
        assertThat(service.loeseAb(12L)).isZero();
        verify(freigabeRepository, never()).findByQuelle(any(), anyList());
        verify(freigabeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ohne andere Angebote im Vorgang wird gar nicht nach Links gesucht")
    void ohneAndereAngebote()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of());

        assertThat(service.findeAbzuloesendeFreigaben(10L)).isEmpty();
        verify(freigabeRepository, never()).findByQuelle(any(), anyList());
    }

    @Test
    @DisplayName("Unbekanntes Dokument oder fehlende ID: leere Liste, nichts abgeloest")
    void unbekanntesDokument()
    {
        when(dokumentRepository.findById(eq(Long.MAX_VALUE))).thenReturn(Optional.empty());

        assertThat(service.findeAbzuloesendeFreigaben(Long.MAX_VALUE)).isEmpty();
        assertThat(service.findeAbzuloesendeFreigaben(null)).isEmpty();
        assertThat(service.loeseAb(Long.MAX_VALUE)).isZero();
        assertThat(service.loeseAb(null)).isZero();
        verify(freigabeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Abloesen setzt offene Links auf REVOKED, loest die PDF und laesst angenommene unberuehrt")
    void loeseAbWiderruftOffeneLinks()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        AusgangsGeschaeftsDokument alt = dok(11L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu, alt));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of());
        DokumentFreigabe offen = freigabe(11L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        DokumentFreigabe angenommen = freigabe(11L, FreigabeStatus.ACCEPTED, LocalDateTime.now().plusDays(5));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(11L)))
                .thenReturn(List.of(offen, angenommen));

        int anzahl = service.loeseAb(10L);

        assertThat(anzahl).isEqualTo(1);
        assertThat(offen.getStatus()).isEqualTo(FreigabeStatus.REVOKED);
        assertThat(offen.getDokumentDatei()).isNull();
        assertThat(offen.getAblaufDatum()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(angenommen.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        assertThat(angenommen.getDokumentDatei()).isEqualTo("alt.pdf");
        verify(freigabeRepository).save(offen);
        verify(freigabeRepository, never()).save(angenommen);
    }
}
