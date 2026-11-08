package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.BwaTyp;
import org.example.kalkulationsprogramm.domain.BwaUpload;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.repository.BwaUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ablage einer BWA aus einer Steuerberater-Mail: KI-Werte übernehmen, Rückfälle, Idempotenz.
 */
@ExtendWith(MockitoExtension.class)
class SteuerberaterBwaAblageTest {

    @Mock
    private BwaUploadRepository bwaUploadRepository;
    @Mock
    private DokumentAnalyseService dokumentAnalyseService;

    @TempDir
    Path mailAttachmentDir;

    private SteuerberaterBwaAblage ablage;
    private SteuerberaterAnhang quelle;
    private SteuerberaterKontakt kanzlei;

    @BeforeEach
    void setUp() throws Exception {
        ablage = new SteuerberaterBwaAblage(bwaUploadRepository, dokumentAnalyseService,
                JsonMapper.builderWithJackson2Defaults().build(), mailAttachmentDir.toString());

        Files.write(mailAttachmentDir.resolve("stored-bwa.pdf"), new byte[]{1, 2, 3});
        EmailAttachment anhang = new EmailAttachment();
        anhang.setOriginalFilename("BWA_2026-03.pdf");
        anhang.setStoredFilename("stored-bwa.pdf");
        Email email = new Email();
        ReflectionTestUtils.setField(email, "id", 42L);
        kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Steuerkanzlei Test");
        quelle = new SteuerberaterAnhang(anhang, email, kanzlei);
    }

    private BwaUpload gespeichert() {
        ArgumentCaptor<BwaUpload> captor = ArgumentCaptor.forClass(BwaUpload.class);
        verify(bwaUploadRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void uebernimmtMonatUndGemeinkostenAusDerKiAntwort() {
        when(dokumentAnalyseService.rufKiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("```json\n{\"jahr\": 2026, \"monat\": 2, \"gesamtkosten\": 40000, \"personalkosten\": 25000}\n```");

        ablage.legeAb(quelle);

        BwaUpload bwa = gespeichert();
        assertThat(bwa.getTyp()).isEqualTo(BwaTyp.MONATLICH);
        assertThat(bwa.getJahr()).isEqualTo(2026);
        assertThat(bwa.getMonat()).isEqualTo(2);
        assertThat(bwa.getGesamtGemeinkosten()).isEqualByComparingTo("15000.00");
        assertThat(bwa.getAnalysiert()).isTrue();
        assertThat(bwa.getGespeicherterDateiname()).isEqualTo("stored-bwa.pdf");
        assertThat(bwa.getSteuerberater()).isSameAs(kanzlei);
        assertThat(bwa.getAiRawJson()).contains("gesamtkosten");
    }

    @Test
    void ohneGemeinkostenBleibtDieBwaAusstehendUndDerMonatKommtAusDemDateinamen() {
        when(dokumentAnalyseService.rufKiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"gesamtkosten\": null}");

        ablage.legeAb(quelle);

        BwaUpload bwa = gespeichert();
        assertThat(bwa.getJahr()).isEqualTo(2026);
        assertThat(bwa.getMonat()).isEqualTo(3);
        assertThat(bwa.getGesamtGemeinkosten()).isNull();
        assertThat(bwa.getAnalysiert()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void kaputtesJsonFuehrtZumRueckfallOhneKiDaten() {
        when(dokumentAnalyseService.rufKiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{kaputt");

        ablage.legeAb(quelle);

        BwaUpload bwa = gespeichert();
        assertThat(bwa.getJahr()).isEqualTo(2026);
        assertThat(bwa.getMonat()).isEqualTo(3);
        assertThat(bwa.getAiRawJson()).isNull();
    }

    @Test
    void bereitsImportierteBwaWirdUebersprungen() {
        when(bwaUploadRepository.existsBySourceEmailIdAndOriginalDateiname(42L, "BWA_2026-03.pdf"))
                .thenReturn(true);

        ablage.legeAb(quelle);

        verifyNoInteractions(dokumentAnalyseService);
        verify(bwaUploadRepository, never()).save(any());
    }

    @Test
    void fehlendeDateiLegtNichtsAn() throws Exception {
        Files.delete(mailAttachmentDir.resolve("stored-bwa.pdf"));

        ablage.legeAb(quelle);

        verifyNoInteractions(dokumentAnalyseService);
        verify(bwaUploadRepository, never()).save(any());
    }
}
