package org.example.kalkulationsprogramm.service

import com.lowagie.text.Document
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate

@Service
open class RechnungPdfService {
    data class LayoutDto(
        val page1Rect: RectDto,
        val page2Rect: RectDto,
        val headerRect: RectDto,
        val footerRect: RectDto,
        val logoPath: String?,
    ) {
        fun page1Rect(): RectDto = page1Rect
        fun page2Rect(): RectDto = page2Rect
        fun headerRect(): RectDto = headerRect
        fun footerRect(): RectDto = footerRect
        fun logoPath(): String? = logoPath
    }

    data class RectDto(
        val llx: Float,
        val lly: Float,
        val urx: Float,
        val ury: Float,
    ) {
        fun llx(): Float = llx
        fun lly(): Float = lly
        fun urx(): Float = urx
        fun ury(): Float = ury
        fun toRectangle(): Rectangle = Rectangle(llx, lly, urx, ury)
    }

    data class RechnungDto(
        val layout: LayoutDto,
        val kopfdaten: KopfdatenDto,
        val contentBlocks: List<ContentBlockDto>?,
        val formBlocks: List<FormBlockDto>?,
        val schlusstext: String?,
        val backgroundImagePage1: String?,
        val backgroundImagePage2: String?,
        val globalRabattProzent: BigDecimal? = null,
        val abrechnungsverlauf: AbrechnungsverlaufPdfDto? = null,
        val betragNetto: BigDecimal? = null,
        val abschlagInfo: AbschlagInfoPdfDto? = null,
    ) {
        fun layout(): LayoutDto = layout
        fun kopfdaten(): KopfdatenDto = kopfdaten
        fun contentBlocks(): List<ContentBlockDto>? = contentBlocks
        fun formBlocks(): List<FormBlockDto>? = formBlocks
        fun schlusstext(): String? = schlusstext
        fun backgroundImagePage1(): String? = backgroundImagePage1
        fun backgroundImagePage2(): String? = backgroundImagePage2
        fun globalRabattProzent(): BigDecimal? = globalRabattProzent
        fun abrechnungsverlauf(): AbrechnungsverlaufPdfDto? = abrechnungsverlauf
        fun betragNetto(): BigDecimal? = betragNetto
        fun abschlagInfo(): AbschlagInfoPdfDto? = abschlagInfo
    }

    data class AbrechnungsverlaufPdfDto(
        val basisdokumentNummer: String?,
        val basisdokumentTyp: String?,
        val basisdokumentDatum: LocalDate?,
        val basisdokumentBetragNetto: BigDecimal?,
        val positionen: List<AbrechnungspositionPdfDto>?,
    ) {
        fun basisdokumentNummer(): String? = basisdokumentNummer
        fun basisdokumentTyp(): String? = basisdokumentTyp
        fun basisdokumentDatum(): LocalDate? = basisdokumentDatum
        fun basisdokumentBetragNetto(): BigDecimal? = basisdokumentBetragNetto
        fun positionen(): List<AbrechnungspositionPdfDto>? = positionen
    }

    data class AbrechnungspositionPdfDto(
        val dokumentNummer: String?,
        val typ: String?,
        val datum: LocalDate?,
        val betragNetto: BigDecimal?,
        val abschlagsNummer: Int?,
    ) {
        fun dokumentNummer(): String? = dokumentNummer
        fun typ(): String? = typ
        fun datum(): LocalDate? = datum
        fun betragNetto(): BigDecimal? = betragNetto
        fun abschlagsNummer(): Int? = abschlagsNummer
    }

    data class AbschlagInfoPdfDto(
        val modus: String?,
        val eingabeWert: BigDecimal?,
    ) {
        fun modus(): String? = modus
        fun eingabeWert(): BigDecimal? = eingabeWert
    }

    data class ContentBlockDto(
        val type: String?,
        val text: String?,
        val fett: Boolean,
        val fontSize: Int,
        val pos: String?,
        val beschreibung: String?,
        val beschreibungHtml: String?,
        val menge: BigDecimal?,
        val einheit: String?,
        val einzelpreis: BigDecimal?,
        val gesamt: BigDecimal?,
        val optional: Boolean,
        val sectionLabel: String?,
        val rabattProzent: BigDecimal?,
    ) {
        fun type(): String? = type
        fun text(): String? = text
        fun fett(): Boolean = fett
        fun fontSize(): Int = fontSize
        fun pos(): String? = pos
        fun beschreibung(): String? = beschreibung
        fun beschreibungHtml(): String? = beschreibungHtml
        fun menge(): BigDecimal? = menge
        fun einheit(): String? = einheit
        fun einzelpreis(): BigDecimal? = einzelpreis
        fun gesamt(): BigDecimal? = gesamt
        fun optional(): Boolean = optional
        fun sectionLabel(): String? = sectionLabel
        fun rabattProzent(): BigDecimal? = rabattProzent
        fun isText(): Boolean = type == "TEXT"
        fun isService(): Boolean = type == "SERVICE"
        fun isSeparator(): Boolean = type == "SEPARATOR"
        fun isSectionHeader(): Boolean = type == "SECTION_HEADER"
        fun isSubtotal(): Boolean = type == "SUBTOTAL"
    }

