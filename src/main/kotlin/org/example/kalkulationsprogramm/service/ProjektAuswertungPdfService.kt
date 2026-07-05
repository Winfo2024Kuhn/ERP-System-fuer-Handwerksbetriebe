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
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.springframework.stereotype.Service
import java.awt.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Comparator
import java.util.TreeMap

@Service
class ProjektAuswertungPdfService(
    private val zeitbuchungRepository: ZeitbuchungRepository,
) {
    fun generatePdf(
        projektId: Long,
        von: LocalDate?,
        bis: LocalDate?,
        sortField: String?,
        sortDir: String?,
        groupBy: String?,
    ): Path {
        val alleBuchungen = zeitbuchungRepository.findByProjektId(projektId)
            .filter { b ->
                if (von == null && bis == null) {
                    true
                } else {
                    val d = b.startZeit?.toLocalDate()
                    d != null && (von == null || !d.isBefore(von)) && (bis == null || !d.isAfter(bis))
                }
            }
            .sortedWith(compareBy(nullsLast()) { it.startZeit })

        if (alleBuchungen.isEmpty()) {
            throw RuntimeException("Keine Buchungen fuer diesen Zeitraum gefunden.")
        }

        val reference = alleBuchungen.first()
        val bauvorhaben = reference.projekt?.bauvorhaben
        val kunde = reference.projekt?.getKunde()
        val auftrag = reference.projekt?.auftragsnummer

        val effectiveGroupBy = groupBy ?: "arbeitsgang"
        val sortierteGruppen = TreeMap(alleBuchungen.groupBy { resolveGroupKey(it, effectiveGroupBy) })

        try {
            val dir = Path.of("uploads")
            Files.createDirectories(dir)
            val temp = Files.createTempFile(dir, "regiebericht-", ".pdf")
            val doc = Document(PageSize.A4.rotate())
            PdfWriter.getInstance(doc, Files.newOutputStream(temp))
            doc.open()

            try {
                val logo = Image.getInstance(javaClass.getResource("/static/firmenlogo.png"))
                logo.scaleToFit(150f, 70f)
                doc.add(logo)
            } catch (ignored: Exception) {
            }
            doc.add(Paragraph(" "))

            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, Color(50, 50, 50))
            val subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12f, Color(100, 100, 100))

            doc.add(Paragraph("REGIEBERICHT / ZEITNACHWEIS", titleFont))
            doc.add(Paragraph("Gruppiert nach: ${groupByLabel(effectiveGroupBy)}", subTitleFont))
            doc.add(Paragraph(" ", subTitleFont))

            val headerTable = PdfPTable(2)
            headerTable.widthPercentage = 100f
            headerTable.addCell(noBorderCell("Bauvorhaben: $bauvorhaben", subTitleFont))
            headerTable.addCell(noBorderCell("Kunde: $kunde", subTitleFont))
            headerTable.addCell(noBorderCell("Auftrag: ${auftrag ?: "-"}", subTitleFont))
            headerTable.addCell(noBorderCell("Datum: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", subTitleFont))
            doc.add(headerTable)
            doc.add(Paragraph(" "))

            if (von != null || bis != null) {
                val zeitraum = "Zeitraum: " +
                    (von?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Anfang") +
                    " - " +
                    (bis?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Ende")
                doc.add(Paragraph(zeitraum, subTitleFont))
                doc.add(Paragraph(" "))
            }

            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Color.WHITE)
            val cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, Color(55, 65, 81))
            val sumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Color(55, 65, 81))
            val headerBg = Color(220, 38, 38)
            val rowAlt = Color(254, 242, 242)
            val borderColor = Color(229, 231, 235)
            val sumBg = Color(241, 245, 249)

            val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy")
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            var totalHoursAll = BigDecimal.ZERO
            val globalQualifikationMinutes = HashMap<String, Long>()

            for ((groupLabel, values) in sortierteGruppen) {
                val bookings = values.toMutableList()
                var comparator = buildComparator(sortField)
                if ("desc".equals(sortDir, ignoreCase = true)) {
                    comparator = comparator.reversed()
                }
                bookings.sortWith(comparator)

                val activityFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, Color(30, 41, 59))
                val groupP = Paragraph(groupLabel, activityFont)
                groupP.spacingBefore = 8f
                doc.add(groupP)
                doc.add(Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6f)))

                val table = PdfPTable(floatArrayOf(2.2f, 1.8f, 2f, 3f, 1.6f, 1.2f, 1.2f, 1.1f))
                table.widthPercentage = 100f
                table.setSpacingBefore(4f)

                arrayOf("Mitarbeiter", "Qualifikation", "Arbeitsgang", "Produktkategorie", "Datum", "Start", "Ende", "Std.").forEach { h ->
                    table.addCell(PdfPCell(Phrase(h, headerFont)).apply {
                        backgroundColor = headerBg
                        paddingTop = 8f
                        paddingBottom = 8f
                        paddingLeft = 6f
                        paddingRight = 6f
                        border = Rectangle.NO_BORDER
                    })
                }

                var groupMinutes = 0L
                var alt = false
                val groupQualifikationMinutes = HashMap<String, Long>()

                for (b in bookings) {
                    val bg = if (alt) rowAlt else Color.WHITE
                    val mitarbeiter = b.mitarbeiter?.let { "${it.vorname} ${it.nachname}" } ?: "Unbekannt"
                    val qualifikation = b.mitarbeiter?.qualifikation?.bezeichnung ?: "-"
                    val arbeitsgang = b.arbeitsgangStundensatz?.arbeitsgang?.beschreibung
                        ?: b.arbeitsgang?.beschreibung
                        ?: "-"
                    val kategoriePfad = b.projektProduktkategorie?.produktkategorie?.let { buildKategoriePfad(it) } ?: "-"
                    val datum = b.startZeit?.format(dateFmt) ?: ""
                    val start = b.startZeit?.format(timeFmt) ?: ""
                    val ende = b.endeZeit?.format(timeFmt) ?: ""
                    val mins = if (b.startZeit != null && b.endeZeit != null) {
                        Duration.between(b.startZeit, b.endeZeit).toMinutes()
                    } else {
                        0L
                    }
                    groupMinutes += mins
                    groupQualifikationMinutes.merge(qualifikation, mins, Long::plus)
                    globalQualifikationMinutes.merge(qualifikation, mins, Long::plus)

                    val dauer = "%.2f".format(mins.toDouble() / 60.0)
                    table.addCell(makeModernCell(mitarbeiter, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(qualifikation, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(arbeitsgang, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(kategoriePfad, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(datum, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(start, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(ende, cellFont, bg, borderColor))
                    table.addCell(makeModernCell(dauer, cellFont, bg, borderColor))
                    alt = !alt
                }

                val groupHours = BigDecimal(groupMinutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
                totalHoursAll = totalHoursAll.add(groupHours)

                for ((key, value) in groupQualifikationMinutes) {
                    val qh = BigDecimal(value).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
                    table.addCell(PdfPCell(Phrase("Summe ${key}stunden", cellFont)).apply {
                        colspan = 7
                        horizontalAlignment = Element.ALIGN_RIGHT
                        backgroundColor = Color.WHITE
                        paddingTop = 6f
                        paddingBottom = 6f
                        paddingRight = 12f
                        border = Rectangle.BOTTOM
                        setBorderColor(borderColor)
                        borderWidth = 0.5f
                    })
                    table.addCell(PdfPCell(Phrase("$qh h", cellFont)).apply {
                        backgroundColor = Color.WHITE
                        paddingTop = 6f
                        paddingBottom = 6f
                        paddingLeft = 8f
                        border = Rectangle.BOTTOM
                        setBorderColor(borderColor)
                        borderWidth = 0.5f
                    })
                }

                table.addCell(PdfPCell(Phrase("Summe $groupLabel", sumFont)).apply {
                    colspan = 7
                    horizontalAlignment = Element.ALIGN_RIGHT
                    backgroundColor = sumBg
                    paddingTop = 10f
                    paddingBottom = 10f
                    paddingRight = 12f
                    border = Rectangle.NO_BORDER
                })
                table.addCell(PdfPCell(Phrase("$groupHours h", sumFont)).apply {
                    backgroundColor = sumBg
                    paddingTop = 10f
                    paddingBottom = 10f
                    paddingLeft = 8f
                    border = Rectangle.NO_BORDER
                })

                doc.add(table)
                doc.add(Paragraph(" "))
            }

            val totalTable = PdfPTable(2)
            totalTable.setWidths(floatArrayOf(3f, 1f))
            totalTable.widthPercentage = 50f
            totalTable.horizontalAlignment = Element.ALIGN_RIGHT
            totalTable.setSpacingBefore(16f)

            val totalLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 11f, Color(55, 65, 81))
            val totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, Color(220, 38, 38))
            val totalRowBg = Color(254, 242, 242)

            for ((key, value) in globalQualifikationMinutes) {
                val qh = BigDecimal(value).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
                totalTable.addCell(PdfPCell(Phrase("Summe ${key}stunden:", totalLabelFont)).apply {
                    backgroundColor = totalRowBg
                    horizontalAlignment = Element.ALIGN_RIGHT
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingRight = 12f
                    paddingLeft = 16f
                    border = Rectangle.NO_BORDER
                })
                totalTable.addCell(PdfPCell(Phrase("$qh h", totalLabelFont)).apply {
                    backgroundColor = totalRowBg
                    horizontalAlignment = Element.ALIGN_RIGHT
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingRight = 16f
                    border = Rectangle.NO_BORDER
                })
            }

            totalTable.addCell(PdfPCell(Phrase("Gesamtstunden:", totalFont)).apply {
                backgroundColor = totalRowBg
                horizontalAlignment = Element.ALIGN_RIGHT
                paddingTop = 12f
                paddingBottom = 12f
                paddingRight = 12f
                paddingLeft = 16f
                border = Rectangle.NO_BORDER
            })
            totalTable.addCell(PdfPCell(Phrase("$totalHoursAll h", totalFont)).apply {
                backgroundColor = totalRowBg
                horizontalAlignment = Element.ALIGN_RIGHT
                paddingTop = 12f
                paddingBottom = 12f
                paddingRight = 16f
                border = Rectangle.NO_BORDER
            })

            doc.add(totalTable)
            doc.add(Paragraph(" "))
            doc.add(Paragraph(" "))
            val footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, Color(148, 163, 184))
            doc.add(Paragraph("Dieses Dokument wurde maschinell erstellt und ist ohne Unterschrift gueltig.", footerFont).apply {
                alignment = Element.ALIGN_CENTER
            })

            doc.close()
            return temp
        } catch (e: Exception) {
            throw RuntimeException("Fehler beim Generieren des Regieberichts", e)
        }
    }

    fun resolveGroupKey(b: Zeitbuchung, groupBy: String): String =
        when (groupBy) {
            "qualifikation" -> b.mitarbeiter?.qualifikation?.bezeichnung ?: "Keine Qualifikation"
            "mitarbeiter" -> b.mitarbeiter?.let { "${it.vorname} ${it.nachname}" } ?: "Unbekannter Mitarbeiter"
            "datum" -> b.startZeit?.toLocalDate()?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Kein Datum"
            "produktkategorie" -> b.projektProduktkategorie?.produktkategorie?.let { buildKategoriePfad(it) } ?: "Keine Kategorie"
            else -> b.arbeitsgangStundensatz?.arbeitsgang?.beschreibung ?: b.arbeitsgang?.beschreibung ?: "Sonstiges"
        }

    fun groupByLabel(groupBy: String): String =
        when (groupBy) {
            "qualifikation" -> "Qualifikation"
            "mitarbeiter" -> "Mitarbeiter"
            "datum" -> "Datum"
            "produktkategorie" -> "Produktkategorie"
            else -> "Arbeitsgang"
        }

    fun buildKategoriePfad(kategorie: Produktkategorie?): String {
        if (kategorie == null) return "-"
        val parts = ArrayDeque<String>()
        var current: Produktkategorie? = kategorie
        while (current != null) {
            parts.addFirst(current.bezeichnung)
            current = current.uebergeordneteKategorie
        }
        return parts.joinToString("/")
    }

    fun buildComparator(sortField: String?): Comparator<Zeitbuchung> =
        when (sortField ?: "datum") {
            "mitarbeiter" -> compareBy(nullsLast()) { b: Zeitbuchung ->
                b.mitarbeiter?.let { "${it.nachname} ${it.vorname}" } ?: ""
            }
            "arbeitsgang" -> compareBy(nullsLast()) { b: Zeitbuchung ->
                b.arbeitsgangStundensatz?.arbeitsgang?.beschreibung ?: b.arbeitsgang?.beschreibung ?: ""
            }
            "dauer" -> compareBy(nullsLast()) { b: Zeitbuchung ->
                if (b.startZeit == null || b.endeZeit == null) 0L else Duration.between(b.startZeit, b.endeZeit).toMinutes()
            }
            "produktkategorie" -> compareBy(nullsLast()) { b: Zeitbuchung ->
                b.projektProduktkategorie?.produktkategorie?.let { buildKategoriePfad(it) } ?: ""
            }
            else -> compareBy(nullsLast()) { b: Zeitbuchung -> b.startZeit }
        }

    private fun makeModernCell(text: String, font: Font, bg: Color, borderColor: Color): PdfPCell =
        PdfPCell(Phrase(text, font)).apply {
            backgroundColor = bg
            paddingTop = 7f
            paddingBottom = 7f
            paddingLeft = 6f
            paddingRight = 6f
            border = Rectangle.BOTTOM
            this.borderColor = borderColor
            borderWidth = 0.5f
        }

    private fun noBorderCell(text: String, font: Font): PdfPCell =
        PdfPCell(Phrase(text, font)).apply {
            border = Rectangle.NO_BORDER
            setPadding(4f)
        }
}
