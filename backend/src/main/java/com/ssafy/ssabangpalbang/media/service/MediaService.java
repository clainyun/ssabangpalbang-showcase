package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.dto.request.MediaPrepareRequest;
import com.ssafy.ssabangpalbang.media.dto.response.MediaDeleteResponse;
import com.ssafy.ssabangpalbang.media.dto.response.MediaFileResponse;
import com.ssafy.ssabangpalbang.media.dto.response.MediaPrepareResponse;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaMutationService mutationService;
    private final FileMetaRepository fileMetaRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MediaAccessUrlProvider accessUrlProvider;

    public MediaPrepareResponse prepare(
            Long memberId,
            MediaPrepareRequest request
    ) {
        return mutationService.prepare(memberId, request);
    }

    public CompleteResult complete(Long memberId, Long fileId) {
        MediaMutationService.CompletionResult completion =
                mutationService.complete(memberId, fileId);
        return new CompleteResult(
                getOwnedCompletedFile(memberId, completion.fileId()),
                completion.alreadyCompleted()
        );
    }

    @Transactional(readOnly = true)
    public MediaFileResponse get(Long memberId, Long fileId) {
        FileMeta file = find(fileId);
        validateReadAccess(file, memberId);
        if (file.getFileUsage() == FileUsage.STT_AUDIO) {
            return MediaFileResponse.from(file, null);
        }
        if (file.getUploadStatus() != UploadStatus.COMPLETED
                || file.getDeletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.MEDIA_ACCESS_DENIED
            );
        }
        return MediaFileResponse.from(
                file,
                accessUrlProvider.issue(fileId)
        );
    }

    public MediaDeleteResponse delete(Long memberId, Long fileId) {
        return mutationService.delete(memberId, fileId);
    }

    private MediaFileResponse getOwnedCompletedFile(
            Long memberId,
            Long fileId
    ) {
        FileMeta file = find(fileId);
        if (!file.getOwnerId().equals(memberId)
                || file.getUploadStatus() != UploadStatus.COMPLETED
                || file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEDIA_ACCESS_DENIED);
        }
        if (file.getFileUsage() == FileUsage.STT_AUDIO) {
            return MediaFileResponse.from(file, null);
        }
        return MediaFileResponse.from(
                file,
                accessUrlProvider.issue(fileId)
        );
    }

    private void validateReadAccess(FileMeta file, Long memberId) {
        if (file.getOwnerId().equals(memberId)) {
            return;
        }
        if (file.getStudyId() != null) {
            boolean activeMember = studyMemberRepository
                    .findByStudyIdAndMemberId(
                            file.getStudyId(),
                            memberId
                    )
                    .filter(member ->
                            member.getStatus()
                                    == StudyMemberStatus.ACTIVE
                    )
                    .isPresent();
            if (activeMember) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.MEDIA_FORBIDDEN);
    }

    private FileMeta find(Long fileId) {
        return fileMetaRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", fileId)
                ));
    }

    public record CompleteResult(
            MediaFileResponse response,
            boolean alreadyCompleted
    ) {
    }
}
