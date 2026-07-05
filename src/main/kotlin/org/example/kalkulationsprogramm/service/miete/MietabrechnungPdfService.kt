package org.example.kalkulationsprogramm.service.miete

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.DocumentException
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfWriter
import org.example.kalkulationsprogramm.domain.miete.Kostenposition
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselTyp
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@Service
class MietabrechnungPdfService(
    private val mietabrechnungService: MietabrechnungService,
) {
    fun generatePdf(mietobjektId: Long, jahr: Int): ByteArray {
        val result = mietabrechnungService.berechneJahresabrechnung(mietobjektId, jahr)
        return try {
            ByteArrayOutputStream().use { baos ->
                val document = Document(PageSize.A4, 40f, 40f, 50f, 50f)
                val writer = PdfWriter.getInstance(document, baos)
                writer.pageEvent = FooterPageEvent(result)
                document.open()
                addHeader(document, result)
                addSummaryCards(document, result)
                addKostenstellenOverview(document, result)
                addKostenstellenDetails(document, result)
                addParteiAbrechnung(document, result)
                document.close()
                baos.toByteArray()
            }
        } catch (e: DocumentException) {
            throw IllegalStateException("PDF-Erstellung fehlgeschlagen", e)
        } catch (e: IOException) {
            throw IllegalStateException("PDF-Erstellung fehlgeschlagen", e)
        }
    }

    @Throws(DocumentException::class)
    private fun addHeader(document: Document, result: AnnualAccountingResult) {
        val headerBar = PdfPTable(1)
        headerBar.widthPercentage = 100f
        headerBar.addCell(PdfPCell().apply {
            backgroundColor = PRIMARY
            fixedHeight = 4f
            border = Rectangle.NO_BORDER
        })
        document.add(headerBar)
        addSpace(document, 12f)

        val headerTable = PdfPTable(floatArrayOf(3f, 2f))
        headerTable.widthPercentage = 100f
        headerTable.addCell(PdfPCell().apply {
            border = Rectangle.NO_BORDER
            paddingLeft = 0f
            addElement(Paragraph("Jahresabrechnung ${result.abrechnungsJahr}", TITLE_FONT))
            addElement(Paragraph(safeStr(result.mietobjektName), SUBTITLE_FONT))
        })

        val addr = StringBuilder()
        if (hasText(result.mietobjektStrasse)) addr.append(result.mietobjektStrasse?.trim())
        if (hasText(result.mietobjektPlz) || hasText(result.mietobjektOrt)) {
            if (addr.isNotEmpty()) addr.append("\n")
            if (hasText(result.mietobjektPlz)) addr.append(result.mietobjektPlz?.trim())
            if (hasText(result.mietobjektOrt)) {
                if (hasText(result.mietobjektPlz)) addr.append(" ")
                addr.append(result.mietobjektOrt?.trim())
            }
        }
        headerTable.addCell(PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            verticalAlignment = Element.ALIGN_MIDDLE
            addElement(Paragraph(addr.toString(), SUBTITLE_FONT).apply { alignment = Element.ALIGN_RIGHT })
        })
        document.add(headerTable)
        addDivider(document)
    }

    @Throws(DocumentException::class)
    private fun addSummaryCards(document: Document, result: AnnualAccountingResult) {
        val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val cards = PdfPTable(3)
        cards.widthPercentage = 100f
        cards.setSpacingBefore(4f)
        val gesamt = safe(result.gesamtkosten)
        val vorjahr = safe(result.gesamtkostenVorjahr)
        val diff = safe(result.gesamtkostenDifferenz)
        addSummaryCard(cards, "Gesamtkosten ${result.abrechnungsJahr}", currency.format(gesamt), null)
        addSummaryCard(cards, "Vorjahr ${(result.abrechnungsJahr ?: 0) - 1}", currency.format(vorjahr), null)
        addSummaryCard(cards, "Veraenderung", formatSignedCurrency(diff, currency), if (diff > BigDecimal.ZERO) NEGATIVE else POSITIVE)
        document.add(cards)
        addSpace(document, 10f)
    }

    private fun addSummaryCard(table: PdfPTable, label: String, value: String, valueColor: java.awt.Color?) {
        val cell = PdfPCell()
        cell.borderColor = BORDER_LIGHT
        cell.borderWidth = 1f
        cell.backgroundColor = CARD_BG
        cell.setPadding(12f)
        cell.paddingBottom = 10f
        cell.addElement(Paragraph(label, LABEL_FONT))
        cell.addElement(Paragraph(value, valueColor?.let { Font(Font.HELVETICA, 14f, Font.BOLD, it) } ?: VALUE_FONT))
        table.addCell(cell)
    }

    @Throws(DocumentException::class)
    private fun addKostenstellenOverview(document: Document, result: AnnualAccountingResult) {
        addSectionHeader(document, "Kostenuebersicht")
        val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val table = PdfPTable(floatArrayOf(4f, 2f, 2f, 3f))
        table.widthPercentage = 100f
        table.setSpacingBefore(4f)
        addStyledHeaderCell(table, "Kostenstelle", Element.ALIGN_LEFT)
        addStyledHeaderCell(table, "Betrag", Element.ALIGN_RIGHT)
        addStyledHeaderCell(table, "Vorjahr", Element.ALIGN_RIGHT)
        addStyledHeaderCell(table, "Verteilungsschluessel", Element.ALIGN_LEFT)
        var totalAktuell = BigDecimal.ZERO
        var totalVorjahr = BigDecimal.ZERO
        result.kostenstellen.orEmpty().forEachIndexed { row, ks ->
            val summe = safe(ks.gesamtkosten)
            val summeVj = safe(ks.gesamtkostenVorjahr)
            totalAktuell = totalAktuell.add(summe)
            totalVorjahr = totalVorjahr.add(summeVj)
            val bg = if (row % 2 == 1) ROW_ALT else WHITE
            addBodyCell(table, ks.kostenstelle?.name, TEXT_BOLD, Element.ALIGN_LEFT, bg)
            addBodyCell(table, currency.format(summe), TEXT_FONT, Element.ALIGN_RIGHT, bg)
            addBodyCell(table, currency.format(summeVj), TEXT_SMALL, Element.ALIGN_RIGHT, bg)
            addBodyCell(table, formatStandardSchluessel(ks.kostenstelle), TEXT_SMALL, Element.ALIGN_LEFT, bg)
        }
        addFooterCell(table, "Gesamt", Element.ALIGN_LEFT)
        addFooterCell(table, currency.format(totalAktuell), Element.ALIGN_RIGHT)
        addFooterCell(table, currency.format(totalVorjahr), Element.ALIGN_RIGHT)
        addFooterCell(table, "", Element.ALIGN_LEFT)
        document.add(table)
        addSpace(document, 12f)
    }

    @Throws(DocumentException::class)
    private fun addKostenstellenDetails(document: Document, result: AnnualAccountingResult) {
        val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val numberFormat = (NumberFormat.getNumberInstance(Locale.GERMANY) as DecimalFormat).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        val faktorFormat = (NumberFormat.getNumberInstance(Locale.GERMANY) as DecimalFormat).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 5
        }
        val verbrauchMap = linkedMapOf<Long, AnnualAccountingResult.Verbrauchsvergleich>()
        result.verbrauchsvergleiche.orEmpty().forEach { v ->
            val id = v.verbrauchsgegenstand?.id
            if (id != null) verbrauchMap[id] = v
        }
        result.kostenstellen.orEmpty().forEach { ks ->
            addDivider(document)
            val ksHeader = PdfPTable(floatArrayOf(1f, 1f))
            ksHeader.widthPercentage = 100f
            ksHeader.setSpacingBefore(4f)
            ksHeader.addCell(PdfPCell().apply {
                border = Rectangle.NO_BORDER
                addElement(Paragraph(ks.kostenstelle?.name.orEmpty(), SECTION_FONT))
            })
            ksHeader.addCell(PdfPCell().apply {
                border = Rectangle.NO_BORDER
                horizontalAlignment = Element.ALIGN_RIGHT
                addElement(Paragraph(currency.format(safe(ks.gesamtkosten)), VALUE_SMALL_FONT).apply { alignment = Element.ALIGN_RIGHT })
            })
            document.add(ksHeader)
            addSpace(document, 4f)
            ks.positionen.orEmpty().forEach { addKostenpositionDetail(document, it, verbrauchMap, currency, numberFormat, faktorFormat) }
            if (!ks.parteianteile.isNullOrEmpty()) {
                addSpace(document, 4f)
                document.add(Paragraph("Verteilung auf Parteien", SUBSECTION_FONT).apply { spacingBefore = 2f })
                val parteiTable = PdfPTable(floatArrayOf(4f, 2f))
                parteiTable.widthPercentage = 60f
                parteiTable.setSpacingBefore(4f)
                addStyledHeaderCell(parteiTable, "Partei", Element.ALIGN_LEFT)
                addStyledHeaderCell(parteiTable, "Betrag", Element.ALIGN_RIGHT)
                ks.parteianteile.orEmpty().forEachIndexed { index, anteil ->
                    val bg = if (index % 2 == 1) ROW_ALT else WHITE
                    addBodyCell(parteiTable, anteil.mietpartei?.name, TEXT_FONT, Element.ALIGN_LEFT, bg)
                    addBodyCell(parteiTable, currency.format(safe(anteil.betrag)), TEXT_BOLD, Element.ALIGN_RIGHT, bg)
                }
                document.add(parteiTable)
            }
            addSpace(document, 8f)
        }
    }

    @Throws(DocumentException::class)
    private fun addKostenpositionDetail(
        document: Document,
        position: Kostenposition,
        verbrauchMap: Map<Long, AnnualAccountingResult.Verbrauchsvergleich>,
        currency: NumberFormat,
        numberFormat: DecimalFormat,
        faktorFormat: DecimalFormat,
    ) {
        val beschreibung = position.beschreibung?.takeIf { it.isNotBlank() } ?: position.belegNummer?.let { "Beleg $it" } ?: "Kostenposition"
        val betrag = safe(position.betrag)
        val schluessel = position.verteilungsschluesselOverride ?: position.kostenstelle?.standardSchluessel
        val istVerbrauchsfaktor = position.berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR
        val positionVerbrauch = linkedMapOf<Long, AnnualAccountingResult.Verbrauchsvergleich>()
        if (istVerbrauchsfaktor && schluessel == null) {
            positionVerbrauch.putAll(verbrauchMap)
        } else {
            schluessel?.eintraege.orEmpty().forEach { eintrag ->
                val id = eintrag.verbrauchsgegenstand?.id
                val vv = id?.let { verbrauchMap[it] }
                if (id != null && vv != null) positionVerbrauch.putIfAbsent(id, vv)
            }
        }

        val posBox = PdfPTable(floatArrayOf(3f, 2f))
        posBox.widthPercentage = 100f
        posBox.setSpacingBefore(4f)

        val leftCell = PdfPCell()
        leftCell.borderColor = BORDER_LIGHT
        leftCell.borderWidth = 0.5f
        leftCell.setPadding(8f)
        leftCell.backgroundColor = WHITE
        leftCell.addElement(Paragraph().apply {
            add(Chunk(beschreibung, TEXT_BOLD))
            add(Chunk("    ", TEXT_FONT))
            add(Chunk(currency.format(betrag), Font(Font.HELVETICA, 10f, Font.BOLD, PRIMARY)))
        })
        if (schluessel != null) {
            addSpaceToCell(leftCell, 4f)
            leftCell.addElement(Paragraph().apply {
                add(Chunk("Verteilung: ", LABEL_FONT))
                add(Chunk("${safeStr(schluessel.name)} (${formatSchluesselTyp(schluessel.typ)})", TEXT_SMALL))
            })
            schluessel.eintraege.orEmpty().forEach { eintrag ->
                val parteiName = eintrag.mietpartei?.name?.takeIf { hasText(it) }?.trim() ?: "-"
                leftCell.addElement(Paragraph().apply {
                    add(Chunk("  - $parteiName: ", TEXT_SMALL))
                    if (schluessel.typ == VerteilungsschluesselTyp.VERBRAUCH) {
                        val gegenstand = eintrag.verbrauchsgegenstand
                        val vv = gegenstand?.id?.let { positionVerbrauch[it] }
                        add(Chunk(if (gegenstand != null) buildVerbrauchsgegenstandName(vv, gegenstand) else "Zuordnung fehlt", TEXT_SMALL))
                    } else if (eintrag.anteil != null) {
                        var formatted = numberFormat.format(eintrag.anteil)
                        if (schluessel.typ == VerteilungsschluesselTyp.PROZENTUAL) formatted += " %"
                        add(Chunk(formatted, TEXT_SMALL))
                    }
                })
            }
        }
        posBox.addCell(leftCell)

        val rightCell = PdfPCell()
        rightCell.borderColor = BORDER_LIGHT
        rightCell.borderWidth = 0.5f
        rightCell.setPadding(8f)
        rightCell.backgroundColor = CARD_BG
        if (istVerbrauchsfaktor && position.verbrauchsfaktor != null) {
            rightCell.addElement(Paragraph("Berechnung", LABEL_FONT))
            addSpaceToCell(rightCell, 2f)
            val faktor = position.verbrauchsfaktor
            rightCell.addElement(Paragraph().apply {
                add(Chunk("Faktor: ", TEXT_SMALL))
                add(Chunk(faktorFormat.format(faktor), TEXT_BOLD))
            })
            val verbrauchSumme = positionVerbrauch.values.fold(BigDecimal.ZERO) { acc, vv -> acc.add(vv.verbrauchJahr ?: BigDecimal.ZERO) }
            val verbrauchVorjahrSumme = positionVerbrauch.values.fold(BigDecimal.ZERO) { acc, vv -> acc.add(vv.verbrauchVorjahr ?: BigDecimal.ZERO) }
            val berechnet = verbrauchSumme.multiply(faktor).setScale(2, RoundingMode.HALF_UP)
            val verbrauchDiff = verbrauchSumme.subtract(verbrauchVorjahrSumme)
            rightCell.addElement(Paragraph().apply {
                add(Chunk("Verbrauch: ", TEXT_SMALL))
                add(Chunk(numberFormat.format(verbrauchSumme), TEXT_BOLD))
            })
            rightCell.addElement(Paragraph().apply {
                add(Chunk("Vorjahr: ", TEXT_SMALL))
                add(Chunk(numberFormat.format(verbrauchVorjahrSumme), TEXT_SMALL))
                add(Chunk("  (", TEXT_SMALL))
                add(Chunk(formatSignedDecimal(verbrauchDiff, numberFormat), Font(Font.HELVETICA, 8f, Font.BOLD, if (verbrauchDiff > BigDecimal.ZERO) NEGATIVE else POSITIVE)))
                add(Chunk(")", TEXT_SMALL))
            })
            addSpaceToCell(rightCell, 4f)
            rightCell.addElement(Paragraph().apply {
                add(Chunk("Ergebnis: ", TEXT_SMALL))
                add(Chunk(currency.format(berechnet), Font(Font.HELVETICA, 10f, Font.BOLD, PRIMARY)))
            })
        } else {
            rightCell.addElement(Paragraph("Berechnung", LABEL_FONT))
            addSpaceToCell(rightCell, 2f)
            rightCell.addElement(Paragraph("Fester Betrag laut Erfassung", TEXT_SMALL))
        }
        posBox.addCell(rightCell)
        document.add(posBox)

        if (positionVerbrauch.isNotEmpty()) {
            val vTable = PdfPTable(floatArrayOf(4f, 2f, 2f, 2f))
            vTable.widthPercentage = 100f
            addSmallHeaderCell(vTable, "Zaehler", Element.ALIGN_LEFT)
            addSmallHeaderCell(vTable, "Aktuell", Element.ALIGN_RIGHT)
            addSmallHeaderCell(vTable, "Vorjahr", Element.ALIGN_RIGHT)
            addSmallHeaderCell(vTable, "Differenz", Element.ALIGN_RIGHT)
            positionVerbrauch.values.forEachIndexed { index, vv ->
                val bg = if (index % 2 == 1) ROW_ALT else WHITE
                val gegenstand = vv.verbrauchsgegenstand
                val einheit = gegenstand?.einheit?.takeIf { hasText(it) }?.let { " ${it.trim()}" }.orEmpty()
                addBodyCell(vTable, buildVerbrauchsgegenstandName(vv, gegenstand), TEXT_SMALL, Element.ALIGN_LEFT, bg)
                addBodyCell(vTable, formatNumber(vv.verbrauchJahr, numberFormat) + einheit, TEXT_FONT, Element.ALIGN_RIGHT, bg)
                addBodyCell(vTable, formatNumber(vv.verbrauchVorjahr, numberFormat) + einheit, TEXT_SMALL, Element.ALIGN_RIGHT, bg)
                val diff = vv.differenz
                val diffText = diff?.let { formatSignedDecimal(it, numberFormat) + einheit } ?: "-"
                val diffColor = when {
                    diff != null && diff > BigDecimal.ZERO -> NEGATIVE
                    diff != null && diff < BigDecimal.ZERO -> POSITIVE
                    else -> TEXT_MUTED
                }
                addBodyCell(vTable, diffText, Font(Font.HELVETICA, 8f, Font.BOLD, diffColor), Element.ALIGN_RIGHT, bg)
            }
            document.add(vTable)
        }
    }

    @Throws(DocumentException::class)
    private fun addParteiAbrechnung(document: Document, result: AnnualAccountingResult) {
        document.newPage()
        addSectionHeader(document, "Endabrechnung nach Parteien")
        val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        result.parteien.orEmpty().forEach { p ->
            val summe = safe(p.betrag)
            val vorauszahlungJahr = safe(p.vorauszahlungJahr)
            val vorauszahlungMonat = safe(p.vorauszahlungMonatlich)
            val saldo = safe(p.saldo)
            val rolle = if (p.mietpartei?.rolle == MietparteiRolle.EIGENTUEMER) "Eigentuemer" else "Mieter"
            val parteiCard = PdfPTable(1)
            parteiCard.widthPercentage = 100f
            parteiCard.setSpacingBefore(8f)
            val cardCell = PdfPCell()
            cardCell.borderColor = BORDER_LIGHT
            cardCell.borderWidth = 1f
            cardCell.setPadding(12f)
            cardCell.backgroundColor = WHITE
            cardCell.addElement(Paragraph().apply {
                add(Chunk(p.mietpartei?.name.orEmpty(), SECTION_FONT))
                add(Chunk("  ", TEXT_FONT))
                add(Chunk(" $rolle ", Font(Font.HELVETICA, 8f, Font.BOLD, if (p.mietpartei?.rolle == MietparteiRolle.EIGENTUEMER) TEXT_MUTED else PRIMARY)))
            })
            addSpaceToCell(cardCell, 8f)
            val detailTable = PdfPTable(floatArrayOf(3f, 2f))
            detailTable.widthPercentage = 100f
            addLabelValueRow(detailTable, "Nebenkosten ${result.abrechnungsJahr}", currency.format(summe))
            if (vorauszahlungJahr > BigDecimal.ZERO) {
                var vzText = currency.format(vorauszahlungJahr)
                if (vorauszahlungMonat > BigDecimal.ZERO) vzText += "  (${currency.format(vorauszahlungMonat)}/Monat)"
                addLabelValueRow(detailTable, "Geleistete Vorauszahlungen", "- $vzText")
            }
            cardCell.addElement(detailTable)
            addSpaceToCell(cardCell, 6f)
            cardCell.addElement(PdfPTable(1).apply {
                widthPercentage = 100f
                addCell(PdfPCell().apply {
                    border = Rectangle.NO_BORDER
                    borderWidthTop = 1f
                    borderColorTop = BORDER_LIGHT
                    fixedHeight = 1f
                })
            })
            addSpaceToCell(cardCell, 6f)
            val isNachzahlung = saldo > BigDecimal.ZERO
            cardCell.addElement(Paragraph().apply {
                add(Chunk((if (isNachzahlung) "Nachzahlung" else "Guthaben") + ":  ", SUBSECTION_FONT))
                add(Chunk(currency.format(saldo.abs()), Font(Font.HELVETICA, 14f, Font.BOLD, if (isNachzahlung) NEGATIVE else POSITIVE)))
            })
            parteiCard.addCell(cardCell)
            document.add(parteiCard)
        }
    }

    private fun addStyledHeaderCell(table: PdfPTable, text: String, align: Int) {
        table.addCell(PdfPCell(Phrase(text, TABLE_HEADER)).apply {
            backgroundColor = HEADER_BG
            borderColor = BORDER_LIGHT
            borderWidth = 0.5f
            borderWidthBottom = 1.5f
            borderColorBottom = PRIMARY
            horizontalAlignment = align
            setPadding(CELL_PADDING)
            paddingTop = CELL_PADDING + 1f
            paddingBottom = CELL_PADDING + 1f
        })
    }

    private fun addSmallHeaderCell(table: PdfPTable, text: String, align: Int) {
        table.addCell(PdfPCell(Phrase(text, TABLE_HEADER)).apply {
            backgroundColor = HEADER_BG
            borderColor = BORDER_LIGHT
            borderWidth = 0.5f
            horizontalAlignment = align
            setPadding(4f)
        })
    }

    private fun addBodyCell(table: PdfPTable, text: String?, font: Font, align: Int, bg: java.awt.Color) {
        table.addCell(PdfPCell(Phrase(text ?: "", font)).apply {
            backgroundColor = bg
            borderColor = BORDER_LIGHT
            borderWidth = 0.5f
            horizontalAlignment = align
            setPadding(CELL_PADDING)
            verticalAlignment = Element.ALIGN_MIDDLE
        })
    }

    private fun addFooterCell(table: PdfPTable, text: String, align: Int) {
        table.addCell(PdfPCell(Phrase(text, TEXT_BOLD)).apply {
            backgroundColor = HEADER_BG
            borderColor = BORDER_LIGHT
            borderWidth = 0.5f
            borderWidthTop = 1.5f
            borderColorTop = PRIMARY
            horizontalAlignment = align
            setPadding(CELL_PADDING)
        })
    }

    private fun addLabelValueRow(table: PdfPTable, label: String, value: String) {
        table.addCell(PdfPCell(Phrase(label, TEXT_FONT)).apply {
            border = Rectangle.NO_BORDER
            setPadding(3f)
        })
        table.addCell(PdfPCell(Phrase(value, TEXT_BOLD)).apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            setPadding(3f)
        })
    }

    @Throws(DocumentException::class)
    private fun addSectionHeader(document: Document, text: String) {
        document.add(Paragraph(text, SECTION_FONT).apply {
            spacingBefore = 8f
            spacingAfter = 2f
        })
    }

    @Throws(DocumentException::class)
    private fun addDivider(document: Document) {
        document.add(PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingBefore(8f)
            setSpacingAfter(4f)
            addCell(PdfPCell().apply {
                border = Rectangle.NO_BORDER
                borderWidthBottom = 0.5f
                borderColorBottom = BORDER_LIGHT
                fixedHeight = 1f
            })
        })
    }

    @Throws(DocumentException::class)
    private fun addSpace(document: Document, height: Float) {
        document.add(Paragraph(" ").apply { spacingBefore = height })
    }

    private fun addSpaceToCell(cell: PdfPCell, height: Float) {
        cell.addElement(Paragraph(" ").apply { spacingBefore = height })
    }

    private fun formatStandardSchluessel(kostenstelle: Kostenstelle?): String {
        val standard = kostenstelle?.standardSchluessel ?: return if (kostenstelle == null) "-" else "Nicht hinterlegt"
        val text = StringBuilder()
        when {
            hasText(standard.name) -> text.append(standard.name?.trim())
            standard.id != null -> text.append("Schluessel #").append(standard.id)
            else -> text.append("Verteilungsschluessel")
        }
        text.append(" (").append(formatSchluesselTyp(standard.typ)).append(")")
        return text.toString()
    }

    private fun formatSchluesselTyp(typ: VerteilungsschluesselTyp?): String =
        when (typ) {
            VerteilungsschluesselTyp.PROZENTUAL -> "Prozentual"
            VerteilungsschluesselTyp.VERBRAUCH -> "Verbrauch"
            VerteilungsschluesselTyp.FLAECHE -> "Flaeche"
            null -> "Unbekannt"
        }

    private fun formatSignedCurrency(value: BigDecimal?, currency: NumberFormat): String {
        val v = value ?: BigDecimal.ZERO
        return when {
            v > BigDecimal.ZERO -> "+" + currency.format(v)
            v < BigDecimal.ZERO -> "-" + currency.format(v.abs())
            else -> currency.format(v)
        }
    }

    private fun formatSignedDecimal(value: BigDecimal?, format: DecimalFormat): String {
        val v = value ?: return "0"
        val formatted = format.format(v.abs())
        return when {
            v > BigDecimal.ZERO -> "+$formatted"
            v < BigDecimal.ZERO -> "-$formatted"
            else -> "0"
        }
    }

    private fun formatNumber(value: BigDecimal?, format: DecimalFormat): String = format.format(value ?: BigDecimal.ZERO)

    private fun buildVerbrauchsgegenstandName(vv: AnnualAccountingResult.Verbrauchsvergleich?, gegenstand: Verbrauchsgegenstand?): String {
        if (gegenstand == null) return "-"
        val name = StringBuilder()
        when {
            hasText(gegenstand.name) -> name.append(gegenstand.name?.trim())
            gegenstand.id != null -> name.append("Gegenstand ").append(gegenstand.id)
        }
        if (vv != null && hasText(vv.raumName)) {
            if (name.isNotEmpty()) name.append(" - ")
            name.append(vv.raumName?.trim())
        }
        return if (name.isEmpty()) "-" else name.toString()
    }

    private fun hasText(value: String?): Boolean = value != null && value.trim().isNotEmpty()

    private fun safeStr(value: String?): String = if (hasText(value)) value!!.trim() else ""

    private fun safe(value: BigDecimal?): BigDecimal = value ?: BigDecimal.ZERO

    private class FooterPageEvent(private val result: AnnualAccountingResult) : PdfPageEventHelper() {
        override fun onEndPage(writer: PdfWriter, document: Document) {
            val footer = PdfPTable(floatArrayOf(2f, 1f))
            footer.totalWidth = document.right() - document.left()
            val name = result.mietobjektName?.takeIf { it.trim().isNotEmpty() }?.trim().orEmpty()
            footer.addCell(PdfPCell(Phrase("Jahresabrechnung ${result.abrechnungsJahr}" + if (name.isEmpty()) "" else " - $name", FOOTER_FONT)).apply {
                border = Rectangle.TOP
                borderColorTop = BORDER_LIGHT
                borderWidthTop = 0.5f
                paddingTop = 6f
                horizontalAlignment = Element.ALIGN_LEFT
            })
            footer.addCell(PdfPCell(Phrase("Seite ${writer.pageNumber}", FOOTER_FONT)).apply {
                border = Rectangle.TOP
                borderColorTop = BORDER_LIGHT
                borderWidthTop = 0.5f
                paddingTop = 6f
                horizontalAlignment = Element.ALIGN_RIGHT
            })
            footer.writeSelectedRows(0, -1, document.left(), document.bottom() - 10f, writer.directContent)
        }
    }

    companion object {
        private val PRIMARY = java.awt.Color(0xDC, 0x26, 0x26)
        private val PRIMARY_DARK = java.awt.Color(0xB9, 0x1C, 0x1C)
        private val HEADER_BG = java.awt.Color(0xFE, 0xF2, 0xF2)
        private val ROW_ALT = java.awt.Color(0xFB, 0xFB, 0xFB)
        private val BORDER_LIGHT = java.awt.Color(0xE5, 0xE7, 0xEB)
        private val TEXT_PRIMARY = java.awt.Color(0x1F, 0x29, 0x37)
        private val TEXT_MUTED = java.awt.Color(0x64, 0x74, 0x8B)
        private val WHITE = java.awt.Color.WHITE
        private val POSITIVE = java.awt.Color(0x16, 0xA3, 0x4A)
        private val NEGATIVE = java.awt.Color(0xDC, 0x26, 0x26)
        private val CARD_BG = java.awt.Color(0xF8, 0xFA, 0xFC)

        private val TITLE_FONT = Font(Font.HELVETICA, 22f, Font.BOLD, PRIMARY_DARK)
        private val SUBTITLE_FONT = Font(Font.HELVETICA, 11f, Font.NORMAL, TEXT_MUTED)
        private val SECTION_FONT = Font(Font.HELVETICA, 13f, Font.BOLD, TEXT_PRIMARY)
        private val SUBSECTION_FONT = Font(Font.HELVETICA, 11f, Font.BOLD, TEXT_PRIMARY)
        private val TABLE_HEADER = Font(Font.HELVETICA, 9f, Font.BOLD, PRIMARY_DARK)
        private val TEXT_FONT = Font(Font.HELVETICA, 9f, Font.NORMAL, TEXT_PRIMARY)
        private val TEXT_SMALL = Font(Font.HELVETICA, 8f, Font.NORMAL, TEXT_MUTED)
        private val TEXT_BOLD = Font(Font.HELVETICA, 9f, Font.BOLD, TEXT_PRIMARY)
        private val LABEL_FONT = Font(Font.HELVETICA, 8f, Font.NORMAL, TEXT_MUTED)
        private val VALUE_FONT = Font(Font.HELVETICA, 14f, Font.BOLD, TEXT_PRIMARY)
        private val VALUE_SMALL_FONT = Font(Font.HELVETICA, 11f, Font.BOLD, TEXT_PRIMARY)
        private val FOOTER_FONT = Font(Font.HELVETICA, 8f, Font.ITALIC, TEXT_MUTED)
        private const val CELL_PADDING = 6f
    }
}
