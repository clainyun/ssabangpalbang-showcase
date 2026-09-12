package com.ssafy.ssabangpalbang.notification.fcm;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FirebaseAdminFcmPushGatewayTest {

    @Test
    void 소유한_FirebaseApp은_close를_반복해도_한_번만_삭제한다() {
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FirebaseAdminFcmPushGateway gateway =
                new FirebaseAdminFcmPushGateway(firebaseApp, true);

        gateway.close();
        gateway.close();

        verify(firebaseApp, times(1)).delete();
    }

    @Test
    void 소유하지_않은_FirebaseApp은_close에서_삭제하지_않는다() {
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FirebaseAdminFcmPushGateway gateway =
                new FirebaseAdminFcmPushGateway(firebaseApp);

        gateway.close();

        verify(firebaseApp, never()).delete();
    }

    @Test
    void FirebaseApp_삭제가_실패하면_다음_close에서_재시도한다() {
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FirebaseAdminFcmPushGateway gateway =
                new FirebaseAdminFcmPushGateway(firebaseApp, true);
        doThrow(new IllegalStateException("temporary failure"))
                .doNothing()
                .when(firebaseApp)
                .delete();

        assertThatThrownBy(gateway::close)
                .isInstanceOf(IllegalStateException.class);
        gateway.close();

        verify(firebaseApp, times(2)).delete();
    }
}
