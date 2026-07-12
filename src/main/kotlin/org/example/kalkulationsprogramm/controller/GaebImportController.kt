package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.service.GaebImportService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/import")
class GaebImportController(private val gaebImportService: GaebImportService) {
    private val log = LoggerFactory.getLogger(GaebImportController::class.java)

    @PostMapping("/gaeb")
    fun importGaeb(@RequestParam("file") file: MultipartFile): ResponseEntity<List<Map<String, Any>>> {
        if (file.isEmpty) return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(gaebImportService.parseGaebXml(file.inputStream))
        } catch (e: Exception) {
            log.error("GAEB import failed for file {}", file.originalFilename, e)
            ResponseEntity.internalServerError().build()
        }
    }
}
