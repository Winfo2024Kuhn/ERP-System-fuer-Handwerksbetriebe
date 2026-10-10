package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentDuplikatServiceTest {

    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private EmailAttachmentRepository attachmentRepository;
    @Mock private LieferantReklamationRepository reklamationRepository;
    @Mock private LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private LieferantDokumentDuplikatService service;
    private final Map<Long, LieferantDokument> dokumente = new HashMap<>();
    private final List<Object[]> duplikatZeilen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new LieferantDokumentDuplikatService(dokumentRepository, attachmentRepository,
                reklamationRepository, sperreRepository, transactionManager);
        when(dokumentRepository.findDateiDuplikate()).thenReturn(duplikatZeilen);
        lenient().when(dokumentRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(dokumente::get).toList();
        });
        lenient().when(attachmentRepository.findByLieferantDokumentId(anyLong())).thenReturn(List.of());
    }

    private LieferantDokument dokument(long id, long lieferantId, String datei, LieferantDokumentTyp typ) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setTyp(typ);
        d.setGespeicherterDateiname(datei);
        dokumente.put(id, d);
        duplikatZeilen.add(new Object[] { id, lieferantId, datei });
        return d;
    }

    private static void bezahlt(LieferantDokument d) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setBezahlt(true);
        d.setGeschaeftsdaten(gd);
    }

    @Test
    void behaeltDasMitDemAnhangVerknuepfteUndLoeschtDieUebrigen() {
        LieferantDokument alt1 = dokument(850, 4, "uuid_ZND1.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument alt2 = dokument(851, 4, "uuid_ZND1.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument verknuepft = dokument(852, 4, "uuid_ZND1.pdf", LieferantDokumentTyp.SONSTIG);
        EmailAttachment anhang = new EmailAttachment();
        anhang.setLieferantDokument(verknuepft);
        when(attachmentRepository.findByLieferantDokumentId(852L)).thenReturn(List.of(anhang));

        LieferantDokumentDuplikatService.Ergebnis ergebnis = service.bereinigeDateiDuplikate();

        assertThat(ergebnis).isEqualTo(new LieferantDokumentDuplikatService.Ergebnis(1, 2, 0));
        verify(dokumentRepository).delete(alt1);
        verify(dokumentRepository).delete(alt2);
        verify(dokumentRepository, never()).delete(verknuepft);
        assertThat(anhang.getLieferantDokument()).isSameAs(verknuepft);
    }

    @Test
    void bevorzugtSichtbaresUndVonHandUmgestelltesExemplar() {
        LieferantDokument ausgeblendet = dokument(10, 4, "uuid_a.pdf", LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        ausgeblendet.setAusgeblendet(true);
        LieferantDokument sonstig = dokument(11, 4, "uuid_a.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument zeugnis = dokument(12, 4, "uuid_a.pdf", LieferantDokumentTyp.WERKSTOFFZEUGNIS);

        service.bereinigeDateiDuplikate();

        verify(dokumentRepository, never()).delete(zeugnis);
        verify(dokumentRepository).delete(ausgeblendet);
        verify(dokumentRepository).delete(sonstig);
    }

    @Test
    void uebernimmtAnhangUndVerknuepfungenDesGeloeschten() {
        LieferantDokument alt = dokument(20, 4, "uuid_b.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument neu = dokument(21, 4, "uuid_b.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        neu.setAusgeblendet(true);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(99L);
        LieferantDokument ab = new LieferantDokument();
        ab.setId(98L);
        neu.getVerknuepfteDokumente().add(rechnung);
        ab.getVerknuepfteDokumente().add(neu);
        neu.getVerknuepftVon().add(ab);
        EmailAttachment anhang = new EmailAttachment();
        anhang.setLieferantDokument(neu);
        when(attachmentRepository.findByLieferantDokumentId(21L)).thenReturn(List.of(anhang));

        service.bereinigeDateiDuplikate();

        verify(dokumentRepository).delete(neu);
        assertThat(anhang.getLieferantDokument()).isSameAs(alt);
        assertThat(alt.getVerknuepfteDokumente()).containsExactly(rechnung);
        assertThat(ab.getVerknuepfteDokumente()).containsExactly(alt);
    }

    @Test
    void behaeltDasVonHandGepflegteAuchWennEsAusgeblendetIst() {
        LieferantDokument gepflegt = dokument(30, 4, "uuid_c.pdf", LieferantDokumentTyp.RECHNUNG);
        gepflegt.setAusgeblendet(true);
        bezahlt(gepflegt);
        LieferantDokument kopie = dokument(31, 4, "uuid_c.pdf", LieferantDokumentTyp.RECHNUNG);

        service.bereinigeDateiDuplikate();

        verify(dokumentRepository).delete(kopie);
        verify(dokumentRepository, never()).delete(gepflegt);
    }

    @Test
    void laesstGruppeUnangetastetWennMehrereVonHandGepflegtSind() {
        LieferantDokument mitProjekt = dokument(40, 4, "uuid_d.pdf", LieferantDokumentTyp.RECHNUNG);
        mitProjekt.getProjektAnteile().add(new LieferantDokumentProjektAnteil());
        LieferantDokument mitReklamation = dokument(41, 4, "uuid_d.pdf", LieferantDokumentTyp.RECHNUNG);
        when(reklamationRepository.existsByLieferscheinId(41L)).thenReturn(true);

        LieferantDokumentDuplikatService.Ergebnis ergebnis = service.bereinigeDateiDuplikate();

        assertThat(ergebnis).isEqualTo(new LieferantDokumentDuplikatService.Ergebnis(1, 0, 1));
        verify(dokumentRepository, never()).delete(any(LieferantDokument.class));
    }

    @Test
    void gleicheDateiBeiVerschiedenenLieferantenIstKeinDuplikat() {
        // Die Query liefert nur echte Gruppen; zwei Lieferanten ergeben zwei Einzel-"Gruppen"
        dokument(50, 4, "uuid_e.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(51, 5, "uuid_e.pdf", LieferantDokumentTyp.SONSTIG);

        LieferantDokumentDuplikatService.Ergebnis ergebnis = service.bereinigeDateiDuplikate();

        assertThat(ergebnis.geloescht()).isZero();
        verify(dokumentRepository, never()).delete(any(LieferantDokument.class));
    }

    @Test
    void fehlerInEinerGruppeStopptDieAnderenNicht() {
        LieferantDokument kaputt = dokument(60, 4, "uuid_f.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(61, 4, "uuid_f.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(70, 4, "uuid_g.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument ok = dokument(71, 4, "uuid_g.pdf", LieferantDokumentTyp.SONSTIG);
        // 60 bleibt (ältestes), 61 soll gelöscht werden und scheitert am Fremdschlüssel
        doThrow(new IllegalStateException("FK")).when(dokumentRepository).delete(dokumente.get(61L));

        LieferantDokumentDuplikatService.Ergebnis ergebnis = service.bereinigeDateiDuplikate();

        assertThat(ergebnis).isEqualTo(new LieferantDokumentDuplikatService.Ergebnis(2, 1, 1));
        verify(dokumentRepository).delete(ok);
        verify(dokumentRepository, never()).delete(kaputt);
    }

    @Test
    void vonHandGeloestesPaarKommtNichtZurueckUndDieSperreGehtAufDasBehalteneUeber() {
        LieferantDokument behalten = dokument(80, 4, "uuid_h.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument kopie = dokument(81, 4, "uuid_h.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        kopie.setAusgeblendet(true);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(90L);
        LieferantDokument ab = new LieferantDokument();
        ab.setId(91L);
        // Am behaltenen Lieferschein wurde die Rechnung von Hand gelöst – die Kopie hat sie noch
        kopie.getVerknuepfteDokumente().add(rechnung);
        when(sperreRepository.findByBeteiligtemDokument(80L)).thenReturn(List.of(
                new LieferantDokumentVerknuepfungSperre(80L, 90L, LocalDateTime.of(2026, 9, 1, 8, 0))));
        // An der Kopie wurde die AB von Hand gelöst – das soll für das behaltene weiter gelten
        LocalDateTime gesperrtAm = LocalDateTime.of(2026, 9, 2, 8, 0);
        when(sperreRepository.findByBeteiligtemDokument(81L)).thenReturn(List.of(
                new LieferantDokumentVerknuepfungSperre(81L, 90L, gesperrtAm),
                new LieferantDokumentVerknuepfungSperre(81L, 91L, gesperrtAm),
                new LieferantDokumentVerknuepfungSperre(81L, 80L, gesperrtAm)));

        service.bereinigeDateiDuplikate();

        verify(dokumentRepository).delete(kopie);
        assertThat(behalten.getVerknuepfteDokumente()).doesNotContain(rechnung);
        ArgumentCaptor<LieferantDokumentVerknuepfungSperre> neu =
                ArgumentCaptor.forClass(LieferantDokumentVerknuepfungSperre.class);
        verify(sperreRepository).save(neu.capture());
        // Nur die AB-Sperre ist neu: die Rechnung ist schon gesperrt, 80↔81 liegt in der Gruppe
        assertThat(neu.getValue().getDokumentId()).isEqualTo(80L);
        assertThat(neu.getValue().getVerknuepftId()).isEqualTo(91L);
        assertThat(neu.getValue().getGesperrtAm()).isEqualTo(gesperrtAm);
    }

    @Test
    void sperreBehaeltIhreRichtungWennDasDuplikatDerVorgaengerIst() {
        dokument(82, 4, "uuid_i.pdf", LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        LieferantDokument kopie = dokument(83, 4, "uuid_i.pdf", LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        kopie.setAusgeblendet(true);
        lenient().when(sperreRepository.findByBeteiligtemDokument(83L)).thenReturn(List.of(
                new LieferantDokumentVerknuepfungSperre(95L, 83L, LocalDateTime.of(2026, 9, 3, 8, 0))));

        service.bereinigeDateiDuplikate();

        ArgumentCaptor<LieferantDokumentVerknuepfungSperre> neu =
                ArgumentCaptor.forClass(LieferantDokumentVerknuepfungSperre.class);
        verify(sperreRepository).save(neu.capture());
        assertThat(neu.getValue().getDokumentId()).isEqualTo(95L);
        assertThat(neu.getValue().getVerknuepftId()).isEqualTo(82L);
    }

    @Test
    void sperreDerKopieWirdNichtUebernommenWennDasBehalteneVerknuepftIst() {
        LieferantDokument behalten = dokument(84, 4, "uuid_j.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument kopie = dokument(85, 4, "uuid_j.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        kopie.setAusgeblendet(true);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(96L);
        behalten.getVerknuepfteDokumente().add(rechnung);
        lenient().when(sperreRepository.findByBeteiligtemDokument(85L)).thenReturn(List.of(
                new LieferantDokumentVerknuepfungSperre(85L, 96L, LocalDateTime.of(2026, 9, 4, 8, 0))));

        service.bereinigeDateiDuplikate();

        verify(dokumentRepository).delete(kopie);
        verify(sperreRepository, never()).save(any(LieferantDokumentVerknuepfungSperre.class));
        assertThat(behalten.getVerknuepfteDokumente()).containsExactly(rechnung);
    }
}
