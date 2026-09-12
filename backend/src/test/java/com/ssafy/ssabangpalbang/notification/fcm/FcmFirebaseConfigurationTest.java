package com.ssafy.ssabangpalbang.notification.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class FcmFirebaseConfigurationTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void default_App과_무관하게_전용_named_App을_초기화한다()
            throws IOException {
        Path serviceAccount = Files.createFile(
                tempDirectory.resolve("service-account.json")
        );
        FirebaseOptions options = mock(FirebaseOptions.class);
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FcmFirebaseConfiguration configuration =
                spy(new FcmFirebaseConfiguration());
        doReturn(options)
                .when(configuration)
                .loadFirebaseOptions(serviceAccount);

        try (MockedStatic<FirebaseApp> firebaseApps =
                     mockStatic(FirebaseApp.class)) {
            firebaseApps.when(() -> FirebaseApp.initializeApp(
                            options,
                            FcmFirebaseConfiguration.FIREBASE_APP_NAME
                    ))
                    .thenReturn(firebaseApp);

            FcmPushGateway gateway = configuration.fcmPushGateway(
                    new FcmProperties(true, serviceAccount.toString())
            );

            assertThat(gateway)
                    .isInstanceOf(FirebaseAdminFcmPushGateway.class);
            firebaseApps.verify(() -> FirebaseApp.initializeApp(
                    options,
                    FcmFirebaseConfiguration.FIREBASE_APP_NAME
            ));

            gateway.close();
            verify(firebaseApp).delete();
        }
    }

    @Test
    void named_App_초기화가_실패하면_gateway를_비활성화한다()
            throws IOException {
        Path serviceAccount = Files.createFile(
                tempDirectory.resolve("invalid-service-account.json")
        );
        FirebaseOptions options = mock(FirebaseOptions.class);
        FcmFirebaseConfiguration configuration =
                spy(new FcmFirebaseConfiguration());
        doReturn(options)
                .when(configuration)
                .loadFirebaseOptions(serviceAccount);

        try (MockedStatic<FirebaseApp> firebaseApps =
                     mockStatic(FirebaseApp.class)) {
            firebaseApps.when(() -> FirebaseApp.initializeApp(
                            options,
                            FcmFirebaseConfiguration.FIREBASE_APP_NAME
                    ))
                    .thenThrow(new IllegalStateException("duplicate app"));

            FcmPushGateway gateway = configuration.fcmPushGateway(
                    new FcmProperties(true, serviceAccount.toString())
            );

            assertThat(gateway).isInstanceOf(DisabledFcmPushGateway.class);
        }
    }
}
