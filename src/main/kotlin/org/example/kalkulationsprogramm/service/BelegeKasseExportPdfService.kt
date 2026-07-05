package org.example.kalkulationsprogramm.service

import com.lowagie.text.BadElementException
import com.lowagie.text.Document
import com.lowagie.text.DocumentException
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.Firmeninformation
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class BelegeKasseExportPdfService(
    private val belegRepository: BelegRepository,
    private val firmeninformationRepository: FirmeninformationRepository,
) {
    @Value("\${upload.path:uploads}")
    private lateinit var uploadPath: String

    fun generatePdf(jahr: Int, monat: Int): Path {
        val ym = YearMonth.of(jahr, monat)
        val von = ym.atDay(1)
        val bis = ym.atEndOfMonth()

        val alle = belegRepository.findByStatusOrderByUploadDatumDesc(BelegStatus.VALIDIERT)
        val imMonat = alle
            .filter {
                val belegDatum = it.belegDatum
                belegDatum != null && !belegDatum.isBefore(von) && !belegDatum.isAfter(bis)
            }
            .sortedWith(compareBy<Beleg> { it.belegDatum }.thenBy { it.id })

        try {
            val dir = Paths.get(uploadPath)
            Files.createDirectories(dir)
            val temp = Files.createTempFile(dir, "belege-export-", ".pdf")
            val doc = Document(PageSize.A4.rotate(), 36f, 36f, 36f, 36f)
            PdfWriter.getInstance(doc, Files.newOutputStream(temp))
            doc.open()

            val firma = firmeninformationRepository.findFirmeninformation().orElse(null)
            addBriefkopf(doc, firma)
            addTitle(doc, ym)
            val anfangssaldo = berechneAnfangssaldo(alle, von)
            addKassenbuchTKonto(doc, imMonat, anfangssaldo)
            addFooter(doc)
            doc.close()
            return temp
        } catch (e: Exception) {
            throw RuntimeException("Fehler beim Erzeugen des Belege-Monatsexports", e)
        }
    }

    @Throws(DocumentException::class)
    private fun addBriefkopf(doc: Document, firma: Firmeninformation?) {
        val logo = ladeFirmenlogo(firma)
        if (logo == null && firma == null) return
        val kopf = PdfPTable(floatArrayOf(2f, 5f))
        kopf.widthPercentage = 100f
        kopf.setSpacingAfter(8f)

        val logoCell = PdfPCell()
        logoCell.border = Rectangle.NO_BORDER
        logoCell.verticalAlignment = Element.ALIGN_MIDDLE
        if (logo != null) {
            logo.scaleToFit(140f, 70f)
            logoCell.addElement(logo)
        }
        kopf.addCell(logoCell)

        val infoCell = PdfPCell()
        infoCell.border = Rectangle.NO_BORDER
        infoCell.horizontalAlignment = Element.ALIGN_RIGHT
        infoCell.verticalAlignment = Element.ALIGN_MIDDLE

        if (firma != null) {
            val firmenname = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, TEXT_DARK)
            val line = FontFactory.getFont(FontFactory.HELVETICA, 9f, TEXT_CELL)
            val lineMuted = FontFactory.getFont(FontFactory.HELVETICA, 8f, TEXT_MUTED)
            addRightLine(infoCell, firma.firmenname, firmenname)
            addRightLine(infoCell, joinNonEmpty(" ", firma.strasse), line)
            addRightLine(infoCell, joinNonEmpty(" ", firma.plz, firma.ort), line)
            val kontakt = joinNonEmpty(" · ", prefix("Tel. ", firma.telefon), prefix("", firma.email), prefix("", firma.website))
            addRightLine(infoCell, kontakt, lineMuted)
            val steuer = joinNonEmpty(" · ", prefix("St.-Nr. ", firma.steuernummer), prefix("USt-IdNr. ", firma.ustIdNr))
            addRightLine(infoCell, steuer, lineMuted)
        }
        kopf.addCell(infoCell)
        doc.add(kopf)
    }

    private fun ladeFirmenlogo(firma: Firmeninformation?): Image? {
        val dateiname = firma?.logoDateiname
        if (!dateiname.isNullOrBlank()) {
            val safe = dateiname.trim()
            if (!safe.contains("..") && !safe.contains("/") && !safe.contains("\\")) {
                val base = Paths.get(uploadPath, "firma", "logo").toAbsolutePath().normalize()
                val logoPath = base.resolve(safe).normalize()
                if (logoPath.startsWith(base) && Files.exists(logoPath)) {
                    try {
                        return Image.getInstance(logoPath.toString())
                    } catch (_: IOException) {
                    } catch (_: BadElementException) {
                    }
                }
            }
        }
        return try {
            val url = javaClass.getResource("/static/firmenlogo_icon.png")
            if (url != null) Image.getInstance(url) else null
        } catch (_: IOException) {
            null
        } catch (_: BadElementException) {
            null
        }
    }

    private fun addRightLine(cell: PdfPCell, text: String?, font: Font) {
        if (text.isNullOrBlank()) return
        val p = Paragraph(text, font)
        p.alignment = Element.ALIGN_RIGHT
        cell.addElement(p)
    }

    private fun joinNonEmpty(sep: String, vararg parts: String?): String =
        parts.filter { !it.isNullOrBlank() }.joinToString(sep) { it!!.trim() }

    private fun prefix(prefix: String, value: String?): String? = if (value.isNullOrBlank()) null else prefix + value.trim()

    @Throws(DocumentException::class)
    private fun addTitle(doc: Document, ym: YearMonth) {
        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, TEXT_DARK)
        val subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12f, TEXT_MUTED)
        val kategorie = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, KPI_ACCENT)
        doc.add(Paragraph("BUCHHALTUNG", kategorie))
        doc.add(Paragraph("BELEGE & KASSE - MONATSEXPORT", titleFont))
        val zeitraum = "Zeitraum: ${monatLabel(ym)}  ·  Erstellt am ${LocalDate.now().format(DATE_FMT)}"
        doc.add(Paragraph(zeitraum, subTitleFont))
        doc.add(Paragraph(" "))
    }

    private fun berechneAnfangssaldo(alleValidiert: List<Beleg>, monatsAnfang: LocalDate): BigDecimal {
        var saldo = BigDecimal.ZERO
        for (b in alleValidiert) {
            val belegDatum = b.belegDatum
            if (belegDatum == null || !belegDatum.isBefore(monatsAnfang)) continue
            val k = b.belegKategorie ?: continue
            if (!k.istKassenBewegung()) continue
            val brutto = nullSafe(b.betragBrutto)
            saldo = if (k.istAusgang()) saldo.subtract(brutto) else saldo.add(brutto)
        }
        return saldo
    }

    @Throws(DocumentException::class)
    private fun addKassenbuchTKonto(doc: Document, belege: List<Beleg>, anfangssaldo: BigDecimal) {
        val sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, TEXT_DARK)
        val section = Paragraph("Kasse · Bargeldkonto (T-Konto)", sectionFont)
        section.spacingBefore = 10f
        doc.add(section)

        val infoFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, TEXT_MUTED)
        val anfang = Paragraph("Anfangssaldo zu Monatsbeginn: ${formatEuro(anfangssaldo)} €", infoFont)
        anfang.spacingBefore = 2f
        anfang.spacingAfter = 6f
        doc.add(anfang)

        val kasse = belege
            .filter { it.belegKategorie?.istKassenBewegung() == true }
            .sortedWith(compareBy<Beleg> { it.belegDatum }.thenBy { it.id })
        val soll = kasse.filter { it.belegKategorie?.istAusgang() != true }
        val haben = kasse.filter { it.belegKategorie?.istAusgang() == true }
        val sumSoll = summeBrutto(soll)
        val sumHaben = summeBrutto(haben)
        val endsaldo = anfangssaldo.add(sumSoll).subtract(sumHaben)

        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setSpacingBefore(4f)
        table.addCell(seitenHeaderCell("SOLL  ·  Eingang"))
        table.addCell(seitenHeaderCell("HABEN  ·  Ausgang"))
        table.addCell(seitenContainer(buildSeitenTabelle(soll)))
        table.addCell(seitenContainer(buildSeitenTabelle(haben)))
        table.addCell(seitenSummeCell("Summe Soll:  ${formatEuro(sumSoll)} €"))
        table.addCell(seitenSummeCell("Summe Haben: ${formatEuro(sumHaben)} €"))
        doc.add(table)

        val endFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, KPI_ACCENT)
        val end = Paragraph("Endsaldo (Anfang + Soll - Haben): ${formatEuro(endsaldo)} €", endFont)
        end.alignment = Element.ALIGN_RIGHT
        end.spacingBefore = 10f
        doc.add(end)
    }

    private fun buildSeitenTabelle(belege: List<Beleg>): PdfPTable {
        val inner = PdfPTable(floatArrayOf(1.4f, 1.6f, 4.5f, 2f))
        inner.widthPercentage = 100f
        val subHdr = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, TEXT_MUTED)
        for (h in arrayOf("Datum", "Beleg-Nr.", "Verwendungszweck", "Betrag €")) {
            val c = PdfPCell(Phrase(h, subHdr))
            c.backgroundColor = SUM_BG
            c.paddingTop = 5f
            c.paddingBottom = 5f
            c.paddingLeft = 6f
            c.paddingRight = 6f
            c.border = Rectangle.BOTTOM
            c.borderColor = BORDER
            c.borderWidth = 0.5f
            inner.addCell(c)
        }
        if (belege.isEmpty()) {
            val empty = PdfPCell(Phrase("Keine Buchungen", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9f, TEXT_MUTED)))
            empty.colspan = 4
            empty.horizontalAlignment = Element.ALIGN_CENTER
            empty.paddingTop = 10f
            empty.paddingBottom = 10f
            empty.border = Rectangle.NO_BORDER
            inner.addCell(empty)
            return inner
        }
        var alt = false
        for (b in belege) {
            val bg = if (alt) ROW_ALT else Color.WHITE
            val datum = b.belegDatum?.format(DATE_SHORT) ?: "-"
            val nr = b.belegNummer ?: "-"
            val zweck = if (!b.beschreibung.isNullOrBlank()) b.beschreibung else kategorieLabel(b.belegKategorie)
            val betrag = formatEuro(nullSafe(b.betragBrutto))
            inner.addCell(cell(datum, bg, Element.ALIGN_LEFT))
            inner.addCell(cell(nr, bg, Element.ALIGN_LEFT))
            inner.addCell(cell(zweck, bg, Element.ALIGN_LEFT))
            inner.addCell(cell(betrag, bg, Element.ALIGN_RIGHT))
            alt = !alt
        }
        return inner
    }

    private fun seitenHeaderCell(text: String): PdfPCell {
        val c = PdfPCell(Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE)))
        c.backgroundColor = HEADER_BG
        c.horizontalAlignment = Element.ALIGN_CENTER
        c.paddingTop = 8f
        c.paddingBottom = 8f
        c.border = Rectangle.NO_BORDER
        return c
    }

    private fun seitenContainer(inner: PdfPTable): PdfPCell {
        val c = PdfPCell(inner)
        c.setPadding(0f)
        c.border = Rectangle.BOX
        c.borderColor = BORDER
        c.borderWidth = 0.5f
        return c
    }

    private fun seitenSummeCell(text: String): PdfPCell {
        val c = PdfPCell(Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, TEXT_DARK)))
        c.backgroundColor = SUM_BG
        c.horizontalAlignment = Element.ALIGN_RIGHT
        c.paddingTop = 8f
        c.paddingBottom = 8f
        c.paddingLeft = 10f
        c.paddingRight = 10f
        c.borderColor = TEXT_DARK
        c.borderWidthTop = 1f
        c.borderWidthBottom = 0f
        c.borderWidthLeft = 0f
        c.borderWidthRight = 0f
        return c
    }

    private fun summeBrutto(belege: List<Beleg>): BigDecimal =
        belege.fold(BigDecimal.ZERO) { sum, beleg -> sum.add(nullSafe(beleg.betragBrutto)) }

    @Throws(DocumentException::class)
    private fun addFooter(doc: Document) {
        val footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, FOOTER_GREY)
        val footer = Paragraph(
            "Dieses Dokument wurde maschinell erstellt und enthält nur validierte Belege. " +
                "Die zugehörigen Belegfotos liegen im selben Ordner.",
            footerFont,
        )
        footer.alignment = Element.ALIGN_CENTER
        footer.spacingBefore = 16f
        doc.add(footer)
    }

    private fun cell(text: String?, bg: Color, alignment: Int): PdfPCell {
        val c = PdfPCell(Phrase(text ?: "", FontFactory.getFont(FontFactory.HELVETICA, 9f, TEXT_CELL)))
        c.backgroundColor = bg
        c.horizontalAlignment = alignment
        c.paddingTop = 6f
        c.paddingBottom = 6f
        c.paddingLeft = 6f
        c.paddingRight = 6f
        c.border = Rectangle.BOTTOM
        c.borderColor = BORDER
        c.borderWidth = 0.5f
        return c
    }

    private fun nullSafe(value: BigDecimal?): BigDecimal = value ?: BigDecimal.ZERO

    private fun formatEuro(value: BigDecimal?): String =
        String.format(Locale.GERMAN, "%,.2f", (value ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))

    private fun kategorieLabel(k: BelegKategorie?): String =
        when (k) {
            null -> "-"
            BelegKategorie.UNZUGEORDNET -> "Unzugeordnet"
            BelegKategorie.KASSE_EINNAHME -> "Kasse · Einnahme"
            BelegKategorie.KASSE_AUSGABE -> "Kasse · Ausgabe"
            BelegKategorie.PRIVATENTNAHME -> "Privatentnahme"
            BelegKategorie.PRIVATEINLAGE -> "Privateinlage"
            BelegKategorie.BANK -> "Bank"
            BelegKategorie.KREDITKARTE -> "Kreditkarte"
            BelegKategorie.SONSTIGER_BELEG -> "Sonstiger Beleg"
        }

    private fun monatLabel(ym: YearMonth): String {
        val monate = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
        return monate[ym.monthValue - 1] + " " + ym.year
    }

    companion object {
        private val HEADER_BG = Color(220, 38, 38)
        private val ROW_ALT = Color(254, 242, 242)
        private val BORDER = Color(229, 231, 235)
        private val SUM_BG = Color(241, 245, 249)
        private val TEXT_DARK = Color(30, 41, 59)
        private val TEXT_MUTED = Color(100, 116, 139)
        private val TEXT_CELL = Color(55, 65, 81)
        private val FOOTER_GREY = Color(148, 163, 184)
        private val KPI_ACCENT = Color(220, 38, 38)
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
    }
}
