package com.codescope.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);
    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream serviceAccount = resourceLoader.getResource("classpath:firebase-service-account.json").getInputStream()) {

                final FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setStorageBucket("codescope.appspot.com")
                        .build();

                FirebaseApp.initializeApp(options);
                logger.info("✅ Firebase initialized successfully!");
            } catch (final IOException e) {
                logger.error("❌ Error initializing Firebase: {}", e.getMessage());
                throw e; // Re-throw to indicate a critical startup failure
            }
        } else {
            logger.info("ℹ️ Firebase already initialized, reusing existing app.");
        }
    }
}
