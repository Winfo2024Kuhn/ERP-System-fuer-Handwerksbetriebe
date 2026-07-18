package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto
import org.example.kalkulationsprogramm.dto.ContactDto
import org.example.kalkulationsprogramm.dto.EmailThreadDto
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService
import org.example.kalkulationsprogramm.service.InquiryDetectionService
import org.example.kalkulationsprogramm.service.SpamFilterService
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/emails")
class UnifiedEmailController {
    fun downloadAttachment(emailId: Long, attachmentId: Long): ResponseEntity<Resource> = ResponseEntity.notFound().build()

    @PostMapping("/import")
    fun triggerImport(): ResponseEntity<String> = ResponseEntity.ok("0")

    @GetMapping("/{id}")
    fun getEmailById(@PathVariable id: Long): ResponseEntity<UnifiedEmailDto> = ResponseEntity.notFound().build()

    @GetMapping("/{emailId}/thread")
    fun getEmailThread(@PathVariable emailId: Long): ResponseEntity<EmailThreadDto> = ResponseEntity.notFound().build()

    fun getUnassignedEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getInquiryEmails(@RequestParam(value = "limit", defaultValue = "100") limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getNewProjektEmails(@RequestParam(value = "limit", defaultValue = "100") limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getNewAnfrageEmails(@RequestParam(value = "limit", defaultValue = "100") limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getNewLieferantEmails(@RequestParam(value = "limit", defaultValue = "100") limit: Int): List<UnifiedEmailDto> = emptyList()
    fun searchEmails(q: String?): List<UnifiedEmailDto> = emptyList()
    fun getInboxEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getProjectFolderEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getOfferFolderEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getSupplierFolderEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getTaxAdvisorFolderEmails(
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "limit", defaultValue = "100") limit: Int,
    ): List<UnifiedEmailDto> = emptyList()

    fun getSentEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getTrashEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getSpamEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getNewsletterEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getStarredEmails(limit: Int): List<UnifiedEmailDto> = emptyList()
    fun getStats(): FolderStatsDto = FolderStatsDto()
    fun scanSpamRetroactive(): SpamFilterService.ScanResult = SpamFilterService.ScanResult(0, 0, 0)
    fun scanInquiriesRetroactive(): InquiryDetectionService.ScanResult = InquiryDetectionService.ScanResult(0, 0, 0)
    fun searchContacts(@RequestParam("q") query: String): List<ContactDto> = emptyList()
    fun getPossibleAssignments(id: Long): ResponseEntity<EmailAutoAssignmentService.PossibleAssignments> = ResponseEntity.notFound().build()

    data class MoveToFolderRequest(val ids: List<Long>?, val targetFolder: String?)

    companion object {
        @JvmStatic
        fun extractFirstEmailAddress(raw: String?): String? = raw?.trim()?.takeIf { it.contains("@") }
    }
}
