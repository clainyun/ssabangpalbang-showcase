package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.config.MediaGatewayProperties;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayMediaAccessUrlProviderTest {

    private static final Instant NOW =
            Instant.parse("2026-07-30T03:00:00Z");

    private FileMetaRepository repository;
    private MediaGatewayClient client;
    private ObjectProvider<MediaGatewayClient> clientProvider;
    private GatewayMediaAccessUrlProvider provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(FileMetaRepository.class);
        client = mock(MediaGatewayClient.class);
        clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        provider = new GatewayMediaAccessUrlProvider(
                repository,
                clientProvider,
                properties(true),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void issuesDownloadUrlThroughGatewayWithoutExposingObjectKey() {
        FileMeta file = completedFile(401L);
        when(repository.findById(401L)).thenReturn(Optional.of(file));
        when(client.createDownloadUrl(
                FileUsage.FIELD_PHOTO,
                "photo/2026/07/final.jpg"
        )).thenReturn(new MediaGatewayClient.DownloadUrlResponse(
                FileUsage.FIELD_PHOTO,
                "photo/2026/07/final.jpg",
                "https://signed.example/photo",
                NOW.plusSeconds(600)
        ));

        MediaAccessUrl result = provider.issue(401L);

        assertThat(result.url()).isEqualTo(
                "https://signed.example/photo"
        );
        assertThat(result.expiresAt())
                .isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void rejectsPendingOrDeletedFilesBeforeCallingGateway() {
        FileMeta pending = completedFile(401L);
        ReflectionTestUtils.setField(
                pending,
                "uploadStatus",
                UploadStatus.PENDING
        );
        when(repository.findById(401L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> provider.issue(401L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_ACCESS_DENIED)
                );
        verifyNoInteractions(client);
    }

    @Test
    void disabledProviderReturnsNoBatchUrls() {
        GatewayMediaAccessUrlProvider disabled =
                new GatewayMediaAccessUrlProvider(
                        repository,
                        clientProvider,
                        properties(false),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        assertThat(disabled.issueAll(List.of(401L))).isEmpty();
        verifyNoInteractions(repository, client);
    }

    @Test
    void batchSkipsUnavailableMetadata() {
        FileMeta available = completedFile(401L);
        FileMeta deleted = completedFile(402L);
        ReflectionTestUtils.setField(deleted, "deletedAt", NOW);
        when(repository.findAllById(List.of(401L, 402L)))
                .thenReturn(List.of(available, deleted));
        when(client.createDownloadUrl(
                FileUsage.FIELD_PHOTO,
                "photo/2026/07/final.jpg"
        )).thenReturn(new MediaGatewayClient.DownloadUrlResponse(
                FileUsage.FIELD_PHOTO,
                "photo/2026/07/final.jpg",
                "https://signed.example/photo",
                NOW.plusSeconds(600)
        ));

        Map<Long, MediaAccessUrl> result =
                provider.issueAll(List.of(401L, 402L));

        assertThat(result).containsOnlyKeys(401L);
    }

    private FileMeta completedFile(Long id) {
        FileMeta file = FileMeta.pending(
                10L,
                7L,
                FileUsage.FIELD_PHOTO,
                "photo.jpg",
                "pending/photo/temp.jpg",
                "image/jpeg",
                100L,
                null,
                NOW.minusSeconds(60)
        );
        file.complete(
                "photo/2026/07/final.jpg",
                "image/jpeg",
                100L,
                null
        );
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    private MediaGatewayProperties properties(boolean enabled) {
        return new MediaGatewayProperties(
                enabled,
                enabled ? "https://gateway.example" : "",
                enabled ? "token" : "",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofHours(24)
        );
    }
}
