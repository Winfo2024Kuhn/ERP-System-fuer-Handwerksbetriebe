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
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Freigabe.AbzuloesendeFreigabeDto;
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
        f.setUuid(java.util.UUID.randomUUID().toString());
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
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L, 11L)))
                .thenReturn(List.of(offen, abgelaufen, angenommen, zurueckgezogen));

        assertThat(service.findeAbzuloesendeFreigaben(10L))
                .extracting(AbzuloesendeFreigabeDto::dokumentNummer, AbzuloesendeFreigabeDto::diesesDokument)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AG-11", false));
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
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L, 20L))).thenReturn(List.of(offen));

        assertThat(service.findeAbzuloesendeFreigaben(10L)).extracting(AbzuloesendeFreigabeDto::dokumentId).containsExactly(20L);
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
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L, 21L))).thenReturn(List.of(offen));

        assertThat(service.findeAbzuloesendeFreigaben(10L)).extracting(AbzuloesendeFreigabeDto::dokumentId).containsExactly(21L);
    }

    @Test
    @DisplayName("Nachtragsangebote loesen keine anderen Angebote ab – nur den eigenen aelteren Link")
    void nachtragLoestNurEigenenLinkAb()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument nachtrag = dok(12L, AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT, p, null);
        when(dokumentRepository.findById(12L)).thenReturn(Optional.of(nachtrag));
        DokumentFreigabe eigenerAlterLink = freigabe(12L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(12L)))
                .thenReturn(List.of(eigenerAlterLink));

        assertThat(service.findeAbzuloesendeFreigaben(12L))
                .extracting(AbzuloesendeFreigabeDto::diesesDokument).containsExactly(true);
        verify(dokumentRepository, never()).findByProjektIdOrderByDatumDesc(any());
    }

    @Test
    @DisplayName("Ohne andere Angebote wird nur nach eigenen alten Links gesucht")
    void ohneAndereAngebote()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of());
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L))).thenReturn(List.of());

        assertThat(service.findeAbzuloesendeFreigaben(10L)).isEmpty();
    }

    @Test
    @DisplayName("Andere Dokumenttypen (z. B. Rechnung) haben keine Online-Annahme")
    void rechnungHatNichtsAbzuloesen()
    {
        AusgangsGeschaeftsDokument rechnung = dok(30L, AusgangsGeschaeftsDokumentTyp.RECHNUNG, projekt(1L), null);
        when(dokumentRepository.findById(30L)).thenReturn(Optional.of(rechnung));

        assertThat(service.findeAbzuloesendeFreigaben(30L)).isEmpty();
        assertThat(service.loeseAbNachVersand(30L, 1L, null, Set.of())).isZero();
        verify(freigabeRepository, never()).findByQuelle(any(), anyList());
    }

    @Test
    @DisplayName("Unbekanntes Dokument oder fehlende ID: leere Liste, nichts abgeloest")
    void unbekanntesDokument()
    {
        when(dokumentRepository.findById(eq(Long.MAX_VALUE))).thenReturn(Optional.empty());

        assertThat(service.findeAbzuloesendeFreigaben(Long.MAX_VALUE)).isEmpty();
        assertThat(service.findeAbzuloesendeFreigaben(null)).isEmpty();
        assertThat(service.loeseAbNachVersand(Long.MAX_VALUE, 1L, null, Set.of())).isZero();
        assertThat(service.loeseAbNachVersand(null, 1L, null, Set.of())).isZero();
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
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L, 11L)))
                .thenReturn(List.of(offen, angenommen));

        int anzahl = service.loeseAbNachVersand(10L, 1L, null, Set.of());

        assertThat(anzahl).isEqualTo(1);
        assertThat(offen.getStatus()).isEqualTo(FreigabeStatus.REVOKED);
        assertThat(offen.getDokumentDatei()).isNull();
        assertThat(offen.getAblaufDatum()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(angenommen.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        assertThat(angenommen.getDokumentDatei()).isEqualTo("alt.pdf");
        verify(freigabeRepository).save(offen);
        verify(freigabeRepository, never()).save(angenommen);
    }

    @Test
    @DisplayName("Erneuter Versand ohne Link: aelterer eigener Link wird abgeloest, der Link in der Mail bleibt")
    void eigenerAlterLinkWirdAbgeloestNeuerBleibt()
    {
        Projekt p = projekt(1L);
        AusgangsGeschaeftsDokument neu = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(neu));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(neu));
        when(dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(1L)).thenReturn(List.of());
        DokumentFreigabe alterLink = freigabe(10L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(5));
        DokumentFreigabe linkInDerMail = freigabe(10L, FreigabeStatus.PENDING, LocalDateTime.now().plusDays(14));
        when(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(10L)))
                .thenReturn(List.of(alterLink, linkInDerMail));

        assertThat(service.findeAbzuloesendeFreigaben(10L))
                .extracting(AbzuloesendeFreigabeDto::diesesDokument).containsExactly(true, true);

        int anzahl = service.loeseAbNachVersand(10L, 1L, null, Set.of(linkInDerMail.getUuid()));

        assertThat(anzahl).isEqualTo(1);
        assertThat(alterLink.getStatus()).isEqualTo(FreigabeStatus.REVOKED);
        assertThat(linkInDerMail.getStatus()).isEqualTo(FreigabeStatus.PENDING);
    }

    @Test
    @DisplayName("Dokument-ID eines fremden Projekts wird ignoriert – nichts wird zurueckgezogen")
    void fremdesProjektWirdIgnoriert()
    {
        AusgangsGeschaeftsDokument fremd = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, projekt(1L), null);
        when(dokumentRepository.findById(10L)).thenReturn(Optional.of(fremd));

        assertThat(service.loeseAbNachVersand(10L, 99L, null, Set.of())).isZero();
        assertThat(service.loeseAbNachVersand(10L, null, 5L, Set.of())).isZero();
        assertThat(service.loeseAbNachVersand(10L, null, null, Set.of())).isZero();
        verify(freigabeRepository, never()).findByQuelle(any(), anyList());
        verify(freigabeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Zuordnung zur Mail: gleiches Projekt, Anfrage des Projekts oder gleiche Anfrage")
    void gehoertZurMail()
    {
        Projekt p = projekt(1L);
        Anfrage a = anfrage(2L);
        a.setProjekt(p);
        AusgangsGeschaeftsDokument imProjekt = dok(10L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, p, null);
        AusgangsGeschaeftsDokument anDerAnfrage = dok(11L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, null, a);

        assertThat(FreigabeAbloesungService.gehoertZurMail(imProjekt, 1L, null)).isTrue();
        assertThat(FreigabeAbloesungService.gehoertZurMail(anDerAnfrage, 1L, null)).isTrue();
        assertThat(FreigabeAbloesungService.gehoertZurMail(anDerAnfrage, null, 2L)).isTrue();
        assertThat(FreigabeAbloesungService.gehoertZurMail(imProjekt, null, 2L)).isFalse();
        assertThat(FreigabeAbloesungService.gehoertZurMail(anDerAnfrage, 7L, 8L)).isFalse();
    }
}
