package org.example.kalkulationsprogramm.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RechnungsuebersichtServiceConfigurationTest {
    @Test
    void liestPdfAusDemKonfiguriertenAusgangsdokumentSpeicher(@TempDir Path uploads) throws Exception {
        Files.write(uploads.resolve("storno.pdf"), new byte[]{1, 2, 3});
        var dokument = new AusgangsGeschaeftsDokument();
        dokument.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        dokument.setGebucht(true); dokument.setPdfDateiname("storno.pdf");
        var repository = mock(AusgangsGeschaeftsDokumentRepository.class);
        when(repository.findById(42L)).thenReturn(Optional.of(dokument));
        new ApplicationContextRunner()
                .withPropertyValues("file.upload-dir=" + uploads, "upload.path=" + uploads.resolve("falsch"))
                .withBean(AusgangsGeschaeftsDokumentRepository.class, () -> repository)
                .withBean(ProjektDokumentRepository.class, () -> mock(ProjektDokumentRepository.class))
                .withUserConfiguration(RechnungsuebersichtService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RechnungsuebersichtService.class).readAusgangsPdf(42L)).containsExactly(1, 2, 3);
                });
    }
}
