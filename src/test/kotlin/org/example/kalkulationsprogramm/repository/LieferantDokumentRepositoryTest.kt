package org.example.kalkulationsprogramm.repository

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDateTime

@DataJpaTest
class LieferantDokumentRepositoryTest {

    @Autowired
    private lateinit var lieferantDokumentRepository: LieferantDokumentRepository

    @Autowired
    private lateinit var lieferantenRepository: LieferantenRepository

    @Autowired
    private lateinit var emailRepository: EmailRepository

    @Autowired
    private lateinit var emailAttachmentRepository: EmailAttachmentRepository

    @Test
    fun findMitXmlAnzeigedateiFindetNurMailImportXmlDokumente() {
        val lieferant = lieferantenRepository.saveAndFlush(
            Lieferanten().apply { lieferantenname = "Test Lieferant GmbH" },
        )

        val xmlDoc = neuesDokument(lieferant, "Rechnung2026-0814.xml")
        val pdfDoc = neuesDokument(lieferant, "ReNr. 2026-0814.pdf")
        val ohneDatei = neuesDokument(lieferant, null)

        lieferantDokumentRepository.saveAll(listOf(xmlDoc, pdfDoc, ohneDatei))
        lieferantDokumentRepository.flush()

        val email = emailRepository.saveAndFlush(
            Email().apply {
                messageId = "msg-1@example.com"
                direction = EmailDirection.IN
            },
        )

        val att = emailAttachmentRepository.saveAndFlush(
            EmailAttachment().apply {
                this.email = email
                originalFilename = "beleg.xml"
                storedFilename = "uuid_beleg.xml"
            },
        )

        val mitAttachment = neuesDokument(lieferant, "mit_attachment.xml").apply {
            attachment = att
        }
        lieferantDokumentRepository.saveAndFlush(mitAttachment)

        val gefunden = lieferantDokumentRepository.findMitXmlAnzeigedatei()

        assertThat(gefunden)
            .extracting<String> { it.gespeicherterDateiname }
            .containsExactly("Rechnung2026-0814.xml")
    }

    private fun neuesDokument(lieferant: Lieferanten, gespeicherterDateiname: String?): LieferantDokument =
        LieferantDokument().apply {
            this.lieferant = lieferant
            typ = LieferantDokumentTyp.RECHNUNG
            this.gespeicherterDateiname = gespeicherterDateiname
            uploadDatum = LocalDateTime.now()
        }
}
