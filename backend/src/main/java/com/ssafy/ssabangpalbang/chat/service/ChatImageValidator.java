package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅 IMAGE 메시지의 imageFileId를 검증한다.
 *
 * <p>모든 검증 실패는 단일 계약({@code CHAT_IMAGE_INVALID})으로 통일한다.
 * 어떤 조건이 실패했는지는 로그로만 남기고 클라이언트에는 세부 사유를 노출하지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatImageValidator {

    private final FileMetaRepository fileMetaRepository;
    private final MediaPresignedUrlProvider mediaPresignedUrlProvider;

    public FileMeta validate(Long studyId, Long senderId, Long imageFileId) {
        if (imageFileId == null) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }
        if (!mediaPresignedUrlProvider.isEnabled()) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }

        FileMeta fileMeta = fileMetaRepository.findById(imageFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_IMAGE_INVALID));

        if (fileMeta.getFileUsage() != FileUsage.CHAT_IMAGE) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }
        if (fileMeta.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }
        if (fileMeta.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }
        if (!fileMeta.getOwnerId().equals(senderId)) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }
        if (!studyId.equals(fileMeta.getStudyId())) {
            throw new BusinessException(ErrorCode.CHAT_IMAGE_INVALID);
        }

        return fileMeta;
    }
}
