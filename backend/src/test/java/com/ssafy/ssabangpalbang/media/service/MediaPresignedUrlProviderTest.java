package com.ssafy.ssabangpalbang.media.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaPresignedUrlProviderTest {

    @Test
    void isEnabledDelegatesToCommonMediaProvider() {
        GatewayMediaAccessUrlProvider commonProvider =
                mock(GatewayMediaAccessUrlProvider.class);
        when(commonProvider.isEnabled()).thenReturn(false);
        MediaPresignedUrlProvider provider =
                new MediaPresignedUrlProvider(commonProvider);

        assertThat(provider.isEnabled()).isFalse();
        verify(commonProvider).isEnabled();
    }

    @Test
    void generatesSingleUrlThroughValidatedCommonIssue() {
        GatewayMediaAccessUrlProvider commonProvider =
                mock(GatewayMediaAccessUrlProvider.class);
        when(commonProvider.issue(91L)).thenReturn(
                new MediaAccessUrl(
                        "https://s3/presigned/91",
                        Instant.parse("2026-07-29T09:10:00Z")
                )
        );
        MediaPresignedUrlProvider provider =
                new MediaPresignedUrlProvider(commonProvider);

        String result = provider.generateGetUrl(91L);

        assertThat(result)
                .isEqualTo("https://s3/presigned/91");
        verify(commonProvider).issue(91L);
    }

    @Test
    void generatesBatchUrlsThroughValidatedCommonIssueAll() {
        GatewayMediaAccessUrlProvider commonProvider =
                mock(GatewayMediaAccessUrlProvider.class);
        List<Long> fileIds = List.of(91L, 92L);
        when(commonProvider.issueAll(fileIds)).thenReturn(
                Map.of(
                        91L,
                        new MediaAccessUrl(
                                "https://s3/presigned/91",
                                Instant.parse(
                                        "2026-07-29T09:10:00Z"
                                )
                        )
                )
        );
        MediaPresignedUrlProvider provider =
                new MediaPresignedUrlProvider(commonProvider);

        Map<Long, String> result =
                provider.generateGetUrls(fileIds);

        assertThat(result).containsOnly(
                Map.entry(
                        91L,
                        "https://s3/presigned/91"
                )
        );
        verify(commonProvider).issueAll(fileIds);
    }
}
