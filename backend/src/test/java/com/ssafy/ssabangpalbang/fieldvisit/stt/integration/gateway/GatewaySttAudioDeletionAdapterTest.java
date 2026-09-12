package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.gateway;

import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewaySttAudioDeletionAdapterTest {

    private final MediaGatewayClient client =
            mock(MediaGatewayClient.class);
    private final GatewaySttAudioDeletionAdapter adapter =
            new GatewaySttAudioDeletionAdapter(client);

    @Test
    void deletesSttAudioThroughGateway() {
        when(client.delete(
                FileUsage.STT_AUDIO,
                "stt/audio.webm"
        )).thenReturn(new MediaGatewayClient.DeleteResponse(
                true,
                FileUsage.STT_AUDIO,
                "stt/audio.webm"
        ));

        adapter.deleteAudio(90L, "stt/audio.webm");

        verify(client).delete(
                FileUsage.STT_AUDIO,
                "stt/audio.webm"
        );
    }

    @Test
    void rejectsBlankObjectKeyBeforeGatewayCall() {
        assertThatThrownBy(() -> adapter.deleteAudio(90L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey");
        verifyNoInteractions(client);
    }
}
