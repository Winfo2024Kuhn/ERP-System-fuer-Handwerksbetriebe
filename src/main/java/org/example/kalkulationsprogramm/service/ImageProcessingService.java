package org.example.kalkulationsprogramm.service;

import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImageProcessingService {

    static {
        // Load Native Libraries
        try {
            OpenCV.loadLocally();
            log.info("OpenCV loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load OpenCV", e);
        }
    }

    // Legacy AI cropping logic removed. 
    // Manual scanner performs cropping on the client side.
}
