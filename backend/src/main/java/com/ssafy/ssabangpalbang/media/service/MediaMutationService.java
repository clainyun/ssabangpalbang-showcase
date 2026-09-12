package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.config.MediaGatewayProperties;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.dto.request.MediaPrepareRequest;
import com.ssafy.ssabangpalbang.media.dto.response.MediaDeleteResponse;
import com.ssafy.ssabangpalbang.media.dto.response.MediaPrepareResponse;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayClient;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayException;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaMutationService {

    private final FileMetaRepository fileMetaRepository;
    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final ObjectProvider<MediaGatewayClient> clientProvider;
    private final MediaGatewayProperties properties;
    private final Clock clock;

    @Transactional
    public MediaPrepareResponse prepare(
            Long memberId,
            MediaPrepareRequest request
    ) {
        String contentType = request.contentType()
                .trim()
                .toLowerCase();
        MediaFilePolicy.validate(
                request.fileUsage(),
                contentType,
                request.sizeBytes()
        );
        validateStudyAccess(
                memberId,
                request.fileUsage(),
                request.studyId()
        );

        MediaGatewayClient.UploadUrlResponse gatewayResponse;
        try {
            gatewayResponse = requiredClient().createUploadUrl(
                    request.fileUsage(),
                    contentType,
                    request.sizeBytes()
            );
        } catch (MediaGatewayException exception) {
            throw gatewayUnavailable();
        }
        Instant now = clock.instant();
        validateUploadResponse(request, gatewayResponse, now);
        Instant finalFileExpiresAt =
                request.fileUsage() == FileUsage.STT_AUDIO
                        ? now.plus(properties.sttRetention())
                        : null;
        FileMeta file = fileMetaRepository.save(FileMeta.pending(
                memberId,
                request.studyId(),
                request.fileUsage(),
                normalizeOriginalName(request.originalName()),
                gatewayResponse.uploadKey(),
                contentType,
                request.sizeBytes(),
                gatewayResponse.expiresAt(),
                now
        ));

        return new MediaPrepareResponse(
                file.getId(),
                file.getFileUsage(),
                gatewayResponse.uploadUrl(),
                gatewayResponse.method(),
                gatewayResponse.requiredHeaders() == null
                        ? Map.of()
                        : Map.copyOf(gatewayResponse.requiredHeaders()),
                file.getUploadStatus(),
                gatewayResponse.expiresAt(),
                finalFileExpiresAt
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CompletionResult complete(Long memberId, Long fileId) {
        FileMeta file = locked(fileId);
        validateOwner(file, memberId);
        if (file.getDeletedAt() != null
                || file.getUploadStatus() == UploadStatus.DELETED) {
            throw new BusinessException(ErrorCode.MEDIA_ACCESS_DENIED);
        }
        if (file.getUploadStatus() == UploadStatus.COMPLETED) {
            return new CompletionResult(file.getId(), true);
        }
        if (file.getUploadStatus() == UploadStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.MEDIA_UPLOAD_ALREADY_FAILED
            );
        }

        Instant now = clock.instant();
        if (file.getExpiresAt() != null
                && !file.getExpiresAt().isAfter(now)) {
            file.fail();
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }
        validateStudyAccess(
                memberId,
                file.getFileUsage(),
                file.getStudyId()
        );
        validatePendingMetadata(file);

        MediaGatewayClient.VerifyResponse verification;
        try {
            verification = requiredClient().verify(
                    file.getFileUsage(),
                    file.getS3Key(),
                    file.getContentType(),
                    file.getSizeBytes()
            );
        } catch (MediaGatewayException exception) {
            if (exception.getFailure()
                    == MediaGatewayException.Failure.NOT_FOUND) {
                throw new BusinessException(
                        ErrorCode.MEDIA_OBJECT_NOT_FOUND
                );
            }
            if (exception.getFailure()
                    == MediaGatewayException.Failure.SIZE_MISMATCH) {
                file.fail();
                throw new BusinessException(
                        ErrorCode.MEDIA_SIZE_MISMATCH
                );
            }
            if (exception.getFailure()
                    == MediaGatewayException.Failure
                    .CONTENT_TYPE_MISMATCH) {
                file.fail();
                throw new BusinessException(
                        ErrorCode.MEDIA_CONTENT_TYPE_INVALID
                );
            }
            if (exception.getFailure()
                    == MediaGatewayException.Failure.CHANGED) {
                file.fail();
                throw new BusinessException(
                        ErrorCode.MEDIA_UPLOAD_CHANGED
                );
            }
            if (exception.getFailure()
                    == MediaGatewayException.Failure.REJECTED) {
                file.fail();
                throw new BusinessException(
                        ErrorCode.MEDIA_CONTENT_TYPE_INVALID
                );
            }
            throw gatewayUnavailable();
        }
        validateVerification(file, verification);
        file.complete(
                verification.s3Key(),
                verification.contentType(),
                verification.sizeBytes(),
                file.getFileUsage() == FileUsage.STT_AUDIO
                        ? file.getCreatedAt().plus(
                                properties.sttRetention()
                        )
                        : null
        );
        return new CompletionResult(file.getId(), false);
    }

    @Transactional
    public MediaDeleteResponse delete(Long memberId, Long fileId) {
        FileMeta file = locked(fileId);
        validateOwner(file, memberId);
        if (file.getDeletedAt() != null
                || file.getUploadStatus() == UploadStatus.DELETED) {
            return new MediaDeleteResponse(
                    file.getId(),
                    UploadStatus.DELETED,
                    file.getDeletedAt()
            );
        }
        if (file.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEDIA_ACCESS_DENIED);
        }

        try {
            MediaGatewayClient.DeleteResponse response =
                    requiredClient().delete(
                            file.getFileUsage(),
                            file.getS3Key()
                    );
            validateDeleteResponse(file, response);
        } catch (MediaGatewayException exception) {
            if (exception.getFailure()
                    != MediaGatewayException.Failure.NOT_FOUND) {
                throw gatewayUnavailable();
            }
        }

        Instant now = clock.instant();
        file.markDeleted(now);
        return new MediaDeleteResponse(
                file.getId(),
                file.getUploadStatus(),
                now
        );
    }

    private void validateUploadResponse(
            MediaPrepareRequest request,
            MediaGatewayClient.UploadUrlResponse response,
            Instant now
    ) {
        if (response.fileUsage() != request.fileUsage()
                || response.expectedSizeBytes()
                != request.sizeBytes()
                || response.uploadKey() == null
                || response.uploadKey().isBlank()
                || response.uploadUrl() == null
                || response.uploadUrl().isBlank()
                || !isSecureHttpUrl(response.uploadUrl())
                || !"PUT".equalsIgnoreCase(response.method())
                || response.expiresAt() == null
                || !response.expiresAt().isAfter(now)) {
            throw gatewayUnavailable();
        }
        validateRequiredHeaders(
                request.contentType().trim(),
                response.requiredHeaders()
        );
    }

    private void validateRequiredHeaders(
            String expectedContentType,
            Map<String, String> headers
    ) {
        if (headers == null || headers.size() != 1) {
            throw gatewayUnavailable();
        }
        Map.Entry<String, String> header =
                headers.entrySet().iterator().next();
        if (!"content-type".equalsIgnoreCase(header.getKey())
                || !expectedContentType.equalsIgnoreCase(
                        header.getValue()
                )) {
            throw gatewayUnavailable();
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

    private void validatePendingMetadata(FileMeta file) {
        if (file.getContentType() == null
                || file.getContentType().isBlank()) {
            file.fail();
            throw new BusinessException(
                    ErrorCode.MEDIA_CONTENT_TYPE_INVALID
            );
        }
        if (file.getSizeBytes() == null
                || file.getSizeBytes() < 1) {
            file.fail();
            throw new BusinessException(
                    ErrorCode.MEDIA_SIZE_MISMATCH
            );
        }
    }

    private void validateDeleteResponse(
            FileMeta file,
            MediaGatewayClient.DeleteResponse response
    ) {
        if (response == null
                || !response.deleted()
                || response.fileUsage() != file.getFileUsage()
                || !file.getS3Key().equals(response.s3Key())) {
            throw gatewayUnavailable();
        }
    }

    private void validateVerification(
            FileMeta file,
            MediaGatewayClient.VerifyResponse verification
    ) {
        if (!verification.verified()
                || verification.fileUsage() != file.getFileUsage()
                || verification.s3Key() == null
                || verification.s3Key().isBlank()) {
            file.fail();
            throw new BusinessException(
                    ErrorCode.MEDIA_CONTENT_TYPE_INVALID
            );
        }
        if (verification.sizeBytes() != file.getSizeBytes()) {
            file.fail();
            throw new BusinessException(ErrorCode.MEDIA_SIZE_MISMATCH);
        }
        if (!file.getContentType().equalsIgnoreCase(
                verification.contentType()
        )) {
            file.fail();
            throw new BusinessException(
                    ErrorCode.MEDIA_CONTENT_TYPE_INVALID
            );
        }
    }

    private void validateStudyAccess(
            Long memberId,
            FileUsage usage,
            Long studyId
    ) {
        if (!MediaFilePolicy.requiresStudy(usage)) {
            if (studyId != null) {
                throw new BusinessException(
                        ErrorCode.MEDIA_FILE_USAGE_INVALID
                );
            }
            return;
        }
        if (studyId == null) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STUDY_FORBIDDEN
            );
        }
        if (studyRepository.findByIdAndDeletedAtIsNull(studyId).isEmpty()) {
            throw new BusinessException(ErrorCode.STUDY_NOT_FOUND);
        }
        boolean activeMember = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(member ->
                        member.getStatus() == StudyMemberStatus.ACTIVE
                )
                .isPresent();
        if (!activeMember) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STUDY_FORBIDDEN
            );
        }
    }

    private FileMeta locked(Long fileId) {
        return fileMetaRepository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", fileId)
                ));
    }

    private void validateOwner(FileMeta file, Long memberId) {
        if (!file.getOwnerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_OWNER);
        }
    }

    private MediaGatewayClient requiredClient() {
        MediaGatewayClient client = clientProvider.getIfAvailable();
        if (!properties.enabled() || client == null) {
            throw gatewayUnavailable();
        }
        return client;
    }

    private BusinessException gatewayUnavailable() {
        return new BusinessException(
                ErrorCode.MEDIA_GATEWAY_UNAVAILABLE
        );
    }

    private String normalizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return null;
        }
        return originalName.trim();
    }

    public record CompletionResult(Long fileId, boolean alreadyCompleted) {
    }
}