    data class KopfdatenDto(
        val rechnungsnummer: String?,
        val rechnungsDatum: LocalDate?,
        val leistungsDatum: LocalDate?,
        val kundenName: String?,
        val kundenAdresse: String?,
        val betreff: String?,
        val kundennummer: String?,
        val dokumentTyp: String?,
        val bezugsdokument: String?,
        val projektnummer: String?,
        val bauvorhaben: String?,
        val bezugsdokumentTyp: String? = null,
        val bezugsdokumentDatum: String? = null,
        val zahlungszielTage: Int? = null,
    ) {
        fun rechnungsnummer(): String? = rechnungsnummer
        fun rechnungsDatum(): LocalDate? = rechnungsDatum
        fun leistungsDatum(): LocalDate? = leistungsDatum
        fun kundenName(): String? = kundenName
        fun kundenAdresse(): String? = kundenAdresse
        fun betreff(): String? = betreff
        fun kundennummer(): String? = kundennummer
        fun dokumentTyp(): String? = dokumentTyp
        fun bezugsdokument(): String? = bezugsdokument
        fun projektnummer(): String? = projektnummer
        fun bauvorhaben(): String? = bauvorhaben
        fun bezugsdokumentTyp(): String? = bezugsdokumentTyp
        fun bezugsdokumentDatum(): String? = bezugsdokumentDatum
        fun zahlungszielTage(): Int? = zahlungszielTage
    }

    data class FormBlockDto(
        val id: String?,
        val type: String?,
        val page: Int?,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val content: String?,
        val styles: Map<String, Any>?,
    ) {
        fun id(): String? = id
        fun type(): String? = type
        fun page(): Int? = page
        fun x(): Float = x
        fun y(): Float = y
        fun width(): Float = width
        fun height(): Float = height
        fun content(): String? = content
        fun styles(): Map<String, Any>? = styles
    }

    open fun generatePdf(data: RechnungDto, out: OutputStream) {
        val document = Document(PageSize.A4)
        PdfWriter.getInstance(document, out)
        document.open()
        document.add(Paragraph(data.kopfdaten.dokumentTyp ?: "Dokument"))
        data.kopfdaten.rechnungsnummer?.let { document.add(Paragraph(it)) }
        data.kopfdaten.kundenName?.let { document.add(Paragraph(it)) }
        data.contentBlocks.orEmpty().forEach { block ->
            val text = block.text ?: block.beschreibung ?: block.sectionLabel
            if (!text.isNullOrBlank()) document.add(Paragraph(text.replace(Regex("<[^>]+>"), " ")))
        }
        data.schlusstext?.takeIf { it.isNotBlank() }?.let { document.add(Paragraph(it.replace(Regex("<[^>]+>"), " "))) }
        document.close()
    }

    open fun generatePdfBytes(data: RechnungDto): ByteArray =
        ByteArrayOutputStream().use {
            generatePdf(data, it)
            it.toByteArray()
        }

    companion object {
        @JvmStatic
        fun getDefaultLayout(): LayoutDto =
            LayoutDto(
                RectDto(50f, 120f, 550f, 600f),
                RectDto(50f, 50f, 550f, 780f),
                RectDto(50f, 750f, 550f, 840f),
                RectDto(50f, 20f, 550f, 100f),
                null,
            )

        @JvmStatic
        fun convertFormBlockToRect(block: FormBlockDto, pageWidthPt: Float, pageHeightPt: Float): RectDto {
            val llx = block.x
            val urx = block.x + block.width
            val ury = pageHeightPt - block.y
            val lly = ury - block.height
            return RectDto(llx, lly, urx, ury)
        }

        @JvmStatic
        fun createLayoutFromFormBlocks(
            blocks: List<FormBlockDto>?,
            pageWidthPt: Float,
            pageHeightPt: Float,
        ): LayoutDto {
            val page1 = blocks.orEmpty().firstOrNull { it.type == "table" && (it.page == null || it.page == 1) }
                ?.let { convertFormBlockToRect(it, pageWidthPt, pageHeightPt) }
                ?: RectDto(50f, 120f, 550f, 600f)
            val page2 = blocks.orEmpty().firstOrNull { it.type == "table" && it.page == 2 }
                ?.let { convertFormBlockToRect(it, pageWidthPt, pageHeightPt) }
                ?: RectDto(50f, 50f, 550f, 780f)
            return LayoutDto(page1, page2, RectDto(50f, 750f, 550f, 840f), RectDto(50f, 20f, 550f, 100f), null)
        }
    }
}
