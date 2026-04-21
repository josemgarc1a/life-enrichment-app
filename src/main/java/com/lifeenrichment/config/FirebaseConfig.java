package com.lifeenrichment.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Spring configuration for the Firebase Admin SDK.
 *
 * <p>Initializes {@link FirebaseApp} once and exposes a {@link FirebaseMessaging} bean
 * so that channel adapters can receive it via constructor injection (and tests can mock it).
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    /**
     * Initializes the Firebase Admin SDK and returns the default {@link FirebaseMessaging} instance.
     *
     * @return the singleton {@link FirebaseMessaging} for FCM delivery
     * @throws IOException if the service-account credentials file cannot be read
     */
    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccount = new FileInputStream(credentialsPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized from {}", credentialsPath);
        } else {
            log.debug("Firebase Admin SDK already initialized; skipping re-init.");
        }
        return FirebaseMessaging.getInstance();
    }
}
