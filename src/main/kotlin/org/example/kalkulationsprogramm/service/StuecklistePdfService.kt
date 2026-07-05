package org.example.kalkulationsprogramm.service

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPCellEvent
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfWriter
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

@Service
class StuecklistePdfService(
    private val projektRepository: ProjektRepository,
    private val artikelInProjektRepository: ArtikelInProjektRepository,
    private val schnittbilderRepository: SchnittbilderRepository,
    private val dateiSpeicherService: DateiSpeicherService,
) {
    private val schnittbildIconCache: MutableMap<String, ByteArray> = ConcurrentHashMap()

    fun generateForProjekt(projektId: Long): ByteArray {
        val projekt = projektRepository.findById(projektId)
            .orElseThrow { RuntimeException("Projekt konnte nicht gefunden werden.") }
        val items = artikelInProjektRepository.findByProjekt_Id(projektId)

        val baos = ByteArrayOutputStream()
        try {
            val doc = Document(PageSize.A4.rotate())
            val writer = PdfWriter.getInstance(doc, baos)
            writer.compressionLevel = 0
            doc.open()

            try {
                val logo = Image.getInstance(StuecklistePdfService::class.java.getResource("/static/firmenlogo.png"))
                logo.scaleToFit(150f, 70f)
                doc.add(logo)
            } catch (_: Exception) {
            }

            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, Color(204, 0, 0))
            val subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)
            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE)
            val cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)
            val headerBg = Color(204, 0, 0)
            val altBg = Color(245, 245, 245)

            doc.add(Paragraph("Stueckliste", titleFont).apply { spacingAfter = 10f })
            doc.add(
                Paragraph(
                    "Bauvorhaben: ${nvl(projekt.bauvorhaben)}\n" +
                        "Auftragsnummer: ${nvl(projekt.auftragsnummer)}\n" +
                        "Kunde: ${nvl(projekt.getKunde()?.toString())}",
                    FontFactory.getFont(FontFactory.HELVETICA, 10f),
                ).apply { spacingAfter = 12f },
            )

            val grouped = items
                .filter { it.artikel != null }
                .groupBy { rootKategorieId(requireNotNull(it.artikel)) }
                .mapValues { (_, value) -> value.groupBy { subKategorieName(requireNotNull(it.artikel)) } }

            for (root in listOf(1, 2, 3)) {
                val bySub = grouped[root]
                if (bySub.isNullOrEmpty()) continue

                val rootName = rootKategorieName(bySub.values.flatten().firstOrNull())
                doc.add(Paragraph(nvl(rootName), subTitleFont).apply {
                    spacingBefore = 8f
                    spacingAfter = 6f
                })

                for (sub in bySub.keys.sortedWith(compareBy<String?> { it == null }.thenBy(String.CASE_INSENSITIVE_ORDER) { it ?: "" })) {
                    val list = bySub[sub]
                    if (list.isNullOrEmpty()) continue
                    if (!sub.isNullOrBlank()) {
                        doc.add(Paragraph(sub, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10f)).apply {
                            spacingBefore = 4f
                            spacingAfter = 4f
                        })
                    }

                    val werkstoff = root == 1
                    val table = if (werkstoff) {
                        buildWerkstoffTable(list, headerFont, cellFont, headerBg, altBg)
                    } else {
                        buildStandardTable(list, headerFont, cellFont, headerBg, altBg)
                    }
                    doc.add(table)
                    doc.add(Paragraph(" ", cellFont))
                }
            }

            doc.close()
        } catch (ex: Exception) {
            throw RuntimeException("PDF generation failed", ex)
        }

        try {
            val refIs = StuecklistePdfService::class.java.getResourceAsStream("/static/Schnittbilder_Formstahl_Kuhn Copy.pdf")
            if (refIs != null) {
                val mainReader = PdfReader(baos.toByteArray())
                val refReader = PdfReader(refIs)
                val mergedOut = ByteArrayOutputStream()
                val merged = Document()
                val copy = PdfCopy(merged, mergedOut)
                merged.open()
                for (i in 1..mainReader.numberOfPages) {
                    copy.addPage(copy.getImportedPage(mainReader, i))
                }
                for (i in 1..refReader.numberOfPages) {
                    copy.addPage(copy.getImportedPage(refReader, i))
                }
                merged.close()
                mainReader.close()
                refReader.close()
                return mergedOut.toByteArray()
            }
        } catch (_: Exception) {
        }
        return baos.toByteArray()
    }

    private fun buildWerkstoffTable(
        list: List<ArtikelInProjekt>,
        headerFont: Font,
        cellFont: Font,
        headerBg: Color,
        altBg: Color,
    ): PdfPTable {
        val table = PdfPTable(floatArrayOf(2f, 3f, 2f, 1f, 1.5f, 1.5f, 1.2f, 1.2f, 1.2f, 2f, 1.3f, 1.3f, 1.8f))
        table.widthPercentage = 100f
        addHeaders(table, arrayOf("Art.-Nr.", "Prod.", "Werkst.", "Stk", "L/Stk [mm]", "Ges. (m)", "Form", "WL", "WR", "Komm.", "Vorh.", "Best.", "R. gel."), headerFont, headerBg)

        var alternate = false
        var totalM = BigDecimal.ZERO
        var totalKg = BigDecimal.ZERO
        var totalMantel = BigDecimal.ZERO
        for (aip in list) {
            val bg = if (alternate) altBg else Color.WHITE
            val artikel = requireNotNull(aip.artikel)
            table.addCell(makeCell(nvl(artikel.getExterneArtikelnummer()), cellFont, bg))
            table.addCell(makeCell(nvl(artikel.produktname), cellFont, bg))
            table.addCell(makeCell(artikel.werkstoff?.name ?: "", cellFont, bg))
            table.addCell(makeCell(aip.stueckzahl?.toString() ?: "", cellFont, bg))
            table.addCell(makeCell(calcMmPerStueck(aip), cellFont, bg))
            table.addCell(makeCell(aip.meter?.let { stripZeros(it) } ?: "", cellFont, bg))
            table.addCell(makeCutCell(aip.schnittForm, cellFont, bg))
            table.addCell(makeCell(nvl(aip.anschnittWinkelLinks), cellFont, bg))
            table.addCell(makeCell(nvl(aip.anschnittWinkelRechts), cellFont, bg))
            table.addCell(makeCell(nvl(aip.kommentar), cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            alternate = !alternate

            aip.meter?.let { totalM = totalM.add(it) }
            val meter = aip.meter
            val kilogramm = aip.kilogramm
            if (kilogramm != null) {
                totalKg = totalKg.add(kilogramm)
            } else if (artikel is ArtikelWerkstoffe && artikel.masse != null && meter != null) {
                totalKg = totalKg.add(requireNotNull(artikel.masse).multiply(meter))
            }
            if (artikel is ArtikelWerkstoffe && artikel.mantelflaeche != null && meter != null) {
                totalMantel = totalMantel.add(requireNotNull(artikel.mantelflaeche).multiply(meter))
            }
            addAnzugsmomentRowIfNeeded(table, artikel, cellFont)
        }

        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f)
        val sumComment = "Sigma kg: ${if (totalKg.compareTo(BigDecimal.ZERO) == 0) "0" else stripZeros(totalKg)}" +
            " | Sigma Mant.: ${if (totalMantel.compareTo(BigDecimal.ZERO) == 0) "0" else stripZeros(totalMantel)} m2"
        listOf("", "Summe", "", "", "", stripZeros(totalM), sumComment, "", "", "").forEachIndexed { index, text ->
            table.addCell(makeCell(text, if (index == 1 || index == 5 || index == 6) bold else cellFont, altBg))
        }
        return table
    }

    private fun buildStandardTable(
        list: List<ArtikelInProjekt>,
        headerFont: Font,
        cellFont: Font,
        headerBg: Color,
        altBg: Color,
    ): PdfPTable {
        val table = PdfPTable(floatArrayOf(2f, 4f, 2f, 1.5f, 1.2f, 1.2f, 1.2f, 2f, 1.3f, 1.3f, 1.8f))
        table.widthPercentage = 100f
        addHeaders(table, arrayOf("Art.-Nr.", "Prod.", "Kat.", "Menge", "Form", "WL", "WR", "Komm.", "Vorh.", "Best.", "R. gel."), headerFont, headerBg)

        var alternate = false
        for (aip in list) {
            val bg = if (alternate) altBg else Color.WHITE
            val artikel = requireNotNull(aip.artikel)
            table.addCell(makeCell(nvl(artikel.getExterneArtikelnummer()), cellFont, bg))
            table.addCell(makeCell(nvl(artikel.produktname), cellFont, bg))
            table.addCell(makeCell(artikel.kategorie?.beschreibung ?: "", cellFont, bg))
            table.addCell(makeCell(formatMengeAllgemein(aip), cellFont, bg))
            table.addCell(makeCutCell(aip.schnittForm, cellFont, bg))
            table.addCell(makeCell(nvl(aip.anschnittWinkelLinks), cellFont, bg))
            table.addCell(makeCell(nvl(aip.anschnittWinkelRechts), cellFont, bg))
            table.addCell(makeCell(nvl(aip.kommentar), cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            table.addCell(makeCheckboxCell(cellFont, bg))
            alternate = !alternate
            addAnzugsmomentRowIfNeeded(table, artikel, cellFont)
        }
        return table
    }

    private fun addHeaders(table: PdfPTable, headers: Array<String>, headerFont: Font, headerBg: Color) {
        headers.forEachIndexed { index, header ->
            table.addCell(PdfPCell(Phrase(header, headerFont)).apply {
                backgroundColor = headerBg
                if (index >= headers.size - 3) {
                    setNoWrap(true)
                    paddingLeft = 2f
                    paddingRight = 2f
                }
            })
        }
    }

    private fun addAnzugsmomentRowIfNeeded(table: PdfPTable, artikel: Artikel, cellFont: Font) {
        if (!isKategorie15(artikel)) return
        val info = PdfPTable(floatArrayOf(2.8f, 4.2f))
        info.widthPercentage = 100f
        val chk = makeCheckboxLabelCell("Anzugsmoment geprueft", cellFont, Color.WHITE)
        val sig = PdfPCell(Phrase("Unterschrift/Datum:", cellFont)).apply {
            borderWidthTop = 0f
            borderWidthLeft = 0f
            borderWidthRight = 0f
            borderWidthBottom = 0.8f
            paddingTop = 6f
            paddingBottom = 2f
        }
        info.addCell(chk)
        info.addCell(sig)

        table.addCell(PdfPCell(info).apply {
            colspan = table.numberOfColumns
            backgroundColor = Color.WHITE
        })
    }

    private fun nvl(value: String?): String = value ?: ""

    private fun stripZeros(value: BigDecimal?): String =
        value?.stripTrailingZeros()?.toPlainString() ?: ""

    private fun makeCutCell(form: String?, font: Font, bg: Color): PdfPCell {
        if (form.isNullOrBlank()) return makeCell("", font, bg)
        val imageBytes = loadSchnittbildIcon(form) ?: return makeCell("Form $form", font, bg)
        return try {
            val icon = Image.getInstance(imageBytes)
            icon.scaleToFit(26f, 26f)
            icon.alignment = Element.ALIGN_CENTER
            PdfPCell().apply {
                backgroundColor = bg
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                setPadding(2f)
                addElement(icon)
                addElement(Paragraph("Form $form", FontFactory.getFont(FontFactory.HELVETICA, 6f)).apply {
                    alignment = Element.ALIGN_CENTER
                })
            }
        } catch (_: Exception) {
            makeCell("Form $form", font, bg)
        }
    }

    private fun loadSchnittbildIcon(form: String): ByteArray? {
        val cached = schnittbildIconCache[form]
        if (cached != null) return if (cached.contentEquals(NO_IMAGE)) null else cached
        val loaded = fetchSchnittbildIcon(form)
        schnittbildIconCache[form] = loaded ?: NO_IMAGE
        return loaded
    }

    private fun fetchSchnittbildIcon(form: String): ByteArray? =
        try {
            val entity = schnittbilderRepository.findByForm(form) ?: return null
            val dateiname = extractFilename(entity.bildUrlSchnittbild) ?: return null
            dateiSpeicherService.ladeBildAlsResource(dateiname).inputStream.use { it.readAllBytes() }
        } catch (_: Exception) {
            null
        }

    private fun extractFilename(bildUrl: String?): String? {
        if (bildUrl.isNullOrBlank()) return null
        var cleaned = bildUrl
        val query = cleaned.indexOf('?')
        if (query >= 0) cleaned = cleaned.substring(0, query)
        val idx = cleaned.lastIndexOf('/')
        val name = if (idx >= 0) cleaned.substring(idx + 1) else cleaned
        return name.ifBlank { null }
    }

    private fun makeCell(text: String?, font: Font, bg: Color): PdfPCell =
        PdfPCell(Phrase(text ?: "", font)).apply { backgroundColor = bg }

    private fun makeCheckboxCell(font: Font, bg: Color): PdfPCell =
        PdfPCell(Phrase("", font)).apply {
            backgroundColor = bg
            fixedHeight = 14f
            horizontalAlignment = Element.ALIGN_CENTER
            verticalAlignment = Element.ALIGN_MIDDLE
            cellEvent = CheckboxCellEvent()
        }

    private fun makeCheckboxLabelCell(label: String, font: Font, bg: Color): PdfPCell =
        PdfPCell(Phrase(label, font)).apply {
            backgroundColor = bg
            fixedHeight = 16f
            paddingLeft = 14f
            verticalAlignment = Element.ALIGN_MIDDLE
            cellEvent = LeftCheckboxCellEvent()
        }

    private class CheckboxCellEvent : PdfPCellEvent {
        override fun cellLayout(cell: PdfPCell, rect: Rectangle, canvas: Array<PdfContentByte>) {
            val cb = canvas[PdfPTable.LINECANVAS]
            val size = minOf(rect.height, rect.width) * 0.6f
            val x = rect.left + (rect.width - size) / 2f
            val y = rect.bottom + (rect.height - size) / 2f
            cb.rectangle(x, y, size, size)
            cb.stroke()
        }
    }

    private class LeftCheckboxCellEvent : PdfPCellEvent {
        override fun cellLayout(cell: PdfPCell, rect: Rectangle, canvas: Array<PdfContentByte>) {
            val cb = canvas[PdfPTable.LINECANVAS]
            val size = minOf(rect.height, 10f) * 0.6f
            val x = rect.left + 2f
            val y = rect.bottom + (rect.height - size) / 2f
            cb.rectangle(x, y, size, size)
            cb.stroke()
        }
    }

    private fun isKategorie15(artikel: Artikel?): Boolean {
        var k: Kategorie? = artikel?.kategorie
        while (k != null) {
            if (k.id != null && k.id == 15) return true
            k = k.parentKategorie
        }
        return false
    }

    private fun rootKategorieId(artikel: Artikel): Int? {
        var k: Kategorie = artikel.kategorie ?: return null
        while (k.parentKategorie != null) {
            k = requireNotNull(k.parentKategorie)
        }
        return k.id
    }

    private fun rootKategorieName(sample: ArtikelInProjekt?): String {
        var k: Kategorie = sample?.artikel?.kategorie ?: return ""
        while (k.parentKategorie != null) {
            k = requireNotNull(k.parentKategorie)
        }
        return k.beschreibung ?: ""
    }

    private fun subKategorieName(artikel: Artikel): String? {
        val k = artikel.kategorie ?: return null
        if (k.parentKategorie == null) return null
        return k.beschreibung
    }

    private fun calcMmPerStueck(aip: ArtikelInProjekt?): String {
        try {
            if (aip == null) return ""
            val stueckzahl = aip.stueckzahl
            val meter = aip.meter
            if (stueckzahl != null && stueckzahl > 0 && meter != null && meter.compareTo(BigDecimal.ZERO) > 0) {
                val st = BigDecimal.valueOf(stueckzahl.toLong())
                val perPieceM = meter.divide(st, 6, RoundingMode.HALF_UP)
                val perPieceMm = perPieceM.multiply(BigDecimal("1000"))
                return perPieceMm.setScale(0, RoundingMode.HALF_UP).toPlainString()
            }
        } catch (_: Exception) {
        }
        return ""
    }

    private fun formatMengeAllgemein(aip: ArtikelInProjekt?): String {
        val artikel = aip?.artikel ?: return ""
        val ve = artikel.verrechnungseinheit ?: return ""
        return when (ve) {
            Verrechnungseinheit.KILOGRAMM -> aip.kilogramm?.let { "${stripZeros(it)} kg" } ?: ""
            Verrechnungseinheit.LAUFENDE_METER, Verrechnungseinheit.QUADRATMETER -> aip.meter?.let { "${stripZeros(it)} m" } ?: ""
            Verrechnungseinheit.STUECK -> aip.stueckzahl?.let { "$it Stk" } ?: ""
        }
    }

    companion object {
        private val NO_IMAGE = ByteArray(0)
    }
}
