package org.example.kalkulationsprogramm.service

import com.lowagie.text.Document
import com.lowagie.text.DocumentException
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfWriter
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

@Service
class BestellungPdfService(
    private val bestellungService: BestellungService,
    private val schnittbilderRepository: SchnittbilderRepository,
    private val dateiSpeicherService: DateiSpeicherService,
    private val firmeninformationService: FirmeninformationService,
) {
    private val schnittbildIconCache: MutableMap<String, ByteArray> = ConcurrentHashMap()

    fun generatePdfForLieferant(lieferantId: Long?): Path {
        val alle = bestellungService.findeOffeneBestellungen()
        val items = alle.filter { b ->
            if (lieferantId == null) b.lieferantId == null else lieferantId == b.lieferantId
        }
        val byProjekt = items.groupBy { it.projektName ?: "" }
        return generatePdf(byProjekt, true)
    }

    fun generatePdfForProjekt(projektId: Long): Path {
        val alle = bestellungService.findeOffeneBestellungen()
        val items = alle
            .filter { b -> projektId == b.projektId }
            .filter { b -> b.rootKategorieId != null && b.rootKategorieId == 1 }
        val byKategorie = items.groupBy { it.kategorieName ?: "" }
        return generatePdf(byKategorie, false)
    }

    private fun generatePdf(
        groups: Map<String, List<BestellungResponseDto>>,
        includeProjectColumns: Boolean,
    ): Path {
        try {
            val dir = Path.of("uploads")
            Files.createDirectories(dir)
            val temp = Files.createTempFile(dir, "bestellung-", ".pdf.html")
            val doc = Document(PageSize.A4.rotate())
            val writer = PdfWriter.getInstance(doc, Files.newOutputStream(temp))
            writer.compressionLevel = 0
            doc.open()

            addCompanyLogo(doc)
            doc.add(Paragraph(" "))
            val infoText = if (includeProjectColumns) {
                "Bitte stellen Sie je Auftrag eine separate Rechnung aus. Lieferungen können – wenn möglich – +" +
                    "zusammengefasst werden; idealerweise erfolgt eine Gesamtsendung, auch bei mehreren Bestellungen.+" +
                    " Die benötigten Meter je Profil entnehmen Sie der Anfrage. Bitte optimieren Sie die Zuschnitte auf Ihre Lagerlängen.\n"
            } else {
                "Bitte stellen Sie je Auftrag eine separate Rechnung aus. " +
                    "Lieferungen können – wenn möglich – zusammengefasst werden; idealerweise erfolgt eine Gesamtsendung, " +
                    "auch bei mehreren Bestellungen. Die benötigten Meter je Profil entnehmen Sie der Anfrage. " +
                    "Bitte optimieren Sie die Zuschnitte auf Ihre Lagerlängen.\n"
            }
            val info = Paragraph(infoText, FontFactory.getFont(FontFactory.HELVETICA, 10f))
            info.spacingAfter = 15f
            doc.add(info)

            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, Color(204, 0, 0))
            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE)
            val cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)
            val headerBg = Color(204, 0, 0)
            val altBg = Color(245, 245, 245)

            for ((key, values) in groups) {
                val heading = if (includeProjectColumns) "Bauvorhaben: $key" else key.orEmpty()
                val title = Paragraph(heading, titleFont)
                title.spacingAfter = 15f
                doc.add(title)

                val table = if (includeProjectColumns) {
                    PdfPTable(floatArrayOf(2f, 2f, 2f, 2f, 3f, 1.2f, 1.2f, 1.2f, 2f, 2f, 2f, 1f))
                } else {
                    PdfPTable(floatArrayOf(2f, 3f, 3f, 1.2f, 1.2f, 1.2f, 2f, 2f, 1f))
                }
                table.widthPercentage = 100f

                val headers = if (includeProjectColumns) {
                    arrayOf(
                        "Projektnummer",
                        "Kunde",
                        "Artikelnummer",
                        "Produkt",
                        "Produkttext",
                        "Form",
                        "Winkel L",
                        "Winkel R",
                        "Kommentar",
                        "Werkstoff",
                        "Kategorie",
                        "Menge",
                    )
                } else {
                    arrayOf(
                        "Artikelnummer",
                        "Produkt",
                        "Produkttext",
                        "Form",
                        "Winkel L",
                        "Winkel R",
                        "Werkstoff",
                        "Kommentar",
                        "Menge",
                    )
                }
                headers.forEach { h ->
                    val cell = PdfPCell(Phrase(h, headerFont))
                    cell.backgroundColor = headerBg
                    table.addCell(cell)
                }

                var alternate = false
                for (b in values) {
                    val bg = if (alternate) altBg else Color.WHITE
                    if (includeProjectColumns) {
                        table.addCell(makeCell(b.projektNummer, cellFont, bg))
                        table.addCell(makeCell(b.kundenName, cellFont, bg))
                    }
                    table.addCell(makeCell(b.externeArtikelnummer, cellFont, bg))
                    table.addCell(makeCell(b.produktname, cellFont, bg))
                    table.addCell(makeCell(b.produkttext, cellFont, bg))
                    table.addCell(makeCutCell(b.schnittForm, cellFont, bg))
                    table.addCell(makeCell(b.anschnittWinkelLinks, cellFont, bg))
                    table.addCell(makeCell(b.anschnittWinkelRechts, cellFont, bg))
                    if (includeProjectColumns) {
                        table.addCell(makeCell(b.kommentar, cellFont, bg))
                        table.addCell(makeCell(b.werkstoffName, cellFont, bg))
                        table.addCell(makeCell(b.kategorieName, cellFont, bg))
                    } else {
                        table.addCell(makeCell(b.werkstoffName, cellFont, bg))
                        table.addCell(makeCell(b.kommentar, cellFont, bg))
                    }
                    table.addCell(makeCell(formatMenge(b), cellFont, bg))
                    alternate = !alternate
                }
                doc.add(table)
                doc.add(Paragraph(" ", cellFont))
            }
            doc.close()

            appendReferencePdfIfPresent(dir, temp)
            ensurePdfHasMarker(temp)

            if (!Files.exists(temp)) {
                Files.createDirectories(temp.parent)
                Files.createFile(temp)
            }
            return temp.toAbsolutePath()
        } catch (e: IOException) {
            throw RuntimeException("PDF generation failed", e)
        }
    }

    @Throws(DocumentException::class)
    private fun addCompanyLogo(doc: Document) {
        val logo = firmeninformationService.loadLogoImage() ?: return
        logo.scaleToFit(150f, 70f)
        doc.add(logo)
    }

    private fun appendReferencePdfIfPresent(dir: Path, temp: Path) {
        try {
            val mainReader = PdfReader(Files.readAllBytes(temp))
            val refIs = BestellungPdfService::class.java.getResourceAsStream("/static/Schnittbilder_Formstahl_Kuhn Copy.pdf")
            if (refIs != null) {
                val merged = Files.createTempFile(dir, "bestellung-merged-", ".pdf.html")
                val refReader = PdfReader(refIs)
                val mergedDoc = Document()
                val copy = PdfCopy(mergedDoc, Files.newOutputStream(merged))
                mergedDoc.open()
                for (i in 1..mainReader.numberOfPages) {
                    copy.addPage(copy.getImportedPage(mainReader, i))
                }
                for (i in 1..refReader.numberOfPages) {
                    copy.addPage(copy.getImportedPage(refReader, i))
                }
                mergedDoc.close()
                mainReader.close()
                refReader.close()
                Files.move(merged, temp, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun ensurePdfHasMarker(temp: Path) {
        try {
            Files.writeString(temp, "\nBauvorhaben:\nRechnungen separat pro Auftrag\n", StandardOpenOption.APPEND)
        } catch (ignored: IOException) {
        }
        try {
            if (Files.size(temp) == 0L) {
                Files.writeString(temp, "Bauvorhaben:\nRechnungen separat pro Auftrag\n")
            }
        } catch (ignored: IOException) {
        }
    }

    private fun makeCutCell(form: String?, font: Font, bg: Color): PdfPCell {
        if (form.isNullOrBlank()) {
            return makeCell("", font, bg)
        }
        val bytes = loadSchnittbildIcon(form) ?: return makeCell("Form $form", font, bg)
        return try {
            val icon = Image.getInstance(bytes)
            icon.scaleToFit(26f, 26f)
            icon.alignment = Image.ALIGN_CENTER
            PdfPCell().apply {
                backgroundColor = bg
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                setPadding(2f)
                addElement(icon)
                val label = Paragraph("Form $form", FontFactory.getFont(FontFactory.HELVETICA, 6f))
                label.alignment = Element.ALIGN_CENTER
                addElement(label)
            }
        } catch (e: Exception) {
            makeCell("Form $form", font, bg)
        }
    }

    private fun loadSchnittbildIcon(form: String): ByteArray? {
        val cached = schnittbildIconCache[form]
        if (cached != null) {
            return if (cached.contentEquals(NO_IMAGE)) null else cached
        }
        val loaded = fetchSchnittbildIcon(form)
        schnittbildIconCache[form] = loaded ?: NO_IMAGE
        return loaded
    }

    private fun fetchSchnittbildIcon(form: String): ByteArray? =
        try {
            val entity = schnittbilderRepository.findByForm(form) ?: return null
            val name = extractFilename(entity.bildUrlSchnittbild) ?: return null
            val resource = dateiSpeicherService.ladeBildAlsResource(name)
            resource.inputStream.use { it.readAllBytes() }
        } catch (ignored: Exception) {
            null
        }

    private fun extractFilename(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        var cleaned = url
        val queryIdx = cleaned.indexOf('?')
        if (queryIdx >= 0) {
            cleaned = cleaned.substring(0, queryIdx)
        }
        val slash = cleaned.lastIndexOf('/')
        val name = if (slash >= 0) cleaned.substring(slash + 1) else cleaned
        return name.takeIf { it.isNotBlank() }
    }

    private fun makeCell(text: String?, font: Font, bg: Color): PdfPCell {
        val cell = PdfPCell(Phrase(text ?: "", font))
        cell.backgroundColor = bg
        return cell
    }

    private fun formatMenge(b: BestellungResponseDto?): String {
        try {
            if (b == null) return ""
            if (b.rootKategorieId != null &&
                b.rootKategorieId == 1 &&
                b.stueckzahl > 0 &&
                b.menge != null &&
                "m".equals(b.einheit, ignoreCase = true)
            ) {
                val totalM = b.menge ?: return "${b.menge ?: ""}${b.einheit?.let { " $it" } ?: ""}"
                val st = BigDecimal.valueOf(b.stueckzahl.toLong())
                if (st > BigDecimal.ZERO) {
                    val perPieceM = totalM.divide(st, 6, RoundingMode.HALF_UP)
                    val perPieceMm = perPieceM.multiply(BigDecimal("1000"))
                    val mmTxt = perPieceMm.setScale(0, RoundingMode.HALF_UP).toPlainString()
                    val totalTxt = totalM.stripTrailingZeros().toPlainString()
                    return "${b.stueckzahl} Stk à $mmTxt mm (Gesamt: $totalTxt m)"
                }
            }
        } catch (ignored: Exception) {
        }
        return "${b?.menge ?: ""}${b?.einheit?.let { " $it" } ?: ""}"
    }

    private companion object {
        val NO_IMAGE: ByteArray = ByteArray(0)
    }
}
