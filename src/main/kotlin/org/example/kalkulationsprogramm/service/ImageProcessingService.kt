package org.example.kalkulationsprogramm.service

import nu.pattern.OpenCV
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ImageProcessingService {
    companion object {
        private val log = LoggerFactory.getLogger(ImageProcessingService::class.java)

        init {
            try {
                OpenCV.loadLocally()
                log.info("OpenCV loaded successfully")
            } catch (e: Throwable) {
                log.error("Failed to load OpenCV", e)
            }
        }
    }
}
