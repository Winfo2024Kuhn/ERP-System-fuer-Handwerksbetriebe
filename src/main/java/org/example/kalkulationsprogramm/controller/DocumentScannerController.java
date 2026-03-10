package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.example.kalkulationsprogramm.service.GeminiScannerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentScannerController {

    private final GeminiScannerService geminiService;

    // AI Cloud Scan removed as per request to focus on Manual Scan + AI Naming.

    @PostMapping("/scan-manual")
    public ResponseEntity<byte[]> scanManual(@RequestParam("image") MultipartFile imageFile) {
        try {
            log.info("Received MANUAL scan request: {} bytes", imageFile.getSize());
            byte[] imageBytes = imageFile.getBytes();

            // 1. Generate Filename (AI)
            // User requested: "bevor dokument gespeichert wird soll es an gemini geschickt
            // werden"
            String filename = geminiService.generateFilename(imageBytes);
            log.info("Generated filename: {}", filename);

            // 2. Direct PDF conversion
            byte[] pdfBytes = createPdfFromImage(imageBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Manual Scan failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private byte[] createPdfFromImage(byte[] imageBytes) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageBytes, "scan");

            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                // Scale image to fit A4
                float scale = Math.min(PDRectangle.A4.getWidth() / image.getWidth(),
                        PDRectangle.A4.getHeight() / image.getHeight());

                float w = image.getWidth() * scale;
                float h = image.getHeight() * scale;

                contentStream.drawImage(image, 0, 0, w, h);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
