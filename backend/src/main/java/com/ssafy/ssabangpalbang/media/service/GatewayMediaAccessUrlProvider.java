package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.config.MediaGatewayProperties;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayException;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GatewayMediaAccessUrlProvider implements MediaAccessUrlProvider {

    private final FileMetaRepository fileMetaRepository;
    private final ObjectProvider<MediaGatewayClient> clientProvider;
    private final MediaGatewayProperties properties;
    private final Clock clock;

    @Override
    public MediaAccessUrl issue(Long fileId) {
        MediaGatewayClient client = requiredClient();
        FileMeta file = fileMetaRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", fileId)
                ));
        validateAccessible(file, fileId, clock.instant());
        return download(client, file);
    }

    @Override
    public Map<Long, MediaAccessUrl> issueAll(Collection<Long> fileIds) {
        MediaGatewayClient client = clientProvider.getIfAvailable();
        if (!properties.enabled() || client == null || fileIds.isEmpty()) {
            return Map.of();
        }

        Instant now = clock.instant();
        Map<Long, MediaAccessUrl> result = new LinkedHashMap<>();
        for (FileMeta file : fileMetaRepository.findAllById(fileIds)) {
            if (isAccessible(file, now)) {
                try {
                    result.put(file.getId(), download(client, file));
                } catch (BusinessException ignored) {
                    // A failed URL for one attachment must not break a list response.
                }
            }
        }
        return Map.copyOf(result);
    }

    public boolean isEnabled() {
        return properties.enabled()
                && clientProvider.getIfAvailable() != null;
    }

    private MediaAccessUrl download(
            MediaGatewayClient client,
            FileMeta file
    ) {
        try {
            MediaGatewayClient.DownloadUrlResponse response =
                    client.createDownloadUrl(
                            file.getFileUsage(),
                            file.getS3Key()
                    );
            Instant now = clock.instant();
            if (response.fileUsage() != file.getFileUsage()
                    || !file.getS3Key().equals(response.s3Key())
                    || response.downloadUrl() == null
                    || response.downloadUrl().isBlank()
                    || response.expiresAt() == null
                    || !response.expiresAt().isAfter(now)
                    || (file.getExpiresAt() != null
                    && response.expiresAt().isAfter(
                            file.getExpiresAt()
                    ))
                    || !isSecureHttpUrl(response.downloadUrl())) {
                throw new BusinessException(
                        ErrorCode.MEDIA_GATEWAY_UNAVAILABLE
                );
            }
            return new MediaAccessUrl(
                    response.downloadUrl(),
                    response.expiresAt()
            );
        } catch (MediaGatewayException exception) {
            if (exception.getFailure()
                    == MediaGatewayException.Failure.NOT_FOUND) {
                throw new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", file.getId())
                );
            }
            throw new BusinessException(
                    ErrorCode.MEDIA_GATEWAY_UNAVAILABLE
            );
        }
    }

    private boolean isSecureHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateAccessible(
            FileMeta file,
            Long fileId,
            Instant now
    ) {
        if (!isAccessible(file, now)) {
            throw new BusinessException(
                    ErrorCode.MEDIA_ACCESS_DENIED,
                    Map.of("fileId", fileId)
            );
        }
    }

    private boolean isAccessible(FileMeta file, Instant now) {
        return file.getUploadStatus() == UploadStatus.COMPLETED
                && file.getDeletedAt() == null
                && (file.getExpiresAt() == null
                || file.getExpiresAt().isAfter(now));
    }

    private MediaGatewayClient requiredClient() {
        MediaGatewayClient client = clientProvider.getIfAvailable();
        if (!properties.enabled() || client == null) {
            throw new BusinessException(
                    ErrorCode.MEDIA_GATEWAY_UNAVAILABLE
            );
        }
        return client;
    }
}
