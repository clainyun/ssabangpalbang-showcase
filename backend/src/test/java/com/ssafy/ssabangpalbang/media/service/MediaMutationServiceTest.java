package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.config.MediaGatewayProperties;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.dto.request.MediaPrepareRequest;
import com.ssafy.ssabangpalbang.media.dto.response.MediaPrepareResponse;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayException;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaMutationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-30T03:00:00Z");

    private FileMetaRepository repository;
    private MediaGatewayClient client;
    private MediaMutationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(FileMetaRepository.class);
        StudyRepository studyRepository = mock(StudyRepository.class);
        StudyMemberRepository memberRepository =
                mock(StudyMemberRepository.class);
        client = mock(MediaGatewayClient.class);
        ObjectProvider<MediaGatewayClient> clientProvider =
                mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(repository.save(any(FileMeta.class))).thenAnswer(invocation -> {
            FileMeta file = invocation.getArgument(0);
            ReflectionTestUtils.setField(file, "id", 99L);
            return file;
        });
        service = new MediaMutationService(
                repository,
                studyRepository,
                memberRepository,
                clientProvider,
                new MediaGatewayProperties(
                        true,
                        "https://gateway.example",
                        "token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        Duration.ofHours(24)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void prepareStoresPendingKeyButReturnsOnlyUploadUrl() {
        when(client.createUploadUrl(
                FileUsage.POST_ATTACHMENT,
                "image/jpeg",
                100L
        )).thenReturn(new MediaGatewayClient.UploadUrlResponse(
                FileUsage.POST_ATTACHMENT,
                "pending/post/temp.jpg",
                "https://signed.example/upload",
                "PUT",
                Map.of("Content-Type", "image/jpeg"),
                100L,
                NOW.plusSeconds(300)
        ));

        MediaPrepareResponse response = service.prepare(
                10L,
                new MediaPrepareRequest(
                        FileUsage.POST_ATTACHMENT,
                        "photo.jpg",
                        "image/jpeg",
                        100L,
                        null
                )
        );

        assertThat(response.fileId()).isEqualTo(99L);
        assertThat(response.uploadStatus())
                .isEqualTo(UploadStatus.PENDING);
        assertThat(response.uploadUrl())
                .isEqualTo("https://signed.example/upload");

        ArgumentCaptor<FileMeta> captor =
                ArgumentCaptor.forClass(FileMeta.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getS3Key())
                .isEqualTo("pending/post/temp.jpg");
    }

    @Test
    void completePersistsVerifiedFinalKeyAndCompletedStatus() {
        FileMeta file = pendingFile();
        when(repository.findByIdForUpdate(99L))
                .thenReturn(Optional.of(file));
        when(client.verify(
                FileUsage.POST_ATTACHMENT,
                "pending/post/temp.jpg",
                "image/jpeg",
                100L
        )).thenReturn(new MediaGatewayClient.VerifyResponse(
                true,
                FileUsage.POST_ATTACHMENT,
                "post-attachment/2026/07/final.jpg",
                "image/jpeg",
                100L,
                "\"etag\""
        ));

        MediaMutationService.CompletionResult result =
                service.complete(10L, 99L);

        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(file.getUploadStatus())
                .isEqualTo(UploadStatus.COMPLETED);
        assertThat(file.getS3Key())
                .isEqualTo("post-attachment/2026/07/final.jpg");
    }

    @Test
    void missingUploadObjectStaysPendingForSafeRetry() {
        FileMeta file = pendingFile();
        when(repository.findByIdForUpdate(99L))
                .thenReturn(Optional.of(file));
        when(client.verify(
                FileUsage.POST_ATTACHMENT,
                "pending/post/temp.jpg",
                "image/jpeg",
                100L
        )).thenThrow(new MediaGatewayException(
                MediaGatewayException.Failure.NOT_FOUND
        ));

        assertThatThrownBy(() -> service.complete(10L, 99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEDIA_OBJECT_NOT_FOUND
                                )
                );
        assertThat(file.getUploadStatus())
                .isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void changedUploadFailsFileWithReuploadError() {
        FileMeta file = pendingFile();
        when(repository.findByIdForUpdate(99L))
                .thenReturn(Optional.of(file));
        when(client.verify(
                FileUsage.POST_ATTACHMENT,
                "pending/post/temp.jpg",
                "image/jpeg",
                100L
        )).thenThrow(new MediaGatewayException(
                MediaGatewayException.Failure.CHANGED
        ));

        // 형식 오류(MEDIA_CONTENT_TYPE_INVALID)로 뭉개지 않고 재업로드 오류로
        // 구분하며, 게이트웨이가 pending 객체를 이미 지웠으므로 파일은 FAILED.
        assertThatThrownBy(() -> service.complete(10L, 99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEDIA_UPLOAD_CHANGED
                                )
                );
        assertThat(file.getUploadStatus())
                .isEqualTo(UploadStatus.FAILED);
    }

    private FileMeta pendingFile() {
        FileMeta file = FileMeta.pending(
                10L,
                null,
                FileUsage.POST_ATTACHMENT,
                "photo.jpg",
                "pending/post/temp.jpg",
                "image/jpeg",
                100L,
                null,
                NOW
        );
        ReflectionTestUtils.setField(file, "id", 99L);
        return file;
    }
}
