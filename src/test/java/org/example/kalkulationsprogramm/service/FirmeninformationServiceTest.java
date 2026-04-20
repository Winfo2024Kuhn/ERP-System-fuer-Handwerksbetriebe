package org.example.kalkulationsprogramm.service;

import com.lowagie.text.Image;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests fuer Logo-Upload-Logik: MIME-Whitelist, Groessen-Limit,
 * Pfad-Traversal-Schutz, Loeschen, Laden.
 */
class FirmeninformationServiceTest {

    @TempDir
    Path tmp;

    private FirmeninformationRepository repository;
    private FirmeninformationService service;
    private Firmeninformation firma;

    // winziges gueltiges PNG (1x1, transparent) als Test-Payload
    private static final byte[] MINI_PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    @BeforeEach
    void setUp() {
        repository = mock(FirmeninformationRepository.class);
        firma = new Firmeninformation();
        firma.setId(1L);
        firma.setFirmenname("Musterbetrieb");
        when(repository.getOrCreate()).thenReturn(firma);
        when(repository.findFirmeninformation()).thenReturn(Optional.of(firma));
        when(repository.save(any(Firmeninformation.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new FirmeninformationService(repository);
        ReflectionTestUtils.setField(service, "logoUploadDir", tmp.toString());
    }

    @Test
    void speichereLogoDatei_speichertAlsLogoPng() throws IOException {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "whatever.png", MediaType.IMAGE_PNG_VALUE, MINI_PNG);

        FirmeninformationDto dto = service.speichereLogoDatei(datei);

        assertEquals("logo.png", dto.getLogoDateiname());
        assertTrue(Files.isRegularFile(tmp.resolve("logo.png")));
        assertArrayEquals(MINI_PNG, Files.readAllBytes(tmp.resolve("logo.png")));
    }

    @Test
    void speichereLogoDatei_ignoriertClientDateinameMitPfadTraversal() throws IOException {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "../../etc/passwd.png", MediaType.IMAGE_PNG_VALUE, MINI_PNG);

        service.speichereLogoDatei(datei);

        // Landet unter tmp/logo.png, NICHT ausserhalb
        assertTrue(Files.isRegularFile(tmp.resolve("logo.png")));
        // Und definitiv nicht unter passwd.png oder ausserhalb
        assertFalse(Files.exists(tmp.resolve("passwd.png")));
    }

    @Test
    void speichereLogoDatei_lehntUngueltigesMimeAb() {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "payload.sh", "application/x-sh", new byte[] { 1, 2, 3 });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.speichereLogoDatei(datei));
        assertTrue(ex.getMessage().contains("Ungültiger Dateityp"));
    }

    @Test
    void speichereLogoDatei_lehntZuGrosseDateienAb() {
        byte[] zuGross = new byte[(int) (FirmeninformationService.MAX_LOGO_BYTES + 1)];
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "gross.png", MediaType.IMAGE_PNG_VALUE, zuGross);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.speichereLogoDatei(datei));
        assertTrue(ex.getMessage().toLowerCase().contains("groß"));
    }

    @Test
    void speichereLogoDatei_loeschtVorherigesMitAndererEndung() throws IOException {
        Files.writeString(tmp.resolve("logo.jpg"), "alt");
        firma.setLogoDateiname("logo.jpg");

        MockMultipartFile datei = new MockMultipartFile(
                "datei", "neu.png", MediaType.IMAGE_PNG_VALUE, MINI_PNG);
        service.speichereLogoDatei(datei);

        assertTrue(Files.isRegularFile(tmp.resolve("logo.png")));
        assertFalse(Files.exists(tmp.resolve("logo.jpg")));
    }

    @Test
    void loescheLogoDatei_entferntDateiUndFeld() throws IOException {
        Files.writeString(tmp.resolve("logo.png"), "alt");
        firma.setLogoDateiname("logo.png");

        FirmeninformationDto dto = service.loescheLogoDatei();

        assertNull(dto.getLogoDateiname());
        assertFalse(Files.exists(tmp.resolve("logo.png")));
    }

    @Test
    void loescheLogoDatei_ohneVorhandenesLogo_istIdempotent() {
        firma.setLogoDateiname(null);
        assertDoesNotThrow(() -> service.loescheLogoDatei());
    }

    @Test
    void loadLogoImage_liefertNullWennKeinLogo() {
        firma.setLogoDateiname(null);
        assertNull(service.loadLogoImage());
    }

    @Test
    void loadLogoImage_liefertImageWennLogoVorhanden() throws IOException {
        Files.write(tmp.resolve("logo.png"), MINI_PNG);
        firma.setLogoDateiname("logo.png");

        Image image = service.loadLogoImage();

        assertNotNull(image);
    }

    @Test
    void loadLogoBytes_liefertNullWennDateiNichtExistiert() {
        firma.setLogoDateiname("logo.png");
        // Datei existiert auf Platte nicht
        assertNull(service.loadLogoBytes());
    }

    @Test
    void ermittleLogoContentType_gibtMimeZurueck() throws IOException {
        Files.write(tmp.resolve("logo.png"), MINI_PNG);
        firma.setLogoDateiname("logo.png");

        assertEquals(MediaType.IMAGE_PNG_VALUE, service.ermittleLogoContentType());

        firma.setLogoDateiname("logo.jpg");
        assertEquals(MediaType.IMAGE_JPEG_VALUE, service.ermittleLogoContentType());

        firma.setLogoDateiname("logo.webp");
        assertEquals("image/webp", service.ermittleLogoContentType());
    }
}
