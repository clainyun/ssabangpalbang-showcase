package com.ssafy.ssabangpalbang.notification.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class FcmFirebaseConfiguration {

    static final String FIREBASE_APP_NAME =
            "ssabangpalbang-fcm";
    private static final Logger log = LoggerFactory.getLogger(
            FcmFirebaseConfiguration.class
    );

    @Bean(destroyMethod = "close")
    public FcmPushGateway fcmPushGateway(FcmProperties properties) {
        if (!properties.isConfigured()) {
            return new DisabledFcmPushGateway();
        }

        try {
            Path serviceAccountPath = Path.of(properties.serviceAccountPath());
            if (!Files.isRegularFile(serviceAccountPath)) {
                log.warn("FCM gateway is disabled because its service account file is unavailable.");
                return new DisabledFcmPushGateway();
            }

            FirebaseOptions options = loadFirebaseOptions(serviceAccountPath);
            FirebaseApp firebaseApp = initializeFirebaseApp(options);
            return new FirebaseAdminFcmPushGateway(firebaseApp, true);
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "FCM gateway is disabled because configuration could not be initialized: errorType={}",
                    exception.getClass().getSimpleName()
            );
            return new DisabledFcmPushGateway();
        }
    }

    FirebaseOptions loadFirebaseOptions(Path serviceAccountPath)
            throws IOException {
        try (InputStream inputStream = Files.newInputStream(serviceAccountPath)) {
            return FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .build();
        }
    }

    FirebaseApp initializeFirebaseApp(FirebaseOptions options) {
        return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
    }

    @Bean
    public ThreadPoolTaskScheduler fcmTestPushTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("fcm-test-push-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(10);
        return taskScheduler;
    }
}
