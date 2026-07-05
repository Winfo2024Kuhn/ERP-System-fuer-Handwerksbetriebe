package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.util.Optional

@Service
open class GeminiDokumentAnalyseService {
    fun analysiereDokument(dokument: LieferantDokument): LieferantGeschaeftsdokument = LieferantGeschaeftsdokument()
    fun reanalysiereDokumentById(dokumentId: Long): LieferantGeschaeftsdokument = LieferantGeschaeftsdokument()
    fun analyzeAsync(dokumentId: Long) {}
    fun analyzeFile(file: MultipartFile): LieferantDokumentDto.AnalyzeResponse = LieferantDokumentDto.AnalyzeResponse()
    fun analyzeFile(file: MultipartFile, customPrompt: String?): LieferantDokumentDto.AnalyzeResponse = LieferantDokumentDto.AnalyzeResponse()
    fun analyzeFile(file: Path, originalDateiname: String?): LieferantDokumentDto.AnalyzeResponse = LieferantDokumentDto.AnalyzeResponse()
    fun analyzeFile(file: Path, originalDateiname: String?, useProModel: Boolean): LieferantDokumentDto.AnalyzeResponse = LieferantDokumentDto.AnalyzeResponse()

    fun analyzeFileForMultipleInvoices(file: MultipartFile): MutableList<LieferantDokumentDto.MultiInvoiceAnalyzeResponse> = mutableListOf()
    fun analyzeFileForMultipleInvoices(file: Path, originalDateiname: String?): MutableList<LieferantDokumentDto.MultiInvoiceAnalyzeResponse> = mutableListOf()
    fun rufGeminiApiMitPrompt(bytes: ByteArray, mimeType: String?, customPrompt: String?): String =
        rufGeminiApiMitPrompt(bytes, mimeType, customPrompt, false)
    fun rufGeminiApiMitPrompt(bytes: ByteArray, mimeType: String?, customPrompt: String?, useProModel: Boolean): String = ""
    fun analysiereDokument(dokument: LieferantDokument, explicitPath: Path): LieferantGeschaeftsdokument = LieferantGeschaeftsdokument()
    fun analyzeAndReturnData(dateiPfad: Path, originalDateiname: String?): LieferantGeschaeftsdokument = LieferantGeschaeftsdokument()
    fun findeLieferantByEmailDomain(emailAddress: String?): Optional<Lieferanten> = Optional.empty()
    fun performRelink(dokument: LieferantDokument) {}
    fun performRelink(dokument: LieferantDokument, alleDokumente: List<LieferantDokument>?) {}
    fun relinkAlleDokumente(): Int = 0
    fun relinkDokumenteByLieferant(lieferantId: Long): Int = 0

}

@Suppress("UNCHECKED_CAST")
fun <T> LieferantDokumentDto.AnalyzeResponse.value(key: String): T? =
    when (key) {
        "dokumentTyp" -> dokumentTyp
        "dokumentNummer" -> dokumentNummer
        "dokumentDatum" -> dokumentDatum
        "betragNetto" -> betragNetto
        "betragBrutto" -> betragBrutto
        "mwstSatz" -> mwstSatz
        "zahlungsziel" -> zahlungsziel
        "bestellnummer" -> bestellnummer
        "referenzNummer" -> referenzNummer
        "skontoTage" -> skontoTage
        "skontoProzent" -> skontoProzent
        "nettoTage" -> nettoTage
        "bereitsGezahlt" -> bereitsGezahlt
        "zahlungsart" -> zahlungsart
        "aiConfidence" -> aiConfidence
        "lieferantName" -> lieferantName
        else -> null
    } as T?
