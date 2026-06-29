package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Lohnabrechnung
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class LohnabrechnungServiceTest {

    @Mock
    private lateinit var lohnabrechnungRepository: LohnabrechnungRepository

    @InjectMocks
    private lateinit var service: LohnabrechnungService

    @TempDir
    lateinit var lohnabrechnungDir: Path

    @TempDir
    lateinit var mailAttachmentDir: Path

    private lateinit var lohnabrechnung: Lohnabrechnung

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(service, "lohnabrechnungDir", lohnabrechnungDir.toString())
        ReflectionTestUtils.setField(service, "mailAttachmentDir", mailAttachmentDir.toString())

        val mitarbeiter = Mitarbeiter().apply {
            vorname = "Max"
            nachname = "Mustermann"
        }
        lohnabrechnung = Lohnabrechnung().apply {
            this.mitarbeiter = mitarbeiter
            jahr = 2026
            monat = 5
            originalDateiname = "Lohnabrechnungen Mai 2026.pdf"
            gespeicherterDateiname = "abc-123-uuid.pdf"
        }
    }

    @Test
    fun findPdfNutztGespeichertenDateinamenStattOriginalnamen() {
        Files.write(lohnabrechnungDir.resolve("abc-123-uuid.pdf"), byteArrayOf(1, 2, 3))
        `when`(lohnabrechnungRepository.findById(2L)).thenReturn(Optional.of(lohnabrechnung))

        val pdf = findPdf(2L)

        assertThat(pdf).isPresent()
        val pdfDatei = pdf.get()
        assertThat(invokePath(pdfDatei, "pfad").fileName.toString()).isEqualTo("abc-123-uuid.pdf")
        assertThat(invoke(pdfDatei, "anzeigeName")).isEqualTo("Lohnabrechnungen Mai 2026.pdf")
    }

    @Test
    fun findPdfFindetAltdatenImMailAttachmentVerzeichnis() {
        Files.write(mailAttachmentDir.resolve("abc-123-uuid.pdf"), byteArrayOf(1, 2, 3))
        `when`(lohnabrechnungRepository.findById(2L)).thenReturn(Optional.of(lohnabrechnung))

        val pdf = findPdf(2L)

        assertThat(pdf).isPresent()
        assertThat(invokePath(pdf.get(), "pfad"))
            .isEqualTo(mailAttachmentDir.resolve("abc-123-uuid.pdf").toAbsolutePath().normalize())
    }

    @Test
    fun findPdfGibtLeerWennDateiNichtExistiert() {
        `when`(lohnabrechnungRepository.findById(2L)).thenReturn(Optional.of(lohnabrechnung))

        assertThat(findPdf(2L)).isEmpty()
    }

    @Test
    fun findPdfGibtLeerWennLohnabrechnungUnbekannt() {
        `when`(lohnabrechnungRepository.findById(999L)).thenReturn(Optional.empty())

        assertThat(findPdf(999L)).isEmpty()
    }

    @Test
    fun findPdfBlocktPathTraversal() {
        lohnabrechnung.gespeicherterDateiname = "../../etc/passwd"
        `when`(lohnabrechnungRepository.findById(2L)).thenReturn(Optional.of(lohnabrechnung))

        assertThat(findPdf(2L)).isEmpty()
    }

    private fun findPdf(id: Long): Optional<*> =
        service.javaClass.getMethod("findPdf", java.lang.Long.TYPE).invoke(service, id) as Optional<*>

    private fun invoke(target: Any, method: String): Any? =
        target.javaClass.getMethod(method).invoke(target)

    private fun invokePath(target: Any, method: String): Path =
        invoke(target, method) as Path
}
