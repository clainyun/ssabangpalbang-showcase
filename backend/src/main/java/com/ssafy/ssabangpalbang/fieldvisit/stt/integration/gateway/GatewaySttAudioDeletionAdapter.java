package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.gateway;

import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.media.gateway",
        name = "enabled",
        havingValue = "true"
)
public class GatewaySttAudioDeletionAdapter
        implements SttAudioDeletionPort {

    private final MediaGatewayClient mediaGatewayClient;

    @Override
    public void deleteAudio(Long audioFileId, String objectKey) {
        if (audioFileId == null || audioFileId < 1) {
            throw new IllegalArgumentException(
                    "삭제할 STT audioFileId가 올바르지 않습니다."
            );
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "삭제할 STT objectKey가 비어 있습니다."
            );
        }
        MediaGatewayClient.DeleteResponse response =
                mediaGatewayClient.delete(
                        FileUsage.STT_AUDIO,
                        objectKey
                );
        if (!response.deleted()
                || response.fileUsage() != FileUsage.STT_AUDIO
                || !objectKey.equals(response.s3Key())) {
            throw new IllegalStateException(
                    "STT audio deletion was not confirmed by the media gateway"
            );
        }
    }
}
